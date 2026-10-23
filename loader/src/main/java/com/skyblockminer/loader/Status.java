package com.skyblockminer.loader;

import com.skyblockminer.loader.Releases.Channel;

/** Shared between the startup code, the background switcher and the title screen widget. */
public final class Status {
    public enum State {
        /** The macro jar is in the mods folder, so Fabric loads it and the loader stays out of the way. */
        DIRECT,
        LOADED,
        NOT_LOADED,
        WORKING,
        /** A different jar is on disk and needs a restart to run. */
        RESTART
    }

    public static volatile LoaderState state;
    public static volatile State current = State.NOT_LOADED;
    public static volatile String message = "";
    /** Label of the jar running this session, e.g. "v2.1.0". */
    public static volatile String running = "";
    public static volatile Channel runningChannel = Channel.STABLE;
    /** File name of the jar running this session, empty when none is. */
    public static volatile String runningJar = "";

    private Status() {
    }

    public static void set(State s, String text) {
        current = s;
        message = text;
    }
}
