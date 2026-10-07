package ray.labs.rayauction.domain.port;

import java.util.Optional;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.Money;

public interface BalancePort {

    Optional<Currency> supportedCurrency();

    Optional<Money> balance(UUID playerId);

    boolean withdraw(UUID playerId, Money money);

    void refund(UUID playerId, Money money);

    void deposit(UUID playerId, Money money);
}
