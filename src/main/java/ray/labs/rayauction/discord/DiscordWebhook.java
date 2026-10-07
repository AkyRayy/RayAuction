package ray.labs.rayauction.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import ray.labs.rayauction.config.DiscordConfig;

public final class DiscordWebhook {

    private final DiscordConfig config;
    private final HttpClient client;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
    private final AtomicInteger failures = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0L);
    private final Logger logger;

    public DiscordWebhook(DiscordConfig config, Executor executor, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .executor(executor)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean enabled() {
        return config.usable();
    }

    public CompletableFuture<Void> sendNewAuction(Map<String, String> placeholders) {
        if (!config.notifyOnNewAuction()) {
            return CompletableFuture.completedFuture(null);
        }
        return send(config.newAuction(), placeholders);
    }

    public CompletableFuture<Void> sendPurchase(Map<String, String> placeholders) {
        if (!config.notifyOnPurchase()) {
            return CompletableFuture.completedFuture(null);
        }
        return send(config.purchase(), placeholders);
    }

    public CompletableFuture<Void> send(DiscordConfig.Embed embed, Map<String, String> placeholders) {
        if (!enabled() || isCoolingDown()) {
            return CompletableFuture.completedFuture(null);
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.webhookUrl()))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "RayAuction")
                    .POST(HttpRequest.BodyPublishers.ofString(body(embed, placeholders), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException ex) {
            logger.warning("invalid discord webhook url: " + ex.getMessage());
            return CompletableFuture.completedFuture(null);
        }
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        failures.set(0);
                        return;
                    }
                    trip("HTTP " + response.statusCode());
                })
                .exceptionally(ex -> {
                    trip(ex.getMessage());
                    return null;
                });
    }

    private void trip(String reason) {
        int count = failures.incrementAndGet();
        if (count >= config.failureThreshold() && openedAt.compareAndSet(0L, System.nanoTime())) {
            logger.warning("discord webhook failing (" + reason + "), paused for " + config.cooldownSeconds() + "s");
        }
    }

    private boolean isCoolingDown() {
        long opened = openedAt.get();
        if (opened == 0L) {
            return false;
        }
        if (System.nanoTime() - opened < Duration.ofSeconds(config.cooldownSeconds()).toNanos()) {
            return true;
        }
        openedAt.set(0L);
        failures.set(0);
        return false;
    }

    private String body(DiscordConfig.Embed embed, Map<String, String> placeholders) {
        JsonObject root = new JsonObject();
        if (!config.username().isBlank()) {
            root.addProperty("username", config.username());
        }
        if (!config.avatarUrl().isBlank()) {
            root.addProperty("avatar_url", config.avatarUrl());
        }
        JsonArray embeds = new JsonArray();
        JsonObject payload = new JsonObject();
        String title = render(embed.title(), placeholders);
        if (!title.isBlank()) {
            payload.addProperty("title", title);
        }
        String description = render(embed.description(), placeholders);
        if (!description.isBlank()) {
            payload.addProperty("description", description);
        }
        payload.addProperty("color", embed.colorValue());
        JsonArray fields = fields(embed, placeholders);
        if (fields.size() > 0) {
            payload.add("fields", fields);
        }
        embeds.add(payload);
        root.add("embeds", embeds);
        return root.toString();
    }

    private JsonArray fields(DiscordConfig.Embed embed, Map<String, String> placeholders) {
        JsonArray fields = new JsonArray();
        for (String line : embed.fields()) {
            String rendered = render(line, placeholders);
            int separator = rendered.indexOf('|');
            if (separator <= 0) {
                continue;
            }
            JsonObject field = new JsonObject();
            field.addProperty("name", rendered.substring(0, separator).trim());
            field.addProperty("value", rendered.substring(separator + 1).trim());
            field.addProperty("inline", true);
            fields.add(field);
        }
        return fields;
    }

    private String render(String template, Map<String, String> placeholders) {
        if (template == null || template.isBlank()) {
            return "";
        }
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }
        try {
            return plain.serialize(miniMessage.deserialize(template, resolvers.toArray(TagResolver[]::new)));
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "cannot render discord template: " + template, ex);
            return template;
        }
    }
}
