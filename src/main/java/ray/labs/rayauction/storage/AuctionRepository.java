package ray.labs.rayauction.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.AuctionSearchFilter;
import ray.labs.rayauction.domain.AuctionStatus;
import ray.labs.rayauction.domain.Page;

public interface AuctionRepository extends AutoCloseable {

    long insert(Auction auction);

    Optional<Auction> findById(long id);

    List<Auction> findActive();

    Page<Auction> search(AuctionSearchFilter filter);

    Page<Auction> findBySeller(UUID sellerId, AuctionStatus status, AuctionSearchFilter filter);

    List<Auction> findExpiring(Instant until, int limit);

    List<Auction> findTerminal(UUID sellerId, int limit);

    int transition(long id, int expectedVersion, AuctionStatus targetStatus, UUID buyerId);

    void markReturned(long id);

    int countActive(UUID sellerId);

    int countByStatus(AuctionStatus status);

    @Override
    void close();
}
