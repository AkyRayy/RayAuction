package ray.labs.rayauction.storage.cache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.AuctionSearchFilter;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.domain.Page;
import ray.labs.rayauction.domain.SortType;
import ray.labs.rayauction.storage.AuctionRepository;

public final class AuctionCache {

    private final AuctionRepository repository;
    private final Cache<Long, Auction> entries;
    private final int sqlFallbackThreshold;

    public AuctionCache(AuctionRepository repository, int maximumSize, int sqlFallbackThreshold) {
        this.repository = repository;
        this.sqlFallbackThreshold = Math.max(0, sqlFallbackThreshold);
        this.entries = Caffeine.newBuilder()
                .maximumSize(Math.max(64, maximumSize))
                .build();
    }

    public void load() {
        entries.invalidateAll();
        ConcurrentMap<Long, Auction> map = entries.asMap();
        for (Auction auction : repository.findActive()) {
            map.put(auction.id(), auction);
        }
    }

    public void refresh() {
        List<Auction> active = repository.findActive();
        ConcurrentMap<Long, Auction> map = entries.asMap();
        Set<Long> live = new HashSet<>();
        for (Auction auction : active) {
            live.add(auction.id());
            map.put(auction.id(), auction);
        }
        map.keySet().removeIf(id -> !live.contains(id));
    }

    public void put(Auction auction) {
        if (auction.isActive()) {
            entries.put(auction.id(), auction);
        } else {
            entries.invalidate(auction.id());
        }
    }

    public void remove(long id) {
        entries.invalidate(id);
    }

    public Optional<Auction> find(long id) {
        Auction cached = entries.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Auction> loaded = repository.findById(id);
        loaded.ifPresent(this::put);
        return loaded;
    }

    public int size() {
        entries.cleanUp();
        return (int) entries.estimatedSize();
    }

    public List<Auction> snapshot() {
        return List.copyOf(entries.asMap().values());
    }

    public int activeOf(UUID sellerId) {
        int count = 0;
        for (Auction auction : entries.asMap().values()) {
            if (auction.isOwn(sellerId)) {
                count++;
            }
        }
        return count;
    }

    public Page<Auction> search(AuctionSearchFilter filter) {
        if (sqlFallbackThreshold > 0 && size() > sqlFallbackThreshold) {
            return repository.search(filter);
        }
        List<Auction> matched = new ArrayList<>();
        for (Auction auction : entries.asMap().values()) {
            if (filter.matches(auction)) {
                matched.add(auction);
            }
        }
        matched.sort(comparator(filter.sort()));
        return Page.of(matched, filter.page(), filter.perPage());
    }

    public Page<Auction> listingsOf(UUID sellerId, AuctionSearchFilter filter) {
        List<Auction> matched = new ArrayList<>();
        for (Auction auction : entries.asMap().values()) {
            if (auction.isOwn(sellerId) && filter.matches(auction)) {
                matched.add(auction);
            }
        }
        matched.sort(comparator(filter.sort()));
        return Page.of(matched, filter.page(), filter.perPage());
    }

    public Optional<Auction> cheapest() {
        Auction cheapest = null;
        for (Auction auction : entries.asMap().values()) {
            if (cheapest == null || PRICE_ORDER.compare(auction, cheapest) < 0) {
                cheapest = auction;
            }
        }
        return Optional.ofNullable(cheapest);
    }

    public Optional<Money> cheapestPrice() {
        return cheapest().map(Auction::price);
    }

    private static final Comparator<Auction> PRICE_ORDER =
            Comparator.comparing((Auction auction) -> auction.price().amount())
                    .thenComparing(auction -> auction.price().currency().id())
                    .thenComparingLong(Auction::id);

    static Comparator<Auction> comparator(SortType sort) {
        Comparator<Auction> comparator = switch (sort) {
            case DATE_ASC -> Comparator.comparing(Auction::createdAt).thenComparingLong(Auction::id);
            case DATE_DESC -> Comparator.comparing(Auction::createdAt).reversed().thenComparingLong(Auction::id);
            case PRICE_ASC -> PRICE_ORDER;
            case PRICE_DESC -> PRICE_ORDER.reversed();
        };
        return comparator;
    }
}
