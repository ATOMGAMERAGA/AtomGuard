package com.atomguard.velocity.module.firewall;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    private final com.atomguard.velocity.AtomGuardVelocity plugin;
    private final ConcurrentHashMap<String, AtomicInteger> scores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> violationCounts = new ConcurrentHashMap<>();
    private final Set<String> verifiedIPs = ConcurrentHashMap.newKeySet(10000);
    private final Queue<String> verifiedIPsOrder = new ConcurrentLinkedQueue<>();
    private int autoBanThreshold;

    /** Grace period: ilk 3 ihlalde otomatik ban yapma */
    private static final int GRACE_VIOLATIONS = 3;
    /** Başarılı login bonus (negatif puan) */
    private static final int SUCCESSFUL_LOGIN_BONUS = 15;

    public IPReputationEngine(com.atomguard.velocity.AtomGuardVelocity plugin, int autoBanThreshold) {
        this.plugin = plugin;
        // Minimum 150 — çok düşük eşik false positive ban oluşturur
        this.autoBanThreshold = Math.max(150, autoBanThreshold);
    }

    public void setThreshold(int threshold) {
        this.autoBanThreshold = Math.max(150, threshold);
    }

    /**
     * Geriye dönük uyumluluk metodu — bağlamsal skorlamaya yönlendirir.
     *
     * @deprecated Yeni kodlar {@link #addContextualScore} kullansın.
     */
    @Deprecated
    public void addScore(String ip, int points) {
        addContextualScore(ip, points, "bilinmeyen");
    }

    /**
     * Bağlamsal skor ekle — ihlal türüne göre farklı çarpanlar uygular.
     */
    public void addContextualScore(String ip, int basePoints, String violationType) {
        double multiplier = getMultiplier(violationType);
        int actualPoints = (int) Math.ceil(basePoints * multiplier);

        // Doğrulanmış IP'ler %50 daha az ceza alır
        if (verifiedIPs.contains(ip)) {
            actualPoints = Math.max(1, actualPoints / 2);
        }

        int oldScore = getScore(ip);
        int newScore = scores.computeIfAbsent(ip, k -> new AtomicInteger(0)).addAndGet(actualPoints);
        violationCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        
        // 1. Veritabanına kaydet (Live Sync)
        if (plugin.getStorageProvider() != null) {
            plugin.getStorageProvider().saveIPReputation(ip, newScore);
        }

        // 2. Velocity Event
        if (plugin.getEventBus() != null) {
            plugin.getEventBus().fireThreatScoreChanged(ip, oldScore, newScore, violationType);
        }
    }

    public void loadScores(Map<String, Integer> loadedScores) {
        if (loadedScores != null) {
            loadedScores.forEach((ip, score) -> scores.put(ip, new AtomicInteger(score)));
        }
    }

    public Map<String, Integer> getAllScores() {
        Map<String, Integer> currentScores = new HashMap<>();
        scores.forEach((k, v) -> currentScores.put(k, v.get()));
        return currentScores;
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
            int oldScore = score.get();
            int newScore = score.addAndGet(-points);
            if (newScore <= 0) {
                scores.remove(ip);
                newScore = 0;
            }
            
            // Veritabanına kaydet
            if (plugin.getStorageProvider() != null) {
                plugin.getStorageProvider().saveIPReputation(ip, newScore);
            }
        }
    }

    /**
     * Başarılı login ödülü: -15 puan + doğrulanmış işaret + violationCount sıfırlama.
     */
    public void rewardSuccessfulLogin(String ip) {
        reduceScore(ip, SUCCESSFUL_LOGIN_BONUS);
        // Başarılı giriş → grace period yeniden başlasın
        violationCounts.remove(ip);
        markVerified(ip);
    }

    public void markVerified(String ip) {
        if (verifiedIPs.add(ip)) {
            verifiedIPsOrder.offer(ip);
        }
        // Cache doluysa en eski IP'yi çıkar (LRU eviction)
        while (verifiedIPs.size() > 10000) {
            String oldest = verifiedIPsOrder.poll();
            if (oldest != null) verifiedIPs.remove(oldest);
        }
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
            int oldVal = e.getValue().get();
            int newVal = e.getValue().addAndGet(-decayAmount);
            
            if (newVal <= 0) {
                // Skor sıfırlandı -> veritabanından tamamen sil
                if (plugin.getStorageProvider() != null) {
                    plugin.getStorageProvider().saveIPReputation(e.getKey(), 0); 
                }
                violationCounts.remove(e.getKey());
                return true;
            } else {
                // Skor azaldı -> veritabanını güncelle
                if (plugin.getStorageProvider() != null) {
                    plugin.getStorageProvider().saveIPReputation(e.getKey(), newVal);
                }
            }
            return false;
        });
    }
}
