package ray.labs.rayauction.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.HistoryEntry;
import ray.labs.rayauction.domain.Transaction;

public interface TransactionRepository extends AutoCloseable {

    long insert(Transaction transaction);

    Optional<Transaction> findById(long id);

    boolean existsForAuction(long auctionId);

    List<Transaction> findRecent(int limit);

    List<HistoryEntry> historyOf(UUID playerId, int limit);

    @Override
    void close();
}
