package com.skyblockminer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Wynncraft's action bar. Wynncraft draws its HUD with resource-pack glyphs instead of plain text: numbers are
 * private-use characters U+E010..U+E01F ("0-9", k, m, b, t, '.', '/') joined by zero-width spacing characters, and
 * the spell-click indicator uses U+E100..U+E106. The glyph ranges here are the ones the Wynntils mod maps out.
 *
 * <p>Health and mana text look the same; they differ only in the spacer around them. Health sits between a negative
 * space (high surrogate U+DAFF) and a positive space (U+DB00), mana the other way round.
 */
final class WynnActionBar {
    record Bar(int current, int max) {
        int percent() {
            return this.max <= 0 ? 0 : (int) Math.round(this.current * 100.0 / this.max);
        }
    }

    private static final char DIGIT_FIRST = '\uE010';
    private static final String DIGITS = "0123456789kmbt./";
    private static final String CHAR_GAP = "\uDAFF\uDFFF";
    private static final String SLASH_GAP = "\uDB00\uDC02";
    private static final char NEGATIVE_SPACE = '\uDAFF';
    private static final char POSITIVE_SPACE = '\uDB00';
    private static final Pattern NUMBER_TEXT = Pattern.compile("(.)((?:[\uE010-\uE01F]|" + CHAR_GAP + "|" + SLASH_GAP + ")+)(.)");
    private static final Pattern VALUE = Pattern.compile("(\\d+(?:\\.\\d+)?[kmbt]?)/(\\d+(?:\\.\\d+)?[kmbt]?)");

    private static final String CLICK = "([\uE100-\uE105])";
    private static final String CLICK_GAP = "\\s\uE106\\s";
    private static final Pattern SPELL = Pattern.compile("\uDAFF\uDFE0" + CLICK + CLICK_GAP + CLICK + CLICK_GAP + CLICK + "\uDAFF\uDFE0");

    private WynnActionBar() {
    }

    static Bar health(String actionBar) {
        return bar(actionBar, NEGATIVE_SPACE, POSITIVE_SPACE);
    }

    static Bar mana(String actionBar) {
        return bar(actionBar, POSITIVE_SPACE, NEGATIVE_SPACE);
    }

    /**
     * The clicks of a spell being entered, like "R" or "RL" (L/R as they are shown, so inverted for archers), "" when
     * the indicator is empty, or null when there is no indicator on the bar.
     */
    static String spellInputs(String actionBar) {
        if (actionBar == null) {
            return null;
        }
        Matcher matcher = SPELL.matcher(actionBar);
        if (!matcher.find()) {
            return null;
        }
        StringBuilder clicks = new StringBuilder(3);
        for (int i = 1; i <= 3; i++) {
            char c = matcher.group(i).charAt(0);
            if (c == '\uE100' || c == '\uE103') {
                clicks.append('L');
            } else if (c == '\uE101' || c == '\uE104') {
                clicks.append('R');
            } else {
                break;
            }
        }
        return clicks.toString();
    }

    private static Bar bar(String actionBar, char before, char after) {
        if (actionBar == null) {
            return null;
        }
        Matcher matcher = NUMBER_TEXT.matcher(actionBar);
        int from = 0;
        while (from < actionBar.length() && matcher.find(from)) {
            // The closing spacer of one value can open the next, so keep searching from it.
            from = matcher.start(3);
            if (matcher.group(1).charAt(0) != before || matcher.group(3).charAt(0) != after) {
                continue;
            }
            Bar bar = decode(matcher.group(2));
            if (bar != null) {
                return bar;
            }
        }
        return null;
    }

    static Bar decode(String glyphs) {
        String text = glyphs.replace(CHAR_GAP, "").replace(SLASH_GAP, "");
        StringBuilder value = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            int index = text.charAt(i) - DIGIT_FIRST;
            if (index < 0 || index >= DIGITS.length()) {
                return null;
            }
            value.append(DIGITS.charAt(index));
        }
        Matcher matcher = VALUE.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        long current = number(matcher.group(1));
        long max = number(matcher.group(2));
        if (current < 0 || max <= 0 || current > Integer.MAX_VALUE || max > Integer.MAX_VALUE) {
            return null;
        }
        return new Bar((int) current, (int) max);
    }

    /** Parses short numbers like 950, 12k or 1.5m. */
    static long number(String text) {
        char last = text.charAt(text.length() - 1);
        long scale = switch (last) {
            case 'k' -> 1_000L;
            case 'm' -> 1_000_000L;
            case 'b' -> 1_000_000_000L;
            case 't' -> 1_000_000_000_000L;
            default -> 1L;
        };
        String digits = scale == 1L ? text : text.substring(0, text.length() - 1);
        try {
            return Math.round(Double.parseDouble(digits) * scale);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** Encodes a value the way Wynncraft draws it (for tests). */
    static String encode(String plain) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c == '/') {
                out.append(SLASH_GAP).append((char) (DIGIT_FIRST + DIGITS.indexOf('/'))).append(SLASH_GAP);
                continue;
            }
            if (i > 0 && plain.charAt(i - 1) != '/') {
                out.append(CHAR_GAP);
            }
            out.append((char) (DIGIT_FIRST + DIGITS.indexOf(c)));
        }
        return out.toString();
    }
}
