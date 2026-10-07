package ray.labs.rayauction.domain;

public enum AuctionStatus {
    ACTIVE,
    SOLD,
    CANCELLED,
    EXPIRED,
    RETURNED;

    public boolean isTerminal() {
        return this != ACTIVE;
    }

    public static AuctionStatus parse(String raw) {
        for (AuctionStatus status : values()) {
            if (status.name().equalsIgnoreCase(raw)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown auction status: " + raw);
    }
}
