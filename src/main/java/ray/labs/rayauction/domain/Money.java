package ray.labs.rayauction.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(currency.scale(), RoundingMode.HALF_UP);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Optional<Money> parse(String raw, Currency currency) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().replace(",", ".").replace(" ", "");
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        try {
            BigDecimal value = new BigDecimal(normalized);
            if (value.signum() < 0) {
                return Optional.empty();
            }
            return Optional.of(new Money(value, currency));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterOrEqual(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) >= 0;
    }

    public boolean fitsIntoWholeUnits() {
        return currency.scale() == 0 || amount.stripTrailingZeros().scale() <= 0;
    }

    public long wholeUnits() {
        return amount.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.id().equals(other.currency.id())) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency.id() + " vs " + other.currency.id());
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.symbol();
    }

    public static List<Money> merge(List<Money> values) {
        List<Money> merged = new ArrayList<>();
        for (Money money : values) {
            boolean replaced = false;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).currency().id().equals(money.currency().id())) {
                    merged.set(i, merged.get(i).add(money));
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                merged.add(money);
            }
        }
        return Collections.unmodifiableList(merged);
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
