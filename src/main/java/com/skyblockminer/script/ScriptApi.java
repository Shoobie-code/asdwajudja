package com.skyblockminer.script;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * The Java side of the userscript API. Scripts never call this directly: {@link #PRELUDE} wraps it in plain global
 * functions ({@code sleep}, {@code walkTo}, {@code on("chat", ...)}) and a read-only {@code player} object.
 * Public only so Rhino can reflect it; every method runs on the script's thread.
 */
public final class ScriptApi {
    /** Defines the globals scripts use. Kept in JavaScript so arguments are checked and defaulted in one place. */
    static final String PRELUDE = """
        var __handlers = {};
        function __dispatch(event, arg) {
            var list = (__handlers[event] || []).slice();
            for (var i = 0; i < list.length; i++) list[i](arg);
        }
        function on(event, fn) {
            if (typeof fn !== 'function') throw new TypeError('on(event, fn): fn must be a function');
            event = String(event);
            (__handlers[event] = __handlers[event] || []).push(fn);
            __api.listen(event, true);
            return fn;
        }
        function off(event, fn) {
            event = String(event);
            var list = __handlers[event] || [];
            var i = list.indexOf(fn);
            if (i >= 0) list.splice(i, 1);
            if (!list.length) __api.listen(event, false);
        }
        function random(min, max) { return min + Math.random() * (max - min); }
        function sleep(ms, maxMs) { __api.sleep(maxMs === undefined ? +ms || 0 : random(+ms, +maxMs)); }
        function waitTicks(n) { __api.waitTicks(n === undefined ? 1 : +n); }
        function waitUntil(test, timeoutMs) {
            var end = timeoutMs > 0 ? Date.now() + timeoutMs : Infinity;
            while (!test()) {
                if (Date.now() >= end) return false;
                __api.waitTicks(1);
            }
            return true;
        }
        function waitForChat(pattern, timeoutMs) {
            var regex = pattern instanceof RegExp;
            return __api.waitForChat(regex ? pattern.source : String(pattern), regex, regex && pattern.ignoreCase, +timeoutMs || 0);
        }
        function log() { __api.log(Array.prototype.map.call(arguments, String).join(' ')); }
        function toast(title, text) { __api.toast(String(title), text === undefined ? '' : String(text)); }
        function status(text) { __api.status(text == null ? '' : String(text)); }
        function stop(reason) { __api.exit(reason == null ? '' : String(reason)); }
        function pauseInMenus(on) { __api.pauseInMenus(on !== false); }

        var player = Object.freeze({
            get name() { return __api.name(); },
            get x() { return __api.x(); },
            get y() { return __api.y(); },
            get z() { return __api.z(); },
            get pos() { return { x: __api.x(), y: __api.y(), z: __api.z() }; },
            get yaw() { return __api.yaw(); },
            get pitch() { return __api.pitch(); },
            get health() { return __api.health(); },
            get maxHealth() { return __api.maxHealth(); },
            get onGround() { return __api.onGround(); },
            get slot() { return __api.slot(); },
            get heldItem() { return __api.held(); }
        });

        function key(name, down) { __api.key(String(name), down !== false); }
        function press(name, ms) { key(name, true); sleep(ms === undefined ? 80 : ms); key(name, false); }
        function releaseKeys() { __api.releaseKeys(); }

        function look(yaw, pitch, speed) { __api.look(+yaw, +pitch, speed === undefined ? -1 : +speed); }
        function lookAt(x, y, z, speed) { __api.lookAt(+x, +y, +z, speed === undefined ? -1 : +speed); }
        function lookDone() { return __api.lookDone(); }
        function waitLook(timeoutMs) { return waitUntil(lookDone, timeoutMs === undefined ? 3000 : timeoutMs); }
        function stopLooking() { __api.stopLook(); }

        function startWalk(x, y, z, radius) { __api.walk(+x, +y, +z, radius === undefined ? 1 : +radius); }
        function walking() { return __api.walking(); }
        function stopWalking() { __api.stopWalk(); }
        function walkTo(x, y, z, radius) {
            startWalk(x, y, z, radius);
            waitUntil(function () { return !__api.walking(); });
            return __api.walkResult() === 'done';
        }

        function mine(on) { __api.mine(on !== false); }
        function attack() { __api.attack(); }
        function use() { __api.use(); }
        function selectSlot(slot) { __api.selectSlot(+slot); }
        function findItem(name) { return __api.findItem(String(name)); }

        function chat(text) { __api.chat(String(text)); }
        function command(cmd) { __api.command(String(cmd)); }

        function screen() { return __api.screen(); }
        function findSlot(name) { return __api.findSlot(String(name)); }
        function slotName(slot) { return __api.slotName(+slot); }
        function clickSlot(slot) { __api.clickSlot(+slot); }
        function closeScreen() { __api.closeScreen(); }
        function inventoryFull() { return __api.inventoryFull(); }

        function block(x, y, z) { return __api.block(Math.floor(x), Math.floor(y), Math.floor(z)); }
        function sidebar() { return __api.sidebar(); }
        function tabList() { return __api.tabList(); }
        function area() { return __api.area(); }
        function server() { return __api.server(); }
        function mobs(radius, filter) { return __api.mobs(radius === undefined ? 16 : +radius, filter == null ? '' : String(filter)); }
        """;

    private static final List<String> KEYS = List.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use");
    private final ScriptRunner runner;
    private final ScriptGame game;
    private volatile boolean pauseInMenus = true;
    private volatile boolean mining;

    ScriptApi(ScriptRunner runner, ScriptGame game) {
        this.runner = runner;
        this.game = game;
    }

    /** Read by the routine on the client thread: false while the script wants to keep running in menus. */
    boolean pausesInMenus() {
        return this.pauseInMenus;
    }

    boolean mining() {
        return this.mining;
    }

    // ---- control ----

    public void sleep(double ms) {
        this.runner.sleep((long) ms);
    }

    public void waitTicks(double ticks) {
        this.runner.waitTicks((int) ticks);
    }

    public String waitForChat(String pattern, boolean regex, boolean ignoreCase, double timeoutMs) {
        Pattern compiled;
        try {
            compiled = Pattern.compile(regex ? pattern : Pattern.quote(pattern), ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        } catch (PatternSyntaxException e) {
            throw Context.reportRuntimeError("waitForChat: bad pattern " + pattern);
        }
        return this.runner.waitForChat(compiled, (long) timeoutMs);
    }

    public void listen(String event, boolean on) {
        if (!event.equals("chat") && !event.equals("tick")) {
            throw Context.reportRuntimeError("Unknown event \"" + event + "\" (use \"chat\" or \"tick\")");
        }
        this.runner.listen(event, on);
    }

    public void log(String text) {
        this.runner.run(() -> this.game.log(text));
    }

    public void toast(String title, String text) {
        this.runner.run(() -> this.game.toast(title, text));
    }

    public void status(String text) {
        this.runner.status(text);
    }

    public void exit(String reason) {
        this.runner.exit(reason);
    }

    public void pauseInMenus(boolean on) {
        this.pauseInMenus = on;
    }

    // ---- player snapshot ----

    public String name() {
        return this.runner.state().name();
    }

    public double x() {
        return this.runner.state().x();
    }

    public double y() {
        return this.runner.state().y();
    }

    public double z() {
        return this.runner.state().z();
    }

    public double yaw() {
        return this.runner.state().yaw();
    }

    public double pitch() {
        return this.runner.state().pitch();
    }

    public double health() {
        return this.runner.state().health();
    }

    public double maxHealth() {
        return this.runner.state().maxHealth();
    }

    public boolean onGround() {
        return this.runner.state().onGround();
    }

    /** Selected hotbar slot, 1-9. */
    public int slot() {
        return this.runner.state().slot() + 1;
    }

    public String held() {
        return this.runner.state().held();
    }

    // ---- movement ----

    public void key(String name, boolean down) {
        if (!KEYS.contains(name)) {
            throw Context.reportRuntimeError("Unknown key \"" + name + "\" (use one of " + String.join(", ", KEYS) + ")");
        }
        this.runner.run(() -> this.game.key(name, down));
    }

    public void releaseKeys() {
        this.mining = false;
        this.runner.run(this.game::releaseAll);
    }

    public void look(double yaw, double pitch, double speed) {
        this.runner.run(() -> this.game.look((float) yaw, (float) pitch, speed));
    }

    public void lookAt(double x, double y, double z, double speed) {
        this.runner.run(() -> this.game.lookAt(x, y, z, speed));
    }

    public boolean lookDone() {
        return this.runner.settled() && this.runner.state().lookDone();
    }

    public void stopLook() {
        this.runner.run(this.game::stopLook);
    }

    public void walk(double x, double y, double z, double radius) {
        this.runner.run(() -> this.game.walk(x, y, z, radius));
    }

    public boolean walking() {
        return !this.runner.settled() || this.runner.state().walking();
    }

    /** "done" or "failed: why" for the last walk. */
    public String walkResult() {
        return this.runner.state().walkResult();
    }

    public void stopWalk() {
        this.runner.run(this.game::stopWalk);
    }

    // ---- actions ----

    public void mine(boolean on) {
        this.mining = on;
        this.runner.run(() -> this.game.mine(on));
    }

    public void attack() {
        this.runner.run(this.game::attack);
    }

    public void use() {
        this.runner.run(this.game::use);
    }

    /** Selects hotbar slot 1-9. */
    public void selectSlot(double slot) {
        int index = (int) slot - 1;
        if (index < 0 || index > 8) {
            throw Context.reportRuntimeError("selectSlot: slot must be 1 to 9, not " + slot);
        }
        this.runner.run(() -> this.game.selectSlot(index));
    }

    /** Hotbar slot 1-9 holding an item whose name contains {@code name}, or -1. */
    public int findItem(String name) {
        int slot = this.runner.call(() -> this.game.findHotbar(name));
        return slot < 0 ? -1 : slot + 1;
    }

    public void chat(String text) {
        this.runner.run(() -> this.game.chat(text));
    }

    public void command(String command) {
        this.runner.run(() -> this.game.command(command));
    }

    // ---- menus ----

    public String screen() {
        return this.runner.call(this.game::screen);
    }

    public int findSlot(String name) {
        return this.runner.call(() -> this.game.menuSlot(name));
    }

    public String slotName(double slot) {
        return this.runner.call(() -> this.game.slotName((int) slot));
    }

    public void clickSlot(double slot) {
        this.runner.run(() -> this.game.clickSlot((int) slot));
    }

    public void closeScreen() {
        this.runner.run(this.game::closeScreen);
    }

    public boolean inventoryFull() {
        return this.runner.call(this.game::inventoryFull);
    }

    // ---- world ----

    public String block(double x, double y, double z) {
        return this.runner.call(() -> this.game.block((int) x, (int) y, (int) z));
    }

    public Scriptable sidebar() {
        return this.array(this.runner.call(this.game::sidebar));
    }

    public Scriptable tabList() {
        return this.array(this.runner.call(this.game::tabList));
    }

    public String area() {
        return this.runner.call(this.game::area);
    }

    public String server() {
        return this.runner.call(this.game::server);
    }

    public Scriptable mobs(double radius, String filter) {
        List<ScriptGame.Mob> found = this.runner.call(() -> this.game.mobs(radius, filter));
        Context cx = Context.getCurrentContext();
        Object[] out = new Object[found.size()];
        for (int i = 0; i < out.length; i++) {
            ScriptGame.Mob mob = found.get(i);
            Scriptable entry = cx.newObject(this.runner.scope());
            ScriptableObject.putProperty(entry, "name", mob.name());
            ScriptableObject.putProperty(entry, "x", mob.x());
            ScriptableObject.putProperty(entry, "y", mob.y());
            ScriptableObject.putProperty(entry, "z", mob.z());
            ScriptableObject.putProperty(entry, "distance", mob.distance());
            out[i] = entry;
        }
        return cx.newArray(this.runner.scope(), out);
    }

    private Scriptable array(List<String> items) {
        return Context.getCurrentContext().newArray(this.runner.scope(), items.toArray());
    }
}
