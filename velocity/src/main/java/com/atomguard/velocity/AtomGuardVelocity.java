package com.atomguard.velocity;

import com.atomguard.velocity.command.AtomGuardVelocityCommand;
import com.atomguard.velocity.communication.BackendCommunicator;
import com.atomguard.velocity.config.VelocityConfigManager;
import com.atomguard.velocity.config.VelocityMessageManager;
import com.atomguard.velocity.listener.*;
import com.atomguard.velocity.manager.*;
import com.atomguard.velocity.module.antiddos.DDoSProtectionModule;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import com.atomguard.velocity.module.firewall.FirewallModule;
import com.atomguard.velocity.module.ratelimit.GlobalRateLimitModule;
import com.atomguard.velocity.module.protocol.ProtocolValidationModule;
import com.atomguard.velocity.module.exploit.ProxyExploitModule;
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

    private VelocityConfigManager configManager;
    private VelocityMessageManager messageManager;
    private VelocityLogManager logManager;
    private VelocityStatisticsManager statisticsManager;
    private VelocityAlertManager alertManager;
    private VelocityModuleManager moduleManager;
    private BackendCommunicator backendCommunicator;

    // Modüller
    private DDoSProtectionModule ddosModule;
    private VelocityAntiBotModule antiBotModule;
    private VPNDetectionModule vpnModule;
    private FirewallModule firewallModule;
    private GlobalRateLimitModule rateLimitModule;
    private ProtocolValidationModule protocolModule;
    private ProxyExploitModule exploitModule;

    private volatile boolean attackMode = false;
    private volatile long attackModeStartTime = 0;

    @Inject
    public AtomGuardVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            initializeManagers();
            registerModules();
            moduleManager.enableAll();
            registerListeners();
            registerCommands();
            initializeCommunication();

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
        if (moduleManager != null) moduleManager.disableAll();
        if (backendCommunicator != null) backendCommunicator.shutdown();
        if (statisticsManager != null) statisticsManager.save();
        if (logManager != null) logManager.shutdown();
    }

    private void initializeManagers() throws Exception {
        configManager = new VelocityConfigManager(dataDirectory, logger);
        configManager.load();

        messageManager = new VelocityMessageManager(dataDirectory, logger);
        messageManager.load();

        logManager = new VelocityLogManager(dataDirectory, logger);
        logManager.initialize();

        statisticsManager = new VelocityStatisticsManager(dataDirectory, logger);
        statisticsManager.load();

        alertManager = new VelocityAlertManager(server, logger);
        String webhookUrl = configManager.getString("discord.webhook-url", "");
        boolean discordEnabled = configManager.getBoolean("discord.aktif", false);
        alertManager.configure(webhookUrl, discordEnabled);

        moduleManager = new VelocityModuleManager(logger);
    }

    private void registerModules() {
        ddosModule = new DDoSProtectionModule(this);
        antiBotModule = new VelocityAntiBotModule(this);
        vpnModule = new VPNDetectionModule(this);
        firewallModule = new FirewallModule(this);
        rateLimitModule = new GlobalRateLimitModule(this);
        protocolModule = new ProtocolValidationModule(this);
        exploitModule = new ProxyExploitModule(this);

        moduleManager.register(firewallModule);    // Önce güvenlik duvarı
        moduleManager.register(rateLimitModule);
        moduleManager.register(ddosModule);
        moduleManager.register(protocolModule);
        moduleManager.register(antiBotModule);
        moduleManager.register(vpnModule);
        moduleManager.register(exploitModule);
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
        if (this.attackMode == state) return;
        this.attackMode = state;
        if (state) {
            attackModeStartTime = System.currentTimeMillis();
            logManager.warn("SALDIRI MODU AKTİF!");
        } else {
            logManager.log("Saldırı modu sona erdi.");
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

    public DDoSProtectionModule getDdosModule() { return ddosModule; }
    public VelocityAntiBotModule getAntiBotModule() { return antiBotModule; }
    public VPNDetectionModule getVpnModule() { return vpnModule; }
    public FirewallModule getFirewallModule() { return firewallModule; }
    public GlobalRateLimitModule getRateLimitModule() { return rateLimitModule; }
    public ProtocolValidationModule getProtocolModule() { return protocolModule; }
    public ProxyExploitModule getExploitModule() { return exploitModule; }
}
