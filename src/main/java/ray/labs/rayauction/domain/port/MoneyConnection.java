package ray.labs.rayauction.domain.port;

import java.math.BigDecimal;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;

public interface MoneyConnection {

    BigDecimal getBalance(UUID playerId, Currency currency);

    boolean tryWithdraw(UUID playerId, Currency currency, BigDecimal amount);

    void deposit(UUID playerId, Currency currency, BigDecimal amount);
}
