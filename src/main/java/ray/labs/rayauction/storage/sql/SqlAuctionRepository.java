package ray.labs.rayauction.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.AuctionSearchFilter;
import ray.labs.rayauction.domain.AuctionStatus;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.domain.ItemCategory;
import ray.labs.rayauction.domain.Page;
import ray.labs.rayauction.storage.AuctionRepository;
import ray.labs.rayauction.storage.Connections;
import ray.labs.rayauction.storage.StorageException;

public final class SqlAuctionRepository implements AuctionRepository {

    private static final String SELECT_COLUMNS =
            "id, seller_id, seller_name, item_data, item_material, item_name, item_lore, item_category,"
                    + " item_amount, price, currency, created_at, expires_at, status, buyer_id, version";

    private final Connections connections;
    private final CurrencyCatalog catalog;

    public SqlAuctionRepository(Connections connections, CurrencyCatalog catalog) {
        this.connections = connections;
        this.catalog = catalog;
    }

    @Override
    public long insert(Auction auction) {
        Rows.requireItemSize(auction.item());
        String sql = "INSERT INTO rayauction_auctions (seller_id, seller_name, item_data, item_material, item_name,"
                + " item_lore, item_category, item_amount, price, currency, created_at, expires_at, status, buyer_id,"
                + " version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, auction.sellerId().toString());
            statement.setString(2, truncate(auction.sellerName(), 16));
            statement.setBytes(3, auction.item().rawData());
            statement.setString(4, truncate(auction.item().material(), 64));
            statement.setString(5, truncate(auction.item().displayName(), 512));
            statement.setString(6, truncate(auction.item().loreText(), 8_000));
            statement.setString(7, auction.item().category());
            statement.setInt(8, auction.item().amount());
            statement.setBigDecimal(9, auction.price().amount());
            statement.setString(10, auction.currency().id());
            statement.setTimestamp(11, Rows.timestamp(auction.createdAt()));
            statement.setTimestamp(12, Rows.timestamp(auction.expiresAt()));
            statement.setString(13, auction.status().name());
            statement.setNull(14, Types.VARCHAR);
            statement.setInt(15, 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new StorageException("database did not return a generated auction id");
                }
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot insert auction", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public Optional<Auction> findById(long id) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM rayauction_auctions WHERE id = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? Optional.of(Rows.auction(set, catalog)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot load auction " + id, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public List<Auction> findActive() {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM rayauction_auctions WHERE status = 'ACTIVE'";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet set = statement.executeQuery()) {
            List<Auction> auctions = new ArrayList<>();
            while (set.next()) {
                auctions.add(Rows.auction(set, catalog));
            }
            return List.copyOf(auctions);
        } catch (SQLException ex) {
            throw new StorageException("cannot load active auctions", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public Page<Auction> search(AuctionSearchFilter filter) {
        Query query = buildQuery(filter, null);
        Connection connection = connections.acquire();
        try {
            int total = count(connection, query);
            List<Auction> items = select(connection, query);
            return new Page<>(items, filter.page(), filter.perPage(), total);
        } catch (SQLException ex) {
            throw new StorageException("auction search failed", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public Page<Auction> findBySeller(UUID sellerId, AuctionStatus status, AuctionSearchFilter filter) {
        Query query = buildQuery(filter, null);
        query.where.add("seller_id = ?");
        query.parameters.add(sellerId.toString());
        if (status != null) {
            query.where.add("status = ?");
            query.parameters.add(status.name());
        }
        Connection connection = connections.acquire();
        try {
            int total = count(connection, query);
            List<Auction> items = select(connection, query);
            return new Page<>(items, filter.page(), filter.perPage(), total);
        } catch (SQLException ex) {
            throw new StorageException("cannot load auctions of " + sellerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public List<Auction> findExpiring(Instant until, int limit) {
        String sql = "SELECT "
                + SELECT_COLUMNS
                + " FROM rayauction_auctions WHERE status = 'ACTIVE' AND expires_at <= ? ORDER BY expires_at ASC LIMIT "
                + Math.max(1, limit);
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Rows.timestamp(until));
            try (ResultSet set = statement.executeQuery()) {
                List<Auction> auctions = new ArrayList<>();
                while (set.next()) {
                    auctions.add(Rows.auction(set, catalog));
                }
                return List.copyOf(auctions);
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot load expiring auctions", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public List<Auction> findTerminal(UUID sellerId, int limit) {
        String sql = "SELECT "
                + SELECT_COLUMNS
                + " FROM rayauction_auctions WHERE seller_id = ? AND status <> 'ACTIVE' ORDER BY created_at DESC LIMIT "
                + Math.max(1, limit);
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sellerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                List<Auction> auctions = new ArrayList<>();
                while (set.next()) {
                    auctions.add(Rows.auction(set, catalog));
                }
                return List.copyOf(auctions);
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot load terminal auctions of " + sellerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public int transition(long id, int expectedVersion, AuctionStatus targetStatus, UUID buyerId) {
        String sql = "UPDATE rayauction_auctions SET status = ?, version = version + 1, buyer_id = ?"
                + " WHERE id = ? AND version = ? AND status = 'ACTIVE'";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetStatus.name());
            if (buyerId == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, buyerId.toString());
            }
            statement.setLong(3, id);
            statement.setInt(4, expectedVersion);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("cannot transition auction " + id, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void markReturned(long id) {
        String sql = "UPDATE rayauction_auctions SET status = 'RETURNED', version = version + 1 WHERE id = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("cannot mark auction " + id + " as returned", ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public int countActive(UUID sellerId) {
        String sql = "SELECT COUNT(*) FROM rayauction_auctions WHERE seller_id = ? AND status = 'ACTIVE'";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sellerId.toString());
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot count auctions of " + sellerId, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public int countByStatus(AuctionStatus status) {
        String sql = "SELECT COUNT(*) FROM rayauction_auctions WHERE status = ?";
        Connection connection = connections.acquire();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new StorageException("cannot count auctions with status " + status, ex);
        } finally {
            connections.release(connection);
        }
    }

    @Override
    public void close() {}

    private int count(Connection connection, Query query) throws SQLException {
        String sql = "SELECT COUNT(*) FROM rayauction_auctions" + query.whereClause();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            query.bind(statement);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }

    private List<Auction> select(Connection connection, Query query) throws SQLException {
        String sql = "SELECT "
                + SELECT_COLUMNS
                + " FROM rayauction_auctions"
                + query.whereClause()
                + query.orderClause()
                + " LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = query.bind(statement);
            statement.setInt(index++, query.filter.perPage());
            statement.setInt(index, query.filter.page() * query.filter.perPage());
            try (ResultSet set = statement.executeQuery()) {
                List<Auction> auctions = new ArrayList<>();
                while (set.next()) {
                    auctions.add(Rows.auction(set, catalog));
                }
                return List.copyOf(auctions);
            }
        }
    }

    private Query buildQuery(AuctionSearchFilter filter, UUID sellerScope) {
        Query query = new Query(filter);
        if (sellerScope == null) {
            query.where.add("status = 'ACTIVE'");
        }
        if (!filter.nameQuery().isEmpty()) {
            query.where.add("(LOWER(item_name) LIKE ? ESCAPE '\\' OR LOWER(item_material) LIKE ? ESCAPE '\\')");
            String needle = like(filter.nameQuery());
            query.parameters.add(needle);
            query.parameters.add(needle);
        }
        if (!filter.loreQuery().isEmpty()) {
            query.where.add("LOWER(item_lore) LIKE ? ESCAPE '\\'");
            query.parameters.add(like(filter.loreQuery()));
        }
        if (!filter.material().isEmpty()) {
            query.where.add("item_material = ?");
            query.parameters.add(filter.material().toUpperCase(java.util.Locale.ROOT));
        }
        if (!filter.sellerName().isEmpty()) {
            query.where.add("LOWER(seller_name) = ?");
            query.parameters.add(filter.sellerName().toLowerCase(java.util.Locale.ROOT));
        }
        if (!filter.currencyId().isEmpty()) {
            query.where.add("currency = ?");
            query.parameters.add(filter.currencyId());
        }
        if (!filter.category().isEmpty() && !ItemCategory.ALL.matches(filter.category())) {
            query.where.add("item_category = ?");
            query.parameters.add(filter.category());
        }
        if (sellerScope != null) {
            query.where.add("seller_id = ?");
            query.parameters.add(sellerScope.toString());
        }
        return query;
    }

    private static String like(String value) {
        return "%" + value.toLowerCase(java.util.Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final class Query {

        private final AuctionSearchFilter filter;
        private final List<String> where = new ArrayList<>();
        private final List<String> parameters = new ArrayList<>();

        private Query(AuctionSearchFilter filter) {
            this.filter = filter;
        }

        private String whereClause() {
            return where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        }

        private String orderClause() {
            return " ORDER BY "
                    + switch (filter.sort()) {
                        case DATE_ASC -> "created_at ASC, id ASC";
                        case DATE_DESC -> "created_at DESC, id DESC";
                        case PRICE_ASC -> "price ASC, id ASC";
                        case PRICE_DESC -> "price DESC, id DESC";
                    };
        }

        private int bind(PreparedStatement statement) throws SQLException {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setString(i + 1, parameters.get(i));
            }
            return parameters.size() + 1;
        }
    }
}
