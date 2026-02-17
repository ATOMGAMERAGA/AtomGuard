package com.atomguard.manager;

import com.atomguard.AtomGuard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MiniMessage API kullanarak mesaj yönetimi yapan manager sınıfı
 * Renkli, gradient ve modern mesajlar için Paper Adventure API desteği
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class MessageManager {

    private final AtomGuard plugin;
    private final MiniMessage miniMessage;

    // Mesaj cache sistemi - parse edilmiş mesajları cache'ler
    private final ConcurrentHashMap<String, Component> messageCache;

    /**
     * MessageManager constructor
     *
     * @param plugin Ana plugin instance
     */
    public MessageManager(@NotNull AtomGuard plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.messageCache = new ConcurrentHashMap<>();
    }

    /**
     * Cache'i temizler
     * Config reload'da kullanılır
     */
    public void clearCache() {
        messageCache.clear();
    }

    /**
     * Mesaj yolundan Component oluşturur
     *
     * @param path Messages.yml'deki mesaj yolu
     * @return Parse edilmiş Component
     */
    @NotNull
    public Component getMessage(@NotNull String path) {
        // Atomik cache lookup - computeIfAbsent ile race condition önlenir
        return messageCache.computeIfAbsent(path, key -> {
            // Config'den mesajı al
            String message = plugin.getConfigManager().getMessage(key);
            if (message == null || message.isEmpty()) {
                message = "<red>Mesaj bulunamadı: " + key;
            }

            // MiniMessage ile parse et
            return miniMessage.deserialize(message);
        });
    }

    /**
     * Placeholder'lar ile mesaj oluşturur
     *
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholders Placeholder map'i (key: placeholder adı, value: değer)
     * @return Parse edilmiş Component
     */
    @NotNull
    public Component getMessage(@NotNull String path, @NotNull Map<String, String> placeholders) {
        // Config'den mesajı al
        String message = plugin.getConfigManager().getMessage(path);
        if (message == null || message.isEmpty()) {
            message = "<red>Mesaj bulunamadı: " + path;
        }

        // Placeholder'ları hazırla
        TagResolver.Builder resolverBuilder = TagResolver.builder();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolverBuilder.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }

        // MiniMessage ile parse et
        return miniMessage.deserialize(message, resolverBuilder.build());
    }

    /**
     * Tek placeholder ile mesaj oluşturur
     *
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholder Placeholder adı
     * @param value Placeholder değeri
     * @return Parse edilmiş Component
     */
    @NotNull
    public Component getMessage(@NotNull String path, @NotNull String placeholder, @NotNull String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        return getMessage(path, placeholders);
    }

    /**
     * String mesajı MiniMessage formatında parse eder
     *
     * @param message Parse edilecek mesaj
     * @return Parse edilmiş Component
     */
    @NotNull
    public Component parse(@NotNull String message) {
        return miniMessage.deserialize(message);
    }

    /**
     * String mesajı placeholder'lar ile parse eder
     *
     * @param message Parse edilecek mesaj
     * @param placeholders Placeholder map'i
     * @return Parse edilmiş Component
     */
    @NotNull
    public Component parse(@NotNull String message, @NotNull Map<String, String> placeholders) {
        TagResolver.Builder resolverBuilder = TagResolver.builder();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolverBuilder.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        return miniMessage.deserialize(message, resolverBuilder.build());
    }

    /**
     * Oyuncuya mesaj gönderir
     *
     * @param player Hedef oyuncu
     * @param path Messages.yml'deki mesaj yolu
     */
    public void sendMessage(@NotNull Player player, @NotNull String path) {
        Component message = getMessage(path);
        player.sendMessage(message);
    }

    /**
     * Oyuncuya placeholder'lı mesaj gönderir
     *
     * @param player Hedef oyuncu
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholders Placeholder map'i
     */
    public void sendMessage(@NotNull Player player, @NotNull String path, @NotNull Map<String, String> placeholders) {
        Component message = getMessage(path, placeholders);
        player.sendMessage(message);
    }

    /**
     * Oyuncuya tek placeholder'lı mesaj gönderir
     *
     * @param player Hedef oyuncu
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholder Placeholder adı
     * @param value Placeholder değeri
     */
    public void sendMessage(@NotNull Player player, @NotNull String path, @NotNull String placeholder, @NotNull String value) {
        Component message = getMessage(path, placeholder, value);
        player.sendMessage(message);
    }

    /**
     * CommandSender'a mesaj gönderir
     *
     * @param sender Hedef sender (Player veya Console)
     * @param path Messages.yml'deki mesaj yolu
     */
    public void sendMessage(@NotNull CommandSender sender, @NotNull String path) {
        Component message = getMessage(path);
        sender.sendMessage(message);
    }

    /**
     * CommandSender'a placeholder'lı mesaj gönderir
     *
     * @param sender Hedef sender
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholders Placeholder map'i
     */
    public void sendMessage(@NotNull CommandSender sender, @NotNull String path, @NotNull Map<String, String> placeholders) {
        Component message = getMessage(path, placeholders);
        sender.sendMessage(message);
    }

    /**
     * Oyuncuya prefix ile mesaj gönderir
     *
     * @param player Hedef oyuncu
     * @param path Messages.yml'deki mesaj yolu
     */
    public void sendPrefixedMessage(@NotNull Player player, @NotNull String path) {
        Component prefix = getMessage("genel.onek");
        Component message = getMessage(path);
        player.sendMessage(prefix.append(Component.space()).append(message));
    }

    /**
     * Oyuncuya prefix ve placeholder'lı mesaj gönderir
     *
     * @param player Hedef oyuncu
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholders Placeholder map'i
     */
    public void sendPrefixedMessage(@NotNull Player player, @NotNull String path, @NotNull Map<String, String> placeholders) {
        Component prefix = getMessage("genel.onek");
        Component message = getMessage(path, placeholders);
        player.sendMessage(prefix.append(Component.space()).append(message));
    }

    /**
     * CommandSender'a prefix ile mesaj gönderir
     *
     * @param sender Hedef sender
     * @param path Messages.yml'deki mesaj yolu
     */
    public void sendPrefixedMessage(@NotNull CommandSender sender, @NotNull String path) {
        Component prefix = getMessage("genel.onek");
        Component message = getMessage(path);
        sender.sendMessage(prefix.append(Component.space()).append(message));
    }

    /**
     * CommandSender'a prefix ve placeholder'lı mesaj gönderir
     *
     * @param sender Hedef sender
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholders Placeholder map'i
     */
    public void sendPrefixedMessage(@NotNull CommandSender sender, @NotNull String path, @NotNull Map<String, String> placeholders) {
        Component prefix = getMessage("genel.onek");
        Component message = getMessage(path, placeholders);
        sender.sendMessage(prefix.append(Component.space()).append(message));
    }

    /**
     * Oyuncuya action bar mesajı gönderir
     *
     * @param player Hedef oyuncu
     * @param message Mesaj string'i
     */
    public void sendActionBar(@NotNull Player player, @NotNull String message) {
        Component component = parse(message);
        player.sendActionBar(component);
    }

    /**
     * Oyuncuya action bar mesajı gönderir (mesaj yolu ile)
     *
     * @param player Hedef oyuncu
     * @param path Messages.yml'deki mesaj yolu
     */
    public void sendActionBarFromPath(@NotNull Player player, @NotNull String path) {
        Component component = getMessage(path);
        player.sendActionBar(component);
    }

    /**
     * Oyuncuya title gönderir
     *
     * @param player Hedef oyuncu
     * @param title Başlık metni
     * @param subtitle Alt başlık metni
     * @param fadeIn Fade in süresi (tick)
     * @param stay Kalma süresi (tick)
     * @param fadeOut Fade out süresi (tick)
     */
    public void sendTitle(@NotNull Player player, @NotNull String title, @Nullable String subtitle,
                          int fadeIn, int stay, int fadeOut) {
        Component titleComponent = parse(title);
        Component subtitleComponent = subtitle != null ? parse(subtitle) : Component.empty();

        player.showTitle(net.kyori.adventure.title.Title.title(
            titleComponent,
            subtitleComponent,
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(fadeIn * 50L),
                java.time.Duration.ofMillis(stay * 50L),
                java.time.Duration.ofMillis(fadeOut * 50L)
            )
        ));
    }

    /**
     * Broadcast mesajı gönderir (tüm oyunculara)
     *
     * @param path Messages.yml'deki mesaj yolu
     */
    public void broadcast(@NotNull String path) {
        Component message = getMessage(path);
        plugin.getServer().broadcast(message);
    }

    /**
     * Broadcast mesajı gönderir (placeholder'lı)
     *
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholders Placeholder map'i
     */
    public void broadcast(@NotNull String path, @NotNull Map<String, String> placeholders) {
        Component message = getMessage(path, placeholders);
        plugin.getServer().broadcast(message);
    }

    /**
     * İzne sahip oyunculara bildirim gönderir
     *
     * @param permission Gerekli izin
     * @param path Messages.yml'deki mesaj yolu
     */
    public void notifyWithPermission(@NotNull String permission, @NotNull String path) {
        Component message = getMessage(path);
        plugin.getServer().getOnlinePlayers().stream()
            .filter(player -> player.hasPermission(permission))
            .forEach(player -> player.sendMessage(message));
    }

    /**
     * İzne sahip oyunculara bildirim gönderir (placeholder'lı)
     *
     * @param permission Gerekli izin
     * @param path Messages.yml'deki mesaj yolu
     * @param placeholders Placeholder map'i
     */
    public void notifyWithPermission(@NotNull String permission, @NotNull String path, @NotNull Map<String, String> placeholders) {
        Component message = getMessage(path, placeholders);
        plugin.getServer().getOnlinePlayers().stream()
            .filter(player -> player.hasPermission(permission))
            .forEach(player -> player.sendMessage(message));
    }
}
