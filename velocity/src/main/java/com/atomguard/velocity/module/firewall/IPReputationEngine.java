package com.atomguard.velocity.module.firewall;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP itibar puanlama motoru — bağlamsal skorlama + doğrulanmış IP desteği.
 *
 * <p>Düzeltmeler (false positive önleme):
 * <ul>
 *   <li>Bağlamsal çarpanlar: bot tespiti %70, şüpheli IP %50, VPN %100, exploit %150, flood %120, crash %200</li>
 *   <li>Doğrulanmış IP'ler %50 daha az ceza alır</li>
 *   <li>{@link #rewardSuccessfulLogin} başarılı girişte -15 puan + doğrulanmış işaret</li>
 *   <li>{@link #shouldAutoBan} grace period desteği — ilk 3 ihlalde ban yok</li>
 *   <li>Auto-ban eşiği minimum 150 (önceden 100)</li>
 * </ul>
 */
public class IPReputationEngine {

    private final ConcurrentHashMap<String, AtomicInteger> scores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> violationCounts = new ConcurrentHashMap<>();
    private final Set<String> verifiedIPs = ConcurrentHashMap.newKeySet(10000);
    private final int autoBanThreshold;

    /** Grace period: ilk 3 ihlalde otomatik ban yapma */
    private static final int GRACE_VIOLATIONS = 3;
    /** Başarılı login bonus (negatif puan) */
    private static final int SUCCESSFUL_LOGIN_BONUS = 15;

    public IPReputationEngine(int autoBanThreshold) {
        // Minimum 150 — çok düşük eşik false positive ban oluşturur
        this.autoBanThreshold = Math.max(150, autoBanThreshold);
    }

    /**
     * Geriye dönük uyumluluk metodu — sabit puan ekler.
     * Yeni kodlar {@link #addContextualScore} kullansın.
     */
    public void addScore(String ip, int points) {
        scores.computeIfAbsent(ip, k -> new AtomicInteger(0)).addAndGet(points);
        violationCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Bağlamsal skor ekle — ihlal türüne göre farklı çarpanlar uygular.
     *
     * <p>Çarpanlar:
     * <ul>
     *   <li>{@code bot-tespiti} → 0.7x (false positive riski yüksek)</li>
     *   <li>{@code supheli-ip} → 0.5x</li>
     *   <li>{@code vpn-tespit} → 1.0x (konsensüs bazlı, güvenilir)</li>
     *   <li>{@code exploit} → 1.5x</li>
     *   <li>{@code flood} → 1.2x</li>
     *   <li>{@code crash-girisimi} → 2.0x</li>
     *   <li>diğer → 1.0x</li>
     * </ul>
     *
     * @param ip            IP adresi
     * @param basePoints    temel puan
     * @param violationType ihlal türü (Türkçe key)
     */
    public void addContextualScore(String ip, int basePoints, String violationType) {
        double multiplier = getMultiplier(violationType);
        int actualPoints = (int) Math.ceil(basePoints * multiplier);

        // Doğrulanmış IP'ler %50 daha az ceza alır
        if (verifiedIPs.contains(ip)) {
            actualPoints = Math.max(1, actualPoints / 2);
        }

        scores.computeIfAbsent(ip, k -> new AtomicInteger(0)).addAndGet(actualPoints);
        violationCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private double getMultiplier(String violationType) {
        if (violationType == null) return 1.0;
        return switch (violationType.toLowerCase()) {
            case "bot-tespiti" -> 0.7;
            case "supheli-ip" -> 0.5;
            case "vpn-tespit" -> 1.0;
            case "exploit" -> 1.5;
            case "flood" -> 1.2;
            case "crash-girisimi" -> 2.0;
            default -> 1.0;
        };
    }

    public void reduceScore(String ip, int points) {
        AtomicInteger score = scores.get(ip);
        if (score != null) {
            int newVal = score.addAndGet(-points);
            if (newVal <= 0) scores.remove(ip);
        }
    }

    /**
     * Başarılı login ödülü: -15 puan + doğrulanmış işaret.
     */
    public void rewardSuccessfulLogin(String ip) {
        reduceScore(ip, SUCCESSFUL_LOGIN_BONUS);
        markVerified(ip);
    }

    public void markVerified(String ip) {
        if (verifiedIPs.size() >= 10000) {
            verifiedIPs.remove(verifiedIPs.iterator().next());
        }
        verifiedIPs.add(ip);
    }

    public boolean isVerified(String ip) {
        return verifiedIPs.contains(ip);
    }

    public int getScore(String ip) {
        AtomicInteger score = scores.get(ip);
        return score != null ? score.get() : 0;
    }

    /**
     * Otomatik ban gerekiyor mu?
     * Grace period (ilk 3 ihlal) ve eşik kontrolü yapar.
     */
    public boolean shouldAutoBan(String ip) {
        // Grace period: ilk GRACE_VIOLATIONS ihlalde ban yok
        AtomicInteger violations = violationCounts.get(ip);
        if (violations == null || violations.get() <= GRACE_VIOLATIONS) return false;

        return getScore(ip) >= autoBanThreshold;
    }

    public void reset(String ip) {
        scores.remove(ip);
        violationCounts.remove(ip);
    }

    public Map<String, Integer> getTopScores(int limit) {
        return scores.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(limit)
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get()
                ));
    }

    public void decayAll(int decayAmount) {
        scores.entrySet().removeIf(e -> {
            int newVal = e.getValue().addAndGet(-decayAmount);
            return newVal <= 0;
        });
    }
}
