package com.atomguard.velocity.data;

/**
 * Oyuncu tehdit skoru modeli.
 * Birden fazla güvenlik kontrolünün ağırlıklı birleşimi (0-100).
 * 100 = maksimum tehdit, 0 = güvenli.
 */
public class ThreatScore {

    private volatile int totalScore;
    private volatile int connectionRateScore;
    private volatile int handshakeScore;
    private volatile int brandScore;
    private volatile int joinPatternScore;
    private volatile int usernameScore;
    private volatile int geoScore;
    private volatile int protocolScore;

    public ThreatScore() {
        this.totalScore = 0;
    }

    /**
     * Ağırlıklı toplam skoru hesaplar.
     */
    public void calculate() {
        int raw = (int) (
            connectionRateScore * 0.20 +
            handshakeScore * 0.15 +
            brandScore * 0.15 +
            joinPatternScore * 0.20 +
            usernameScore * 0.10 +
            geoScore * 0.10 +
            protocolScore * 0.10
        );
        this.totalScore = Math.min(100, Math.max(0, raw));
    }

    public int getTotalScore() { return totalScore; }
    public int getConnectionRateScore() { return connectionRateScore; }
    public int getHandshakeScore() { return handshakeScore; }
    public int getBrandScore() { return brandScore; }
    public int getJoinPatternScore() { return joinPatternScore; }
    public int getUsernameScore() { return usernameScore; }
    public int getGeoScore() { return geoScore; }
    public int getProtocolScore() { return protocolScore; }

    public void setConnectionRateScore(int score) { this.connectionRateScore = Math.min(100, Math.max(0, score)); }
    public void setHandshakeScore(int score) { this.handshakeScore = Math.min(100, Math.max(0, score)); }
    public void setBrandScore(int score) { this.brandScore = Math.min(100, Math.max(0, score)); }
    public void setJoinPatternScore(int score) { this.joinPatternScore = Math.min(100, Math.max(0, score)); }
    public void setUsernameScore(int score) { this.usernameScore = Math.min(100, Math.max(0, score)); }
    public void setGeoScore(int score) { this.geoScore = Math.min(100, Math.max(0, score)); }
    public void setProtocolScore(int score) { this.protocolScore = Math.min(100, Math.max(0, score)); }

    public boolean isHighRisk() { return totalScore >= 75; }
    public boolean isMediumRisk() { return totalScore >= 40; }
    public boolean isLowRisk() { return totalScore < 40; }

    @Override
    public String toString() {
        return String.format("ThreatScore{total=%d, connRate=%d, handshake=%d, brand=%d, joinPattern=%d, username=%d, geo=%d, protocol=%d}",
            totalScore, connectionRateScore, handshakeScore, brandScore, joinPatternScore, usernameScore, geoScore, protocolScore);
    }
}
