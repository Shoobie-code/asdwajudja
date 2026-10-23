package com.skyblockminer.loader;

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
 * <p>Stable is the newest non-prerelease {@code v*} tag, falling back to the rolling {@code build-latest} main build
 * while no version has been tagged. Beta is whichever of the {@code v*} releases (prereleases included) and the
 * {@code build-latest} main build was uploaded most recently. Feature-branch builds ({@code build-preview}) are never
 * picked.
 */
public final class Releases {
    public static final String MAIN_BUILD = "build-latest";
    public static final String BRANCH_BUILD = "build-preview";

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

    public record Asset(long id, String name, String url, Instant updatedAt, String sha256) {
    }

    public record Release(String tag, boolean prerelease, boolean draft, Instant publishedAt, List<Asset> assets) {
        /** The macro jar attached to this release, if any (the loader's own jar is skipped). */
        public Optional<Asset> macroJar() {
            return this.assets.stream().filter(a -> isMacroJar(a.name())).findFirst();
        }
    }

    public record Pick(Release release, Asset asset) {
        /** Short text for the title screen, e.g. "v2.1.0" or "main build". */
        public String label() {
            return MAIN_BUILD.equals(this.release.tag()) ? "main build" : this.release.tag();
        }
    }

    private Releases() {
    }

    static boolean isMacroJar(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jar") && n.startsWith("skyblock-macro") && !n.contains("loader") && !n.endsWith("-sources.jar");
    }

    public static Optional<Pick> pick(List<Release> releases, Channel channel) {
        List<Pick> candidates = new ArrayList<>();
        for (Release r : releases) {
            if (r.draft() || BRANCH_BUILD.equals(r.tag())) {
                continue;
            }
            boolean tagged = r.tag().startsWith("v");
            if (!tagged && !MAIN_BUILD.equals(r.tag())) {
                continue;
            }
            r.macroJar().ifPresent(a -> candidates.add(new Pick(r, a)));
        }
        if (channel == Channel.STABLE) {
            Optional<Pick> tagged = candidates.stream()
                .filter(p -> p.release().tag().startsWith("v") && !p.release().prerelease())
                .max(Comparator.comparing(p -> p.release().publishedAt()));
            if (tagged.isPresent()) {
                return tagged;
            }
            return candidates.stream().filter(p -> MAIN_BUILD.equals(p.release().tag())).findFirst();
        }
        return candidates.stream().max(Comparator.comparing(p -> p.asset().updatedAt()));
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
                    instant(a, "updated_at"), digest != null && digest.startsWith("sha256:") ? digest.substring(7) : null));
            }
            out.add(new Release(string(r, "tag_name"), bool(r, "prerelease"), bool(r, "draft"), instant(r, "published_at"), assets));
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
