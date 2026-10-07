package ray.labs.rayauction.placeholder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ray.labs.rayauction.domain.AuctionStats;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.domain.LimitService;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.platform.Scheduler;
import ray.labs.rayauction.storage.StatsRepository;
import ray.labs.rayauction.storage.cache.AuctionCache;
import ray.labs.rayauction.text.TextFormatter;

public final class RayAuctionExpansion extends PlaceholderExpansion {

    private final String version;
    private final AuctionCache cache;
    private final StatsRepository stats;
    private final LimitService limits;
    private final CurrencyCatalog catalog;
    private final TextFormatter formatter;
    private final Scheduler scheduler;
    private final long refreshMillis;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);
    private final Cache<UUID, PlayerStats> playerStats = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();
    private Scheduler.TaskHandle task;

    public RayAuctionExpansion(
            String version,
            AuctionCache cache,
            StatsRepository stats,
            LimitService limits,
            CurrencyCatalog catalog,
            TextFormatter formatter,
            Scheduler scheduler,
            int refreshSeconds) {
        this.version = version;
        this.cache = cache;
        this.stats = stats;
        this.limits = limits;
        this.catalog = catalog;
        this.formatter = formatter;
        this.scheduler = scheduler;
        this.refreshMillis = Duration.ofSeconds(Math.max(5, refreshSeconds)).toMillis();
    }

    public void start() {
        scheduler.runAsync(this::refresh);
        task = scheduler.runAsyncTimer(this::refresh, refreshMillis, refreshMillis);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        playerStats.invalidateAll();
    }

    @Override
    public String getIdentifier() {
        return "rayauction";
    }

    @Override
    public String getAuthor() {
        return "RayLabs";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT);
        Snapshot current = snapshot.get();
        UUID playerId = player == null ? null : player.getUniqueId();
        return switch (key) {
            case "active_auctions" -> formatter.count(current.activeAuctions());
            case "top_seller_name" -> current.topSeller().name();
            case "top_seller_amount" -> formatter.count((int) current.topSeller().amount());
            case "top_buyer_name" -> current.topBuyer().name();
            case "top_buyer_amount" -> formatter.count((int) current.topBuyer().amount());
            case "cheapest_item" -> current.cheapestName();
            case "cheapest_price" -> current.cheapestPrice();
            case "player_sales" -> playerId == null ? "0" : formatter.count(cache.activeOf(playerId));
            case "player_sold" -> playerId == null ? "0" : formatter.count((int) statsOf(playerId).sold());
            case "player_bought" -> playerId == null ? "0" : formatter.count((int) statsOf(playerId).bought());
            case "player_limit" -> playerId == null ? "0" : formatLimit(limits.limitOf(playerId));
            case "player_limit_remaining" -> playerId == null ? "0" : formatLimit(limits.remaining(playerId));
            case "player_earned" -> playerId == null ? "0" : totals(statsOf(playerId).earned());
            case "player_spent" -> playerId == null ? "0" : totals(statsOf(playerId).spent());
            default -> null;
        };
    }

    private PlayerStats statsOf(UUID playerId) {
        PlayerStats cached = playerStats.getIfPresent(playerId);
        return cached == null ? PlayerStats.EMPTY : cached;
    }

    private String formatLimit(int limit) {
        return limit < 0 ? "\u221E" : formatter.count(limit);
    }

    private String totals(Map<String, BigDecimal> values) {
        StringBuilder builder = new StringBuilder();
        for (Currency currency : catalog.currencies()) {
            BigDecimal value = values.getOrDefault(currency.id(), BigDecimal.ZERO);
            if (value.signum() == 0) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" / ");
            }
            builder.append(formatter.money(value, currency.scale())).append(' ').append(currency.symbol());
        }
        return builder.isEmpty() ? "0" : builder.toString();
    }

    private void refresh() {
        try {
            AuctionStats.Leader topSeller = stats.topSeller();
            AuctionStats.Leader topBuyer = stats.topBuyer();
            Optional<Money> cheapest = cache.cheapestPrice();
            String cheapestName = cache.cheapest()
                    .map(auction -> auction.item().displayName().isEmpty()
                            ? auction.item().material()
                            : auction.item().displayName())
                    .orElse("");
            snapshot.set(new Snapshot(
                    cache.size(), topSeller, topBuyer, cheapestName, cheapest.map(this::format).orElse("")));
            for (UUID playerId : onlineIds()) {
                playerStats.put(playerId, load(playerId));
            }
        } catch (RuntimeException ignored) {
            snapshot.set(Snapshot.EMPTY);
        }
    }

    private Collection<UUID> onlineIds() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
    }

    private PlayerStats load(UUID playerId) {
        AuctionStats values = stats.stats(playerId);
        java.util.Map<String, BigDecimal> earned = new java.util.LinkedHashMap<>();
        java.util.Map<String, BigDecimal> spent = new java.util.LinkedHashMap<>();
        for (Currency currency : catalog.currencies()) {
            earned.put(currency.id(), stats.earnedIn(playerId, currency.id()));
            spent.put(currency.id(), stats.spentIn(playerId, currency.id()));
        }
        return new PlayerStats(values.totalSold(), values.totalBought(), Map.copyOf(earned), Map.copyOf(spent));
    }

    private String format(Money money) {
        return formatter.money(money.amount(), money.currency().scale()) + " " + money.currency().symbol();
    }

    private record Snapshot(
            int activeAuctions,
            AuctionStats.Leader topSeller,
            AuctionStats.Leader topBuyer,
            String cheapestName,
            String cheapestPrice) {

        private static final Snapshot EMPTY =
                new Snapshot(0, AuctionStats.Leader.NONE, AuctionStats.Leader.NONE, "", "");
    }

    record PlayerStats(long sold, long bought, Map<String, BigDecimal> earned, Map<String, BigDecimal> spent) {

        static final PlayerStats EMPTY = new PlayerStats(0L, 0L, Map.of(), Map.of());
    }
}
