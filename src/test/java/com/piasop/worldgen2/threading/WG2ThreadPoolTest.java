package com.piasop.worldgen2.threading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WG2ThreadPoolTest {
    @AfterEach
    void tearDown() {
        WG2ThreadPool.resetForTesting();
    }

    @Test
    void executesCallableTasks() throws Exception {
        Future<Integer> result = WG2ThreadPool.submit(() -> 21 + 21);

        assertEquals(42, result.get(5, TimeUnit.SECONDS));
    }

    @Test
    void executesRunnableTasks() throws Exception {
        var flag = new boolean[1];
        Future<?> future = WG2ThreadPool.submit(() -> flag[0] = true);

        future.get(5, TimeUnit.SECONDS);
        assertTrue(flag[0]);
    }
}
