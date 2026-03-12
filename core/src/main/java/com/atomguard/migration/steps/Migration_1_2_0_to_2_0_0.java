package com.atomguard.migration.steps;

import com.atomguard.migration.MigrationResult;
import com.atomguard.migration.MigrationStep;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * v1.2.0 → v2.0.0 migration adimi.
 * Yeni sistemler:
 * - Bildirim Sistemi (bildirimler: Discord/Telegram/Slack)
 * - Paket Kaydi (forensik.paket-kaydi)
 * - Adaptif Esik (tehdit-istihbarati.adaptif-esik)
 * - Web Panel API/SSE/GeoMap (web-panel.api/sse/geo-harita)
 * - Harici Servis URL'leri (harici-servisler)
 * - discord-webhook → bildirimler.discord tasinmasi
 */
public class Migration_1_2_0_to_2_0_0 implements MigrationStep {

    @Override
    public String getFromVersion() { return "1.2.0"; }

    @Override
    public String getToVersion() { return "2.0.0"; }

    @Override
    public String getDescription() {
        return "v1.2.0 -> v2.0.0: Bildirim sistemi, paket kaydi, adaptif esik, web panel API";
    }

    @Override
    public MigrationResult migrate(FileConfiguration config) {
        long start = System.currentTimeMillis();
        MigrationResult.Builder result = new MigrationResult.Builder("1.2.0", "2.0.0");

        // --- discord-webhook → bildirimler.discord tasinmasi ---
        if (config.contains("discord-webhook.aktif")) {
            boolean aktif = config.getBoolean("discord-webhook.aktif", false);
            String webhookUrl = config.getString("discord-webhook.webhook-url", "");
            int toplamaSuresi = config.getInt("discord-webhook.toplama-suresi", 30);

            addIfAbsent(config, "bildirimler.discord.aktif", aktif, result);
            addIfAbsent(config, "bildirimler.discord.webhook-url", webhookUrl, result);
            addIfAbsent(config, "bildirimler.discord.toplama-suresi", toplamaSuresi, result);
            addIfAbsent(config, "bildirimler.discord.bildirim-turleri",
                List.of("ATTACK_MODE", "EXPLOIT_BLOCKED", "PANIC_COMMAND", "DDOS_DETECTED"), result);

            result.warn("discord-webhook bölümü bildirimler.discord altına taşındı. Eski bölüm korundu.");
        }

        // --- Bildirimler: Telegram & Slack ---
        addIfAbsent(config, "bildirimler.telegram.aktif", false, result);
        addIfAbsent(config, "bildirimler.telegram.bot-token", "", result);
        addIfAbsent(config, "bildirimler.telegram.chat-id", "", result);
        addIfAbsent(config, "bildirimler.telegram.bildirim-turleri",
            List.of("ATTACK_MODE", "DDOS_DETECTED", "PANIC_COMMAND"), result);

        addIfAbsent(config, "bildirimler.slack.aktif", false, result);
        addIfAbsent(config, "bildirimler.slack.webhook-url", "", result);
        addIfAbsent(config, "bildirimler.slack.kanal", "#minecraft-security", result);
        addIfAbsent(config, "bildirimler.slack.bildirim-turleri",
            List.of("ATTACK_MODE", "PERFORMANCE_ALERT"), result);

        // --- Forensik: Paket Kaydi ---
        addIfAbsent(config, "forensik.paket-kaydi.aktif", true, result);
        addIfAbsent(config, "forensik.paket-kaydi.tampon-suresi-saniye", 30, result);
        addIfAbsent(config, "forensik.paket-kaydi.max-eszamanli-kayit", 10, result);
        addIfAbsent(config, "forensik.paket-kaydi.otomatik-kayit-esik", 50, result);
        addIfAbsent(config, "forensik.paket-kaydi.max-dosya-boyutu-mb", 50, result);
        addIfAbsent(config, "forensik.paket-kaydi.saklama-gunu", 7, result);

        // --- Tehdit Istihbarati: Adaptif Esik + EWMA ---
        addIfAbsent(config, "tehdit-istihbarati.algilama-motoru", "ewma", result);
        addIfAbsent(config, "tehdit-istihbarati.adaptif-esik.aktif", true, result);
        addIfAbsent(config, "tehdit-istihbarati.adaptif-esik.min-ogrenme-haftasi", 2, result);
        addIfAbsent(config, "tehdit-istihbarati.adaptif-esik.gunduz-gece-ayri", true, result);
        addIfAbsent(config, "tehdit-istihbarati.adaptif-esik.hafta-ici-sonu-ayri", true, result);

        // --- Web Panel: API, SSE, GeoMap ---
        addIfAbsent(config, "web-panel.cors-origin", "", result);
        addIfAbsent(config, "web-panel.api.aktif", true, result);
        addIfAbsent(config, "web-panel.api.rate-limit", 60, result);
        addIfAbsent(config, "web-panel.sse.aktif", true, result);
        addIfAbsent(config, "web-panel.sse.heartbeat-saniye", 30, result);
        addIfAbsent(config, "web-panel.geo-harita.aktif", true, result);
        addIfAbsent(config, "web-panel.geo-harita.max-olay", 500, result);
        addIfAbsent(config, "web-panel.geo-harita.guncelleme-saniye", 60, result);

        // --- Harici Servis URL'leri ---
        addIfAbsent(config, "harici-servisler.ashcon-api", "https://api.ashcon.app/mojang/v2/user/", result);
        addIfAbsent(config, "harici-servisler.proxycheck", "https://proxycheck.io/v2/", result);
        addIfAbsent(config, "harici-servisler.ip-api", "http://ip-api.com/json/", result);
        addIfAbsent(config, "harici-servisler.abuseipdb", "https://api.abuseipdb.com/api/v2/check", result);
        addIfAbsent(config, "harici-servisler.iphub", "https://v2.api.iphub.info/ip/", result);

        // --- Config versiyonu guncelle ---
        config.set("config-version", "2.0.0");
        result.modifyKey("config-version");

        return result.durationMs(System.currentTimeMillis() - start).success(true).build();
    }

    private void addIfAbsent(FileConfiguration config, String key, Object value, MigrationResult.Builder result) {
        if (!config.contains(key)) {
            config.set(key, value);
            result.addKey(key);
        }
    }
}
