package ray.labs.rayauction.domain.port;

import java.util.Optional;
import java.util.UUID;

public interface OnlinePort {

    boolean isOnline(UUID playerId);

    long currentExperience(UUID playerId);

    void setExperience(UUID playerId, long totalExperience);

    Optional<String> name(UUID playerId);
}
