package ray.labs.rayauction.config;

public record MultiServerConfig(
        boolean enabled,
        String serverId,
        int pollIntervalSeconds,
        int batchSize,
        int retentionMinutes,
        int failureThreshold,
        int breakerCooldownSeconds) {

    public MultiServerConfig {
        serverId = serverId == null || serverId.isBlank() ? "server-1" : serverId.trim();
        pollIntervalSeconds = Math.max(1, pollIntervalSeconds);
        batchSize = Math.max(1, Math.min(1000, batchSize));
        retentionMinutes = Math.max(1, retentionMinutes);
        failureThreshold = Math.max(1, failureThreshold);
        breakerCooldownSeconds = Math.max(5, breakerCooldownSeconds);
    }

    public static MultiServerConfig from(YamlNode node) {
        return new MultiServerConfig(
                node.bool("enabled", false),
                node.string("server-id", "server-1"),
                node.integer("poll-interval-seconds", 5),
                node.integer("batch-size", 100),
                node.integer("event-retention-minutes", 60),
                node.integer("failure-threshold", 3),
                node.integer("breaker-cooldown-seconds", 300));
    }
}
