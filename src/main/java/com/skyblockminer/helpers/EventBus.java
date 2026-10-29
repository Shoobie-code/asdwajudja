package com.skyblockminer.helpers;

import com.skyblockminer.MinerMod;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A minimal typed publish/subscribe bus, the foundation for a centralised event system. Listeners are held in a
 * copy-on-write list so posting is safe while subscribing, and one failing listener never stops the others.
 */
public final class EventBus<T> {
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<T> listener) {
        this.listeners.add(listener);
    }

    public void unsubscribe(Consumer<T> listener) {
        this.listeners.remove(listener);
    }

    /** Delivers {@code event} to every listener; a listener that throws is logged and skipped. */
    public void post(T event) {
        for (Consumer<T> listener : this.listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                MinerMod.LOGGER.warn("Event listener failed", e);
            }
        }
    }

    public int listenerCount() {
        return this.listeners.size();
    }
}
