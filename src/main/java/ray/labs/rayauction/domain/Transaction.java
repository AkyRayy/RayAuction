package ray.labs.rayauction.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Transaction(
        long id,
        long auctionId,
        UUID buyerId,
        String buyerName,
        UUID sellerId,
        String sellerName,
        AuctionItem item,
        Money price,
        Money tax,
        Instant createdAt) {

    public Transaction {
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(tax, "tax");
        Objects.requireNonNull(createdAt, "createdAt");
        buyerName = buyerName == null ? "" : buyerName;
        sellerName = sellerName == null ? "" : sellerName;
    }

    public static Transaction of(Auction auction, UUID buyerId, String buyerName, Money tax, Instant createdAt) {
        return new Transaction(
                0L,
                auction.id(),
                buyerId,
                buyerName,
                auction.sellerId(),
                auction.sellerName(),
                auction.item(),
                auction.price(),
                tax,
                createdAt);
    }

    public Money sellerReceives() {
        return price.subtract(tax);
    }

    public String counterpartName(UUID viewerId) {
        return viewerId.equals(buyerId) ? sellerName : buyerName;
    }

    public UUID counterpartId(UUID viewerId) {
        return viewerId.equals(buyerId) ? sellerId : buyerId;
    }

    public HistoryEntry toHistory(UUID viewerId) {
        return new HistoryEntry(
                id,
                auctionId,
                viewerId,
                viewerId.equals(buyerId) ? HistoryEntry.HistoryRole.BUYER : HistoryEntry.HistoryRole.SELLER,
                counterpartId(viewerId),
                counterpartName(viewerId),
                item,
                price,
                tax,
                AuctionStatus.SOLD,
                createdAt);
    }
}
