package com.skyblockminer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads skill XP gains from the action bar ("+12.5 Farming (1,234/50,000)"), works out XP per hour over a
 * rolling window and the time to the next level, and remembers the best rate each macro has reached for
 * each skill, so the fastest way to level a skill can be looked up later.
 */
public final class SkillTracker {
    static final Pattern GAIN = Pattern.compile(
        "\\+([\\d,.]+) (Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Taming|Carpentry|Runecrafting|Social|Hunting) \\(([^)]*)\\)");
    private static final Pattern PROGRESS = Pattern.compile("([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)");
    private static final Pattern PERCENT = Pattern.compile("([\\d.]+)%");
    private static final long WINDOW_MS = 10 * 60_000L;
    private static final long ACTIVE_MS = 5 * 60_000L;
    /** Rates need some history before they mean anything. */
    private static final long MIN_SAMPLE_MS = 60_000L;

    /** One skill's live numbers. {@code needed} is -1 when the action bar only shows a percentage. */
    public record Skill(String name, double gained, double perHour, double current, double needed, long lastAt) {
        /** Milliseconds until the next level at the current rate, or -1. */
        public long etaMs() {
            if (this.needed <= 0.0 || this.perHour <= 0.0) {
                return -1L;
            }
            return (long) ((this.needed - this.current) / this.perHour * 3_600_000.0);
        }
    }

    private static final class State {
        final Deque<double[]> samples = new ArrayDeque<>();
        double gained;
        double current;
        double needed = -1.0;
        long lastAt;
    }

    private final Map<String, State> skills = new LinkedHashMap<>();
    private final Map<String, Double> best;

    /** @param best best XP/hour per "skill|macro", kept in the config between sessions */
    public SkillTracker(Map<String, Double> best) {
        this.best = best;
    }

    public void reset() {
        this.skills.clear();
    }

    /** Feeds one action-bar line. Returns true when it held a skill gain. */
    public boolean onActionBar(String text, long now, String activity) {
        Matcher matcher = GAIN.matcher(text);
        if (!matcher.find()) {
            return false;
        }
        double amount = parse(matcher.group(1));
        State state = this.skills.computeIfAbsent(matcher.group(2), k -> new State());
        state.gained += amount;
        state.lastAt = now;
        state.samples.addLast(new double[]{now, amount});
        while (!state.samples.isEmpty() && now - state.samples.peekFirst()[0] > WINDOW_MS) {
            state.samples.removeFirst();
        }
        String progress = matcher.group(3);
        Matcher fraction = PROGRESS.matcher(progress);
        if (fraction.find()) {
            state.current = parse(fraction.group(1));
            state.needed = parse(fraction.group(2));
        } else {
            Matcher percent = PERCENT.matcher(progress);
            if (percent.find()) {
                state.current = Double.parseDouble(percent.group(1));
                state.needed = -1.0;
            }
        }
        double rate = rate(state, now);
        if (activity != null && rate > 0.0) {
            this.best.merge(matcher.group(2) + "|" + activity, rate, Math::max);
        }
        return true;
    }

    private static double rate(State state, long now) {
        if (state.samples.isEmpty()) {
            return 0.0;
        }
        long span = now - (long) state.samples.peekFirst()[0];
        if (span < MIN_SAMPLE_MS) {
            return 0.0;
        }
        double sum = 0.0;
        for (double[] sample : state.samples) {
            sum += sample[1];
        }
        return sum / span * 3_600_000.0;
    }

    /** Skills that gained XP recently, most recent first. */
    public List<Skill> active(long now) {
        List<Skill> out = new ArrayList<>();
        for (Map.Entry<String, State> entry : this.skills.entrySet()) {
            State s = entry.getValue();
            if (now - s.lastAt <= ACTIVE_MS) {
                out.add(new Skill(entry.getKey(), s.gained, rate(s, now), s.current, s.needed, s.lastAt));
            }
        }
        out.sort((a, b) -> Long.compare(b.lastAt(), a.lastAt()));
        return out;
    }

    /** Best recorded XP/hour per macro for one skill, fastest first. */
    public List<Map.Entry<String, Double>> bestFor(String skill) {
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        String prefix = skill + "|";
        for (Map.Entry<String, Double> entry : this.best.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                out.add(Map.entry(entry.getKey().substring(prefix.length()), entry.getValue()));
            }
        }
        out.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        return Collections.unmodifiableList(out);
    }

    public Map<String, Double> best() {
        return this.best;
    }

    /** "1,234.5", "12.3k", "4M" to numbers. */
    static double parse(String text) {
        String clean = text.replace(",", "").trim();
        double scale = 1.0;
        char last = Character.toLowerCase(clean.charAt(clean.length() - 1));
        if (last == 'k') {
            scale = 1e3;
            clean = clean.substring(0, clean.length() - 1);
        } else if (last == 'm') {
            scale = 1e6;
            clean = clean.substring(0, clean.length() - 1);
        }
        return Double.parseDouble(clean) * scale;
    }

    /** HUD lines: "Farming +12.3k  98.2k/h  next in 14m". */
    public List<String> lines(long now) {
        List<String> out = new ArrayList<>();
        for (Skill skill : this.active(now)) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%s +%s", skill.name(), compact(skill.gained())));
            if (skill.perHour() > 0.0) {
                line.append(String.format(Locale.ROOT, "  %s/h", compact(skill.perHour())));
            }
            long eta = skill.etaMs();
            if (eta >= 0L) {
                line.append("  next in ").append(Macro.clock(eta));
            }
            out.add(line.toString());
        }
        return out;
    }

    static String compact(double value) {
        if (value >= 1e6) {
            return String.format(Locale.ROOT, "%.2fM", value / 1e6);
        }
        if (value >= 1e3) {
            return String.format(Locale.ROOT, "%.1fk", value / 1e3);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
