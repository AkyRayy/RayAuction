package ray.labs.rayauction.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import ray.labs.rayauction.config.CurrencySettings;
import ray.labs.rayauction.config.EconomyConfig;
import ray.labs.rayauction.domain.Currency;
import ray.labs.rayauction.domain.port.BalancePort;
import ray.labs.rayauction.domain.port.OnlinePort;
import ray.labs.rayauction.economy.bridge.BalanceBridge;
import ray.labs.rayauction.economy.bridge.CoinsEngineBridge;
import ray.labs.rayauction.economy.bridge.PlayerPointsBridge;
import ray.labs.rayauction.economy.bridge.VaultBridge;
import ray.labs.rayauction.storage.LedgerRepository;

public final class EconomyFactory {

    private EconomyFactory() {}

    public static List<EconomyProvider> providers(
            EconomyConfig config, LedgerRepository ledger, OnlinePort online, Logger logger) {
        List<EconomyProvider> providers = new ArrayList<>();
        for (CurrencySettings settings : config.currencies()) {
            Optional<Currency> currency = settings.toCurrency();
            if (currency.isEmpty()) {
                logger.warning("unknown currency type in config: " + settings.kind());
                continue;
            }
            create(currency.get(), ledger, online, logger).ifPresent(providers::add);
        }
        return List.copyOf(providers);
    }

    public static List<BalancePort> ports(List<EconomyProvider> providers) {
        List<BalancePort> ports = new ArrayList<>(providers.size());
        ports.addAll(providers);
        return List.copyOf(ports);
    }

    private static Optional<EconomyProvider> create(
            Currency currency, LedgerRepository ledger, OnlinePort online, Logger logger) {
        return switch (currency) {
            case Currency.Experience experience -> Optional.of(new ExperienceProvider(experience, ledger, online));
            case Currency.Vault vault -> vaultBridge(logger).map(bridge -> new VaultProvider(vault, bridge));
            case Currency.CoinsEngine coins -> CoinsEngineBridge.create(coins.currencyId(), logger)
                    .map(bridge -> new CoinsEngineProvider(coins, bridge));
            case Currency.PlayerPoints points -> playerPointsBridge(logger)
                    .map(bridge -> new PlayerPointsProvider(points, bridge));
        };
    }

    private static Optional<BalanceBridge> vaultBridge(Logger logger) {
        return VaultBridge.create(logger).map(bridge -> (BalanceBridge) bridge);
    }

    private static Optional<BalanceBridge> playerPointsBridge(Logger logger) {
        return PlayerPointsBridge.create(logger).map(bridge -> (BalanceBridge) bridge);
    }
}
