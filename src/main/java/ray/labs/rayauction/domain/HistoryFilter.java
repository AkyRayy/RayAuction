package ray.labs.rayauction.domain;

import java.util.Locale;
import java.util.Optional;

public enum HistoryFilter {
    ALL,
    SOLD,
    BOUGHT,
    EXPIRED,
    CANCELLED,
    RETURNED;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean matches(HistoryEntry entry) {
        return switch (this) {
            case ALL -> true;
            case SOLD -> entry.role() == HistoryEntry.HistoryRole.SELLER && entry.status() == AuctionStatus.SOLD;
            case BOUGHT -> entry.role() == HistoryEntry.HistoryRole.BUYER && entry.status() == AuctionStatus.SOLD;
            case EXPIRED -> entry.status() == AuctionStatus.EXPIRED;
            case CANCELLED -> entry.status() == AuctionStatus.CANCELLED;
            case RETURNED -> entry.status() == AuctionStatus.RETURNED;
        };
    }

    public static Optional<HistoryFilter> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (HistoryFilter filter : values()) {
            if (filter.name().equals(value)) {
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }
}
