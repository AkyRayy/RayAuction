package ray.labs.rayauction.domain;

public record AuctionSearchFilter(
        String nameQuery,
        String loreQuery,
        String material,
        String sellerName,
        String currencyId,
        String category,
        SortType sort,
        int page,
        int perPage) {

    public AuctionSearchFilter {
        nameQuery = normalize(nameQuery);
        loreQuery = normalize(loreQuery);
        material = normalize(material);
        sellerName = normalize(sellerName);
        currencyId = normalize(currencyId);
        category = normalize(category);
        sort = sort == null ? SortType.DATE_DESC : sort;
        page = Math.max(0, page);
        perPage = Math.max(1, perPage);
    }

    public static AuctionSearchFilter empty(int perPage) {
        return new AuctionSearchFilter(null, null, null, null, null, null, SortType.DATE_DESC, 0, perPage);
    }

    public boolean isUnrestricted() {
        return nameQuery.isEmpty()
                && loreQuery.isEmpty()
                && material.isEmpty()
                && sellerName.isEmpty()
                && currencyId.isEmpty()
                && (category.isEmpty() || ItemCategory.ALL.matches(category));
    }

    public AuctionSearchFilter withPage(int newPage) {
        return new AuctionSearchFilter(
                nameQuery, loreQuery, material, sellerName, currencyId, category, sort, newPage, perPage);
    }

    public AuctionSearchFilter withSort(SortType newSort) {
        return new AuctionSearchFilter(
                nameQuery, loreQuery, material, sellerName, currencyId, category, newSort, 0, perPage);
    }

    public AuctionSearchFilter withName(String query) {
        return new AuctionSearchFilter(query, loreQuery, material, sellerName, currencyId, category, sort, 0, perPage);
    }

    public AuctionSearchFilter withCategory(ItemCategory newCategory) {
        return new AuctionSearchFilter(
                nameQuery,
                loreQuery,
                material,
                sellerName,
                currencyId,
                newCategory == null ? null : newCategory.key(),
                sort,
                0,
                perPage);
    }

    public AuctionSearchFilter withSeller(String seller) {
        return new AuctionSearchFilter(nameQuery, loreQuery, material, seller, currencyId, category, sort, 0, perPage);
    }

    public boolean matches(Auction auction) {
        if (!nameQuery.isEmpty() && !auction.item().matchesName(nameQuery)) {
            return false;
        }
        if (!loreQuery.isEmpty() && !auction.item().matchesLore(loreQuery)) {
            return false;
        }
        if (!material.isEmpty() && !auction.item().material().equalsIgnoreCase(material)) {
            return false;
        }
        if (!sellerName.isEmpty() && !auction.sellerName().equalsIgnoreCase(sellerName)) {
            return false;
        }
        if (!currencyId.isEmpty() && !auction.currency().id().equalsIgnoreCase(currencyId)) {
            return false;
        }
        if (!category.isEmpty()
                && !ItemCategory.ALL.matches(category)
                && !auction.item().category().equalsIgnoreCase(category)) {
            return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
