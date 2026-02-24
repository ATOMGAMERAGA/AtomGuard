package com.atomguard;

import com.atomguard.command.AtomGuardCommand;
import com.atomguard.command.AtomGuardTabCompleter;
import com.atomguard.command.PanicCommand;
import com.atomguard.data.VerifiedPlayerCache;
import com.atomguard.forensics.ForensicsManager;
import com.atomguard.heuristic.HeuristicEngine;
import com.atomguard.intelligence.TrafficIntelligenceEngine;
import com.atomguard.listener.*;
import com.atomguard.manager.*;
import com.atomguard.migration.ConfigMigrationManager;
import com.atomguard.module.*;
import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.honeypot.HoneypotModule;
import com.atomguard.reputation.IPReputationManager;
import com.atomguard.trust.TrustScoreManager;
import com.atomguard.web.WebPanel;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class AtomGuard extends JavaPlugin {

    private static AtomGuard instance;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private LogManager logManager;
    private ModuleManager moduleManager;
    private StatisticsManager statisticsManager;
    private RedisManager redisManager;
    private AttackModeManager attackModeManager;
    private DiscordWebhookManager discordWebhookManager;
    private IPReputationManager reputationManager;
    private HeuristicEngine heuristicEngine;
    private VerifiedPlayerCache verifiedPlayerCache;
    private PacketListener packetListener;
    private com.atomguard.web.WebPanel webPanel;

    private com.atomguard.api.storage.IStorageProvider storageProvider;
    private TrustScoreManager trustScoreManager;
    private ForensicsManager forensicsManager;
    private TrafficIntelligenceEngine intelligenceEngine;
    private ConfigMigrationManager migrationManager;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        try {
            // Managers
            this.configManager = new ConfigManager(this);
            configManager.load(); // Config diğer manager'lardan önce yüklenmeli
            this.messageManager = new MessageManager(this);

            // Initialize Storage Provider
            initializeStorage();

            this.logManager = new LogManager(this);
            this.statisticsManager = new StatisticsManager(this);
            this.redisManager = new RedisManager(this);
            this.attackModeManager = new AttackModeManager(this);
            this.discordWebhookManager = new DiscordWebhookManager(this);
            this.moduleManager = new ModuleManager(this);
            this.reputationManager = new IPReputationManager(this);
            this.heuristicEngine = new HeuristicEngine(this);
            this.verifiedPlayerCache = new VerifiedPlayerCache(this);
            this.migrationManager = new ConfigMigrationManager(this);
            this.trustScoreManager = new TrustScoreManager(this);
            this.forensicsManager = new ForensicsManager(this);
            this.intelligenceEngine = new TrafficIntelligenceEngine(this);

            // Initialize Managers
            logManager.start();
            redisManager.start();
            statisticsManager.start();
            verifiedPlayerCache.start();
            trustScoreManager.start();
            forensicsManager.start();
            intelligenceEngine.start();

            // Register Modules BEFORE enabling them
            registerModules();
            moduleManager.enableAllModules();

            // Listeners
            this.packetListener = new PacketListener(this);
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);
            
            getServer().getPluginManager().registerEvents(new BukkitListener(this), this);
            getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

            if (getServer().getPluginManager().getPlugin("AuthMe") != null) {
                getServer().getPluginManager().registerEvents(new AuthListener(this), this);
                getLogger().info("AuthMe entegrasyonu aktif.");
            } else {
                getLogger().info("AuthMe bulunamadı, auth entegrasyonu devre dışı.");
            }

            // Messaging
            if (getConfig().getBoolean("mesajlasma.aktif", true)) {
                getServer().getMessenger().registerOutgoingPluginChannel(this, "atomguard:main");
                getServer().getMessenger().registerIncomingPluginChannel(this, "atomguard:main", new CoreMessagingListener(this));
                getServer().getMessenger().registerOutgoingPluginChannel(this, "atomguard:auth");
            }

            // PlaceholderAPI
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new com.atomguard.util.AtomGuardPlaceholderExpansion(this).register();
                getLogger().info("PlaceholderAPI entegrasyonu aktif.");
            }

            // Discord Webhook
            discordWebhookManager.start();

            // Periyodik görev: saldırı modunu otomatik kapat
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> attackModeManager.update(), 20L, 20L); // her saniye kontrol

            // Web Panel
            if (getConfig().getBoolean("web-panel.aktif", false)) {
                this.webPanel = new com.atomguard.web.WebPanel(this);
                webPanel.start();
                getLogger().info("Web Panel başlatıldı: port " + getConfig().getInt("web-panel.port", 8080));
            }

            // Commands
            getCommand("atomguard").setExecutor(new AtomGuardCommand(this));
            getCommand("atomguard").setTabCompleter(new AtomGuardTabCompleter(this));
            getCommand("panic").setExecutor(new PanicCommand(this));

            getLogger().info("AtomGuard (Core) has been enabled!");

        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "AtomGuard başlatılamadı", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeStorage() throws Exception {
        String type = getConfig().getString("database.type", "FLATFILE").toUpperCase();
        
        switch (type) {
            case "MYSQL":
                this.storageProvider = new com.atomguard.storage.MySQLStorageProvider(this,
                    getConfig().getString("database.mysql.host", "localhost"),
                    getConfig().getInt("database.mysql.port", 3306),
                    getConfig().getString("database.mysql.database", "atomguard"),
                    getConfig().getString("database.mysql.username", "root"),
                    getConfig().getString("database.mysql.password", ""),
                    getConfig().getBoolean("database.mysql.use-ssl", false)
                );
                break;
            case "SQLITE":
                this.storageProvider = new com.atomguard.storage.SQLiteStorageProvider(this);
                break;
            default:
                // FLATFILE implementation (JSON) - already handled by individual managers
                // but we might want a unified one later.
                break;
        }

        if (storageProvider != null) {
            storageProvider.connect();
            getLogger().info("Depolama sağlayıcısı başlatıldı: " + storageProvider.getTypeName());
        }
    }

    @Override
    public void onDisable() {
        if (intelligenceEngine != null) intelligenceEngine.stop();
        if (forensicsManager != null) forensicsManager.stop();
        if (trustScoreManager != null) trustScoreManager.stop();
        if (storageProvider != null) storageProvider.disconnect();
        if (webPanel != null) webPanel.stop();
        if (moduleManager != null) moduleManager.disableAllModules();
        if (reputationManager != null) reputationManager.shutdown();
        if (verifiedPlayerCache != null) verifiedPlayerCache.stop();
        if (redisManager != null) redisManager.stop();
        if (statisticsManager != null) statisticsManager.stop();
        if (logManager != null) logManager.stop();
        
        getLogger().info("AtomGuard (Core) has been disabled.");
    }

    private void registerModules() {
        moduleManager.registerModule(new NBTCrasherModule(this));
        moduleManager.registerModule(new AntiBotModule(this));
        moduleManager.registerModule(new SmartLagModule(this));
        moduleManager.registerModule(new PacketExploitModule(this));
        moduleManager.registerModule(new NettyCrashModule(this));
        moduleManager.registerModule(new InvalidSlotModule(this));
        moduleManager.registerModule(new ShulkerByteModule(this));
        moduleManager.registerModule(new ConnectionThrottleModule(this));
        moduleManager.registerModule(new PortalBreakModule(this));
        moduleManager.registerModule(new ComponentCrashModule(this));
        moduleManager.registerModule(new PistonLimiterModule(this));
        moduleManager.registerModule(new AdvancedPayloadModule(this));
        moduleManager.registerModule(new BundleLockModule(this));
        moduleManager.registerModule(new CommandsCrashModule(this));
        moduleManager.registerModule(new BotProtectionModule(this));
        moduleManager.registerModule(new StorageEntityLockModule(this));
        moduleManager.registerModule(new TooManyBooksModule(this));
        moduleManager.registerModule(new ContainerCrashModule(this));
        moduleManager.registerModule(new DispenserCrasherModule(this));
        moduleManager.registerModule(new TokenBucketModule(this));
        moduleManager.registerModule(new LecternCrasherModule(this));
        moduleManager.registerModule(new AnvilCraftCrashModule(this));
        moduleManager.registerModule(new NormalizeCoordinatesModule(this));
        moduleManager.registerModule(new InventoryDuplicationModule(this));
        moduleManager.registerModule(new PacketDelayModule(this));
        moduleManager.registerModule(new CowDuplicationModule(this));
        moduleManager.registerModule(new MovementSecurityModule(this));
        moduleManager.registerModule(new SignCrasherModule(this));
        moduleManager.registerModule(new VisualCrasherModule(this));
        moduleManager.registerModule(new ChunkCrashModule(this));
        moduleManager.registerModule(new CustomPayloadModule(this));
        moduleManager.registerModule(new AdvancedChatModule(this));
        moduleManager.registerModule(new OfflinePacketModule(this));
        moduleManager.registerModule(new CreativeItemsModule(this));
        moduleManager.registerModule(new DuplicationFixModule(this));
        moduleManager.registerModule(new FallingBlockLimiterModule(this));
        moduleManager.registerModule(new ExplosionLimiterModule(this));
        moduleManager.registerModule(new FrameCrashModule(this));
        moduleManager.registerModule(new BundleDuplicationModule(this));
        moduleManager.registerModule(new ViewDistanceMaskModule(this));
        moduleManager.registerModule(new EntityInteractCrashModule(this));
        moduleManager.registerModule(new ItemSanitizerModule(this));
        moduleManager.registerModule(new RedstoneLimiterModule(this));
        moduleManager.registerModule(new MuleDuplicationModule(this));
        moduleManager.registerModule(new BookCrasherModule(this));
        moduleManager.registerModule(new MapLabelCrasherModule(this));
        moduleManager.registerModule(new HoneypotModule(this));
    }

    public static AtomGuard getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public LogManager getLogManager() { return logManager; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public StatisticsManager getStatisticsManager() { return statisticsManager; }
    public RedisManager getRedisManager() { return redisManager; }
    public AttackModeManager getAttackModeManager() { return attackModeManager; }
    public DiscordWebhookManager getDiscordWebhookManager() { return discordWebhookManager; }
    public IPReputationManager getReputationManager() { return reputationManager; }
    public HeuristicEngine getHeuristicEngine() { return heuristicEngine; }
    public VerifiedPlayerCache getVerifiedPlayerCache() { return verifiedPlayerCache; }
    public PacketListener getPacketListener() { return packetListener; }
    public com.atomguard.web.WebPanel getWebPanel() { return webPanel; }
    public com.atomguard.api.storage.IStorageProvider getStorageProvider() { return storageProvider; }
    public TrustScoreManager getTrustScoreManager() { return trustScoreManager; }
    public ForensicsManager getForensicsManager() { return forensicsManager; }
    public TrafficIntelligenceEngine getIntelligenceEngine() { return intelligenceEngine; }
    public ConfigMigrationManager getMigrationManager() { return migrationManager; }
}