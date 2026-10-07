package ray.labs.rayauction.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import ray.labs.rayauction.config.MultiServerConfig;
import ray.labs.rayauction.domain.AuctionService;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.platform.Scheduler;
import ray.labs.rayauction.storage.AuctionRepository;
import ray.labs.rayauction.storage.SyncEventRepository;
import ray.labs.rayauction.storage.cache.AuctionCache;

public final class PollingSyncService implements SyncService {

    private final SyncEventRepository events;
    private final AuctionCache cache;
    private final AuctionRepository auctions;
    private final AuctionService auctionService;
    private final CurrencyCatalog catalog;
    private final MultiServerConfig config;
    private final Scheduler scheduler;
    private final Logger logger;

    private final List<SyncListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong lastSeenId = new AtomicLong(0L);
    private final AtomicInteger failures = new AtomicInteger(0);
    private final AtomicLong breakerOpenedAt = new AtomicLong(0L);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Scheduler.TaskHandle> tasks = new ArrayList<>();

    public PollingSyncService(
            SyncEventRepository events,
            AuctionCache cache,
            AuctionRepository auctions,
            AuctionService auctionService,
            CurrencyCatalog catalog,
            MultiServerConfig config,
            Scheduler scheduler,
            Logger logger) {
        this.events = events;
        this.cache = cache;
        this.auctions = auctions;
        this.auctionService = auctionService;
        this.catalog = catalog;
        this.config = config;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        lastSeenId.set(events.maxId());
        long interval = TimeUnit.SECONDS.toMillis(config.pollIntervalSeconds());
        tasks.add(scheduler.runAsyncTimer(this::poll, interval, interval));
        long retention = TimeUnit.MINUTES.toMillis(config.retentionMinutes());
        tasks.add(scheduler.runAsyncTimer(this::purge, retention, retention));
        tasks.add(scheduler.runAsyncTimer(this::refreshCache, interval * 4L, interval * 4L));
        logger.info("multi-server sync started for server-id " + config.serverId() + " (poll every "
                + config.pollIntervalSeconds() + "s)");
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (Scheduler.TaskHandle task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    @Override
    public boolean isActive() {
        return running.get() && breakerOpenedAt.get() == 0L;
    }

    @Override
    public void publish(SyncEvent event) {
        if (!running.get()) {
            return;
        }
        try {
            events.insert(event);
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "cannot publish sync event " + event.type(), ex);
        }
    }

    @Override
    public void addListener(SyncListener listener) {
        listeners.add(listener);
    }

    private void poll() {
        if (!running.get()) {
            return;
        }
        if (isCoolingDown()) {
            return;
        }
        try {
            List<SyncEvent> batch = events.fetchAfter(lastSeenId.get(), config.serverId(), config.batchSize());
            List<Long> processed = new ArrayList<>(batch.size());
            for (SyncEvent event : batch) {
                apply(event);
                processed.add(event.id());
                lastSeenId.accumulateAndGet(event.id(), Math::max);
            }
            if (!processed.isEmpty()) {
                events.markProcessed(processed);
            }
            if (failures.get() != 0) {
                failures.set(0);
                auctionService.readOnly(false);
                logger.info("database connection restored, leaving read-only mode");
            }
        } catch (RuntimeException ex) {
            onFailure(ex);
        }
    }

    private void purge() {
        if (!running.get()) {
            return;
        }
        try {
            long cutoff = Instant.now().minusSeconds(TimeUnit.MINUTES.toSeconds(config.retentionMinutes())).getEpochSecond();
            int removed = events.purgeOlderThan(cutoff);
            if (removed > 0) {
                logger.fine(() -> "purged " + removed + " sync events");
            }
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "cannot purge sync events", ex);
        }
    }

    private void refreshCache() {
        if (!running.get()) {
            return;
        }
        try {
            cache.refresh();
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "periodic cache refresh failed", ex);
        }
    }

    private void apply(SyncEvent event) {
        switch (event.type()) {
            case AUCTION_CREATED -> cache.put(SyncPayload.auction(event, catalog));
            case AUCTION_PURCHASED, AUCTION_CANCELLED, AUCTION_EXPIRED -> {
                long id = event.requiredLong("id");
                cache.remove(id);
                auctions.findById(id).ifPresent(cache::put);
            }
            case LIMIT_CHANGED, BALANCE_CHANGED -> {}
        }
        for (SyncListener listener : listeners) {
            try {
                listener.onRemoteChange(event);
            } catch (RuntimeException ex) {
                logger.log(Level.WARNING, "sync listener failed for " + event.type(), ex);
            }
        }
    }

    private void onFailure(RuntimeException ex) {
        int count = failures.incrementAndGet();
        if (count >= config.failureThreshold() && breakerOpenedAt.compareAndSet(0L, System.nanoTime())) {
            auctionService.readOnly(true);
            logger.log(Level.SEVERE, "database unreachable, auction switched to read-only for "
                    + config.breakerCooldownSeconds() + "s", ex);
            return;
        }
        logger.log(Level.WARNING, "sync poll failed (" + count + " consecutive)", ex);
    }

    private boolean isCoolingDown() {
        long openedAt = breakerOpenedAt.get();
        if (openedAt == 0L) {
            return false;
        }
        long elapsedNanos = System.nanoTime() - openedAt;
        if (elapsedNanos < TimeUnit.SECONDS.toNanos(config.breakerCooldownSeconds())) {
            return true;
        }
        breakerOpenedAt.set(0L);
        failures.set(0);
        return false;
    }
}
