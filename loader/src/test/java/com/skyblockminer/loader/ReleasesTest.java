package com.skyblockminer.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skyblockminer.loader.Releases.Channel;
import com.skyblockminer.loader.Releases.Release;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleasesTest {
    private static String release(String tag, boolean prerelease, String published, String assetJson) {
        return "{\"tag_name\":\"" + tag + "\",\"prerelease\":" + prerelease + ",\"draft\":false,\"published_at\":\"" + published
            + "\",\"assets\":[" + assetJson + "]}";
    }

    private static String asset(long id, String name, String updated) {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"browser_download_url\":\"https://example.com/" + name
            + "\",\"updated_at\":\"" + updated + "\",\"digest\":\"sha256:abc\"}";
    }

    private static final String MAIN = release("build-latest", false, "2026-10-02T20:29:04Z",
        asset(30, "skyblock-macro-2.1.0.jar", "2026-10-20T10:00:00Z") + "," + asset(31, "skymacro-loader-1.0.0.jar", "2026-10-20T10:00:00Z"));
    private static final String PREVIEW = release("build-preview", true, "2026-10-21T00:00:00Z",
        asset(40, "skyblock-macro-2.1.0.jar", "2026-10-22T10:00:00Z"));
    private static final String V210 = release("v2.1.0", false, "2026-10-10T00:00:00Z",
        asset(10, "skyblock-macro-2.1.0.jar", "2026-10-10T00:00:00Z"));
    private static final String V200 = release("v2.0.0", false, "2026-09-01T00:00:00Z",
        asset(5, "skyblock-macro-2.0.0.jar", "2026-09-01T00:00:00Z"));
    private static final String V220_BETA = release("v2.2.0-beta.1", true, "2026-10-21T00:00:00Z",
        asset(20, "skyblock-macro-2.2.0.jar", "2026-10-21T00:00:00Z"));

    private static List<Release> parse(String... releases) {
        return Releases.parse("[" + String.join(",", releases) + "]");
    }

    private static long pick(Channel channel, String... releases) {
        return Releases.pick(parse(releases), channel).orElseThrow().asset().id();
    }

    @Test
    void parsesAssetsAndDigest() {
        Release r = parse(MAIN).get(0);
        assertEquals("build-latest", r.tag());
        assertEquals(2, r.assets().size());
        assertEquals("abc", r.assets().get(0).sha256());
        assertEquals(30L, r.macroJar().orElseThrow().id());
    }

    @Test
    void rollingBuildUsesNewestJarNotStaleOnes() {
        String main = release("build-latest", false, "2026-10-02T20:29:04Z",
            asset(1, "skyblock-macro-2.0.0.jar", "2026-10-02T21:31:38Z") + "," + asset(2, "skyblock-macro-2.1.0.jar", "2026-10-23T00:50:47Z")
                + "," + asset(3, "skymacro-loader-1.0.0.jar", "2026-10-23T00:50:47Z"));
        assertEquals(2L, pick(Channel.STABLE, main));
        assertEquals(2L, pick(Channel.BETA, main));
    }

    @Test
    void stableFallsBackToMainBuildWithoutTags() {
        assertEquals(30L, pick(Channel.STABLE, MAIN, PREVIEW));
        assertEquals(30L, pick(Channel.BETA, MAIN, PREVIEW));
    }

    @Test
    void stablePrefersNewestTaggedRelease() {
        assertEquals(10L, pick(Channel.STABLE, MAIN, V200, V210, V220_BETA));
    }

    @Test
    void betaTakesNewestUploadButNeverBranchBuilds() {
        assertEquals(20L, pick(Channel.BETA, MAIN, PREVIEW, V210, V220_BETA));
        assertEquals(30L, pick(Channel.BETA, MAIN, PREVIEW, V210));
    }

    @Test
    void nothingToPick() {
        assertTrue(Releases.pick(parse(PREVIEW), Channel.STABLE).isEmpty());
        assertTrue(Releases.pick(parse(), Channel.BETA).isEmpty());
    }

    @Test
    void recognisesMacroJars() {
        assertTrue(Releases.isMacroJar("skyblock-macro-2.1.0.jar"));
        assertFalse(Releases.isMacroJar("skymacro-loader-1.0.0.jar"));
        assertFalse(Releases.isMacroJar("skyblock-macro-2.1.0-sources.jar"));
        assertFalse(Releases.isMacroJar("skyblock-macro-loader-1.0.0.jar"));
        assertNull(parse(release("v1", false, "2026-01-01T00:00:00Z", "{\"id\":1,\"name\":\"a.jar\"}")).get(0).assets().get(0).sha256());
    }
}
