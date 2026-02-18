package com.atomguard;

import com.atomguard.command.AtomGuardCommand;
import com.atomguard.command.AtomGuardTabCompleter;
import com.atomguard.listener.BukkitListener;
import com.atomguard.listener.CoreMessagingListener;
import com.atomguard.listener.InventoryListener;
import com.atomguard.listener.PacketListener;
import com.atomguard.manager.*;
import com.atomguard.listener.AuthListener;
import org.bukkit.plugin.java.JavaPlugin;

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

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        // ... (Initialization code) ...
        try {
            // Managers
            this.configManager = new ConfigManager(this);
            this.messageManager = new MessageManager(this);
            this.logManager = new LogManager(this);
            this.statisticsManager = new StatisticsManager(this);
            this.redisManager = new RedisManager(this);
            this.attackModeManager = new AttackModeManager(this);
            this.discordWebhookManager = new DiscordWebhookManager(this);
            this.moduleManager = new ModuleManager(this);

            // Initialize Managers
            configManager.loadConfig();
            messageManager.loadMessages();
            logManager.initialize();
            statisticsManager.load();
            moduleManager.registerModules();
            moduleManager.enableModules(); // Enable modules based on config

            // Listeners
            getServer().getPluginManager().registerEvents(new BukkitListener(this), this);
            getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
            getServer().getPluginManager().registerEvents(new PacketListener(this), this); // PacketEvents hook inside

            // Messaging
            if (getConfig().getBoolean("mesajlasma.aktif", true)) {
                getServer().getMessenger().registerOutgoingPluginChannel(this, "atomguard:main");
                getServer().getMessenger().registerIncomingPluginChannel(this, "atomguard:main", new CoreMessagingListener(this));
                
                // Auth channel
                getServer().getMessenger().registerOutgoingPluginChannel(this, "atomguard:auth");
            }

            // Commands
            getCommand("atomguard").setExecutor(new AtomGuardCommand(this));
            getCommand("atomguard").setTabCompleter(new AtomGuardTabCompleter(this));

            getLogger().info("AtomGuard (Core) has been enabled!");
            
            // Send startup info to Velocity if connected? 
            // Usually Velocity pulls info or we send heartbeat.

        } catch (Exception e) {
            getLogger().severe("Failed to enable AtomGuard: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) moduleManager.disableModules();
        if (redisManager != null) redisManager.close();
        if (statisticsManager != null) statisticsManager.save();
        if (logManager != null) logManager.shutdown();
        
        getLogger().info("AtomGuard (Core) has been disabled.");
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
}