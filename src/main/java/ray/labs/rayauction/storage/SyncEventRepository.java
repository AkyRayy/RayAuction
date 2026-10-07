package ray.labs.rayauction.storage;

import java.util.List;

import ray.labs.rayauction.sync.SyncEvent;

public interface SyncEventRepository {

    long insert(SyncEvent event);

    List<SyncEvent> fetchAfter(long lastSeenId, String excludingServerId, int limit);

    List<SyncEvent> fetchUnprocessed(String excludingServerId, int limit);

    void markProcessed(List<Long> ids);

    int purgeOlderThan(long epochSeconds);

    long maxId();
}
