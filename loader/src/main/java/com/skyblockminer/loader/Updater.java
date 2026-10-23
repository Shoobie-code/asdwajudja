package com.skyblockminer.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyblockminer.loader.Releases.Channel;
import com.skyblockminer.loader.Releases.Pick;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Talks to the GitHub releases API and downloads the macro jar into the loader's folder. */
public final class Updater {
    private static final String USER_AGENT = "skymacro-loader";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4L))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private Updater() {
    }

    /** The release the channel currently points at. */
    public static Pick latest(String repo, Channel channel) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases?per_page=30"))
            .timeout(Duration.ofSeconds(8L))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub answered " + response.statusCode());
        }
        Optional<Pick> pick = Releases.pick(Releases.parse(response.body()), channel);
        return pick.orElseThrow(() -> new IOException("no " + channel.label.toLowerCase() + " release has a macro jar yet"));
    }

    /**
     * Makes sure the jar for {@code channel} is on disk and recorded in {@code state}.
     *
     * @return true when a new jar was downloaded
     */
    public static boolean update(LoaderState state, Channel channel) throws IOException, InterruptedException {
        Pick pick = latest(state.repo, channel);
        if (pick.asset().id() == state.assetId && state.jarPath() != null) {
            if (state.installedChannel != channel) {
                state.installedChannel = channel;
                state.label = pick.label();
                state.save();
            }
            return false;
        }
        String name = "skyblock-macro-" + pick.asset().id() + ".jar";
        Path target = state.dir().resolve(name);
        if (!Files.isRegularFile(target)) {
            download(pick, target);
        }
        state.assetId = pick.asset().id();
        state.jar = name;
        state.label = pick.label();
        state.installedChannel = channel;
        state.save();
        LoaderPreLaunch.LOGGER.info("Downloaded Skyblock Macro {} ({})", pick.label(), pick.asset().name());
        return true;
    }

    private static void download(Pick pick, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(pick.asset().url()))
            .timeout(Duration.ofSeconds(90L))
            .header("Accept", "application/octet-stream")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(part));
        try {
            if (response.statusCode() != 200) {
                throw new IOException("download answered " + response.statusCode());
            }
            String expected = pick.asset().sha256();
            if (expected != null && !expected.equalsIgnoreCase(sha256(part))) {
                throw new IOException("checksum mismatch for " + pick.asset().name());
            }
            Mod mod = Mod.read(part);
            if (!Mod.MACRO_ID.equals(mod.id())) {
                throw new IOException(pick.asset().name() + " is not the Skyblock Macro jar");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /** Deletes downloaded jars other than the current one. Run before the current jar is opened. */
    public static void cleanup(LoaderState state) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(state.dir(), "skyblock-macro-*")) {
            for (Path p : files) {
                if (!p.getFileName().toString().equals(state.jar)) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        LoaderPreLaunch.LOGGER.debug("Could not delete old jar {}", p, e);
                    }
                }
            }
        } catch (IOException e) {
            LoaderPreLaunch.LOGGER.debug("Could not list {}", state.dir(), e);
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** The bits of a jar's fabric.mod.json the loader needs. */
    public record Mod(String id, JsonObject json) {
        public static final String MACRO_ID = "skyblockminer";

        public static Mod read(Path jar) throws IOException {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry("fabric.mod.json");
                if (entry == null) {
                    throw new IOException(jar.getFileName() + " has no fabric.mod.json");
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    return new Mod(json.has("id") ? json.get("id").getAsString() : "", json);
                }
            } catch (RuntimeException e) {
                throw new IOException("bad fabric.mod.json in " + jar.getFileName(), e);
            }
        }
    }
}
