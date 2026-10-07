package ray.labs.rayauction.config;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class YamlNode {

    private final Map<String, Object> values;
    private final String path;

    private YamlNode(Map<String, Object> values, String path) {
        this.values = values;
        this.path = path;
    }

    public static YamlNode load(Path file) {
        if (!Files.exists(file)) {
            throw new ConfigException("missing configuration file " + file.getFileName());
        }
        try (InputStream stream = Files.newInputStream(file)) {
            return read(stream, file.getFileName().toString());
        } catch (IOException ex) {
            throw new ConfigException("cannot read " + file.getFileName(), ex);
        }
    }

    public static YamlNode loadResource(String resource) {
        InputStream stream = YamlNode.class.getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new ConfigException("missing bundled resource " + resource);
        }
        try (InputStream input = stream) {
            return read(input, resource);
        } catch (IOException ex) {
            throw new ConfigException("cannot read bundled resource " + resource, ex);
        }
    }

    private static YamlNode read(InputStream stream, String name) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(16 * 1024 * 1024);
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object root;
        try {
            root = yaml.load(stream);
        } catch (RuntimeException ex) {
            throw new ConfigException("invalid YAML in " + name + ": " + ex.getMessage(), ex);
        }
        if (root == null) {
            return new YamlNode(Map.of(), name);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new ConfigException("root of " + name + " must be a mapping");
        }
        return new YamlNode(stringify(map), name);
    }

    public String path() {
        return path;
    }

    public YamlNode section(String key) {
        Object value = values.get(key);
        String childPath = path + "." + key;
        if (value == null) {
            return new YamlNode(Map.of(), childPath);
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConfigException(childPath + " must be a section");
        }
        return new YamlNode(stringify(map), childPath);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public String string(String key, String fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    public String requireString(String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new ConfigException(path + "." + key + " is required");
        }
        return String.valueOf(value);
    }

    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(text);
        }
        throw new ConfigException(path + "." + key + " must be true or false, got: " + text);
    }

    public int integer(String key, int fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ConfigException(path + "." + key + " must be an integer", ex);
        }
    }

    public long longInteger(String key, long fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ConfigException(path + "." + key + " must be a number", ex);
        }
    }

    public double doubleValue(String key, double fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ConfigException(path + "." + key + " must be a decimal number", ex);
        }
    }

    public BigDecimal decimal(String key, BigDecimal fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ConfigException(path + "." + key + " must be a decimal number", ex);
        }
    }

    public List<String> stringList(String key, List<String> fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof List<?> list)) {
            throw new ConfigException(path + "." + key + " must be a list");
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry == null) {
                continue;
            }
            result.add(String.valueOf(entry));
        }
        return List.copyOf(result);
    }

    public List<YamlNode> nodeList(String key) {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ConfigException(path + "." + key + " must be a list of sections");
        }
        List<YamlNode> nodes = new ArrayList<>(list.size());
        int index = 0;
        for (Object entry : list) {
            String childPath = path + "." + key + "[" + index + "]";
            if (!(entry instanceof Map<?, ?> map)) {
                throw new ConfigException(childPath + " must be a section");
            }
            nodes.add(new YamlNode(stringify(map), childPath));
            index++;
        }
        return List.copyOf(nodes);
    }

    public Map<String, YamlNode> sections() {
        Map<String, YamlNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> child) {
                nodes.put(entry.getKey(), new YamlNode(stringify(child), path + "." + entry.getKey()));
            }
        }
        return Map.copyOf(nodes);
    }

    public Map<String, YamlNode> sections(String key) {
        Object value = values.get(key);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConfigException(path + "." + key + " must be a section map");
        }
        Map<String, YamlNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> child)) {
                throw new ConfigException(path + "." + key + "." + name + " must be a section");
            }
            nodes.put(name, new YamlNode(stringify(child), path + "." + key + "." + name));
        }
        return Map.copyOf(nodes);
    }

    public Map<String, String> stringMap(String key) {
        Object value = values.get(key);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConfigException(path + "." + key + " must be a map");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    public Map<String, Object> raw() {
        return values;
    }

    private static Map<String, Object> stringify(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return Map.copyOf(result);
    }
}
