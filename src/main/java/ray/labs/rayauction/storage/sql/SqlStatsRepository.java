package ray.labs.rayauction.storage.sql;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import ray.labs.rayauction.domain.AuctionStats;
import ray.labs.rayauction.storage.Connections;
import ray.labs.rayauction.storage.StatsRepository;
import ray.labs.rayauction.storage.StorageException;

public final class SqlStatsRepository implements StatsRepository {

    private final Connections connections;

    public SqlStatsRepository(Connections connections) {
        this.connections = connections;
    }

    @Override
    public AuctionStats stats(UUID playerId) {
        long sales = countSales(playerId);
        long purchases = countPurchases(playerId);
        BigDecimal earned = sum("SELECT COALESCE(SUM(price - tax_amount), 0) FROM rayauction_transactions"
                + " WHERE seller_id = ?", playerId);
        BigDecimal spent = sum("SELECT COALESCE(SUM(price), 0) FROM rayauction_transactions WHERE buyer_id = ?", playerId);
        return new AuctionStats(0, sales, purchases, earned, spent);
    }

    @Override
    public BigDecimal earnedIn(UUID playerId, String currencyId) {
        return sumCurrency(
                "SELECT COALESCE(SUM(price - tax_amount), 0) FROM rayauction_transactions"
                        + " WHERE seller_id = ? AND currency = ?",
                playerId,
                currencyId);
    }

    @Override
    public BigDecimal spentIn(UUID playerId, String currencyId) {
        return sumCurrency(
                "SELECT COALESCE(SUM(price), 0) FROM rayauction_transactions WHERE buyer_id = ? AND currency = ?",
                playerId,
                currencyId);
    }

    @Override
    public long countSales(UUID playerId) {
        return count("SELECT COUNT(*) FROM rayauction_transactions WHERE seller_id = ?", playerId);
    }

    @Override
    public long countPurchases(UUID playerId) {
        return count("SELECT COUNT(*) FROM rayauction_transactions WHERE buyer_id = ?", playerId);
    }

    @Override
    public AuctionStats.Leader topSeller() {
        return leader("SELECT seller_id, seller_name, COUNT(*) AS amount FROM rayauction_transactions"
                + " GROUP BY seller_id, seller_name ORDER BY COUNT(*) DESC, seller_id ASC LIMIT 1");
    }

    @Override
    public AuctionStats.Leader topBuyer() {
        return leader("SELECT buyer_id, buyer_name, COUNT(*) AS amount FROM rayauction_transactions"
                + " GROUP BY buyer_id, buyer_name ORDER BY COUNT(*) DESC, buyer_id ASC LIMIT 1");
    }

    private AuctionStats.Leader leader(String sql) {
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet set = statement.executeQuery()) {
            if (!set.next()) {
                return AuctionStats.Leader.NONE;
            }
            String id = set.getString(1);
            String name = set.getString(2);
            long amount = set.getLong(3);
            if (id == null || id.isEmpty()) {
                return AuctionStats.Leader.NONE;
            }
            return new AuctionStats.Leader(UUID.fromString(id), name == null ? "" : name, amount);
        } catch (SQLException ex) {
            throw new StorageException("cannot compute leaderboard", ex);
        } finally {
            connections.release(connection);
        }
    }

    private BigDecimal sum(String sql, UUID playerId) {
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot sum transactions of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    private BigDecimal sumCurrency(String sql, UUID playerId, String currencyId) {
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, currencyId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot sum " + currencyId + " transactions of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    private long count(String sql, UUID playerId) {
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot count transactions of " + playerId, ex);
        } finally {
            connections.release(connection);
        }
    }
}
