package ray.labs.rayauction.storage;

import java.util.List;
import java.util.UUID;

import ray.labs.rayauction.domain.MailEntry;

public interface MailRepository {

    long insert(MailEntry entry);

    List<MailEntry> findUndelivered(UUID playerId, int limit);

    boolean markDelivered(long id);

    int countUndelivered(UUID playerId);

    void purgeDeliveredOlderThan(long epochSeconds);
}
