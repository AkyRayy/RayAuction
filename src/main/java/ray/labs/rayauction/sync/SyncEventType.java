package ray.labs.rayauction.sync;

import java.util.Locale;
import java.util.Optional;

public enum SyncEventType {
    AUCTION_CREATED,
    AUCTION_PURCHASED,
    AUCTION_CANCELLED,
    AUCTION_EXPIRED,
    LIMIT_CHANGED,
    BALANCE_CHANGED;

    public String wireName() {
        return name();
    }

    public static Optional<SyncEventType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (SyncEventType type : values()) {
            if (type.name().equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
