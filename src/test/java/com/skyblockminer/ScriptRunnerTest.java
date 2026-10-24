package com.skyblockminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class ScriptRunnerTest {
    /** Records what scripts do; the test thread plays the client thread. */
    private static final class FakeGame implements ScriptGame {
        final List<String> calls = new ArrayList<>();
        int walkTicks = -1;
        boolean lookDone = true;

        @Override
        public State tick() {
            boolean walking = this.walkTicks > 0;
            if (walking) {
                this.walkTicks--;
            }
            return new State("Steve", 1.5, 64, -3, 90, 10, 20, 20, true, 2, "Pickaxe", null, this.lookDone, walking, "done");
        }

        @Override
        public void releaseAll() {
            this.calls.add("release");
        }

        @Override
        public boolean key(String name, boolean down) {
            this.calls.add("key " + name + " " + down);
            return true;
        }

        @Override
        public void look(float yaw, float pitch, double speed) {
            this.calls.add("look " + yaw + " " + pitch);
        }

        @Override
        public void lookAt(double x, double y, double z, double speed) {
            this.calls.add("lookAt " + x + " " + y + " " + z);
        }

        @Override
        public void stopLook() {
        }

        @Override
        public void walk(double x, double y, double z, double radius) {
            this.calls.add("walk " + x + " " + y + " " + z);
            this.walkTicks = 3;
        }

        @Override
        public void stopWalk() {
        }

        @Override
        public void mine(boolean on) {
        }

        @Override
        public void attack() {
            this.calls.add("attack");
        }

        @Override
        public void use() {
        }

        @Override
        public void selectSlot(int slot) {
            this.calls.add("slot " + slot);
        }

        @Override
        public int findHotbar(String name) {
            return name.equals("Rod") ? 4 : -1;
        }

        @Override
        public void chat(String text) {
        }

        @Override
        public void command(String command) {
            this.calls.add("/" + command);
        }

        @Override
        public void log(String text) {
            this.calls.add("log " + text);
        }

        @Override
        public void toast(String title, String text) {
        }

        @Override
        public String screen() {
            return null;
        }

        @Override
        public int menuSlot(String name) {
            return -1;
        }

        @Override
        public String slotName(int slot) {
            return "";
        }

        @Override
        public void clickSlot(int slot) {
        }

        @Override
        public void closeScreen() {
        }

        @Override
        public boolean inventoryFull() {
            return false;
        }

        @Override
        public String block(int x, int y, int z) {
            return "minecraft:stone@" + x + "," + y + "," + z;
        }

        @Override
        public List<String> sidebar() {
            return List.of("Purse: 100", "Bits: 5");
        }

        @Override
        public List<String> tabList() {
            return List.of();
        }

        @Override
        public String area() {
            return "Dwarven Mines";
        }

        @Override
        public String server() {
            return "hypixel";
        }

        @Override
        public List<Mob> mobs(double radius, String filter) {
            return List.of(new Mob("Zombie 100❤", 1, 2, 3, 4));
        }
    }

    private static ScriptRunner start(FakeGame game, String source) {
        ScriptRunner runner = new ScriptRunner("test.js", source, game);
        runner.start();
        return runner;
    }

    /** Ticks like the client would until {@code until} holds or two seconds pass. */
    private static void tickUntil(ScriptRunner runner, BooleanSupplier until) throws InterruptedException {
        long end = System.currentTimeMillis() + 2000L;
        while (!until.getAsBoolean() && System.currentTimeMillis() < end) {
            runner.tick();
            Thread.sleep(2L);
        }
        assertTrue(until.getAsBoolean(), "timed out");
    }

    @Test
    void actionsRunInOrderOnTheTickingThread() throws InterruptedException {
        FakeGame game = new FakeGame();
        ScriptRunner runner = start(game, """
            key('forward');
            sleep(5);
            command('warp hub');
            selectSlot(findItem('Rod'));
            log(player.name, player.x, player.slot, block(1.7, 2, -0.5), sidebar().length, area());
            """);
        tickUntil(runner, () -> runner.ended() != null);
        assertEquals("Script test.js finished", runner.ended());
        assertEquals(List.of("key forward true", "/warp hub", "slot 4", "log Steve 1.5 3 minecraft:stone@1,2,-1 2 Dwarven Mines"), game.calls);
    }

    @Test
    void walkToWaitsForTheWalkToFinish() throws InterruptedException {
        FakeGame game = new FakeGame();
        ScriptRunner runner = start(game, "var ok = walkTo(10, 64, 20); log(ok, walking());");
        tickUntil(runner, () -> runner.ended() != null);
        assertEquals(List.of("walk 10.0 64.0 20.0", "log true false"), game.calls);
    }

    @Test
    void stopEndsBusyLoopsAndCannotBeCaught() throws InterruptedException {
        for (String source : List.of("while (true) {}", "while (true) { try { sleep(1); } catch (e) {} }", "on('tick', function () { while (true) {} });")) {
            FakeGame game = new FakeGame();
            ScriptRunner runner = start(game, source);
            for (int i = 0; i < 5; i++) {
                runner.tick();
                Thread.sleep(5L);
            }
            assertFalse(runner.done(), source);
            runner.stop();
            tickUntil(runner, runner::done);
        }
    }

    @Test
    void errorsReportTheLine() throws InterruptedException {
        FakeGame game = new FakeGame();
        ScriptRunner runner = start(game, "log('a');\nnotAFunction();");
        tickUntil(runner, runner::done);
        assertTrue(runner.ended().contains("line 2"), runner.ended());
        assertTrue(runner.ended().contains("notAFunction"), runner.ended());

        ScriptRunner bad = start(new FakeGame(), "key('fly');");
        tickUntil(bad, bad::done);
        assertTrue(bad.ended().contains("Unknown key"), bad.ended());
    }

    @Test
    void scriptsCannotReachJava() throws InterruptedException {
        for (String source : List.of("java.lang.System.exit(1);", "Packages.java.lang.Runtime.getRuntime();",
            "__api.getClass().forName('java.lang.Runtime');")) {
            ScriptRunner runner = start(new FakeGame(), source);
            tickUntil(runner, runner::done);
            assertTrue(runner.ended().startsWith("Script error"), source + " -> " + runner.ended());
        }
    }

    @Test
    void stopWithAReasonEndsTheScript() throws InterruptedException {
        ScriptRunner runner = start(new FakeGame(), "stop('Out of coins'); log('never');");
        tickUntil(runner, () -> runner.ended() != null);
        assertEquals("Out of coins", runner.ended());
    }

    @Test
    void chatHandlersAndWaits() throws InterruptedException {
        FakeGame game = new FakeGame();
        ScriptRunner runner = start(game, """
            on('chat', function (line) { if (line === 'ping') command('pong'); });
            log('ready');
            var hit = waitForChat(/You earned (\\d+) coins/);
            log(hit.match(/\\d+/)[0]);
            var none = waitForChat('never said', 20);
            log(none);
            """);
        tickUntil(runner, () -> game.calls.contains("log ready"));
        runner.post("chat", "ping");
        tickUntil(runner, () -> game.calls.contains("/pong"));
        runner.post("chat", "You earned 42 coins");
        tickUntil(runner, () -> game.calls.contains("log null"));
        assertTrue(game.calls.contains("log 42"), game.calls.toString());
        // The chat handler keeps the script alive after its top level returns.
        assertFalse(runner.done());
        assertNull(runner.ended());
        runner.stop();
        tickUntil(runner, runner::done);
    }

    @Test
    void tickHandlersRunEveryTick() throws InterruptedException {
        FakeGame game = new FakeGame();
        ScriptRunner runner = start(game, """
            var n = 0;
            on('tick', function tick() { if (++n === 3) { attack(); off('tick', tick); } });
            """);
        tickUntil(runner, () -> runner.ended() != null);
        assertEquals(List.of("attack"), game.calls);
        assertNotNull(runner.ended());
    }
}
