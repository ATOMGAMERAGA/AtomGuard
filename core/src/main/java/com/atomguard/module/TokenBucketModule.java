package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.module.OfflinePacketModule;
import com.atomguard.util.TokenBucket;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token Bucket Rate Limiter Modülü
 *
 * Her oyuncu için 6 ayrı kova ile paket rate limiting uygular:
 * - HAREKET: Position, PositionRotation, Rotation paketleri
 * - SOHBET: Chat mesaj paketleri (komutlar HARİÇ)
 * - KOMUT: ChatCommand paketleri — auth komutları tamamen muaf
 * - ENVANTER: WindowClick, CreativeSlot, CloseWindow paketleri
 * - ETKILESIM: PlayerDigging, BlockPlacement, UseItem, Animation, InteractEntity
 * - DIGER: Geri kalan tüm client→server paketleri
 *
 * Token ≤ 0 → paketi sessizce düşür (bilgi sızıntısı önleme)
 * Token < kickThreshold → oyuncuyu kick et (sürekli flood)
 *
 * @author AtomGuard Team
 * @version 2.0.5
 */
public class TokenBucketModule extends AbstractModule {

    /** Kova türleri */
    private enum BucketType {
        HAREKET, SOHBET, KOMUT, ENVANTER, ETKILESIM, DIGER
    }

    /** Hareket paketleri — PLAYER_FLYING dahil (client her tick gönderir) */
    private static final Set<PacketType.Play.Client> MOVEMENT_PACKETS = Set.of(
            PacketType.Play.Client.PLAYER_POSITION,
            PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION,
            PacketType.Play.Client.PLAYER_ROTATION,
            PacketType.Play.Client.PLAYER_FLYING
    );

    /** Sohbet paketleri — CHAT_COMMAND ARTIK BURAYA DAHİL DEĞİL */
    private static final Set<PacketType.Play.Client> CHAT_PACKETS = Set.of(
            PacketType.Play.Client.CHAT_MESSAGE
    );

    /** Komut paketleri — auth komutları bu kovadan da muaf tutulacak */
    private static final Set<PacketType.Play.Client> COMMAND_PACKETS = Set.of(
            PacketType.Play.Client.CHAT_COMMAND
    );

    /** Envanter paketleri */
    private static final Set<PacketType.Play.Client> INVENTORY_PACKETS = Set.of(
            PacketType.Play.Client.CLICK_WINDOW,
            PacketType.Play.Client.CREATIVE_INVENTORY_ACTION,
            PacketType.Play.Client.CLOSE_WINDOW
    );

    /** Etkileşim paketleri — blok kırma, yerleştirme, entity etkileşimi */
    private static final Set<PacketType.Play.Client> INTERACTION_PACKETS = Set.of(
            PacketType.Play.Client.PLAYER_DIGGING,
            PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT,
            PacketType.Play.Client.USE_ITEM,
            PacketType.Play.Client.ANIMATION,
            PacketType.Play.Client.INTERACT_ENTITY
    );

    /** Oyuncu başına 5 kova — ConcurrentHashMap[UUID → BucketType → TokenBucket] */
    private final Map<UUID, Map<BucketType, TokenBucket>> playerBuckets = new ConcurrentHashMap<>();

    // Config cache
    private long hareketKapasite;
    private long hareketDolum;
    private long sohbetKapasite;
    private long sohbetDolum;
    private long komutKapasite;
    private long komutDolum;
    private long envanterKapasite;
    private long envanterDolum;
    private long etkilesimKapasite;
    private long etkilesimDolum;
    private long digerKapasite;
    private long digerDolum;
    private long floodKickThreshold;

    /** Auth komutu muafiyeti — bu komutlar hiç rate limit'lenmez */
    private Set<String> authExemptCommands;

    /**
     * TokenBucketModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public TokenBucketModule(@NotNull AtomGuard plugin) {
        super(plugin, "token-bucket", "Token bucket rate limiter");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        loadConfig();

        // Tüm paketleri dinlemek için null type kullanıyoruz (Merkezi Listener)
        registerReceiveHandler(null, this::handlePacketReceive);

        debug("Token bucket modülü başlatıldı. Kick eşiği: " + floodKickThreshold +
              ", Auth muaf komut: " + authExemptCommands.size());
    }

    @Override
    public void onDisable() {
        super.onDisable();
        playerBuckets.clear();
        debug("Token bucket modülü durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.hareketKapasite = getConfigLong("buckets.movement.kapasite", 200L);
        this.hareketDolum = getConfigLong("buckets.movement.dolum-saniye", 80L);
        this.sohbetKapasite = getConfigLong("buckets.chat.kapasite", 20L);
        this.sohbetDolum = getConfigLong("buckets.chat.dolum-saniye", 5L);
        this.komutKapasite = getConfigLong("buckets.command.kapasite", 50L);
        this.komutDolum = getConfigLong("buckets.command.dolum-saniye", 20L);
        this.envanterKapasite = getConfigLong("buckets.inventory.kapasite", 100L);
        this.envanterDolum = getConfigLong("buckets.inventory.dolum-saniye", 50L);
        this.etkilesimKapasite = getConfigLong("buckets.interaction.kapasite", 300L);
        this.etkilesimDolum = getConfigLong("buckets.interaction.dolum-saniye", 120L);
        this.digerKapasite = getConfigLong("buckets.other.kapasite", 150L);
        this.digerDolum = getConfigLong("buckets.other.dolum-saniye", 60L);
        this.floodKickThreshold = getConfigLong("flood-kick-threshold", -200L);

        // Auth komutu muafiyeti — config'den veya varsayılan liste
        List<String> exemptList = plugin.getConfigManager()
                .getStringList("modules." + getName() + ".auth-exempt-commands");
        if (exemptList == null || exemptList.isEmpty()) {
            exemptList = List.of("login", "l", "register", "reg", "changepassword", "cp",
                                 "giriş", "giris", "kayıt", "kayit");
        }
        this.authExemptCommands = new HashSet<>(exemptList);
    }

    /**
     * Paket alındığında kova kontrolü yapar
     */
    private void handlePacketReceive(@NotNull PacketReceiveEvent event) {
        if (!isEnabled()) return;

        // Sadece Play aşamasındaki paketleri kontrol et
        if (!(event.getPacketType() instanceof PacketType.Play.Client clientPacket)) return;

        if (!(event.getPlayer() instanceof Player player)) return;

        // Auth grace period kontrolü — auth bekleyen oyunculara rate limit uygulanmaz
        OfflinePacketModule offlineModule = plugin.getModuleManager().getModule(OfflinePacketModule.class);
        if (offlineModule != null && offlineModule.isInGracePeriod(player.getUniqueId())) {
            return;
        }

        // CHAT_COMMAND paketleri için auth komut muafiyeti
        if (clientPacket == PacketType.Play.Client.CHAT_COMMAND) {
            try {
                WrapperPlayClientChatCommand wrapper = new WrapperPlayClientChatCommand(event);
                String command = wrapper.getCommand().split(" ")[0].toLowerCase();
                if (authExemptCommands.contains(command)) {
                    return; // Auth komutu — rate limit YOK
                }
            } catch (Exception e) {
                // Paket parse edilemezse sessizce geç
                debug("CHAT_COMMAND parse hatası: " + e.getMessage());
            }
        }

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

                blockExploit(player, String.format("Flood tespiti! Kova: %s, Token: %d, Eşik: %d",
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
        if (COMMAND_PACKETS.contains(packetType)) return BucketType.KOMUT;
        if (INVENTORY_PACKETS.contains(packetType)) return BucketType.ENVANTER;
        if (INTERACTION_PACKETS.contains(packetType)) return BucketType.ETKILESIM;
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
            case KOMUT -> new TokenBucket(komutKapasite, komutDolum);
            case ENVANTER -> new TokenBucket(envanterKapasite, envanterDolum);
            case ETKILESIM -> new TokenBucket(etkilesimKapasite, etkilesimDolum);
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
