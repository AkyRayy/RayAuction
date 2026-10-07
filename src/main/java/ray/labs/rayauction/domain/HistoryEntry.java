package ray.labs.rayauction.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoryEntry(
        long id,
        long auctionId,
        UUID playerId,
        HistoryRole role,
        UUID counterpartId,
        String counterpartName,
        AuctionItem item,
        Money price,
        Money tax,
        AuctionStatus status,
        Instant createdAt) {

    public HistoryEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        counterpartName = counterpartName == null ? "" : counterpartName;
        tax = tax == null ? Money.zero(price.currency()) : tax;
    }

    public enum HistoryRole {
        BUYER,
        SELLER
    }
}
