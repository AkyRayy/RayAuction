package ray.labs.rayauction.gui;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ray.labs.rayauction.config.GuiConfig;
import ray.labs.rayauction.config.Messages;
import ray.labs.rayauction.config.Sounds;
import ray.labs.rayauction.discord.DiscordWebhook;
import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.AuctionSearchFilter;
import ray.labs.rayauction.domain.AuctionService;
import ray.labs.rayauction.domain.BlacklistPolicy;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.domain.DurationSpec;
import ray.labs.rayauction.domain.HistoryEntry;
import ray.labs.rayauction.domain.HistoryFilter;
import ray.labs.rayauction.domain.ItemCategory;
import ray.labs.rayauction.domain.LimitService;
import ray.labs.rayauction.domain.MailEntry;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.domain.Outcome;
import ray.labs.rayauction.domain.Page;
import ray.labs.rayauction.domain.PlayerLimit;
import ray.labs.rayauction.domain.PurchaseTicket;
import ray.labs.rayauction.domain.Result;
import ray.labs.rayauction.domain.SortType;
import ray.labs.rayauction.domain.Transaction;
import ray.labs.rayauction.gui.holder.AnvilInputHolder;
import ray.labs.rayauction.gui.holder.AuctionBrowserHolder;
import ray.labs.rayauction.gui.holder.AuctionConfirmHolder;
import ray.labs.rayauction.gui.holder.AuctionHistoryHolder;
import ray.labs.rayauction.gui.holder.AuctionPlayerHolder;
import ray.labs.rayauction.gui.holder.AuctionSellHolder;
import ray.labs.rayauction.gui.holder.MailboxHolder;
import ray.labs.rayauction.gui.holder.MenuHolder;
import ray.labs.rayauction.platform.PaperItemGateway;
import ray.labs.rayauction.platform.Scheduler;
import ray.labs.rayauction.storage.MailRepository;
import ray.labs.rayauction.storage.cache.AuctionCache;
import ray.labs.rayauction.text.TextFormatter;
import ray.labs.rayauction.util.Debouncer;
import ray.labs.rayauction.util.ItemSerializer;

public final class GuiService {

    private final Supplier<AuctionService> auctions;
    private final Supplier<LimitService> limits;
    private final AuctionCache cache;
    private final MailRepository mail;
    private final ShulkerPreviewService preview;
    private final PaperItemGateway items;
    private final CurrencyCatalog catalog;
    private final Scheduler scheduler;
    private final TextFormatter formatter;
    private final Clock clock;
    private final Logger logger;
    private final Supplier<GuiConfig> guiConfig;
    private final Supplier<Messages> messages;
    private final Supplier<DiscordWebhook> discord;
    private final Supplier<BlacklistPolicy> blacklist;
    private final Supplier<Sounds> sounds;
    private final Debouncer purchaseDebounce;

    private final Map<UUID, SellSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> preparedAnvilText = new ConcurrentHashMap<>();
    private final Map<UUID, ViewState> remembered = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingSearch = new ConcurrentHashMap<>();

    public GuiService(
            Supplier<AuctionService> auctions,
            Supplier<LimitService> limits,
            AuctionCache cache,
            MailRepository mail,
            ShulkerPreviewService preview,
            PaperItemGateway items,
            CurrencyCatalog catalog,
            Scheduler scheduler,
            TextFormatter formatter,
            Clock clock,
            Logger logger,
            Supplier<GuiConfig> guiConfig,
            Supplier<Messages> messages,
            Supplier<DiscordWebhook> discord,
            Supplier<BlacklistPolicy> blacklist,
            Supplier<Sounds> sounds,
            long purchaseCooldownMillis) {
        this.auctions = auctions;
        this.limits = limits;
        this.cache = cache;
        this.mail = mail;
        this.preview = preview;
        this.items = items;
        this.catalog = catalog;
        this.scheduler = scheduler;
        this.formatter = formatter;
        this.clock = clock;
        this.logger = logger;
        this.guiConfig = guiConfig;
        this.messages = messages;
        this.discord = discord;
        this.blacklist = blacklist;
        this.sounds = sounds;
        this.purchaseDebounce = new Debouncer(purchaseCooldownMillis, System::currentTimeMillis);
    }

    public void openBrowser(Player player) {
        openBrowser(player, remembered.getOrDefault(player.getUniqueId(), ViewState.DEFAULT));
    }

    public void openBrowser(Player player, ViewState state) {
        AuctionBrowserHolder holder = new AuctionBrowserHolder(player.getUniqueId(), state);
        open(player, holder, "gui.browser.title", Map.of(), 54, (viewer, inventory) -> renderBrowser(viewer, holder, inventory, state));
    }

    public void openPlayerListings(Player player, UUID targetId, String targetName) {
        AuctionPlayerHolder holder = new AuctionPlayerHolder(targetId, targetName, ViewState.DEFAULT);
        open(
                player,
                holder,
                "gui.player.title",
                Map.of("player", targetName),
                54,
                (viewer, inventory) -> renderPlayerListings(viewer, holder, inventory));
    }

    public void openHistory(Player player, HistoryFilter filter, int page) {
        AuctionHistoryHolder holder = new AuctionHistoryHolder(player.getUniqueId(), filter, page);
        open(player, holder, "gui.history.title", Map.of(), 54, (viewer, inventory) -> renderHistory(viewer, holder, inventory));
    }

    public void openMailbox(Player player, int page) {
        MailboxHolder holder = new MailboxHolder(page);
        open(player, holder, "gui.mailbox.title", Map.of(), 54, (viewer, inventory) -> renderMailbox(viewer, holder, inventory));
    }

    public void openConfirm(Player player, Auction auction) {
        AuctionConfirmHolder holder = new AuctionConfirmHolder(auction.id());
        open(
                player,
                holder,
                "gui.confirm.title",
                Map.of("item", displayName(auction.item())),
                27,
                (viewer, inventory) -> renderConfirm(viewer, holder, auction, inventory));
    }

    public void openSearchPrompt(Player player) {
        player.closeInventory();
        pendingSearch.put(player.getUniqueId(), Boolean.TRUE);
        player.sendMessage(messages().render("gui.search.prompt"));
    }

    public boolean isAwaitingSearch(UUID playerId) {
        return pendingSearch.containsKey(playerId);
    }

    public void clearAwaitingSearch(UUID playerId) {
        pendingSearch.remove(playerId);
    }

    public void submitSearch(Player player, String query) {
        pendingSearch.remove(player.getUniqueId());
        if (query == null || query.isBlank()) {
            player.sendMessage(messages().render("gui.search.empty"));
            return;
        }
        openBrowser(player, ViewState.DEFAULT.query(query.trim()));
    }

    public void openSell(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            player.sendMessage(messages().render("sell.hand-empty"));
            return;
        }
        byte[] data = ItemSerializer.toBytes(held);
        AuctionItem item = items.describe(data);
        Optional<String> blocked = denyReason(item, player);
        if (blocked.isPresent()) {
            player.sendMessage(messages().render("sell.blacklisted", Map.of("reason", blocked.get())));
            return;
        }
        int limit = limits.get().limitOf(player.getUniqueId());
        int active = cache.activeOf(player.getUniqueId());
        if (limit != PlayerLimit.UNLIMITED && active >= limit) {
            player.sendMessage(messages().render(
                    "sell.limit-reached",
                    Map.of("limit", Integer.toString(limit), "active", Integer.toString(active))));
            return;
        }
        int amount = held.getAmount();
        player.getInventory().setItemInMainHand(null);
        SellSession session = new SellSession(
                player.getUniqueId(), item, defaultCurrency(), auctions.get().rules().durations().get(0));
        sessions.put(player.getUniqueId(), session);
        AuctionSellHolder holder = new AuctionSellHolder(player.getUniqueId().toString());
        open(
                player,
                holder,
                "gui.sell.title",
                Map.of("amount", Integer.toString(amount)),
                54,
                (viewer, inventory) -> renderSell(viewer, holder, session, inventory));
    }

    public void cancelSell(Player player) {
        SellSession session = sessions.remove(player.getUniqueId());
        player.closeInventory();
        if (session == null || session.consumed()) {
            return;
        }
        scheduler.runFor(player, () -> {
            if (items.give(player.getUniqueId(), session.item())) {
                player.sendMessage(messages().render("sell.cancelled"));
                playNow(player, "cancel");
                return;
            }
            storeSession(session, MailEntry.MailReason.CANCELLED_RETURN);
            player.sendMessage(messages().render("sell.returned-to-mailbox"));
            playNow(player, "cancel");
        });
    }

    public void openPriceInput(Player player) {
        SellSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        session.suspend(true);
        AnvilInputHolder holder = new AnvilInputHolder(
                AnvilInputHolder.Purpose.PRICE, player.getUniqueId().toString());
        Inventory inventory = Bukkit.createInventory(holder, org.bukkit.event.inventory.InventoryType.ANVIL, messages().render("gui.anvil.title"));
        holder.attach(inventory);
        inventory.setItem(0, ItemFactory.icon("NAME_TAG", 0, messages().render("gui.anvil.input-name"), List.of(), false));
        holder.bind(2, (clicker, clickType) -> acceptAnvilPrice(clicker));
        player.openInventory(inventory);
    }

    public void prepareAnvilText(Player player, String text) {
        preparedAnvilText.put(player.getUniqueId(), text == null ? "" : text);
    }

    private void acceptAnvilPrice(Player player) {
        SellSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.suspend(false);
        }
        String text = preparedAnvilText.remove(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        if (text == null || text.isBlank()) {
            player.sendMessage(messages().render("gui.anvil.empty"));
            return;
        }
        Optional<Money> parsed = Money.parse(stripTags(text), session.currency());
        if (parsed.isEmpty()) {
            player.sendMessage(messages().render("gui.anvil.invalid"));
            return;
        }
        AuctionService service = auctions.get();
        var bounds = service.rules().boundsFor(session.currency());
        if (!bounds.contains(parsed.get().amount())) {
            player.sendMessage(messages().render(
                    "sell.price-out-of-range",
                    Map.of("min", bounds.min().toPlainString(), "max", bounds.max().toPlainString())));
            return;
        }
        session.price(parsed.get());
        AuctionSellHolder holder = new AuctionSellHolder(player.getUniqueId().toString());
        open(
                player,
                holder,
                "gui.sell.title",
                Map.of("amount", Integer.toString(session.item().amount())),
                54,
                (viewer, inventory) -> renderSell(viewer, holder, session, inventory));
    }

    public void confirmListing(Player player) {
        SellSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        if (!session.isReady()) {
            player.sendMessage(messages().render("sell.incomplete"));
            return;
        }
        if (!purchaseDebounce.tryAcquire(player.getUniqueId(), "list")) {
            return;
        }
        session.consume();
        sessions.remove(player.getUniqueId());
        String sellerName = player.getName();
        scheduler.runAsync(() -> {
            Result<Auction> result = auctions.get()
                    .createListing(
                            session.playerId(), sellerName, session.item(), session.price(), session.duration());
            scheduler.runFor(player, () -> {
                respond(player, result);
                if (!result.isSuccess()) {
                    rollbackListing(player, session);
                    return;
                }
                announceListing(result.valueOrThrow());
                player.closeInventory();
                openBrowser(player);
            });
        });
    }

    public void purchase(Player player, Auction auction) {
        if (!purchaseDebounce.tryAcquire(player.getUniqueId(), "buy:" + auction.id())) {
            return;
        }
        boolean admin = player.hasPermission("rayauction.admin");
        String buyerName = player.getName();
        player.sendMessage(messages().render("buy.processing"));
        scheduler.runAsync(() -> {
            Result<Transaction> result =
                    auctions.get().buy(new PurchaseTicket(auction, player.getUniqueId(), buyerName, admin));
            scheduler.runFor(player, () -> {
                respond(player, result);
                if (result.isSuccess()) {
                    notifyDiscord(buyerName, auction);
                    cache.remove(auction.id());
                    player.closeInventory();
                    return;
                }
                refreshCurrent(player);
            });
        });
    }

    public void cancel(Player player, Auction auction) {
        if (!purchaseDebounce.tryAcquire(player.getUniqueId(), "cancel:" + auction.id())) {
            return;
        }
        boolean admin = player.hasPermission("rayauction.admin");
        scheduler.runAsync(() -> {
            Result<Auction> result = auctions.get().cancel(player.getUniqueId(), auction.id(), admin);
            scheduler.runFor(player, () -> {
                respond(player, result);
                cache.remove(auction.id());
                player.closeInventory();
            });
        });
    }

    public void refreshCurrent(Player player) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        switch (holder) {
            case AuctionBrowserHolder browser -> openBrowser(player, browser.state());
            case AuctionPlayerHolder target -> openPlayerListings(player, target.targetId(), target.targetName());
            case AuctionHistoryHolder history -> openHistory(player, history.filter(), history.page());
            case MailboxHolder mailbox -> openMailbox(player, mailbox.page());
            default -> {}
        }
    }

    public void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                scheduler.runFor(player, () -> refreshCurrent(player));
            }
        }
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                player.closeInventory();
            }
        }
    }

    public void dropSellSession(Player player) {
        SellSession session = sessions.remove(player.getUniqueId());
        if (session == null || session.consumed()) {
            return;
        }
        if (items.give(player.getUniqueId(), session.item())) {
            player.sendMessage(messages().render("sell.returned-to-inventory"));
            return;
        }
        storeSession(session, MailEntry.MailReason.LISTING_ROLLBACK);
        player.sendMessage(messages().render("sell.returned-to-mailbox"));
    }

    public void clearAnvilState(Player player) {
        preparedAnvilText.remove(player.getUniqueId());
        SellSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.suspend(false);
        }
    }

    public void onQuit(UUID playerId) {
        remembered.remove(playerId);
        pendingSearch.remove(playerId);
        preparedAnvilText.remove(playerId);
        SellSession session = sessions.remove(playerId);
        if (session != null && !session.consumed()) {
            storeSession(session, MailEntry.MailReason.LISTING_ROLLBACK);
        }
    }

    public void shutdown() {
        for (SellSession session : List.copyOf(sessions.values())) {
            if (!session.consumed()) {
                storeSession(session, MailEntry.MailReason.LISTING_ROLLBACK);
            }
        }
        sessions.clear();
        remembered.clear();
        pendingSearch.clear();
        preparedAnvilText.clear();
    }

    private void renderBrowser(Player player, AuctionBrowserHolder holder, Inventory inventory, ViewState state) {
        remembered.put(player.getUniqueId(), state);
        holder.state(state);
        Page<Auction> page = cache.search(filter(state, pageSize()));
        List<Auction> visible = page.items();
        int[] slots = guiConfig.get().itemSlots();
        holder.clearBindings();
        for (int index = 0; index < slots.length; index++) {
            int slot = slots[index];
            if (index >= visible.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            Auction auction = visible.get(index);
            inventory.setItem(slot, listingIcon(auction));
            holder.bind(slot, (clicker, clickType) -> onListingClick(clicker, auction, clickType));
        }
        for (ItemCategory category : ItemCategory.values()) {
            int slot = guiConfig.get().button("category-" + category.key()).map(GuiConfig.ButtonConfig::slot).orElse(-1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            boolean selected = state.category() == category;
            inventory.setItem(slot, icon("category-" + category.key(), Map.of("selected", mark(selected)), selected));
            holder.bind(slot, (clicker, clickType) -> openBrowser(clicker, state.category(category)));
        }
        bindNavigation(player, holder, inventory, state, page);
    }

    private void renderPlayerListings(Player player, AuctionPlayerHolder holder, Inventory inventory) {
        ViewState state = holder.state();
        Page<Auction> page = cache.listingsOf(holder.targetId(), filter(state, pageSize()));
        List<Auction> visible = page.items();
        int[] slots = guiConfig.get().itemSlots();
        boolean own = holder.targetId().equals(player.getUniqueId());
        holder.clearBindings();
        for (int index = 0; index < slots.length; index++) {
            int slot = slots[index];
            if (index >= visible.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            Auction auction = visible.get(index);
            inventory.setItem(slot, listingIcon(auction));
            holder.bind(slot, (clicker, clickType) -> {
                if (own) {
                    openConfirm(clicker, auction);
                    return;
                }
                onListingClick(clicker, auction, clickType);
            });
        }
        bindNavigation(player, holder, inventory, state, page);
        if (own) {
            int cancelAll = buttonSlot("player-cancel-all", inventory.getSize() - 1);
            boolean noLots = page.total() == 0;
            inventory.setItem(
                    cancelAll,
                    icon("player-cancel-all", Map.of("count", Integer.toString(page.total())), noLots));
            holder.bind(cancelAll, (clicker, clickType) -> cancelAllListings(clicker));
        }
    }

    private void respond(Player player, Result<?> result) {
        Outcome outcome = result.outcome();
        if (outcome != null && !outcome.isSilent()) {
            player.sendMessage(messages().render(outcome.messageKey(), outcome.placeholders()));
        }
        if (result.isSuccess()) {
            playNow(player, outcome == null ? "buy" : soundKey(outcome.messageKey()));
            return;
        }
        playNow(player, "error");
    }

    private String soundKey(String messageKey) {
        int dot = messageKey.indexOf('.');
        return dot < 0 ? messageKey : messageKey.substring(0, dot);
    }

    private void playNow(Player player, String key) {
        Sounds.SoundSpec spec = sounds.get().get(key);
        if (!spec.enabled()) {
            return;
        }
        String name = spec.name();
        float volume = spec.volume();
        float pitch = spec.pitch();
        scheduler.runFor(player, () -> player.playSound(player.getLocation(), name, volume, pitch));
    }

    private void cancelAllListings(Player player) {
        if (!purchaseDebounce.tryAcquire(player.getUniqueId(), "cancel-all")) {
            return;
        }
        scheduler.runAsync(() -> {
            int count = auctions.get().cancelAll(player.getUniqueId(), 200);
            cache.refresh();
            scheduler.runFor(player, () -> {
                if (count > 0) {
                    player.sendMessage(
                            messages().render("cancel.all", Map.of("count", Integer.toString(count))));
                    playNow(player, "cancel");
                } else {
                    player.sendMessage(messages().render("cancel.nothing", Map.of()));
                    playNow(player, "error");
                }
                openPlayerListings(player, player.getUniqueId(), player.getName());
            });
        });
    }

    private void onListingClick(Player player, Auction auction, ClickType clickType) {
        if (clickType == ClickType.RIGHT && preview.isPreviewable(auction.item())) {
            preview.open(player, auction.item(), auction.id());
            return;
        }
        openConfirm(player, auction);
    }

    private void renderConfirm(Player player, AuctionConfirmHolder holder, Auction auction, Inventory inventory) {
        int display = buttonSlot("confirm-item", 11);
        inventory.setItem(display, listingIcon(auction));

        boolean own = auction.isOwn(player.getUniqueId());
        int buy = buttonSlot("confirm-buy", 13);
        inventory.setItem(
                buy,
                icon(
                        own ? "confirm-remove" : "confirm-buy",
                        Map.of(
                                "price", formatter.money(auction.price().amount(), auction.currency().scale()),
                                "currency", auction.currency().displayName(),
                                "seller", auction.sellerName(),
                                "remaining", formatter.remaining(auction.expiresAt(), clock.instant())),
                        false));
        holder.bind(buy, (clicker, clickType) -> {
            if (own) {
                cancel(clicker, auction);
                return;
            }
            purchase(clicker, auction);
        });

        int cancel = buttonSlot("confirm-cancel", 15);
        inventory.setItem(cancel, icon("confirm-cancel", Map.of(), false));
        holder.bind(cancel, (clicker, clickType) -> openBrowser(clicker));

        if (preview.isPreviewable(auction.item())) {
            int previewSlot = buttonSlot("confirm-preview", 4);
            inventory.setItem(previewSlot, icon("confirm-preview", Map.of(), false));
            holder.bind(previewSlot, (clicker, clickType) -> preview.open(clicker, auction.item(), auction.id()));
        }
    }

    private void renderSell(Player player, AuctionSellHolder holder, SellSession session, Inventory inventory) {
        int itemSlot = buttonSlot("sell-item", 11);
        inventory.setItem(itemSlot, ItemSerializer.fromBytes(session.item().rawData()));

        int infoSlot = buttonSlot("sell-info", 15);
        inventory.setItem(
                infoSlot,
                icon(
                        "sell-info",
                        Map.of(
                                "item", displayName(session.item()),
                                "amount", Integer.toString(session.item().amount()),
                                "price", session.price() == null
                                        ? "\u2014"
                                        : formatter.money(session.price().amount(), session.currency().scale()),
                                "currency", session.currency().displayName(),
                                "duration", session.duration() == null ? "\u2014" : session.duration().label()),
                        false));

        List<Currency> available = auctions.get().currencies().available();
        for (int index = 0; index < available.size(); index++) {
            Currency currency = available.get(index);
            int slot = currencySlot(index, inventory.getSize());
            boolean selected = session.currency().id().equals(currency.id());
            inventory.setItem(
                    slot,
                    icon(
                            "currency-" + index,
                            Map.of("currency", currency.displayName(), "symbol", currency.symbol(), "selected", mark(selected)),
                            selected));
            holder.bind(slot, (clicker, clickType) -> {
                session.currency(currency);
                renderSell(clicker, holder, session, inventory);
            });
        }

        List<DurationSpec> durations = auctions.get().rules().durations();
        for (int index = 0; index < durations.size(); index++) {
            DurationSpec duration = durations.get(index);
            int slot = durationSlot(index, inventory.getSize());
            boolean selected = session.duration() != null && session.duration().label().equals(duration.label());
            inventory.setItem(
                    slot,
                    icon("duration", Map.of("duration", duration.label(), "selected", mark(selected)), selected));
            holder.bind(slot, (clicker, clickType) -> {
                session.duration(duration);
                renderSell(clicker, holder, session, inventory);
            });
        }

        int priceSlot = buttonSlot("sell-price", 22);
        inventory.setItem(
                priceSlot,
                icon(
                        "sell-price",
                        Map.of(
                                "price", session.price() == null
                                        ? "\u2014"
                                        : formatter.money(session.price().amount(), session.currency().scale()),
                                "currency", session.currency().symbol()),
                        false));
        holder.bind(priceSlot, (clicker, clickType) -> openPriceInput(clicker));

        int confirmSlot = buttonSlot("sell-confirm", 24);
        inventory.setItem(confirmSlot, icon("sell-confirm", Map.of(), !session.isReady()));
        holder.bind(confirmSlot, (clicker, clickType) -> confirmListing(clicker));

        int cancelSlot = buttonSlot("sell-cancel", 20);
        inventory.setItem(cancelSlot, icon("sell-cancel", Map.of(), false));
        holder.bind(cancelSlot, (clicker, clickType) -> cancelSell(clicker));
    }

    private void renderHistory(Player player, AuctionHistoryHolder holder, Inventory inventory) {
        int perPage = guiConfig.get().itemSlots().length;
        Page<HistoryEntry> page =
                auctions.get().historyPage(holder.viewerId(), holder.filter(), holder.page(), perPage);
        int[] slots = guiConfig.get().itemSlots();
        holder.clearBindings();
        for (int index = 0; index < slots.length; index++) {
            int slot = slots[index];
            if (index >= page.items().size()) {
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, historyIcon(page.items().get(index)));
        }
        for (HistoryFilter filter : HistoryFilter.values()) {
            int slot = guiConfig.get()
                    .button("history-filter-" + filter.key())
                    .map(GuiConfig.ButtonConfig::slot)
                    .orElse(-1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            boolean selected = holder.filter() == filter;
            inventory.setItem(
                    slot,
                    scopedIcon(
                            "history-filter",
                            filter.key(),
                            Map.of(
                                    "filter", messages().raw("gui.history.filter." + filter.key(), filter.key()),
                                    "selected", mark(selected)),
                            selected));
            holder.bind(slot, (clicker, clickType) -> openHistory(clicker, filter, 0));
        }
        int back = buttonSlot("back", "history", inventory.getSize() - 9);
        inventory.setItem(back, icon("back", Map.of(), false));
        holder.bind(back, (clicker, clickType) -> openBrowser(clicker));
        bindPaging(holder, inventory, page.displayPage(), page.totalPages(), page.total(), page.hasPrevious(), page.hasNext(),
                previous -> openHistory(player, holder.filter(), previous),
                next -> openHistory(player, holder.filter(), next));
    }

    private void renderMailbox(Player player, MailboxHolder holder, Inventory inventory) {
        int perPage = guiConfig.get().itemSlots().length;
        List<MailEntry> entries = mail.findUndelivered(player.getUniqueId(), perPage * (holder.page() + 1));
        int from = holder.page() * perPage;
        int[] slots = guiConfig.get().itemSlots();
        holder.clearBindings();
        for (int index = 0; index < slots.length; index++) {
            int slot = slots[index];
            int entryIndex = from + index;
            if (entryIndex >= entries.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            MailEntry entry = entries.get(entryIndex);
            inventory.setItem(slot, mailIcon(entry));
            holder.bind(slot, (clicker, clickType) -> claim(clicker));
        }
        int back = buttonSlot("back", "mailbox", inventory.getSize() - 9);
        inventory.setItem(back, icon("back", Map.of(), false));
        holder.bind(back, (clicker, clickType) -> openBrowser(clicker));
        int claimAll = buttonSlot("mailbox-claim-all", inventory.getSize() - 5);
        inventory.setItem(claimAll, icon("mailbox-claim-all", Map.of("count", Integer.toString(entries.size())), entries.isEmpty()));
        holder.bind(claimAll, (clicker, clickType) -> claimAll(clicker));
        boolean hasNext = entries.size() > from + perPage;
        bindPaging(holder, inventory, holder.page() + 1, Math.max(1, (entries.size() + perPage - 1) / perPage),
                entries.size(), holder.page() > 0, hasNext,
                previous -> openMailbox(player, previous),
                next -> openMailbox(player, next));
    }

    private void bindNavigation(
            Player player, MenuHolder holder, Inventory inventory, ViewState state, Page<Auction> page) {
        int back = buttonSlot("back", inventory.getSize() - 9);
        inventory.setItem(back, icon("back", Map.of(), false));
        holder.bind(back, (clicker, clickType) -> clicker.closeInventory());

        int sell = buttonSlot("sell", inventory.getSize() - 7);
        inventory.setItem(sell, icon("sell", Map.of(), false));
        holder.bind(sell, (clicker, clickType) -> openSell(clicker));

        int mine = buttonSlot("my-listings", inventory.getSize() - 6);
        inventory.setItem(mine, icon("my-listings", Map.of(), false));
        holder.bind(mine, (clicker, clickType) -> openPlayerListings(clicker, clicker.getUniqueId(), clicker.getName()));

        int history = buttonSlot("history", inventory.getSize() - 5);
        inventory.setItem(history, icon("history", Map.of(), false));
        holder.bind(history, (clicker, clickType) -> openHistory(clicker, HistoryFilter.ALL, 0));

        int mailbox = buttonSlot("mailbox", inventory.getSize() - 4);
        inventory.setItem(
                mailbox, icon("mailbox", Map.of("count", Integer.toString(pendingMail(player))), false));
        holder.bind(mailbox, (clicker, clickType) -> openMailbox(clicker, 0));

        int sort = buttonSlot("sort", 8);
        inventory.setItem(sort, icon("sort", Map.of("sort", sortLabel(state.sort())), false));
        holder.bind(sort, (clicker, clickType) -> reopenWith(clicker, holder, state.sort(state.sort().next())));

        int search = buttonSlot("search", 7);
        inventory.setItem(search, icon("search", Map.of(), false));
        holder.bind(search, (clicker, clickType) -> openSearchPrompt(clicker));

        bindPaging(
                holder,
                inventory,
                page.displayPage(),
                page.totalPages(),
                page.total(),
                page.hasPrevious(),
                page.hasNext(),
                previous -> reopenWith(player, holder, state.page(previous)),
                next -> reopenWith(player, holder, state.page(next)));
    }

    private void bindPaging(
            MenuHolder holder,
            Inventory inventory,
            int displayPage,
            int totalPages,
            int total,
            boolean hasPrevious,
            boolean hasNext,
            java.util.function.IntConsumer previous,
            java.util.function.IntConsumer next) {
        int info = buttonSlot("page-info", inventory.getSize() - 5);
        inventory.setItem(
                info,
                icon(
                        "page-info",
                        Map.of(
                                "page", Integer.toString(displayPage),
                                "pages", Integer.toString(totalPages),
                                "total", formatter.count(total)),
                        false));
        int previousSlot = buttonSlot("previous-page", inventory.getSize() - 8);
        inventory.setItem(previousSlot, icon("previous-page", Map.of(), !hasPrevious));
        holder.bind(previousSlot, (clicker, clickType) -> {
            if (hasPrevious) {
                previous.accept(displayPage - 2);
            }
        });
        int nextSlot = buttonSlot("next-page", inventory.getSize() - 2);
        inventory.setItem(nextSlot, icon("next-page", Map.of(), !hasNext));
        holder.bind(nextSlot, (clicker, clickType) -> {
            if (hasNext) {
                next.accept(displayPage);
            }
        });
    }

    private void reopenWith(Player player, MenuHolder holder, ViewState state) {
        if (holder instanceof AuctionPlayerHolder target) {
            AuctionPlayerHolder updated = new AuctionPlayerHolder(target.targetId(), target.targetName(), state);
            open(
                    player,
                    updated,
                    "gui.player.title",
                    Map.of("player", target.targetName()),
                    54,
                    (viewer, inventory) -> renderPlayerListings(viewer, updated, inventory));
            return;
        }
        openBrowser(player, state);
    }

    private void claim(Player player) {
        scheduler.runAsync(() -> {
            int flushed = auctions.get().flushMailbox(player.getUniqueId(), 1);
            scheduler.runFor(player, () -> {
                player.sendMessage(messages().render(
                        flushed > 0 ? "mailbox.claimed" : "mailbox.empty",
                        Map.of("count", Integer.toString(Math.max(flushed, 0)))));
                playNow(player, flushed > 0 ? "claim" : "error");
                openMailbox(player, 0);
            });
        });
    }

    private void claimAll(Player player) {
        scheduler.runAsync(() -> {
            int flushed = auctions.get().flushMailbox(player.getUniqueId(), 54);
            scheduler.runFor(player, () -> {
                player.sendMessage(messages().render("mailbox.claimed", Map.of("count", Integer.toString(flushed))));
                playNow(player, flushed > 0 ? "claim" : "error");
                openMailbox(player, 0);
            });
        });
    }

    private int pendingMail(Player player) {
        try {
            return mail.countUndelivered(player.getUniqueId());
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "cannot count pending mail", ex);
            return 0;
        }
    }

    private void rollbackListing(Player player, SellSession session) {
        if (items.give(player.getUniqueId(), session.item())) {
            player.sendMessage(messages().render("sell.returned-to-inventory"));
            return;
        }
        storeSession(session, MailEntry.MailReason.LISTING_ROLLBACK);
        player.sendMessage(messages().render("sell.returned-to-mailbox"));
    }

    private void storeSession(SellSession session, MailEntry.MailReason reason) {
        try {
            mail.insert(MailEntry.item(session.playerId(), reason, session.item(), 0L, clock.instant()));
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "cannot store the unsold item of " + session.playerId(), ex);
        }
    }

    private void notifyDiscord(String buyerName, Auction auction) {
        DiscordWebhook webhook = discord.get();
        if (webhook == null || !webhook.enabled()) {
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("buyer", buyerName);
        placeholders.put("seller", auction.sellerName());
        placeholders.put("item", displayName(auction.item()));
        placeholders.put("price", formatter.money(auction.price().amount(), auction.currency().scale()));
        placeholders.put("currency", auction.currency().displayName());
        webhook.sendPurchase(placeholders);
    }

    private void announceListing(Auction auction) {
        DiscordWebhook webhook = discord.get();
        if (webhook == null || !webhook.enabled()) {
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", auction.sellerName());
        placeholders.put("item", displayName(auction.item()));
        placeholders.put("price", formatter.money(auction.price().amount(), auction.currency().scale()));
        placeholders.put("currency", auction.currency().displayName());
        webhook.sendNewAuction(placeholders);
    }

    private ItemStack listingIcon(Auction auction) {
        ItemStack base = ItemSerializer.fromBytes(auction.item().rawData());
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("seller", auction.sellerName());
        placeholders.put("price", formatter.money(auction.price().amount(), auction.currency().scale()));
        placeholders.put("currency", auction.currency().displayName());
        placeholders.put("symbol", auction.currency().symbol());
        placeholders.put("remaining", formatter.remaining(auction.expiresAt(), clock.instant()));
        placeholders.put("created", formatter.dateTime(auction.createdAt()));
        placeholders.put("amount", Integer.toString(auction.item().amount()));
        placeholders.put("item", displayName(auction.item()));
        placeholders.put("id", Long.toString(auction.id()));
        List<Component> lore = new ArrayList<>(messages().renderList("gui.listing.lore", placeholders));
        return ItemFactory.display(base, messages().render("gui.listing.name", placeholders), lore);
    }

    private ItemStack historyIcon(HistoryEntry entry) {
        ItemStack base = ItemSerializer.fromBytes(entry.item().rawData());
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("price", formatter.money(entry.price().amount(), entry.price().currency().scale()));
        placeholders.put("currency", entry.price().currency().displayName());
        placeholders.put("date", formatter.dateTime(entry.createdAt()));
        placeholders.put("player", entry.counterpartName().isEmpty() ? "\u2014" : entry.counterpartName());
        placeholders.put("role", messages().raw(
                "gui.history.role." + entry.role().name().toLowerCase(Locale.ROOT), entry.role().name()));
        placeholders.put("status", messages().raw(
                "gui.history.status." + entry.status().name().toLowerCase(Locale.ROOT), entry.status().name()));
        placeholders.put("item", displayName(entry.item()));
        List<Component> lore = new ArrayList<>(messages().renderList("gui.history.lore", placeholders));
        return ItemFactory.display(base, messages().render("gui.history.name", placeholders), lore);
    }

    private ItemStack mailIcon(MailEntry entry) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("date", formatter.dateTime(entry.createdAt()));
        placeholders.put("reason", messages().raw(
                "gui.mailbox.reason." + entry.reason().name().toLowerCase(Locale.ROOT), entry.reason().name()));
        Optional<AuctionItem> item = entry.item();
        if (item.isPresent()) {
            placeholders.put("item", displayName(item.get()));
            List<Component> lore = new ArrayList<>(messages().renderList("gui.mailbox.item-lore", placeholders));
            return ItemFactory.display(
                    ItemSerializer.fromBytes(item.get().rawData()),
                    messages().render("gui.mailbox.item-name", placeholders),
                    lore);
        }
        Money money = entry.money().orElseThrow();
        placeholders.put("price", formatter.money(money.amount(), money.currency().scale()));
        placeholders.put("currency", money.currency().displayName());
        return ItemFactory.icon(
                "GOLD_NUGGET",
                0,
                messages().render("gui.mailbox.money-name", placeholders),
                messages().renderList("gui.mailbox.money-lore", placeholders),
                false);
    }

    private void open(
            Player player,
            MenuHolder holder,
            String titleKey,
            Map<String, String> placeholders,
            int fallbackSize,
            BiConsumer<Player, Inventory> render) {
        int size = guiConfig.get().menu(holder.menuId(), holder.menuId(), fallbackSize).size();
        Inventory inventory = Bukkit.createInventory(holder, size, messages().render(titleKey, placeholders));
        holder.attach(inventory);
        if (guiConfig.get().fillEnabled()) {
            ItemStack filler = ItemFactory.filler(guiConfig.get().fillMaterial());
            for (int slot = 0; slot < size; slot++) {
                inventory.setItem(slot, filler);
            }
        }
        render.accept(player, inventory);
        player.openInventory(inventory);
    }

    private int pageSize() {
        return Math.min(guiConfig.get().itemSlots().length, auctions.get().rules().browserPageSize());
    }

    private AuctionSearchFilter filter(ViewState state, int perPage) {
        return new AuctionSearchFilter(
                state.query(), null, null, null, null, state.category().key(), state.sort(), state.page(), perPage);
    }

    private int buttonSlot(String id, int fallback) {
        return buttonSlot(id, "", fallback);
    }

    private ItemStack scopedIcon(String id, String scope, Map<String, String> placeholders, boolean dimmed) {
        GuiConfig config = guiConfig.get();
        if (config.button(scope + "-" + id).isPresent()) {
            return icon(scope + "-" + id, placeholders, dimmed);
        }
        return icon(id, placeholders, dimmed);
    }

    private int buttonSlot(String id, String scope, int fallback) {
        GuiConfig config = guiConfig.get();
        Optional<Integer> scoped =
                config.button(scope + "-" + id).map(GuiConfig.ButtonConfig::slot).filter(slot -> slot >= 0);
        if (scoped.isPresent()) {
            return scoped.get();
        }
        return config.button(id)
                .map(GuiConfig.ButtonConfig::slot)
                .filter(slot -> slot >= 0)
                .orElse(fallback);
    }

    private int currencySlot(int index, int size) {
        return Math.min(size - 1, guiConfig.get()
                .button("currency-" + index)
                .map(GuiConfig.ButtonConfig::slot)
                .filter(slot -> slot >= 0)
                .orElse(19 + index));
    }

    private int durationSlot(int index, int size) {
        return Math.min(size - 1, guiConfig.get()
                .button("duration-" + index)
                .map(GuiConfig.ButtonConfig::slot)
                .filter(slot -> slot >= 0)
                .orElse(29 + index));
    }

    private ItemStack icon(String id, Map<String, String> placeholders, boolean dimmed) {
        GuiConfig.ButtonConfig config = guiConfig.get()
                .button(id)
                .orElseGet(() -> new GuiConfig.ButtonConfig(id, "PAPER", 0, id, List.of(), -1, "", false));
        Component name = messages().render("gui.button." + id + ".name", placeholders);
        List<Component> lore = messages().renderList("gui.button." + id + ".lore", placeholders);
        ItemStack stack = ItemFactory.icon(config.material(), config.customModelData(), name, lore, config.glow());
        if (!dimmed) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            Component current = meta.displayName();
            if (current != null) {
                meta.displayName(current.color(NamedTextColor.DARK_GRAY));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Messages messages() {
        return messages.get();
    }

    private Currency defaultCurrency() {
        return catalog.primary().orElseThrow(() -> new IllegalStateException("no currencies are configured"));
    }

    private Optional<String> denyReason(AuctionItem item, Player player) {
        BlacklistPolicy policy = blacklist.get();
        if (policy == null) {
            return Optional.empty();
        }
        if (!policy.bypassPermission().isBlank() && player.hasPermission(policy.bypassPermission())) {
            return Optional.empty();
        }
        return policy.denyReason(new BlacklistPolicy.Subject(
                item.material(), item.displayName(), item.loreText(), items.tags(item)));
    }

    public String sortLabel(SortType sort) {
        return messages().raw("gui.sort." + sort.name().toLowerCase(Locale.ROOT), sort.name());
    }

    public static String displayName(AuctionItem item) {
        return item.displayName().isEmpty() ? item.material() : item.displayName();
    }

    private static String mark(boolean selected) {
        return selected ? "\u2714" : "";
    }

    static String stripTags(String text) {
        return text.replaceAll("(?i)\u00A7[0-9A-FK-ORX]", "").replaceAll("<[^>]+>", "").trim();
    }
}
