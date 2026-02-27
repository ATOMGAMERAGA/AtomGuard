package com.atomguard.module.antibot;

import com.atomguard.AtomGuard;
import com.atomguard.module.AbstractModule;
import com.atomguard.module.antibot.check.*;
import com.atomguard.module.antibot.action.ActionExecutor;
import com.atomguard.module.antibot.verification.VerificationManager;
import com.atomguard.module.antibot.verification.WhitelistManager;
import com.atomguard.module.antibot.action.BlacklistManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiBotModule - Gelişmiş Bot Algılama Sistemi
 * Çok katmanlı, skor tabanlı bot koruması
 */
public class AntiBotModule extends AbstractModule implements Listener {

    private final Map<UUID, PlayerProfile> playerProfiles = new ConcurrentHashMap<>();
    private final Map<String, PlayerProfile> ipProfiles = new ConcurrentHashMap<>();
    
    private AttackTracker attackTracker;
    private ThreatScoreCalculator threatScoreCalculator;
    private ActionExecutor actionExecutor;
    private BlacklistManager blacklistManager;
    private WhitelistManager whitelistManager;
    private VerificationManager verificationManager;

    public AntiBotModule(@NotNull AtomGuard plugin) {
        super(plugin, "bot-koruma", "Çok katmanlı gelişmiş bot algılama sistemi");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        
        this.attackTracker = new AttackTracker(this);
        this.blacklistManager = new BlacklistManager(this);
        this.whitelistManager = new WhitelistManager(this);
        this.verificationManager = new VerificationManager(this);
        this.threatScoreCalculator = new ThreatScoreCalculator(this);
        this.actionExecutor = new ActionExecutor(this);
        
        // PacketEvents global handlers - Merkezi Listener üzerinden
        registerReceiveHandler(null, this::handleIncomingPacket);
        registerSendHandler(null, this::handleOutgoingPacket);
        
        // Attack evaluation task
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (isEnabled()) {
                attackTracker.evaluateAttackStatus();
            }
        }, 100L, 100L); // Every 5 seconds
    }

    @Override
    public void onDisable() {
        super.onDisable();
        
        if (blacklistManager != null) blacklistManager.saveAsync();
        if (whitelistManager != null) whitelistManager.saveAsync();
        
        playerProfiles.clear();
        ipProfiles.clear();
    }

    @Override
    public void cleanup() {
        cleanupProfiles();
    }

    private void handleIncomingPacket(PacketReceiveEvent event) {
        User user = event.getUser();
        if (user == null) return;
        
        java.net.InetSocketAddress handlerAddr = user.getAddress();
        String ip = (handlerAddr != null) ? handlerAddr.getAddress().getHostAddress() : "0.0.0.0";

        // Check blacklist first
        if (blacklistManager.isBlacklisted(ip)) {
            event.setCancelled(true);
            user.closeConnection();
            return;
        }

        PlayerProfile profile = getOrCreateProfile(user);
        
        // Protocol-level checks
        if (event.getPacketType() == PacketType.Status.Client.PING) {
            profile.recordPing();
        } else if (event.getPacketType() == PacketType.Handshaking.Client.HANDSHAKE) {
            profile.recordHandshake(event);
        } else if (event.getPacketType() == PacketType.Login.Client.LOGIN_START) {
            profile.recordLoginStart(event);
        } else if (event.getPacketType() == PacketType.Login.Client.ENCRYPTION_RESPONSE) {
            profile.recordEncryptionResponse();
        } else if (event.getPacketType() == PacketType.Configuration.Client.CLIENT_SETTINGS ||
                   event.getPacketType() == PacketType.Play.Client.CLIENT_SETTINGS) {
            profile.recordClientSettings();
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE ||
                   event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            profile.recordPluginMessage(event);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION ||
                   event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            profile.recordMovement(event);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
            profile.recordRotation(event);
        } else if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            profile.recordKeepAliveResponse();
        } else if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE ||
                   event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
            profile.recordChat();
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            profile.recordInventoryInteraction();
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY ||
                   event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT ||
                   event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            profile.recordWorldInteraction();
        }
    }

    private void handleOutgoingPacket(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Login.Server.ENCRYPTION_REQUEST) {
            PlayerProfile profile = getOrCreateProfile(event.getUser());
            profile.recordEncryptionRequest();
        } else if (event.getPacketType() == PacketType.Play.Server.KEEP_ALIVE) {
            PlayerProfile profile = getOrCreateProfile(event.getUser());
            profile.recordKeepAliveSent();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!isEnabled()) return;
        
        String ip = event.getAddress().getHostAddress();
        String name = event.getName();
        UUID uuid = event.getUniqueId();
        
        // Blacklist check
        if (blacklistManager.isBlacklisted(ip)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, 
                plugin.getMessageManager().getMessage("antibot.kara-liste-mesaji"));
            return;
        }
        
        // Whitelist check
        if (whitelistManager.isWhitelisted(uuid)) {
            return;
        }

        // Verified player cache check
        if (plugin.getVerifiedPlayerCache() != null && plugin.getVerifiedPlayerCache().isVerified(name, ip)) {
            if (plugin.getVerifiedPlayerCache().shouldSkipBotCheck()) {
                return;
            }
        }
        
        attackTracker.recordConnection();
        
        PlayerProfile profile = ipProfiles.get(ip);
        if (profile == null) {
            profile = new PlayerProfile(uuid, name, ip);
            ipProfiles.put(ip, profile);
        } else {
            profile.updateIdentity(uuid, name);
        }
        playerProfiles.put(uuid, profile);
        
        // Initial checks
        ThreatScoreCalculator.ThreatResult result = threatScoreCalculator.evaluate(profile);
        actionExecutor.executeInitial(event, profile, result);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        
        PlayerProfile profile = playerProfiles.get(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.onJoin();
            verificationManager.startVerification(event.getPlayer(), profile);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerProfile profile = playerProfiles.remove(event.getPlayer().getUniqueId());
        if (profile != null) {
            verificationManager.stopVerification(profile);
        }
    }

    private void cleanupProfiles() {
        long now = System.currentTimeMillis();
        playerProfiles.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null && (now - entry.getValue().getLastSeen()) > 300000);
        ipProfiles.entrySet().removeIf(entry -> (now - entry.getValue().getLastSeen()) > 600000);
    }

    private PlayerProfile getOrCreateProfile(User user) {
        if (user == null) return null;

        UUID uuid = user.getUUID();
        java.net.InetSocketAddress addr = user.getAddress();
        String ip = (addr != null) ? addr.getAddress().getHostAddress() : "0.0.0.0";
        
        if (uuid != null) {
            return playerProfiles.computeIfAbsent(uuid, k -> new PlayerProfile(uuid, null, ip));
        } else {
            return ipProfiles.computeIfAbsent(ip, k -> new PlayerProfile(null, null, ip));
        }
    }

    public AttackTracker getAttackTracker() { return attackTracker; }
    public BlacklistManager getBlacklistManager() { return blacklistManager; }
    public WhitelistManager getWhitelistManager() { return whitelistManager; }
    public VerificationManager getVerificationManager() { return verificationManager; }
    public ThreatScoreCalculator getThreatScoreCalculator() { return threatScoreCalculator; }
    public ActionExecutor getActionExecutor() { return actionExecutor; }
}
