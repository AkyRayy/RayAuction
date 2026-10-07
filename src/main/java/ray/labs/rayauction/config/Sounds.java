package ray.labs.rayauction.config;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record Sounds(Map<String, SoundSpec> specs) {

    public static final Sounds EMPTY = new Sounds(Map.of());

    public Sounds {
        specs = specs == null ? Map.of() : Map.copyOf(specs);
    }

    public SoundSpec get(String key) {
        if (key == null) {
            return SoundSpec.OFF;
        }
        return specs.getOrDefault(key.toLowerCase(Locale.ROOT), SoundSpec.OFF);
    }

    public static Sounds from(YamlNode node) {
        Map<String, SoundSpec> parsed = new TreeMap<>();
        for (Map.Entry<String, YamlNode> entry : node.sections().entrySet()) {
            String name = entry.getValue().string("name", "");
            if (name.isBlank()) {
                continue;
            }
            parsed.put(
                    entry.getKey().trim().toLowerCase(Locale.ROOT),
                    new SoundSpec(
                            name.trim(),
                            (float) entry.getValue().doubleValue("volume", 1.0),
                            (float) entry.getValue().doubleValue("pitch", 1.0)));
        }
        return new Sounds(parsed);
    }

    public record SoundSpec(String name, float volume, float pitch) {

        public static final SoundSpec OFF = new SoundSpec("", 0.0f, 1.0f);

        public SoundSpec {
            name = name == null ? "" : name.trim();
            volume = Math.max(0.0f, Math.min(10.0f, volume));
            pitch = Math.max(0.5f, Math.min(2.0f, pitch));
        }

        public boolean enabled() {
            return !name.isEmpty();
        }
    }
}
