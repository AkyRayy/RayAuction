package ray.labs.rayauction.sync;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import ray.labs.rayauction.domain.Auction;
import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.AuctionStatus;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.CurrencyCatalog;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.storage.StorageException;

public final class SyncPayload {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private SyncPayload() {}

    public static Map<String, String> ofAuction(Auction auction) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("id", Long.toString(auction.id()));
        payload.put("sellerId", auction.sellerId().toString());
        payload.put("sellerName", auction.sellerName());
        payload.put("item", ENCODER.encodeToString(auction.item().rawData()));
        payload.put("material", auction.item().material());
        payload.put("name", auction.item().displayName());
        payload.put("lore", auction.item().loreText());
        payload.put("category", auction.item().category());
        payload.put("amount", Integer.toString(auction.item().amount()));
        payload.put("container", Boolean.toString(auction.item().container()));
        payload.put("containerSize", Integer.toString(auction.item().containerSize()));
        payload.put("price", auction.price().amount().toPlainString());
        payload.put("currency", auction.currency().id());
        payload.put("createdAt", auction.createdAt().toString());
        payload.put("expiresAt", auction.expiresAt().toString());
        payload.put("status", auction.status().name());
        payload.put("version", Integer.toString(auction.version()));
        return Map.copyOf(payload);
    }

    public static Auction auction(SyncEvent event, CurrencyCatalog catalog) {
        String currencyId = event.required("currency");
        Currency currency = catalog.byId(currencyId)
                .orElseThrow(() -> new StorageException("sync event references unknown currency " + currencyId));
        byte[] data = DECODER.decode(event.required("item"));
        AuctionItem item = new AuctionItem(
                data,
                event.required("material"),
                event.optional("name", ""),
                event.optional("lore", ""),
                event.optional("category", null),
                Integer.parseInt(event.optional("amount", "1")),
                Boolean.parseBoolean(event.optional("container", "false")),
                Integer.parseInt(event.optional("containerSize", "0")));
        String buyer = event.optional("buyerId", "");
        return new Auction(
                event.requiredLong("id"),
                UUID.fromString(event.required("sellerId")),
                event.required("sellerName"),
                item,
                new Money(new BigDecimal(event.required("price")), currency),
                Instant.parse(event.required("createdAt")),
                Instant.parse(event.required("expiresAt")),
                AuctionStatus.parse(event.optional("status", AuctionStatus.ACTIVE.name())),
                buyer.isEmpty() ? null : UUID.fromString(buyer),
                Integer.parseInt(event.optional("version", "0")));
    }

    public static Map<String, String> ofId(long auctionId) {
        return Map.of("id", Long.toString(auctionId));
    }

    public static Map<String, String> ofPurchase(long auctionId, UUID buyerId, Money price) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("id", Long.toString(auctionId));
        payload.put("buyerId", buyerId.toString());
        payload.put("price", price.amount().toPlainString());
        payload.put("currency", price.currency().id());
        return Map.copyOf(payload);
    }

    public static Map<String, String> ofLimit(UUID playerId, int limit) {
        return Map.of("playerId", playerId.toString(), "limit", Integer.toString(limit));
    }
}
