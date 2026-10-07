package ray.labs.rayauction.storage.sql;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.storage.Connections;
import ray.labs.rayauction.storage.LedgerRepository;
import ray.labs.rayauction.storage.StorageException;

public final class SqlLedgerRepository implements LedgerRepository {

    private final Connections connections;

    public SqlLedgerRepository(Connections connections) {
        this.connections = connections;
    }

    @Override
    public BigDecimal balance(UUID playerId, Currency currency) {
        String sql = "SELECT balance FROM rayauction_ledger WHERE player_id = ? AND currency = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, currency.id());
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot read ledger balance of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public boolean tryWithdraw(UUID playerId, Currency currency, BigDecimal amount) {
        String sql = "UPDATE rayauction_ledger SET balance = balance - ?, updated_at = ?"
                + " WHERE player_id = ? AND currency = ? AND balance >= ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, amount);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setString(3, playerId.toString());
            statement.setString(4, currency.id());
            statement.setBigDecimal(5, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new StorageException("cannot withdraw from ledger of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void credit(UUID playerId, Currency currency, BigDecimal amount) {
        if (amount.signum() == 0) {
            return;
        }
        Connection connection = connections.acquire();
        try {
            String update = "UPDATE rayauction_ledger SET balance = balance + ?, updated_at = ?"
                    + " WHERE player_id = ? AND currency = ?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setBigDecimal(1, amount);
                statement.setTimestamp(2, Timestamp.from(Instant.now()));
                statement.setString(3, playerId.toString());
                statement.setString(4, currency.id());
                if (statement.executeUpdate() == 1) {
                    return;
                }
            }
            String insert = "INSERT INTO rayauction_ledger (player_id, currency, balance, updated_at)"
                    + " VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, currency.id());
                statement.setBigDecimal(3, amount);
                statement.setTimestamp(4, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot credit ledger of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }
}
