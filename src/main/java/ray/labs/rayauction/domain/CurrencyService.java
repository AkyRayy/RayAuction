package ray.labs.rayauction.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ray.labs.rayauction.domain.port.BalancePort;

public final class CurrencyService {

    private final CurrencyCatalog catalog;
    private final Map<String, BalancePort> balances;

    public CurrencyService(CurrencyCatalog catalog, List<BalancePort> balancePorts) {
        this.catalog = catalog;
        Map<String, BalancePort> byCurrency = new LinkedHashMap<>();
        for (BalancePort port : balancePorts) {
            port.supportedCurrency().ifPresent(currency -> byCurrency.put(currency.id(), port));
        }
        this.balances = Map.copyOf(byCurrency);
    }

    public CurrencyCatalog catalog() {
        return catalog;
    }

    public List<Currency> available() {
        List<Currency> available = new ArrayList<>();
        for (Currency currency : catalog.currencies()) {
            if (isAvailable(currency)) {
                available.add(currency);
            }
        }
        return List.copyOf(available);
    }

    public boolean isAvailable(Currency currency) {
        return balances.containsKey(currency.id());
    }

    public Optional<BalancePort> balancePort(Currency currency) {
        return Optional.ofNullable(balances.get(currency.id()));
    }

    public BalancePort requireBalancePort(Currency currency) {
        BalancePort port = balances.get(currency.id());
        if (port == null) {
            throw new IllegalStateException("no balance port registered for currency " + currency.id());
        }
        return port;
    }

    public Optional<Currency> resolve(String idOrKind) {
        Optional<Currency> byId = catalog.byId(idOrKind);
        if (byId.isPresent()) {
            return byId;
        }
        return catalog.byKind(idOrKind);
    }

    public Money tax(Money price, BigDecimal taxRate) {
        if (taxRate == null || taxRate.signum() <= 0) {
            return Money.zero(price.currency());
        }
        BigDecimal value = price.amount().multiply(taxRate).setScale(price.currency().scale(), RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            return Money.zero(price.currency());
        }
        if (value.compareTo(price.amount()) >= 0) {
            return new Money(price.amount(), price.currency());
        }
        return new Money(value, price.currency());
    }

    public Money sellerIncome(Money price, Money tax) {
        return price.subtract(tax);
    }
}
