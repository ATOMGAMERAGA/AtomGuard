package com.atomguard.migration.steps;

import com.atomguard.migration.MigrationResult;
import com.atomguard.migration.MigrationStep;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;

/**
 * v1.1.1 → v1.2.0 migration adımı.
 * 5 yeni sistem için tüm config key'leri eklendi:
 * - Adaptive Threat Intelligence (tehdit-istihbarati)
 * - Player Trust Score (guven-skoru)
 * - Attack Forensics (adli-analiz)
 * - Honeypot Module (moduller.bal-kupu)
 */
public class Migration_1_1_1_to_1_2_0 implements MigrationStep {

    @Override
    public String getFromVersion() { return "1.1.1"; }

    @Override
    public String getToVersion() { return "1.2.0"; }

    @Override
    public String getDescription() { return "v1.1.1 -> v1.2.0: Tehdit İstihbaratı, Güven Puanı, Adli Analiz ve Bal Küpü sistemleri eklendi"; }

    @Override
    public MigrationResult migrate(FileConfiguration config) {
        long start = System.currentTimeMillis();
        MigrationResult.Builder result = new MigrationResult.Builder("1.1.1", "1.2.0");

        // ─── Tehdit İstihbaratı ───
        addIfAbsent(config, "tehdit-istihbarati.aktif", true, result);
        addIfAbsent(config, "tehdit-istihbarati.hassasiyet", 1.0, result);
        addIfAbsent(config, "tehdit-istihbarati.ogrenme-suresi-saat", 168, result);
        addIfAbsent(config, "tehdit-istihbarati.min-sample", 100, result);
        addIfAbsent(config, "tehdit-istihbarati.analiz-araligi-sn", 60, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.elevated.discord-bildirim", true, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.elevated.log", true, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.high.discord-bildirim", true, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.high.log", true, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.high.limit-sikistir", true, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.high.limit-carpani", 0.5, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.critical.discord-bildirim", true, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.critical.log", true, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.critical.saldiri-modu-tetikle", true, result);
        addIfAbsent(config, "tehdit-istihbarati.aksiyonlar.critical.admin-bildirim", true, result);
        addIfAbsent(config, "tehdit-istihbarati.profil-kaydetme-dk", 30, result);

        // ─── Güven Skoru ───
        addIfAbsent(config, "guven-skoru.aktif", true, result);
        addIfAbsent(config, "guven-skoru.baz-puan", 20, result);
        addIfAbsent(config, "guven-skoru.esikler.regular", 30, result);
        addIfAbsent(config, "guven-skoru.esikler.trusted", 60, result);
        addIfAbsent(config, "guven-skoru.esikler.veteran", 85, result);
        addIfAbsent(config, "guven-skoru.bypass.saldiri-modu-esik", 70, result);
        addIfAbsent(config, "guven-skoru.bypass.bot-kontrol-atla", true, result);
        addIfAbsent(config, "guven-skoru.bypass.vpn-kontrol-atla", false, result);
        addIfAbsent(config, "guven-skoru.guncelleme-araligi-dk", 5, result);
        addIfAbsent(config, "guven-skoru.kaydetme-araligi-dk", 10, result);
        addIfAbsent(config, "guven-skoru.ihlal-sifirlama-saat", 24, result);

        // ─── Adli Analiz ───
        addIfAbsent(config, "adli-analiz.aktif", true, result);
        addIfAbsent(config, "adli-analiz.max-bellek", 20, result);
        addIfAbsent(config, "adli-analiz.max-disk", 100, result);
        addIfAbsent(config, "adli-analiz.metrik-araligi-sn", 10, result);
        addIfAbsent(config, "adli-analiz.geoip-aktif", false, result);
        addIfAbsent(config, "adli-analiz.geoip-dosya", "GeoLite2-Country.mmdb", result);
        addIfAbsent(config, "adli-analiz.discord-rapor", true, result);
        addIfAbsent(config, "adli-analiz.temizlik-araligi-saat", 24, result);

        // ─── Bal Küpü Modülü ───
        addIfAbsent(config, "moduller.bal-kupu.aktif", false, result);
        if (!config.contains("moduller.bal-kupu.portlar")) {
            config.set("moduller.bal-kupu.portlar", Arrays.asList(25566, 25567));
            result.addKey("moduller.bal-kupu.portlar");
        }
        addIfAbsent(config, "moduller.bal-kupu.sahte-motd.aktif", true, result);
        addIfAbsent(config, "moduller.bal-kupu.sahte-motd.sunucu-adi", "§a§lPopüler Sunucu §7— §e1.21.4", result);
        addIfAbsent(config, "moduller.bal-kupu.sahte-motd.max-oyuncu", 200, result);
        addIfAbsent(config, "moduller.bal-kupu.sahte-motd.online-oyuncu", 87, result);
        addIfAbsent(config, "moduller.bal-kupu.sahte-motd.versiyon", "1.21.4", result);
        addIfAbsent(config, "moduller.bal-kupu.sahte-motd.protokol", 769, result);
        addIfAbsent(config, "moduller.bal-kupu.blacklist.aninda-engelle", true, result);
        addIfAbsent(config, "moduller.bal-kupu.blacklist.max-baglanti", 3, result);
        addIfAbsent(config, "moduller.bal-kupu.blacklist.sure-sn", 3600, result);
        addIfAbsent(config, "moduller.bal-kupu.beyaz-liste-muaf", true, result);
        addIfAbsent(config, "moduller.bal-kupu.guvenlik.max-eszamanli-baglanti", 50, result);
        addIfAbsent(config, "moduller.bal-kupu.guvenlik.baglanti-zaman-asimi-sn", 5, result);
        addIfAbsent(config, "moduller.bal-kupu.guvenlik.max-okuma-byte", 256, result);
        addIfAbsent(config, "moduller.bal-kupu.discord-bildirim", true, result);
        addIfAbsent(config, "moduller.bal-kupu.log-seviyesi", "SUMMARY", result);

        return result.durationMs(System.currentTimeMillis() - start).success(true).build();
    }

    private void addIfAbsent(FileConfiguration config, String key, Object value, MigrationResult.Builder result) {
        if (!config.contains(key)) {
            config.set(key, value);
            result.addKey(key);
        }
    }
}
