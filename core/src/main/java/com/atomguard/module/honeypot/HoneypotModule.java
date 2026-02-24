package com.atomguard.module.honeypot;

import com.atomguard.AtomGuard;
import com.atomguard.api.event.HoneypotTrapEvent;
import com.atomguard.module.AbstractModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Bal Kupu (Honeypot) Modülü — System 5.
 *
 * <p>Gerçek Minecraft portlarına bağlanmaması gereken bot tarayıcıları ve
 * saldırgan IP'leri tespit etmek için yapılandırılmış portlarda sahte TCP/SLP
 * sunucuları çalıştırır. Yakalanan IP'ler yapılandırılmış süre boyunca kara
 * listeye alınır.</p>
 *
 * <p>Config yolu: {@code moduller.bal-kupu.*}</p>
 */
public class HoneypotModule extends AbstractModule {

    private final List<HoneypotServer> servers = new ArrayList<>();
    private HoneypotBlacklistBridge blacklistBridge;
    private HoneypotStatistics statistics;
    private FakeMotdHandler fakeMotdHandler;

    // --- Config alanları ---
    private List<Integer> honeypotPorts;
    private int blacklistDurationSeconds;
    private boolean fakeMotdEnabled;
    private int maxConnectionsPerIp;
    private boolean instantBlacklist;
    private boolean whitelistExempt;
    private int maxConcurrentConnections;
    private int connectionTimeoutSeconds;
    private boolean discordBildirim;
    private String logSeviyesi;

    public HoneypotModule(AtomGuard plugin) {
        super(plugin, "bal-kupu", "Bot tarayıcı ve saldırgan IP tuzak sistemi");
    }

    // ═══════════════════════════════════════════════════════
    // Yaşam döngüsü
    // ═══════════════════════════════════════════════════════

    @Override
    public void onEnable() {
        loadConfig();

        this.blacklistBridge = new HoneypotBlacklistBridge(plugin);
        this.statistics = new HoneypotStatistics();

        // Sahte MOTD işleyicisi
        if (fakeMotdEnabled) {
            String serverName = plugin.getConfig().getString(
                    "moduller.bal-kupu.sahte-motd.sunucu-adi", "§a§lPopüler Sunucu");
            int maxP = plugin.getConfig().getInt(
                    "moduller.bal-kupu.sahte-motd.max-oyuncu", 200);
            int onlineP = plugin.getConfig().getInt(
                    "moduller.bal-kupu.sahte-motd.online-oyuncu", 87);
            String ver = plugin.getConfig().getString(
                    "moduller.bal-kupu.sahte-motd.versiyon", "1.21.4");
            int proto = plugin.getConfig().getInt(
                    "moduller.bal-kupu.sahte-motd.protokol", 769);
            this.fakeMotdHandler = new FakeMotdHandler(serverName, maxP, onlineP, ver, proto);
        }

        // Her port için ayrı HoneypotServer başlat
        for (int port : honeypotPorts) {
            HoneypotServer server = new HoneypotServer(port, this);
            servers.add(server);
            server.start();
        }

        // AbstractModule'ün enabled bayrağını ve log'unu ayarla
        super.onEnable();
    }

    @Override
    public void onDisable() {
        for (HoneypotServer server : servers) {
            server.stop();
        }
        servers.clear();
        super.onDisable();
    }

    /** Periyodik bakım: süresi dolmuş kara liste kayıtlarını temizle */
    @Override
    public void cleanup() {
        if (blacklistBridge != null) {
            blacklistBridge.cleanup();
        }
    }

    // ═══════════════════════════════════════════════════════
    // Config yükleme
    // ═══════════════════════════════════════════════════════

    private void loadConfig() {
        honeypotPorts = plugin.getConfig().getIntegerList("moduller.bal-kupu.portlar");
        if (honeypotPorts == null || honeypotPorts.isEmpty()) {
            honeypotPorts = new ArrayList<>(List.of(25566, 25567));
        }

        blacklistDurationSeconds = plugin.getConfig().getInt(
                "moduller.bal-kupu.blacklist.sure-sn", 3600);
        fakeMotdEnabled = plugin.getConfig().getBoolean(
                "moduller.bal-kupu.sahte-motd.aktif", true);
        maxConnectionsPerIp = plugin.getConfig().getInt(
                "moduller.bal-kupu.blacklist.max-baglanti", 3);
        instantBlacklist = plugin.getConfig().getBoolean(
                "moduller.bal-kupu.blacklist.aninda-engelle", true);
        whitelistExempt = plugin.getConfig().getBoolean(
                "moduller.bal-kupu.beyaz-liste-muaf", true);
        maxConcurrentConnections = plugin.getConfig().getInt(
                "moduller.bal-kupu.guvenlik.max-eszamanli-baglanti", 50);
        connectionTimeoutSeconds = plugin.getConfig().getInt(
                "moduller.bal-kupu.guvenlik.baglanti-zaman-asimi-sn", 5);
        discordBildirim = plugin.getConfig().getBoolean(
                "moduller.bal-kupu.discord-bildirim", true);
        logSeviyesi = plugin.getConfig().getString(
                "moduller.bal-kupu.log-seviyesi", "SUMMARY");
    }

    // ═══════════════════════════════════════════════════════
    // Bağlantı işleme
    // ═══════════════════════════════════════════════════════

    /**
     * Bir HoneypotServer'dan honeypot bağlantısı bildirimi alır.
     *
     * @param ip              Bağlanan IP adresi
     * @param port            Bağlantının yapıldığı honeypot portu
     * @param protocol        Tespit edilen protokol ("SLP", "TCP_RAW", "UNKNOWN")
     * @param connectionCount Bu IP'nin bu porta kaçıncı bağlantısı
     */
    public void onHoneypotConnection(String ip, int port, String protocol, int connectionCount) {
        // Beyaz liste muafiyeti — verified/trusted IP'leri atla
        if (whitelistExempt) {
            if (plugin.getVerifiedPlayerCache() != null
                    && plugin.getVerifiedPlayerCache().isIpVerified(ip)) {
                logIfDetailed("[Honeypot] " + ip + " muaf tutuldu (doğrulanmış oyuncu).");
                return;
            }
        }

        // Kara listeye alınmalı mı?
        boolean shouldBlacklist = instantBlacklist || connectionCount >= maxConnectionsPerIp;

        if (shouldBlacklist && !blacklistBridge.isBlacklisted(ip)) {
            blacklistBridge.blacklist(ip, blacklistDurationSeconds);
        }

        // İstatistikleri kaydet
        HoneypotConnection conn = new HoneypotConnection(ip, port, protocol, shouldBlacklist);
        statistics.record(conn);
        incrementBlockedCount();

        // Log
        if (!logSeviyesi.equalsIgnoreCase("NONE")) {
            String durationStr = buildDurationString();
            plugin.getLogManager().info(
                    "[Honeypot] " + ip + " yakalandı (port " + port + ", " + protocol + ")"
                    + (shouldBlacklist ? " → " + durationStr + " engellendi" : ""));
        }

        // Discord bildirimi
        if (discordBildirim && shouldBlacklist && plugin.getDiscordWebhookManager() != null) {
            plugin.getDiscordWebhookManager().notifyHoneypotTrap(ip, port);
        }

        // API event'i async fire et
        HoneypotTrapEvent event = new HoneypotTrapEvent(ip, port, shouldBlacklist);
        plugin.getServer().getPluginManager().callEvent(event);
    }

    // ═══════════════════════════════════════════════════════
    // Yardımcı metotlar
    // ═══════════════════════════════════════════════════════

    private String buildDurationString() {
        if (blacklistDurationSeconds <= 0) return "kalıcı";
        if (blacklistDurationSeconds < 60) return blacklistDurationSeconds + " saniye";
        return (blacklistDurationSeconds / 60) + " dakika";
    }

    private void logIfDetailed(String message) {
        if (logSeviyesi.equalsIgnoreCase("DETAILED")) {
            plugin.getLogManager().debug(message);
        }
    }

    /**
     * Bir IP'nin honeypot kara listesinde olup olmadığını kontrol eder.
     *
     * @param ip Kontrol edilecek IP adresi
     * @return Kara listedeyse {@code true}
     */
    public boolean isIpBlacklisted(String ip) {
        return blacklistBridge != null && blacklistBridge.isBlacklisted(ip);
    }

    // ═══════════════════════════════════════════════════════
    // Getterlar
    // ═══════════════════════════════════════════════════════

    public HoneypotStatistics getStatistics() { return statistics; }
    public HoneypotBlacklistBridge getBlacklistBridge() { return blacklistBridge; }
    public FakeMotdHandler getFakeMotdHandler() { return fakeMotdHandler; }
    public boolean isFakeMotdEnabled() { return fakeMotdEnabled; }
    public int getMaxConcurrentConnections() { return maxConcurrentConnections; }
    public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
    public List<Integer> getHoneypotPorts() { return new ArrayList<>(honeypotPorts); }
    public List<HoneypotServer> getServers() { return new ArrayList<>(servers); }
}
