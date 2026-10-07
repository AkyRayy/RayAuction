package ray.labs.rayauction.storage;

import java.util.Optional;
import java.util.UUID;

public interface LimitRepository {

    Optional<Integer> find(UUID playerId);

    void set(UUID playerId, Integer limit);

    void add(UUID playerId, int baseLimit, int amount);

    void take(UUID playerId, int baseLimit, int amount);

    void reset(UUID playerId);
}
