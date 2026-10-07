package ray.labs.rayauction.sync;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SyncEvent(long id, SyncEventType type, Map<String, String> payload, String serverId, Instant createdAt) {

    public SyncEvent {
        Objects.requireNonNull(type, "type");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static SyncEvent of(SyncEventType type, Map<String, String> payload, String serverId) {
        return new SyncEvent(0L, type, payload, serverId, Instant.now());
    }

    public String required(String key) {
        String value = payload.get(key);
        if (value == null) {
            throw new IllegalStateException("sync event " + type + " misses payload key " + key);
        }
        return value;
    }

    public String optional(String key, String fallback) {
        return payload.getOrDefault(key, fallback);
    }

    public long requiredLong(String key) {
        return Long.parseLong(required(key));
    }
}
