package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.listener.NettyCrashHandler;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty Crash Koruması Modülü
 *
 * İki katmanlı koruma sağlar:
 * 1. Netty pipeline enjeksiyonu — decoder hataları ve exception'ları yakalar
 * 2. PacketEvents ile konum doğrulama — NaN, Infinity, sınır dışı koordinatları engeller
 *
 * Enjeksiyon noktası: PlayerJoinEvent → oyuncunun Netty channel pipeline'ına
 * "decoder" handler'ından SONRA handler eklenir.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class NettyCrashModule extends AbstractModule implements Listener {

    /** Enjekte edilen handler'ları takip eden set */
    private final Set<UUID> injectedPlayers = ConcurrentHashMap.newKeySet();

    private PacketListenerAbstract packetListener;

    // Config cache
    private double maxPositionValue;
    private double maxYValue;

    /**
     * NettyCrashModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public NettyCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "netty-crash", "Netty crash koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        // Bukkit event listener olarak kaydet
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // PacketEvents ile konum doğrulama
        packetListener = new PacketListenerAbstract(PacketListenerPriority.LOW) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                handlePositionPacket(event);
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        // Zaten online olan oyuncular için handler enjekte et
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            injectHandler(player);
        }

        debug("Netty crash koruması başlatıldı. Max konum: " + maxPositionValue + ", Max Y: " + maxYValue);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        HandlerList.unregisterAll(this);

        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }

        // Tüm enjekte edilmiş handler'ları kaldır
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removeHandler(player);
        }
        injectedPlayers.clear();

        debug("Netty crash koruması durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxPositionValue = plugin.getConfigManager()
                .getDouble("moduller." + getName() + ".max-konum-degeri", 3.2E7);
        this.maxYValue = plugin.getConfigManager()
                .getDouble("moduller." + getName() + ".max-y-degeri", 4096.0);
    }

    /**
     * Oyuncu katıldığında Netty handler enjekte et
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        if (!isEnabled()) return;

        // 1 tick sonra enjekte et — bağlantının tam kurulmasını bekle
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) {
                injectHandler(player);
            }
        }, 1L);
    }

    /**
     * Oyuncu ayrıldığında handler'ı temizle
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        injectedPlayers.remove(uuid);
    }

    /**
     * Netty handler'ı oyuncunun pipeline'ına enjekte eder
     */
    private void injectHandler(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (injectedPlayers.contains(uuid)) return;

        try {
            Object channelObj = PacketEvents.getAPI().getPlayerManager().getChannel(player);
            if (!(channelObj instanceof Channel channel)) {
                debug("Channel alınamadı: " + player.getName());
                return;
            }

            ChannelPipeline pipeline = channel.pipeline();

            // Eski handler varsa kaldır
            if (pipeline.get(NettyCrashHandler.HANDLER_NAME) != null) {
                pipeline.remove(NettyCrashHandler.HANDLER_NAME);
            }

            // "decoder" handler'ından SONRA enjekte et
            if (pipeline.get("decoder") != null) {
                NettyCrashHandler handler = new NettyCrashHandler(plugin, uuid, player.getName());
                pipeline.addAfter("decoder", NettyCrashHandler.HANDLER_NAME, handler);
                injectedPlayers.add(uuid);
                debug("Netty handler enjekte edildi: " + player.getName());
            } else {
                debug("Decoder handler bulunamadı: " + player.getName());
            }

        } catch (Exception e) {
            error("Handler enjeksiyonu sırasında hata (" + player.getName() + "): " + e.getMessage());
        }
    }

    /**
     * Netty handler'ı oyuncunun pipeline'ından kaldırır
     */
    private void removeHandler(@NotNull Player player) {
        try {
            Object channelObj = PacketEvents.getAPI().getPlayerManager().getChannel(player);
            if (!(channelObj instanceof Channel channel)) return;

            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(NettyCrashHandler.HANDLER_NAME) != null) {
                pipeline.remove(NettyCrashHandler.HANDLER_NAME);
            }
        } catch (Exception e) {
            // Oyuncu zaten ayrılmış olabilir — güvenli şekilde atla
        }
    }

    /**
     * Konum paketlerini NaN/Infinity/sınır dışı değerler için kontrol eder
     */
    private void handlePositionPacket(@NotNull PacketReceiveEvent event) {
        if (!isEnabled()) return;

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
            handlePlayerPosition(event);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            handlePlayerPositionAndRotation(event);
        }
    }

    /**
     * Position paketi kontrolü
     */
    private void handlePlayerPosition(@NotNull PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        try {
            WrapperPlayClientPlayerPosition packet = new WrapperPlayClientPlayerPosition(event);
            double x = packet.getLocation().getX();
            double y = packet.getLocation().getY();
            double z = packet.getLocation().getZ();

            if (isInvalidPosition(x, y, z)) {
                blockAndKick(event, player, x, y, z);
            }
        } catch (Exception e) {
            error("Position paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * PositionAndRotation paketi kontrolü
     */
    private void handlePlayerPositionAndRotation(@NotNull PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        try {
            WrapperPlayClientPlayerPositionAndRotation packet =
                    new WrapperPlayClientPlayerPositionAndRotation(event);
            double x = packet.getLocation().getX();
            double y = packet.getLocation().getY();
            double z = packet.getLocation().getZ();
            float yaw = packet.getLocation().getYaw();
            float pitch = packet.getLocation().getPitch();

            // Koordinat kontrolü
            if (isInvalidPosition(x, y, z)) {
                blockAndKick(event, player, x, y, z);
                return;
            }

            // Rotation NaN/Infinity kontrolü
            if (Float.isNaN(yaw) || Float.isInfinite(yaw)
                    || Float.isNaN(pitch) || Float.isInfinite(pitch)) {
                event.setCancelled(true);
                incrementBlockedCount();
                logExploit(player.getName(),
                        String.format("Geçersiz rotation: yaw=%.2f, pitch=%.2f", yaw, pitch));
            }
        } catch (Exception e) {
            error("PositionAndRotation paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * Koordinat değerlerinin geçerli olup olmadığını kontrol eder
     */
    private boolean isInvalidPosition(double x, double y, double z) {
        // NaN ve Infinity kontrolü
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return true;
        if (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) return true;

        // Sınır kontrolü
        if (Math.abs(x) > maxPositionValue) return true;
        if (Math.abs(z) > maxPositionValue) return true;
        if (Math.abs(y) > maxYValue) return true;

        return false;
    }

    /**
     * Paketi engeller ve oyuncuyu async kick eder
     */
    private void blockAndKick(@NotNull PacketReceiveEvent event, @NotNull Player player,
                              double x, double y, double z) {
        event.setCancelled(true);
        incrementBlockedCount();

        logExploit(player.getName(),
                String.format("Geçersiz konum tespit edildi! X=%.2f, Y=%.2f, Z=%.2f", x, y, z));

        // Async kick — ana thread'de
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                Component kickMessage = plugin.getMessageManager()
                        .getMessage("engelleme.netty-crash");
                player.kick(kickMessage);
            }
        });
    }
}
