package com.piasop.worldgen2.core;

import com.piasop.worldgen2.api.events.WG2LifecycleEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WG2EventBusTest {
    @AfterEach
    void tearDown() {
        WG2EventBus.clear();
    }

    @Test
    void deliversEventsToRegisteredListeners() {
        AtomicInteger counter = new AtomicInteger();
        WG2EventBus.on(WG2LifecycleEvent.class, event -> counter.incrementAndGet());

        WG2EventBus.fire(new WG2LifecycleEvent(WG2LifecycleEvent.Stage.READY));
        WG2EventBus.fire(new WG2LifecycleEvent(WG2LifecycleEvent.Stage.SHUTDOWN));

        assertEquals(2, counter.get());
    }

    @Test
    void removesListenersWithOff() {
        AtomicInteger counter = new AtomicInteger();
        var listener = (java.util.function.Consumer<WG2LifecycleEvent>) event -> counter.incrementAndGet();

        WG2EventBus.on(WG2LifecycleEvent.class, listener);
        WG2EventBus.fire(new WG2LifecycleEvent(WG2LifecycleEvent.Stage.READY));
        WG2EventBus.off(WG2LifecycleEvent.class, listener);
        WG2EventBus.fire(new WG2LifecycleEvent(WG2LifecycleEvent.Stage.SHUTDOWN));

        assertEquals(1, counter.get());
    }
}
