package com.skyblockminer.macro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.skyblockminer.MinerMod;
import com.skyblockminer.config.MinerConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class Webhook {
    static final Pattern URL = Pattern.compile("^https://(?:ptb\\.|canary\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[^\\s/]+/?$");
    /** Events a user can switch off under Alerts → Discord. Failsafes and chat alerts always send. */
    public static final List<String> EVENTS = List.of("stop", "rejoin", "pest", "commission", "slayer", "status", "waiting");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();

    /** Whether the user has left this event on in {@code discordEvents}. */
    static boolean wants(MinerConfig config, String event) {
        return config.discordEvents != null && Arrays.asList(config.discordEvents.split(",")).stream().map(String::strip).anyMatch(event::equals);
    }

    void send(MinerConfig config, String title, String text, int color, boolean ping) {
        if (config.webhookUrl != null && URL.matcher(config.webhookUrl).matches()) {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", title);
            embed.addProperty("description", text);
            embed.addProperty("color", color);
            embed.addProperty("timestamp", Instant.now().toString());
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Skyblock Macro");
            embed.add("footer", footer);
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            JsonObject body = new JsonObject();
            body.addProperty("username", "Skyblock Macro");
            body.add("embeds", embeds);
            if (ping && config.pingId != null && !config.pingId.isBlank()) {
                body.addProperty("content", "<@" + config.pingId + ">");
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(config.webhookUrl))
                .timeout(Duration.ofSeconds(10L))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body.toString()))
                .build();
            this.client.sendAsync(request, BodyHandlers.discarding()).exceptionally(error -> {
                MinerMod.LOGGER.warn("Discord webhook failed", error);
                return null;
            });
        }
    }
}
