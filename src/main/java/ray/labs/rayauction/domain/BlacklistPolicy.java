package ray.labs.rayauction.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record BlacklistPolicy(
        List<String> materials,
        List<String> loreContains,
        List<NbtRule> nbtRules,
        String bypassPermission,
        boolean blocksShulkerBoxes) {

    public BlacklistPolicy {
        materials = normalized(materials);
        loreContains = loreContains == null ? List.of() : List.copyOf(loreContains);
        nbtRules = nbtRules == null ? List.of() : List.copyOf(nbtRules);
        bypassPermission = bypassPermission == null ? "" : bypassPermission;
    }

    public record NbtRule(String key, String value) {

        public NbtRule {
            key = key == null ? "" : key.trim();
            value = value == null ? "" : value.trim();
        }
    }

    public record Subject(String material, String displayName, String loreText, Map<String, String> tags) {

        public Subject {
            material = material == null ? "" : material.toUpperCase(Locale.ROOT);
            displayName = displayName == null ? "" : displayName;
            loreText = loreText == null ? "" : loreText;
            tags = tags == null ? Map.of() : Map.copyOf(tags);
        }
    }

    public Optional<String> denyReason(Subject subject) {
        if (blocksShulkerBoxes
                && (subject.material().equals("SHULKER_BOX") || subject.material().endsWith("_SHULKER_BOX"))) {
            return Optional.of("blacklist-shulker");
        }
        if (materials.contains(subject.material())) {
            return Optional.of("blacklist-material");
        }
        for (String needle : loreContains) {
            if (!needle.isEmpty() && subject.loreText().contains(needle)) {
                return Optional.of("blacklist-lore");
            }
            if (!needle.isEmpty() && subject.displayName().contains(needle)) {
                return Optional.of("blacklist-lore");
            }
        }
        for (NbtRule rule : nbtRules) {
            if (rule.key().isEmpty()) {
                continue;
            }
            String actual = subject.tags().get(rule.key());
            if (actual != null && (rule.value().isEmpty() || actual.equalsIgnoreCase(rule.value()))) {
                return Optional.of("blacklist-nbt");
            }
        }
        return Optional.empty();
    }

    public boolean isBlocked(Subject subject) {
        return denyReason(subject).isPresent();
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .toList();
    }
}
