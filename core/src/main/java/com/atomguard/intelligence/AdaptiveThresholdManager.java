package com.atomguard.intelligence;

import com.atomguard.AtomGuard;
import com.atomguard.intelligence.detector.EWMADetector;

import java.time.LocalDateTime;

/**
 * Gun/saat bazli adaptif esik yoneticisi.
 * 168 saatlik slot (7 gun x 24 saat) uzerinde bagimsiz EWMADetector'lar
 * ile her zaman dilimi icin ozel baseline ogrenir.
 * Ogrenme suresi tamamlanmadan anomali raporu uretmez.
 *
 * @author AtomGuard Team
 * @version 1.3.0
 */
public class AdaptiveThresholdManager {

    private static final int HOUR_SLOTS = 168; // 7 gun x 24 saat

    private final EWMADetector[] hourlyDetectors = new EWMADetector[HOUR_SLOTS];
    private final long[] sampleCounts = new long[HOUR_SLOTS];

    private final boolean separateDayNight;
    private final boolean separateWeekdayWeekend;
    private final int minLearningWeeks;

    private volatile boolean learningComplete = false;

    public AdaptiveThresholdManager(AtomGuard plugin) {
        this.separateDayNight = plugin.getConfig()
                .getBoolean("threat-intelligence.adaptive-threshold.day-night-separation", true);
        this.separateWeekdayWeekend = plugin.getConfig()
                .getBoolean("threat-intelligence.adaptive-threshold.weekday-weekend-separation", true);
        this.minLearningWeeks = Math.max(1, plugin.getConfig()
                .getInt("threat-intelligence.adaptive-threshold.min-learning-weeks", 2));

        double alpha = plugin.getConfig()
                .getDouble("threat-intelligence.ema-alpha", 0.1);

        for (int i = 0; i < HOUR_SLOTS; i++) {
            hourlyDetectors[i] = new EWMADetector(alpha, 2.5, 10);
        }
    }

    /**
     * Verilen degeri analiz eder ve anomali olup olmadigini dondurur.
     * Ogrenme suresi tamamlanmamissa her zaman false doner.
     *
     * @param value Kontrol edilecek metrik degeri
     * @return Anomali tespit edildiyse true
     */
    public boolean isAnomaly(double value) {
        int slot = getCurrentSlot();
        sampleCounts[slot]++;
        checkLearningComplete();

        if (!learningComplete) return false;

        return hourlyDetectors[slot].isAnomaly(value);
    }

    /**
     * Degeri kaydeder (ogrenme icin). Anomali kontrolu yapmadan
     * sadece ic durumu gunceller.
     *
     * @param value Kaydedilecek metrik degeri
     */
    public void recordSample(double value) {
        int slot = getCurrentSlot();
        hourlyDetectors[slot].isAnomaly(value); // ic durumu gunceller
        sampleCounts[slot]++;
        checkLearningComplete();
    }

    /**
     * Haftanin gunu (0=Pazartesi) ve saat (0-23) bazinda slot hesaplar.
     *
     * @return 0-167 arasi slot indeksi
     */
    private int getCurrentSlot() {
        LocalDateTime now = LocalDateTime.now();
        int dayOfWeek = now.getDayOfWeek().getValue() - 1; // 0=Pazartesi, 6=Pazar
        int hour = now.getHour();
        return dayOfWeek * 24 + hour;
    }

    private void checkLearningComplete() {
        if (learningComplete) return;

        long minSamples = (long) minLearningWeeks;
        for (long count : sampleCounts) {
            if (count < minSamples) return;
        }
        learningComplete = true;
    }

    // ─── Getters ───

    public boolean isLearningComplete() { return learningComplete; }

    public EWMADetector getDetectorForCurrentSlot() {
        return hourlyDetectors[getCurrentSlot()];
    }

    public boolean isSeparateDayNight() { return separateDayNight; }
    public boolean isSeparateWeekdayWeekend() { return separateWeekdayWeekend; }
}
