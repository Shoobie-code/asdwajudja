package com.skyblockminer.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EventBusTest {
    @Test
    void deliversToAllSubscribers() {
        EventBus<String> bus = new EventBus<>();
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        bus.subscribe(e -> a.incrementAndGet());
        bus.subscribe(e -> b.incrementAndGet());
        bus.post("x");
        bus.post("y");
        assertEquals(2, a.get());
        assertEquals(2, b.get());
    }

    @Test
    void oneFailingListenerDoesNotStopOthers() {
        EventBus<String> bus = new EventBus<>();
        AtomicInteger reached = new AtomicInteger();
        bus.subscribe(e -> { throw new RuntimeException("boom"); });
        bus.subscribe(e -> reached.incrementAndGet());
        bus.post("x");
        assertEquals(1, reached.get());
    }

    @Test
    void unsubscribeStopsDelivery() {
        EventBus<String> bus = new EventBus<>();
        AtomicInteger n = new AtomicInteger();
        java.util.function.Consumer<String> l = e -> n.incrementAndGet();
        bus.subscribe(l);
        bus.post("x");
        bus.unsubscribe(l);
        bus.post("y");
        assertEquals(1, n.get());
        assertEquals(0, bus.listenerCount());
    }
}
