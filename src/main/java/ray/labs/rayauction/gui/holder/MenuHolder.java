package ray.labs.rayauction.gui.holder;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class MenuHolder implements InventoryHolder {

    public interface Action {

        void run(Player player, ClickType clickType);
    }

    private final Map<Integer, Action> actions = new HashMap<>();
    private Inventory inventory;

    public final void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    public final void bind(int slot, Action action) {
        actions.put(slot, action);
    }

    public final void clearBindings() {
        actions.clear();
    }

    public final boolean handles(int slot) {
        return actions.containsKey(slot);
    }

    public final void click(Player player, int slot, ClickType clickType) {
        Action action = actions.get(slot);
        if (action != null) {
            action.run(player, clickType);
        }
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    public abstract String menuId();
}
