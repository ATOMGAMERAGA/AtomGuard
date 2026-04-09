package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Force OP Exploit Algılama Modülü
 *
 * <p>Bilinen Force OP vektörlerini dört katmanda algılar ve engeller:
 * <ol>
 *   <li><b>Komut izleme</b> — Oyunculardan gelen yetkisiz {@code /op}/{@code /deop} komutları
 *   <li><b>Konsol izleme</b> — Konsol/{@code ServerCommandEvent} üzerinden OP komutları
 *   <li><b>BungeeCord payload tespiti</b> — {@code bungeecord:main} kanalı üzerinden
 *       tehlikeli komut gönderilmesi (ServerCommand, Message alt kanalları)
 *   <li><b>OP snapshot monitörü</b> — Beklenmedik runtime OP değişikliklerinin tespiti
 * </ol>
 *
 * <p>Tespit edilen her olay {@link AlertSeverity#CRITICAL} seviyesinde admin bildirim
 * sistemi ({@code alertAdmins()}) tetikler — online yöneticiler sesli title uyarısı alır,
 * log dosyasına yazılır ve Discord webhook bildirim gönderilir.
 *
 * <p>Config anahtarı: {@code modules.force-op-detection}
 *
 * @author AtomGuard Team
 * @version 2.3.0
 */
public class ForceOPDetectionModule extends AbstractModule implements Listener {

    /** Oyuncu tarafından kullanılabilecek OP komut prefix'leri (lowercase) */
    private static final Set<String> OP_COMMAND_PREFIXES = Set.of(
            "/op ", "/deop ",
            "/minecraft:op ", "/minecraft:deop ",
            "/bukkit:op ", "/bukkit:deop "
    );

    /** BungeeCord plugin mesaj kanalları (lowercase) */
    private static final Set<String> BUNGEE_CHANNELS = Set.of(
            "bungeecord:main", "bungeecord"
    );

    /** OP listesi snapshot'ı — beklenmedik değişiklik tespiti için */
    private final Set<UUID> opSnapshot = new HashSet<>();

    /** Periyodik snapshot görevinin task ID'si */
    private int snapshotTaskId = -1;

    // ─── Config cache ────────────────────────────────────────────────────
    private boolean monitorOpCommands;
    private boolean monitorConsoleOp;
    private boolean monitorBungeeExploit;
    private int snapshotIntervalSeconds;
    private String onPlayerForceOpAction;
    private String onBungeeExploitAction;
    private String onUnknownOpChangeAction;
    private Set<UUID> whitelistedOpUsers;
    private Set<String> blockedBungeeCommands;

    /**
     * ForceOPDetectionModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public ForceOPDetectionModule(@NotNull AtomGuard plugin) {
        super(plugin, "force-op-detection", "Force OP exploit algılama ve engelleme");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        loadConfig();

        // İlk OP snapshot'ını al
        takeOpSnapshot();

        // BungeeCord payload izleme — packet handler
        if (monitorBungeeExploit) {
            registerReceiveHandler(PacketType.Play.Client.PLUGIN_MESSAGE, this::handleBungeePayload);
        }

        // OP snapshot periyodik kontrolü — main thread'de çalışır
        snapshotTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                this::checkOpSnapshot,
                snapshotIntervalSeconds * 20L,
                snapshotIntervalSeconds * 20L
        );

        debug("Force OP tespiti başlatıldı — komut izleme=" + monitorOpCommands
                + ", konsol izleme=" + monitorConsoleOp
                + ", bungee izleme=" + monitorBungeeExploit
                + ", snapshot aralığı=" + snapshotIntervalSeconds + "s");
    }

    @Override
    public void onDisable() {
        if (snapshotTaskId != -1) {
            Bukkit.getScheduler().cancelTask(snapshotTaskId);
            snapshotTaskId = -1;
        }
        opSnapshot.clear();
        super.onDisable();
    }

    // ─── Config ──────────────────────────────────────────────────────────

    private void loadConfig() {
        this.monitorOpCommands = getConfigBoolean("monitor-op-commands", true);
        this.monitorConsoleOp = getConfigBoolean("monitor-console-op", true);
        this.monitorBungeeExploit = getConfigBoolean("monitor-bungee-exploit", true);
        this.snapshotIntervalSeconds = Math.max(1, getConfigInt("op-snapshot-interval-seconds", 5));
        this.onPlayerForceOpAction = getConfigString("actions.on-player-forceop", "KICK").toUpperCase();
        this.onBungeeExploitAction = getConfigString("actions.on-bungee-exploit", "BLOCK").toUpperCase();
        this.onUnknownOpChangeAction = getConfigString("actions.on-unknown-op-change", "ALERT_ONLY").toUpperCase();

        // Whitelist UUID'leri yükle
        this.whitelistedOpUsers = new HashSet<>();
        List<String> whitelistStrs = plugin.getConfigManager()
                .getStringList("modules." + getName() + ".whitelisted-op-users");
        for (String uuidStr : whitelistStrs) {
            try {
                whitelistedOpUsers.add(UUID.fromString(uuidStr.trim()));
            } catch (IllegalArgumentException e) {
                warning("Geçersiz UUID whitelist kaydı yoksayıldı: " + uuidStr);
            }
        }

        // Engellenen BungeeCord komutlarını yükle
        List<String> configCmds = plugin.getConfigManager()
                .getStringList("modules." + getName() + ".blocked-bungee-commands");
        this.blockedBungeeCommands = configCmds.isEmpty()
                ? new HashSet<>(Set.of("op", "deop", "stop", "ban", "ban-ip", "pardon", "whitelist"))
                : new HashSet<>(configCmds);
    }

    // ─── Komut İzleme ────────────────────────────────────────────────────

    /**
     * Oyuncu komut ön işleme — yetkisiz /op ve /deop komutlarını yakalar.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerCommand(@NotNull PlayerCommandPreprocessEvent event) {
        if (!isEnabled() || !monitorOpCommands) return;

        String cmd = event.getMessage().toLowerCase();

        for (String prefix : OP_COMMAND_PREFIXES) {
            if (cmd.startsWith(prefix)) {
                Player player = event.getPlayer();

                // Whitelist veya bypass izni kontrolü
                if (whitelistedOpUsers.contains(player.getUniqueId())
                        || player.hasPermission("atomguard.bypass")) return;

                event.setCancelled(true);
                String details = "Yetkisiz OP komutu denemesi: " + event.getMessage();
                blockExploit(player, details, AlertSeverity.CRITICAL);

                player.sendMessage(plugin.getMessageManager()
                        .getMessage("engelleme.forceop-komut-engellendi"));

                applyPlayerAction(player, onPlayerForceOpAction);
                return;
            }
        }
    }

    // ─── Konsol Komut İzleme ─────────────────────────────────────────────

    /**
     * Sunucu komut olayı — konsol veya plugin kaynaklı OP komutlarını izler.
     * Bu olay iptal edilmez; yalnızca log ve admin uyarısı tetiklenir.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(@NotNull ServerCommandEvent event) {
        if (!isEnabled() || !monitorConsoleOp) return;

        String cmd = event.getCommand().toLowerCase().trim();
        if (cmd.startsWith("op ") || cmd.startsWith("deop ")
                || cmd.startsWith("minecraft:op ") || cmd.startsWith("minecraft:deop ")) {
            String alertMsg = "<yellow><bold>⚠</bold> <gold>Konsol OP komutu çalıştırıldı: <white>"
                    + event.getCommand();
            alertAdmins(alertMsg, AlertSeverity.HIGH);
            plugin.getLogManager().warning("[ForceOP] Konsol OP komutu: " + event.getCommand());
        }
    }

    // ─── BungeeCord Payload Tespiti ──────────────────────────────────────

    /**
     * Gelen PLUGIN_MESSAGE paketini BungeeCord ForceOP keyword'leri için tarar.
     * Tehlikeli içerik tespit edilirse paket iptal edilir.
     */
    private void handleBungeePayload(@NotNull PacketReceiveEvent event) {
        if (!isEnabled()) return;
        if (event.isCancelled()) return;
        if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        try {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            String channel = packet.getChannelName().toLowerCase();

            if (!BUNGEE_CHANNELS.contains(channel)) return;

            byte[] data = packet.getData();
            if (data == null || data.length == 0) return;

            String payloadStr = new String(data, StandardCharsets.UTF_8).toLowerCase();

            for (String keyword : blockedBungeeCommands) {
                // Komutun gerçek bir kelime olduğundan emin ol (boşluk veya satır sonu sonrası)
                if (payloadStr.contains(keyword + " ") || payloadStr.contains(keyword + "\n")
                        || payloadStr.contains(keyword + "\r")) {
                    event.setCancelled(true);
                    String details = "BungeeCord ForceOP payload ('" + keyword + "')"
                            + " — kanal: " + packet.getChannelName();
                    blockExploit(player, details, AlertSeverity.CRITICAL);
                    return;
                }
            }
        } catch (Exception e) {
            debug("BungeeCord payload analizi sırasında hata: " + e.getMessage());
        }
    }

    // ─── OP Snapshot Monitörü ────────────────────────────────────────────

    /**
     * Mevcut OP listesini snapshot ile karşılaştırır.
     * Ana thread'de çalışır (scheduleSyncRepeatingTask).
     */
    private void checkOpSnapshot() {
        if (!isEnabled()) return;

        Set<UUID> currentOps = new HashSet<>();
        for (OfflinePlayer op : Bukkit.getOperators()) {
            if (op.getUniqueId() != null) {
                currentOps.add(op.getUniqueId());
            }
        }

        // Yeni eklenen OP'leri tespit et
        for (UUID uuid : currentOps) {
            if (!opSnapshot.contains(uuid) && !whitelistedOpUsers.contains(uuid)) {
                OfflinePlayer newOp = Bukkit.getOfflinePlayer(uuid);
                String opName = newOp.getName() != null ? newOp.getName() : uuid.toString();

                String alertMsg = "<red><bold>⚠ KRİTİK:</bold> <white>Beklenmedik OP değişikliği! "
                        + "<yellow>" + opName + " <red>artık OP yetkisine sahip!";
                alertAdmins(alertMsg, AlertSeverity.CRITICAL);
                plugin.getLogManager().warning("[ForceOP] Beklenmedik OP eklendi: "
                        + opName + " (" + uuid + ")");

                if ("REMOVE_OP".equals(onUnknownOpChangeAction)) {
                    newOp.setOp(false);
                    alertAdmins("<green>✓ <white>" + opName
                            + "<green> kullanıcısının OP yetkisi otomatik olarak kaldırıldı.",
                            AlertSeverity.MEDIUM);
                }
            }
        }

        // Snapshot'ı güncelle
        opSnapshot.clear();
        opSnapshot.addAll(currentOps);
    }

    /**
     * İlk OP snapshot'ını alır — onEnable()'da ve konfigürasyon yeniden yüklemesinde çağrılır.
     */
    private void takeOpSnapshot() {
        opSnapshot.clear();
        for (OfflinePlayer op : Bukkit.getOperators()) {
            if (op.getUniqueId() != null) {
                opSnapshot.add(op.getUniqueId());
            }
        }
        debug("OP snapshot alındı: " + opSnapshot.size() + " operatör.");
    }

    // ─── Aksiyon Yardımcısı ──────────────────────────────────────────────

    /**
     * Tespit edilen exploit için konfigürasyona göre aksiyon uygular.
     *
     * @param player Hedef oyuncu
     * @param action "KICK", "BAN" veya "ALERT_ONLY"
     */
    private void applyPlayerAction(@NotNull Player player, @NotNull String action) {
        switch (action) {
            case "KICK":
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.kick(Component.text("§cForce OP exploit tespit edildi! Sunucudan atıldınız.")));
                break;
            case "BAN":
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                            .addBan(player.getName(), "AtomGuard: Force OP exploit", null, "AtomGuard");
                    player.kick(Component.text("§cForce OP exploit — kalıcı ban uygulandı."));
                });
                break;
            case "ALERT_ONLY":
            default:
                break;
        }
    }
}
