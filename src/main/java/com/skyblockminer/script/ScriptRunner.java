package com.skyblockminer.script;

import com.skyblockminer.MinerMod;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;

/**
 * Runs one userscript on its own thread with Rhino. The script may block (sleep, wait for chat, walk somewhere)
 * without freezing the game: anything that touches Minecraft is queued and run by {@link #tick} on the client
 * thread, and per-tick state is read from a snapshot.
 *
 * <p>Scripts are sandboxed: they get the standard JavaScript objects and the macro API, but no Java classes.
 * Stopping is checked every few thousand instructions, so even {@code while (true) {}} ends when asked.
 */
final class ScriptRunner {
    private static final int MAX_QUEUED = 256;
    private static final int ACTIONS_PER_TICK = 64;
    private static final int MAX_EVENTS = 200;
    /** How long a script may run without waiting (for a tick, a chat line, a menu...) before it is stopped. */
    static volatile long maxBusyNanos = 5_000_000_000L;
    private static final ThreadLocal<ScriptRunner> CURRENT = new ThreadLocal<>();
    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            ScriptRunner runner = CURRENT.get();
            if (runner != null) {
                runner.checkStop();
            }
        }
    };

    /** Thrown on the script thread to unwind it. An Error, so a script's try/catch cannot swallow it. */
    static final class Stopped extends Error {
        Stopped() {
            super("script stopped", null, false, false);
        }
    }

    final String name;
    private final String source;
    private final ScriptGame game;
    private final ScriptApi api;
    private final Object lock = new Object();
    private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
    private final ArrayDeque<Object[]> events = new ArrayDeque<>();
    private final Set<String> listening = ConcurrentHashMap.newKeySet();
    private long ticks;
    private boolean tickPending;
    private long queued;
    private long snapshotSeq;
    private Pattern chatWait;
    private String chatHit;
    private volatile ScriptGame.State state = ScriptGame.State.EMPTY;
    private volatile boolean stopping;
    /** When the script last waited. Refreshed on every pass of {@link #await}, so only real computation counts as busy. */
    private volatile long busySince = System.nanoTime();
    private volatile boolean done;
    private volatile String error;
    private volatile String exitReason;
    private volatile String status = "";
    private Thread thread;
    // Script thread only.
    private ScriptableObject scope;
    private boolean dispatching;

    ScriptRunner(String name, String source, ScriptGame game) {
        this.name = name;
        this.source = source;
        this.game = game;
        this.api = new ScriptApi(this, game);
    }

    void start() {
        this.thread = new Thread(this::main, "Userscript " + this.name);
        this.thread.setDaemon(true);
        // Rhino finds its regexp engine through ServiceLoader, which looks in the context class loader.
        this.thread.setContextClassLoader(ScriptRunner.class.getClassLoader());
        this.thread.start();
    }

    /** Asks the script to end. Returns at once; the thread unwinds on its next instruction or wait. */
    void stop() {
        this.stopping = true;
        synchronized (this.lock) {
            this.actions.clear();
            this.lock.notifyAll();
        }
        if (this.thread != null) {
            this.thread.interrupt();
        }
    }

    boolean done() {
        return this.done;
    }

    /** Why the script ended ("finished", a stop() reason or an error), or null while it runs. */
    String ended() {
        if (!this.done) {
            return null;
        }
        if (this.error != null) {
            return "Script error in " + this.name + ": " + this.error;
        }
        synchronized (this.lock) {
            if (!this.actions.isEmpty()) {
                // Let the last log() or command() run before the macro stops.
                return null;
            }
        }
        String reason = this.exitReason;
        return reason == null || reason.isBlank() ? "Script " + this.name + " finished" : reason;
    }

    String status() {
        return this.status;
    }

    ScriptApi api() {
        return this.api;
    }

    // ---- client thread ----

    /** Runs queued actions, lets the game take its turn, and wakes the script for the new tick. */
    void tick() {
        long ran = 0;
        for (int i = 0; i < ACTIONS_PER_TICK; i++) {
            Runnable action;
            synchronized (this.lock) {
                action = this.actions.poll();
                if (action == null) {
                    break;
                }
                this.lock.notifyAll();
            }
            ran++;
            try {
                action.run();
            } catch (RuntimeException e) {
                MinerMod.LOGGER.warn("Userscript action failed", e);
                this.fail(e.toString());
            }
        }
        ScriptGame.State next;
        try {
            next = this.game.tick();
        } catch (RuntimeException e) {
            MinerMod.LOGGER.warn("Userscript tick failed", e);
            this.fail(e.toString());
            next = this.state;
        }
        synchronized (this.lock) {
            this.state = next;
            this.snapshotSeq += ran;
            this.ticks++;
            if (this.listening.contains("tick")) {
                this.tickPending = true;
            }
            this.lock.notifyAll();
        }
    }

    /** Delivers a game event (such as a chat line) to the script's handlers. */
    void post(String event, String arg) {
        synchronized (this.lock) {
            if (event.equals("chat") && this.chatWait != null && this.chatHit == null && this.chatWait.matcher(arg).find()) {
                this.chatHit = arg;
            }
            if (this.listening.contains(event)) {
                if (this.events.size() >= MAX_EVENTS) {
                    this.events.poll();
                }
                this.events.add(new Object[]{event, arg});
            }
            this.lock.notifyAll();
        }
    }

    // ---- script thread ----

    private void main() {
        CURRENT.set(this);
        Context cx = FACTORY.enterContext();
        try {
            cx.setInterpretedMode(true);
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setInstructionObserverThreshold(5000);
            cx.setApplicationClassLoader(ScriptRunner.class.getClassLoader());
            cx.setClassShutter(className -> className.equals(ScriptApi.class.getName()));
            WrapFactory wrap = new WrapFactory();
            wrap.setJavaPrimitiveWrap(false);
            cx.setWrapFactory(wrap);
            this.scope = cx.initSafeStandardObjects();
            ScriptableObject.putProperty(this.scope, "__api", Context.javaToJS(this.api, this.scope));
            cx.evaluateString(this.scope, ScriptApi.PRELUDE, "<macro api>", 1, null);
            cx.evaluateString(this.scope, this.source, this.name, 1, null);
            // Like a browser page: once the top level returns, handlers keep the script alive.
            this.await(this.listening::isEmpty, Long.MAX_VALUE, true);
        } catch (Stopped ignored) {
            // Asked to stop, or the script called stop().
        } catch (RhinoException e) {
            this.fail(describe(e));
        } catch (Throwable e) {
            MinerMod.LOGGER.warn("Userscript {} crashed", this.name, e);
            this.fail(e.toString());
        } finally {
            Context.exit();
            CURRENT.remove();
            this.done = true;
        }
    }

    static String describe(RhinoException e) {
        String where = e.lineNumber() > 0 ? " (line " + e.lineNumber() + ")" : "";
        return e.details() + where;
    }

    private void fail(String message) {
        if (this.error == null && !this.done) {
            this.error = message;
        }
        this.stopping = true;
        if (this.thread != null && Thread.currentThread() != this.thread) {
            this.thread.interrupt();
        }
    }

    void checkStop() {
        if (this.stopping) {
            throw new Stopped();
        }
        if (System.nanoTime() - this.busySince > maxBusyNanos) {
            this.exit("script ran for more than " + maxBusyNanos / 1_000_000_000L + " s without waiting");
        }
    }

    /** Ends the script from inside, stopping the macro with {@code reason}. */
    void exit(String reason) {
        this.exitReason = reason;
        this.stopping = true;
        throw new Stopped();
    }

    void status(String text) {
        this.status = text;
    }

    void listen(String event, boolean on) {
        if (on) {
            this.listening.add(event);
        } else {
            this.listening.remove(event);
        }
    }

    ScriptGame.State state() {
        return this.state;
    }

    /** True once the snapshot was taken after every action queued so far had run. */
    boolean settled() {
        synchronized (this.lock) {
            return this.snapshotSeq >= this.queued;
        }
    }

    ScriptableObject scope() {
        return this.scope;
    }

    /** Queues an action for the client thread without waiting for it. */
    void run(Runnable action) {
        synchronized (this.lock) {
            while (this.actions.size() >= MAX_QUEUED) {
                this.waitLock(50L);
            }
            this.checkStop();
            this.actions.add(action);
            this.queued++;
        }
    }

    /** Runs {@code action} on the client thread and waits for its result (takes until the next tick). */
    <T> T call(Supplier<T> action) {
        FutureTask<T> task = new FutureTask<>(action::get);
        this.run(task);
        this.await(task::isDone, Long.MAX_VALUE, false);
        try {
            return task.get();
        } catch (ExecutionException e) {
            throw Context.reportRuntimeError(String.valueOf(e.getCause()));
        } catch (InterruptedException e) {
            this.checkStop();
            throw new Stopped();
        }
    }

    void sleep(long ms) {
        this.await(() -> false, System.currentTimeMillis() + Math.max(0L, ms), true);
    }

    void waitTicks(int count) {
        long target;
        synchronized (this.lock) {
            target = this.ticks + Math.max(0, count);
        }
        this.await(() -> this.ticks >= target, Long.MAX_VALUE, true);
    }

    /** Waits for a chat line matching {@code pattern}; returns it, or null after {@code timeoutMs} (0 = forever). */
    String waitForChat(Pattern pattern, long timeoutMs) {
        synchronized (this.lock) {
            this.chatWait = pattern;
            this.chatHit = null;
        }
        try {
            long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
            this.await(() -> this.chatHit != null, deadline, true);
            synchronized (this.lock) {
                return this.chatHit;
            }
        } finally {
            synchronized (this.lock) {
                this.chatWait = null;
                this.chatHit = null;
            }
        }
    }

    /**
     * Blocks until {@code ready} (checked under the lock) or the deadline. With {@code dispatch}, queued events run
     * their handlers meanwhile, except inside a handler, so handlers never interleave.
     */
    private boolean await(BooleanSupplier ready, long deadline, boolean dispatch) {
        while (true) {
            this.busySince = System.nanoTime();
            this.checkStop();
            if (dispatch && !this.dispatching) {
                this.dispatchPending();
            }
            synchronized (this.lock) {
                if (ready.getAsBoolean()) {
                    return true;
                }
                long left = deadline == Long.MAX_VALUE ? 50L : deadline - System.currentTimeMillis();
                if (left <= 0L) {
                    return false;
                }
                if (dispatch && !this.dispatching && (!this.events.isEmpty() || this.tickPending)) {
                    continue;
                }
                this.waitLock(Math.min(left, 50L));
            }
        }
    }

    private void waitLock(long ms) {
        try {
            this.lock.wait(ms);
        } catch (InterruptedException e) {
            this.checkStop();
        }
    }

    private void dispatchPending() {
        while (true) {
            Object[] event;
            synchronized (this.lock) {
                event = this.events.poll();
                if (event == null && this.tickPending) {
                    this.tickPending = false;
                    event = new Object[]{"tick", null};
                }
            }
            if (event == null) {
                return;
            }
            this.dispatching = true;
            try {
                ScriptableObject.callMethod(this.scope, "__dispatch", event);
            } finally {
                this.dispatching = false;
            }
            this.checkStop();
        }
    }
}
