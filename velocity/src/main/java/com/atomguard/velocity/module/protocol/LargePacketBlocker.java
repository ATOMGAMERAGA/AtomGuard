package com.atomguard.velocity.module.protocol;

/**
 * Aşırı büyük paket boyutu engelleme.
 */
public class LargePacketBlocker {

    private final int maxPacketSize;
    private final int warnThreshold;

    public LargePacketBlocker(int maxPacketSize, int warnThreshold) {
        this.maxPacketSize = maxPacketSize;
        this.warnThreshold = warnThreshold;
    }

    public CheckResult check(int packetSize, String packetType) {
        if (packetSize > maxPacketSize)
            return new CheckResult(Action.BLOCK, "Paket çok büyük: " + packetSize + " bytes (" + packetType + ")");
        if (packetSize > warnThreshold)
            return new CheckResult(Action.WARN, "Büyük paket: " + packetSize + " bytes (" + packetType + ")");
        return new CheckResult(Action.ALLOW, "ok");
    }

    public enum Action { ALLOW, WARN, BLOCK }
    public record CheckResult(Action action, String reason) {}
}
