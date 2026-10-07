package ray.labs.rayauction.storage.sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ray.labs.rayauction.config.DatabaseConfig;
import ray.labs.rayauction.storage.DatabaseType;
import ray.labs.rayauction.storage.StorageException;

public final class DataSourceFactory {

    private DataSourceFactory() {}

    public static HikariDataSource create(DatabaseConfig config, Path dataFolder) {
        DatabaseType type = config.type();
        ensureDriver(type);
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("RayAuction-" + type.id());
        hikari.setJdbcUrl(jdbcUrl(config, type, dataFolder));
        hikari.setMaximumPoolSize(Math.max(1, config.maxPoolSize()));
        hikari.setMinimumIdle(Math.min(Math.max(0, config.minIdle()), Math.max(1, config.maxPoolSize())));
        hikari.setConnectionTimeout(TimeUnit.SECONDS.toMillis(Math.max(1, config.connectionTimeoutSeconds())));
        hikari.setMaxLifetime(TimeUnit.MINUTES.toMillis(Math.max(1, config.maxLifetimeMinutes())));
        hikari.setKeepaliveTime(TimeUnit.SECONDS.toMillis(Math.max(30, config.keepaliveSeconds())));
        hikari.setAutoCommit(true);
        hikari.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        if (!type.isEmbedded()) {
            hikari.setUsername(config.username());
            hikari.setPassword(config.password());
        }
        hikari.addDataSourceProperty("characterEncoding", "utf8");
        hikari.addDataSourceProperty("useUnicode", "true");
        return new HikariDataSource(hikari);
    }

    public static String jdbcUrl(DatabaseConfig config, DatabaseType type, Path dataFolder) {
        if (!config.jdbcUrl().isBlank()) {
            return config.jdbcUrl();
        }
        return switch (type) {
            case H2 -> {
                Path file = dataFolder.resolve(config.file());
                Path parent = file.getParent();
                if (parent != null) {
                    try {
                        Files.createDirectories(parent);
                    } catch (java.io.IOException ex) {
                        throw new StorageException("cannot create database directory " + parent, ex);
                    }
                }
                yield "jdbc:h2:file:"
                        + file.toAbsolutePath()
                        + ";MODE=LEGACY;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE;LOCK_TIMEOUT=10000";
            }
            case MYSQL -> "jdbc:mysql://"
                    + config.host()
                    + ":"
                    + config.port()
                    + "/"
                    + config.database()
                    + "?useSSL="
                    + config.useSsl()
                    + "&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8&autoReconnect=true";
            case POSTGRESQL -> "jdbc:postgresql://"
                    + config.host()
                    + ":"
                    + config.port()
                    + "/"
                    + config.database()
                    + (config.useSsl() ? "?sslmode=require" : "?sslmode=disable");
        };
    }

    private static void ensureDriver(DatabaseType type) {
        try {
            Class.forName(type.driverClass());
            return;
        } catch (ClassNotFoundException ignored) {
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                if (drivers.nextElement().getClass().getName().equals(type.driverClass())) {
                    return;
                }
            }
        }
        throw new StorageException("JDBC driver for " + type.id() + " is missing: " + type.driverClass());
    }
}
