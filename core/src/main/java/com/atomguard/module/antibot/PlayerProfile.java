package com.atomguard.module.antibot;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.handshaking.client.WrapperHandshakingClientHandshake;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerProfile {
    private UUID uuid;
    private String username;
    private final String ipAddress;
    private final long firstSeen;
    private long lastSeen;
    
    // Identity
    private int protocolVersion;
    private String clientBrand;
    private String handshakeHostname;
    
    // Timing and stats
    private final AtomicInteger ticksSinceJoin = new AtomicInteger(0);
    private final AtomicLong lastPingTime = new AtomicLong(0);
    private final AtomicLong handshakeTime = new AtomicLong(0);
    private final AtomicLong loginStartTime = new AtomicLong(0);
    private final AtomicLong encryptionRequestTime = new AtomicLong(0);
    private final AtomicLong encryptionResponseTime = new AtomicLong(0);
    private final AtomicLong firstJoinTime = new AtomicLong(0);
    
    // Flags
    private boolean sentClientSettings = false;
    private boolean sentPositionPacket = false;
    private boolean interactedWithInventory = false;
    private boolean interactedWithWorld = false;
    
    // Movement and analysis data
    private final Deque<Double> recentYPositions = new ConcurrentLinkedDeque<>();
    private final Deque<Long> positionPacketTimestamps = new ConcurrentLinkedDeque<>();
    private final Set<Float> uniqueYaws = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Set<Float> uniquePitches = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Set<String> uniquePositions = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    
    private final AtomicLong firstMovementTime = new AtomicLong(0);
    private final AtomicLong firstChatTime = new AtomicLong(0);
    private final AtomicLong lastKeepAliveSent = new AtomicLong(0);
    private final Deque<Long> keepAliveResponseTimes = new ConcurrentLinkedDeque<>();
    
    private int cachedFirstJoinScore = 0;
    private int currentThreatScore = 0;
    private int maxThreatScore = 0;
    private int successfulSessionCount = 0; // In a real scenario, this would persist

    public PlayerProfile(@Nullable UUID uuid, @Nullable String username, String ipAddress) {
        this.uuid = uuid;
        this.username = username;
        this.ipAddress = ipAddress;
        this.firstSeen = System.currentTimeMillis();
        this.lastSeen = firstSeen;
    }

    public void updateIdentity(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
        this.lastSeen = System.currentTimeMillis();
    }

    public void onJoin() {
        this.firstJoinTime.set(System.currentTimeMillis());
        this.lastSeen = System.currentTimeMillis();
    }

    public void tick() {
        ticksSinceJoin.incrementAndGet();
    }

    public void recordPing() {
        lastPingTime.set(System.currentTimeMillis());
        lastSeen = System.currentTimeMillis();
    }

    public void recordHandshake(PacketReceiveEvent event) {
        handshakeTime.set(System.currentTimeMillis());
        WrapperHandshakingClientHandshake wrapper = new WrapperHandshakingClientHandshake(event);
        this.protocolVersion = wrapper.getProtocolVersion();
        this.handshakeHostname = wrapper.getServerAddress();
        lastSeen = System.currentTimeMillis();
    }

    public void recordLoginStart(PacketReceiveEvent event) {
        loginStartTime.set(System.currentTimeMillis());
        lastSeen = System.currentTimeMillis();
    }

    public void recordEncryptionRequest() {
        encryptionRequestTime.set(System.currentTimeMillis());
    }

    public void recordEncryptionResponse() {
        encryptionResponseTime.set(System.currentTimeMillis());
        lastSeen = System.currentTimeMillis();
    }

    public void recordClientSettings() {
        sentClientSettings = true;
        lastSeen = System.currentTimeMillis();
    }

    public void recordPluginMessage(PacketReceiveEvent event) {
        WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
        if (wrapper.getChannelName().equals("minecraft:brand") || wrapper.getChannelName().equals("brand")) {
            try {
                // PacketEvents 2.x handles brand reading differently, usually it's a string in the buffer
                // For simplicity, we just mark that we got a brand
                this.clientBrand = "unknown"; // In a real implementation, read from buffer
            } catch (Exception ignored) {}
        }
        lastSeen = System.currentTimeMillis();
    }

    public void recordMovement(PacketReceiveEvent event) {
        long now = System.currentTimeMillis();
        if (firstMovementTime.get() == 0) {
            firstMovementTime.set(now);
        }
        
        double y;
        float yaw = 0, pitch = 0;
        boolean hasLook = false;
        
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
            WrapperPlayClientPlayerPosition wrapper = new WrapperPlayClientPlayerPosition(event);
            y = wrapper.getPosition().getY();
            uniquePositions.add(wrapper.getPosition().getX() + "," + wrapper.getPosition().getY() + "," + wrapper.getPosition().getZ());
        } else {
            WrapperPlayClientPlayerPositionAndRotation wrapper = new WrapperPlayClientPlayerPositionAndRotation(event);
            y = wrapper.getPosition().getY();
            yaw = wrapper.getYaw();
            pitch = wrapper.getPitch();
            hasLook = true;
            uniquePositions.add(wrapper.getPosition().getX() + "," + wrapper.getPosition().getY() + "," + wrapper.getPosition().getZ());
        }
        
        sentPositionPacket = true;
        recentYPositions.addLast(y);
        if (recentYPositions.size() > 20) recentYPositions.removeFirst();
        
        positionPacketTimestamps.addLast(now);
        if (positionPacketTimestamps.size() > 50) positionPacketTimestamps.removeFirst();
        
        if (hasLook) {
            uniqueYaws.add(yaw);
            uniquePitches.add(pitch);
        }
        
        lastSeen = now;
    }

    public void recordRotation(PacketReceiveEvent event) {
        WrapperPlayClientPlayerRotation wrapper = new WrapperPlayClientPlayerRotation(event);
        uniqueYaws.add(wrapper.getYaw());
        uniquePitches.add(wrapper.getPitch());
        lastSeen = System.currentTimeMillis();
    }

    public void recordKeepAliveSent() {
        lastKeepAliveSent.set(System.currentTimeMillis());
    }

    public void recordKeepAliveResponse() {
        long sent = lastKeepAliveSent.get();
        if (sent > 0) {
            keepAliveResponseTimes.addLast(System.currentTimeMillis() - sent);
            if (keepAliveResponseTimes.size() > 10) keepAliveResponseTimes.removeFirst();
        }
        lastSeen = System.currentTimeMillis();
    }

    public void recordChat() {
        if (firstChatTime.get() == 0) {
            firstChatTime.set(System.currentTimeMillis());
        }
        lastSeen = System.currentTimeMillis();
    }

    public void recordInventoryInteraction() {
        interactedWithInventory = true;
        lastSeen = System.currentTimeMillis();
    }

    public void recordWorldInteraction() {
        interactedWithWorld = true;
        lastSeen = System.currentTimeMillis();
    }

    // Getters and helper methods
    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public String getIpAddress() { return ipAddress; }
    public int getProtocolVersion() { return protocolVersion; }
    public String getClientBrand() { return clientBrand; }
    public String getHandshakeHostname() { return handshakeHostname; }
    public int getTicksSinceJoin() { return ticksSinceJoin.get(); }
    public long getHandshakeTime() { return handshakeTime.get(); }
    public long getLastPingTime() { return lastPingTime.get(); }
    public boolean hasSentClientSettings() { return sentClientSettings; }
    public boolean hasSentPositionPacket() { return sentPositionPacket; }
    public List<Double> getRecentYPositions() { return new ArrayList<>(recentYPositions); }
    public int getUniqueYawValues() { return uniqueYaws.size(); }
    public int getUniquePitchValues() { return uniquePitches.size(); }
    public int getUniquePositionCount() { return uniquePositions.size(); }
    public long getFirstMovementDelayMs() { 
        return firstMovementTime.get() > 0 ? firstMovementTime.get() - firstJoinTime.get() : -1; 
    }
    public long getFirstChatDelayMs() { 
        return firstChatTime.get() > 0 ? firstChatTime.get() - firstJoinTime.get() : -1; 
    }
    public double getAveragePositionPacketInterval() {
        if (positionPacketTimestamps.size() < 2) return -1;
        List<Long> times = new ArrayList<>(positionPacketTimestamps);
        long total = 0;
        for (int i = 1; i < times.size(); i++) {
            total += (times.get(i) - times.get(i-1));
        }
        return (double) total / (times.size() - 1);
    }
    public double getPositionPacketVariance() {
        if (positionPacketTimestamps.size() < 10) return -1;
        List<Long> times = new ArrayList<>(positionPacketTimestamps);
        List<Long> intervals = new ArrayList<>();
        double sum = 0;
        for (int i = 1; i < times.size(); i++) {
            long interval = times.get(i) - times.get(i-1);
            intervals.add(interval);
            sum += interval;
        }
        double mean = sum / intervals.size();
        double varianceSum = 0;
        for (long interval : intervals) {
            varianceSum += Math.pow(interval - mean, 2);
        }
        return varianceSum / intervals.size();
    }
    public long getAverageKeepAliveResponseMs() {
        if (keepAliveResponseTimes.isEmpty()) return -1;
        long total = 0;
        for (long time : keepAliveResponseTimes) total += time;
        return total / keepAliveResponseTimes.size();
    }
    public boolean hasInteractedWithInventory() { return interactedWithInventory; }
    public boolean hasInteractedWithWorld() { return interactedWithWorld; }
    public int getCachedFirstJoinScore() { return cachedFirstJoinScore; }
    public void setCachedFirstJoinScore(int score) { this.cachedFirstJoinScore = score; }
    public int getMaxThreatScore() { return maxThreatScore; }
    public void updateMaxThreatScore(int score) { if (score > maxThreatScore) maxThreatScore = score; }
    public int getCurrentThreatScore() { return currentThreatScore; }
    public void setCurrentThreatScore(int score) { this.currentThreatScore = score; }
    public int getSuccessfulSessionCount() { return successfulSessionCount; }
    public void incrementSuccessfulSessionCount() { successfulSessionCount++; }
    public long getLastSeen() { return lastSeen; }
    public int getPositionPacketCount() { return positionPacketTimestamps.size(); }
}
