package com.skyblockminer.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One editable option. Settings read and write the config through lambdas, so the config stays a plain
 * Gson object while the GUI and the {@code /sm set} command share a single description of every option.
 */
public abstract class Setting {
    public final String id;
    public final String name;
    public final String description;
    private BooleanSupplier visible = () -> true;
    protected Runnable changed = () -> {
    };

    protected Setting(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public Setting visibleWhen(BooleanSupplier condition) {
        this.visible = condition;
        return this;
    }

    public boolean visible() {
        return this.visible.getAsBoolean();
    }

    /** Called after every change (the registry uses it to mark the config dirty). */
    public Setting onChange(Runnable action) {
        this.changed = action;
        return this;
    }

    /** Current value as text, for chat output and search. */
    public abstract String valueText();

    /** Parses and applies a value typed in chat; returns an error message or null on success. */
    public abstract String set(String raw);

    /** True for settings that hold a value (buttons do not). */
    public boolean hasValue() {
        return true;
    }

    public boolean matches(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return this.name.toLowerCase(Locale.ROOT).contains(q) || this.description.toLowerCase(Locale.ROOT).contains(q)
            || this.id.contains(q);
    }

    public static final class Toggle extends Setting {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;

        public Toggle(String id, String name, String description, BooleanSupplier get, Consumer<Boolean> set) {
            super(id, name, description);
            this.get = get;
            this.set = set;
        }

        public boolean get() {
            return this.get.getAsBoolean();
        }

        public void toggle() {
            this.set.accept(!this.get());
            this.changed.run();
        }

        @Override
        public String valueText() {
            return this.get() ? "on" : "off";
        }

        @Override
        public String set(String raw) {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            boolean on;
            if (List.of("on", "true", "yes", "1").contains(value)) {
                on = true;
            } else if (List.of("off", "false", "no", "0").contains(value)) {
                on = false;
            } else {
                return "use on or off";
            }
            this.set.accept(on);
            this.changed.run();
            return null;
        }
    }

    public static final class Slider extends Setting {
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        public final double min;
        public final double max;
        public final double step;
        private final String unit;

        public Slider(String id, String name, String description, double min, double max, double step, String unit,
            DoubleSupplier get, DoubleConsumer set) {
            super(id, name, description);
            this.min = min;
            this.max = max;
            this.step = step;
            this.unit = unit;
            this.get = get;
            this.set = set;
        }

        public double get() {
            return this.get.getAsDouble();
        }

        /** Position of the value on the track, 0-1. */
        public double fraction() {
            return (this.get() - this.min) / (this.max - this.min);
        }

        public void setFraction(double fraction) {
            this.apply(this.min + Math.max(0.0, Math.min(1.0, fraction)) * (this.max - this.min));
        }

        public void nudge(int direction) {
            this.apply(this.get() + direction * this.step);
        }

        private void apply(double value) {
            double snapped = Math.round((value - this.min) / this.step) * this.step + this.min;
            snapped = Math.max(this.min, Math.min(this.max, snapped));
            if (snapped != this.get()) {
                this.set.accept(snapped);
                this.changed.run();
            }
        }

        @Override
        public String valueText() {
            double value = this.get();
            String number = this.step >= 1.0 ? String.valueOf((long) Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
            return number + this.unit;
        }

        @Override
        public String set(String raw) {
            try {
                double value = Double.parseDouble(raw.trim());
                if (value < this.min || value > this.max) {
                    return "must be between " + fmt(this.min) + " and " + fmt(this.max);
                }
                this.apply(value);
                return null;
            } catch (NumberFormatException e) {
                return "not a number";
            }
        }

        private static String fmt(double value) {
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        }
    }

    public static final class Choice extends Setting {
        private final Supplier<String> get;
        private final Consumer<String> set;
        private final Supplier<List<String>> options;
        private final Function<String, String> label;

        public Choice(String id, String name, String description, Supplier<List<String>> options, Function<String, String> label,
            Supplier<String> get, Consumer<String> set) {
            super(id, name, description);
            this.options = options;
            this.label = label;
            this.get = get;
            this.set = set;
        }

        public String get() {
            return this.get.get();
        }

        public List<String> options() {
            return this.options.get();
        }

        public String label() {
            return this.label.apply(this.get());
        }

        public void cycle(int direction) {
            List<String> options = this.options();
            if (options.isEmpty()) {
                return;
            }
            int index = options.indexOf(this.get());
            this.set.accept(options.get(Math.floorMod(index + direction, options.size())));
            this.changed.run();
        }

        @Override
        public String valueText() {
            return this.get();
        }

        @Override
        public String set(String raw) {
            for (String option : this.options()) {
                if (option.equalsIgnoreCase(raw.trim())) {
                    this.set.accept(option);
                    this.changed.run();
                    return null;
                }
            }
            return "choose one of: " + String.join(", ", this.options());
        }
    }

    public static final class Multi extends Setting {
        private final Supplier<List<String>> get;
        public final List<String> options;

        public Multi(String id, String name, String description, List<String> options, Supplier<List<String>> get) {
            super(id, name, description);
            this.options = options;
            this.get = get;
        }

        public boolean selected(String option) {
            return this.get.get().contains(option);
        }

        public void toggle(String option) {
            List<String> list = this.get.get();
            if (!list.remove(option)) {
                list.add(option);
            }
            this.changed.run();
        }

        @Override
        public String valueText() {
            List<String> list = this.get.get();
            return list.isEmpty() ? "none" : String.join(" ", list);
        }

        @Override
        public String set(String raw) {
            List<String> chosen = new ArrayList<>();
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.equals("all")) {
                chosen.addAll(this.options);
            } else if (!value.equals("none")) {
                for (String part : value.split("[ ,]+")) {
                    if (!this.options.contains(part)) {
                        return "unknown option " + part + " (options: " + String.join(", ", this.options) + ")";
                    }
                    if (!chosen.contains(part)) {
                        chosen.add(part);
                    }
                }
            }
            List<String> list = this.get.get();
            list.clear();
            list.addAll(chosen);
            this.changed.run();
            return null;
        }
    }

    public static final class Text extends Setting {
        private final Supplier<String> get;
        private final Consumer<String> set;
        public final String placeholder;
        public final boolean secret;
        public final int maxLength;

        public Text(String id, String name, String description, String placeholder, boolean secret, int maxLength,
            Supplier<String> get, Consumer<String> set) {
            super(id, name, description);
            this.placeholder = placeholder;
            this.secret = secret;
            this.maxLength = maxLength;
            this.get = get;
            this.set = set;
        }

        public String get() {
            return this.get.get();
        }

        public void put(String value) {
            this.set.accept(value.length() > this.maxLength ? value.substring(0, this.maxLength) : value);
            this.changed.run();
        }

        @Override
        public String valueText() {
            String value = this.get();
            return this.secret && !value.isEmpty() ? "(hidden)" : value.isEmpty() ? "(empty)" : value;
        }

        @Override
        public String set(String raw) {
            this.put(raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("none") ? "" : raw.trim());
            return null;
        }
    }

    public static final class Button extends Setting {
        private final Runnable action;
        public final String label;

        public Button(String id, String name, String description, String label, Runnable action) {
            super(id, name, description);
            this.label = label;
            this.action = action;
        }

        public void press() {
            this.action.run();
        }

        @Override
        public boolean hasValue() {
            return false;
        }

        @Override
        public String valueText() {
            return "";
        }

        @Override
        public String set(String raw) {
            this.press();
            return null;
        }
    }

    /** A titled group of settings inside a category page. */
    public record Section(String title, List<Setting> settings) {
    }

    /** A sidebar page. */
    public record Category(String name, String icon, List<Section> sections) {
    }
}
