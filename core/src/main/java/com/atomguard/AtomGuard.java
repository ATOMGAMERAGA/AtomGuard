package com.atomguard;

import com.atomguard.api.AtomGuardAPI;
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
import com.atomguard.notification.NotificationManager;
import com.atomguard.notification.NotificationType;
import com.atomguard.notification.provider.DiscordProvider;
import com.atomguard.notification.provider.TelegramProvider;
import com.atomguard.notification.provider.SlackProvider;
import com.atomguard.util.ExecutorManager;
import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.honeypot.HoneypotModule;
import com.atomguard.reputation.IPReputationManager;
import com.atomguard.trust.TrustScoreManager;
import com.atomguard.web.WebPanel;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Main plugin singleton and lifecycle orchestrator for AtomGuard.
 *
 * <p>This class serves as the central entry point for the Paper plugin. It manages
 * the full lifecycle through {@code onLoad()} (PacketEvents API init), {@code onEnable()}
 * (manager/listener/module bootstrap and public API initialization), and {@code onDisable()}
 * (graceful teardown in reverse order). All managers, the module system, storage, and
 * the public {@link com.atomguard.api.AtomGuardAPI} are wired and accessed through this class
 * via {@code AtomGuard.getInstance()}.
 *
 * <p><b>Critical initialization order:</b> The {@link com.atomguard.listener.PacketListener}
 * must be created before modules are registered, because modules call
 * {@code registerReceiveHandler()} during their {@code onEnable()}.
 *
 * @see com.atomguard.api.AtomGuardAPI
 * @see com.atomguard.manager.ModuleManager
 */
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
    private NotificationManager notificationManager;
    private ExecutorManager executorManager;
    private com.atomguard.metrics.CoreMetrics coreMetrics;
    private AuthListener authListener;
    private boolean hasAuthPlugin;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        try {
            // Managers
            this.executorManager = new ExecutorManager();
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
            this.coreMetrics = new com.atomguard.metrics.CoreMetrics(this);

            // Initialize Managers
            logManager.start();
            redisManager.start();
            statisticsManager.start();
            verifiedPlayerCache.start();
            trustScoreManager.start();
            forensicsManager.start();
            intelligenceEngine.start();
            if (coreMetrics != null) coreMetrics.start();

            // ÖNCE PacketListener oluştur — modüller onEnable()'da registerReceiveHandler() çağırdığından
            this.packetListener = new PacketListener(this);
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);

            // SONRA modülleri kaydet ve etkinleştir
            registerModules();
            moduleManager.enableAllModules();
            
            getServer().getPluginManager().registerEvents(new BukkitListener(this), this);
            getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

            // Generic auth listener — sadece bilinen auth plugini varsa aktif
            if (hasAuthPlugin) {
                this.authListener = new AuthListener(this);
                getServer().getPluginManager().registerEvents(authListener, this);
                getLogger().info("Generic auth listener active.");
            }

            // Messaging
            if (getConfig().getBoolean("messaging.enabled", true)) {
                getServer().getMessenger().registerOutgoingPluginChannel(this, "atomguard:main");
                getServer().getMessenger().registerIncomingPluginChannel(this, "atomguard:main", new CoreMessagingListener(this));
                getServer().getMessenger().registerOutgoingPluginChannel(this, "atomguard:auth");
            }

            // PlaceholderAPI
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new com.atomguard.util.AtomGuardPlaceholderExpansion(this).register();
                getLogger().info("PlaceholderAPI integration active.");
            }

            // Discord Webhook
            discordWebhookManager.start();

            // Notification Manager
            this.notificationManager = new NotificationManager(this);
            initializeNotificationProviders();
            notificationManager.start();

            // Periyodik görev: saldırı modunu otomatik kapat
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> attackModeManager.update(), 20L, 20L); // her saniye kontrol

            // Periyodik cleanup görevi: bellek sızıntılarını önle
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                moduleManager.getAllModules().forEach(m -> {
                    try { m.cleanup(); } catch (Exception e) {
                        getLogger().warning("Cleanup error (" + m.getName() + "): " + e.getMessage());
                    }
                });
                if (heuristicEngine != null) heuristicEngine.cleanupOfflinePlayers();
            }, 6000L, 6000L); // 5 dakikada bir

            // Web Panel
            if (getConfig().getBoolean("web-panel.enabled", false)) {
                this.webPanel = new com.atomguard.web.WebPanel(this);
                webPanel.start();
                getLogger().info("Web Panel başlatıldı: port " + getConfig().getInt("web-panel.port", 8080));
            }

            // Commands
            getCommand("atomguard").setExecutor(new AtomGuardCommand(this));
            getCommand("atomguard").setTabCompleter(new AtomGuardTabCompleter(this));
            getCommand("panic").setExecutor(new PanicCommand(this));

            // API initialization
            initializeAPI();

            getLogger().info("AtomGuard (Core) has been enabled!");

        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "AtomGuard failed to start", e);
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
            getLogger().info("Storage provider initialized: " + storageProvider.getTypeName());
        }
    }

    private void initializeAPI() {
        new AtomGuardAPI(
            moduleManager,
            storageProvider,
            statisticsManager,
            reputationManager,
            trustScoreManager,
            forensicsManager,
            null, // connectionPipeline — not yet implemented in core
            getDescription().getVersion()
        );
        getLogger().info("AtomGuard API v" + getDescription().getVersion() + " initialized.");
    }

    @Override
    public void onDisable() {
        // 0. API shutdown
        AtomGuardAPI.shutdown();

        // 1. ÖNCE modülleri kapat (modüller veri kaydetsin)
        if (moduleManager != null) moduleManager.disableAllModules();

        // 2. Manager'ları kapat (verileri diske yazsınlar)
        if (intelligenceEngine != null) intelligenceEngine.stop();
        if (coreMetrics != null) coreMetrics.stop();
        if (forensicsManager != null) forensicsManager.stop();
        if (trustScoreManager != null) trustScoreManager.stop();
        if (reputationManager != null) reputationManager.shutdown();
        if (verifiedPlayerCache != null) verifiedPlayerCache.stop();
        if (statisticsManager != null) statisticsManager.stop();
        if (notificationManager != null) notificationManager.stop();
        if (discordWebhookManager != null) discordWebhookManager.stop();
        if (redisManager != null) redisManager.stop();
        if (executorManager != null) executorManager.shutdown();
        if (webPanel != null) webPanel.stop();

        // 3. PacketEvents listener temizliği
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }

        // 4. Messenger channel temizliği
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);

        // 5. EN SON storage ve log kapat
        if (storageProvider != null) storageProvider.disconnect();
        if (logManager != null) logManager.stop();

        getLogger().info("AtomGuard (Core) has been disabled.");
    }

    private void initializeNotificationProviders() {
        // Discord
        if (getConfig().getBoolean("notifications.discord.enabled", false)) {
            DiscordProvider discord = new DiscordProvider(this);
            java.util.Set<NotificationType> types = parseNotificationTypes(
                    getConfig().getStringList("notifications.discord.types"));
            notificationManager.registerProvider(discord, types);
        }
        // Telegram
        if (getConfig().getBoolean("notifications.telegram.enabled", false)) {
            TelegramProvider telegram = new TelegramProvider(this);
            java.util.Set<NotificationType> types = parseNotificationTypes(
                    getConfig().getStringList("notifications.telegram.types"));
            notificationManager.registerProvider(telegram, types);
        }
        // Slack
        if (getConfig().getBoolean("notifications.slack.enabled", false)) {
            SlackProvider slack = new SlackProvider(this);
            java.util.Set<NotificationType> types = parseNotificationTypes(
                    getConfig().getStringList("notifications.slack.types"));
            notificationManager.registerProvider(slack, types);
        }
    }

    private java.util.Set<NotificationType> parseNotificationTypes(java.util.List<String> typeNames) {
        java.util.Set<NotificationType> types = java.util.EnumSet.noneOf(NotificationType.class);
        for (String name : typeNames) {
            try {
                types.add(NotificationType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Unknown notification type: " + name);
            }
        }
        return types;
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
        moduleManager.registerModule(new AdvancedChatModule(this));
        // Auth plugin kontrolü — yoksa OfflinePacketModule gereksiz ve timeout'a yol açar
        this.hasAuthPlugin = getServer().getPluginManager().getPlugin("AuthMe") != null
                || getServer().getPluginManager().getPlugin("nLogin") != null
                || getServer().getPluginManager().getPlugin("OpeNLogin") != null
                || getServer().getPluginManager().getPlugin("LoginSecurity") != null
                || getServer().getPluginManager().getPlugin("JPremium") != null
                || getServer().getPluginManager().getPlugin("FastLogin") != null
                || getServer().getPluginManager().getPlugin("LimboAuth") != null
                || getConfig().getBoolean("modules.offline-packet.zorla-aktif", false);

        if (hasAuthPlugin) {
            moduleManager.registerModule(new OfflinePacketModule(this));
        } else {
            getLogger().warning("[AtomGuard] Bilinen auth plugini bulunamadi — OfflinePacketModule devre disi birakildi.");
            getLogger().warning("[AtomGuard] Zorla acmak icin config.yml: modules.offline-packet.zorla-aktif: true");
        }
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
    public NotificationManager getNotificationManager() { return notificationManager; }
    public ExecutorManager getExecutorManager() { return executorManager; }
    public com.atomguard.metrics.CoreMetrics getCoreMetrics() { return coreMetrics; }
    public AuthListener getAuthListener() { return authListener; }
}