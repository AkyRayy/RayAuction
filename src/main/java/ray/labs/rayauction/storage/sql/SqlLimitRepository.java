package ray.labs.rayauction.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.storage.Connections;
import ray.labs.rayauction.storage.LimitRepository;
import ray.labs.rayauction.storage.StorageException;

public final class SqlLimitRepository implements LimitRepository {

    private final Connections connections;

    public SqlLimitRepository(Connections connections) {
        this.connections = connections;
    }

    @Override
    public Optional<Integer> find(UUID playerId) {
        String sql = "SELECT custom_limit FROM rayauction_limits WHERE player_id = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                int value = set.getInt(1);
                return set.wasNull() ? Optional.empty() : Optional.of(value);
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot read limit of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void set(UUID playerId, Integer limit) {
        Connection connection = connections.acquire();
        try {
            String update = "UPDATE rayauction_limits SET custom_limit = ?, updated_at = ? WHERE player_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                if (limit == null) {
                    statement.setNull(1, Types.INTEGER);
                } else {
                    statement.setInt(1, limit);
                }
                statement.setTimestamp(2, Timestamp.from(Instant.now()));
                statement.setString(3, playerId.toString());
                if (statement.executeUpdate() == 1) {
                    return;
                }
            }
            String insert = "INSERT INTO rayauction_limits (player_id, custom_limit, updated_at) VALUES (?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, playerId.toString());
                if (limit == null) {
                    statement.setNull(2, Types.INTEGER);
                } else {
                    statement.setInt(2, limit);
                }
                statement.setTimestamp(3, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot store limit of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void add(UUID playerId, int baseLimit, int amount) {
        shift(playerId, baseLimit, amount);
    }

    @Override
    public void take(UUID playerId, int baseLimit, int amount) {
        shift(playerId, baseLimit, -amount);
    }

    @Override
    public void reset(UUID playerId) {
        String sql = "DELETE FROM rayauction_limits WHERE player_id = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("cannot reset limit of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    private void shift(UUID playerId, int baseLimit, int delta) {
        set(playerId, Math.max(0, baseLimit + delta));
    }
}
