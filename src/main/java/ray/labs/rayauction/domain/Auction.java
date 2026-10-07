package ray.labs.rayauction.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Auction(
        long id,
        UUID sellerId,
        String sellerName,
        AuctionItem item,
        Money price,
        Instant createdAt,
        Instant expiresAt,
        AuctionStatus status,
        UUID buyerId,
        int version) {

    public Auction {
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(sellerName, "sellerName");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
    }

    public static Auction active(
            long id,
            UUID sellerId,
            String sellerName,
            AuctionItem item,
            Money price,
            Instant createdAt,
            Instant expiresAt) {
        return new Auction(id, sellerId, sellerName, item, price, createdAt, expiresAt, AuctionStatus.ACTIVE, null, 0);
    }

    public Currency currency() {
        return price.currency();
    }

    public boolean isActive() {
        return status == AuctionStatus.ACTIVE;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isOwn(UUID playerId) {
        return sellerId.equals(playerId);
    }

    public Auction withStatus(AuctionStatus newStatus, UUID buyer) {
        return new Auction(id, sellerId, sellerName, item, price, createdAt, expiresAt, newStatus, buyer, version);
    }

    public Auction withVersion(int newVersion) {
        return new Auction(id, sellerId, sellerName, item, price, createdAt, expiresAt, status, buyerId, newVersion);
    }
}
