package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.module.firewall.FirewallModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DDoS modülüne özel IP itibar takip sistemi.
 * <p>
 * FirewallModule'ün IPReputationEngine'inden bağımsız olarak çalışır;
 * ancak otomatik ban kararlarını FirewallModule üzerinden uygular.
 * <p>
 * Skor aralığı: 0 – 100 (başlangıç: 50)
 * <ul>
 *   <li>Başarılı bağlantı       → +5</li>
 *   <li>Rate limit ihlali       → -10</li>
 *   <li>Geçersiz handshake     → -15</li>
 *   <li>Slowloris tespiti       → -25</li>
 *   <li>Saldırı sırasında bağ. → -20</li>
 *   <li>Zaman bazlı decay       → saatte +decayPerHour puan</li>
 * </ul>
 * <p>
 * Otomatik ban:
 * <ul>
 *   <li>Skor &lt; autoBanThreshold1h  → 1 saat tempban</li>
 *   <li>Skor &lt; autoBanThreshold24h → 24 saat tempban</li>
 * </ul>
 * Verified oyuncuların skoru asla {@code verifiedMinScore}'un altına düşmez.
 */
public class IPReputationTracker {

    // ────────────────────────────────────────────────────────
    // Konfigürasyon
    // ────────────────────────────────────────────────────────

    private final int  startingScore;
    private final int  successBonus;
    private final int  rateLimitPenalty;
    private final int  invalidHandshakePenalty;
    private final int  slowlorisPenalty;
    private final int  attackConnectionPenalty;
    private final int  autoBanThreshold1h;
    private final int  autoBanThreshold24h;
    private final int  decayPerHour;
    private final int  verifiedMinScore;

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    /** IP → itibar skoru */
    private final ConcurrentHashMap<String, AtomicInteger> scores = new ConcurrentHashMap<>();

    /** IP → son decay zamanı (ms) */
    private final ConcurrentHashMap<String, AtomicLong> lastDecay = new ConcurrentHashMap<>();

    /** Verified IP seti (otomatik ban için minimum skor garantisi) */
    private final Cache<String, Boolean> verifiedIPs = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(50_000)
            .build();

    /** Zaten 1 saatlik ban yemiş IP'ler (tekrar ban önleme) */
    private final Cache<String, Boolean> banned1h = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    /** Zaten 24 saatlik ban yemiş IP'ler */
    private final Cache<String, Boolean> banned24h = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(5_000)
            .build();

    private final AtomicLong autoBansApplied = new AtomicLong(0);

    /** FirewallModule referansı (nullable — modül yüklü olmayabilir) */
    private FirewallModule firewallModule;

    public IPReputationTracker(int startingScore, int successBonus,
                                int rateLimitPenalty, int invalidHandshakePenalty,
                                int slowlorisPenalty, int attackConnectionPenalty,
                                int autoBanThreshold1h, int autoBanThreshold24h,
                                int decayPerHour, int verifiedMinScore) {
        this.startingScore            = startingScore;
        this.successBonus             = successBonus;
        this.rateLimitPenalty         = rateLimitPenalty;
        this.invalidHandshakePenalty  = invalidHandshakePenalty;
        this.slowlorisPenalty         = slowlorisPenalty;
        this.attackConnectionPenalty  = attackConnectionPenalty;
        this.autoBanThreshold1h       = autoBanThreshold1h;
        this.autoBanThreshold24h      = autoBanThreshold24h;
        this.decayPerHour             = decayPerHour;
        this.verifiedMinScore         = verifiedMinScore;
    }

    public void setFirewallModule(FirewallModule firewallModule) {
        this.firewallModule = firewallModule;
    }

    // ────────────────────────────────────────────────────────
    // Puan güncelleme
    // ────────────────────────────────────────────────────────

    public void recordSuccess(String ip) {
        adjustScore(ip, successBonus);
    }

    public void recordRateLimitViolation(String ip) {
        adjustScore(ip, -rateLimitPenalty);
        checkAutoBan(ip);
    }

    public void recordInvalidHandshake(String ip) {
        adjustScore(ip, -invalidHandshakePenalty);
        checkAutoBan(ip);
    }

    public void recordSlowloris(String ip) {
        adjustScore(ip, -slowlorisPenalty);
        checkAutoBan(ip);
    }

    public void recordAttackConnection(String ip) {
        adjustScore(ip, -attackConnectionPenalty);
        checkAutoBan(ip);
    }

    public void markVerified(String ip) {
        verifiedIPs.put(ip, Boolean.TRUE);
        // Verified IP'ye minimum skor garantisi
        AtomicInteger score = scores.get(ip);
        if (score != null && score.get() < verifiedMinScore) {
            score.set(verifiedMinScore);
        }
    }

    private void adjustScore(String ip, int delta) {
        applyDecay(ip);
        AtomicInteger score = scores.computeIfAbsent(ip, k -> new AtomicInteger(startingScore));

        boolean isVerified = verifiedIPs.getIfPresent(ip) != null;
        score.updateAndGet(current -> {
            int next = current + delta;
            if (isVerified) next = Math.max(next, verifiedMinScore);
            return Math.max(0, Math.min(100, next));
        });
    }

    /**
     * Zaman bazlı decay uygula (saatte +decayPerHour puan iyileşme).
     */
    private void applyDecay(String ip) {
        long now = System.currentTimeMillis();
        AtomicLong last = lastDecay.computeIfAbsent(ip, k -> new AtomicLong(now));
        long elapsed = now - last.get();

        if (elapsed >= 3_600_000L) {
            int hoursElapsed = (int) (elapsed / 3_600_000L);
            int bonus        = hoursElapsed * decayPerHour;
            last.set(now);

            AtomicInteger score = scores.get(ip);
            if (score != null) {
                score.updateAndGet(v -> Math.min(100, v + bonus));
            }
        }
    }

    // ────────────────────────────────────────────────────────
    // Otomatik ban
    // ────────────────────────────────────────────────────────

    private void checkAutoBan(String ip) {
        int score = getScore(ip);

        if (score < autoBanThreshold24h && banned24h.getIfPresent(ip) == null) {
            banned24h.put(ip, Boolean.TRUE);
            autoBansApplied.incrementAndGet();
            applyBan(ip, 24 * 3_600_000L, "DDoS itibar skoru kritik (" + score + ")");
            return;
        }

        if (score < autoBanThreshold1h && banned1h.getIfPresent(ip) == null) {
            banned1h.put(ip, Boolean.TRUE);
            autoBansApplied.incrementAndGet();
            applyBan(ip, 3_600_000L, "DDoS itibar skoru düşük (" + score + ")");
        }
    }

    private void applyBan(String ip, long durationMs, String reason) {
        if (firewallModule != null) {
            firewallModule.banIP(ip, durationMs, reason);
        }
    }

    // ────────────────────────────────────────────────────────
    // Periyodik bakım
    // ────────────────────────────────────────────────────────

    /**
     * Tüm IP'lere decay uygula ve boş kayıtları temizle.
     * 1 saatte bir çağrılır.
     */
    public void periodicMaintenance() {
        long now = System.currentTimeMillis();
        scores.forEach((ip, score) -> applyDecay(ip));
        // Tam skor (100) olan temiz IP'leri temizle — bellek tasarrufu
        scores.entrySet().removeIf(e -> e.getValue().get() >= 100);
        lastDecay.entrySet().removeIf(e -> !scores.containsKey(e.getKey()));
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public int  getScore(String ip) {
        applyDecay(ip);
        AtomicInteger s = scores.get(ip);
        return s != null ? s.get() : startingScore;
    }

    public boolean isLowReputation(String ip) {
        return getScore(ip) < autoBanThreshold1h;
    }

    public long getAutoBansApplied() { return autoBansApplied.get(); }
    public int  getTrackedIPCount()  { return scores.size(); }
}
