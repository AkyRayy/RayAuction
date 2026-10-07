package ray.labs.rayauction.economy.bridge;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface BalanceBridge {

    String name();

    boolean available();

    Optional<BigDecimal> getBalance(UUID playerId);

    boolean withdraw(UUID playerId, BigDecimal amount);

    boolean deposit(UUID playerId, BigDecimal amount);
}
