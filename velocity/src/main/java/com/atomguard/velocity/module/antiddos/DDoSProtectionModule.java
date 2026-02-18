package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ana DDoS koruma modülü. Config key: "ddos-koruma"
 */
public class DDoSProtectionModule extends VelocityModule {

    private ConnectionThrottler throttler;
    private SynFloodDetector synFloodDetector;
    private SlowlorisDetector slowlorisDetector;
    private PingFloodDetector pingFloodDetector;
    private NullPingDetector nullPingDetector;
    private GeoBlocker geoBlocker;
    private SmartThrottleEngine throttleEngine;

    public DDoSProtectionModule(AtomGuardVelocity plugin) {
        super(plugin, "ddos-koruma");
    }

    @Override
    public void onEnable() {
        int connPerMinute = getConfigInt("baglanti-limiti.ip-basina-dakika", 5);
        int synThreshold = getConfigInt("saldiri-modu.esik", 30);
        int maxPingPerSecond = getConfigInt("ping-limit", 3);

        throttler = new ConnectionThrottler(connPerMinute);
        synFloodDetector = new SynFloodDetector(plugin, synThreshold);
        slowlorisDetector = new SlowlorisDetector(5, 10_000);
        pingFloodDetector = new PingFloodDetector(maxPingPerSecond);
        nullPingDetector = new NullPingDetector();

        if (getConfigBoolean("geo-engelleme.aktif", false)) {
            List<String> blocked = getConfigStringList("geo-engelleme.engelli-ulkeler");
            List<String> allowed = getConfigStringList("geo-engelleme.izinli-ulkeler");
            boolean wlMode = getConfigBoolean("geo-engelleme.izinli-ulkeler-modu", false);
            geoBlocker = new GeoBlocker(plugin.getDataDirectory(), blocked, allowed, wlMode, logger);
            try { geoBlocker.initialize(); }
            catch (Exception e) { logger.error("GeoIP başlatılamadı: {}", e.getMessage()); geoBlocker = null; }
        }

        int normalLimit = getConfigInt("akilli-throttle.normal-limit", 10);
        int carefulLimit = getConfigInt("akilli-throttle.dikkatli-limit", 5);
        int aggressiveLimit = getConfigInt("akilli-throttle.agresif-limit", 2);
        int lockdownLimit = getConfigInt("akilli-throttle.lockdown-limit", 0);
        throttleEngine = new SmartThrottleEngine(plugin, normalLimit, carefulLimit, aggressiveLimit, lockdownLimit);

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::periodicCleanup)
            .repeat(1, TimeUnit.MINUTES)
            .schedule();
    }

    @Override
    public void onDisable() {
        if (geoBlocker != null) geoBlocker.close();
        if (synFloodDetector != null) synFloodDetector.shutdown();
    }

    public ConnectionCheckResult checkConnection(String ip, boolean isVerified) {
        if (!enabled) return new ConnectionCheckResult(true, "disabled", null);
        synFloodDetector.recordConnection();

        // Slowloris kontrolü
        String connId = ip + ":" + System.nanoTime();
        slowlorisDetector.onConnectionStarted(ip, connId);
        if (slowlorisDetector.isSlowlorisIP(ip)) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("ddos_blocked");
            return new ConnectionCheckResult(false, "slowloris", "kick.ddos");
        }

        if (!throttleEngine.shouldAllow(ip, isVerified)) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("ddos_blocked");
            return new ConnectionCheckResult(false, "lockdown", "kick.ddos");
        }

        boolean throttled = isAttackMode()
            ? !throttler.tryConnectAttackMode(ip)
            : !throttler.tryConnect(ip);

        if (throttled) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("ddos_blocked");
            return new ConnectionCheckResult(false, "throttle", "kick.rate-limit");
        }

        if (geoBlocker != null && geoBlocker.isBlocked(ip)) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("ddos_blocked");
            return new ConnectionCheckResult(false, "geo", "kick.genel");
        }

        return new ConnectionCheckResult(true, "ok", null);
    }

    public boolean checkPing(String ip) {
        if (!enabled) return true;
        return pingFloodDetector.allowPing(ip);
    }

    public HandshakeCheckResult checkHandshake(String ip, String hostname, int port, int protocolVersion) {
        if (!enabled) return new HandshakeCheckResult(true, "ok");
        if (!nullPingDetector.isValidHandshake(hostname, port, protocolVersion)) {
            nullPingDetector.recordInvalid(ip);
            incrementBlocked();
            return new HandshakeCheckResult(false, "Geçersiz handshake");
        }
        if (nullPingDetector.isBlocked(ip)) {
            incrementBlocked();
            return new HandshakeCheckResult(false, "Tekrarlayan geçersiz handshake");
        }
        return new HandshakeCheckResult(true, "ok");
    }

    public void onHandshakeComplete(String ip, String connId) {
        if (slowlorisDetector != null) slowlorisDetector.onHandshakeComplete(ip, connId);
    }
    public void onConnectionClosed(String ip, String connId) {
        if (slowlorisDetector != null) slowlorisDetector.onConnectionClosed(ip, connId);
    }

    private void periodicCleanup() {
        if (throttler != null) throttler.cleanup();
        if (nullPingDetector != null) nullPingDetector.cleanup();
        if (pingFloodDetector != null) pingFloodDetector.cleanup();
        if (slowlorisDetector != null) slowlorisDetector.cleanupExpiredConnections();
        if (throttleEngine != null) throttleEngine.relax();
    }

    public ConnectionThrottler getThrottler() { return throttler; }
    public SynFloodDetector getSynFloodDetector() { return synFloodDetector; }
    public PingFloodDetector getPingFloodDetector() { return pingFloodDetector; }
    public SmartThrottleEngine getThrottleEngine() { return throttleEngine; }
    public GeoBlocker getGeoBlocker() { return geoBlocker; }

    public record ConnectionCheckResult(boolean allowed, String reason, String kickMessageKey) {}
    public record HandshakeCheckResult(boolean valid, String reason) {}
}
