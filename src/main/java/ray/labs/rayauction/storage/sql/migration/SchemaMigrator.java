package ray.labs.rayauction.storage.sql.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import javax.sql.DataSource;

import ray.labs.rayauction.storage.DatabaseType;
import ray.labs.rayauction.storage.StorageException;

public final class SchemaMigrator {

    private static final String HISTORY_TABLE = "rayauction_schema_history";
    private static final List<String> KNOWN_VERSIONS = List.of("1", "2", "3");

    private final DataSource dataSource;
    private final DatabaseType type;

    public SchemaMigrator(DataSource dataSource, DatabaseType type) {
        this.dataSource = dataSource;
        this.type = type;
    }

    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            ensureHistoryTable(connection);
            Map<String, Long> applied = appliedVersions(connection);
            for (Migration migration : loadMigrations()) {
                Long checksum = applied.get(migration.version());
                if (checksum != null) {
                    if (checksum.longValue() != migration.checksum()) {
                        throw new StorageException("checksum mismatch for migration V"
                                + migration.version()
                                + " (database="
                                + checksum
                                + ", plugin="
                                + migration.checksum()
                                + "). Schema was edited outside of migrations.");
                    }
                    continue;
                }
                apply(connection, migration);
            }
        } catch (SQLException ex) {
            throw new StorageException("schema migration failed for " + type.id(), ex);
        }
    }

    private void ensureHistoryTable(Connection connection) throws SQLException {
        String ddl = switch (type) {
            case H2, POSTGRESQL -> "CREATE TABLE IF NOT EXISTS "
                    + HISTORY_TABLE
                    + " (version VARCHAR(16) PRIMARY KEY, checksum BIGINT NOT NULL, script VARCHAR(255) NOT NULL,"
                    + " installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";
            case MYSQL -> "CREATE TABLE IF NOT EXISTS "
                    + HISTORY_TABLE
                    + " (version VARCHAR(16) PRIMARY KEY, checksum BIGINT NOT NULL, script VARCHAR(255) NOT NULL,"
                    + " installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB"
                    + " DEFAULT CHARSET=utf8mb4";
        };
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private Map<String, Long> appliedVersions(Connection connection) throws SQLException {
        Map<String, Long> applied = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT version, checksum FROM " + HISTORY_TABLE)) {
            while (set.next()) {
                applied.put(set.getString(1), set.getLong(2));
            }
        }
        return applied;
    }

    private List<Migration> loadMigrations() {
        List<Migration> migrations = new ArrayList<>();
        for (String version : KNOWN_VERSIONS) {
            String path = type.migrationPath() + "/V" + version + "__" + scriptName(version) + ".sql";
            String sql = readResource(path);
            migrations.add(new Migration(version, path, sql, checksum(sql)));
        }
        migrations.sort(Comparator.comparingInt(migration -> Integer.parseInt(migration.version())));
        return List.copyOf(migrations);
    }

    private String scriptName(String version) {
        return switch (version) {
            case "1" -> "initial_schema";
            case "2" -> "mailbox_and_ledger";
            case "3" -> "search_indexes";
            default -> throw new StorageException("unknown migration version " + version);
        };
    }

    private String readResource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new StorageException("missing migration resource " + path);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (IOException ex) {
            throw new StorageException("cannot read migration " + path, ex);
        }
    }

    private void apply(Connection connection, Migration migration) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            for (String query : split(migration.sql())) {
                try {
                    statement.execute(query);
                } catch (SQLException ex) {
                    if (!isDuplicateIndex(ex)) {
                        throw ex;
                    }
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO "
                + HISTORY_TABLE
                + " (version, checksum, script) VALUES (?, ?, ?)")) {
            insert.setString(1, migration.version());
            insert.setLong(2, migration.checksum());
            insert.setString(3, migration.path());
            insert.executeUpdate();
        }
        connection.commit();
        connection.setAutoCommit(autoCommit);
    }

    private boolean isDuplicateIndex(SQLException ex) {
        return type == DatabaseType.MYSQL && ex.getErrorCode() == 1061;
    }

    static List<String> split(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : sql.split("\n")) {
            String line = stripComment(rawLine);
            if (line.isBlank()) {
                continue;
            }
            current.append(line).append(' ');
            if (line.trim().endsWith(";")) {
                String statement = current.toString().trim();
                statements.add(statement.substring(0, statement.length() - 1).trim());
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail.endsWith(";") ? tail.substring(0, tail.length() - 1).trim() : tail);
        }
        return statements;
    }

    private static String stripComment(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("--")) {
            return "";
        }
        int index = trimmed.indexOf("--");
        return index >= 0 ? trimmed.substring(0, index) : trimmed;
    }

    static long checksum(String sql) {
        CRC32 crc = new CRC32();
        crc.update(sql.replaceAll("\\s+", " ").trim().getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    record Migration(String version, String path, String sql, long checksum) {}
}
