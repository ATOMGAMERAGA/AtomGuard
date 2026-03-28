package com.atomguard.velocity.manager;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.data.PlayerBehaviorProfile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BehaviorManager {

    private final AtomGuardVelocity plugin;
    private final Map<String, PlayerBehaviorProfile> profiles = new ConcurrentHashMap<>();

    public BehaviorManager(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    public void loadFromDatabase() {
        if (plugin.getStorageProvider() == null) return;
        plugin.getStorageProvider().loadBehaviorProfiles().thenAccept(data -> {
            data.forEach((ip, json) -> {
                PlayerBehaviorProfile profile = new PlayerBehaviorProfile(ip);
                profile.loadFromJson(json);
                profiles.put(ip, profile);
            });
            plugin.getSlf4jLogger().info("Veritabanından {} davranış profili yüklendi.", data.size());
        });
    }

    public PlayerBehaviorProfile getProfile(String ip) {
        return profiles.computeIfAbsent(ip, k -> {
            PlayerBehaviorProfile p = new PlayerBehaviorProfile(k);
            // Profil oluşturulurken offline-mode flag'ini hemen set et.
            // recordLogin()'i beklemeden TrustScoreCheck'e doğru flag iletilir.
            boolean offlineMode = plugin.getConfigManager().getBoolean("general.offline-mode", false);
            p.setOfflineModeLenient(offlineMode);
            return p;
        });
    }

    public java.util.Collection<PlayerBehaviorProfile> getAllProfiles() {
        return java.util.Collections.unmodifiableCollection(profiles.values());
    }

    public void recordViolation(String ip) {
        PlayerBehaviorProfile profile = getProfile(ip);
        profile.recordViolation();
        saveProfile(profile);
    }

    public void recordLogin(String ip, String username) {
        PlayerBehaviorProfile profile = getProfile(ip);
        // Offline-mode sunucularda aynı IP'den farklı kullanıcı adıyla giriş normaldir (aile, cracked).
        // Config'den offline-mode ayarını oku ve profildeki penaltıyı devre dışı bırak.
        boolean offlineMode = plugin.getConfigManager().getBoolean("general.offline-mode", false);
        profile.setOfflineModeLenient(offlineMode);
        profile.recordSession(username, "Unknown");
        profile.recordLoginSuccess();
        saveProfile(profile);
    }

    private void saveProfile(PlayerBehaviorProfile profile) {
        if (plugin.getStorageProvider() != null) {
            plugin.getStorageProvider().saveBehaviorProfile(profile);
        }
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - (7 * 86_400_000L); // 7 gün aktif olmayanları temizle
        profiles.entrySet().removeIf(e -> e.getValue().getLastSeen() < cutoff);
    }
}
