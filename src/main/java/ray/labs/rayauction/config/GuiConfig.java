package ray.labs.rayauction.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record GuiConfig(
        Map<String, MenuConfig> menus,
        Map<String, ButtonConfig> buttons,
        int itemSlotStart,
        int itemSlotEnd,
        String fillMaterial,
        boolean fillEnabled,
        boolean closeOnPurchase) {

    public record MenuConfig(String id, String title, int size) {

        public MenuConfig {
            size = Math.max(9, Math.min(54, (size / 9) * 9));
        }
    }

    public record ButtonConfig(
            String id,
            String material,
            int customModelData,
            String name,
            List<String> lore,
            int slot,
            String menu,
            boolean glow) {

        public ButtonConfig {
            material = material == null || material.isBlank() ? "PAPER" : material.trim().toUpperCase(java.util.Locale.ROOT);
            name = name == null ? "" : name;
            lore = lore == null ? List.of() : List.copyOf(lore);
            menu = menu == null ? "" : menu;
        }
    }

    public GuiConfig {
        menus = menus == null ? Map.of() : Map.copyOf(menus);
        buttons = buttons == null ? Map.of() : Map.copyOf(buttons);
        itemSlotStart = Math.max(0, itemSlotStart);
        itemSlotEnd = Math.max(itemSlotStart, itemSlotEnd);
        fillMaterial = fillMaterial == null || fillMaterial.isBlank()
                ? "GRAY_STAINED_GLASS_PANE"
                : fillMaterial.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public MenuConfig menu(String id, String defaultTitle, int defaultSize) {
        MenuConfig config = menus.get(id);
        return config == null ? new MenuConfig(id, defaultTitle, defaultSize) : config;
    }

    public Optional<ButtonConfig> button(String id) {
        return Optional.ofNullable(buttons.get(id));
    }

    public List<ButtonConfig> buttonsOf(String menuId) {
        List<ButtonConfig> result = new ArrayList<>();
        for (ButtonConfig config : buttons.values()) {
            if (config.menu().equals(menuId)) {
                result.add(config);
            }
        }
        result.sort((left, right) -> Integer.compare(left.slot(), right.slot()));
        return List.copyOf(result);
    }

    public int[] itemSlots() {
        int count = itemSlotEnd - itemSlotStart + 1;
        int[] slots = new int[Math.max(0, count)];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = itemSlotStart + i;
        }
        return slots;
    }

    public static GuiConfig from(YamlNode root) {
        Map<String, MenuConfig> menus = new LinkedHashMap<>();
        for (Map.Entry<String, YamlNode> entry : root.section("menus").sections().entrySet()) {
            YamlNode node = entry.getValue();
            menus.put(
                    entry.getKey(),
                    new MenuConfig(entry.getKey(), node.string("title", entry.getKey()), node.integer("size", 54)));
        }
        Map<String, ButtonConfig> buttons = new LinkedHashMap<>();
        int autoSlot = -1;
        for (Map.Entry<String, YamlNode> entry : root.section("buttons").sections().entrySet()) {
            YamlNode node = entry.getValue();
            int slot = node.integer("slot", -1);
            if (slot < 0) {
                slot = ++autoSlot;
            } else {
                autoSlot = slot;
            }
            buttons.put(
                    entry.getKey(),
                    new ButtonConfig(
                            entry.getKey(),
                            node.string("material", "PAPER"),
                            node.integer("custom-model-data", 0),
                            node.string("name", entry.getKey()),
                            node.stringList("lore", List.of()),
                            slot,
                            node.string("menu", "browser"),
                            node.bool("glow", false)));
        }
        YamlNode layout = root.section("layout");
        YamlNode fill = root.section("fill");
        return new GuiConfig(
                menus,
                buttons,
                layout.integer("item-slot-start", 9),
                layout.integer("item-slot-end", 44),
                fill.string("material", "GRAY_STAINED_GLASS_PANE"),
                fill.bool("enabled", true),
                root.section("behaviour").bool("close-on-purchase", false));
    }
}
