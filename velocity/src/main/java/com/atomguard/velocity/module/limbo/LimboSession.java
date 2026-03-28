package com.atomguard.velocity.module.limbo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tek bir oyuncunun limbo doğrulama oturumu.
 */
public class LimboSession {

    public enum State { WAITING_POSITION, COLLECTING, COMPLETE, FAILED, TIMEOUT }

    private final UUID playerUuid;
    private final String ip;
    private final String targetServer;
    private final long createdAt;
    private final long timeoutMs;
    private final List<Double> receivedYPositions = new ArrayList<>();
    private final CompletableFuture<Boolean> result = new CompletableFuture<>();

    private volatile State state = State.WAITING_POSITION;
    private volatile boolean teleportSent = false;

    public LimboSession(UUID playerUuid, String ip, String targetServer, long timeoutMs) {
        this.playerUuid = playerUuid;
        this.ip = ip;
        this.targetServer = targetServer;
        this.timeoutMs = timeoutMs;
        this.createdAt = System.currentTimeMillis();
    }

    public void recordYPosition(double y) {
        receivedYPositions.add(y);
        if (state == State.WAITING_POSITION) state = State.COLLECTING;
    }

    public boolean isTimedOut() {
        return System.currentTimeMillis() - createdAt > timeoutMs;
    }

    public boolean hasEnoughData() {
        return receivedYPositions.size() >= 5;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getIp() { return ip; }
    public String getTargetServer() { return targetServer; }
    public List<Double> getReceivedYPositions() { return List.copyOf(receivedYPositions); }
    public CompletableFuture<Boolean> getResult() { return result; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public boolean isTeleportSent() { return teleportSent; }
    public void setTeleportSent(boolean teleportSent) { this.teleportSent = teleportSent; }
}
