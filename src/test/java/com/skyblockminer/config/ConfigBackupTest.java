package com.skyblockminer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigBackupTest {
    @TempDir
    Path dir;

    @Test
    void keepsOnlyTheNewestBackups() throws Exception {
        for (int i = 1; i <= 7; i++) {
            MinerConfig.backup("config " + i, dir, 1_000_000_000_000L + i);
        }
        List<Path> kept;
        try (var files = Files.list(dir)) {
            kept = files.sorted().toList();
        }
        assertEquals(MinerConfig.KEEP_BACKUPS, kept.size());
        assertEquals("config 7", Files.readString(kept.get(kept.size() - 1)));
        assertEquals("config 3", Files.readString(kept.get(0)));
    }
}
