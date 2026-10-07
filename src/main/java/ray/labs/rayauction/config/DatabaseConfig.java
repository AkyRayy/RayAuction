package ray.labs.rayauction.config;

import ray.labs.rayauction.storage.DatabaseType;

public record DatabaseConfig(
        DatabaseType type,
        String host,
        int port,
        String database,
        String username,
        String password,
        String file,
        String jdbcUrl,
        boolean useSsl,
        int maxPoolSize,
        int minIdle,
        int connectionTimeoutSeconds,
        int maxLifetimeMinutes,
        int keepaliveSeconds) {

    public DatabaseConfig {
        host = host == null || host.isBlank() ? "localhost" : host.trim();
        database = database == null ? "" : database.trim();
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        file = file == null || file.isBlank() ? "data/auctions" : file.trim();
        jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
        maxPoolSize = Math.max(1, maxPoolSize);
        minIdle = Math.max(0, minIdle);
        connectionTimeoutSeconds = Math.max(1, connectionTimeoutSeconds);
        maxLifetimeMinutes = Math.max(1, maxLifetimeMinutes);
        keepaliveSeconds = Math.max(30, keepaliveSeconds);
    }

    public static DatabaseConfig from(YamlNode node) {
        String typeName = node.string("type", "h2");
        DatabaseType type = DatabaseType.parse(typeName)
                .orElseThrow(() -> new ConfigException(
                        node.path() + ".type must be one of h2, mysql, postgresql; got: " + typeName));
        return new DatabaseConfig(
                type,
                node.string("host", "localhost"),
                node.integer("port", type == DatabaseType.POSTGRESQL ? 5432 : 3306),
                node.string("database", "minecraft"),
                node.string("username", "root"),
                node.string("password", ""),
                node.string("file", "data/auctions"),
                node.string("jdbc-url", ""),
                node.bool("use-ssl", false),
                node.integer("max-pool-size", 10),
                node.integer("min-idle", 2),
                node.integer("connection-timeout-seconds", 10),
                node.integer("max-lifetime-minutes", 30),
                node.integer("keepalive-seconds", 120));
    }
}
