package com.skyblockminer;

/** The latest health, mana and spell clicks read from Wynncraft's action bar. */
final class WynnStatus {
    /** Readings older than this are treated as unknown (the bar is resent every second or so). */
    private static final long STALE_MS = 3000L;

    private WynnActionBar.Bar health;
    private WynnActionBar.Bar mana;
    private String clicks;
    private long updatedAt;

    void onActionBar(String text, long now) {
        WynnActionBar.Bar health = WynnActionBar.health(text);
        WynnActionBar.Bar mana = WynnActionBar.mana(text);
        if (health == null && mana == null) {
            return;
        }
        this.health = health != null ? health : this.health;
        this.mana = mana != null ? mana : this.mana;
        this.clicks = WynnActionBar.spellInputs(text);
        this.updatedAt = now;
    }

    private boolean fresh(long now) {
        return now - this.updatedAt <= STALE_MS;
    }

    /** Health in percent, or -1 when unknown. */
    int healthPercent(long now) {
        return this.health != null && this.fresh(now) ? this.health.percent() : -1;
    }

    /** Mana in percent, or -1 when unknown. */
    int manaPercent(long now) {
        return this.mana != null && this.fresh(now) ? this.mana.percent() : -1;
    }

    /** Clicks of a half-entered spell ("" when none), or null when unknown. */
    String clicks(long now) {
        return this.fresh(now) ? this.clicks : null;
    }

    String hudText(long now) {
        if (!this.fresh(now)) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (this.health != null) {
            text.append(String.format("%,d/%,d HP", this.health.current(), this.health.max()));
        }
        if (this.mana != null) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(String.format("%d/%d mana", this.mana.current(), this.mana.max()));
        }
        return text.toString();
    }
}
