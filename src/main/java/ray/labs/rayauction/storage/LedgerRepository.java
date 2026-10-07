package ray.labs.rayauction.storage;

import java.math.BigDecimal;
import java.util.UUID;

import ray.labs.rayauction.domain.Currency;

public interface LedgerRepository {

    BigDecimal balance(UUID playerId, Currency currency);

    boolean tryWithdraw(UUID playerId, Currency currency, BigDecimal amount);

    void credit(UUID playerId, Currency currency, BigDecimal amount);
}
