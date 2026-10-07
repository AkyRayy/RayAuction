package ray.labs.rayauction.domain;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationSpecTest {

    @Test
    void parsesSimpleUnits() {
        assertThat(DurationSpec.parse("30m")).get().extracting(DurationSpec::seconds).isEqualTo(1800L);
        assertThat(DurationSpec.parse("6h")).get().extracting(DurationSpec::seconds).isEqualTo(21600L);
        assertThat(DurationSpec.parse("1d")).get().extracting(DurationSpec::seconds).isEqualTo(86400L);
        assertThat(DurationSpec.parse("2w")).get().extracting(DurationSpec::seconds).isEqualTo(1209600L);
        assertThat(DurationSpec.parse("45s")).get().extracting(DurationSpec::seconds).isEqualTo(45L);
    }

    @Test
    void parsesLongUnitNamesCaseInsensitive() {
        assertThat(DurationSpec.parse(" 2 Hours ")).get().extracting(DurationSpec::seconds).isEqualTo(7200L);
        assertThat(DurationSpec.parse("3MINUTES")).get().extracting(DurationSpec::seconds).isEqualTo(180L);
    }

    @Test
    void parsesCompoundValues() {
        assertThat(DurationSpec.parse("2d 12h")).get().extracting(DurationSpec::seconds).isEqualTo(216000L);
        assertThat(DurationSpec.parse("1h,30m")).get().extracting(DurationSpec::seconds).isEqualTo(5400L);
        assertThat(DurationSpec.parse("1.5h")).get().extracting(DurationSpec::seconds).isEqualTo(5400L);
    }

    @Test
    void rejectsInvalidInput() {
        assertThat(DurationSpec.parse(null)).isEmpty();
        assertThat(DurationSpec.parse("   ")).isEmpty();
        assertThat(DurationSpec.parse("0m")).isEmpty();
        assertThat(DurationSpec.parse("5")).isEmpty();
        assertThat(DurationSpec.parse("m")).isEmpty();
        assertThat(DurationSpec.parse("5x")).isEmpty();
        assertThat(DurationSpec.parse("5m 2x")).isEmpty();
        assertThat(DurationSpec.parse("-5m")).isEmpty();
    }

    @Test
    void parseAllSkipsInvalidEntries() {
        assertThat(DurationSpec.parseAll(List.of("1h", "bogus", "2d"))).hasSize(2);
    }

    @Test
    void keepsRawLabel() {
        assertThat(DurationSpec.parse(" 1D ").orElseThrow().label()).isEqualTo("1D");
    }

    @Test
    void rejectsNonPositiveDuration() {
        assertThatThrownBy(() -> new DurationSpec("0", Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurationSpec("-1", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
