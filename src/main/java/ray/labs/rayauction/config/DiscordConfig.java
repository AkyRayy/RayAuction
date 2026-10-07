package ray.labs.rayauction.config;

import java.util.List;

public record DiscordConfig(
        boolean enabled,
        String webhookUrl,
        boolean notifyOnNewAuction,
        boolean notifyOnPurchase,
        boolean notifyOnExpiry,
        String username,
        String avatarUrl,
        int timeoutSeconds,
        int failureThreshold,
        int cooldownSeconds,
        Embed newAuction,
        Embed purchase) {

    public record Embed(String title, String description, String color, List<String> fields) {

        public Embed {
            title = title == null ? "" : title;
            description = description == null ? "" : description;
            color = color == null || color.isBlank() ? "#5865F2" : color.trim();
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        public int colorValue() {
            String value = color.startsWith("#") ? color.substring(1) : color;
            try {
                return Integer.parseInt(value, 16) & 0xFFFFFF;
            } catch (NumberFormatException ex) {
                return 0x5865F2;
            }
        }

        static Embed from(YamlNode node) {
            return new Embed(
                    node.string("title", ""),
                    node.string("description", ""),
                    node.string("color", "#5865F2"),
                    node.stringList("fields", List.of()));
        }
    }

    public DiscordConfig {
        webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        username = username == null ? "" : username.trim();
        avatarUrl = avatarUrl == null ? "" : avatarUrl.trim();
        timeoutSeconds = Math.max(1, timeoutSeconds);
        failureThreshold = Math.max(1, failureThreshold);
        cooldownSeconds = Math.max(5, cooldownSeconds);
    }

    public boolean usable() {
        return enabled && webhookUrl.startsWith("http");
    }

    public static DiscordConfig from(YamlNode node) {
        YamlNode embeds = node.section("embeds");
        return new DiscordConfig(
                node.bool("enabled", false),
                node.string("webhook-url", ""),
                node.section("notify-on").bool("new-auction", true),
                node.section("notify-on").bool("purchase", true),
                node.section("notify-on").bool("expiry", false),
                node.string("username", "RayAuction"),
                node.string("avatar-url", ""),
                node.integer("timeout-seconds", 5),
                node.integer("failure-threshold", 3),
                node.integer("cooldown-seconds", 300),
                Embed.from(embeds.section("new-auction")),
                Embed.from(embeds.section("purchase")));
    }
}
