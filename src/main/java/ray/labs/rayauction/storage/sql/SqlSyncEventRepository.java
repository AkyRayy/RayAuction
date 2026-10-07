package ray.labs.rayauction.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ray.labs.rayauction.storage.Connections;
import ray.labs.rayauction.storage.StorageException;
import ray.labs.rayauction.storage.SyncEventRepository;
import ray.labs.rayauction.sync.SyncEvent;
import ray.labs.rayauction.sync.SyncEventType;
import ray.labs.rayauction.util.JsonCodec;

public final class SqlSyncEventRepository implements SyncEventRepository {

    private final Connections connections;

    public SqlSyncEventRepository(Connections connections) {
        this.connections = connections;
    }

    @Override
    public long insert(SyncEvent event) {
        String sql = "INSERT INTO rayauction_sync_events (event_type, payload, server_id, created_at, processed)"
                + " VALUES (?, ?, ?, ?, ?)";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, event.type().wireName());
            statement.setString(2, JsonCodec.encode(event.payload()));
            statement.setString(3, event.serverId());
            statement.setTimestamp(4, Timestamp.from(event.createdAt()));
            statement.setBoolean(5, false);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot publish sync event " + event.type(), ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public List<SyncEvent> fetchAfter(long lastSeenId, String excludingServerId, int limit) {
        String sql = "SELECT id, event_type, payload, server_id, created_at FROM rayauction_sync_events"
                + " WHERE id > ? AND server_id <> ? ORDER BY id ASC LIMIT "
                + Math.max(1, limit);
        return query(sql, statement -> {
            statement.setLong(1, lastSeenId);
            statement.setString(2, excludingServerId);
        }, limit);
    }

    @Override
    public List<SyncEvent> fetchUnprocessed(String excludingServerId, int limit) {
        String sql = "SELECT id, event_type, payload, server_id, created_at FROM rayauction_sync_events"
                + " WHERE processed = FALSE AND server_id <> ? ORDER BY id ASC LIMIT "
                + Math.max(1, limit);
        return query(sql, statement -> statement.setString(1, excludingServerId), limit);
    }

    @Override
    public void markProcessed(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String sql = "UPDATE rayauction_sync_events SET processed = TRUE WHERE id = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Long id : ids) {
                statement.setLong(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new StorageException("cannot mark sync events as processed", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public int purgeOlderThan(long epochSeconds) {
        String sql = "DELETE FROM rayauction_sync_events WHERE created_at < ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.ofEpochSecond(epochSeconds)));
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("cannot purge sync events", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public long maxId() {
        String sql = "SELECT MAX(id) FROM rayauction_sync_events";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet set = statement.executeQuery()) {
            if (!set.next()) {
                return 0L;
            }
            long value = set.getLong(1);
            return set.wasNull() ? 0L : value;
        } catch (SQLException ex) {
            throw new StorageException("cannot read sync event watermark", ex);
        } finally {
            connections.release(connection);
        }
    }

    private List<SyncEvent> query(String sql, Binder binder, int limit) {
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet set = statement.executeQuery()) {
                List<SyncEvent> events = new ArrayList<>();
                while (set.next() && events.size() < Math.max(1, limit)) {
                    SyncEventType type = SyncEventType.parse(set.getString("event_type")).orElse(null);
                    if (type == null) {
                        continue;
                    }
                    Map<String, String> payload = JsonCodec.decode(set.getString("payload"));
                    events.add(new SyncEvent(
                            set.getLong("id"),
                            type,
                            payload,
                            set.getString("server_id"),
                            Rows.instant(set.getTimestamp("created_at"))));
                }
                return List.copyOf(events);
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot fetch sync events", ex);
        } finally {
            connections.release(connection);
        }
    }

    @FunctionalInterface
    private interface Binder {

        void bind(PreparedStatement statement) throws SQLException;
    }
}
