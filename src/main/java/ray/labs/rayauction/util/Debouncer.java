package ray.labs.rayauction.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Debouncer {

    private final Map<String, Long> stamps = new ConcurrentHashMap<>();
    private final long windowMillis;
    private final Clock clock;

    public interface Clock {

        long millis();
    }

    public Debouncer(long windowMillis, Clock clock) {
        this.windowMillis = Math.max(0L, windowMillis);
        this.clock = clock;
    }

    public boolean tryAcquire(UUID playerId, String action) {
        return tryAcquire(playerId.toString() + ':' + action);
    }

    public boolean tryAcquire(String key) {
        long now = clock.millis();
        Long previous = stamps.put(key, now);
        if (previous == null) {
            return true;
        }
        if (now - previous >= windowMillis) {
            return true;
        }
        stamps.put(key, previous);
        return false;
    }

    public void release(UUID playerId, String action) {
        stamps.remove(playerId.toString() + ':' + action);
    }

    public void purgeOlderThan(long maxAgeMillis) {
        long now = clock.millis();
        stamps.values().removeIf(stamp -> now - stamp > maxAgeMillis);
    }

    public void clear() {
        stamps.clear();
    }

    public int size() {
        return stamps.size();
    }
}
