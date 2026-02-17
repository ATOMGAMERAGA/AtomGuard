package com.atomguard.velocity.util;

import com.atomguard.velocity.AtomGuardVelocity;
import com.velocitypowered.api.scheduler.ScheduledTask;

import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe yardımcı metodlar.
 */
public final class ConcurrencyUtils {

    private ConcurrencyUtils() {}

    /**
     * Velocity async scheduler'ında Runnable çalıştırır.
     */
    public static void runAsync(AtomGuardVelocity plugin, Runnable task) {
        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, task)
            .schedule();
    }

    /**
     * Belirli bir gecikme sonrasında görev çalıştırır.
     */
    public static ScheduledTask runLater(AtomGuardVelocity plugin, Runnable task, long delayMs) {
        return plugin.getProxyServer().getScheduler()
            .buildTask(plugin, task)
            .delay(delayMs, TimeUnit.MILLISECONDS)
            .schedule();
    }

    /**
     * Kayan pencerede sayı eşiği aşılıp aşılmadığını kontrol eder.
     */
    public static boolean exceedsThreshold(Deque<Long> timestamps, long windowMs, int threshold) {
        long cutoff = System.currentTimeMillis() - windowMs;
        long count = timestamps.stream().filter(t -> t > cutoff).count();
        return count >= threshold;
    }

    /**
     * Kayan pencereye zaman damgası ekler, eski girişleri temizler.
     */
    public static void addToWindow(Deque<Long> timestamps, long windowMs) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;
        timestamps.addLast(now);
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }
    }

    /**
     * Maksimum boyut sınırlı thread-safe Map oluşturur (access-order LRU).
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> boundedMap(int maxSize) {
        return java.util.Collections.synchronizedMap(
            new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > maxSize;
                }
            }
        );
    }

    /**
     * CompletableFuture'ı hata toleranslı şekilde çalıştırır.
     */
    public static <T> CompletableFuture<T> safeAsync(java.util.concurrent.Callable<T> callable) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
