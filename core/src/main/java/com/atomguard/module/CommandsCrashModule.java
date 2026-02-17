package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Komut Crash Modülü
 *
 * Zararlı komutları tespit eder ve engeller.
 * Regex pattern matching kullanarak crash exploit'lerini önler.
 *
 * Özellikler:
 * - Regex tabanlı komut engelleme
 * - Özelleştirilebilir engelleme listesi
 * - Büyük/küçük harf duyarsız kontrol
 * - Wildcard ve pattern desteği
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class CommandsCrashModule extends AbstractModule implements Listener {

    // Engellenecek komut pattern'lari
    private final List<Pattern> blockedPatterns;

    /**
     * CommandsCrashModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public CommandsCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "komut-crash", "Zararlı komutları engeller");
        this.blockedPatterns = new ArrayList<>();
    }

    @Override
    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        // PacketEvents listener - Merkezi Listener üzerinden
        registerReceiveHandler(PacketType.Play.Client.UPDATE_JIGSAW_BLOCK, this::handleUpdateBlock);
        registerReceiveHandler(PacketType.Play.Client.UPDATE_STRUCTURE_BLOCK, this::handleUpdateBlock);

        debug("Modül aktifleştirildi. Engellenecek pattern sayısı: " + blockedPatterns.size());
    }

    @Override
    public void onDisable() {
        super.onDisable();

        // Pattern'lari temizle
        blockedPatterns.clear();

        debug("Modül devre dışı bırakıldı.");
    }

    private void handleUpdateBlock(PacketReceiveEvent event) {
        if (!isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Survival oyuncusu bu paketleri gönderemez!
        if (player.getGameMode() != GameMode.CREATIVE || !player.isOp()) {
            event.setCancelled(true);
            blockExploit(player, "Yetkisiz Jigsaw/Structure Block güncelleme girişimi!");
        }
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        blockedPatterns.clear();

        List<String> commandList = plugin.getConfigManager()
            .getConfig()
            .getStringList("moduller." + name + ".engellenen-komutlar");

        if (commandList == null || commandList.isEmpty()) {
            // Varsayılan engellenecek komutlar
            commandList = getDefaultBlockedCommands();
        }

        // Regex pattern'lari oluştur
        for (String cmd : commandList) {
            try {
                Pattern pattern = Pattern.compile(cmd, Pattern.CASE_INSENSITIVE);
                blockedPatterns.add(pattern);
                debug("Pattern eklendi: " + cmd);
            } catch (PatternSyntaxException e) {
                error("Geçersiz regex pattern: " + cmd + " - " + e.getMessage());
            }
        }

        debug("Config yüklendi: " + blockedPatterns.size() + " pattern");
    }

    /**
     * Varsayılan engellenecek komutları döndürür
     */
    @NotNull
    private List<String> getDefaultBlockedCommands() {
        List<String> defaults = new ArrayList<>();

        // Selector exploit'leri — çift parametreli selector'lar
        defaults.add(".*@[aeprs]\\[.*distance=.*,.*distance=.*\\].*");
        defaults.add(".*@[aeprs]\\[.*type=.*,.*type=.*\\].*");

        // Uzun komutlar (crash) — 2000+ karakter
        defaults.add(".{2000,}");

        // Aşırı entity selector — 10+ selector
        defaults.add(".*(@[aeprs]\\[.*\\]){10,}.*");

        // Derin recursive execute — 6+ iç içe execute (normal kullanım 1-3)
        defaults.add(".*/execute(\\s+.*\\s+execute){5,}.*");

        return defaults;
    }

    /**
     * Komut işleme olayını dinler
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        String command = event.getMessage();

        debug(player.getName() + " komutu: " + command);

        // CR-06: Pre-regex length check to prevent ReDoS on massive strings
        if (command.length() > 500) {
            blockExploit(player, String.format("Çok uzun komut: %d karakter (Limit: 500)", command.length()));
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("komut-cok-uzun"));
            return;
        }

        // Her pattern'i kontrol et
        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(command).matches()) {
                // Engellenmesi gereken komut
                blockExploit(player,
                    String.format("Engellenmiş komut kullanımı: %s (Pattern: %s)",
                        command.length() > 100 ? command.substring(0, 100) + "..." : command,
                        pattern.pattern()));

                event.setCancelled(true);

                // Oyuncuya mesaj gönder
                player.sendMessage(plugin.getMessageManager().getMessage("komut-engellendi"));

                debug(player.getName() + " için komut engellendi (pattern match)");
                return;
            }
        }

        // Uzunluk kontrolü (ek güvenlik) — Minecraft vanilya limiti 32500 karakter
        // Not: Yukarıdaki 500 sınırı zaten bunu kapsıyor, ama eski config uyumluluğu için tutabiliriz veya kaldırabiliriz.
        // Kod temizliği için kaldırıyoruz çünkü yukarıdaki check daha katı.
    }

    /**
     * Yeni pattern ekler
     */
    public void addPattern(@NotNull String regex) {
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            blockedPatterns.add(pattern);
            info("Pattern eklendi: " + regex);
        } catch (PatternSyntaxException e) {
            error("Geçersiz regex pattern: " + regex + " - " + e.getMessage());
        }
    }

    /**
     * Pattern kaldırır
     */
    public void removePattern(int index) {
        if (index >= 0 && index < blockedPatterns.size()) {
            Pattern removed = blockedPatterns.remove(index);
            info("Pattern kaldırıldı: " + removed.pattern());
        }
    }

    /**
     * Tüm pattern'lari döndürür
     */
    @NotNull
    public List<String> getPatterns() {
        List<String> patterns = new ArrayList<>();
        for (Pattern pattern : blockedPatterns) {
            patterns.add(pattern.pattern());
        }
        return patterns;
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Pattern sayısı: %d, Engellenen komut: %d",
            blockedPatterns.size(),
            getBlockedCount());
    }
}
