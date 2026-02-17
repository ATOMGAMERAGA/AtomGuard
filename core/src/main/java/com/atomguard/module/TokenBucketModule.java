package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.TokenBucket;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token Bucket Rate Limiter Modülü
 *
 * Her oyuncu için 4 ayrı kova ile paket rate limiting uygular:
 * - HAREKET: Position, PositionRotation, Rotation paketleri
 * - SOHBET: Chat, ChatCommand paketleri
 * - ENVANTER: WindowClick, CreativeSlot, CloseWindow paketleri
 * - DIGER: Geri kalan tüm client→server paketleri
 *
 * Token ≤ 0 → paketi sessizce düşür (bilgi sızıntısı önleme)
 * Token < kickThreshold → oyuncuyu kick et (sürekli flood)
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class TokenBucketModule extends AbstractModule {

    /** Kova türleri */
    private enum BucketType {
        HAREKET, SOHBET, ENVANTER, DIGER
    }

    /** Hareket paketleri — PLAYER_FLYING dahil (client her tick gönderir) */
    private static final Set<PacketType.Play.Client> MOVEMENT_PACKETS = Set.of(
            PacketType.Play.Client.PLAYER_POSITION,
            PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION,
            PacketType.Play.Client.PLAYER_ROTATION,
            PacketType.Play.Client.PLAYER_FLYING
    );

    /** Sohbet paketleri */
    private static final Set<PacketType.Play.Client> CHAT_PACKETS = Set.of(
            PacketType.Play.Client.CHAT_MESSAGE,
            PacketType.Play.Client.CHAT_COMMAND
    );

    /** Envanter paketleri */
    private static final Set<PacketType.Play.Client> INVENTORY_PACKETS = Set.of(
            PacketType.Play.Client.CLICK_WINDOW,
            PacketType.Play.Client.CREATIVE_INVENTORY_ACTION,
            PacketType.Play.Client.CLOSE_WINDOW
    );

    /** Oyuncu başına 4 kova — ConcurrentHashMap[UUID → BucketType → TokenBucket] */
    private final Map<UUID, Map<BucketType, TokenBucket>> playerBuckets = new ConcurrentHashMap<>();

    private PacketListenerAbstract listener;

    // Config cache
    private long hareketKapasite;
    private long hareketDolum;
    private long sohbetKapasite;
    private long sohbetDolum;
    private long envanterKapasite;
    private long envanterDolum;
    private long digerKapasite;
    private long digerDolum;
    private long floodKickThreshold;

    /**
     * TokenBucketModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public TokenBucketModule(@NotNull AtomGuard plugin) {
        super(plugin, "jeton-kovasi", "Token bucket rate limiter");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        listener = new PacketListenerAbstract(PacketListenerPriority.LOW) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                handlePacketReceive(event);
            }
        };

        PacketEvents.getAPI().getEventManager().registerListener(listener);
        debug("Token bucket modülü başlatıldı. Kick eşiği: " + floodKickThreshold);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        }

        playerBuckets.clear();
        debug("Token bucket modülü durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.hareketKapasite = getConfigLong("kovalar.hareket.kapasite", 200L);
        this.hareketDolum = getConfigLong("kovalar.hareket.dolum-saniye", 80L);
        this.sohbetKapasite = getConfigLong("kovalar.sohbet.kapasite", 20L);
        this.sohbetDolum = getConfigLong("kovalar.sohbet.dolum-saniye", 5L);
        this.envanterKapasite = getConfigLong("kovalar.envanter.kapasite", 100L);
        this.envanterDolum = getConfigLong("kovalar.envanter.dolum-saniye", 50L);
        this.digerKapasite = getConfigLong("kovalar.diger.kapasite", 150L);
        this.digerDolum = getConfigLong("kovalar.diger.dolum-saniye", 60L);
        this.floodKickThreshold = getConfigLong("flood-kick-esigi", -200L);
    }

    /**
     * Paket alındığında kova kontrolü yapar
     */
    private void handlePacketReceive(@NotNull PacketReceiveEvent event) {
        if (!isEnabled()) return;

        // Sadece Play aşamasındaki paketleri kontrol et
        if (!(event.getPacketType() instanceof PacketType.Play.Client clientPacket)) return;

        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        BucketType bucketType = classifyPacket(clientPacket);
        TokenBucket bucket = getOrCreateBucket(uuid, bucketType);

        long remaining = bucket.tryConsume();

        if (remaining <= 0) {
            // Token bitti — paketi sessizce düşür
            event.setCancelled(true);
            incrementBlockedCount();

            // Flood kick eşiği kontrolü
            if (remaining < floodKickThreshold) {
                // Oyuncuyu kick et — ana thread'de
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        Component kickMessage = plugin.getMessageManager()
                                .getMessage("engelleme.jeton-kovasi-kick");
                        player.kick(kickMessage);
                    }
                });

                logExploit(player.getName(),
                        String.format("Flood tespiti! Kova: %s, Token: %d, Eşik: %d",
                                bucketType.name(), remaining, floodKickThreshold));

                // Oyuncu verisini temizle
                playerBuckets.remove(uuid);
            }
        }
    }

    /**
     * Paket türünü kova kategorisine sınıflandırır
     */
    @NotNull
    private BucketType classifyPacket(@NotNull PacketType.Play.Client packetType) {
        if (MOVEMENT_PACKETS.contains(packetType)) return BucketType.HAREKET;
        if (CHAT_PACKETS.contains(packetType)) return BucketType.SOHBET;
        if (INVENTORY_PACKETS.contains(packetType)) return BucketType.ENVANTER;
        return BucketType.DIGER;
    }

    /**
     * Oyuncu için kova alır veya oluşturur
     */
    @NotNull
    private TokenBucket getOrCreateBucket(@NotNull UUID uuid, @NotNull BucketType type) {
        Map<BucketType, TokenBucket> buckets = playerBuckets.computeIfAbsent(uuid,
                k -> new ConcurrentHashMap<>());
        return buckets.computeIfAbsent(type, k -> createBucket(type));
    }

    /**
     * Kova türüne göre yeni TokenBucket oluşturur
     */
    @NotNull
    private TokenBucket createBucket(@NotNull BucketType type) {
        return switch (type) {
            case HAREKET -> new TokenBucket(hareketKapasite, hareketDolum);
            case SOHBET -> new TokenBucket(sohbetKapasite, sohbetDolum);
            case ENVANTER -> new TokenBucket(envanterKapasite, envanterDolum);
            case DIGER -> new TokenBucket(digerKapasite, digerDolum);
        };
    }

    /**
     * Oyuncu verisini temizler (çıkışta)
     *
     * @param uuid Oyuncu UUID'si
     */
    public void removePlayerData(@NotNull UUID uuid) {
        playerBuckets.remove(uuid);
    }

    /**
     * Bellek temizliği — bağlı olmayan oyuncuları kaldırır
     */
    public void cleanup() {
        playerBuckets.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });
    }
}
