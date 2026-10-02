package com.skyblockminer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Tracks the movement keys a routine holds so they can always be released together. */
final class Keys {
    private final List<KeyMapping> held = new ArrayList<>(4);

    /** Holds exactly {@code keys}, releasing anything else this tracker pressed. */
    void hold(List<KeyMapping> keys) {
        for (int i = this.held.size() - 1; i >= 0; i--) {
            KeyMapping key = this.held.get(i);
            if (!keys.contains(key)) {
                key.setDown(false);
                this.held.remove(i);
            }
        }
        for (KeyMapping key : keys) {
            key.setDown(true);
            if (!this.held.contains(key)) {
                this.held.add(key);
            }
        }
    }

    void release() {
        for (KeyMapping key : this.held) {
            key.setDown(false);
        }
        this.held.clear();
    }

    /** Parses movement letters such as "A+W" into key mappings; unknown letters are ignored. */
    static List<KeyMapping> parse(Minecraft mc, String spec) {
        Options options = mc.options;
        List<KeyMapping> keys = new ArrayList<>(2);
        for (char c : spec.toUpperCase().toCharArray()) {
            KeyMapping key = switch (c) {
                case 'W' -> options.keyUp;
                case 'A' -> options.keyLeft;
                case 'S' -> options.keyDown;
                case 'D' -> options.keyRight;
                default -> null;
            };
            if (key != null && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** True when the spec contains at least one movement letter. */
    static boolean valid(String spec) {
        return spec != null && spec.toUpperCase().matches(".*[WASD].*") && spec.toUpperCase().matches("[WASD+ ]+");
    }
}
