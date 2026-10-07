package ray.labs.rayauction.storage;

import java.util.Locale;
import java.util.Optional;

public enum DatabaseType {
    H2("h2", "org.h2.Driver", "jdbc:h2:"),
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql:"),
    POSTGRESQL("postgresql", "org.postgresql.Driver", "jdbc:postgresql:");

    private final String id;
    private final String driverClass;
    private final String urlPrefix;

    DatabaseType(String id, String driverClass, String urlPrefix) {
        this.id = id;
        this.driverClass = driverClass;
        this.urlPrefix = urlPrefix;
    }

    public String id() {
        return id;
    }

    public String driverClass() {
        return driverClass;
    }

    public String urlPrefix() {
        return urlPrefix;
    }

    public String migrationPath() {
        return "migration/" + id;
    }

    public boolean isEmbedded() {
        return this == H2;
    }

    public static Optional<DatabaseType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (DatabaseType type : values()) {
            if (type.id.equals(value) || type.name().toLowerCase(Locale.ROOT).equals(value)) {
                return Optional.of(type);
            }
        }
        if (value.equals("mariadb")) {
            return Optional.of(MYSQL);
        }
        if (value.equals("postgres") || value.equals("pg")) {
            return Optional.of(POSTGRESQL);
        }
        return Optional.empty();
    }
}
