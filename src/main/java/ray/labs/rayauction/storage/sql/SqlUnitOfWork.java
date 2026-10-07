package ray.labs.rayauction.storage.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import ray.labs.rayauction.storage.Connections;
import ray.labs.rayauction.storage.StorageException;
import ray.labs.rayauction.storage.UnitOfWork;

public final class SqlUnitOfWork implements UnitOfWork, Connections {

    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

    private final DataSource dataSource;
    private final Logger logger;

    public SqlUnitOfWork(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public static Connection currentConnection() {
        return CURRENT.get();
    }

    @Override
    public <T> T call(Supplier<T> action) {
        Connection existing = CURRENT.get();
        if (existing != null) {
            return action.get();
        }
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException ex) {
            throw new StorageException("cannot acquire database connection", ex);
        }
        boolean autoCommit = true;
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            CURRENT.set(connection);
            T value = action.get();
            connection.commit();
            return value;
        } catch (SQLException ex) {
            quietRollback(connection);
            throw new StorageException("transaction failed", ex);
        } catch (RuntimeException ex) {
            quietRollback(connection);
            throw ex;
        } finally {
            CURRENT.remove();
            quietRestoreAutoCommit(connection, autoCommit);
            quietRelease(connection);
        }
    }

    @Override
    public boolean run(Supplier<Boolean> action) {
        return Boolean.TRUE.equals(call(action));
    }

    public Connection acquire() {
        Connection connection = CURRENT.get();
        if (connection != null) {
            return connection;
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException ex) {
            throw new StorageException("cannot acquire database connection", ex);
        }
    }

    public void release(Connection connection) {
        if (connection == null || connection == CURRENT.get()) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new StorageException("cannot release database connection", ex);
        }
    }

    @Override
    public void close() {
        CURRENT.remove();
    }

    private void quietRollback(Connection connection) {
        try {
            if (!connection.isClosed()) {
                connection.rollback();
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "rollback failed, connection will be evicted from the pool", ex);
        }
    }

    private void quietRestoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            if (!connection.isClosed() && connection.getAutoCommit() != autoCommit) {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "cannot restore autocommit flag", ex);
        }
    }

    private void quietRelease(Connection connection) {
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "cannot release database connection", ex);
        }
    }
}
