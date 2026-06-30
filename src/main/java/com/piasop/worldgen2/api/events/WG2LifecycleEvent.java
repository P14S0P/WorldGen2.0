package com.piasop.worldgen2.api.events;

public record WG2LifecycleEvent(Stage stage) {
    public enum Stage {
        READY,
        SHUTDOWN
    }
}
