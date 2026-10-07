package ray.labs.rayauction.economy.bridge;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

public final class VaultBridge implements BalanceBridge {

    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final Object economy;
    private final Method getBalance;
    private final Method withdrawPlayer;
    private final Method depositPlayer;
    private final Logger logger;

    private VaultBridge(Object economy, Method getBalance, Method withdrawPlayer, Method depositPlayer, Logger logger) {
        this.economy = economy;
        this.getBalance = getBalance;
        this.withdrawPlayer = withdrawPlayer;
        this.depositPlayer = depositPlayer;
        this.logger = logger;
    }

    public static Optional<VaultBridge> create(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return Optional.empty();
        }
        Optional<Class<?>> type = Reflection.type(ECONOMY_CLASS);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        ServicesManager services = Bukkit.getServicesManager();
        RegisteredServiceProvider<?> registration = services.getRegistration(type.get());
        if (registration == null) {
            return Optional.empty();
        }
        Object economy = registration.getProvider();
        if (economy == null) {
            return Optional.empty();
        }
        Optional<Method> balance = Reflection.method(economy.getClass(), "getBalance", OfflinePlayer.class);
        Optional<Method> withdraw = Reflection.method(economy.getClass(), "withdrawPlayer", OfflinePlayer.class, double.class);
        Optional<Method> deposit = Reflection.method(economy.getClass(), "depositPlayer", OfflinePlayer.class, double.class);
        if (balance.isEmpty() || withdraw.isEmpty() || deposit.isEmpty()) {
            logger.warning("Vault economy implementation has an unexpected API shape, integration disabled");
            return Optional.empty();
        }
        return Optional.of(new VaultBridge(economy, balance.get(), withdraw.get(), deposit.get(), logger));
    }

    @Override
    public String name() {
        return "Vault";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Optional<BigDecimal> getBalance(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return Reflection.invoke(logger, getBalance, economy, player).map(Reflection::decimal);
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        Object response = Reflection.invoke(logger, withdrawPlayer, economy, player, Reflection.fractional(amount))
                .orElse(null);
        return transactionSucceeded(response);
    }

    @Override
    public boolean deposit(UUID playerId, BigDecimal amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        Object response = Reflection.invoke(logger, depositPlayer, economy, player, Reflection.fractional(amount))
                .orElse(null);
        return transactionSucceeded(response);
    }

    private boolean transactionSucceeded(Object response) {
        if (response == null) {
            return false;
        }
        Optional<Method> succeeded = Reflection.method(response.getClass(), "transactionSuccess");
        if (succeeded.isEmpty()) {
            return true;
        }
        Object value = Reflection.invoke(logger, succeeded.get(), response).orElse(Boolean.FALSE);
        return Boolean.TRUE.equals(value);
    }
}
