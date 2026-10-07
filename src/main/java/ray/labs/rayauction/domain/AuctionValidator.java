package ray.labs.rayauction.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class AuctionValidator {

    private final AuctionRules rules;

    public AuctionValidator(AuctionRules rules) {
        this.rules = rules;
    }

    public AuctionRules rules() {
        return rules;
    }

    public Optional<Outcome> checkListing(
            UUID sellerId, AuctionItem item, Money price, DurationSpec duration, int activeListings, int limit) {
        if (item == null || item.rawData().length == 0) {
            return Optional.of(Outcome.of("sell.no-item"));
        }
        if (item.amount() < 1) {
            return Optional.of(Outcome.of("sell.invalid-item"));
        }
        if (price == null || !price.isPositive()) {
            return Optional.of(Outcome.of("sell.price-too-low"));
        }
        AuctionRules.PriceBounds bounds = rules.boundsFor(price.currency());
        if (price.amount().compareTo(bounds.min()) < 0) {
            return Optional.of(Outcome.builder("sell.price-below-min")
                    .with("min", bounds.min().toPlainString())
                    .build());
        }
        if (price.amount().compareTo(bounds.max()) > 0) {
            return Optional.of(Outcome.builder("sell.price-above-max")
                    .with("max", bounds.max().toPlainString())
                    .build());
        }
        if (duration == null || rules.duration(duration.label()).isEmpty()) {
            return Optional.of(Outcome.of("sell.invalid-duration"));
        }
        if (limit != PlayerLimit.UNLIMITED && activeListings >= limit) {
            return Optional.of(Outcome.builder("sell.limit-reached")
                    .with("limit", Integer.toString(limit))
                    .with("active", Integer.toString(activeListings))
                    .build());
        }
        return Optional.empty();
    }

    public Optional<Outcome> checkPurchase(Auction auction, UUID buyerId, Instant now, boolean isAdmin) {
        if (auction == null) {
            return Optional.of(Outcome.of("buy.not-found"));
        }
        if (!auction.isActive()) {
            return Optional.of(Outcome.of("buy.not-active"));
        }
        if (auction.isExpired(now)) {
            return Optional.of(Outcome.of("buy.expired"));
        }
        if (auction.isOwn(buyerId) && !rules.allowSelfPurchase() && !isAdmin) {
            return Optional.of(Outcome.of("buy.own-auction"));
        }
        return Optional.empty();
    }

    public Optional<Outcome> checkCancel(Auction auction, UUID playerId, boolean isAdmin) {
        if (auction == null) {
            return Optional.of(Outcome.of("cancel.not-found"));
        }
        if (!auction.isActive()) {
            return Optional.of(Outcome.of("cancel.not-active"));
        }
        if (!isAdmin && !auction.isOwn(playerId)) {
            return Optional.of(Outcome.of("cancel.not-owner"));
        }
        return Optional.empty();
    }
}
