package ray.labs.rayauction.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.domain.MailEntry;
import ray.labs.rayauction.storage.Connections;
import ray.labs.rayauction.storage.MailRepository;
import ray.labs.rayauction.storage.StorageException;

public final class SqlMailRepository implements MailRepository {

    private final Connections connections;
    private final CurrencyCatalog catalog;

    public SqlMailRepository(Connections connections, CurrencyCatalog catalog) {
        this.connections = connections;
        this.catalog = catalog;
    }

    @Override
    public long insert(MailEntry entry) {
        String sql = "INSERT INTO rayauction_mailbox (player_id, reason, item_data, item_material, item_amount,"
                + " amount, currency, auction_id, created_at, delivered) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, entry.playerId().toString());
            statement.setString(2, entry.reason().name());
            if (entry.item().isPresent()) {
                statement.setBytes(3, entry.item().get().rawData());
                statement.setString(4, entry.item().get().material());
                statement.setInt(5, entry.item().get().amount());
            } else {
                statement.setNull(3, Types.BLOB);
                statement.setNull(4, Types.VARCHAR);
                statement.setInt(5, 1);
            }
            if (entry.money().isPresent()) {
                statement.setBigDecimal(6, entry.money().get().amount());
                statement.setString(7, entry.money().get().currency().id());
            } else {
                statement.setNull(6, Types.DECIMAL);
                statement.setNull(7, Types.VARCHAR);
            }
            statement.setLong(8, entry.auctionId());
            statement.setTimestamp(9, Timestamp.from(entry.createdAt()));
            statement.setBoolean(10, false);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot store mail entry for " + entry.playerId(), ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public List<MailEntry> findUndelivered(UUID playerId, int limit) {
        String sql = "SELECT id, player_id, reason, item_data, item_material, item_amount, amount, currency,"
                + " auction_id, created_at, delivered FROM rayauction_mailbox"
                + " WHERE player_id = ? AND delivered = FALSE ORDER BY id ASC LIMIT "
                + Math.max(1, limit);
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                List<MailEntry> entries = new ArrayList<>();
                while (set.next()) {
                    entries.add(Rows.mail(set, catalog));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot load mailbox of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public boolean markDelivered(long id) {
        String sql = "UPDATE rayauction_mailbox SET delivered = TRUE WHERE id = ? AND delivered = FALSE";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new StorageException("cannot mark mail entry " + id + " as delivered", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public int countUndelivered(UUID playerId) {
        String sql = "SELECT COUNT(*) FROM rayauction_mailbox WHERE player_id = ? AND delivered = FALSE";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot count mailbox entries of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void purgeDeliveredOlderThan(long epochSeconds) {
        String sql = "DELETE FROM rayauction_mailbox WHERE delivered = TRUE AND created_at < ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.ofEpochSecond(epochSeconds)));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("cannot purge mailbox", ex);
        } finally {
            connections.release(connection);
        }
    }
}
