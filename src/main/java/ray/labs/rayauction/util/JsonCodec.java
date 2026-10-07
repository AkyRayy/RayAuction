package ray.labs.rayauction.util;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public final class JsonCodec {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private JsonCodec() {}

    public static String encode(Map<String, String> payload) {
        return GSON.toJson(payload == null ? Map.of() : payload);
    }

    public static Map<String, String> decode(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> decoded = GSON.fromJson(json, MAP_TYPE);
        if (decoded == null) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : decoded.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return Map.copyOf(normalized);
    }

    public static String stringify(JsonElement element) {
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    public static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json == null ? "{}" : json);
        return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }
}
