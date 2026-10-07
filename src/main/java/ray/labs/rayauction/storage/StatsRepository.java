package ray.labs.rayauction.storage;

import java.math.BigDecimal;
import java.util.UUID;

import ray.labs.rayauction.domain.AuctionStats;

public interface StatsRepository {

    AuctionStats stats(UUID playerId);

    BigDecimal earnedIn(UUID playerId, String currencyId);

    BigDecimal spentIn(UUID playerId, String currencyId);

    long countSales(UUID playerId);

    long countPurchases(UUID playerId);

    AuctionStats.Leader topSeller();

    AuctionStats.Leader topBuyer();
}
