package ray.labs.rayauction.gui.button;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record NavigationButton(
        int slot,
        ItemStack icon,
        String id,
        Map<String, String> placeholders,
        ClickHandler handler)
        implements Button {

    public static NavigationButton of(int slot, ItemStack icon, String id, ClickHandler handler) {
        return new NavigationButton(slot, icon, id, Map.of(), handler);
    }

    public NavigationButton withPlaceholders(Map<String, String> values) {
        return new NavigationButton(slot, icon, id, values, handler);
    }

    public List<String> placeholderKeys() {
        return List.copyOf(placeholders.keySet());
    }

    @Override
    public ItemStack icon(Player viewer) {
        return icon.clone();
    }
}
