package ray.labs.rayauction.economy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.domain.port.OnlinePort;
import ray.labs.rayauction.storage.LedgerRepository;

public final class ExperienceProvider implements EconomyProvider {

    private final Currency.Experience currency;
    private final LedgerRepository ledger;
    private final OnlinePort online;

    public ExperienceProvider(Currency.Experience currency, LedgerRepository ledger, OnlinePort online) {
        this.currency = currency;
        this.ledger = ledger;
        this.online = online;
    }

    @Override
    public Currency.Experience currency() {
        return currency;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String kind() {
        return Currency.Experience.ID;
    }

    @Override
    public Optional<Money> lookup(UUID playerId) {
        if (online.isOnline(playerId)) {
            return Optional.of(new Money(BigDecimal.valueOf(online.currentExperience(playerId)), currency));
        }
        return Optional.of(new Money(ledger.balance(playerId, currency), currency));
    }

    @Override
    public boolean take(UUID playerId, Money money) {
        int amount = (int) Math.min(Integer.MAX_VALUE, money.amount().longValueExact());
        if (amount <= 0) {
            return true;
        }
        if (online.isOnline(playerId)) {
            long current = online.currentExperience(playerId);
            if (current < amount) {
                return false;
            }
            online.setExperience(playerId, current - amount);
            return true;
        }
        return ledger.tryWithdraw(playerId, currency, BigDecimal.valueOf(amount));
    }

    @Override
    public void give(UUID playerId, Money money) {
        int amount = (int) Math.min(Integer.MAX_VALUE, money.amount().longValueExact());
        if (amount <= 0) {
            return;
        }
        ledger.credit(playerId, currency, BigDecimal.valueOf(amount));
        if (online.isOnline(playerId)) {
            long pending = ledger.balance(playerId, currency).longValue();
            if (pending <= 0) {
                return;
            }
            long updated = online.currentExperience(playerId) + pending;
            online.setExperience(playerId, Math.min(Integer.MAX_VALUE, updated));
            ledger.tryWithdraw(playerId, currency, BigDecimal.valueOf(pending));
        }
    }
}
