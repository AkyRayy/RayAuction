package ray.labs.rayauction.sync;

import java.util.UUID;

import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.Transaction;
import ray.labs.rayauction.domain.port.EventPublisher;
import ray.labs.rayauction.storage.cache.AuctionCache;

public final class SqlEventPublisher implements EventPublisher {

    private final SyncService sync;
    private final String serverId;
    private final AuctionCache cache;
    private final boolean enabled;

    private SqlEventPublisher(SyncService sync, String serverId, AuctionCache cache, boolean enabled) {
        this.sync = sync;
        this.serverId = serverId;
        this.cache = cache;
        this.enabled = enabled;
    }

    public static SqlEventPublisher local(AuctionCache cache) {
        return new SqlEventPublisher(null, "local", cache, false);
    }

    public static SqlEventPublisher multiServer(PollingSyncService service, AuctionCache cache, String serverId) {
        return new SqlEventPublisher(service, serverId, cache, true);
    }

    public SyncService syncService() {
        return sync;
    }

    public String serverId() {
        return serverId;
    }

    public boolean isMultiServer() {
        return enabled;
    }

    @Override
    public void auctionCreated(Auction auction) {
        cache.put(auction);
        if (enabled) {
            sync.publish(SyncEvent.of(SyncEventType.AUCTION_CREATED, SyncPayload.ofAuction(auction), serverId));
        }
    }

    @Override
    public void auctionPurchased(Transaction transaction) {
        cache.remove(transaction.auctionId());
        if (enabled) {
            sync.publish(SyncEvent.of(
                    SyncEventType.AUCTION_PURCHASED,
                    SyncPayload.ofPurchase(transaction.auctionId(), transaction.buyerId(), transaction.price()),
                    serverId));
        }
    }

    @Override
    public void auctionCancelled(Auction auction) {
        cache.remove(auction.id());
        if (enabled) {
            sync.publish(SyncEvent.of(SyncEventType.AUCTION_CANCELLED, SyncPayload.ofId(auction.id()), serverId));
        }
    }

    @Override
    public void auctionExpired(Auction auction) {
        cache.remove(auction.id());
        if (enabled) {
            sync.publish(SyncEvent.of(SyncEventType.AUCTION_EXPIRED, SyncPayload.ofId(auction.id()), serverId));
        }
    }

    @Override
    public void limitChanged(UUID playerId, int limit) {
        if (enabled) {
            sync.publish(SyncEvent.of(SyncEventType.LIMIT_CHANGED, SyncPayload.ofLimit(playerId, limit), serverId));
        }
    }
}
