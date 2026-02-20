package com.atomguard.velocity;

import com.atomguard.api.AtomGuardAPI;
import com.atomguard.velocity.adapter.VelocityModuleManagerAdapter;
import com.atomguard.velocity.adapter.VelocityReputationAdapter;
import com.atomguard.velocity.adapter.VelocityStatisticsAdapter;
import com.atomguard.velocity.command.AtomGuardVelocityCommand;
import com.atomguard.velocity.communication.BackendCommunicator;
import com.atomguard.velocity.config.VelocityConfigManager;
import com.atomguard.velocity.config.VelocityMessageManager;
import com.atomguard.velocity.event.VelocityEventBus;
import com.atomguard.velocity.listener.*;
import com.atomguard.velocity.manager.*;
import com.atomguard.velocity.module.antiddos.DDoSProtectionModule;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import com.atomguard.velocity.module.firewall.FirewallModule;
import com.atomguard.velocity.module.ratelimit.GlobalRateLimitModule;
import com.atomguard.velocity.module.protocol.ProtocolValidationModule;
import com.atomguard.velocity.module.exploit.ProxyExploitModule;
import com.atomguard.velocity.storage.VelocityStorageProvider;

// New Modules
import com.atomguard.velocity.module.iptables.IPTablesModule;
import com.atomguard.velocity.module.firewall.AccountFirewallModule;
import com.atomguard.velocity.module.geo.CountryFilterModule;
import com.atomguard.velocity.module.fastchat.FastChatModule;
import com.atomguard.velocity.module.reconnect.ReconnectControlModule;
import com.atomguard.velocity.module.auth.PasswordCheckModule;
import com.atomguard.velocity.module.latency.LatencyCheckModule;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "atomguard-velocity",
    name = "AtomGuard Velocity",
    version = "1.0.0",
    description = "Kurumsal Velocity proxy güvenlik sistemi",
    authors = {"AtomGuard Team"}
)
public class AtomGuardVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private static AtomGuardVelocity instance;

    private VelocityConfigManager configManager;
    private VelocityMessageManager messageManager;
    private VelocityLogManager logManager;
    private VelocityStatisticsManager statisticsManager;
    private VelocityAlertManager alertManager;
    private VelocityModuleManager moduleManager;
    private BackendCommunicator backendCommunicator;
    private VelocityStorageProvider storageProvider;
    private VelocityEventBus eventBus;
    private com.atomguard.velocity.audit.AuditLogger auditLogger;
    private com.atomguard.velocity.pipeline.ConnectionPipeline connectionPipeline;
    private AttackAnalyticsManager attackAnalyticsManager;
    private com.atomguard.velocity.metrics.PrometheusExporter prometheusExporter;
    private AttackModeManager attackModeManager;
    private BehaviorManager behaviorManager;
    private com.atomguard.velocity.data.ConnectionHistory connectionHistory;
    private volatile boolean dataLoaded = false;

    // Modüller
    private DDoSProtectionModule ddosModule;
    private VelocityAntiBotModule antiBotModule;
    private VPNDetectionModule vpnModule;
    private FirewallModule firewallModule;
    private GlobalRateLimitModule rateLimitModule;
    private ProtocolValidationModule protocolModule;
    private ProxyExploitModule exploitModule;
    
    // New Modules
    private IPTablesModule ipTablesModule;
    private AccountFirewallModule accountFirewallModule;
    private CountryFilterModule countryFilterModule;
    private FastChatModule fastChatModule;
    private ReconnectControlModule reconnectControlModule;
    private PasswordCheckModule passwordCheckModule;
    private LatencyCheckModule latencyCheckModule;

    private volatile boolean attackMode = false;
    private volatile long attackModeStartTime = 0;

    @Inject
    public AtomGuardVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    public static AtomGuardVelocity getInstance() { return instance; }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            initializeManagers();
            registerModules();
            moduleManager.enableAll();
            registerListeners();
            registerCommands();
            initializeCommunication();
            initializeAPI();
            startCleanupScheduler();
            loadDatabaseData();

            // İstatistikleri periyodik kaydet
            server.getScheduler()
                .buildTask(this, statisticsManager::save)
                .repeat(5, TimeUnit.MINUTES)
                .schedule();

            VelocityBuildInfo.printBanner(logger, moduleManager.getAll().size());
            logManager.log("AtomGuard Velocity başlatıldı. " + VelocityBuildInfo.getStartupInfo());
        } catch (Exception e) {
            logger.error("AtomGuard Velocity başlatılamadı!", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("AtomGuard Velocity kapatılıyor...");
        
        // Save current attack snapshot if active
        if (attackMode && attackAnalyticsManager != null) {
            attackAnalyticsManager.onAttackEnd();
        }

        // Persist bans and verified players
        if (storageProvider != null && storageProvider.isConnected()) {
            if (firewallModule != null) {
                firewallModule.getTempBanManager().getBannedIPs().forEach(ip -> {
                    long remaining = firewallModule.getTempBanManager().getRemainingMs(ip);
                    String reason = firewallModule.getTempBanManager().getBanReason(ip);
                    storageProvider.saveBlockedIP(ip, reason != null ? reason : "shutdown-persist", System.currentTimeMillis() + remaining).join();
                });
            }
        }

        // Persist behavioral profiles
        if (behaviorManager != null) {
            behaviorManager.getAllProfiles().forEach(profile -> 
                storageProvider.saveBehaviorProfile(profile));
        }

        if (moduleManager != null) moduleManager.disableAll();
        if (backendCommunicator != null) backendCommunicator.shutdown();
        if (statisticsManager != null) statisticsManager.save();
        if (auditLogger != null) auditLogger.shutdown();
        if (prometheusExporter != null) prometheusExporter.stop();
        if (storageProvider != null) storageProvider.disconnect();
        if (logManager != null) logManager.shutdown();
        
        AtomGuardAPI.shutdown();
    }

    private void initializeManagers() throws Exception {
        configManager = new VelocityConfigManager(dataDirectory, logger);
        configManager.load();
        configManager.validateAndMigrate();

        messageManager = new VelocityMessageManager(dataDirectory, logger);
        messageManager.load(configManager.getString("dil", "tr"));

        logManager = new VelocityLogManager(dataDirectory, logger);
        logManager.initialize();

        statisticsManager = new VelocityStatisticsManager(dataDirectory, logger);
        statisticsManager.load();

        alertManager = new VelocityAlertManager(server, logger);
        String webhookUrl = configManager.getString("discord-webhook.webhook-url", "");
        boolean discordEnabled = configManager.getBoolean("discord-webhook.aktif", false);
        alertManager.configure(webhookUrl, discordEnabled);

        storageProvider = new VelocityStorageProvider(this);
        try {
            storageProvider.connect();
        } catch (Exception e) {
            logger.error("Depolama bağlantısı kurulamadı: {}", e.getMessage());
        }

        auditLogger = new com.atomguard.velocity.audit.AuditLogger(storageProvider);
        connectionPipeline = new com.atomguard.velocity.pipeline.ConnectionPipeline();
        moduleManager = new VelocityModuleManager(logger);
        eventBus = new VelocityEventBus(this);
        attackAnalyticsManager = new AttackAnalyticsManager(this);
        attackModeManager = new AttackModeManager(this);
        behaviorManager = new BehaviorManager(this);
        connectionHistory = new com.atomguard.velocity.data.ConnectionHistory();

        if (configManager.getBoolean("metrikler.prometheus.aktif", false)) {
            prometheusExporter = new com.atomguard.velocity.metrics.PrometheusExporter(this);
            try {
                prometheusExporter.start(configManager.getInt("metrikler.prometheus.port", 9225));
            } catch (Exception e) {
                logger.error("Prometheus metrik sunucusu başlatılamadı: {}", e.getMessage());
            }
        }
    }

    private void initializeAPI() {
        VelocityModuleManagerAdapter moduleAdapter = new VelocityModuleManagerAdapter(moduleManager);
        VelocityStatisticsAdapter statsAdapter = new VelocityStatisticsAdapter(statisticsManager);
        VelocityReputationAdapter reputationAdapter = new VelocityReputationAdapter(this);

        new AtomGuardAPI(moduleAdapter, storageProvider, statsAdapter, reputationAdapter, "1.0.0");
        logger.info("AtomGuard API başlatıldı (Velocity).");
    }

    private void startCleanupScheduler() {
        server.getScheduler().buildTask(this, () -> {
            logger.debug("Periyodik bellek temizliği başlatıldı...");
            
            if (behaviorManager != null) behaviorManager.cleanup();
            if (rateLimitModule != null) rateLimitModule.cleanup();
            if (reconnectControlModule != null) reconnectControlModule.cleanup();
            
            logger.info("Periyodik sistem bakımı ve bellek temizliği tamamlandı.");
        }).repeat(10, java.util.concurrent.TimeUnit.MINUTES).schedule();
    }

    private void loadDatabaseData() {
        if (storageProvider == null || !storageProvider.isConnected()) return;

        // 1. Banları Yükle
        storageProvider.loadActiveBansWithExpiry().thenAccept(bans -> {
            if (firewallModule != null && bans != null) {
                bans.forEach((ip, expiry) -> {
                    long duration = expiry - System.currentTimeMillis();
                    if (duration > 0 || expiry == 0) {
                        firewallModule.getTempBanManager().ban(ip, duration > 0 ? duration : 31536000000L, "DB-Restore");
                    }
                });
                logger.info("Veritabanından {} aktif yasak yüklendi.", bans.size());
            }
        });

        // 2. Doğrulanmış Oyuncuları Yükle
        storageProvider.loadVerifiedPlayers().thenAccept(ips -> {
            if (antiBotModule != null && ips != null) {
                ips.forEach(ip -> antiBotModule.markVerified(ip));
                logger.info("Veritabanından {} doğrulanmış oyuncu yüklendi.", ips.size());
            }
        });

        // 3. Davranış Profillerini Yükle
        if (behaviorManager != null) behaviorManager.loadFromDatabase();
        
        dataLoaded = true;
        logger.info("AtomGuard güvenlik verileri tamamen yüklendi ve koruma %100 kapasiteye ulaştı.");
    }

    public boolean isDataLoaded() { return dataLoaded; }

    private void registerModules() {
        ddosModule = new DDoSProtectionModule(this);
        antiBotModule = new VelocityAntiBotModule(this);
        vpnModule = new VPNDetectionModule(this);
        firewallModule = new FirewallModule(this);
        rateLimitModule = new GlobalRateLimitModule(this);
        protocolModule = new ProtocolValidationModule(this);
        exploitModule = new ProxyExploitModule(this);
        
        // Initialize New Modules
        ipTablesModule = new IPTablesModule(this);
        accountFirewallModule = new AccountFirewallModule(this);
        countryFilterModule = new CountryFilterModule(this);
        fastChatModule = new FastChatModule(this);
        reconnectControlModule = new ReconnectControlModule(this);
        passwordCheckModule = new PasswordCheckModule(this);
        latencyCheckModule = new LatencyCheckModule(this);

        moduleManager.register(firewallModule);    // Önce güvenlik duvarı
        moduleManager.register(rateLimitModule);
        moduleManager.register(ddosModule);
        moduleManager.register(protocolModule);
        moduleManager.register(antiBotModule);
        moduleManager.register(vpnModule);
        moduleManager.register(exploitModule);
        
        // Register New Modules
        moduleManager.register(ipTablesModule);
        moduleManager.register(accountFirewallModule);
        moduleManager.register(countryFilterModule);
        moduleManager.register(fastChatModule);
        moduleManager.register(reconnectControlModule);
        moduleManager.register(passwordCheckModule);
        moduleManager.register(latencyCheckModule);

        // Register Pipeline Checks
        connectionPipeline.addCheck(new com.atomguard.velocity.pipeline.ProtocolCheck(this));
        connectionPipeline.addCheck(new com.atomguard.velocity.pipeline.FirewallCheck(this));
        connectionPipeline.addCheck(new com.atomguard.velocity.pipeline.TrustScoreCheck(this));
        connectionPipeline.addCheck(new com.atomguard.velocity.pipeline.RateLimitCheck(this));
        connectionPipeline.addCheck(new com.atomguard.velocity.pipeline.DDoSCheck(this));
        connectionPipeline.addCheck(new com.atomguard.velocity.pipeline.AccountFirewallCheck(this));
        connectionPipeline.addCheck(new com.atomguard.velocity.pipeline.AntiBotCheck(this));
        connectionPipeline.addCheck(new com.atomguard.velocity.pipeline.VPNCheck(this));
    }

    private void registerListeners() {
        com.velocitypowered.api.event.EventManager em = server.getEventManager();
        em.register(this, new ConnectionListener(this));
        em.register(this, new ProxyPingListener(this));
        em.register(this, new ServerSwitchListener(this));
        em.register(this, new ChatListener(this));
        em.register(this, new PluginMessageListener(this));
    }

    private void registerCommands() {
        CommandMeta meta = server.getCommandManager().metaBuilder("agv")
            .aliases("atomguard-velocity", "agvelocity")
            .build();
        server.getCommandManager().register(meta, new AtomGuardVelocityCommand(this));
    }

    private void initializeCommunication() {
        backendCommunicator = new BackendCommunicator(this);
        backendCommunicator.initialize();
    }

    // Attack mode
    public boolean isAttackMode() { return attackMode; }

    public void setAttackMode(boolean state) {
        setAttackMode(state, 0);
    }

    public void setAttackMode(boolean state, int triggerRate) {
        if (this.attackMode == state) return;
        this.attackMode = state;
        if (state) {
            attackModeStartTime = System.currentTimeMillis();
            logManager.warn("SALDIRI MODU AKTİF!");
            if (attackAnalyticsManager != null) {
                attackAnalyticsManager.onAttackStart(triggerRate);
            }
        } else {
            logManager.log("Saldırı modu sona erdi.");
            if (attackAnalyticsManager != null) {
                attackAnalyticsManager.onAttackEnd();
            }
        }
        // Backend'e bildir
        if (backendCommunicator != null) backendCommunicator.broadcastAttackMode(state);
    }

    public long getAttackModeStartTime() { return attackModeStartTime; }

    // Getters
    public ProxyServer getProxyServer() { return server; }
    public Logger getSlf4jLogger() { return logger; }
    public Path getDataDirectory() { return dataDirectory; }
    public VelocityConfigManager getConfigManager() { return configManager; }
    public VelocityMessageManager getMessageManager() { return messageManager; }
    public VelocityLogManager getLogManager() { return logManager; }
    public VelocityStatisticsManager getStatisticsManager() { return statisticsManager; }
    public VelocityAlertManager getAlertManager() { return alertManager; }
    public VelocityModuleManager getModuleManager() { return moduleManager; }
    public BackendCommunicator getBackendCommunicator() { return backendCommunicator; }
    public VelocityStorageProvider getStorageProvider() { return storageProvider; }
    public VelocityEventBus getEventBus() { return eventBus; }
    public com.atomguard.velocity.audit.AuditLogger getAuditLogger() { return auditLogger; }
    public com.atomguard.velocity.pipeline.ConnectionPipeline getConnectionPipeline() { return connectionPipeline; }
    public AttackAnalyticsManager getAttackAnalyticsManager() { return attackAnalyticsManager; }
    public AttackModeManager getAttackModeManager() { return attackModeManager; }
    public BehaviorManager getBehaviorManager() { return behaviorManager; }
    public com.atomguard.velocity.data.ConnectionHistory getConnectionHistory() { return connectionHistory; }

    public DDoSProtectionModule getDdosModule() { return ddosModule; }
    public VelocityAntiBotModule getAntiBotModule() { return antiBotModule; }
    public VPNDetectionModule getVpnModule() { return vpnModule; }
    public FirewallModule getFirewallModule() { return firewallModule; }
    public GlobalRateLimitModule getRateLimitModule() { return rateLimitModule; }
    public ProtocolValidationModule getProtocolModule() { return protocolModule; }
    public ProxyExploitModule getExploitModule() { return exploitModule; }
    
    // New Module Getters
    public IPTablesModule getIpTablesModule() { return ipTablesModule; }
    public AccountFirewallModule getAccountFirewallModule() { return accountFirewallModule; }
    public CountryFilterModule getCountryFilterModule() { return countryFilterModule; }
    public FastChatModule getFastChatModule() { return fastChatModule; }
    public ReconnectControlModule getReconnectControlModule() { return reconnectControlModule; }
    public PasswordCheckModule getPasswordCheckModule() { return passwordCheckModule; }
    public LatencyCheckModule getLatencyCheckModule() { return latencyCheckModule; }
}