package com.atomguard.velocity.module.antibot;

import com.atomguard.velocity.data.ThreatScore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bileşik bot tespit motoru - ağırlıklı skorlama sistemi.
 */
public class BotDetectionEngine {

    private final ConnectionAnalyzer connectionAnalyzer;
    private final HandshakeValidator handshakeValidator;
    private final BrandAnalyzer brandAnalyzer;
    private final JoinPatternDetector joinPatternDetector;

    private final Map<String, ThreatScore> threatScores = new ConcurrentHashMap<>();
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

    public ThreatScore analyze(String ip, String username, String brand,
                                String hostname, int port, int protocol) {
        ThreatScore score = threatScores.computeIfAbsent(ip, k -> new ThreatScore());

        // Bağlantı hızı analizi
        if (connectionAnalyzer.isSuspicious(ip)) {
            score.setConnectionRateScore((int)(connectionAnalyzer.getConnectionRate(ip) * 10));
        }

        // Handshake doğrulama
        HandshakeValidator.ValidationResult hvResult = handshakeValidator.validate(hostname, port, protocol, username);
        if (!hvResult.valid()) score.setHandshakeScore(50);

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
        connectionAnalyzer.recordConnection(ip);
    }

    public void recordJoin(String ip) {
        joinPatternDetector.recordJoin(ip);
    }

    public void recordQuit(String ip) {
        joinPatternDetector.recordQuit(ip);
    }

    public boolean isHighRisk(String ip) {
        ThreatScore score = threatScores.get(ip);
        return score != null && score.getTotalScore() >= highRiskThreshold;
    }

    public boolean isMediumRisk(String ip) {
        ThreatScore score = threatScores.get(ip);
        return score != null && score.getTotalScore() >= mediumRiskThreshold;
    }

    public ThreatScore getScore(String ip) {
        return threatScores.getOrDefault(ip, new ThreatScore());
    }

    public void cleanup() {
        connectionAnalyzer.cleanup();
        joinPatternDetector.cleanup();
        threatScores.entrySet().removeIf(e -> e.getValue().getTotalScore() == 0);
    }
}
