package com.atomguard.velocity.module.antibot;

import com.atomguard.velocity.data.ThreatScore;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bileşik bot tespit motoru — ağırlıklı skorlama + doğrulanmış oyuncu bypass.
 *
 * <p>Kritik düzeltmeler (false positive önleme):
 * <ul>
 *   <li>Her {@link #analyze} çağrısında {@code score.resetForNewAnalysis()} çağrılır</li>
 *   <li>Doğrulanmış oyuncular ({@link #markVerified}) bot kontrolüne girmez</li>
 *   <li>{@link #isHighRisk}: hem skor eşiği hem {@code flagCount >= 2} şartı</li>
 *   <li>{@link #cleanup}: 10dk'dan eski ve sıfır skorlu girişler temizlenir</li>
 * </ul>
 */
public class BotDetectionEngine {

    private final ConnectionAnalyzer connectionAnalyzer;
    private final HandshakeValidator handshakeValidator;
    private final BrandAnalyzer brandAnalyzer;
    private final JoinPatternDetector joinPatternDetector;

    private final Map<String, ThreatScore> threatScores = new ConcurrentHashMap<>();

    /** Başarılı login yapan, doğrulanmış oyuncu IP'leri (LRU eviction) */
    private final Set<String> verifiedPlayers = ConcurrentHashMap.newKeySet(10000);
    private final Queue<String> verifiedPlayersOrder = new ConcurrentLinkedQueue<>();

    private final int highRiskThreshold;
    private final int mediumRiskThreshold;

    public BotDetectionEngine(ConnectionAnalyzer connectionAnalyzer,
                               HandshakeValidator handshakeValidator,
                               BrandAnalyzer brandAnalyzer,
                               JoinPatternDetector joinPatternDetector,
                               int highRiskThreshold, int mediumRiskThreshold) {
        this.connectionAnalyzer = connectionAnalyzer;
        this.handshakeValidator = handshakeValidator;
        this.brandAnalyzer = brandAnalyzer;
        this.joinPatternDetector = joinPatternDetector;
        this.highRiskThreshold = highRiskThreshold;
        this.mediumRiskThreshold = mediumRiskThreshold;
    }

    /**
     * IP'yi analiz et ve ThreatScore döndür.
     * Doğrulanmış oyuncular için sıfır skor döner.
     */
    public ThreatScore analyze(String ip, String username, String brand,
                                String hostname, int port, int protocol) {
        // Doğrulanmış oyuncular bot kontrolüne girmez
        if (verifiedPlayers.contains(ip)) {
            return new ThreatScore();
        }

        ThreatScore score = threatScores.computeIfAbsent(ip, k -> new ThreatScore());

        // Her analiz döngüsü başlamadan önce sıfırla — birikim sorununu önler
        score.resetForNewAnalysis();

        // Zaman bazlı decay uygula
        score.applyTimeDecay(60_000L, 10);

        // Bağlantı hızı analizi
        if (connectionAnalyzer.isSuspicious(ip)) {
            score.setConnectionRateScore((int) (connectionAnalyzer.getConnectionRate(ip) * 10));
        }

        // Handshake doğrulama
        if (hostname != null || port > 0 || protocol > 0) {
            HandshakeValidator.ValidationResult hvResult =
                    handshakeValidator.validate(hostname, port, protocol, username);
            if (!hvResult.valid()) score.setHandshakeScore(50);
        }

        // Brand analizi
        if (brand != null) {
            BrandAnalyzer.BrandCheckResult brandResult = brandAnalyzer.check(brand);
            score.setBrandScore(brandResult.scoreContribution());
        }

        // Katılma örüntüsü
        int joinScore = joinPatternDetector.getJoinScore(ip);
        if (joinScore > 0) score.setJoinPatternScore(joinScore);

        score.calculate();
        return score;
    }

    public void recordConnection(String ip) {
        // Doğrulanmış IP'lerde sayma yapma
        if (!verifiedPlayers.contains(ip)) {
            connectionAnalyzer.recordConnection(ip);
        }
    }

    public void recordJoin(String ip) {
        joinPatternDetector.recordJoin(ip);
    }

    public void recordQuit(String ip) {
        joinPatternDetector.recordQuit(ip);
    }

    /**
     * Yüksek risk mi? Doğrulanmış oyuncular asla yüksek risk değildir.
     * flagCount >= 2 şartı da aranır (tek kategoride yüksek skor yeterli değil).
     */
    public boolean isHighRisk(String ip) {
        if (verifiedPlayers.contains(ip)) return false;
        ThreatScore score = threatScores.get(ip);
        return score != null && score.getTotalScore() >= highRiskThreshold && score.getFlagCount() >= 2;
    }

    /**
     * Orta risk mi? Doğrulanmış oyuncular asla orta risk değildir.
     */
    public boolean isMediumRisk(String ip) {
        if (verifiedPlayers.contains(ip)) return false;
        ThreatScore score = threatScores.get(ip);
        return score != null && score.getTotalScore() >= mediumRiskThreshold && score.getFlagCount() >= 2;
    }

    public ThreatScore getScore(String ip) {
        return threatScores.getOrDefault(ip, new ThreatScore());
    }

    /**
     * IP'yi doğrulanmış oyuncu olarak işaretle (başarılı login sonrası).
     * LRU eviction: ekleme sırasındaki en eski IP çıkarılır.
     */
    public void markVerified(String ip) {
        if (verifiedPlayers.add(ip)) {
            verifiedPlayersOrder.offer(ip);
        }
        // Cache doluysa en eski IP'yi çıkar
        while (verifiedPlayers.size() > 10000) {
            String oldest = verifiedPlayersOrder.poll();
            if (oldest != null) verifiedPlayers.remove(oldest);
        }
        threatScores.remove(ip); // Eski skor temizle
    }

    /**
     * Doğrulama statüsünü iptal et.
     */
    public void revokeVerification(String ip) {
        verifiedPlayers.remove(ip);
    }

    /**
     * IP doğrulanmış mı?
     */
    public boolean isVerified(String ip) {
        return verifiedPlayers.contains(ip);
    }

    /**
     * 10dk'dan eski ve sıfır skorlu girişleri temizle.
     */
    public void cleanup() {
        connectionAnalyzer.cleanup();
        joinPatternDetector.cleanup();
        long cutoff = System.currentTimeMillis() - 600_000L; // 10 dakika
        threatScores.entrySet().removeIf(e -> {
            ThreatScore s = e.getValue();
            return s.getTotalScore() == 0 || s.getLastUpdateTime() < cutoff;
        });
    }
}
