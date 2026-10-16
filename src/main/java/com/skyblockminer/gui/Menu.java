package com.skyblockminer.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * What the menu shows, in the shape of the Skymacro overlay design: sidebar tabs holding cards, where each card
 * is a macro or module with an ON/OFF button, a keybind chip and a gear that opens its settings in place.
 */
public final class Menu {
    private Menu() {
    }

    /**
     * One card. {@code on} and {@code toggle} are null for cards without an ON/OFF button, {@code chip} may be
     * null or return null for no chip, and {@code select} (may be null) runs when the card's name is clicked.
     */
    public record Card(String name, Supplier<String> chip, BooleanSupplier on, Runnable toggle, Runnable select,
        List<Setting.Section> sections) {

        public boolean hasToggle() {
            return this.on != null && this.toggle != null;
        }

        public boolean matches(String query) {
            if (this.name.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
            for (Setting.Section section : this.sections) {
                for (Setting setting : section.settings()) {
                    if (setting.matches(query)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** A sidebar tab. {@code icon} is one glyph from Minecraft's font. */
    public record Tab(String name, String icon, String description, List<Card> cards) {
    }

    /** Reads and rebinds the mod's keys, so the menu can show and change them without touching key mappings. */
    public interface KeyBinds {
        /** Name of the key that opens this menu. */
        String menuKey();

        /** Name of the key that starts and stops the selected macro, or null when unbound. */
        String toggleKey();

        /** Binds the menu key to a GLFW key code. */
        void rebindMenu(int glfwKey);
    }

    /** Everything the menu needs: the macro tabs, the extra cards on the Settings page, and key access. */
    public record Layout(List<Tab> tabs, List<Card> settingsCards, KeyBinds keys) {
    }
}
