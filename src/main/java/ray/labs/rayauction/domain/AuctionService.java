package ray.labs.rayauction.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import ray.labs.rayauction.domain.port.BalancePort;
import ray.labs.rayauction.domain.port.DeliveryPort;
import ray.labs.rayauction.domain.port.EventPublisher;
import ray.labs.rayauction.storage.AuctionRepository;
import ray.labs.rayauction.storage.LedgerRepository;
import ray.labs.rayauction.storage.MailRepository;
import ray.labs.rayauction.storage.TransactionRepository;
import ray.labs.rayauction.storage.UnitOfWork;

public final class AuctionService {

    private final AuctionRepository auctions;
    private final TransactionRepository transactions;
    private final MailRepository mail;
    private final LedgerRepository ledger;
    private final UnitOfWork unitOfWork;
    private final CurrencyService currencies;
    private final AuctionValidator validator;
    private final EventPublisher publisher;
    private final DeliveryPort delivery;
    private final Function<String, Optional<UUID>> accountResolver;
    private final Clock clock;
    private final Logger logger;
    private final AtomicBoolean readOnly = new AtomicBoolean(false);

    public AuctionService(
            AuctionRepository auctions,
            TransactionRepository transactions,
            MailRepository mail,
            LedgerRepository ledger,
            UnitOfWork unitOfWork,
            CurrencyService currencies,
            AuctionValidator validator,
            EventPublisher publisher,
            DeliveryPort delivery,
            Function<String, Optional<UUID>> accountResolver,
            Clock clock,
            Logger logger) {
        this.auctions = auctions;
        this.transactions = transactions;
        this.mail = mail;
        this.ledger = ledger;
        this.unitOfWork = unitOfWork;
        this.currencies = currencies;
        this.validator = validator;
        this.publisher = publisher;
        this.delivery = delivery;
        this.accountResolver = accountResolver;
        this.clock = clock;
        this.logger = logger;
    }

    public AuctionValidator validator() {
        return validator;
    }

    public CurrencyService currencies() {
        return currencies;
    }

    public AuctionRules rules() {
        return validator.rules();
    }

    public boolean isReadOnly() {
        return readOnly.get();
    }

    public void readOnly(boolean value) {
        readOnly.set(value);
    }

    public Optional<Auction> find(long id) {
        return auctions.findById(id);
    }

    public Page<Auction> search(AuctionSearchFilter filter) {
        return auctions.search(filter);
    }

    public Page<Auction> listingsOf(UUID sellerId, AuctionSearchFilter filter) {
        return auctions.findBySeller(sellerId, AuctionStatus.ACTIVE, filter);
    }

    public Result<Auction> createListing(UUID sellerId, String sellerName, AuctionItem item, Money price, DurationSpec duration) {
        if (readOnly.get()) {
            return Result.Failure.of("storage.read-only");
        }
        Instant now = clock.instant();
        Auction draft = Auction.active(
                0L,
                sellerId,
                sellerName,
                item,
                price,
                now,
                now.plus(duration.duration()));
        long id;
        try {
            id = auctions.insert(draft);
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "failed to insert auction for " + sellerId, ex);
            return Result.Failure.of("storage.failure");
        }
        Auction created = new Auction(
                id,
                sellerId,
                sellerName,
                item,
                price,
                now,
                draft.expiresAt(),
                AuctionStatus.ACTIVE,
                null,
                0);
        publisher.auctionCreated(created);
        return Result.Success.of(created, Outcome.builder("sell.success")
                .with("price", price.amount().toPlainString())
                .with("currency", price.currency().displayName())
                .with("item", item.displayName().isEmpty() ? item.material() : item.displayName())
                .with("duration", duration.label())
                .build());
    }

    public Result<Transaction> buy(PurchaseTicket ticket) {
        String buyerName = ticket.buyerName();
        if (readOnly.get()) {
            return Result.Failure.of("storage.read-only");
        }
        Auction auction = ticket.auction();
        UUID buyerId = ticket.buyerId();
        Optional<Outcome> denial = validator.checkPurchase(auction, buyerId, clock.instant(), ticket.admin());
        if (denial.isPresent()) {
            return Result.Failure.of(denial.get());
        }
        Money price = auction.price();
        BalancePort buyerPort = currencies.requireBalancePort(price.currency());
        Optional<Money> balance = buyerPort.balance(buyerId);
        if (balance.isEmpty()) {
            return Result.Failure.of("buy.currency-unavailable");
        }
        if (!balance.get().isGreaterOrEqual(price)) {
            return Result.Failure.of(Outcome.builder("buy.not-enough-money")
                    .with("required", price.amount().toPlainString())
                    .with("balance", balance.get().amount().toPlainString())
                    .with("currency", price.currency().displayName())
                    .build());
        }
        Money tax = currencies.tax(price, rules().taxRate());
        Money income = currencies.sellerIncome(price, tax);
        UUID taxAccount = rules().taxDestination() == AuctionRules.TaxDestination.ACCOUNT && tax.isPositive()
                ? accountResolver.apply(rules().taxAccount()).orElse(null)
                : null;
        if (rules().taxDestination() == AuctionRules.TaxDestination.ACCOUNT && tax.isPositive() && taxAccount == null) {
            return Result.Failure.of(Outcome.builder("buy.tax-account-missing")
                    .with("account", rules().taxAccount())
                    .build());
        }
        MoneyTransfer transfer = new MoneyTransfer(
                price.currency(), buyerId, price, auction.sellerId(), income, taxAccount, tax);
        if (!withdraw(transfer, buyerPort)) {
            return Result.Failure.of(Outcome.builder("buy.not-enough-money")
                    .with("required", price.amount().toPlainString())
                    .with("balance", balance.get().amount().toPlainString())
                    .with("currency", price.currency().displayName())
                    .build());
        }
        if (!credit(transfer, buyerPort)) {
            buyerPort.refund(buyerId, price);
            return Result.Failure.of("buy.payout-failed");
        }
        Transaction record = Transaction.of(auction, buyerId, buyerName, tax, clock.instant());
        Optional<Transaction> stored = commitPurchase(auction, record);
        if (stored.isEmpty()) {
            rollbackMoney(transfer, buyerPort);
            return Result.Failure.of("buy.lost-race");
        }
        Transaction committed = stored.get();
        publisher.auctionPurchased(committed);
        delivery.deliver(buyerId, auction.item(), MailEntry.MailReason.PURCHASE_INCOME, auction.id());
        return Result.Success.of(committed, Outcome.builder("buy.success")
                .with("price", price.amount().toPlainString())
                .with("currency", price.currency().displayName())
                .with("item", auction.item().displayName().isEmpty()
                        ? auction.item().material()
                        : auction.item().displayName())
                .with("seller", auction.sellerName())
                .build());
    }

    private boolean withdraw(MoneyTransfer transfer, BalancePort buyerPort) {
        return buyerPort.withdraw(transfer.buyerId(), transfer.withdrawn());
    }

    private boolean credit(MoneyTransfer transfer, BalancePort buyerPort) {
        try {
            if (transfer.sellerIncome().isPositive() && !transfer.sellerId().equals(transfer.buyerId())) {
                buyerPort.deposit(transfer.sellerId(), transfer.sellerIncome());
            }
            if (transfer.hasTax() && transfer.hasTaxAccount() && !transfer.taxAccountId().equals(transfer.buyerId())) {
                buyerPort.deposit(transfer.taxAccountId(), transfer.tax());
            }
            return true;
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "failed to credit seller for auction " + transfer, ex);
            return false;
        }
    }

    private void rollbackMoney(MoneyTransfer transfer, BalancePort buyerPort) {
        try {
            if (transfer.sellerIncome().isPositive() && !transfer.sellerId().equals(transfer.buyerId())) {
                buyerPort.withdraw(transfer.sellerId(), transfer.sellerIncome());
            }
            if (transfer.hasTax() && transfer.hasTaxAccount() && !transfer.taxAccountId().equals(transfer.buyerId())) {
                buyerPort.withdraw(transfer.taxAccountId(), transfer.tax());
            }
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "compensation failed for purchase of auction, manual audit required", ex);
        }
        buyerPort.refund(transfer.buyerId(), transfer.withdrawn());
    }

    private Optional<Transaction> commitPurchase(Auction auction, Transaction record) {
        if (transactions.existsForAuction(auction.id())) {
            return Optional.empty();
        }
        try {
            Long id = unitOfWork.call(() -> {
                int updated = auctions.transition(auction.id(), auction.version(), AuctionStatus.SOLD, record.buyerId());
                if (updated != 1) {
                    return null;
                }
                return transactions.insert(record);
            });
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(new Transaction(
                    id,
                    record.auctionId(),
                    record.buyerId(),
                    record.buyerName(),
                    record.sellerId(),
                    record.sellerName(),
                    record.item(),
                    record.price(),
                    record.tax(),
                    record.createdAt()));
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "purchase transaction failed for auction " + auction.id(), ex);
            return Optional.empty();
        }
    }

    public Result<Auction> cancel(UUID playerId, long auctionId, boolean admin) {
        if (readOnly.get()) {
            return Result.Failure.of("storage.read-only");
        }
        Optional<Auction> found = auctions.findById(auctionId);
        if (found.isEmpty()) {
            return Result.Failure.of("cancel.not-found");
        }
        Auction auction = found.get();
        Optional<Outcome> denial = validator.checkCancel(auction, playerId, admin);
        if (denial.isPresent()) {
            return Result.Failure.of(denial.get());
        }
        int updated = auctions.transition(auction.id(), auction.version(), AuctionStatus.CANCELLED, null);
        if (updated != 1) {
            return Result.Failure.of("cancel.not-active");
        }
        Auction cancelled = auction.withStatus(AuctionStatus.CANCELLED, null);
        publisher.auctionCancelled(cancelled);
        delivery.deliver(playerId, auction.item(), MailEntry.MailReason.CANCELLED_RETURN, auction.id());
        return Result.Success.of(cancelled, Outcome.builder("cancel.success")
                .with("item", auction.item().displayName().isEmpty()
                        ? auction.item().material()
                        : auction.item().displayName())
                .build());
    }

    public int cancelAll(UUID playerId, int max) {
        if (readOnly.get()) {
            return 0;
        }
        int cancelled = 0;
        int attempts = Math.max(1, max);
        for (int attempt = 0; attempt < attempts; attempt++) {
            Page<Auction> page = listingsOf(playerId, AuctionSearchFilter.empty(1));
            if (page.items().isEmpty()) {
                break;
            }
            if (!cancel(playerId, page.items().get(0).id(), false).isSuccess()) {
                break;
            }
            cancelled++;
        }
        return cancelled;
    }

    public int sweepExpired(int batchSize) {
        if (readOnly.get()) {
            return 0;
        }
        Instant now = clock.instant();
        List<Auction> expiring;
        try {
            expiring = auctions.findExpiring(now, batchSize);
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "expiry sweep query failed", ex);
            readOnly.set(true);
            return 0;
        }
        int handled = 0;
        for (Auction auction : expiring) {
            if (!auction.isExpired(now)) {
                continue;
            }
            int updated = auctions.transition(auction.id(), auction.version(), AuctionStatus.EXPIRED, null);
            if (updated != 1) {
                continue;
            }
            Auction expired = auction.withStatus(AuctionStatus.EXPIRED, null);
            publisher.auctionExpired(expired);
            delivery.deliver(
                    auction.sellerId(), auction.item(), MailEntry.MailReason.EXPIRED_RETURN, auction.id());
            handled++;
        }
        return handled;
    }

    public List<HistoryEntry> history(UUID playerId, HistoryFilter filter, int limit) {
        int fetchLimit = Math.max(1, limit);
        List<HistoryEntry> entries = new ArrayList<>();
        for (HistoryEntry entry : transactions.historyOf(playerId, fetchLimit)) {
            if (filter.matches(entry)) {
                entries.add(entry);
            }
        }
        for (Auction auction : auctions.findTerminal(playerId, fetchLimit)) {
            HistoryEntry entry = new HistoryEntry(
                    auction.id(),
                    auction.id(),
                    playerId,
                    HistoryEntry.HistoryRole.SELLER,
                    auction.buyerId(),
                    "",
                    auction.item(),
                    auction.price(),
                    Money.zero(auction.currency()),
                    auction.status(),
                    auction.createdAt());
            if (filter.matches(entry)) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(HistoryEntry::createdAt).reversed());
        return entries.size() > limit ? List.copyOf(entries.subList(0, limit)) : List.copyOf(entries);
    }

    public Page<HistoryEntry> historyPage(UUID playerId, HistoryFilter filter, int page, int perPage) {
        int fetchLimit = Math.max(perPage, 1) * 4;
        return Page.of(history(playerId, filter, fetchLimit), page, perPage);
    }

    public int flushMailbox(UUID playerId, int limit) {
        List<MailEntry> entries = mail.findUndelivered(playerId, limit);
        int flushed = 0;
        for (MailEntry entry : entries) {
            boolean done = false;
            if (entry.item().isPresent()) {
                done = delivery.deliver(playerId, entry.item().get(), entry.reason(), entry.auctionId());
            } else if (entry.money().isPresent()) {
                currencies
                        .balancePort(entry.money().get().currency())
                        .ifPresent(port -> port.deposit(playerId, entry.money().get()));
                done = true;
            }
            if (done && mail.markDelivered(entry.id())) {
                flushed++;
            }
        }
        return flushed;
    }

    public int pendingMail(UUID playerId) {
        return mail.countUndelivered(playerId);
    }

    public JoinReport onJoin(UUID playerId, int mailLimit) {
        long credited = 0L;
        Optional<Currency> experience = currencies.catalog().byId(Currency.Experience.ID);
        if (experience.isPresent()) {
            BigDecimal pending = ledger.balance(playerId, experience.get());
            if (pending.signum() > 0) {
                ledger.tryWithdraw(playerId, experience.get(), pending);
                credited = pending.longValue();
                currencies.requireBalancePort(experience.get()).deposit(playerId, new Money(pending, experience.get()));
            }
        }
        int flushed = flushMailbox(playerId, mailLimit);
        int pending = mail.countUndelivered(playerId);
        return new JoinReport(credited, flushed, pending);
    }

    public record JoinReport(long experienceCredited, int mailDelivered, int mailPending) {

        public boolean isEmpty() {
            return experienceCredited == 0L && mailDelivered == 0 && mailPending == 0;
        }
    }
}
