package ray.labs.rayauction.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.domain.HistoryEntry;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.domain.Transaction;
import ray.labs.rayauction.storage.Connections;
import ray.labs.rayauction.storage.StorageException;
import ray.labs.rayauction.storage.TransactionRepository;

public final class SqlTransactionRepository implements TransactionRepository {

    private static final String COLUMNS =
            "id, auction_id, buyer_id, buyer_name, seller_id, seller_name, item_data, item_material, item_amount,"
                    + " price, currency, tax_amount, created_at";

    private final Connections connections;
    private final CurrencyCatalog catalog;

    public SqlTransactionRepository(Connections connections, CurrencyCatalog catalog) {
        this.connections = connections;
        this.catalog = catalog;
    }

    @Override
    public long insert(Transaction transaction) {
        String sql = "INSERT INTO rayauction_transactions (auction_id, buyer_id, buyer_name, seller_id, seller_name,"
                + " item_data, item_material, item_amount, price, currency, tax_amount, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, transaction.auctionId());
            statement.setString(2, transaction.buyerId().toString());
            statement.setString(3, truncate(transaction.buyerName()));
            statement.setString(4, transaction.sellerId().toString());
            statement.setString(5, truncate(transaction.sellerName()));
            statement.setBytes(6, transaction.item().rawData());
            statement.setString(7, truncateMaterial(transaction.item().material()));
            statement.setInt(8, transaction.item().amount());
            statement.setBigDecimal(9, transaction.price().amount());
            statement.setString(10, transaction.price().currency().id());
            statement.setBigDecimal(11, transaction.tax().amount());
            statement.setTimestamp(12, Rows.timestamp(transaction.createdAt()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new StorageException("database did not return a generated transaction id");
                }
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot insert transaction for auction " + transaction.auctionId(), ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public Optional<Transaction> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM rayauction_transactions WHERE id = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? Optional.of(read(set)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot load transaction " + id, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public boolean existsForAuction(long auctionId) {
        String sql = "SELECT 1 FROM rayauction_transactions WHERE auction_id = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, auctionId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot check idempotency for auction " + auctionId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public List<Transaction> findRecent(int limit) {
        String sql = "SELECT "
                + COLUMNS
                + " FROM rayauction_transactions ORDER BY created_at DESC, id DESC LIMIT "
                + Math.max(1, limit);
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet set = statement.executeQuery()) {
            List<Transaction> found = new ArrayList<>();
            while (set.next()) {
                found.add(read(set));
            }
            return List.copyOf(found);
        } catch (SQLException ex) {
            throw new StorageException("cannot load recent transactions", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public List<HistoryEntry> historyOf(UUID playerId, int limit) {
        String sql = "SELECT "
                + COLUMNS
                + " FROM rayauction_transactions WHERE buyer_id = ? OR seller_id = ?"
                + " ORDER BY created_at DESC, id DESC LIMIT "
                + Math.max(1, limit);
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                List<HistoryEntry> entries = new ArrayList<>();
                while (set.next()) {
                    entries.add(read(set).toHistory(playerId));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot load history of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void close() {}

    private Transaction read(ResultSet set) throws SQLException {
        String currencyId = set.getString("currency");
        Currency currency = catalog.byId(currencyId)
                .orElseThrow(() -> new StorageException("unknown currency in transaction row: " + currencyId));
        byte[] data = set.getBytes("item_data");
        if (data == null || data.length == 0) {
            throw new StorageException("transaction row without item payload");
        }
        String material = set.getString("item_material");
        AuctionItem item = new AuctionItem(
                data,
                material == null ? "AIR" : material,
                "",
                "",
                null,
                Math.max(1, set.getInt("item_amount")),
                Rows.isContainer(material),
                Rows.containerSize(material));
        return new Transaction(
                set.getLong("id"),
                set.getLong("auction_id"),
                UUID.fromString(set.getString("buyer_id")),
                set.getString("buyer_name"),
                UUID.fromString(set.getString("seller_id")),
                set.getString("seller_name"),
                item,
                new Money(set.getBigDecimal("price"), currency),
                new Money(set.getBigDecimal("tax_amount"), currency),
                Rows.instant(set.getTimestamp("created_at")));
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 16 ? value : value.substring(0, 16);
    }

    private static String truncateMaterial(String value) {
        if (value == null || value.isBlank()) {
            return "AIR";
        }
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
