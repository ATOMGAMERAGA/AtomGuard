package com.atomguard.trust;

/**
 * Durum-bağımsız güven puanı hesaplama motoru.
 *
 * Formül:
 * TOPLAM = BasePuan
 *   + min(15, uniqueLoginDays * 0.5)
 *   + min(15, totalPlaytimeMinutes / 60.0 * 0.5)
 *   + min(10, consecutiveCleanSessions * 2.0)
 *   + hesap_yasi_bonus (0, +5 veya +10)
 *   + (24h temizse +5)
 *   + min(10, daysSinceLastViolation / 7.0 * 2.0)
 *   - min(30, recentViolations * 5.0)
 *   - min(20, totalViolations * 0.5)
 *   - min(15, kickCount * 3.0)
 *   - min(10, suspiciousPacketCount / 100.0)
 * clamp(0, 100)
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class TrustScoreCalculator {

    private TrustScoreCalculator() {}

    /**
     * Profil için güven puanını hesaplar.
     *
     * @param profile Oyuncu profili
     * @param basePuan Başlangıç puanı (config'den)
     * @return 0-100 arası güven puanı
     */
    public static double calculate(TrustProfile profile, int basePuan) {
        if (profile == null) return basePuan;

        double score = basePuan;
        long now = System.currentTimeMillis();

        // ─── Pozitif Faktörler ───

        // Gün bazlı sadakat (max +15)
        score += Math.min(15.0, profile.getUniqueLoginDays() * 0.5);

        // Oynama süresi (max +15)
        score += Math.min(15.0, profile.getTotalPlaytimeMinutes() / 60.0 * 0.5);

        // Temiz oturumlar (max +10)
        score += Math.min(10.0, profile.getConsecutiveCleanSessions() * 2.0);

        // Hesap yaşı bonusu
        if (profile.getFirstJoinTimestamp() > 0) {
            long ageDays = (now - profile.getFirstJoinTimestamp()) / (1000L * 60 * 60 * 24);
            if (ageDays >= 30) score += 10.0;
            else if (ageDays >= 7) score += 5.0;
        }

        // Son 24 saat temizse bonus
        long hoursSinceViolation;
        if (profile.getLastViolationTimestamp() > 0) {
            hoursSinceViolation = (now - profile.getLastViolationTimestamp()) / (1000L * 60 * 60);
        } else {
            hoursSinceViolation = Long.MAX_VALUE / 2;
        }

        if (hoursSinceViolation >= 24) score += 5.0;

        // İhlalsiz süre bonusu (max +10)
        double daysSinceViolation = hoursSinceViolation / 24.0;
        score += Math.min(10.0, (daysSinceViolation / 7.0) * 2.0);

        // ─── Negatif Faktörler ───

        // Son 24 saat ihlalleri (max -30)
        score -= Math.min(30.0, profile.getRecentViolations() * 5.0);

        // Toplam ihlaller (max -20)
        score -= Math.min(20.0, profile.getTotalViolations() * 0.5);

        // Kick cezası (max -15)
        score -= Math.min(15.0, profile.getKickCount() * 3.0);

        // Şüpheli paketler (max -10)
        score -= Math.min(10.0, profile.getSuspiciousPacketCount() / 100.0);

        // Clamp 0-100
        return Math.max(0.0, Math.min(100.0, score));
    }
}
