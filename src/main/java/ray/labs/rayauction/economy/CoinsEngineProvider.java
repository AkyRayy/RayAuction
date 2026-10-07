package ray.labs.rayauction.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.economy.bridge.BalanceBridge;

public final class CoinsEngineProvider implements EconomyProvider {

    private final Currency.CoinsEngine currency;
    private final BalanceBridge bridge;

    public CoinsEngineProvider(Currency.CoinsEngine currency, BalanceBridge bridge) {
        this.currency = currency;
        this.bridge = bridge;
    }

    @Override
    public Currency.CoinsEngine currency() {
        return currency;
    }

    @Override
    public boolean available() {
        return bridge.available();
    }

    @Override
    public String kind() {
        return "coinsengine";
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
        return money.amount().setScale(currency.scale(), RoundingMode.HALF_UP);
    }
}
