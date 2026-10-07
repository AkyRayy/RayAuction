package ray.labs.rayauction.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public record Messages(Map<String, String> raw, String prefix, List<String> unprefixed, MiniMessage miniMessage) {

    public static final Messages FALLBACK = new Messages(Map.of(), "", List.of("gui."), MiniMessage.miniMessage());

    public static Messages load(Path file) {
        YamlNode root = YamlNode.load(file);
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", root.raw(), flat);
        String prefix = flat.getOrDefault("prefix", "");
        List<String> unprefixed = root.stringList("unprefixed-keys", List.of("gui."));
        return new Messages(Map.copyOf(flat), prefix, unprefixed, MiniMessage.miniMessage());
    }

    private boolean prefixed(String key) {
        if (prefix.isEmpty() || key.equals("prefix")) {
            return false;
        }
        for (String pattern : unprefixed) {
            if (!pattern.isEmpty() && key.startsWith(pattern)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> source, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> child) {
                flatten(key, (Map<String, Object>) child, target);
                continue;
            }
            if (value instanceof List<?> list) {
                List<String> lines = new ArrayList<>(list.size());
                for (Object line : list) {
                    lines.add(String.valueOf(line));
                }
                target.put(key, String.join("\n", lines));
                continue;
            }
            target.put(key, value == null ? "" : String.valueOf(value));
        }
    }

    public boolean has(String key) {
        return raw.containsKey(key);
    }

    public Component render(String key, Map<String, String> placeholders) {
        String template = raw.get(key);
        if (template == null) {
            return Component.text("missing message: " + key);
        }
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size() + 1);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }
        Component body = miniMessage.deserialize(template, resolvers.toArray(TagResolver[]::new));
        return prefixed(key) ? miniMessage.deserialize(prefix).append(body) : body;
    }

    public Component render(String key) {
        return render(key, Map.of());
    }

    public Component render(String key, TagResolver... resolvers) {
        String template = raw.get(key);
        if (template == null) {
            return Component.text("missing message: " + key);
        }
        Component body = miniMessage.deserialize(template, resolvers);
        return prefixed(key) ? miniMessage.deserialize(prefix).append(body) : body;
    }

    public String raw(String key, String fallback) {
        return raw.getOrDefault(key, fallback);
    }

    public List<Component> renderList(String key, Map<String, String> placeholders) {
        String template = raw.get(key);
        if (template == null || template.isBlank()) {
            return List.of();
        }
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }
        String[] lines = template.split("\n");
        List<Component> components = new ArrayList<>(lines.length);
        for (String line : lines) {
            components.add(miniMessage.deserialize(line, resolvers.toArray(TagResolver[]::new)));
        }
        return List.copyOf(components);
    }
}
