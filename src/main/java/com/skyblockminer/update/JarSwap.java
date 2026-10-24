package com.skyblockminer.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Replaces the running mod jar with a downloaded one when Minecraft closes. Windows keeps the jar locked until the
 * process is gone, so there a small script waits for this process to exit and then moves the file (the same trick
 * V5Loader uses). Other systems can rename over an open file, so the shutdown hook moves it directly.
 */
public final class JarSwap {
    private static final int WAIT_SECONDS = 90;
    private static final int RETRIES = 20;

    private static volatile Path staged;
    private static volatile Path target;
    private static boolean hooked;

    private JarSwap() {
    }

    /** Installs {@code downloaded} over {@code activeJar} at exit. A later call replaces the earlier one. */
    public static synchronized void stage(Path downloaded, Path activeJar) {
        staged = downloaded;
        target = activeJar;
        if (!hooked) {
            hooked = true;
            Runtime.getRuntime().addShutdownHook(new Thread(JarSwap::install, "Skyblock Macro update"));
        }
    }

    public static boolean pending() {
        return staged != null;
    }

    private static void install() {
        Path from = staged;
        Path to = target;
        if (from == null || to == null || !Files.isRegularFile(from)) {
            return;
        }
        try {
            if (windows()) {
                startWindowsHelper(from, to);
            } else {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[Skyblock Macro] Could not install the update: " + e);
        }
    }

    private static void startWindowsHelper(Path from, Path to) throws IOException {
        long pid = ProcessHandle.current().pid();
        Path backup = from.resolveSibling(to.getFileName() + ".bak");
        Path script = from.resolveSibling("skyblock-macro-update-" + pid + ".cmd");
        List<String> lines = List.of(
            "@echo off",
            "setlocal enabledelayedexpansion",
            "set \"ATTEMPTS=0\"",
            ":wait_for_exit",
            "tasklist /FI \"PID eq " + pid + "\" | find \"" + pid + "\" >nul",
            "if not errorlevel 1 (",
            "  timeout /t 1 /nobreak >nul",
            "  set /a ATTEMPTS+=1",
            "  if !ATTEMPTS! geq " + WAIT_SECONDS + " exit /b 1",
            "  goto wait_for_exit",
            ")",
            "set \"ATTEMPTS=0\"",
            ":replace_loop",
            "del /f /q " + quote(backup) + " >nul 2>nul",
            "if exist " + quote(to) + " move /y " + quote(to) + " " + quote(backup) + " >nul 2>nul",
            "move /y " + quote(from) + " " + quote(to) + " >nul 2>nul",
            "if not errorlevel 1 if exist " + quote(to) + " if not exist " + quote(from) + " goto cleanup",
            "if exist " + quote(backup) + " move /y " + quote(backup) + " " + quote(to) + " >nul 2>nul",
            "set /a ATTEMPTS+=1",
            "if !ATTEMPTS! geq " + RETRIES + " exit /b 1",
            "timeout /t 1 /nobreak >nul",
            "goto replace_loop",
            ":cleanup",
            "del /f /q " + quote(backup) + " >nul 2>nul",
            "del /f /q \"%~f0\" >nul 2>nul",
            "exit /b 0");
        Files.writeString(script, String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        new ProcessBuilder("cmd.exe", "/c", script.toString()).directory(from.getParent().toFile()).start();
    }

    private static String quote(Path p) {
        return "\"" + p.toAbsolutePath().toString().replace("\"", "\"\"") + "\"";
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
