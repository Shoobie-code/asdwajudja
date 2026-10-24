package com.skyblockminer.script;

import com.skyblockminer.MinerMod;
import com.skyblockminer.game.Server;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The userscripts folder ({@code config/skyblockminer/scripts}). Every {@code .js} file there is a script; its
 * leading comment can name it and say where it runs:
 * <pre>
 * // @name Pumpkin farm
 * // @description Walks the lanes and rewarps.
 * // @server hypixel
 * </pre>
 */
public final class Scripts {
    static final String EXAMPLE_FILE = "example.js";
    static final String EXAMPLE = """
        // @name Example
        // @description Says hi, looks around, and stops. Copy this file to start your own script.
        // @server any

        // The full API is in API.md next to this file.
        log('Hello from', player.name, 'at', Math.round(player.x), Math.round(player.y), Math.round(player.z));
        status('Looking around');

        var yaw = player.yaw;
        for (var i = 1; i <= 4; i++) {
            look(yaw + i * 90, 0);
            waitLook();
            sleep(300, 600);
        }

        // Stop early if someone mentions you.
        on('chat', function (line) {
            if (line.toLowerCase().indexOf(player.name.toLowerCase()) >= 0) stop('Someone mentioned you');
        });
        status('Waiting a few seconds');
        sleep(3000);
        stop('Example done');
        """;

    /** What the menu and commands show for one script file. */
    public record Info(String file, String name, String description, Server.Kind server) {
    }

    private final Path dir;
    private List<Info> scripts = List.of();

    Scripts(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return this.dir;
    }

    public List<Info> list() {
        return this.scripts;
    }

    public Info find(String file) {
        for (Info info : this.scripts) {
            if (info.file.equalsIgnoreCase(file) || info.file.equalsIgnoreCase(file + ".js")) {
                return info;
            }
        }
        return null;
    }

    /** Rescans the folder, creating it with an example and the API reference the first time. */
    public List<Info> reload() {
        try {
            if (!Files.isDirectory(this.dir)) {
                Files.createDirectories(this.dir);
                Files.writeString(this.dir.resolve(EXAMPLE_FILE), EXAMPLE, StandardCharsets.UTF_8);
            }
            writeApiDoc();
            List<Info> found = new ArrayList<>();
            try (Stream<Path> files = Files.list(this.dir)) {
                for (Path file : files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".js")).sorted().toList()) {
                    found.add(parse(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8)));
                }
            }
            this.scripts = List.copyOf(found);
        } catch (IOException e) {
            MinerMod.LOGGER.warn("Could not read the userscripts folder {}", this.dir, e);
            this.scripts = List.of();
        }
        return this.scripts;
    }

    String read(Info info) throws IOException {
        return Files.readString(this.dir.resolve(info.file), StandardCharsets.UTF_8);
    }

    /** Keeps API.md in the folder matching this version of the mod. */
    private void writeApiDoc() throws IOException {
        try (var in = Scripts.class.getResourceAsStream("/skyblockminer/scripts/API.md")) {
            if (in != null) {
                byte[] doc = in.readAllBytes();
                Path target = this.dir.resolve("API.md");
                if (!Files.exists(target) || !java.util.Arrays.equals(Files.readAllBytes(target), doc)) {
                    Files.write(target, doc);
                }
            }
        }
    }

    /** Reads the {@code // @key value} lines of a script's leading comment block. */
    static Info parse(String file, String source) {
        String base = file.toLowerCase(Locale.ROOT).endsWith(".js") ? file.substring(0, file.length() - 3) : file;
        String name = base;
        String description = "";
        Server.Kind server = null;
        boolean started = false;
        for (String raw : source.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() && !started) {
                continue;
            }
            if (!line.startsWith("//")) {
                // The header is the first run of comment lines; a blank line or code ends it.
                break;
            }
            started = true;
            String body = line.substring(2).strip();
            if (!body.startsWith("@")) {
                continue;
            }
            int space = body.indexOf(' ');
            String key = (space < 0 ? body.substring(1) : body.substring(1, space)).toLowerCase(Locale.ROOT);
            String value = space < 0 ? "" : body.substring(space + 1).strip();
            switch (key) {
                case "name" -> name = value.isEmpty() ? base : value;
                case "description" -> description = value;
                case "server" -> server = switch (value.toLowerCase(Locale.ROOT)) {
                    case "hypixel", "skyblock" -> Server.Kind.HYPIXEL;
                    case "wynncraft", "wynn" -> Server.Kind.WYNNCRAFT;
                    default -> null;
                };
                default -> {
                }
            }
        }
        return new Info(file, name, description, server);
    }
}
