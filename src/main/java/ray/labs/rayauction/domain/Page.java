package ray.labs.rayauction.domain;

import java.util.List;

public record Page<T>(List<T> items, int page, int perPage, int total) {

    public Page {
        items = List.copyOf(items);
        page = Math.max(0, page);
        perPage = Math.max(1, perPage);
        total = Math.max(0, total);
    }

    public static <T> Page<T> of(List<T> source, int page, int perPage) {
        int safePage = Math.max(0, page);
        int safePerPage = Math.max(1, perPage);
        int from = safePage * safePerPage;
        if (from >= source.size()) {
            return new Page<>(List.of(), safePage, safePerPage, source.size());
        }
        int to = Math.min(source.size(), from + safePerPage);
        return new Page<>(source.subList(from, to), safePage, safePerPage, source.size());
    }

    public int totalPages() {
        return perPage == 0 ? 1 : Math.max(1, (total + perPage - 1) / perPage);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public int displayPage() {
        return page + 1;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
