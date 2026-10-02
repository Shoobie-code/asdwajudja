package com.skyblockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * One macro behaviour driven by {@link Macro}. Macro owns the shared machinery (failsafes, breaks,
 * rejoining, menus); a routine only decides what to do on each tick while it is allowed to act.
 */
interface Routine {
    /** Runs one client tick and returns the status shown on the HUD. */
    String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level);

    /** Clears all state before a fresh start. */
    void reset();

    /** Lets go of every key the routine may be holding (called on pause, break and stop). */
    default void releaseKeys(Minecraft mc) {
    }

    /** True while the routine is holding a block break, so vanilla attack handling stays out of the way. */
    default boolean breaking() {
        return false;
    }

    /** True while the routine itself opened a menu and must keep ticking instead of pausing for it. */
    default boolean ownsMenu() {
        return false;
    }

    /** Called when the macro gets back to work after a rejoin or after walking to {@link #home}. */
    default void resume() {
    }

    /** Server (non-player) chat lines, for routines that react to game messages. */
    default void onChat(Macro macro, String text) {
    }

    /** Extra HUD line with routine progress, or null. */
    default String hudLine(Macro macro) {
        return null;
    }

    /** Command to run after auto-rejoin brings the player back to SkyBlock, or null to resume in place. */
    default String rejoinWarp(MinerConfig config) {
        return null;
    }

    /** Where to walk after the rejoin warp before resuming, or null. */
    default Vec3 home(MinerConfig config) {
        return null;
    }

    /** World overlays (called every client tick, like route rendering). */
    default void render(Macro macro, LocalPlayer player) {
    }
}
