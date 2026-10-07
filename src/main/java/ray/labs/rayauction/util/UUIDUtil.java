package ray.labs.rayauction.util;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class UUIDUtil {

    private UUIDUtil() {}

    public static String write(UUID id) {
        return id.toString();
    }

    public static Optional<UUID> read(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static UUID offlineId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID deterministic(String namespace, String value) {
        return UUID.nameUUIDFromBytes((namespace + ':' + value).getBytes(StandardCharsets.UTF_8));
    }
}
