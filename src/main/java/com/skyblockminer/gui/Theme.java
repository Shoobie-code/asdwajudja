package com.skyblockminer.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Colors used by the GUI and HUD. The accent comes from the config so users can pick their own. */
public final class Theme {
    public static final Map<String, Integer> ACCENTS = new LinkedHashMap<>();
    public static final int BACKGROUND = 0xF20F1116;
    public static final int SIDEBAR = 0xF20B0D11;
    public static final int CARD = 0xFF161921;
    public static final int CARD_HOVER = 0xFF1C2029;
    public static final int FIELD = 0xFF0F1116;
    public static final int BORDER = 0xFF262B36;
    public static final int TEXT = 0xFFE8EAF0;
    public static final int MUTED = 0xFF8A90A0;
    public static final int FAINT = 0xFF555B6A;
    public static final int OFF = 0xFF2E3340;
    public static final int GOOD = 0xFF3DDC84;
    public static final int BAD = 0xFFFF5C5C;
    public static final int WARN = 0xFFFFB13D;
    public static final int HUD_BACK = 0xB00B0D11;

    static {
        ACCENTS.put("violet", 0xFF8B5CF6);
        ACCENTS.put("blue", 0xFF3B82F6);
        ACCENTS.put("cyan", 0xFF06B6D4);
        ACCENTS.put("green", 0xFF22C55E);
        ACCENTS.put("pink", 0xFFEC4899);
        ACCENTS.put("orange", 0xFFF97316);
        ACCENTS.put("red", 0xFFEF4444);
    }

    public static final List<String> ACCENT_NAMES = List.copyOf(ACCENTS.keySet());

    private Theme() {
    }

    public static int accent(String name) {
        return ACCENTS.getOrDefault(name, 0xFF8B5CF6);
    }
}
