package com.atomguard;

import com.atomguard.api.AtomGuardAPI;
import com.atomguard.api.storage.IStorageProvider;
import com.atomguard.storage.MySQLStorageProvider;
import com.atomguard.command.AtomGuardCommand;
import com.atomguard.command.AtomGuardTabCompleter;
import com.atomguard.listener.BukkitListener;
import com.atomguard.listener.InventoryListener;
import com.atomguard.listener.PacketListener;
import com.atomguard.manager.ConfigManager;
import com.atomguard.manager.LogManager;
import com.atomguard.manager.MessageManager;
import com.atomguard.manager.ModuleManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import com.atomguard.data.VerifiedPlayerCache;
import com.atomguard.heuristic.HeuristicEngine;
import com.atomguard.manager.AttackModeManager;
import com.atomguard.manager.DiscordWebhookManager;
import com.atomguard.manager.StatisticsManager;
import com.atomguard.reputation.IPReputationManager;
import com.atomguard.web.WebPanel;

/**
 * AtomGuard - Paper 1.21.4 Exploit Fixer Plugin
 * Gelişmiş exploit düzeltme ve sunucu koruma sistemi
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public final class AtomGuard extends JavaPlugin {

    // Singleton instance
    private static AtomGuard instance;


    // Manager sınıfları
    private ConfigManager configManager;
    private MessageManager messageManager;
    private LogManager logManager;
    private ModuleManager moduleManager;
    
    // Storage Provider
    private IStorageProvider storageProvider;
    
    // Attack Mode Manager
    private AttackModeManager attackModeManager;

    // Heuristic Engine
    private HeuristicEngine heuristicEngine;

    // Web Panel
    private WebPanel webPanel;

    // IP Reputation Manager
    private IPReputationManager reputationManager;

    // Discord Webhook Manager
    private DiscordWebhookManager discordWebhookManager;

    // Statistics Manager
    private StatisticsManager statisticsManager;

    // Redis Manager (Cross-Server Sync)
    private com.atomguard.manager.RedisManager redisManager;

    // Verified Player Cache
    private VerifiedPlayerCache verifiedPlayerCache;

    // Listener'lar
    private PacketListener packetListener;
    private BukkitListener bukkitListener;
    private InventoryListener inventoryListener;

    /**
     * Plugin aktif edildiğinde çağrılır
     */
    @Override
    public void onLoad() {
        // Singleton instance
        instance = this;

        // PacketEvents'i yükle
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    /**
     * Plugin etkinleştirildiğinde çağrılır
     */
    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        // ASCII Art Banner
        printBanner();

        // PacketEvents kontrolü
        if (!checkPacketEvents()) {
            getLogger().severe("╔════════════════════════════════════════╗");
            getLogger().severe("║  PacketEvents bulunamadı!              ║");
            getLogger().severe("║  Plugin devre dışı bırakılıyor...      ║");
            getLogger().severe("╚════════════════════════════════════════╝");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Manager'ları başlat
        initializeManagers();

        // Listener'ları kaydet
        registerListeners();

        // Komutları kaydet
        registerCommands();

        // PacketEvents'i başlat
        PacketEvents.getAPI().init();

        // Periyodik temizlik görevi (bellek sızıntısı önleme) - her 5 dakikada bir
        startCleanupTask();

        // Başarı mesajı
        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  Atom Guard başarıyla yüklendi!    ║");
        getLogger().info("║  Versiyon: " + BuildInfo.getFullVersion() + "                         ║");
        getLogger().info("║  Yükleme süresi: " + loadTime + "ms                  ║");
        getLogger().info("║  Aktif modül: " + moduleManager.getEnabledModuleCount() + "/" + moduleManager.getTotalModuleCount() + "                    ║");
        getLogger().info("╚════════════════════════════════════════╝");
    }

    /**
     * Plugin devre dışı bırakıldığında çağrılır
     */
    @Override
    public void onDisable() {
        getLogger().info("AtomGuard kapatılıyor...");

        // Discord Webhook durdur
        if (discordWebhookManager != null) {
            discordWebhookManager.stop();
        }

        // Statistics kaydet ve durdur
        if (statisticsManager != null) {
            statisticsManager.stop();
        }

        // Redis kapat
        if (redisManager != null) {
            redisManager.stop();
        }

        // Verified Player Cache kaydet ve durdur
        if (verifiedPlayerCache != null) {
            verifiedPlayerCache.stop();
        }

        // Web Panel'i durdur
        if (webPanel != null) {
            webPanel.stop();
        }

        // Anti-VPN sistemi kapat (task durdur + cache kaydet)
        if (reputationManager != null) {
            reputationManager.shutdown();
        }

        // Modülleri devre dışı bırak
        if (moduleManager != null) {
            moduleManager.disableAllModules();
        }

        // Log sistemini durdur
        if (logManager != null) {
            logManager.stop();
        }

        // PacketEvents'i kapat
        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }

        // API kapat
        AtomGuardAPI.shutdown();

        // Storage Provider kapat
        if (storageProvider != null && storageProvider.isConnected()) {
            storageProvider.disconnect();
        }

        getLogger().info("AtomGuard başarıyla kapatıldı.");
    }

    /**
     * Manager'ları başlatır
     */
    private void initializeManagers() {
        getLogger().info("Manager'lar başlatılıyor...");

        // Config Manager
        this.configManager = new ConfigManager(this);
        configManager.load();

        // Message Manager
        this.messageManager = new MessageManager(this);

        // Log Manager
        this.logManager = new LogManager(this);
        logManager.start();
        
        // Discord Webhook Manager
        this.discordWebhookManager = new DiscordWebhookManager(this);
        discordWebhookManager.start();

        // Statistics Manager
        this.statisticsManager = new StatisticsManager(this);
        statisticsManager.start();

        // Redis Manager
        this.redisManager = new com.atomguard.manager.RedisManager(this);
        redisManager.start();

        // Verified Player Cache
        this.verifiedPlayerCache = new VerifiedPlayerCache(this);
        verifiedPlayerCache.start();

        // Storage Provider
        try {
            String storageType = configManager.getConfig().getString("database.type", "FLATFILE");
            if ("MYSQL".equalsIgnoreCase(storageType)) {
                String host = configManager.getConfig().getString("database.mysql.host", "localhost");
                int port = configManager.getConfig().getInt("database.mysql.port", 3306);
                String db = configManager.getConfig().getString("database.mysql.database", "atomguard");
                String user = configManager.getConfig().getString("database.mysql.username", "root");
                String pass = configManager.getConfig().getString("database.mysql.password", "");
                boolean ssl = configManager.getConfig().getBoolean("database.mysql.use-ssl", false);

                MySQLStorageProvider provider = new MySQLStorageProvider(this, host, port, db, user, pass, ssl);
                provider.connect();
                this.storageProvider = provider;
                getLogger().info("MySQL bağlantısı başarılı.");
            }
        } catch (Exception e) {
            getLogger().severe("Veritabanı bağlantısı başarısız: " + e.getMessage());
        }

        // Attack Mode Manager
        this.attackModeManager = new AttackModeManager(this);

        // Heuristic Engine
        this.heuristicEngine = new HeuristicEngine(this);

        // Web Panel
        this.webPanel = new WebPanel(this);
        webPanel.start();

        // Reputation Manager
        this.reputationManager = new IPReputationManager(this);

        // Module Manager
        this.moduleManager = new ModuleManager(this);

        // Modülleri kaydet
        registerModules();

        // Modülleri etkinleştir
        moduleManager.enableAllModules();

        // Attack Mode Update Task (Every 5 seconds)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (attackModeManager != null) {
                attackModeManager.update();
            }
        }, 100L, 100L);

        // Public API başlat
        initializeAPI();

        getLogger().info("Tüm manager'lar başlatıldı.");
    }

    /**
     * Listener'ları kaydeder
     */
    private void registerListeners() {
        getLogger().info("Listener'lar kaydediliyor...");

        // PacketEvents Listener
        this.packetListener = new PacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        // Bukkit Listener
        this.bukkitListener = new BukkitListener(this);
        getServer().getPluginManager().registerEvents(bukkitListener, this);

        // Inventory Listener
        this.inventoryListener = new InventoryListener(this);
        getServer().getPluginManager().registerEvents(inventoryListener, this);

        getLogger().info("Listener'lar kaydedildi.");
    }

    /**
     * Komutları kaydeder
     */
    private void registerCommands() {
        getLogger().info("Komutlar kaydediliyor...");

        // /atomguard komutu
        AtomGuardCommand atomGuardCommand = new AtomGuardCommand(this);
        AtomGuardTabCompleter tabCompleter = new AtomGuardTabCompleter(this);

        getCommand("atomguard").setExecutor(atomGuardCommand);
        getCommand("atomguard").setTabCompleter(tabCompleter);

        // /panic komutu
        getCommand("panic").setExecutor(new com.atomguard.command.PanicCommand(this));

        getLogger().info("Komutlar kaydedildi.");
    }

    /**
     * Periyodik temizlik görevini başlatır
     * Modüllerin cleanup() metodlarını çağırarak bellek sızıntısını önler
     */
    private void startCleanupTask() {
        // Her 5 dakikada bir (6000 tick) cleanup çalıştır
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                var offlineModule = moduleManager.getModule(com.atomguard.module.OfflinePacketModule.class);
                if (offlineModule != null) offlineModule.cleanup();

                var exploitModule = moduleManager.getModule(com.atomguard.module.PacketExploitModule.class);
                if (exploitModule != null) exploitModule.cleanup();

                // FrameCrashModule.cleanup() Bukkit API kullanıyor, sync olmalı
                getServer().getScheduler().runTask(this, () -> {
                    var frameModule = moduleManager.getModule(com.atomguard.module.FrameCrashModule.class);
                    if (frameModule != null) frameModule.cleanup();
                });

                var invModule = moduleManager.getModule(com.atomguard.module.InventoryDuplicationModule.class);
                if (invModule != null) invModule.cleanup();

                // TokenBucket oyuncu verisi temizliği (sync gerekli — Bukkit API kullanıyor)
                getServer().getScheduler().runTask(this, () -> {
                    var tokenModule = moduleManager.getModule(com.atomguard.module.TokenBucketModule.class);
                    if (tokenModule != null) tokenModule.cleanup();
                });

            } catch (Exception e) {
                getLogger().warning("Cleanup görevi sırasında hata: " + e.getMessage());
            }
        }, 6000L, 6000L);
    }

    /**
     * PacketEvents'in yüklenip yüklenmediğini kontrol eder
     *
     * @return Yüklü ise true
     */
    private boolean checkPacketEvents() {
        return PacketEvents.getAPI() != null;
    }

    /**
     * Modülleri kaydeder
     */
    private void registerModules() {
        getLogger().info("Modüller kaydediliyor...");

        // Tüm modülleri kaydet
        moduleManager.registerModule(new com.atomguard.module.TooManyBooksModule(this));
        moduleManager.registerModule(new com.atomguard.module.PacketDelayModule(this));
        moduleManager.registerModule(new com.atomguard.module.PacketExploitModule(this));
        moduleManager.registerModule(new com.atomguard.module.CommandsCrashModule(this));
        moduleManager.registerModule(new com.atomguard.module.CreativeItemsModule(this));
        moduleManager.registerModule(new com.atomguard.module.SignCrasherModule(this));
        moduleManager.registerModule(new com.atomguard.module.LecternCrasherModule(this));
        moduleManager.registerModule(new com.atomguard.module.MapLabelCrasherModule(this));
        moduleManager.registerModule(new com.atomguard.module.InvalidSlotModule(this));
        moduleManager.registerModule(new com.atomguard.module.NBTCrasherModule(this));
        moduleManager.registerModule(new com.atomguard.module.BookCrasherModule(this));
        moduleManager.registerModule(new com.atomguard.module.CowDuplicationModule(this));
        moduleManager.registerModule(new com.atomguard.module.DispenserCrasherModule(this));
        moduleManager.registerModule(new com.atomguard.module.OfflinePacketModule(this));
        moduleManager.registerModule(new com.atomguard.module.InventoryDuplicationModule(this));
        moduleManager.registerModule(new com.atomguard.module.MuleDuplicationModule(this));
        moduleManager.registerModule(new com.atomguard.module.PortalBreakModule(this));
        moduleManager.registerModule(new com.atomguard.module.BundleDuplicationModule(this));
        moduleManager.registerModule(new com.atomguard.module.NormalizeCoordinatesModule(this));
        moduleManager.registerModule(new com.atomguard.module.FrameCrashModule(this));
        
        // NEW-01: Chunk Crash Module
        moduleManager.registerModule(new com.atomguard.module.ChunkCrashModule(this));
        
        // NEW-02 -> NEW-05: Ek Güvenlik Modülleri
        moduleManager.registerModule(new com.atomguard.module.AnvilCraftCrashModule(this));
        moduleManager.registerModule(new com.atomguard.module.EntityInteractCrashModule(this));
        moduleManager.registerModule(new com.atomguard.module.ContainerCrashModule(this));
        moduleManager.registerModule(new com.atomguard.module.ComponentCrashModule(this));

        // Yeni güvenlik modülleri
        moduleManager.registerModule(new com.atomguard.module.TokenBucketModule(this));
        moduleManager.registerModule(new com.atomguard.module.AdvancedPayloadModule(this));
        moduleManager.registerModule(new com.atomguard.module.NettyCrashModule(this));
        moduleManager.registerModule(new com.atomguard.module.ItemSanitizerModule(this));
        moduleManager.registerModule(new com.atomguard.module.BundleLockModule(this));
        moduleManager.registerModule(new com.atomguard.module.ShulkerByteModule(this));
        moduleManager.registerModule(new com.atomguard.module.StorageEntityLockModule(this));
        moduleManager.registerModule(new com.atomguard.module.RedstoneLimiterModule(this));
        moduleManager.registerModule(new com.atomguard.module.ViewDistanceMaskModule(this));
        
        // Yeni Gelişmiş Güvenlik Modülleri
        moduleManager.registerModule(new com.atomguard.module.FallingBlockLimiterModule(this));
        moduleManager.registerModule(new com.atomguard.module.ExplosionLimiterModule(this));
        moduleManager.registerModule(new com.atomguard.module.MovementSecurityModule(this));
        moduleManager.registerModule(new com.atomguard.module.VisualCrasherModule(this));
        moduleManager.registerModule(new com.atomguard.module.AdvancedChatModule(this));
        moduleManager.registerModule(new com.atomguard.module.PistonLimiterModule(this));

        // Rapor Gereksinimleri
        moduleManager.registerModule(new com.atomguard.module.SmartLagModule(this));
        moduleManager.registerModule(new com.atomguard.module.DuplicationFixModule(this));

        // Bağlantı Hız Sınırlandırıcı
        moduleManager.registerModule(new com.atomguard.module.ConnectionThrottleModule(this));

        // Gelişmiş AntiBot Modülü
        moduleManager.registerModule(new com.atomguard.module.antibot.AntiBotModule(this));

        getLogger().info("Toplam " + moduleManager.getTotalModuleCount() + " modül kaydedildi.");
    }

    /**
     * Public API'yi başlatır
     */
    private void initializeAPI() {
        new AtomGuardAPI(
                moduleManager,
                storageProvider,
                statisticsManager,
                reputationManager,
                BuildInfo.getFullVersion()
        );
        getLogger().info("Atom Guard API v" + BuildInfo.getFullVersion() + " başlatıldı.");
    }

    /**
     * ASCII Art banner yazdırır
     */
    private void printBanner() {
        for (String line : BuildInfo.getBanner().split("\n")) {
            getLogger().info(line);
        }
    }

    // ═══════════════════════════════════════
    // Getter Metodları
    // ═══════════════════════════════════════

    /**
     * Singleton instance alır
     *
     * @return Plugin instance
     */
    @NotNull
    public static AtomGuard getInstance() {
        return instance;
    }

    /**
     * ConfigManager alır
     *
     * @return ConfigManager instance
     */
    @NotNull
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * MessageManager alır
     *
     * @return MessageManager instance
     */
    @NotNull
    public MessageManager getMessageManager() {
        return messageManager;
    }

    /**
     * LogManager alır
     *
     * @return LogManager instance
     */
    @NotNull
    public LogManager getLogManager() {
        return logManager;
    }

    /**
     * ModuleManager alır
     *
     * @return ModuleManager instance
     */
    @NotNull
    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    /**
     * PacketListener alır
     *
     * @return PacketListener instance
     */
    @NotNull
    public PacketListener getPacketListener() {
        return packetListener;
    }

    /**
     * BukkitListener alır
     *
     * @return BukkitListener instance
     */
    @NotNull
    public BukkitListener getBukkitListener() {
        return bukkitListener;
    }

    /**
     * InventoryListener alır
     *
     * @return InventoryListener instance
     */
    @NotNull
    public InventoryListener getInventoryListener() {
        return inventoryListener;
    }

    /**
     * HeuristicEngine alır
     *
     * @return HeuristicEngine instance
     */
    @NotNull
    public HeuristicEngine getHeuristicEngine() {
        return heuristicEngine;
    }

    /**
     * IPReputationManager alır
     *
     * @return IPReputationManager instance
     */
    @NotNull
    public IPReputationManager getReputationManager() {
        return reputationManager;
    }

    /**
     * AttackModeManager alır
     *
     * @return AttackModeManager instance
     */
    @NotNull
    public AttackModeManager getAttackModeManager() {
        return attackModeManager;
    }

    /**
     * DiscordWebhookManager alır
     *
     * @return DiscordWebhookManager instance
     */
    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    /**
     * StatisticsManager alır
     *
     * @return StatisticsManager instance
     */
    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    /**
     * RedisManager alır
     *
     * @return RedisManager instance
     */
    public com.atomguard.manager.RedisManager getRedisManager() {
        return redisManager;
    }

    /**
     * VerifiedPlayerCache alır
     *
     * @return VerifiedPlayerCache instance
     */
    public VerifiedPlayerCache getVerifiedPlayerCache() {
        return verifiedPlayerCache;
    }

    /**
     * WebPanel alır
     *
     * @return WebPanel instance
     */
    public WebPanel getWebPanel() {
        return webPanel;
    }
}
