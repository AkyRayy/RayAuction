package ray.labs.rayauction.economy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.economy.bridge.BalanceBridge;

public final class VaultProvider implements EconomyProvider {

    private final Currency.Vault currency;
    private final BalanceBridge bridge;

    public VaultProvider(Currency.Vault currency, BalanceBridge bridge) {
        this.currency = currency;
        this.bridge = bridge;
    }

    @Override
    public Currency.Vault currency() {
        return currency;
    }

    @Override
    public boolean available() {
        return bridge.available();
    }

    @Override
    public String kind() {
        return Currency.Vault.ID;
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
        return money.amount().setScale(currency.scale(), java.math.RoundingMode.HALF_UP);
    }
}
