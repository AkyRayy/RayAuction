package ray.labs.rayauction.storage.sql;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.AuctionStatus;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.domain.MailEntry;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.storage.StorageException;

final class Rows {

    static final int MAX_ITEM_BYTES = 1_048_576;

    private Rows() {}

    static Auction auction(ResultSet set, CurrencyCatalog catalog) throws SQLException {
        AuctionItem item = item(set);
        String currencyId = set.getString("currency");
        Currency currency = catalog
                .byId(currencyId)
                .orElseThrow(() -> new StorageException("unknown currency in database row: " + currencyId));
        return new Auction(
                set.getLong("id"),
                UUID.fromString(set.getString("seller_id")),
                set.getString("seller_name"),
                item,
                new Money(set.getBigDecimal("price"), currency),
                instant(set.getTimestamp("created_at")),
                instant(set.getTimestamp("expires_at")),
                AuctionStatus.parse(set.getString("status")),
                uuid(set, "buyer_id"),
                set.getInt("version"));
    }

    static AuctionItem item(ResultSet set) throws SQLException {
        byte[] data = set.getBytes("item_data");
        if (data == null || data.length == 0) {
            throw new StorageException("row without item payload");
        }
        String lore = set.getString("item_lore");
        String material = set.getString("item_material");
        String name = set.getString("item_name");
        String category = set.getString("item_category");
        return new AuctionItem(
                data,
                material == null ? "AIR" : material,
                name == null ? "" : name,
                lore == null ? "" : lore,
                category,
                Math.max(1, set.getInt("item_amount")),
                isContainer(material),
                containerSize(material));
    }

    static MailEntry mail(ResultSet set, CurrencyCatalog catalog) throws SQLException {
        byte[] data = set.getBytes("item_data");
        AuctionItem item = null;
        if (data != null && data.length > 0) {
            String material = set.getString("item_material");
            item = new AuctionItem(
                    data,
                    material == null ? "AIR" : material,
                    "",
                    "",
                    null,
                    Math.max(1, set.getInt("item_amount")),
                    isContainer(material),
                    containerSize(material));
        }
        Money money = null;
        BigDecimal amount = set.getBigDecimal("amount");
        String currencyId = set.getString("currency");
        if (amount != null && currencyId != null) {
            Currency currency = catalog
                    .byId(currencyId)
                    .orElseThrow(() -> new StorageException("unknown currency in mailbox row: " + currencyId));
            money = new Money(amount, currency);
        }
        return new MailEntry(
                set.getLong("id"),
                UUID.fromString(set.getString("player_id")),
                MailEntry.MailReason.valueOf(set.getString("reason")),
                item,
                money,
                set.getLong("auction_id"),
                instant(set.getTimestamp("created_at")),
                set.getBoolean("delivered"));
    }

    static boolean isContainer(String material) {
        return material != null && (material.endsWith("_SHULKER_BOX") || material.equals("BARREL"));
    }

    static int containerSize(String material) {
        if (material == null) {
            return 0;
        }
        if (material.endsWith("_SHULKER_BOX") || material.equals("BARREL")) {
            return 27;
        }
        return 0;
    }

    static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            throw new StorageException("null timestamp in database row");
        }
        return timestamp.toInstant();
    }

    static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    static UUID uuid(ResultSet set, String column) throws SQLException {
        String value = set.getString(column);
        return value == null || value.isEmpty() ? null : UUID.fromString(value);
    }

    static void requireItemSize(AuctionItem item) {
        if (item.rawData().length > MAX_ITEM_BYTES) {
            throw new StorageException("item payload too large: " + item.rawData().length + " bytes");
        }
    }
}
