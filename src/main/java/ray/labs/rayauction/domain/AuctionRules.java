package ray.labs.rayauction.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record AuctionRules(
        int defaultLimit,
        boolean permissionLimits,
        List<DurationSpec> durations,
        Map<String, PriceBounds> priceBounds,
        PriceBounds fallbackPriceBounds,
        BigDecimal taxRate,
        TaxDestination taxDestination,
        String taxAccount,
        Duration expiryCheckInterval,
        ExpiredDelivery expiredDelivery,
        boolean allowSelfPurchase,
        boolean allowCreativeListing,
        int searchCooldownSeconds,
        int purchaseCooldownMillis,
        Duration confirmationTtl,
        int historyPageSize,
        int browserPageSize) {

    public AuctionRules {
        durations = durations == null ? List.of() : List.copyOf(durations);
        priceBounds = priceBounds == null ? Map.of() : Map.copyOf(priceBounds);
        taxRate = taxRate == null ? BigDecimal.ZERO : taxRate;
        taxAccount = taxAccount == null ? "" : taxAccount;
        expiryCheckInterval = expiryCheckInterval == null ? Duration.ofSeconds(60) : expiryCheckInterval;
        confirmationTtl = confirmationTtl == null ? Duration.ofSeconds(30) : confirmationTtl;
        if (defaultLimit < -1) {
            defaultLimit = -1;
        }
        if (browserPageSize < 1) {
            browserPageSize = 36;
        }
        if (historyPageSize < 1) {
            historyPageSize = 36;
        }
    }

    public record PriceBounds(BigDecimal min, BigDecimal max) {

        public PriceBounds {
            min = min == null ? BigDecimal.ZERO : min;
            max = max == null ? new BigDecimal("1000000000") : max;
        }

        public boolean contains(BigDecimal value) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
        }
    }

    public enum TaxDestination {
        BURN,
        ACCOUNT;

        public static TaxDestination parse(String raw) {
            for (TaxDestination destination : values()) {
                if (destination.name().equalsIgnoreCase(raw)) {
                    return destination;
                }
            }
            return BURN;
        }
    }

    public enum ExpiredDelivery {
        INVENTORY,
        MAILBOX;

        public static ExpiredDelivery parse(String raw) {
            for (ExpiredDelivery delivery : values()) {
                if (delivery.name().equalsIgnoreCase(raw)) {
                    return delivery;
                }
            }
            return MAILBOX;
        }
    }

    public Optional<DurationSpec> duration(String label) {
        if (label == null) {
            return Optional.empty();
        }
        String wanted = label.trim().toLowerCase(java.util.Locale.ROOT);
        for (DurationSpec spec : durations) {
            if (spec.label().toLowerCase(java.util.Locale.ROOT).equals(wanted)) {
                return Optional.of(spec);
            }
            if (spec.seconds() == DurationSpec.parse(label).map(DurationSpec::seconds).orElse(-1L)) {
                return Optional.of(spec);
            }
        }
        return Optional.empty();
    }

    public PriceBounds boundsFor(Currency currency) {
        PriceBounds bounds = priceBounds.get(currency.id());
        return bounds == null ? fallbackPriceBounds : bounds;
    }

    public int limitFor(Integer customLimit, int permissionLimit) {
        return PlayerLimit.resolve(customLimit, defaultLimit, permissionLimit);
    }
}
