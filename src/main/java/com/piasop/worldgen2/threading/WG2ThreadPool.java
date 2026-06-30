package com.piasop.worldgen2.threading;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * Dedicated thread pool for async macro and regional generation tasks.
 */
public final class WG2ThreadPool {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ForkJoinPool pool;

    private WG2ThreadPool() {
    }

    public static void start(int requestedSize) {
        if (pool != null) {
            return;
        }
        int size = requestedSize > 0 ? requestedSize : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        pool = new ForkJoinPool(size, ForkJoinPool.defaultForkJoinWorkerThreadFactory, (thread, throwable) ->
                LOGGER.error("WG2 worker thread {} failed", thread.getName(), throwable), true);
        LOGGER.info("WorldGen 2.0 thread pool started with {} workers", size);
    }

    public static <T> Future<T> submit(Callable<T> task) {
        ensureStarted();
        return pool.submit(task);
    }

    public static Future<?> submit(Runnable task) {
        ensureStarted();
        return pool.submit(task);
    }

    public static void shutdown() {
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
    }

    public static void resetForTesting() {
        shutdown();
    }

    private static void ensureStarted() {
        if (pool == null) {
            start(0);
        }
    }
}
