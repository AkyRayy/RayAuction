package ray.labs.rayauction.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MailEntry(
        long id,
        UUID playerId,
        MailReason reason,
        AuctionItem itemPayload,
        Money moneyPayload,
        long auctionId,
        Instant createdAt,
        boolean delivered) {

    public MailEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        if (itemPayload == null && moneyPayload == null) {
            throw new IllegalArgumentException("mail entry requires an item or money");
        }
    }

    public static MailEntry item(UUID playerId, MailReason reason, AuctionItem item, long auctionId, Instant createdAt) {
        return new MailEntry(0L, playerId, reason, item, null, auctionId, createdAt, false);
    }

    public static MailEntry money(UUID playerId, MailReason reason, Money money, long auctionId, Instant createdAt) {
        return new MailEntry(0L, playerId, reason, null, money, auctionId, createdAt, false);
    }

    public Optional<AuctionItem> item() {
        return Optional.ofNullable(itemPayload);
    }

    public Optional<Money> money() {
        return Optional.ofNullable(moneyPayload);
    }

    public enum MailReason {
        PURCHASE_INCOME,
        EXPIRED_RETURN,
        CANCELLED_RETURN,
        DELIVERY_OVERFLOW,
        LISTING_ROLLBACK,
        ADMIN_COMPENSATION
    }
}
