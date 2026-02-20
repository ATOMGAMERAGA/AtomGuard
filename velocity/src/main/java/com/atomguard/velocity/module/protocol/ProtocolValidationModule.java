package com.atomguard.velocity.module.protocol;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.concurrent.TimeUnit;

/**
 * Protokol doğrulama modülü. Config key: "protokol-dogrulama"
 */
public class ProtocolValidationModule extends VelocityModule {

    private HandshakeSanitizer handshakeSanitizer;
    private ProtocolVersionFilter versionFilter;
    private InvalidPacketFilter invalidPacketFilter;
    private LargePacketBlocker largePacketBlocker;

    public ProtocolValidationModule(AtomGuardVelocity plugin) {
        super(plugin, "protokol-dogrulama");
    }

    @Override
    public int getPriority() { return 5; }

    @Override
    public void onEnable() {
        boolean enforceKnown = getConfigBoolean("bilinen-protokol-zorla", false);
        int minProtocol = getConfigInt("min-protokol", 0);
        int maxProtocol = getConfigInt("max-protokol", 0);
        int maxInvalid = getConfigInt("max-gecersiz-paket", 10);
        int maxPacketSize = getConfigInt("max-paket-boyutu", 32768);
        int warnPacketSize = getConfigInt("uyari-paket-boyutu", 16384);

        handshakeSanitizer = new HandshakeSanitizer();
        versionFilter = new ProtocolVersionFilter(enforceKnown, minProtocol, maxProtocol);
        invalidPacketFilter = new InvalidPacketFilter(maxInvalid, 60_000L);
        largePacketBlocker = new LargePacketBlocker(maxPacketSize, warnPacketSize);

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, invalidPacketFilter::cleanup)
            .repeat(2, TimeUnit.MINUTES)
            .schedule();
    }

    @Override
    public void onDisable() {}

    public ValidationResult validateHandshake(String ip, String hostname, int port, int protocol) {
        if (!enabled) return new ValidationResult(true, "disabled", null);

        HandshakeSanitizer.SanitizeResult sanitize = handshakeSanitizer.sanitizeHostname(hostname);
        if (!sanitize.valid()) {
            if (invalidPacketFilter.recordAndCheck(ip)) {
                incrementBlocked();
                plugin.getStatisticsManager().increment("protocol_blocked");
                return new ValidationResult(false, "invalid-hostname", sanitize.reason());
            }
        }

        ProtocolVersionFilter.FilterResult versionResult = versionFilter.check(protocol);
        if (!versionResult.allowed()) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("protocol_blocked");
            return new ValidationResult(false, "invalid-protocol", versionResult.reason());
        }

        return new ValidationResult(true, "ok", sanitize.sanitized());
    }

    public boolean checkPacketSize(String ip, int size, String type) {
        if (!enabled) return true;
        LargePacketBlocker.CheckResult result = largePacketBlocker.check(size, type);
        if (result.action() == LargePacketBlocker.Action.BLOCK) {
            if (invalidPacketFilter.recordAndCheck(ip)) {
                incrementBlocked();
                plugin.getStatisticsManager().increment("large_packets_blocked");
                return false;
            }
        } else if (result.action() == LargePacketBlocker.Action.WARN) {
            logger.warn("Büyük paket: IP={} Boyut={} Tür={}", ip, size, type);
        }
        return true;
    }

    public record ValidationResult(boolean valid, String reason, String sanitizedHostname) {}
}
