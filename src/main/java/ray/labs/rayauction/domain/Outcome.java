package ray.labs.rayauction.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record Outcome(String messageKey, Map<String, String> placeholders) {

    public Outcome {
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
    }

    public static Outcome of(String messageKey) {
        return new Outcome(messageKey, Map.of());
    }

    public static Builder builder(String messageKey) {
        return new Builder(messageKey);
    }

    public boolean isSilent() {
        return messageKey == null || messageKey.isBlank();
    }

    public static final class Builder {

        private final String messageKey;
        private final Map<String, String> placeholders = new LinkedHashMap<>();

        private Builder(String messageKey) {
            this.messageKey = messageKey;
        }

        public Builder with(String key, String value) {
            placeholders.put(key, value == null ? "" : value);
            return this;
        }

        public Outcome build() {
            return new Outcome(messageKey, placeholders);
        }
    }
}
