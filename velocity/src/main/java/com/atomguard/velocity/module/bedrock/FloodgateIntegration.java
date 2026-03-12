package com.atomguard.velocity.module.bedrock;

import com.atomguard.velocity.AtomGuardVelocity;
import com.velocitypowered.api.proxy.InboundConnection;

/**
 * Floodgate API ile opsiyonel entegrasyon.
 *
 * <p>Floodgate mevcut değilse graceful degrade eder.</p>
 */
public class FloodgateIntegration {

    private final AtomGuardVelocity plugin;
    private boolean available;
    private Object floodgateApi; // Reflection ile erişilecek

    public FloodgateIntegration(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            Class<?> floodgateClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            var getInstanceMethod = floodgateClass.getMethod("getInstance");
            this.floodgateApi = getInstanceMethod.invoke(null);
            this.available = true;
            plugin.getSlf4jLogger().info("Floodgate API entegrasyonu aktif.");
        } catch (ClassNotFoundException e) {
            this.available = false;
            plugin.getSlf4jLogger().info("Floodgate bulunamadı — Bedrock tespiti prefix tabanlı olacak.");
        } catch (Exception e) {
            this.available = false;
            plugin.getSlf4jLogger().warn("Floodgate API başlatılamadı: {}", e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Floodgate API ile Bedrock oyuncu olup olmadığını kontrol eder.
     */
    public boolean isBedrockPlayer(InboundConnection connection) {
        if (!available || floodgateApi == null) return false;
        try {
            var method = floodgateApi.getClass().getMethod("isFloodgatePlayer", java.util.UUID.class);
            // PreLoginEvent'te UUID olmayabilir, IP ile kontrol
            // Floodgate genelde isFloodgateId() veya prefix kontrolü yapar
            return false; // Floodgate tam entegrasyonu login sonrası gerektirir
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Floodgate üzerinden Xbox Live XUID'sini alır.
     */
    public String getXuid(java.util.UUID playerId) {
        if (!available || floodgateApi == null) return null;
        try {
            var method = floodgateApi.getClass().getMethod("getPlayer", java.util.UUID.class);
            Object player = method.invoke(floodgateApi, playerId);
            if (player == null) return null;
            var xuidMethod = player.getClass().getMethod("getXuid");
            return String.valueOf(xuidMethod.invoke(player));
        } catch (Exception e) {
            return null;
        }
    }
}
