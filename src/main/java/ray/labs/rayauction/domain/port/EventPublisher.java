package ray.labs.rayauction.domain.port;

import java.util.UUID;

import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.Transaction;

public interface EventPublisher {

    void auctionCreated(Auction auction);

    void auctionPurchased(Transaction transaction);

    void auctionCancelled(Auction auction);

    void auctionExpired(Auction auction);

    void limitChanged(UUID playerId, int limit);
}
