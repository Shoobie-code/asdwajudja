package com.skyblockminer.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.update.Releases.Channel;
import com.skyblockminer.update.Releases.Release;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleasesTest {
    private static final String MC = "26.2";

    private static String release(String tag, boolean prerelease, String assetJson) {
        return "{\"tag_name\":\"" + tag + "\",\"prerelease\":" + prerelease + ",\"draft\":false,\"assets\":[" + assetJson + "]}";
    }

    private static String asset(long id, String name, String updated) {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"size\":10,\"browser_download_url\":\"https://example.com/" + name
            + "\",\"updated_at\":\"" + updated + "\",\"digest\":\"sha256:hash" + id + "\"}";
    }

    private static final String R1 = release("2.1.0-r1", false, asset(10, "skyblock-macro-26.2.jar", "2026-10-24T10:00:00Z"));
    private static final String R2 = release("2.1.0-r2", false,
        asset(20, "skyblock-macro-26.2.jar", "2026-10-25T10:00:00Z") + "," + asset(21, "skyblock-macro-26.3.jar", "2026-10-25T10:00:00Z"));
    private static final String BETA = release("2.1.0-beta-r1", true, asset(30, "skyblock-macro-26.2.jar", "2026-10-26T10:00:00Z"));
    private static final String PREVIEW = release("build-preview", true, asset(40, "skyblock-macro-2.1.0.jar", "2026-10-27T10:00:00Z"));
    private static final String OLD_MAIN = release("build-latest", false,
        asset(50, "skyblock-macro-2.0.0.jar", "2026-10-02T21:31:38Z") + "," + asset(51, "skyblock-macro-2.1.0.jar", "2026-10-28T00:50:47Z"));

    private static List<Release> parse(String... releases) {
        return Releases.parse("[" + String.join(",", releases) + "]");
    }

    private static long pick(Channel channel, String... releases) {
        return Releases.pick(parse(releases), channel, MC).orElseThrow().asset().id();
    }

    @Test
    void parsesAssets() {
        Release r = parse(R2).get(0);
        assertEquals("2.1.0-r2", r.tag());
        assertEquals("hash20", r.assets().get(0).sha256());
        assertEquals(10L, r.assets().get(0).size());
    }

    @Test
    void stableTakesNewestFullRelease() {
        assertEquals(20L, pick(Channel.STABLE, R1, R2, BETA, PREVIEW, OLD_MAIN));
    }

    @Test
    void betaAlsoTakesPrereleases() {
        assertEquals(30L, pick(Channel.BETA, R1, R2, BETA, PREVIEW, OLD_MAIN));
        assertEquals(20L, pick(Channel.BETA, R1, R2));
    }

    @Test
    void picksTheJarForThisMinecraftVersion() {
        assertEquals(21L, Releases.pick(parse(R2), Channel.STABLE, "26.3").orElseThrow().asset().id());
    }

    @Test
    void olderReleasesFallBackToNewestMacroJar() {
        String legacy = release("v2.1.0", false,
            asset(60, "skyblock-macro-2.0.0.jar", "2026-10-01T00:00:00Z") + "," + asset(61, "skyblock-macro-2.1.0.jar", "2026-10-02T00:00:00Z"));
        assertEquals(61L, pick(Channel.STABLE, legacy));
    }

    @Test
    void rollingBuildsAreNeverPicked() {
        assertTrue(Releases.pick(parse(PREVIEW, OLD_MAIN), Channel.BETA, MC).isEmpty());
    }

    @Test
    void findsTheRunningRelease() {
        assertEquals("2.1.0-r1", Releases.withHash(parse(R1, R2), "HASH10").orElseThrow().tag());
        assertTrue(Releases.withHash(parse(R1, R2), "nope").isEmpty());
    }

    @Test
    void recognisesMacroJars() {
        assertTrue(Releases.isMacroJar("skyblock-macro-26.2.jar"));
        assertFalse(Releases.isMacroJar("skymacro-loader-1.0.0.jar"));
        assertFalse(Releases.isMacroJar("skyblock-macro-2.1.0-sources.jar"));
        assertNull(parse(release("x", false, "{\"id\":1,\"name\":\"a.jar\"}")).get(0).assets().get(0).sha256());
    }
}
