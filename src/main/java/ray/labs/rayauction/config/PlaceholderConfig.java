package ray.labs.rayauction.config;

public record PlaceholderConfig(boolean enabled, int refreshSeconds, int topSize) {

    public PlaceholderConfig {
        refreshSeconds = Math.max(5, refreshSeconds);
        topSize = Math.max(1, Math.min(100, topSize));
    }

    public static PlaceholderConfig from(YamlNode node) {
        return new PlaceholderConfig(
                node.bool("enabled", true),
                node.integer("refresh-seconds", 60),
                node.integer("top-size", 1));
    }
}
