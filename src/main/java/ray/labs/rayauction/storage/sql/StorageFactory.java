package ray.labs.rayauction.storage.sql;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariDataSource;
import ray.labs.rayauction.config.DatabaseConfig;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.storage.Storage;
import ray.labs.rayauction.storage.StorageException;
import ray.labs.rayauction.storage.sql.migration.SchemaMigrator;

public final class StorageFactory {

    private StorageFactory() {}

    public static Storage open(DatabaseConfig config, CurrencyCatalog catalog, Path dataFolder, Logger logger) {
        HikariDataSource dataSource;
        try {
            dataSource = DataSourceFactory.create(config, dataFolder);
        } catch (StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new StorageException("cannot start the connection pool", ex);
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.isValid(5);
        } catch (SQLException ex) {
            dataSource.close();
            throw new StorageException("database is not reachable: " + ex.getMessage(), ex);
        }
        new SchemaMigrator(dataSource, config.type()).migrate();
        SqlUnitOfWork unitOfWork = new SqlUnitOfWork(dataSource, logger);
        return new Storage(
                dataSource,
                unitOfWork,
                new SqlAuctionRepository(unitOfWork, catalog),
                new SqlTransactionRepository(unitOfWork, catalog),
                new SqlLimitRepository(unitOfWork),
                new SqlMailRepository(unitOfWork, catalog),
                new SqlLedgerRepository(unitOfWork),
                new SqlStatsRepository(unitOfWork),
                new SqlSyncEventRepository(unitOfWork),
                logger);
    }
}
