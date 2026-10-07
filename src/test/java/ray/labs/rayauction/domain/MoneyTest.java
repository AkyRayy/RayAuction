package ray.labs.rayauction.domain;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private final Currency vault = new Currency.Vault("", "Monety", "$", 2);
    private final Currency experience = new Currency.Experience("Opyt", "XP");

    @Test
    void appliesCurrencyScaleOnCreation() {
        Money money = Money.of(new BigDecimal("10.4567"), vault);

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("10.46"));
    }

    @Test
    void experienceHasZeroScale() {
        Money money = Money.of(new BigDecimal("10.6"), experience);

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("11"));
    }

    @Test
    void parsesCommaDecimalSeparator() {
        assertThat(Money.parse(" 1 500,50 ", vault))
                .get()
                .extracting(money -> money.amount().toPlainString())
                .isEqualTo("1500.50");
    }

    @Test
    void rejectsNegativeAndGarbage() {
        assertThat(Money.parse("-5", vault)).isEmpty();
        assertThat(Money.parse("abc", vault)).isEmpty();
        assertThat(Money.parse("", vault)).isEmpty();
        assertThat(Money.parse(null, vault)).isEmpty();
    }

    @Test
    void addAndSubtractKeepCurrency() {
        Money sum = Money.of(new BigDecimal("1.005"), vault).add(Money.of(new BigDecimal("2.005"), vault));

        assertThat(sum.amount().toPlainString()).isEqualTo("3.02");
        assertThat(sum.subtract(Money.of(new BigDecimal("0.01"), vault)).amount().toPlainString()).isEqualTo("3.01");
    }

    @Test
    void rejectsMixedCurrencies() {
        Money left = Money.of(BigDecimal.ONE, vault);
        Money right = Money.of(BigDecimal.ONE, experience);

        assertThatThrownBy(() -> left.add(right)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mismatch");
    }

    @Test
    void mergesSameCurrencies() {
        List<Money> merged =
                Money.merge(List.of(Money.of(BigDecimal.ONE, vault), Money.of(new BigDecimal("2"), vault)));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).amount()).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void wholeUnitsOnlyForIntegralAmounts() {
        assertThat(Money.of(new BigDecimal("12"), experience).fitsIntoWholeUnits()).isTrue();
        assertThat(Money.of(new BigDecimal("12.50"), vault).fitsIntoWholeUnits()).isFalse();
        assertThat(Money.of(new BigDecimal("12.00"), vault).wholeUnits()).isEqualTo(12L);
    }

    @Test
    void currencyIdsAreNormalized() {
        assertThat(new Currency.Vault("", "A", "$", 2).id()).isEqualTo("vault");
        assertThat(new Currency.CoinsEngine("Coins", "A", "*", 0).id()).isEqualTo("coinsengine:coins");
        assertThat(new Currency.PlayerPoints("A", "PP").id()).isEqualTo("playerpoints");
        assertThat(new Currency.Experience("A", "XP").isExperience()).isTrue();
    }
}
