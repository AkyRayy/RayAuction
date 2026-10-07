package ray.labs.rayauction.gui;

import ray.labs.rayauction.domain.ItemCategory;
import ray.labs.rayauction.domain.SortType;

public record ViewState(ItemCategory category, SortType sort, String query, int page) {

    public static final ViewState DEFAULT = new ViewState(ItemCategory.ALL, SortType.DATE_DESC, "", 0);

    public ViewState {
        category = category == null ? ItemCategory.ALL : category;
        sort = sort == null ? SortType.DATE_DESC : sort;
        query = query == null ? "" : query.trim();
        page = Math.max(0, page);
    }

    public ViewState nextPage() {
        return new ViewState(category, sort, query, page + 1);
    }

    public ViewState previousPage() {
        return new ViewState(category, sort, query, Math.max(0, page - 1));
    }

    public ViewState page(int value) {
        return new ViewState(category, sort, query, value);
    }

    public ViewState category(ItemCategory value) {
        return new ViewState(value, sort, query, 0);
    }

    public ViewState sort(SortType value) {
        return new ViewState(category, value, query, 0);
    }

    public ViewState query(String value) {
        return new ViewState(category, sort, value, 0);
    }
}
