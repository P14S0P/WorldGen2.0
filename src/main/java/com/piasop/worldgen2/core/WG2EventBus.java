package com.piasop.worldgen2.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Lightweight in-mod event bus for generation hooks and third-party extensions.
 */
public final class WG2EventBus {
    private static final Map<Class<?>, List<Consumer<?>>> LISTENERS = new LinkedHashMap<>();

    private WG2EventBus() {
    }

    public static <T> void on(Class<T> eventType, Consumer<T> listener) {
        LISTENERS.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public static <T> void fire(T event) {
        List<Consumer<?>> listeners = LISTENERS.get(event.getClass());
        if (listeners == null) {
            return;
        }
        for (Consumer<?> listener : listeners) {
            ((Consumer<T>) listener).accept(event);
        }
    }

    public static <T> void off(Class<T> eventType, Consumer<T> listener) {
        Optional.ofNullable(LISTENERS.get(eventType)).ifPresent(list -> list.remove(listener));
    }

    public static void clear() {
        LISTENERS.clear();
    }
}
