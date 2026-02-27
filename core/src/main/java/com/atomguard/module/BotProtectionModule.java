package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.BotUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.handshaking.client.WrapperHandshakingClientHandshake;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BotProtectionModule - Bot algılama ve koruma modülü
 * @deprecated Use {@link com.atomguard.module.antibot.AntiBotModule} for advanced protection.
 */
@Deprecated
public class BotProtectionModule extends AbstractModule implements Listener {

    private final Set<UUID> pendingVerification = ConcurrentHashMap.newKeySet();
    private final Set<String> onlinePlayerNames = ConcurrentHashMap.newKeySet();
    // IP Address -> Offense Count
    private final Map<String, Integer> ipOffenseCount = new ConcurrentHashMap<>();
    
    // AtomShield: Session tracking using User objects
    private final Map<User, Long> handshakeTimestamps = new ConcurrentHashMap<>();
    private final Map<User, Long> encryptionRequestTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, RotationData> playerRotations = new ConcurrentHashMap<>();

    public BotProtectionModule(AtomGuard plugin) {
        super(plugin, "bot-korumasi", "Bot algılama ve koruma modülü (Legacy)");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        
        // PacketEvents listeners - Merkezi Listener üzerinden
        registerReceiveHandler(PacketType.Handshaking.Client.HANDSHAKE, this::handleHandshake);
        registerReceiveHandler(PacketType.Login.Client.LOGIN_START, this::handleLoginStart);
        registerReceiveHandler(PacketType.Login.Client.ENCRYPTION_RESPONSE, this::handleEncryptionResponse);
        registerReceiveHandler(PacketType.Play.Client.PLAYER_ROTATION, this::handleRotation);
        registerReceiveHandler(PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION, this::handleRotation);
        
        registerSendHandler(PacketType.Login.Server.ENCRYPTION_REQUEST, this::handleEncryptionRequest);

        // Populate initially
        for (Player p : Bukkit.getOnlinePlayers()) {
            onlinePlayerNames.add(p.getName());
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        pendingVerification.clear();
        onlinePlayerNames.clear();
        ipOffenseCount.clear();
        handshakeTimestamps.clear();
        encryptionRequestTimestamps.clear();
    }

    @Override
    public void cleanup() {
        // No-op, uses maps that are cleared on disable or managed by events
    }

    private void handleEncryptionRequest(com.github.retrooper.packetevents.event.PacketSendEvent event) {
        if (!getConfigBoolean("atom-shield.protokol.sifreleme-gecikmesi", true)) return;

        User user = event.getUser();
        if (user == null) return;

        encryptionRequestTimestamps.put(user, System.currentTimeMillis());
        debug("Encryption Request sent to " + user.getAddress());
    }

    private void handleEncryptionResponse(PacketReceiveEvent event) {
        if (!getConfigBoolean("atom-shield.protokol.sifreleme-gecikmesi", true)) return;

        User user = event.getUser();
        if (user == null) return;

        Long requestTime = encryptionRequestTimestamps.remove(user);
        if (requestTime != null) {
            long delta = System.currentTimeMillis() - requestTime;
            // RSA/AES işlemleri ve ağ gecikmesi dahil 5ms'den kısa sürmesi imkansızdır (insan/gerçek client için)
            if (delta < 5) { 
                event.setCancelled(true);
                user.closeConnection();
                String ip = user.getAddress().getAddress().getHostAddress();
                plugin.getLogManager().logBot("IP-" + ip, ip,
                        "AtomShield: Şüpheli Şifreleme Yanıtı (Çok Hızlı). Delta: " + delta + "ms");
                handleOffense("Bot-IP-" + ip, ip);
            }
        }
    }

    private void handleHandshake(PacketReceiveEvent event) {
        if (!getConfigBoolean("atom-shield.handshake.aktif", true)) return;

        User user = event.getUser();
        if (user == null) return;

        handshakeTimestamps.put(user, System.currentTimeMillis());

        WrapperHandshakingClientHandshake handshake = new WrapperHandshakingClientHandshake(event);
        String serverAddress = handshake.getServerAddress();
        int serverPort = handshake.getServerPort();
        String ip = user.getAddress().getAddress().getHostAddress();

        // Katman 1: Hostname Kontrolü
        if (getConfigBoolean("atom-shield.handshake.hostname-zorunlu", false)) {
            if (serverAddress.isEmpty() || serverAddress.equalsIgnoreCase("localhost") || serverAddress.equalsIgnoreCase("127.0.0.1")) {
                event.setCancelled(true);
                user.closeConnection();
                plugin.getLogManager().logBot("IP-" + ip, ip, "AtomShield: Geçersiz hostname: " + serverAddress);
                return;
            }
        }

        // Katman 1: Port Kontrolü
        if (getConfigBoolean("atom-shield.handshake.port-kontrolu", true)) {
            int defaultPort = Bukkit.getPort();
            if (serverPort != defaultPort && serverPort != 0) { 
                event.setCancelled(true);
                user.closeConnection();
                plugin.getLogManager().logBot("IP-" + ip, ip, "AtomShield: Geçersiz port: " + serverPort);
            }
        }
    }

    private void handleLoginStart(PacketReceiveEvent event) {
        if (!getConfigBoolean("atom-shield.protokol.aktif", true)) return;

        User user = event.getUser();
        if (user == null) return;

        Long handshakeTime = handshakeTimestamps.remove(user);
        if (handshakeTime != null) {
            long delta = System.currentTimeMillis() - handshakeTime;
            int minDelta = getConfigInt("atom-shield.protokol.min-login-gecikmesi", 0);

            if (delta < minDelta) {
                event.setCancelled(true);
                user.closeConnection();
                String ip = user.getAddress().getAddress().getHostAddress();
                plugin.getLogManager().logBot("IP-" + ip, ip,
                        "AtomShield: Hızlı Login tespiti (Instant-Join). Gecikme: " + delta + "ms");
                
                handleOffense("Bot-IP-" + ip, ip);
            }
        }
    }

    private void handleRotation(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!getConfigBoolean("atom-shield.davranissal.rotasyon-analizi", true)) return;

        float yaw, pitch;
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
            WrapperPlayClientPlayerRotation packet = new WrapperPlayClientPlayerRotation(event);
            yaw = packet.getYaw();
            pitch = packet.getPitch();
        } else {
            WrapperPlayClientPlayerPositionAndRotation packet = new WrapperPlayClientPlayerPositionAndRotation(event);
            yaw = packet.getYaw();
            pitch = packet.getPitch();
        }

        UUID uuid = player.getUniqueId();
        RotationData data = playerRotations.computeIfAbsent(uuid, k -> new RotationData());

        float deltaYaw = Math.abs(yaw - data.lastYaw);
        float deltaPitch = Math.abs(pitch - data.lastPitch);

        // 1. Snap Check (Ani ve büyük dönüş)
        if (deltaYaw > 100 || deltaPitch > 100) {
            // Sadece bir kez uyar, spam yapma
            if (System.currentTimeMillis() - data.lastViolationTime > 2000) {
                debug("Snap Rotation tespiti: " + player.getName() + " (Yaw Delta: " + deltaYaw + ")");
                data.lastViolationTime = System.currentTimeMillis();
            }
        }

        // 2. GCD Analizi (Bot/Hile Tespiti)
        if (deltaPitch > 0 && deltaPitch < 30) {
            long expandedPitch = (long) (deltaPitch * Math.pow(2, 24));
            long lastExpandedPitch = (long) (data.lastDeltaPitch * Math.pow(2, 24));
            long gcd = getGcd(expandedPitch, lastExpandedPitch);

            if (gcd < 131072) { // Çok düşük GCD = insan dışı hassasiyet veya bot
                data.gcdViolations++;
                if (data.gcdViolations > 20) {
                    plugin.getLogManager().logBot(player.getName(), 
                            player.getAddress().getAddress().getHostAddress(), 
                            "AtomShield: Anormal Fare Hareketi (GCD Analizi)");
                    data.gcdViolations = 0;
                }
            }
        }

        data.lastYaw = yaw;
        data.lastPitch = pitch;
        data.lastDeltaPitch = deltaPitch;
    }

    private long getGcd(long a, long b) {
        while (b > 0) {
            a %= b;
            long temp = a;
            a = b;
            b = temp;
        }
        return a;
    }

    private static class RotationData {
        float lastYaw = 0;
        float lastPitch = 0;
        float lastDeltaPitch = 0;
        int gcdViolations = 0;
        long lastViolationTime = 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!isEnabled()) return;

        // 1. Check Similar Names
        if (getConfigBoolean("benzer-isim-kontrolu.aktif", true)) {
            checkSimilarNames(event);
        }

        // 2. Offense Check (Ban logic)
        String ip = event.getAddress().getHostAddress();
        if (ipOffenseCount.getOrDefault(ip, 0) >= 2) {
             // Already marked for ban, but if they try to join again before the command executed?
             // Usually handled by the ban command itself.
             // But we can disallow login here too.
        }
    }

    private void checkSimilarNames(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        // FP-09: Daha katı eşikler
        int threshold = getConfigInt("benzer-isim-kontrolu.karakter-farki", 2); // 3'ten 2'ye düşürüldü
        int maxSimilar = getConfigInt("benzer-isim-kontrolu.max-benzer-sayisi", 15); // 10'dan 15'e çıkarıldı
        
        List<String> similarPlayers = new ArrayList<>();
        for (String pName : onlinePlayerNames) {
            // FP-09: Sadece Levenshtein'a güvenme. Prefix-only kontrolü ekle.
            if (BotUtils.getLevenshteinDistance(name, pName) <= threshold) {
                // Sadece ismin son kısmı (rakamlar) farklı ise benzer say, prefix benzer olmalı
                String prefix1 = name.replaceAll("\\d+$", "");
                String prefix2 = pName.replaceAll("\\d+$", "");
                
                if (prefix1.equalsIgnoreCase(prefix2) && prefix1.length() >= 4) {
                    similarPlayers.add(pName);
                }
            }
        }

        // Add current player to count (checking against others)
        if (similarPlayers.size() >= maxSimilar - 1) {
             event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, 
                ChatColor.RED + "Bot saldırısı şüphesi (Benzer isim kalıbı).");
            
            incrementBlockedCount();
            plugin.getLogManager().logBot(name, event.getAddress().getHostAddress(), 
                    "Benzer isim saldırısı (Pattern match). Benzerler: " + similarPlayers.size());

            // Ban others on main thread
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (String similarName : similarPlayers) {
                         Player target = Bukkit.getPlayerExact(similarName);
                         if (target != null) {
                             performBan(target.getName(), target.getAddress().getAddress().getHostAddress());
                             target.kickPlayer(ChatColor.RED + "Bot saldırısı şüphesi (Toplu işlem).");
                         }
                    }
                }
            }.runTask(plugin);
            
            // Ban current IP
            performBan(name, event.getAddress().getHostAddress());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        onlinePlayerNames.add(player.getName());

        if (getConfigBoolean("dogrulama.aktif", false)) {
            if (player.hasPermission("atomguard.bypass")) return;

            pendingVerification.add(player.getUniqueId());
            
            // Katman 3: Yerçekimi Testi (Gravity Check)
            if (getConfigBoolean("atom-shield.davranissal.yercekimi-testi", false)) {
                // Oyuncuyu havaya ışınla (sadece doğrulanmamışlar için)
                player.teleport(player.getLocation().add(0, 5, 0));
                debug("Yerçekimi testi başlatıldı: " + player.getName());
            }

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                getConfigString("dogrulama.dogrulama-mesaji", "&cLütfen doğrulamak için hareket edin!")));

            // Timeout scheduler
            int timeout = getConfigInt("dogrulama.sure", 30); // 15'ten 30'a çıkarıldı
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && pendingVerification.contains(player.getUniqueId())) {
                        handleOffense(player.getName(), player.getAddress().getAddress().getHostAddress());
                        
                        // FP-10: 3-strike sistemi (handleOffense zaten banlıyor 2. veya 3. ihlalde)
                        int offenses = ipOffenseCount.getOrDefault(player.getAddress().getAddress().getHostAddress(), 0);
                        if (offenses < 3) {
                            player.kickPlayer(ChatColor.RED + "Doğrulama zaman aşımı. (" + offenses + "/3)");
                        }
                    }
                }
            }.runTaskLater(plugin, timeout * 20L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (pendingVerification.contains(player.getUniqueId())) {
            // Katman 3: Yerçekimi Testi Kontrolü
            if (getConfigBoolean("atom-shield.davranissal.yercekimi-testi", false)) {
                // Eğer oyuncu aşağı doğru hareket etmiyorsa (veya hiç hareket etmiyorsa) doğrulanmaz
                if (event.getTo().getY() >= event.getFrom().getY() && !player.isOnGround()) {
                    // Sadece bekliyoruz, aşağı düşmesi lazım
                    return;
                }
            }

            // Check if actual movement occurred (threshold 0.1 blocks)
            if (event.getFrom().getWorld() != event.getTo().getWorld()) return;
            double distanceSq = event.getFrom().distanceSquared(event.getTo());
            if (distanceSq > 0.01) { // 0.1^2
                
                // Verified!
                pendingVerification.remove(player.getUniqueId());
                // Reset offenses on successful verification
                ipOffenseCount.remove(player.getAddress().getAddress().getHostAddress());
                
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    getConfigString("dogrulama.dogrulandi-mesaji", "&aDoğrulama başarılı!")));
            }
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        if (pendingVerification.remove(player.getUniqueId())) {
            ipOffenseCount.remove(player.getAddress().getAddress().getHostAddress());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                getConfigString("dogrulama.dogrulandi-mesaji", "&aDoğrulama başarılı!")));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        pendingVerification.remove(player.getUniqueId());
        // Sohbet düşük risklidir — sessizce doğrula, IP sayacı sıfırlanmaz
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingVerification.remove(event.getPlayer().getUniqueId());
        onlinePlayerNames.remove(event.getPlayer().getName());
    }

    private void handleOffense(String playerName, String ip) {
        int offenses = ipOffenseCount.getOrDefault(ip, 0) + 1;
        ipOffenseCount.put(ip, offenses);

        // FP-10: 3-strike sistemi (Kick, Kick, Ban)
        if (offenses <= 2) {
            plugin.getLogManager().logBot(playerName, ip, "Doğrulama ihlali: " + offenses + "/3");
        } else {
            // Third offense -> BAN
            performBan(playerName, ip);
            ipOffenseCount.remove(ip); // Reset after ban
        }
    }

    private void performBan(String playerName, String ip) {
        String banCmd = getConfigString("ceza.ban-komutu", "ban %player% 30dk Bot saldırısı tespit edildi");
        banCmd = banCmd.replace("%player%", playerName);
        
        final String commandToRun = banCmd;
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
                // Also log to panic/bot log using BotUtils
                BotUtils.logPanicBan(plugin, playerName, ip, "bot-bans.log");
            }
        }.runTask(plugin);
        
        plugin.getLogManager().logBot(playerName, ip, "Bot olarak algılandı ve yasaklandı.");
    }
}
