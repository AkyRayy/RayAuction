package ray.labs.rayauction.domain;

public enum SortType {
    DATE_ASC,
    DATE_DESC,
    PRICE_ASC,
    PRICE_DESC;

    public SortType next() {
        SortType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static SortType parse(String raw) {
        for (SortType type : values()) {
            if (type.name().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return DATE_DESC;
    }
}
