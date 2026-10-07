package ray.labs.rayauction.config;

import java.util.Optional;

import ray.labs.rayauction.domain.Currency;

public record CurrencySettings(String kind, String id, String displayName, String symbol, int scale, boolean enabled) {

    public CurrencySettings {
        kind = kind == null ? "" : kind.trim().toLowerCase(java.util.Locale.ROOT);
        id = id == null ? "" : id.trim();
        displayName = displayName == null || displayName.isBlank() ? kind : displayName;
        symbol = symbol == null || symbol.isBlank() ? kind : symbol;
        scale = Math.max(0, Math.min(4, scale));
    }

    public Optional<Currency> toCurrency() {
        return switch (kind) {
            case Currency.Experience.ID -> Optional.of(new Currency.Experience(displayName, symbol));
            case Currency.Vault.ID -> Optional.of(new Currency.Vault(id, displayName, symbol, scale));
            case "coinsengine" -> Optional.of(new Currency.CoinsEngine(id, displayName, symbol, scale));
            case Currency.PlayerPoints.ID -> Optional.of(new Currency.PlayerPoints(displayName, symbol));
            default -> Optional.empty();
        };
    }
}
