package com.atomguard.intelligence;

/**
 * Saatlik trafik profili — öğrenilmiş baseline verilerini tutar.
 * Exponential Moving Average (EMA) ile güncellenir.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class TrafficProfile {

    private double meanConnections;
    private double stddevConnections;
    private double meanUniqueIps;
    private double stddevUniqueIps;
    private double meanPacketRate;
    private double stddevPacketRate;
    private double meanJoinLeaveRatio;
    private double stddevJoinLeaveRatio;
    private int sampleCount;
    private long lastUpdated;

    public TrafficProfile() {}

    /**
     * Profili minimum örnekle güvenilir sayılıp sayılmadığını kontrol eder.
     */
    public boolean isReliable(int minSamples) {
        return sampleCount >= Math.max(1, minSamples);
    }

    /**
     * Exponential Moving Average ile profili günceller.
     * İlk güncelleme: doğrudan ata. Sonraki: EMA ile karıştır.
     *
     * @param alpha EMA katsayısı (0.0-1.0), küçük değer = yavaş öğrenme
     */
    public void update(double connections, double uniqueIps, double packetRate,
                       double joinLeaveRatio, double alpha) {
        if (sampleCount == 0) {
            this.meanConnections = connections;
            this.meanUniqueIps = uniqueIps;
            this.meanPacketRate = packetRate;
            this.meanJoinLeaveRatio = joinLeaveRatio;
            // Başlangıçta stddev = mean'in %20'si (makul bir başlangıç)
            this.stddevConnections = Math.max(1.0, connections * 0.2);
            this.stddevUniqueIps = Math.max(1.0, uniqueIps * 0.2);
            this.stddevPacketRate = Math.max(1.0, packetRate * 0.2);
            this.stddevJoinLeaveRatio = 0.1;
        } else {
            double a = Math.min(1.0, Math.max(0.001, alpha));

            this.meanConnections = a * connections + (1 - a) * meanConnections;
            this.meanUniqueIps = a * uniqueIps + (1 - a) * meanUniqueIps;
            this.meanPacketRate = a * packetRate + (1 - a) * meanPacketRate;
            this.meanJoinLeaveRatio = a * joinLeaveRatio + (1 - a) * meanJoinLeaveRatio;

            // EMA stddev (mutlak sapma üzerinden)
            this.stddevConnections = Math.max(1.0, a * Math.abs(connections - meanConnections) + (1 - a) * stddevConnections);
            this.stddevUniqueIps = Math.max(1.0, a * Math.abs(uniqueIps - meanUniqueIps) + (1 - a) * stddevUniqueIps);
            this.stddevPacketRate = Math.max(1.0, a * Math.abs(packetRate - meanPacketRate) + (1 - a) * stddevPacketRate);
            this.stddevJoinLeaveRatio = Math.max(0.01, a * Math.abs(joinLeaveRatio - meanJoinLeaveRatio) + (1 - a) * stddevJoinLeaveRatio);
        }

        this.sampleCount++;
        this.lastUpdated = System.currentTimeMillis();
    }

    // ─── Getters / Setters ───

    public double getMeanConnections() { return meanConnections; }
    public void setMeanConnections(double v) { this.meanConnections = v; }

    public double getStddevConnections() { return stddevConnections; }
    public void setStddevConnections(double v) { this.stddevConnections = v; }

    public double getMeanUniqueIps() { return meanUniqueIps; }
    public void setMeanUniqueIps(double v) { this.meanUniqueIps = v; }

    public double getStddevUniqueIps() { return stddevUniqueIps; }
    public void setStddevUniqueIps(double v) { this.stddevUniqueIps = v; }

    public double getMeanPacketRate() { return meanPacketRate; }
    public void setMeanPacketRate(double v) { this.meanPacketRate = v; }

    public double getStddevPacketRate() { return stddevPacketRate; }
    public void setStddevPacketRate(double v) { this.stddevPacketRate = v; }

    public double getMeanJoinLeaveRatio() { return meanJoinLeaveRatio; }
    public void setMeanJoinLeaveRatio(double v) { this.meanJoinLeaveRatio = v; }

    public double getStddevJoinLeaveRatio() { return stddevJoinLeaveRatio; }
    public void setStddevJoinLeaveRatio(double v) { this.stddevJoinLeaveRatio = v; }

    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int v) { this.sampleCount = v; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long v) { this.lastUpdated = v; }
}
