package com.skyblockminer.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Colors used by the GUI and HUD. The accent comes from the config so users can pick their own. */
public final class Theme {
    public static final Map<String, Integer> ACCENTS = new LinkedHashMap<>();
    // Warm dark palette of the Skymacro overlay design.
    public static final int BACKGROUND = 0xF2121317;
    public static final int PANEL_EDGE = 0xFF302F2A;
    public static final int TITLE_TOP = 0xFF24211B;
    public static final int TITLE_BOTTOM = 0xFF17160F;
    public static final int TITLE_LINE = 0xFF332F23;
    public static final int SIDEBAR = 0xF2141310;
    public static final int SIDEBAR_LINE = 0xFF2E2B24;
    public static final int CONTENT = 0xF219170F;
    public static final int CARD = 0xFF201D17;
    public static final int CARD_HOVER = 0xFF27231B;
    public static final int NAV_ACTIVE = 0xFF2A261E;
    public static final int NAV_ACTIVE_LINE = 0xFF4A4433;
    public static final int FIELD = 0xFF17150F;
    public static final int BORDER = 0xFF3A362A;
    public static final int OFF_BUTTON = 0xFF2A261D;
    public static final int OFF_LINE = 0xFF45402F;
    public static final int GRIP = 0xFF6B6250;
    public static final int TEXT = 0xFFECEEEF;
    public static final int MUTED = 0xFF8C8B86;
    public static final int FAINT = 0xFF66645C;
    public static final int OFF = 0xFF34302A;
    public static final int GOOD = 0xFF3DDC84;
    public static final int BAD = 0xFFFF5C5C;
    public static final int WARN = 0xFFFFB13D;
    public static final int HUD_BACK = 0xB0121317;
    public static final String DEFAULT_ACCENT = "amber";

    static {
        ACCENTS.put("amber", 0xFFF5A623);
        ACCENTS.put("emerald", 0xFF3DDC84);
        ACCENTS.put("cyan", 0xFF3EC6E0);
    }

    public static final List<String> ACCENT_NAMES = List.copyOf(ACCENTS.keySet());

    private Theme() {
    }

    /** Darker end of an accent's gradient (the design's buttons fade from the accent to a deeper shade). */
    public static int accentDeep(int accent) {
        return Draw.mix(accent, 0xFF000000, 0.22F);
    }

    /** Dark text that reads on an accent-filled button. */
    public static int onAccent(int accent) {
        return Draw.mix(accent, 0xFF000000, 0.82F);
    }

    public static int accent(String name) {
        return ACCENTS.getOrDefault(name, 0xFFF5A623);
    }
}
