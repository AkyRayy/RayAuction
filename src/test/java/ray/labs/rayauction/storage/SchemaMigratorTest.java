package ray.labs.rayauction.storage;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import ray.labs.rayauction.storage.sql.migration.SchemaMigrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaMigratorTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final class MemoryDataSource implements DataSource {

        private final String url;

        private MemoryDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private MemoryDataSource newDatabase() {
        return new MemoryDataSource("jdbc:h2:mem:rayauction_test_" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
    }

    @Test
    void createsAllTablesOnH2() throws SQLException {
        DataSource dataSource = newDatabase();

        new SchemaMigrator(dataSource, DatabaseType.H2).migrate();

        List<String> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                ResultSet result = connection.getMetaData().getTables(null, null, "RAYAUCTION_%", null)) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
            }
        }
        assertThat(tables)
                .contains(
                        "rayauction_auctions",
                        "rayauction_transactions",
                        "rayauction_limits",
                        "rayauction_mailbox",
                        "rayauction_ledger",
                        "rayauction_sync_events",
                        "rayauction_schema_history");
    }

    @Test
    void migrationIsIdempotent() throws SQLException {
        DataSource dataSource = newDatabase();
        SchemaMigrator migrator = new SchemaMigrator(dataSource, DatabaseType.H2);

        migrator.migrate();
        migrator.migrate();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM rayauction_schema_history")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    void detectsChecksumTampering() throws SQLException {
        DataSource dataSource = newDatabase();
        SchemaMigrator migrator = new SchemaMigrator(dataSource, DatabaseType.H2);
        migrator.migrate();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE rayauction_schema_history SET checksum = 12345 WHERE version = '1'");
        }

        assertThatThrownBy(migrator::migrate)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    void auctionTableAcceptsRowAndSupportsOptimisticLock() throws SQLException {
        DataSource dataSource = newDatabase();
        new SchemaMigrator(dataSource, DatabaseType.H2).migrate();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO rayauction_auctions (seller_id, seller_name, item_data, item_material, item_name,"
                            + " item_lore, item_category, item_amount, price, currency, created_at, expires_at,"
                            + " status, buyer_id, version) VALUES ('p1', 'Seller', X'00', 'diamond', 'Diamond',"
                            + " '', 'rare', 1, 100.00, 'vault', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
                            + " 'ACTIVE', NULL, 0)");

            statement.executeUpdate(
                    "INSERT INTO rayauction_auctions (seller_id, seller_name, item_data, item_material, item_name,"
                            + " item_lore, item_category, item_amount, price, currency, created_at, expires_at,"
                            + " status, buyer_id, version) VALUES ('p2', 'Seller', X'00', 'diamond', 'Diamond',"
                            + " '', 'rare', 1, 1234.5678, 'coinsengine:coins', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
                            + " 'ACTIVE', NULL, 0)");

            try (ResultSet result =
                    statement.executeQuery("SELECT price FROM rayauction_auctions WHERE seller_id = 'p2'")) {
                result.next();
                assertThat(result.getBigDecimal(1).toPlainString()).isEqualTo("1234.5678");
            }

            int updated = statement.executeUpdate(
                    "UPDATE rayauction_auctions SET status = 'SOLD', version = version + 1"
                            + " WHERE id = 1 AND status = 'ACTIVE' AND version = 0");
            assertThat(updated).isEqualTo(1);

            int staleUpdate = statement.executeUpdate(
                    "UPDATE rayauction_auctions SET status = 'CANCELLED', version = version + 1"
                            + " WHERE id = 1 AND status = 'ACTIVE' AND version = 0");
            assertThat(staleUpdate).isZero();
        }
    }
}
