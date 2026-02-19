package com.atomguard.velocity.data;

/**
 * Oyuncu tehdit skoru modeli.
 * Birden fazla güvenlik kontrolünün ağırlıklı birleşimi (0-100).
 * 100 = maksimum tehdit, 0 = güvenli.
 *
 * <p>Kritik düzeltmeler (false positive önleme):
 * <ul>
 *   <li>{@link #resetForNewAnalysis()} — her analiz döngüsünde skorları sıfırlar</li>
 *   <li>{@link #calculate()} — tek kategoride yüksek skor artık engelleme yapmaz (flagCount &lt;= 1 ise %60 indirim)</li>
 *   <li>{@link #isHighRisk()} — hem {@code totalScore >= 75} hem {@code flagCount >= 2} şartı aranır</li>
 *   <li>{@link #applyTimeDecay(long, int)} — zaman bazlı skor azalma</li>
 * </ul>
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

    /** Kaç farklı kategoride pozitif skor var */
    private volatile int flagCount;
    /** Bu nesne kaç kez analiz edildi */
    private volatile int analysisCount;
    /** Son güncelleme zaman damgası (ms) */
    private volatile long lastUpdateTime;

    public ThreatScore() {
        this.totalScore = 0;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Her yeni analiz döngüsü başlamadan önce tüm alt skorları sıfırlar.
     * Bu olmadan skorlar her analyze() çağrısında birikir ve false positive oluşturur.
     */
    public void resetForNewAnalysis() {
        connectionRateScore = 0;
        handshakeScore = 0;
        brandScore = 0;
        joinPatternScore = 0;
        usernameScore = 0;
        geoScore = 0;
        protocolScore = 0;
        flagCount = 0;
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Ağırlıklı toplam skoru hesaplar.
     *
     * <p>Tek kategoride yüksek skor artık kesin engelleme yaratmaz:
     * eğer {@code flagCount <= 1} ise ham skor %60'a düşürülür.
     */
    public void calculate() {
        double raw =
            connectionRateScore * 0.20 +
            handshakeScore * 0.15 +
            brandScore * 0.15 +
            joinPatternScore * 0.20 +
            usernameScore * 0.10 +
            geoScore * 0.10 +
            protocolScore * 0.10;

        // flagCount hesapla: kaç alt skor > 0?
        int flags = 0;
        if (connectionRateScore > 0) flags++;
        if (handshakeScore > 0) flags++;
        if (brandScore > 0) flags++;
        if (joinPatternScore > 0) flags++;
        if (usernameScore > 0) flags++;
        if (geoScore > 0) flags++;
        if (protocolScore > 0) flags++;
        this.flagCount = flags;

        // Tek kategoride yüksek skor → false positive riski → %60'a düşür
        if (flagCount <= 1) {
            raw *= 0.60;
        }

        this.totalScore = Math.min(100, Math.max(0, (int) raw));
        this.analysisCount++;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Zaman bazlı skor azalma uygular.
     *
     * @param intervalMs  son güncellemeden bu yana geçen süre (ms)
     * @param decayAmount azaltılacak puan miktarı
     */
    public void applyTimeDecay(long intervalMs, int decayAmount) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastUpdateTime;
        if (elapsed >= intervalMs && totalScore > 0) {
            totalScore = Math.max(0, totalScore - decayAmount);
            lastUpdateTime = now;
        }
    }

    public int getTotalScore() { return totalScore; }
    public int getConnectionRateScore() { return connectionRateScore; }
    public int getHandshakeScore() { return handshakeScore; }
    public int getBrandScore() { return brandScore; }
    public int getJoinPatternScore() { return joinPatternScore; }
    public int getUsernameScore() { return usernameScore; }
    public int getGeoScore() { return geoScore; }
    public int getProtocolScore() { return protocolScore; }
    public int getFlagCount() { return flagCount; }
    public int getAnalysisCount() { return analysisCount; }
    public long getLastUpdateTime() { return lastUpdateTime; }

    public void setConnectionRateScore(int score) { this.connectionRateScore = Math.min(100, Math.max(0, score)); }
    public void setHandshakeScore(int score) { this.handshakeScore = Math.min(100, Math.max(0, score)); }
    public void setBrandScore(int score) { this.brandScore = Math.min(100, Math.max(0, score)); }
    public void setJoinPatternScore(int score) { this.joinPatternScore = Math.min(100, Math.max(0, score)); }
    public void setUsernameScore(int score) { this.usernameScore = Math.min(100, Math.max(0, score)); }
    public void setGeoScore(int score) { this.geoScore = Math.min(100, Math.max(0, score)); }
    public void setProtocolScore(int score) { this.protocolScore = Math.min(100, Math.max(0, score)); }

    /**
     * Yüksek risk: hem skor >= 75 hem en az 2 farklı kategoride şüpheli davranış.
     */
    public boolean isHighRisk() { return totalScore >= 75 && flagCount >= 2; }

    /**
     * Orta risk: hem skor >= 45 hem en az 2 farklı kategoride şüpheli davranış.
     */
    public boolean isMediumRisk() { return totalScore >= 45 && flagCount >= 2; }

    public boolean isLowRisk() { return !isHighRisk() && !isMediumRisk(); }

    @Override
    public String toString() {
        return String.format(
            "ThreatScore{total=%d, flags=%d, connRate=%d, handshake=%d, brand=%d, joinPattern=%d, username=%d, geo=%d, protocol=%d}",
            totalScore, flagCount, connectionRateScore, handshakeScore, brandScore,
            joinPatternScore, usernameScore, geoScore, protocolScore);
    }
}
