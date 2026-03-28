package com.atomguard.velocity.module.verification;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Eş zamanlı doğrulama sayısını sınırlayan kuyruk.
 *
 * <p>Bot flood saldırılarında binlerce bağlantı aynı anda gelir. Bu kuyruk
 * aynı anda yalnızca {@code maxConcurrent} adet doğrulamayı işler; geri
 * kalanlar 5 saniye bekler ya da reddedilir.
 *
 * <p>Bu mekanizma tek başına büyük çaplı saldırıları etkisiz kılar: botlar
 * birbirlerini kuyruğa iter ve timeout olur.
 */
public class ConnectionQueue {

    private final Semaphore slots;
    private final AtomicInteger totalQueued = new AtomicInteger(0);
    private final int maxQueueSize;
    private final int perIPMax;
    private final ConcurrentHashMap<String, AtomicInteger> perIPCounts = new ConcurrentHashMap<>();

    public ConnectionQueue(int maxConcurrent, int maxQueueSize, int perIPMax) {
        this.slots = new Semaphore(maxConcurrent, true);
        this.maxQueueSize = maxQueueSize;
        this.perIPMax = perIPMax;
    }

    /**
     * Bağlantıyı kuyruğa al.
     *
     * @return {@code true} → slot alındı, doğrulamaya başlanabilir;
     *         {@code false} → kuyruk dolu veya per-IP limit aşıldı
     */
    public CompletableFuture<Boolean> enqueue(String ip) {
        // Per-IP limit
        AtomicInteger ipCount = perIPCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (ipCount.get() >= perIPMax) {
            return CompletableFuture.completedFuture(false);
        }

        // Genel kuyruk doldu mu?
        if (totalQueued.get() >= maxQueueSize) {
            return CompletableFuture.completedFuture(false);
        }

        totalQueued.incrementAndGet();
        ipCount.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 5 saniye bekle; slot alamazsa reddet
                boolean acquired = slots.tryAcquire(5, TimeUnit.SECONDS);
                if (!acquired) {
                    release(ip);
                    return false;
                }
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                release(ip);
                return false;
            }
        });
    }

    /**
     * Doğrulama tamamlandığında (başarılı veya başarısız) slot'u serbest bırak.
     */
    public void release(String ip) {
        slots.release();
        totalQueued.decrementAndGet();
        AtomicInteger count = perIPCounts.get(ip);
        if (count != null) {
            int remaining = count.decrementAndGet();
            if (remaining <= 0) perIPCounts.remove(ip);
        }
    }

    public int getQueuedCount() { return totalQueued.get(); }
    public int getAvailableSlots() { return slots.availablePermits(); }

    /** Periyodik cleanup: boş IP counter'larını kaldır. */
    public void cleanup() {
        perIPCounts.entrySet().removeIf(e -> e.getValue().get() <= 0);
    }
}
