package com.skyblockminer.update;

import com.skyblockminer.MinerMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyblockminer.update.Releases.Channel;
import com.skyblockminer.update.Releases.Pick;
import com.skyblockminer.update.Releases.Release;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Keeps the mod jar itself up to date, the way V5Loader does: at launch it hashes its own jar and compares it with
 * the jar on the newest GitHub release for the chosen channel. When they differ it downloads the new jar, checks its
 * size and SHA-256 against what GitHub reports, and swaps it in when Minecraft closes ({@link JarSwap}). The check
 * runs on a background thread so it never slows down startup.
 */
public final class SelfUpdate implements PreLaunchEntrypoint {
    public static final String REPO = "Shoobie-code/asdwajudja";
    private static final String MOD_ID = "skyblockminer";
    private static final String USER_AGENT = "SkyblockMacro-Updater";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10L))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public enum State { IDLE, CHECKING, UP_TO_DATE, STAGED, FAILED, DISABLED }

    public static volatile State state = State.IDLE;
    public static volatile String message = "";
    /** Release the running jar came from, e.g. "2.1.0-r7", or empty for a local build. */
    public static volatile String running = "";
    public static volatile Channel channel = Channel.STABLE;

    private static Path activeJar;
    private static String activeHash = "";
    private static Path settingsFile;
    /** Downloads wait here, outside the mods folder so Fabric never sees them. */
    private static Path stagingDir;
    private static boolean offline;

    @Override
    public void onPreLaunch() {
        FabricLoader fabric = FabricLoader.getInstance();
        settingsFile = fabric.getConfigDir().resolve("skyblockminer-updater.properties");
        stagingDir = fabric.getGameDir().resolve(".skyblock-macro-update");
        loadSettings();
        if (fabric.isDevelopmentEnvironment()) {
            set(State.DISABLED, "Dev environment, updates off");
            return;
        }
        activeJar = fabric.getModContainer(MOD_ID).flatMap(SelfUpdate::jarOf).orElse(null);
        if (activeJar == null) {
            set(State.DISABLED, "Not running from a jar, updates off");
            return;
        }
        cleanStaged();
        if (offline) {
            set(State.DISABLED, "Updates off (offline=true)");
            return;
        }
        check(channel);
    }

    /** Checks the channel in the background and stages its jar if it differs from the running one. */
    public static synchronized void check(Channel wanted) {
        if (state == State.CHECKING || activeJar == null) {
            return;
        }
        if (wanted != channel) {
            channel = wanted;
            saveSettings();
        }
        set(State.CHECKING, "Checking GitHub...");
        Thread thread = new Thread(() -> {
            try {
                run(wanted);
            } catch (Exception e) {
                MinerMod.LOGGER.warn("Update check failed", e);
                set(State.FAILED, "Update check failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }, "Skyblock Macro updater");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(Channel wanted) throws IOException, InterruptedException {
        if (activeHash.isEmpty()) {
            activeHash = sha256(activeJar);
        }
        String minecraft = FabricLoader.getInstance().getModContainer("minecraft")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        List<Release> releases = Releases.parse(get("https://api.github.com/repos/" + REPO + "/releases?per_page=50"));
        running = Releases.withHash(releases, activeHash).map(Release::tag).orElse("");
        Pick pick = Releases.pick(releases, wanted, minecraft)
            .orElseThrow(() -> new IOException("no " + wanted.label.toLowerCase() + " release for Minecraft " + minecraft));
        String tag = pick.release().tag();
        if (pick.asset().sha256() == null) {
            throw new IOException("GitHub gave no checksum for " + tag);
        }
        if (pick.asset().sha256().equalsIgnoreCase(activeHash)) {
            set(State.UP_TO_DATE, "Up to date");
            return;
        }
        Path file = stagingDir.resolve("skyblock-macro-" + pick.asset().id() + ".jar");
        if (!Files.isRegularFile(file) || !pick.asset().sha256().equalsIgnoreCase(sha256(file))) {
            download(pick, file);
        }
        JarSwap.stage(file, activeJar);
        set(State.STAGED, tag + " downloaded, installs when you close the game");
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20L))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub answered " + response.statusCode());
        }
        return response.body();
    }

    private static void download(Pick pick, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(pick.asset().url()))
            .timeout(Duration.ofSeconds(120L))
            .header("Accept", "application/octet-stream")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        try {
            HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(part));
            if (response.statusCode() != 200) {
                throw new IOException("download answered " + response.statusCode());
            }
            if (pick.asset().size() > 0 && Files.size(part) != pick.asset().size()) {
                throw new IOException("download was cut short");
            }
            if (!pick.asset().sha256().equalsIgnoreCase(sha256(part))) {
                throw new IOException("downloaded jar failed its checksum");
            }
            if (!MOD_ID.equals(modId(part))) {
                throw new IOException(pick.asset().name() + " is not the Skyblock Macro jar");
            }
            Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /** Deletes jars a previous session downloaded but never installed. */
    private static void cleanStaged() {
        Path dir = stagingDir;
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path p : files) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Still locked by an install helper; the next launch gets it.
                }
            }
        } catch (IOException ignored) {
            // Nothing to clean.
        }
    }

    private static Optional<Path> jarOf(ModContainer container) {
        for (Path root : container.getRootPaths()) {
            String uri = root.toUri().toString();
            if (uri.startsWith("jar:")) {
                int bang = uri.indexOf('!');
                if (bang > 0) {
                    try {
                        Path jar = Path.of(URI.create(uri.substring(4, bang)));
                        if (Files.isRegularFile(jar) && jar.toString().toLowerCase().endsWith(".jar")) {
                            return Optional.of(jar);
                        }
                    } catch (RuntimeException ignored) {
                        // Not a file system path.
                    }
                }
            }
        }
        return Optional.empty();
    }

    static String modId(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return "";
            }
            try (InputStream in = zip.getInputStream(entry)) {
                JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                return json.has("id") ? json.get("id").getAsString() : "";
            }
        } catch (RuntimeException e) {
            throw new IOException("bad fabric.mod.json in " + jar.getFileName(), e);
        }
    }

    static String sha256(Path file) throws IOException {
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

    private static void set(State s, String text) {
        state = s;
        message = text;
    }

    private static void loadSettings() {
        Properties p = new Properties();
        if (Files.isRegularFile(settingsFile)) {
            try (InputStream in = Files.newInputStream(settingsFile)) {
                p.load(in);
            } catch (IOException e) {
                MinerMod.LOGGER.warn("Could not read {}", settingsFile, e);
            }
        }
        channel = Channel.parse(p.getProperty("channel", "stable"));
        offline = Boolean.parseBoolean(p.getProperty("offline", "false"));
    }

    private static void saveSettings() {
        Properties p = new Properties();
        p.setProperty("channel", channel.name().toLowerCase());
        p.setProperty("offline", Boolean.toString(offline));
        try (OutputStream out = Files.newOutputStream(settingsFile)) {
            p.store(out, "Skyblock Macro updates. channel is stable or beta; offline=true turns update checks off.");
        } catch (IOException e) {
            MinerMod.LOGGER.warn("Could not save {}", settingsFile, e);
        }
    }
}
