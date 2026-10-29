package com.skyblockminer.macro;

/** A macro lifecycle event posted on {@link Macro#events}, so other features (or userscripts) can react. */
public record MacroEvent(Kind kind, MacroType type) {
    public enum Kind {
        STARTED,
        STOPPED
    }
}
