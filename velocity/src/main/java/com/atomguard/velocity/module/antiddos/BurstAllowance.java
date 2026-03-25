package com.atomguard.velocity.module.antiddos;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kısa süreli trafik patlamalarını yöneten burst tolerans sistemi.
 *
 * <p>Mantık:
 * <ul>
 *   <li>CPS {@code burstThreshold}'u aşarsa VE verified IP oranı ≥ {@code minVerifiedRatioPercent}% ise → burst modu aktif</li>
 *   <li>Burst modu süresince tüm rate limit'ler {@code burstMultiplier} ile çarpılır</li>
 *   <li>Burst süresi dolunca ({@code burstWindowSeconds}) normal mode'a dön</li>
 *   <li>Saatte {@code maxBurstsPerHour}'dan fazla burst tetiklenemez (abuse önleme)</li>
 * </ul>
 *
 * <p>Tasarım: sunucu restart, event başlangıcı gibi kısa süreli yoğunlukları
 * DDoS saldırısı olarak yanlış sınıflandırmayı önler.
 */
public class BurstAllowance {

    private final int  burstThreshold;
    private final int  burstWindowSeconds;
    private final int  burstMultiplier;
    private final int  maxBurstsPerHour;
    private final int  minVerifiedRatioPercent;

    private final AtomicBoolean inBurstMode    = new AtomicBoolean(false);
    private final AtomicLong    burstStartTime = new AtomicLong(0L);
    private final AtomicInteger burstsThisHour = new AtomicInteger(0);
    private final AtomicLong    hourWindowStart = new AtomicLong(System.currentTimeMillis());

    /**
     * @param burstThreshold          Bu CPS değerinde burst değerlendirmesi başlar
     * @param burstWindowSeconds      Burst tolerans süresi (saniye)
     * @param burstMultiplier         Burst sırasında rate limit çarpanı (örn. 3 = 3x)
     * @param maxBurstsPerHour        Saatte izin verilen maksimum burst sayısı
     * @param minVerifiedRatioPercent Burst aktive edilmesi için minimum verified IP oranı (%)
     */
    public BurstAllowance(int burstThreshold, int burstWindowSeconds,
                          int burstMultiplier, int maxBurstsPerHour,
                          int minVerifiedRatioPercent) {
        this.burstThreshold           = burstThreshold;
        this.burstWindowSeconds       = burstWindowSeconds;
        this.burstMultiplier          = burstMultiplier;
        this.maxBurstsPerHour         = maxBurstsPerHour;
        this.minVerifiedRatioPercent  = minVerifiedRatioPercent;
    }

    /**
     * Burst modu aktif mi? Süresi dolmuşsa otomatik devre dışı bırakır.
     */
    public boolean isBurstActive() {
        if (!inBurstMode.get()) return false;
        long elapsed = System.currentTimeMillis() - burstStartTime.get();
        if (elapsed > (long) burstWindowSeconds * 1000L) {
            inBurstMode.set(false);
            return false;
        }
        return true;
    }

    /**
     * Güncel etkin çarpanı döndürür (burst aktifse {@code burstMultiplier}, değilse 1).
     */
    public int getEffectiveMultiplier() {
        return isBurstActive() ? burstMultiplier : 1;
    }

    /**
     * Mevcut trafik bilgileriyle burst modunun tetiklenip tetiklenmeyeceğini değerlendirir.
     *
     * @param currentCps      Anlık bağlantı/saniye
     * @param verifiedRatioPct Verified IP oranı (0–100)
     */
    public void evaluateTraffic(int currentCps, int verifiedRatioPct) {
        if (isBurstActive()) return;

        // Saat penceresini sıfırla
        long now = System.currentTimeMillis();
        if (now - hourWindowStart.get() > 3_600_000L) {
            burstsThisHour.set(0);
            hourWindowStart.set(now);
        }

        if (currentCps >= burstThreshold
                && verifiedRatioPct >= minVerifiedRatioPercent
                && burstsThisHour.get() < maxBurstsPerHour) {
            if (inBurstMode.compareAndSet(false, true)) {
                burstStartTime.set(now);
                burstsThisHour.incrementAndGet();
            }
        }
    }

    /** Burst modunu manuel olarak sıfırla. */
    public void reset() {
        inBurstMode.set(false);
    }

    public int getBurstThreshold()     { return burstThreshold; }
    public int getBurstMultiplier()    { return burstMultiplier; }
    public int getBurstsThisHour()     { return burstsThisHour.get(); }
}
