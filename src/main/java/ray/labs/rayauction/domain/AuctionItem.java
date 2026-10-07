package ray.labs.rayauction.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public record AuctionItem(
        byte[] data,
        String material,
        String displayName,
        String loreText,
        String category,
        int amount,
        boolean container,
        int containerSize) {

    public AuctionItem {
        Objects.requireNonNull(data, "data");
        data = data.clone();
        material = Objects.requireNonNull(material, "material");
        displayName = displayName == null ? "" : displayName;
        loreText = loreText == null ? "" : loreText;
        category = category == null ? ItemCategory.OTHER.key() : category.toLowerCase(Locale.ROOT);
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public byte[] rawData() {
        return data;
    }

    public boolean matchesName(String query) {
        return contains(displayName, query) || contains(material, query);
    }

    public boolean matchesLore(String query) {
        return contains(loreText, query);
    }

    private static boolean contains(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuctionItem item)) {
            return false;
        }
        return amount == item.amount
                && container == item.container
                && containerSize == item.containerSize
                && material.equals(item.material)
                && displayName.equals(item.displayName)
                && loreText.equals(item.loreText)
                && category.equals(item.category)
                && Arrays.equals(data, item.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(material, displayName, loreText, category, amount, container, containerSize);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "AuctionItem[material=" + material + ", amount=" + amount + ", bytes=" + data.length + ']';
    }
}
