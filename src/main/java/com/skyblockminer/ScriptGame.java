package com.skyblockminer;

import java.util.List;

/**
 * The game as a userscript sees it. {@link ScriptRunner} calls every method on the client thread (between ticks),
 * never on the script's own thread, so implementations can touch Minecraft freely.
 */
interface ScriptGame {
    /** Runs after the tick's queued script actions: keeps held keys down, walks, mines, and returns a fresh snapshot. */
    State tick();

    /** Lets go of everything the script holds (keys, walking, mining, the camera). */
    void releaseAll();

    /** Holds or releases a named key; returns false for an unknown name. */
    boolean key(String name, boolean down);

    void look(float yaw, float pitch, double speed);

    void lookAt(double x, double y, double z, double speed);

    void stopLook();

    void walk(double x, double y, double z, double radius);

    void stopWalk();

    /** Starts or stops breaking whatever block the crosshair is on, every tick. */
    void mine(boolean on);

    /** Hits the entity under the crosshair (or swings at nothing). */
    void attack();

    /** Right clicks the held item. */
    void use();

    /** Selects hotbar slot 0-8. */
    void selectSlot(int slot);

    /** Hotbar slot 0-8 of the first item whose name contains {@code name}, or -1. */
    int findHotbar(String name);

    void chat(String text);

    void command(String command);

    void log(String text);

    void toast(String title, String text);

    /** Title of the open screen, or null when none is open. */
    String screen();

    /** Container slot whose item name contains {@code name}, or -1. */
    int menuSlot(String name);

    /** Item name in a container slot, or "" when empty. */
    String slotName(int slot);

    void clickSlot(int slot);

    void closeScreen();

    boolean inventoryFull();

    /** Block id such as "minecraft:stone". */
    String block(int x, int y, int z);

    List<String> sidebar();

    List<String> tabList();

    /** SkyBlock area from the tab list, or null. */
    String area();

    /** "hypixel", "wynncraft" or "other". */
    String server();

    /** Name-tagged mobs within {@code radius} whose tag contains {@code filter} (any when empty), nearest first. */
    List<Mob> mobs(double radius, String filter);

    /** Everything a script reads every tick without waiting for the client thread. */
    record State(String name, double x, double y, double z, float yaw, float pitch, float health, float maxHealth, boolean onGround,
        int slot, String held, String screen, boolean lookDone, boolean walking, String walkResult) {
        static final State EMPTY = new State("", 0, 0, 0, 0, 0, 0, 0, false, 0, "", null, true, false, "");
    }

    record Mob(String name, double x, double y, double z, double distance) {
    }
}
