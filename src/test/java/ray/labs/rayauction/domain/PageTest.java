package ray.labs.rayauction.domain;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageTest {

    private final List<Integer> source = IntStream.rangeClosed(1, 10).boxed().toList();

    @Test
    void slicesFirstPage() {
        Page<Integer> page = Page.of(source, 0, 4);

        assertThat(page.items()).containsExactly(1, 2, 3, 4);
        assertThat(page.total()).isEqualTo(10);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.displayPage()).isEqualTo(1);
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void slicesLastPartialPage() {
        Page<Integer> page = Page.of(source, 2, 4);

        assertThat(page.items()).containsExactly(9, 10);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void outOfRangePageIsEmpty() {
        Page<Integer> page = Page.of(source, 99, 4);

        assertThat(page.items()).isEmpty();
        assertThat(page.isEmpty()).isTrue();
        assertThat(page.total()).isEqualTo(10);
    }

    @Test
    void clampsNegativeValues() {
        Page<Integer> page = Page.of(source, -5, 0);

        assertThat(page.page()).isZero();
        assertThat(page.perPage()).isEqualTo(1);
        assertThat(page.items()).containsExactly(1);
    }

    @Test
    void emptySourceHasOnePage() {
        Page<Integer> page = Page.of(List.of(), 0, 10);

        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.isEmpty()).isTrue();
    }
}
