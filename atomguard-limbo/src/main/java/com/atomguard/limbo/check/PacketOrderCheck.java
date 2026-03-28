package com.atomguard.limbo.check;

/**
 * Paket sırası ve varlığı kontrolü.
 *
 * <p>Gerçek bir Minecraft client'ı login sonrası şu paketleri gönderir:
 * <ol>
 *   <li>CLIENT_SETTINGS (dil, render mesafesi, chat modu)
 *   <li>PLUGIN_MESSAGE / brand kanalı: "minecraft:brand"
 *   <li>PLAYER_POSITION veya PLAYER_POSITION_AND_ROTATION
 *   <li>PLAYER_ON_GROUND (her tick)
 * </ol>
 *
 * <p>Bot yazılımlarının çoğu CLIENT_SETTINGS paketi göndermez veya
 * POSITION paketini hiç göndermez → tespit edilir.
 *
 * <p>Bu check tek başına fail ettirmez; {@link GravityCheck} ile birlikte
 * karar verir.
 */
public class PacketOrderCheck {

    private boolean clientSettingsReceived = false;
    private boolean brandReceived = false;
    private boolean positionReceived = false;

    // ───────────────────────────── Paket Bildirimi ─────────────────────────────

    public void onClientSettings() {
        clientSettingsReceived = true;
    }

    public void onBrandReceived() {
        brandReceived = true;
    }

    public void onPositionReceived() {
        positionReceived = true;
    }

    // ───────────────────────────── Karar ─────────────────────────────

    /**
     * Minimum paket gereksinimleri karşılandı mı?
     *
     * <p>CLIENT_SETTINGS + en az 1 position update zorunlu.
     * Brand opsiyonel (bazı modded client'lar gecikmeli gönderir).
     */
    public boolean isMinimumMet() {
        return clientSettingsReceived && positionReceived;
    }

    /**
     * Paket eksikliği nedeniyle kesin bot mı?
     * POSITION hiç gelmemişse → kesinlikle bot.
     */
    public boolean isDefiniteBot() {
        return !positionReceived;
    }

    // ───────────────────────────── Getters ─────────────────────────────

    public boolean isClientSettingsReceived() { return clientSettingsReceived; }
    public boolean isBrandReceived()          { return brandReceived; }
    public boolean isPositionReceived()       { return positionReceived; }

    @Override
    public String toString() {
        return "PacketOrderCheck{settings=" + clientSettingsReceived +
               ", brand=" + brandReceived +
               ", position=" + positionReceived + "}";
    }
}
