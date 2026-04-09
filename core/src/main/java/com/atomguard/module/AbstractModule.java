package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.api.event.ExploitBlockedEvent;
import com.atomguard.api.module.IModule;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Abstract base class for all AtomGuard exploit-fixer modules.
 *
 * <p>Every module extends this class and provides a Turkish config key as its {@code name},
 * which maps to the {@code moduller.<name>} section in {@code config.yml}. Helper methods
 * ({@code getConfigBoolean}, {@code getConfigInt}, etc.) automatically prefix the config path
 * with the module name.
 *
 * <p>Key behaviors provided by this base class:
 * <ul>
 *   <li><b>Automatic Bukkit event registration:</b> If the subclass implements {@link org.bukkit.event.Listener},
 *       it is registered as a Bukkit listener in {@code onEnable()} — subclasses must NOT register themselves.</li>
 *   <li><b>Packet handler tracking:</b> Subclasses should use the protected {@code registerReceiveHandler()}
 *       and {@code registerSendHandler()} methods (not {@code plugin.getPacketListener().registerReceiveHandler()})
 *       so that handlers are properly tracked and cleaned up on {@code onDisable()}.</li>
 *   <li><b>Thread-safe state:</b> The {@code enabled} flag is {@code volatile}; the block counter
 *       uses {@link java.util.concurrent.atomic.AtomicLong}.</li>
 *   <li><b>Exploit blocking:</b> {@code blockExploit()} fires the {@link com.atomguard.api.event.ExploitBlockedEvent},
 *       updates heuristics, trust scores, and forensics asynchronously.</li>
 * </ul>
 *
 * @see com.atomguard.api.module.IModule
 * @see com.atomguard.manager.ModuleManager
 */
public abstract class AbstractModule implements IModule {

    protected final AtomGuard plugin;
    protected final String name;
    protected final String description;

    // Modül durumu (thread-safe)
    protected volatile boolean enabled;

    // Engelleme sayacı (thread-safe)
    protected final AtomicLong blockedCount;

    // Packet handler tracking
    private final List<AbstractMap.SimpleEntry<PacketTypeCommon, Consumer<PacketReceiveEvent>>> registeredReceiveHandlers = new ArrayList<>();
    private final List<AbstractMap.SimpleEntry<PacketTypeCommon, Consumer<PacketSendEvent>>> registeredSendHandlers = new ArrayList<>();

    // Alert cooldown — aynı uyarının 30sn içinde tekrar gönderilmesini önler
    private final Map<String, Long> alertCooldowns = new ConcurrentHashMap<>();

    /** Admin bildirim izni */
    private static final String ADMIN_NOTIFY_PERMISSION = "atomguard.admin.notify";
    /** Alert cooldown süresi (ms) */
    private static final long ALERT_COOLDOWN_MS = 30_000L;

    /**
     * AbstractModule constructor
     *
     * @param plugin Ana plugin instance
     * @param name Modül adı (config'deki key ile aynı olmalı)
     * @param description Modül açıklaması
     */
    public AbstractModule(@NotNull AtomGuard plugin, @NotNull String name, @NotNull String description) {
        this.plugin = plugin;
        this.name = name;
        this.description = description;
        this.enabled = false;
        this.blockedCount = new AtomicLong(0);
    }

    /**
     * Modül etkinleştirildiğinde çağrılır
     * Alt sınıflar bu metodu override ederek kendi başlatma kodlarını ekler
     */
    public void onEnable() {
        this.enabled = true;
        // Otomatik Bukkit listener kaydı
        if (this instanceof Listener) {
            Bukkit.getPluginManager().registerEvents((Listener) this, plugin);
        }
        plugin.getLogManager().info(name + " module enabled.");
    }

    /**
     * Modül devre dışı bırakıldığında çağrılır
     * Alt sınıflar bu metodu override ederek kendi temizleme kodlarını ekler
     */
    public void onDisable() {
        this.enabled = false;
        
        // Otomatik packet handler temizliği (Merkezi PacketListener'dan)
        if (plugin.getPacketListener() != null) {
            for (var entry : registeredReceiveHandlers) {
                plugin.getPacketListener().unregisterReceiveHandler(entry.getKey(), entry.getValue());
            }
            for (var entry : registeredSendHandlers) {
                plugin.getPacketListener().unregisterSendHandler(entry.getKey(), entry.getValue());
            }
        }
        registeredReceiveHandlers.clear();
        registeredSendHandlers.clear();

        // Otomatik Bukkit listener temizliği
        if (this instanceof Listener) {
            HandlerList.unregisterAll((Listener) this);
        }
        plugin.getLogManager().info(name + " module disabled.");
    }

    /**
     * Periyodik temizlik için çağrılır
     */
    public void cleanup() {
        // No-op by default
    }

    /**
     * Exploit engellendiğinde çağrılır, istatistikleri artırır, log yazar ve event fire eder.
     */
    protected void blockExploit(@NotNull Player player, @NotNull String details) {
        incrementBlockedCount();
        logExploit(player.getName(), details);

        // Ağır işlemleri async'e taşı — Netty thread'i bloklanmasın
        final java.util.UUID uuid = player.getUniqueId();
        final String ip = (player.getAddress() != null && player.getAddress().getAddress() != null)
                ? player.getAddress().getAddress().getHostAddress() : "0.0.0.0";
        final String playerName = player.getName();
        final String moduleName = getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // API Event fire et
            ExploitBlockedEvent event = new ExploitBlockedEvent(moduleName, player, playerName, ip, details);
            Bukkit.getPluginManager().callEvent(event);

            // Heuristic Engine'e bildir — modül türüne göre ağırlıklı
            if (plugin.getHeuristicEngine() != null) {
                double heuristicWeight;
                switch (moduleName) {
                    case "piston-limiter":
                    case "redstone-limiter":
                    case "falling-block-limiter":
                    case "explosion-limiter":
                        heuristicWeight = 0.02;
                        break;
                    default:
                        heuristicWeight = 0.1;
                }
                plugin.getHeuristicEngine().getProfile(uuid).addSuspicion(heuristicWeight);
            }

            // Trust Score ihlal kaydı
            if (plugin.getTrustScoreManager() != null) {
                plugin.getTrustScoreManager().recordViolation(uuid, moduleName);
            }

            // Forensics — modül engel kaydı
            if (plugin.getForensicsManager() != null && plugin.getForensicsManager().isRecording()) {
                plugin.getForensicsManager().recordModuleBlock(moduleName);
                if (!ip.equals("0.0.0.0")) {
                    plugin.getForensicsManager().recordBlock(ip);
                }
            }

            // CoreMetrics — modul engel kaydı
            if (plugin.getCoreMetrics() != null) {
                plugin.getCoreMetrics().recordModuleBlock(moduleName);
            }
        });
    }

    /**
     * Exploit engellendiğinde çağrılır — mevcut davranışa ek olarak admin bildirim sistemi de tetiklenir.
     *
     * @param player   Exploit'i gerçekleştiren oyuncu
     * @param details  Detay mesajı
     * @param severity Uyarı seviyesi (CRITICAL → title + ses + Discord)
     */
    protected void blockExploit(@NotNull Player player, @NotNull String details, @NotNull AlertSeverity severity) {
        incrementBlockedCount();
        logExploit(player.getName(), details);

        String alertMsg = "<red><bold>⚠ " + severity.name() + ":</bold> <gray>[" + name + "] <white>"
                + player.getName() + " <dark_gray>— <gray>" + details;
        alertAdmins(alertMsg, severity);

        final java.util.UUID uuid = player.getUniqueId();
        final String ip = (player.getAddress() != null && player.getAddress().getAddress() != null)
                ? player.getAddress().getAddress().getHostAddress() : "0.0.0.0";
        final String playerName = player.getName();
        final String moduleName = getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ExploitBlockedEvent event = new ExploitBlockedEvent(moduleName, player, playerName, ip, details);
            Bukkit.getPluginManager().callEvent(event);

            if (plugin.getHeuristicEngine() != null) {
                double heuristicWeight;
                switch (moduleName) {
                    case "piston-limiter":
                    case "redstone-limiter":
                    case "falling-block-limiter":
                    case "explosion-limiter":
                        heuristicWeight = 0.02;
                        break;
                    default:
                        heuristicWeight = 0.1;
                }
                plugin.getHeuristicEngine().getProfile(uuid).addSuspicion(heuristicWeight);
            }

            if (plugin.getTrustScoreManager() != null) {
                plugin.getTrustScoreManager().recordViolation(uuid, moduleName);
            }

            if (plugin.getForensicsManager() != null && plugin.getForensicsManager().isRecording()) {
                plugin.getForensicsManager().recordModuleBlock(moduleName);
                if (!ip.equals("0.0.0.0")) {
                    plugin.getForensicsManager().recordBlock(ip);
                }
            }

            if (plugin.getCoreMetrics() != null) {
                plugin.getCoreMetrics().recordModuleBlock(moduleName);
            }
        });
    }

    /**
     * Online adminlere uyarı mesajı gönderir.
     *
     * <p>Cooldown: aynı mesaj 30 saniye içinde tekrar gönderilmez.
     * Herhangi bir thread'den güvenle çağrılabilir.
     *
     * @param message  MiniMessage formatında mesaj
     * @param severity Uyarı seviyesi — yükseldikçe daha fazla kanal kullanılır
     */
    protected void alertAdmins(@NotNull String message, @NotNull AlertSeverity severity) {
        if (severity == AlertSeverity.LOW) {
            plugin.getLogManager().warning("[ALERT:LOW] [" + name + "] " + message);
            return;
        }

        // Cooldown kontrolü
        String cooldownKey = name + ":" + message.hashCode();
        long now = System.currentTimeMillis();
        Long lastAlert = alertCooldowns.get(cooldownKey);
        if (lastAlert != null && now - lastAlert < ALERT_COOLDOWN_MS) return;
        alertCooldowns.put(cooldownKey, now);

        // Log
        plugin.getLogManager().warning("[ALERT:" + severity + "] [" + name + "] " + message);

        // In-game bildirim — main thread'de çalıştır
        final Component parsedMsg = plugin.getMessageManager().parse(message);
        final boolean playSound = severity.ordinal() >= AlertSeverity.HIGH.ordinal();
        final boolean showTitle = severity == AlertSeverity.CRITICAL;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (!admin.hasPermission(ADMIN_NOTIFY_PERMISSION)) continue;
                admin.sendMessage(parsedMsg);
                if (playSound) {
                    admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                }
                if (showTitle) {
                    admin.showTitle(Title.title(
                            Component.text("⚠ ATOMGUARD", NamedTextColor.RED, TextDecoration.BOLD),
                            Component.text("Kritik exploit algılandı!", NamedTextColor.YELLOW),
                            Title.Times.times(
                                    Duration.ofMillis(500),
                                    Duration.ofSeconds(3),
                                    Duration.ofSeconds(1)
                            )
                    ));
                    admin.playSound(admin.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);
                }
            }
        });

        // Discord webhook — HIGH ve üzeri
        if (playSound && plugin.getDiscordWebhookManager() != null) {
            plugin.getDiscordWebhookManager().notifyExploitBlocked(name, "SYSTEM", message);
        }
    }

    /**
     * Merkezi PacketListener üzerinden receive handler kaydeder
     */
    protected void registerReceiveHandler(PacketTypeCommon type, Consumer<PacketReceiveEvent> handler) {
        plugin.getPacketListener().registerReceiveHandler(type, handler);
        registeredReceiveHandlers.add(new AbstractMap.SimpleEntry<>(type, handler));
    }

    /**
     * Merkezi PacketListener üzerinden send handler kaydeder
     */
    protected void registerSendHandler(PacketTypeCommon type, Consumer<PacketSendEvent> handler) {
        plugin.getPacketListener().registerSendHandler(type, handler);
        registeredSendHandlers.add(new AbstractMap.SimpleEntry<>(type, handler));
    }

    /**
     * Modülün aktif olup olmadığını kontrol eder
     *
     * @return Aktif ise true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Modülün durumunu değiştirir (toggle)
     *
     * @return Yeni durum (true = etkin, false = devre dışı)
     */
    public boolean toggle() {
        if (enabled) {
            onDisable();
        } else {
            onEnable();
        }
        return enabled;
    }

    /**
     * Modül adını alır
     *
     * @return Modül adı
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Modül açıklamasını alır
     *
     * @return Modül açıklaması
     */
    @NotNull
    public String getDescription() {
        return description;
    }

    /**
     * Toplam engelleme sayısını alır
     *
     * @return Engelleme sayısı
     */
    public long getBlockedCount() {
        return blockedCount.get();
    }

    /**
     * Engelleme sayısını sıfırlar
     */
    public void resetBlockedCount() {
        blockedCount.set(0);
    }

    /**
     * Engelleme sayısını artırır
     *
     * @return Yeni engelleme sayısı
     */
    protected long incrementBlockedCount() {
        long count = blockedCount.incrementAndGet();
        // Statistics recording
        if (plugin.getStatisticsManager() != null) {
            plugin.getStatisticsManager().recordBlock(name);
        }
        return count;
    }

    /**
     * Engelleme sayısını belirli bir miktar artırır
     *
     * @param amount Artırılacak miktar
     * @return Yeni engelleme sayısı
     */
    protected long addBlockedCount(long amount) {
        return blockedCount.addAndGet(amount);
    }

    /**
     * Modül hakkında bilgi döner
     *
     * @return Modül bilgisi
     */
    @Override
    public String toString() {
        return String.format("Module{name='%s', enabled=%s, blocked=%d}",
            name, enabled, blockedCount.get());
    }

    /**
     * Ana plugin instance alır
     *
     * @return AtomGuard instance
     */
    @NotNull
    public AtomGuard getPlugin() {
        return plugin;
    }

    /**
     * Config'den boolean değer alır
     *
     * @param key Config anahtarı (moduller.{modül_adı}. otomatik eklenir)
     * @param def Varsayılan değer
     * @return Config değeri
     */
    public boolean getConfigBoolean(@NotNull String key, boolean def) {
        return plugin.getConfigManager().getBoolean("modules." + name + "." + key, def);
    }

    /**
     * Config'den int değer alır
     *
     * @param key Config anahtarı
     * @param def Varsayılan değer
     * @return Config değeri
     */
    public int getConfigInt(@NotNull String key, int def) {
        return plugin.getConfigManager().getInt("modules." + name + "." + key, def);
    }

    /**
     * Config'den long değer alır
     *
     * @param key Config anahtarı
     * @param def Varsayılan değer
     * @return Config değeri
     */
    public long getConfigLong(@NotNull String key, long def) {
        return plugin.getConfigManager().getLong("modules." + name + "." + key, def);
    }

    /**
     * Config'den double değer alır
     *
     * @param key Config anahtarı
     * @param def Varsayılan değer
     * @return Config değeri
     */
    public double getConfigDouble(@NotNull String key, double def) {
        return plugin.getConfigManager().getDouble("modules." + name + "." + key, def);
    }

    /**
     * Config'den string değer alır
     *
     * @param key Config anahtarı
     * @param def Varsayılan değer
     * @return Config değeri
     */
    @NotNull
    public String getConfigString(@NotNull String key, @NotNull String def) {
        return plugin.getConfigManager().getString("modules." + name + "." + key, def);
    }

    /**
     * Debug modunda log yazar
     *
     * @param message Log mesajı
     */
    public void debug(@NotNull String message) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogManager().debug("[" + name + "] " + message);
        }
    }

    /**
     * Info seviyesinde log yazar
     *
     * @param message Log mesajı
     */
    public void info(@NotNull String message) {
        plugin.getLogManager().info("[" + name + "] " + message);
    }

    /**
     * Warning seviyesinde log yazar
     *
     * @param message Log mesajı
     */
    public void warning(@NotNull String message) {
        plugin.getLogManager().warning("[" + name + "] " + message);
    }

    /**
     * Error seviyesinde log yazar
     *
     * @param message Log mesajı
     */
    public void error(@NotNull String message) {
        plugin.getLogManager().error("[" + name + "] " + message);
    }

    /**
     * Exploit engelleme logu yazar
     *
     * @param playerName Oyuncu adı
     * @param details Detaylar
     */
    public void logExploit(@NotNull String playerName, @NotNull String details) {
        plugin.getLogManager().logExploit(playerName, name, details);
        // Discord webhook notification
        if (plugin.getDiscordWebhookManager() != null) {
            plugin.getDiscordWebhookManager().notifyExploitBlocked(name, playerName, details);
        }
        // Web panel event recording
        if (plugin.getWebPanel() != null) {
            plugin.getWebPanel().recordEvent(name, playerName, details);
        }
    }
}
