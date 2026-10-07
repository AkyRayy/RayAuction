package ray.labs.rayauction.domain;

import java.util.Locale;
import java.util.Optional;

public enum ItemCategory {
    ALL,
    WEAPONS,
    ARMOR,
    BLOCKS,
    FOOD,
    RARE,
    OTHER;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean matches(String categoryName) {
        return this == ALL || key().equals(Money.normalizeId(categoryName));
    }

    public static Optional<ItemCategory> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (ItemCategory category : values()) {
            if (category.name().equals(value)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
