package ray.labs.rayauction.economy;

import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.Money;
import ray.labs.rayauction.domain.port.BalancePort;

public sealed interface EconomyProvider extends BalancePort
        permits ExperienceProvider, VaultProvider, CoinsEngineProvider, PlayerPointsProvider {

    Currency currency();

    boolean available();

    String kind();

    Optional<Money> lookup(UUID playerId);

    boolean take(UUID playerId, Money money);

    void give(UUID playerId, Money money);

    @Override
    default Optional<Currency> supportedCurrency() {
        return available() ? Optional.of(currency()) : Optional.empty();
    }

    @Override
    default Optional<Money> balance(UUID playerId) {
        return available() ? lookup(playerId) : Optional.empty();
    }

    @Override
    default boolean withdraw(UUID playerId, Money money) {
        return available() && take(playerId, money);
    }

    @Override
    default void refund(UUID playerId, Money money) {
        if (available()) {
            give(playerId, money);
        }
    }

    @Override
    default void deposit(UUID playerId, Money money) {
        if (available()) {
            give(playerId, money);
        }
    }
}
