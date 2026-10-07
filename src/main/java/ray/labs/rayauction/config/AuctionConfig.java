package ray.labs.rayauction.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ray.labs.rayauction.domain.AuctionRules;
import ray.labs.rayauction.domain.DurationSpec;

public record AuctionConfig(AuctionRules rules, int listingBatchSize, int expiryBatchSize, int mailboxFlushLimit) {

    public AuctionConfig {
        listingBatchSize = Math.max(1, listingBatchSize);
        expiryBatchSize = Math.max(1, expiryBatchSize);
        mailboxFlushLimit = Math.max(1, mailboxFlushLimit);
    }

    public static AuctionConfig from(YamlNode node) {
        YamlNode limits = node.section("limits");
        List<DurationSpec> durations = DurationSpec.parseAll(
                node.stringList("default-durations", List.of("6h", "1d", "3d", "7d")));
        if (durations.isEmpty()) {
            durations = List.of(new DurationSpec("1d", Duration.ofDays(1)));
        }
        Map<String, AuctionRules.PriceBounds> bounds = new LinkedHashMap<>();
        for (Map.Entry<String, YamlNode> entry : node.section("price-limits").section("per-currency").sections().entrySet()) {
            bounds.put(
                    entry.getKey().trim().toLowerCase(java.util.Locale.ROOT),
                    new AuctionRules.PriceBounds(
                            entry.getValue().decimal("min", BigDecimal.ONE),
                            entry.getValue().decimal("max", new BigDecimal("1000000000"))));
        }
        YamlNode globalBounds = node.section("price-limits");
        AuctionRules.PriceBounds fallback = new AuctionRules.PriceBounds(
                globalBounds.decimal("min", BigDecimal.ONE),
                globalBounds.decimal("max", new BigDecimal("1000000000")));
        AuctionRules rules = new AuctionRules(
                limits.integer("default", 10),
                limits.bool("permission-based", true),
                durations,
                bounds,
                fallback,
                clampRate(node.decimal("tax-rate", BigDecimal.ZERO)),
                AuctionRules.TaxDestination.parse(node.string("tax-destination", "burn")),
                node.string("tax-account", ""),
                Duration.ofSeconds(Math.max(5, node.integer("expiry-check-interval-seconds", 60))),
                AuctionRules.ExpiredDelivery.parse(node.string("expired-auction-delivery", "mailbox")),
                node.bool("allow-self-purchase", false),
                node.bool("allow-creative-listing", false),
                Math.max(0, node.integer("search-cooldown-seconds", 3)),
                Math.max(50, node.integer("purchase-cooldown-millis", 300)),
                Duration.ofSeconds(Math.max(5, node.integer("confirmation-ttl-seconds", 30))),
                Math.max(1, node.integer("history-page-size", 36)),
                Math.max(1, node.integer("browser-page-size", 36)));
        return new AuctionConfig(
                rules,
                Math.max(1, node.integer("listing-batch-size", 200)),
                Math.max(1, node.integer("expiry-batch-size", 200)),
                Math.max(1, node.integer("mailbox-flush-limit", 32)));
    }

    public List<String> durationLabels() {
        List<String> labels = new ArrayList<>(rules.durations().size());
        for (DurationSpec spec : rules.durations()) {
            labels.add(spec.label());
        }
        return List.copyOf(labels);
    }

    private static BigDecimal clampRate(BigDecimal rate) {
        if (rate.signum() < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal max = new BigDecimal("0.95");
        return rate.compareTo(max) > 0 ? max : rate;
    }
}
