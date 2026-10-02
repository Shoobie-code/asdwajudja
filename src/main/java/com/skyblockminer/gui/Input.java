package com.skyblockminer.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Polls mouse buttons and keys straight from GLFW once per frame. Screens use this instead of overriding
 * vanilla input callbacks, whose signatures change between Minecraft versions.
 */
public final class Input {
    private static final long REPEAT_DELAY = 450L;
    private static final long REPEAT_RATE = 35L;
    private static final int FIRST_KEY = GLFW.GLFW_KEY_SPACE;
    private static final int LAST_KEY = GLFW.GLFW_KEY_LAST;
    private static final String DIGITS_SHIFTED = ")!@#$%^&*(";

    private final boolean[] mouse = new boolean[3];
    private final boolean[] clicked = new boolean[3];
    private final boolean[] released = new boolean[3];
    private final boolean[] keys = new boolean[LAST_KEY + 1];
    private final long[] downAt = new long[LAST_KEY + 1];
    private final long[] repeatAt = new long[LAST_KEY + 1];
    private final int[] typed = new int[32];
    private int typedCount;
    private boolean primed;

    /** Reads the current state; call once at the start of every frame. */
    public void poll() {
        long window = Minecraft.getInstance().getWindow().handle();
        long now = System.currentTimeMillis();
        for (int button = 0; button < 3; button++) {
            boolean down = GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
            this.clicked[button] = this.primed && down && !this.mouse[button];
            this.released[button] = this.primed && !down && this.mouse[button];
            this.mouse[button] = down;
        }
        this.typedCount = 0;
        for (int key = FIRST_KEY; key <= LAST_KEY; key++) {
            boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            if (down && !this.keys[key]) {
                this.downAt[key] = now;
                this.repeatAt[key] = now + REPEAT_DELAY;
                if (this.primed) {
                    this.emit(key);
                }
            } else if (down && now >= this.repeatAt[key]) {
                this.repeatAt[key] = now + REPEAT_RATE;
                this.emit(key);
            }
            this.keys[key] = down;
        }
        this.primed = true;
    }

    private void emit(int key) {
        if (this.typedCount < this.typed.length) {
            this.typed[this.typedCount++] = key;
        }
    }

    public boolean clicked(int button) {
        return this.clicked[button];
    }

    public boolean released(int button) {
        return this.released[button];
    }

    public boolean down(int button) {
        return this.mouse[button];
    }

    /** Keys pressed (or auto-repeated) this frame, in order. */
    public int typedCount() {
        return this.typedCount;
    }

    public int typed(int index) {
        return this.typed[index];
    }

    public boolean shift() {
        return this.keys[GLFW.GLFW_KEY_LEFT_SHIFT] || this.keys[GLFW.GLFW_KEY_RIGHT_SHIFT];
    }

    /** Control on Windows/Linux, Command on macOS. */
    public boolean shortcut() {
        return this.keys[GLFW.GLFW_KEY_LEFT_CONTROL] || this.keys[GLFW.GLFW_KEY_RIGHT_CONTROL]
            || this.keys[GLFW.GLFW_KEY_LEFT_SUPER] || this.keys[GLFW.GLFW_KEY_RIGHT_SUPER];
    }

    /** Character for a key on a US layout, or 0. Ctrl+V pasting covers other layouts. */
    public char character(int key) {
        boolean shift = this.shift();
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char c = (char) ('a' + key - GLFW.GLFW_KEY_A);
            return shift ? Character.toUpperCase(c) : c;
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return shift ? DIGITS_SHIFTED.charAt(key - GLFW.GLFW_KEY_0) : (char) ('0' + key - GLFW.GLFW_KEY_0);
        }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return (char) ('0' + key - GLFW.GLFW_KEY_KP_0);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> ' ';
            case GLFW.GLFW_KEY_APOSTROPHE -> shift ? '"' : '\'';
            case GLFW.GLFW_KEY_COMMA -> shift ? '<' : ',';
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> shift ? '_' : '-';
            case GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_KP_DECIMAL -> shift ? '>' : '.';
            case GLFW.GLFW_KEY_SLASH, GLFW.GLFW_KEY_KP_DIVIDE -> shift ? '?' : '/';
            case GLFW.GLFW_KEY_SEMICOLON -> shift ? ':' : ';';
            case GLFW.GLFW_KEY_EQUAL -> shift ? '+' : '=';
            case GLFW.GLFW_KEY_KP_ADD -> '+';
            case GLFW.GLFW_KEY_LEFT_BRACKET -> shift ? '{' : '[';
            case GLFW.GLFW_KEY_BACKSLASH -> shift ? '|' : '\\';
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> shift ? '}' : ']';
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> shift ? '~' : '`';
            default -> 0;
        };
    }
}
