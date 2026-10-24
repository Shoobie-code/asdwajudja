package com.skyblockminer.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the macro jar to run from the repo's GitHub releases.
 *
 * <p>CI publishes a numbered release for every push to {@code main} ({@code 2.1.0-r7}) and a prerelease for every
 * push to {@code beta} ({@code 2.1.0-beta-r3}). Stable takes the newest full release, Beta the newest of either.
 * Rolling {@code build-*} releases (branch previews and the old {@code build-latest}) are never picked.
 */
public final class Releases {
    public enum Channel {
        STABLE("Stable"), BETA("Beta");

        public final String label;

        Channel(String label) {
            this.label = label;
        }

        public static Channel parse(String name) {
            return "beta".equalsIgnoreCase(name) ? BETA : STABLE;
        }
    }

    public record Asset(long id, String name, String url, long size, Instant updatedAt, String sha256) {
    }

    public record Release(String tag, boolean prerelease, boolean draft, List<Asset> assets) {
        /**
         * The jar for this Minecraft version ({@code skyblock-macro-26.2.jar}), or for releases made before jars were
         * named by version, the most recently uploaded macro jar.
         */
        public Optional<Asset> macroJar(String minecraft) {
            String exact = "skyblock-macro-" + minecraft + ".jar";
            Optional<Asset> named = this.assets.stream().filter(a -> a.name().equalsIgnoreCase(exact)).findFirst();
            if (named.isPresent()) {
                return named;
            }
            return this.assets.stream().filter(a -> isMacroJar(a.name())).max(Comparator.comparing(Asset::updatedAt));
        }
    }

    public record Pick(Release release, Asset asset) {
    }

    private Releases() {
    }

    static boolean isMacroJar(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jar") && n.startsWith("skyblock-macro") && !n.contains("loader") && !n.endsWith("-sources.jar");
    }

    public static Optional<Pick> pick(List<Release> releases, Channel channel, String minecraft) {
        List<Pick> candidates = new ArrayList<>();
        for (Release r : releases) {
            if (r.draft() || r.tag().startsWith("build-") || (r.prerelease() && channel == Channel.STABLE)) {
                continue;
            }
            r.macroJar(minecraft).ifPresent(a -> candidates.add(new Pick(r, a)));
        }
        return candidates.stream().max(Comparator.comparing(p -> p.asset().updatedAt()));
    }

    /** The release that published the jar with this SHA-256, so the title screen can say what is running. */
    public static Optional<Release> withHash(List<Release> releases, String sha256) {
        return releases.stream()
            .filter(r -> r.assets().stream().anyMatch(a -> sha256.equalsIgnoreCase(a.sha256())))
            .findFirst();
    }

    /** Parses the body of {@code GET /repos/{owner}/{repo}/releases}. */
    public static List<Release> parse(String json) {
        List<Release> out = new ArrayList<>();
        for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject r = e.getAsJsonObject();
            List<Asset> assets = new ArrayList<>();
            JsonArray list = r.has("assets") ? r.getAsJsonArray("assets") : new JsonArray();
            for (JsonElement ae : list) {
                JsonObject a = ae.getAsJsonObject();
                String digest = string(a, "digest");
                assets.add(new Asset(a.get("id").getAsLong(), string(a, "name"), string(a, "browser_download_url"),
                    a.has("size") ? a.get("size").getAsLong() : -1L, instant(a, "updated_at"),
                    digest != null && digest.startsWith("sha256:") ? digest.substring(7) : null));
            }
            out.add(new Release(string(r, "tag_name"), bool(r, "prerelease"), bool(r, "draft"), assets));
        }
        return out;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    private static Instant instant(JsonObject o, String key) {
        String s = string(o, key);
        return s == null ? Instant.EPOCH : Instant.parse(s);
    }
}
