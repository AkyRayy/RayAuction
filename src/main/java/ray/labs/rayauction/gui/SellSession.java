package ray.labs.rayauction.gui;

import java.time.Instant;
import java.util.UUID;

import ray.labs.rayauction.domain.AuctionItem;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.DurationSpec;
import ray.labs.rayauction.domain.Money;

public final class SellSession {

    private final UUID playerId;
    private final AuctionItem item;
    private final Instant createdAt;
    private Currency currency;
    private Money price;
    private DurationSpec duration;
    private boolean consumed;
    private boolean suspended;

    public SellSession(UUID playerId, AuctionItem item, Currency currency, DurationSpec duration) {
        this.playerId = playerId;
        this.item = item;
        this.createdAt = Instant.now();
        this.currency = currency;
        this.duration = duration;
    }

    public UUID playerId() {
        return playerId;
    }

    public AuctionItem item() {
        return item;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Currency currency() {
        return currency;
    }

    public void currency(Currency currency) {
        this.currency = currency;
        this.price = null;
    }

    public Money price() {
        return price;
    }

    public void price(Money price) {
        this.price = price;
    }

    public DurationSpec duration() {
        return duration;
    }

    public void duration(DurationSpec duration) {
        this.duration = duration;
    }

    public boolean isReady() {
        return currency != null && duration != null && price != null && price.isPositive();
    }

    public boolean consumed() {
        return consumed;
    }

    public void consume() {
        consumed = true;
    }

    public boolean suspended() {
        return suspended;
    }

    public void suspend(boolean suspended) {
        this.suspended = suspended;
    }
}
