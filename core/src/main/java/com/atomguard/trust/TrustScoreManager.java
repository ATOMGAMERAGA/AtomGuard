package com.atomguard.trust;

import com.atomguard.AtomGuard;
import com.atomguard.api.trust.ITrustService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Per-player trust scoring system that tracks reputation over time.
 *
 * <p>Implements {@link ITrustService} (the public API interface). Each player has a
 * {@code TrustProfile} containing unique login days, violation history, and a computed
 * trust score. Scores determine trust tiers (new, regular, trusted, veteran) which
 * influence how strictly exploit checks are applied — for example, veteran players may
 * bypass bot detection or attack-mode restrictions.
 *
 * <p><b>Persistence:</b> Profiles are stored in {@code trust-scores.json} inside the plugin
 * data folder and saved periodically by a daemon scheduled executor. Scores are recalculated
 * on a configurable interval, and recent violations decay after a configurable reset period.
 *
 * <p><b>Thread safety:</b> All profile data is held in a {@link ConcurrentHashMap}. The
 * background scheduler runs on a dedicated daemon thread ({@code AtomGuard-Trust}).
 *
 * @see com.atomguard.api.trust.ITrustService
 */
public class TrustScoreManager implements ITrustService {

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

        this.enabled = plugin.getConfig().getBoolean("trust-score.enabled", true);
        this.basePuan = plugin.getConfig().getInt("trust-score.initial-score", 20);
        this.regularEsik = plugin.getConfig().getInt("trust-score.thresholds.regular", 30);
        this.trustedEsik = plugin.getConfig().getInt("trust-score.thresholds.trusted", 60);
        this.veteranEsik = plugin.getConfig().getInt("trust-score.thresholds.veteran", 85);
        this.attackBypassEsik = plugin.getConfig().getInt("trust-score.attack-mode-bypass-min", 70);
        this.botKontrolBypass = plugin.getConfig().getBoolean("trust-score.bot-check-bypass-min", true);
        this.vpnKontrolBypass = plugin.getConfig().getBoolean("trust-score.vpn-check-bypass-min", false);
        this.saveIntervalMinutes = plugin.getConfig().getInt("trust-score.auto-save-minutes", 10);
        this.updateIntervalMinutes = plugin.getConfig().getInt("trust-score.update-interval-minutes", 5);
        long resetHours = plugin.getConfig().getLong("trust-score.violation-reset-hours", 24);
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
     * Oyuncu güven puanını al (double).
     */
    public double getScoreDouble(UUID uuid) {
        TrustProfile profile = profiles.get(uuid);
        if (profile == null) return basePuan;
        return profile.getTrustScore();
    }

    @Override
    public int getScore(@NotNull UUID playerId) {
        return (int) getScoreDouble(playerId);
    }

    /**
     * Oyuncu güven kademesini al (core TrustTier).
     */
    public TrustTier getCoreTier(UUID uuid) {
        return TrustTier.fromScore(getScoreDouble(uuid));
    }

    @Override
    @NotNull
    public com.atomguard.api.trust.TrustTier getTier(@NotNull UUID playerId) {
        return com.atomguard.api.trust.TrustTier.fromScore(getScore(playerId));
    }

    @Override
    public void addBonus(@NotNull UUID playerId, int amount, @NotNull String reason) {
        TrustProfile profile = getOrCreate(playerId);
        double newScore = Math.min(100, profile.getTrustScore() + amount);
        profile.setTrustScore(newScore);
        profile.setLastCalculation(System.currentTimeMillis());
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogManager().debug("[Trust] " + profile.getLastKnownName() + " bonus: +" + amount + " (" + reason + ") → " + String.format("%.1f", newScore));
        }
    }

    @Override
    public void addPenalty(@NotNull UUID playerId, int amount, @NotNull String reason) {
        TrustProfile profile = getOrCreate(playerId);
        double newScore = Math.max(0, profile.getTrustScore() - amount);
        profile.setTrustScore(newScore);
        profile.setLastCalculation(System.currentTimeMillis());
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogManager().debug("[Trust] " + profile.getLastKnownName() + " penalty: -" + amount + " (" + reason + ") → " + String.format("%.1f", newScore));
        }
    }

    @Override
    public boolean isTrusted(@NotNull UUID playerId) {
        return getScoreDouble(playerId) >= trustedEsik;
    }

    @Override
    public boolean isVeteran(@NotNull UUID playerId) {
        return getScoreDouble(playerId) >= veteranEsik;
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

        // Fire PostVerificationEvent when trust reaches bypass threshold
        if (score >= attackBypassEsik) {
            org.bukkit.Bukkit.getPluginManager().callEvent(
                new com.atomguard.api.event.PostVerificationEvent(
                    profile.getUuid(), true, "behavior"));
        }
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
            if (!trustFile.getParentFile().exists()) {
                trustFile.getParentFile().mkdirs();
            }
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
