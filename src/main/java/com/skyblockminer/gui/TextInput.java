package com.skyblockminer.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Single-line edit buffer with a caret, fed from {@link Input}. */
public final class TextInput {
    private final StringBuilder text = new StringBuilder();
    private int caret;
    private int maxLength = 200;

    public void set(String value, int maxLength) {
        this.text.setLength(0);
        this.text.append(value);
        this.caret = this.text.length();
        this.maxLength = maxLength;
    }

    public String text() {
        return this.text.toString();
    }

    public int caret() {
        return this.caret;
    }

    public boolean isEmpty() {
        return this.text.isEmpty();
    }

    /** Applies this frame's key presses. Returns {@link Result#SUBMIT} on Enter, {@link Result#CANCEL} on Escape. */
    public Result handle(Input input) {
        Result result = Result.NONE;
        for (int i = 0; i < input.typedCount(); i++) {
            int key = input.typed(i);
            if (input.shortcut()) {
                switch (key) {
                    case GLFW.GLFW_KEY_V -> this.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                    case GLFW.GLFW_KEY_C -> Minecraft.getInstance().keyboardHandler.setClipboard(this.text.toString());
                    case GLFW.GLFW_KEY_A -> this.caret = this.text.length();
                    case GLFW.GLFW_KEY_BACKSPACE -> {
                        this.text.delete(0, this.caret);
                        this.caret = 0;
                    }
                    default -> {
                    }
                }
                continue;
            }
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> result = Result.SUBMIT;
                case GLFW.GLFW_KEY_ESCAPE -> result = Result.CANCEL;
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (this.caret > 0) {
                        this.text.deleteCharAt(--this.caret);
                    }
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (this.caret < this.text.length()) {
                        this.text.deleteCharAt(this.caret);
                    }
                }
                case GLFW.GLFW_KEY_LEFT -> this.caret = Math.max(0, this.caret - 1);
                case GLFW.GLFW_KEY_RIGHT -> this.caret = Math.min(this.text.length(), this.caret + 1);
                case GLFW.GLFW_KEY_HOME -> this.caret = 0;
                case GLFW.GLFW_KEY_END -> this.caret = this.text.length();
                default -> {
                    char c = input.character(key);
                    if (c != 0) {
                        this.insert(String.valueOf(c));
                    }
                }
            }
        }
        return result;
    }

    private void insert(String value) {
        if (value == null) {
            return;
        }
        String clean = value.replaceAll("[\\r\\n\\t]", "").replace("§", "");
        int room = this.maxLength - this.text.length();
        if (room <= 0) {
            return;
        }
        if (clean.length() > room) {
            clean = clean.substring(0, room);
        }
        this.text.insert(this.caret, clean);
        this.caret += clean.length();
    }

    public enum Result {
        NONE,
        SUBMIT,
        CANCEL
    }
}
