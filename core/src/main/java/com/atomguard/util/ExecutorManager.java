package com.atomguard.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** IO, hesaplama ve zamanlama iş parçacığı havuzlarını yöneten merkezi executor yöneticisi. @since 2.0.0 */
public final class ExecutorManager {
    private final ExecutorService ioExecutor;
    private final ExecutorService computeExecutor;
    private final ScheduledExecutorService scheduler;

    public ExecutorManager() {
        int cores = Runtime.getRuntime().availableProcessors();
        this.ioExecutor = Executors.newFixedThreadPool(
            Math.max(2, cores / 2),
            daemonThreadFactory("AtomGuard-IO"));
        this.computeExecutor = Executors.newFixedThreadPool(
            Math.max(2, cores / 2),
            daemonThreadFactory("AtomGuard-Compute"));
        this.scheduler = Executors.newScheduledThreadPool(
            2, daemonThreadFactory("AtomGuard-Scheduler"));
    }

    public ExecutorService getIoExecutor() { return ioExecutor; }
    public ExecutorService getComputeExecutor() { return computeExecutor; }
    public ScheduledExecutorService getScheduler() { return scheduler; }

    public void shutdown() {
        ioExecutor.shutdown();
        computeExecutor.shutdown();
        scheduler.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
            computeExecutor.awaitTermination(5, TimeUnit.SECONDS);
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
    }
}
