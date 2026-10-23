package com.skyblockminer.loader;

import com.skyblockminer.loader.Releases.Channel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** What the loader has installed, kept in {@code .minecraft/skymacro/loader.properties}. */
public final class LoaderState {
    public static final String DEFAULT_REPO = "Shoobie-code/asdwajudja";

    private final Path file;
    /** GitHub repo to pull releases from, as owner/name. Editable by hand for forks. */
    public String repo = DEFAULT_REPO;
    public Channel channel = Channel.STABLE;
    /** Channel of the jar on disk; differs from {@link #channel} until the switch is downloaded. */
    public Channel installedChannel = Channel.STABLE;
    public long assetId;
    public String jar = "";
    public String label = "";
    /** Skip the GitHub check and use the jar on disk. */
    public boolean offline;

    private LoaderState(Path file) {
        this.file = file;
    }

    public static LoaderState load(Path dir) {
        LoaderState s = new LoaderState(dir.resolve("loader.properties"));
        if (!Files.isRegularFile(s.file)) {
            return s;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(s.file)) {
            p.load(in);
        } catch (IOException e) {
            LoaderPreLaunch.LOGGER.warn("Could not read {}", s.file, e);
            return s;
        }
        s.repo = p.getProperty("repo", DEFAULT_REPO).trim();
        s.channel = Channel.parse(p.getProperty("channel", "stable"));
        s.installedChannel = Channel.parse(p.getProperty("installedChannel", "stable"));
        try {
            s.assetId = Long.parseLong(p.getProperty("assetId", "0"));
        } catch (NumberFormatException e) {
            s.assetId = 0L;
        }
        s.jar = p.getProperty("jar", "");
        s.label = p.getProperty("label", "");
        s.offline = Boolean.parseBoolean(p.getProperty("offline", "false"));
        return s;
    }

    public synchronized void save() {
        Properties p = new Properties();
        p.setProperty("repo", this.repo);
        p.setProperty("channel", this.channel.name().toLowerCase());
        p.setProperty("installedChannel", this.installedChannel.name().toLowerCase());
        p.setProperty("assetId", Long.toString(this.assetId));
        p.setProperty("jar", this.jar);
        p.setProperty("label", this.label);
        p.setProperty("offline", Boolean.toString(this.offline));
        try {
            Files.createDirectories(this.file.getParent());
            try (OutputStream out = Files.newOutputStream(this.file)) {
                p.store(out, "Skyblock Macro Loader. channel is stable or beta; set offline=true to stop update checks.");
            }
        } catch (IOException e) {
            LoaderPreLaunch.LOGGER.warn("Could not save {}", this.file, e);
        }
    }

    public Path dir() {
        return this.file.getParent();
    }

    /** The installed macro jar, or null when nothing has been downloaded yet. */
    public Path jarPath() {
        if (this.jar.isEmpty()) {
            return null;
        }
        Path p = this.dir().resolve(this.jar);
        return Files.isRegularFile(p) ? p : null;
    }
}
