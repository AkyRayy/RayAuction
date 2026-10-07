package ray.labs.rayauction;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import ray.labs.rayauction.command.AuctionAdminCommand;
import ray.labs.rayauction.command.AuctionCommand;
import ray.labs.rayauction.config.BlacklistConfig;
import ray.labs.rayauction.config.CommandsConfig;
import ray.labs.rayauction.config.GuiConfig;
import ray.labs.rayauction.config.Messages;
import ray.labs.rayauction.config.PluginConfig;
import ray.labs.rayauction.config.YamlNode;
import ray.labs.rayauction.discord.DiscordWebhook;
import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.AuctionRules;
import ray.labs.rayauction.domain.AuctionService;
import ray.labs.rayauction.domain.AuctionValidator;
import ray.labs.rayauction.domain.BlacklistPolicy;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.domain.CurrencyService;
import ray.labs.rayauction.domain.LimitService;
import ray.labs.rayauction.domain.PlayerLimit;
import ray.labs.rayauction.domain.Transaction;
import ray.labs.rayauction.domain.port.BalancePort;
import ray.labs.rayauction.domain.port.DeliveryPort;
import ray.labs.rayauction.domain.port.EventPublisher;
import ray.labs.rayauction.economy.EconomyFactory;
import ray.labs.rayauction.economy.EconomyProvider;
import ray.labs.rayauction.gui.GuiService;
import ray.labs.rayauction.gui.ShulkerPreviewService;
import ray.labs.rayauction.listener.AuctionListener;
import ray.labs.rayauction.listener.ChatListener;
import ray.labs.rayauction.listener.PlayerListener;
import ray.labs.rayauction.placeholder.RayAuctionExpansion;
import ray.labs.rayauction.platform.FoliaScheduler;
import ray.labs.rayauction.platform.MaterialClassifier;
import ray.labs.rayauction.platform.PaperDeliveryPort;
import ray.labs.rayauction.platform.PaperItemGateway;
import ray.labs.rayauction.platform.PaperOnlinePort;
import ray.labs.rayauction.platform.PaperScheduler;
import ray.labs.rayauction.platform.Scheduler;
import ray.labs.rayauction.storage.Storage;
import ray.labs.rayauction.storage.cache.AuctionCache;
import ray.labs.rayauction.storage.sql.StorageFactory;
import ray.labs.rayauction.sync.PollingSyncService;
import ray.labs.rayauction.sync.SqlEventPublisher;
import ray.labs.rayauction.sync.SyncService;
import ray.labs.rayauction.text.TextFormatter;
import ray.labs.rayauction.util.UUIDUtil;

public final class RayAuctionPlugin extends JavaPlugin {

    private final AtomicReference<Runtime> runtime = new AtomicReference<>();
    private final List<Scheduler.TaskHandle> timers = new ArrayList<>();
    private final AtomicLong nextSweepNanos = new AtomicLong(0L);
    private Scheduler scheduler;
    private Storage storage;
    private ExecutorService httpExecutor;
    private RayAuctionExpansion expansion;

    @Override
    public void onEnable() {
        try {
            scheduler = FoliaScheduler.isAvailable() ? new FoliaScheduler(this) : new PaperScheduler(this);
            httpExecutor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "RayAuction-HTTP");
                thread.setDaemon(true);
                return thread;
            });
            runtime.set(buildRuntime());
            registerListeners();
            registerCommands();
            registerPlaceholders();
            startTimers();
            Runtime current = runtime.get();
            getLogger().info("RayAuction enabled ("
                    + (scheduler.isFolia() ? "Folia" : "Paper")
                    + ", storage=" + current.config().database().type().id()
                    + ", currencies=" + current.catalog().ids()
                    + ", sync=" + (current.syncService() == null ? "off" : current.config().multiServer().serverId())
                    + ")");
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "RayAuction failed to start", ex);
            shutdownRuntime();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        shutdownRuntime();
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
    }

    private Runtime buildRuntime() {
        Path dataFolder = getDataFolder().toPath();
        List<String> created = new ArrayList<>();
        PluginConfig.installDefaults(dataFolder, created);
        if (!created.isEmpty()) {
            getLogger().info("created default configuration files: " + String.join(", ", created));
        }
        PluginConfig config = PluginConfig.load(dataFolder);
        Messages messages = Messages.load(dataFolder.resolve("messages.yml"));
        GuiConfig guiConfig = GuiConfig.from(YamlNode.load(dataFolder.resolve("gui.yml")));
        BlacklistConfig blacklist = BlacklistConfig.load(dataFolder.resolve("blacklist.yml"));
        CurrencyCatalog catalog = config.economy().catalog();
        if (storage == null) {
            storage = StorageFactory.open(config.database(), catalog, dataFolder, getLogger());
        }
        MaterialClassifier classifier = new MaterialClassifier();
        PaperItemGateway itemGateway = new PaperItemGateway(classifier);
        PaperOnlinePort onlinePort = new PaperOnlinePort(scheduler);
        List<EconomyProvider> providers =
                EconomyFactory.providers(config.economy(), storage.ledger(), onlinePort, getLogger());
        List<BalancePort> ports = EconomyFactory.ports(providers);
        CurrencyService currencyService = new CurrencyService(catalog, ports);
        AuctionCache cache = new AuctionCache(
                storage.auctions(), config.cache().maximumSize(), config.cache().sqlFallbackThreshold());
        AuctionValidator validator = new AuctionValidator(config.auction().rules());
        AtomicReference<EventPublisher> publisherRef = new AtomicReference<>(NoopPublisher.INSTANCE);
        DeliveryPort delivery = new PaperDeliveryPort(
                scheduler,
                itemGateway,
                storage.mail(),
                config.auction().rules().expiredDelivery() == AuctionRules.ExpiredDelivery.INVENTORY,
                Clock.systemUTC(),
                getLogger());
        AuctionService auctionService = new AuctionService(
                storage.auctions(),
                storage.transactions(),
                storage.mail(),
                storage.ledger(),
                storage.unitOfWork(),
                currencyService,
                validator,
                new DelegatingPublisher(publisherRef),
                delivery,
                this::resolveAccount,
                Clock.systemUTC(),
                getLogger());
        LimitService limitService = new LimitService(
                storage.limits(),
                storage.auctions(),
                config.auction().rules(),
                new DelegatingPublisher(publisherRef),
                this::permissionLimit);
        TextFormatter formatter = new TextFormatter(Locale.forLanguageTag(config.locale()), ZoneId.systemDefault());
        ShulkerPreviewService preview = new ShulkerPreviewService(itemGateway, messages, guiConfig);
        DiscordWebhook webhook = config.discord().usable()
                ? new DiscordWebhook(config.discord(), httpExecutor, getLogger())
                : null;
        GuiService guiService = new GuiService(
                () -> runtime.get().auctionService(),
                () -> runtime.get().limitService(),
                cache,
                storage.mail(),
                preview,
                itemGateway,
                catalog,
                scheduler,
                formatter,
                Clock.systemUTC(),
                getLogger(),
                () -> runtime.get().guiConfig(),
                () -> runtime.get().messages(),
                () -> runtime.get().discord(),
                () -> runtime.get().blacklist(),
                () -> runtime.get().config().sounds(),
                config.auction().rules().purchaseCooldownMillis());
        PollingSyncService syncService = null;
        if (config.multiServer().enabled()) {
            syncService = new PollingSyncService(
                    storage.syncEvents(),
                    cache,
                    storage.auctions(),
                    auctionService,
                    catalog,
                    config.multiServer(),
                    scheduler,
                    getLogger());
            publisherRef.set(SqlEventPublisher.multiServer(syncService, cache, config.multiServer().serverId()));
            syncService.addListener(event -> guiService.refreshOpenMenus());
            syncService.start();
        } else {
            publisherRef.set(SqlEventPublisher.local(cache));
        }
        cache.load();
        return new Runtime(
                config,
                messages,
                guiConfig,
                blacklist.policy(),
                catalog,
                currencyService,
                auctionService,
                limitService,
                cache,
                guiService,
                webhook,
                syncService,
                formatter,
                providers);
    }

    private void startTimers() {
        cancelTimers();
        timers.add(scheduler.runAsyncTimer(this::sweepExpired, 5_000L, 5_000L));
        timers.add(scheduler.runAsyncTimer(this::refreshCache, 30_000L, 30_000L));
        timers.add(scheduler.runAsyncTimer(this::purgeMailbox, TimeUnit.HOURS.toMillis(6), TimeUnit.HOURS.toMillis(6)));
    }

    private void cancelTimers() {
        for (Scheduler.TaskHandle timer : timers) {
            timer.cancel();
        }
        timers.clear();
    }

    private void sweepExpired() {
        Runtime current = runtime.get();
        if (current == null) {
            return;
        }
        long now = System.nanoTime();
        if (now < nextSweepNanos.get()) {
            return;
        }
        long interval = current.config().auction().rules().expiryCheckInterval().toNanos();
        nextSweepNanos.set(now + interval);
        try {
            current.auctionService().sweepExpired(current.config().auction().expiryBatchSize());
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "expiry sweep failed", ex);
        }
    }

    private void refreshCache() {
        Runtime current = runtime.get();
        if (current == null || current.syncService() != null) {
            return;
        }
        try {
            current.cache().refresh();
        } catch (RuntimeException ex) {
            getLogger().log(Level.WARNING, "cache refresh failed", ex);
        }
    }

    private void purgeMailbox() {
        Runtime current = runtime.get();
        if (current == null) {
            return;
        }
        try {
            long cutoff = Clock.systemUTC().instant().minusSeconds(TimeUnit.DAYS.toSeconds(7)).getEpochSecond();
            storage.mail().purgeDeliveredOlderThan(cutoff);
        } catch (RuntimeException ex) {
            getLogger().log(Level.WARNING, "mailbox purge failed", ex);
        }
    }

    private void registerListeners() {
        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new AuctionListener(() -> runtime.get().guiService()), this);
        plugins.registerEvents(
                new PlayerListener(
                        () -> runtime.get().auctionService(),
                        () -> runtime.get().guiService(),
                        () -> runtime.get().config(),
                        () -> runtime.get().messages(),
                        scheduler,
                        getLogger()),
                this);
        plugins.registerEvents(new ChatListener(() -> runtime.get().guiService(), scheduler), this);
    }

    private void registerCommands() {
        AuctionCommand auctionCommand =
                new AuctionCommand(() -> runtime.get().guiService(), () -> runtime.get().messages());
        AuctionAdminCommand adminCommand = new AuctionAdminCommand(
                () -> this::reload,
                () -> runtime.get().limitService(),
                () -> runtime.get().auctionService(),
                () -> runtime.get().currencyService(),
                () -> runtime.get().guiService(),
                () -> runtime.get().messages(),
                () -> runtime.get().cache(),
                () -> storage.stats(),
                () -> runtime.get().formatter(),
                scheduler);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            Runtime current = runtime.get();
            CommandsConfig commands = current.config().commands();
            registrar.register(
                    auctionCommand.node(),
                    current.messages().raw("command.auction.description", "Auction house"),
                    commands.auctionAliases());
            registrar.register(
                    adminCommand.node(),
                    current.messages().raw("command.admin.description", "Auction house administration"),
                    commands.adminAliases());
        });
    }

    private void registerPlaceholders() {
        PluginManager plugins = getServer().getPluginManager();
        if (plugins.getPlugin("PlaceholderAPI") == null || !runtime.get().config().placeholder().enabled()) {
            return;
        }
        try {
            Runtime current = runtime.get();
            RayAuctionExpansion candidate = new RayAuctionExpansion(
                    getPluginMeta().getVersion(),
                    current.cache(),
                    storage.stats(),
                    current.limitService(),
                    current.catalog(),
                    current.formatter(),
                    scheduler,
                    current.config().placeholder().refreshSeconds());
            if (candidate.register()) {
                candidate.start();
                expansion = candidate;
                getLogger().info("PlaceholderAPI expansion registered");
                return;
            }
            getLogger().warning("PlaceholderAPI refused the RayAuction expansion registration");
        } catch (RuntimeException | LinkageError ex) {
            getLogger().log(Level.WARNING, "cannot register the PlaceholderAPI expansion", ex);
        }
    }

    boolean reload(CommandSender sender) {
        Runtime previous = runtime.get();
        try {
            if (previous != null) {
                previous.guiService().closeAll();
            }
            Runtime rebuilt = buildRuntime();
            runtime.set(rebuilt);
            if (previous != null && previous.syncService() != null && previous.syncService() != rebuilt.syncService()) {
                previous.syncService().stop();
            }
            if (expansion != null) {
                expansion.stop();
                expansion.unregister();
                expansion = null;
            }
            registerPlaceholders();
            startTimers();
            return true;
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "reload failed, keeping the previous configuration", ex);
            if (runtime.get() == null) {
                runtime.set(previous);
            }
            return false;
        }
    }

    private void shutdownRuntime() {
        cancelTimers();
        Runtime current = runtime.getAndSet(null);
        if (expansion != null) {
            expansion.stop();
            expansion = null;
        }
        if (current != null) {
            try {
                current.guiService().shutdown();
                current.guiService().closeAll();
            } catch (RuntimeException ex) {
                getLogger().log(Level.WARNING, "cannot close open menus", ex);
            }
            if (current.syncService() != null) {
                current.syncService().stop();
            }
        }
        if (scheduler != null) {
            scheduler.cancelTasks();
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (RuntimeException ex) {
                getLogger().log(Level.WARNING, "cannot close the storage", ex);
            }
            storage = null;
        }
    }

    private Optional<UUID> resolveAccount(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getUniqueId() != null) {
            return Optional.of(cached.getUniqueId());
        }
        return Optional.of(UUIDUtil.offlineId(name));
    }

    private int permissionLimit(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return 0;
        }
        int best = 0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String permission = info.getPermission();
            if (!permission.startsWith("rayauction.limit.") || !info.getValue()) {
                continue;
            }
            String suffix = permission.substring("rayauction.limit.".length());
            if (suffix.equalsIgnoreCase("unlimited")) {
                return PlayerLimit.UNLIMITED;
            }
            try {
                best = Math.max(best, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return best;
    }

    public record Runtime(
            PluginConfig config,
            Messages messages,
            GuiConfig guiConfig,
            BlacklistPolicy blacklist,
            CurrencyCatalog catalog,
            CurrencyService currencyService,
            AuctionService auctionService,
            LimitService limitService,
            AuctionCache cache,
            GuiService guiService,
            DiscordWebhook discord,
            SyncService syncService,
            TextFormatter formatter,
            List<EconomyProvider> providers) {}

    private record DelegatingPublisher(AtomicReference<EventPublisher> delegate) implements EventPublisher {

        @Override
        public void auctionCreated(Auction auction) {
            delegate.get().auctionCreated(auction);
        }

        @Override
        public void auctionPurchased(Transaction transaction) {
            delegate.get().auctionPurchased(transaction);
        }

        @Override
        public void auctionCancelled(Auction auction) {
            delegate.get().auctionCancelled(auction);
        }

        @Override
        public void auctionExpired(Auction auction) {
            delegate.get().auctionExpired(auction);
        }

        @Override
        public void limitChanged(UUID playerId, int limit) {
            delegate.get().limitChanged(playerId, limit);
        }
    }

    private enum NoopPublisher implements EventPublisher {
        INSTANCE;

        @Override
        public void auctionCreated(Auction auction) {}

        @Override
        public void auctionPurchased(Transaction transaction) {}

        @Override
        public void auctionCancelled(Auction auction) {}

        @Override
        public void auctionExpired(Auction auction) {}

        @Override
        public void limitChanged(UUID playerId, int limit) {}
    }
}
