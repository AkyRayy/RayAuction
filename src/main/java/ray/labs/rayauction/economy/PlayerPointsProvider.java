package ray.labs.rayauction.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.economy.bridge.BalanceBridge;

public final class PlayerPointsProvider implements EconomyProvider {

    private final Currency.PlayerPoints currency;
    private final BalanceBridge bridge;

    public PlayerPointsProvider(Currency.PlayerPoints currency, BalanceBridge bridge) {
        this.currency = currency;
        this.bridge = bridge;
    }

    @Override
    public Currency.PlayerPoints currency() {
        return currency;
    }

    @Override
    public boolean available() {
        return bridge.available();
    }

    @Override
    public String kind() {
        return Currency.PlayerPoints.ID;
    }

    @Override
    public Optional<Money> lookup(UUID playerId) {
        return bridge.getBalance(playerId).map(amount -> new Money(amount, currency));
    }

    @Override
    public boolean take(UUID playerId, Money money) {
        return bridge.withdraw(playerId, scaled(money));
    }

    @Override
    public void give(UUID playerId, Money money) {
        bridge.deposit(playerId, scaled(money));
    }

    private BigDecimal scaled(Money money) {
        return money.amount().setScale(0, RoundingMode.DOWN);
    }
}
