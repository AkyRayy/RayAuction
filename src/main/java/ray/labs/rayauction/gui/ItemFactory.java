package ray.labs.rayauction.gui;

import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemFactory {

    private ItemFactory() {}

    public static ItemStack icon(String materialName, int customModelData, Component name, List<Component> lore, boolean glow) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null || !material.isItem()) {
            material = Material.PAPER;
        }
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(name);
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        if (customModelData > 0) {
            org.bukkit.inventory.meta.components.CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(component);
        }
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack display(ItemStack source, Component name, List<Component> lore) {
        ItemStack stack = source.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (name != null) {
            meta.displayName(name);
        }
        if (lore != null) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack filler(String materialName) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null || !material.isItem()) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
