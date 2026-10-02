package com.skyblockminer;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

/**
 * "Echo" farming: records the movement keys and view angles you use while walking a farm once, so the farming
 * macro can replay them for designs that none of the lane presets fit.
 */
final class Recording {
    static final int W = 1;
    static final int A = 2;
    static final int S = 4;
    static final int D = 8;
    static final int JUMP = 16;
    static final int SNEAK = 32;
    private static final Gson GSON = new Gson();

    /** One tick: {keys bitmask, yaw, pitch}. */
    private List<float[]> frames = new ArrayList<>();
    private List<float[]> recording;

    List<float[]> frames() {
        return this.frames;
    }

    boolean recording() {
        return this.recording != null;
    }

    void start() {
        this.recording = new ArrayList<>();
        MinerMod.message("Recording movement. Walk your farm once, then run /sm echo stop.", ChatFormatting.GREEN);
    }

    void stop() {
        if (this.recording == null) {
            return;
        }
        List<float[]> done = this.recording;
        this.recording = null;
        // Drop the idle ticks at both ends so playback starts and ends on movement.
        int first = 0;
        while (first < done.size() && ((int) done.get(first)[0] & (W | A | S | D)) == 0) {
            first++;
        }
        int last = done.size();
        while (last > first && ((int) done.get(last - 1)[0] & (W | A | S | D)) == 0) {
            last--;
        }
        this.frames = new ArrayList<>(done.subList(first, last));
        this.save();
        MinerMod.message("Saved a recording of " + this.frames.size() / 20 + " s. Pick the \"Recorded movement\" farm type to replay it.",
            ChatFormatting.GREEN);
    }

    void clear() {
        this.recording = null;
        this.frames = new ArrayList<>();
        this.save();
    }

    /** Called every client tick; captures a frame while recording. */
    void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (this.recording == null || player == null) {
            return;
        }
        Options o = mc.options;
        int mask = (o.keyUp.isDown() ? W : 0) | (o.keyLeft.isDown() ? A : 0) | (o.keyDown.isDown() ? S : 0) | (o.keyRight.isDown() ? D : 0)
            | (o.keyJump.isDown() ? JUMP : 0) | (o.keyShift.isDown() ? SNEAK : 0);
        this.recording.add(new float[]{mask, player.getYRot(), player.getXRot()});
    }

    static List<KeyMapping> keys(Minecraft mc, int mask) {
        Options o = mc.options;
        List<KeyMapping> keys = new ArrayList<>(4);
        if ((mask & W) != 0) {
            keys.add(o.keyUp);
        }
        if ((mask & A) != 0) {
            keys.add(o.keyLeft);
        }
        if ((mask & S) != 0) {
            keys.add(o.keyDown);
        }
        if ((mask & D) != 0) {
            keys.add(o.keyRight);
        }
        if ((mask & JUMP) != 0) {
            keys.add(o.keyJump);
        }
        if ((mask & SNEAK) != 0) {
            keys.add(o.keyShift);
        }
        return keys;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyblockminer-echo.json");
    }

    void load() {
        try {
            if (Files.exists(path())) {
                float[][] data = GSON.fromJson(Files.readString(path()), float[][].class);
                this.frames = new ArrayList<>();
                if (data != null) {
                    for (float[] frame : data) {
                        if (frame != null && frame.length == 3) {
                            this.frames.add(frame);
                        }
                    }
                }
            }
        } catch (RuntimeException | IOException e) {
            MinerMod.LOGGER.warn("Could not read skyblockminer-echo.json", e);
        }
    }

    private void save() {
        try {
            Files.writeString(path(), GSON.toJson(this.frames.toArray(new float[0][])));
        } catch (IOException e) {
            MinerMod.LOGGER.warn("Could not save skyblockminer-echo.json", e);
        }
    }
}
