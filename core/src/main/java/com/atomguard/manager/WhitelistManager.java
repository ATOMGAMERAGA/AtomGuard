package com.atomguard.manager;

import com.atomguard.AtomGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AtomGuard kalıcı whitelist yöneticisi.
 *
 * <p>Bukkit'in {@code atomguard.bypass} permission'ına ek olarak, isim-bazlı
 * kalıcı bir whitelist sunar. Persistent storage: JSON dosyası
 * ({@code plugins/AtomGuard/whitelist.json}).
 *
 * <p>Yapı: UUID → display-name map'i (display-name son bilinen isim, isim
 * değiştirme durumlarında kullanıcı dostu listeleme için tutulur).
 *
 * <p>Tüm okuma operasyonları lock-free. Yazma operasyonları (add/remove)
 * dosyayı asenkron olarak diske flush eder.
 *
 * @author AtomGuard Team
 * @version 2.3.0
 */
public class WhitelistManager {

    private static final String FILE_NAME = "whitelist.json";

    private final AtomGuard plugin;
    private final Path filePath;
    private final Map<UUID, String> whitelist = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public WhitelistManager(@NotNull AtomGuard plugin) {
        this.plugin = plugin;
        this.filePath = plugin.getDataFolder().toPath().resolve(FILE_NAME);
    }

    /** Diskten yükle (eklenti enable olurken çağrılır) */
    public void load() {
        try {
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent());
                return;
            }
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            if (json.isBlank()) return;
            Map<String, String> raw = gson.fromJson(json,
                    new TypeToken<Map<String, String>>(){}.getType());
            if (raw != null) {
                whitelist.clear();
                for (Map.Entry<String, String> e : raw.entrySet()) {
                    try {
                        whitelist.put(UUID.fromString(e.getKey()), e.getValue());
                    } catch (IllegalArgumentException ignored) {
                        // Bozuk UUID — yoksay
                    }
                }
                plugin.getLogger().info("Whitelist yüklendi: " + whitelist.size() + " oyuncu.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Whitelist yüklenemedi: " + e.getMessage());
        }
    }

    /** Asenkron diske kaydet */
    public void save() {
        Map<String, String> snapshot = new HashMap<>();
        whitelist.forEach((uuid, name) -> snapshot.put(uuid.toString(), name));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, gson.toJson(snapshot), StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Whitelist kaydedilemedi: " + e.getMessage());
            }
        });
    }

    /** Oyuncu whitelist'te mi? */
    public boolean isWhitelisted(@NotNull UUID uuid) {
        return whitelist.containsKey(uuid);
    }

    /** İsme göre arama (case-insensitive) */
    public boolean isWhitelistedByName(@NotNull String name) {
        return whitelist.values().stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    /** UUID + display-name ile ekle */
    public boolean add(@NotNull UUID uuid, @NotNull String displayName) {
        if (whitelist.containsKey(uuid)) return false;
        whitelist.put(uuid, displayName);
        save();
        return true;
    }

    /** OfflinePlayer üzerinden ekle (sık kullanım) */
    public boolean add(@NotNull OfflinePlayer player) {
        if (player.getUniqueId() == null) return false;
        String name = player.getName();
        return add(player.getUniqueId(), name != null ? name : player.getUniqueId().toString());
    }

    /** UUID ile çıkar */
    public boolean remove(@NotNull UUID uuid) {
        if (whitelist.remove(uuid) == null) return false;
        save();
        return true;
    }

    /** İsim ile çıkar (case-insensitive). İlk eşleşen kaldırılır. */
    public boolean removeByName(@NotNull String name) {
        UUID found = null;
        for (Map.Entry<UUID, String> e : whitelist.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                found = e.getKey();
                break;
            }
        }
        if (found == null) return false;
        whitelist.remove(found);
        save();
        return true;
    }

    /** Tüm whitelist girişleri (UUID → name). Immutable copy. */
    @NotNull
    public Map<UUID, String> getAll() {
        return Map.copyOf(whitelist);
    }

    public int size() {
        return whitelist.size();
    }

    /** Whitelist'teki bir UUID için kaydedilmiş display-name */
    @Nullable
    public String getName(@NotNull UUID uuid) {
        return whitelist.get(uuid);
    }
}
