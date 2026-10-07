package ray.labs.rayauction.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record AuctionStats(
        int activeListings,
        long totalSold,
        long totalBought,
        BigDecimal earned,
        BigDecimal spent) {

    public static final AuctionStats EMPTY = new AuctionStats(0, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);

    public AuctionStats {
        earned = earned == null ? BigDecimal.ZERO : earned;
        spent = spent == null ? BigDecimal.ZERO : spent;
    }

    public int remainingSlots(int limit) {
        if (limit == PlayerLimit.UNLIMITED) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, limit - activeListings);
    }

    public record Leader(UUID playerId, String name, long amount) {

        public static final Leader NONE = new Leader(new UUID(0L, 0L), "", 0L);

        public boolean isPresent() {
            return amount > 0 && !name.isEmpty();
        }
    }

    public record Cheapest(String itemName, Money price, UUID sellerId) {

        public static boolean isEmpty(Cheapest cheapest) {
            return cheapest == null;
        }
    }
}
