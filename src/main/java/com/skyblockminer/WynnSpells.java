package com.skyblockminer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wynncraft spell combos. Every class casts spells with three clicks: 1st R-L-R, 2nd R-R-R, 3rd R-L-L, 4th R-R-L.
 * Archers use bows, whose attack is a right click, so their combos are mirrored (L-R-L and so on).
 */
final class WynnSpells {
    enum Click {
        LEFT,
        RIGHT;

        Click flip() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    enum WynnClass {
        WARRIOR("warrior", "Warrior", false),
        ASSASSIN("assassin", "Assassin", false),
        MAGE("mage", "Mage", true),
        SHAMAN("shaman", "Shaman", true),
        ARCHER("archer", "Archer", true);

        final String id;
        final String label;
        /** Mages, shamans and archers hit from range; warriors and assassins need to be next to the mob. */
        final boolean ranged;

        WynnClass(String id, String label, boolean ranged) {
            this.id = id;
            this.label = label;
            this.ranged = ranged;
        }

        /** The click that does a basic attack. */
        Click attack() {
            return this == ARCHER ? Click.RIGHT : Click.LEFT;
        }

        static WynnClass parse(String id) {
            if (id != null) {
                for (WynnClass type : values()) {
                    if (type.id.equalsIgnoreCase(id.trim())) {
                        return type;
                    }
                }
            }
            return null;
        }
    }

    static final List<String> CLASS_IDS = List.of("warrior", "assassin", "mage", "shaman", "archer");

    private static final Click L = Click.LEFT;
    private static final Click R = Click.RIGHT;
    private static final List<List<Click>> COMBOS = List.of(List.of(R, L, R), List.of(R, R, R), List.of(R, L, L), List.of(R, R, L));

    private WynnSpells() {
    }

    /** The clicks for spell 1..4, or an empty list for anything else. */
    static List<Click> combo(int spell, WynnClass type) {
        if (spell < 1 || spell > COMBOS.size()) {
            return List.of();
        }
        List<Click> combo = COMBOS.get(spell - 1);
        return type == WynnClass.ARCHER ? combo.stream().map(Click::flip).toList() : combo;
    }

    /** Parses a spell rotation like "1, 3" or "3 1 1". Unknown entries are skipped. */
    static List<Integer> rotation(String text) {
        List<Integer> spells = new ArrayList<>();
        if (text == null) {
            return spells;
        }
        for (String part : text.toLowerCase(Locale.ROOT).split("[^0-9]+")) {
            if (part.length() == 1 && part.charAt(0) >= '1' && part.charAt(0) <= '4') {
                spells.add(part.charAt(0) - '0');
            }
        }
        return spells;
    }
}
