package com.atomguard.trust;

import com.atomguard.AtomGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Oyuncu güven puanı yöneticisi.
 * JSON kalıcılığı, periyodik hesaplama ve bypass kontrolü sağlar.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class TrustScoreManager {

    private final AtomGuard plugin;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;
    private final File trustFile;

    /** UUID → TrustProfile */
    private final ConcurrentHashMap<UUID, TrustProfile> profiles = new ConcurrentHashMap<>();

    /** UUID → son giriş günü (günde 1 kez uniqueLoginDays artırmak için) */
    private final ConcurrentHashMap<UUID, String> lastLoginDay = new ConcurrentHashMap<>();

    // Config
    private final boolean enabled;
    private final int basePuan;
    private final int veteranEsik;
    private final int trustedEsik;
    private final int regularEsik;
    private final int attackBypassEsik;
    private final boolean botKontrolBypass;
    private final boolean vpnKontrolBypass;
    private final int saveIntervalMinutes;
    private final int updateIntervalMinutes;
    private final long recentViolationResetMs;

    public TrustScoreManager(AtomGuard plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.trustFile = new File(plugin.getDataFolder(), "trust-scores.json");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AtomGuard-Trust");
            t.setDaemon(true);
            return t;
        });

        this.enabled = plugin.getConfig().getBoolean("guven-skoru.aktif", true);
        this.basePuan = plugin.getConfig().getInt("guven-skoru.baz-puan", 20);
        this.regularEsik = plugin.getConfig().getInt("guven-skoru.esikler.regular", 30);
        this.trustedEsik = plugin.getConfig().getInt("guven-skoru.esikler.trusted", 60);
        this.veteranEsik = plugin.getConfig().getInt("guven-skoru.esikler.veteran", 85);
        this.attackBypassEsik = plugin.getConfig().getInt("guven-skoru.bypass.saldiri-modu-esik", 70);
        this.botKontrolBypass = plugin.getConfig().getBoolean("guven-skoru.bypass.bot-kontrol-atla", true);
        this.vpnKontrolBypass = plugin.getConfig().getBoolean("guven-skoru.bypass.vpn-kontrol-atla", false);
        this.saveIntervalMinutes = plugin.getConfig().getInt("guven-skoru.kaydetme-araligi-dk", 10);
        this.updateIntervalMinutes = plugin.getConfig().getInt("guven-skoru.guncelleme-araligi-dk", 5);
        long resetHours = plugin.getConfig().getLong("guven-skoru.ihlal-sifirlama-saat", 24);
        this.recentViolationResetMs = resetHours * 60 * 60 * 1000L;
    }

    public void start() {
        if (!enabled) return;
        load();

        // Puan hesaplama
        scheduler.scheduleAtFixedRate(this::updateScores,
            updateIntervalMinutes, updateIntervalMinutes, TimeUnit.MINUTES);

        // Kaydetme
        scheduler.scheduleAtFixedRate(this::save,
            saveIntervalMinutes, saveIntervalMinutes, TimeUnit.MINUTES);

        // Son 24 saat ihlal sıfırlama
        scheduler.scheduleAtFixedRate(this::resetRecentViolations,
            1, 1, TimeUnit.HOURS);

        plugin.getLogger().info("Güven Puanı sistemi başlatıldı (" + profiles.size() + " profil yüklendi).");
    }

    public void stop() {
        save();
        scheduler.shutdown();
        try { scheduler.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * UUID için profil al veya oluştur.
     */
    public TrustProfile getOrCreate(UUID uuid) {
        return profiles.computeIfAbsent(uuid, TrustProfile::new);
    }

    /**
     * Oyuncu güven puanını al.
     */
    public double getScore(UUID uuid) {
        TrustProfile profile = profiles.get(uuid);
        if (profile == null) return basePuan;
        return profile.getTrustScore();
    }

    /**
     * Oyuncu güven kademesini al.
     */
    public TrustTier getTier(UUID uuid) {
        return TrustTier.fromScore(getScore(uuid));
    }

    /**
     * Oyuncu giriş kaydı — login günü takibi ve session başlatma.
     */
    public void recordJoin(Player player) {
        if (!enabled) return;
        UUID uuid = player.getUniqueId();
        TrustProfile profile = getOrCreate(uuid);
        profile.setLastKnownName(player.getName());
        profile.setLastJoinTimestamp(System.currentTimeMillis());
        profile.markSessionStart();

        // Günde 1 kez uniqueLoginDays artır
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        String lastDay = lastLoginDay.get(uuid);
        if (!today.equals(lastDay)) {
            profile.incrementUniqueLoginDays();
            lastLoginDay.put(uuid, today);
        }
    }

    /**
     * Oyuncu çıkış kaydı — oynama süresi ve temiz oturum takibi.
     */
    public void recordQuit(Player player) {
        if (!enabled) return;
        UUID uuid = player.getUniqueId();
        TrustProfile profile = profiles.get(uuid);
        if (profile == null) return;

        // Oturum süresini ekle
        if (profile.getCurrentSessionStart() > 0) {
            long sessionMs = System.currentTimeMillis() - profile.getCurrentSessionStart();
            int sessionMinutes = (int) (sessionMs / 60_000);
            profile.addPlaytimeMinutes(sessionMinutes);
        }

        // Temiz oturum sayacı
        if (!profile.isHadViolationThisSession()) {
            profile.incrementConsecutiveCleanSessions();
        }

        // Puan güncelle
        recalculate(profile);
    }

    /**
     * İhlal kaydı — tüm negatif sayaçları günceller.
     */
    public void recordViolation(UUID uuid, String moduleName) {
        if (!enabled) return;
        TrustProfile profile = getOrCreate(uuid);
        profile.incrementTotalViolations();
        profile.incrementRecentViolations();
        profile.resetConsecutiveCleanSessions();
        profile.setLastViolationTimestamp(System.currentTimeMillis());
        profile.setHadViolationThisSession(true);
        recalculate(profile);

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogManager().debug("[Trust] " + profile.getLastKnownName() + " ihlal: "
                + moduleName + " → puan: " + String.format("%.1f", profile.getTrustScore()));
        }
    }

    /**
     * Kick kaydı.
     */
    public void recordKick(UUID uuid) {
        if (!enabled) return;
        TrustProfile profile = getOrCreate(uuid);
        profile.incrementKickCount();
        recalculate(profile);
    }

    /**
     * Şüpheli paket kaydı.
     */
    public void recordSuspiciousPacket(UUID uuid) {
        if (!enabled) return;
        TrustProfile profile = getOrCreate(uuid);
        profile.incrementSuspiciousPacketCount();
    }

    /**
     * Manuel puan ayarlama.
     */
    public void setScore(UUID uuid, String playerName, double score) {
        TrustProfile profile = getOrCreate(uuid);
        if (playerName != null) profile.setLastKnownName(playerName);
        profile.setTrustScore(score);
        profile.setLastCalculation(System.currentTimeMillis());
    }

    /**
     * Profil sıfırlama.
     */
    public void resetProfile(UUID uuid, String playerName) {
        TrustProfile fresh = new TrustProfile(uuid);
        if (playerName != null) fresh.setLastKnownName(playerName);
        fresh.setTrustScore(basePuan);
        profiles.put(uuid, fresh);
    }

    /**
     * UUID'nin saldırı modunu bypass edip edemeyeceğini kontrol eder.
     */
    public boolean canBypassAttackMode(UUID uuid) {
        return getScore(uuid) >= attackBypassEsik;
    }

    /**
     * Bot kontrolü bypass kontrolü.
     */
    public boolean canBypassBotCheck(UUID uuid) {
        return botKontrolBypass && getScore(uuid) >= trustedEsik;
    }

    /**
     * VPN kontrolü bypass kontrolü.
     */
    public boolean canBypassVpnCheck(UUID uuid) {
        return vpnKontrolBypass && getScore(uuid) >= veteranEsik;
    }

    /**
     * En yüksek puanlı oyuncuları listeler.
     */
    public List<Map.Entry<UUID, TrustProfile>> getTopPlayers(int limit) {
        return profiles.entrySet().stream()
            .filter(e -> e.getValue().getLastKnownName() != null)
            .sorted((a, b) -> Double.compare(b.getValue().getTrustScore(), a.getValue().getTrustScore()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * İsme göre profil ara.
     */
    public Optional<TrustProfile> findByName(String name) {
        return profiles.values().stream()
            .filter(p -> name.equalsIgnoreCase(p.getLastKnownName()))
            .findFirst();
    }

    private void recalculate(TrustProfile profile) {
        double score = TrustScoreCalculator.calculate(profile, basePuan);
        profile.setTrustScore(score);
        profile.setLastCalculation(System.currentTimeMillis());
        profile.setLastCalculatedScore(score);
    }

    private void updateScores() {
        try {
            profiles.values().forEach(this::recalculate);
        } catch (Exception e) {
            plugin.getLogger().warning("[Trust] Puan güncelleme hatası: " + e.getMessage());
        }
    }

    private void resetRecentViolations() {
        long now = System.currentTimeMillis();
        profiles.values().forEach(p -> {
            if (now - p.getLastRecentViolationReset() >= recentViolationResetMs) {
                p.resetRecentViolations();
            }
        });
    }

    public void load() {
        if (!trustFile.exists()) return;
        try (Reader reader = new InputStreamReader(new java.io.FileInputStream(trustFile), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<TrustProfile>>(){}.getType();
            List<TrustProfile> list = gson.fromJson(reader, listType);
            if (list != null) {
                for (TrustProfile p : list) {
                    if (p.getUuid() != null) profiles.put(p.getUuid(), p);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Trust] trust-scores.json yüklenemedi: " + e.getMessage());
        }
    }

    public void save() {
        try {
            List<TrustProfile> list = new ArrayList<>(profiles.values());
            String json = gson.toJson(list);
            try (Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(trustFile), StandardCharsets.UTF_8)) {
                writer.write(json);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Trust] trust-scores.json kaydedilemedi: " + e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }
    public int getBasePuan() { return basePuan; }
    public int getTrustedEsik() { return trustedEsik; }
    public int getVeteranEsik() { return veteranEsik; }
    public ConcurrentHashMap<UUID, TrustProfile> getProfiles() { return profiles; }
}
