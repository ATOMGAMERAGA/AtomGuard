package com.atomguard.velocity.module.verification;

import java.util.UUID;

/**
 * Tek bir oyuncunun limbo doğrulama oturumunu temsil eder.
 * Tüm kontrol sonuçlarını ve zamanlama bilgilerini tutar.
 */
public class VerificationSession {

    private final UUID playerUuid;
    private final String ip;
    private final String username;
    private final long startTime;

    /** Timeout sonrası ana sunucuya transfer bekleniyor mu? */
    private volatile boolean transferring = false;
    /** Oturum tamamlandı mı (PASS veya FAIL)? */
    private volatile boolean complete = false;
    /** Sonuç: true = doğrulandı, false = başarısız. */
    private volatile boolean result = false;

    public VerificationSession(UUID playerUuid, String ip, String username) {
        this.playerUuid = playerUuid;
        this.ip = ip;
        this.username = username;
        this.startTime = System.currentTimeMillis();
    }

    // ───────────────────────────── Durum ─────────────────────────────

    public boolean isTimedOut(int timeoutSeconds) {
        return System.currentTimeMillis() - startTime > timeoutSeconds * 1000L;
    }

    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }

    public void complete(boolean passed) {
        this.result = passed;
        this.complete = true;
    }

    // ───────────────────────────── Getters ─────────────────────────────

    public UUID getPlayerUuid() { return playerUuid; }
    public String getIp() { return ip; }
    public String getUsername() { return username; }
    public long getStartTime() { return startTime; }
    public boolean isComplete() { return complete; }
    public boolean isPassed() { return result; }
    public boolean isTransferring() { return transferring; }
    public void setTransferring(boolean transferring) { this.transferring = transferring; }
}
