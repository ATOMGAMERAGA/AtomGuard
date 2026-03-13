package com.atomguard.migration.steps;

import com.atomguard.migration.MigrationResult;
import com.atomguard.migration.MigrationStep;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

/**
 * v2.0.1 → v2.0.2 migration step.
 *
 * <p>Renames all Turkish config keys to English equivalents.
 * This is a comprehensive rename migration covering:
 * - Top-level section keys (genel, moduller, istatistik, etc.)
 * - Module names within the modules section
 * - Sub-keys within modules (aktif→enabled, eylem→action, etc.)
 * - Attack mode action keys
 * - Notification section keys
 * - Language value (tr→en)
 */
public class Migration_2_0_1_to_2_0_2 implements MigrationStep {

    @Override
    public String getFromVersion() { return "2.0.1"; }

    @Override
    public String getToVersion() { return "2.0.2"; }

    @Override
    public String getDescription() {
        return "v2.0.1 -> v2.0.2: Full Turkish to English config key translation";
    }

    @Override
    public MigrationResult migrate(FileConfiguration config) {
        long start = System.currentTimeMillis();
        MigrationResult.Builder result = new MigrationResult.Builder("2.0.1", "2.0.2");

        // ── Top-level section renames ──
        renameSection(config, "genel", "general", result);
        renameSection(config, "moduller", "modules", result);
        renameSection(config, "istatistik", "statistics", result);
        renameSection(config, "dogrulanmis-onbellek", "verified-cache", result);
        renameSection(config, "metrikler", "metrics", result);
        renameSection(config, "tehdit-istihbarati", "threat-intelligence", result);
        renameSection(config, "guven-skoru", "trust-score", result);
        renameSection(config, "adli-analiz", "forensics", result);
        renameSection(config, "bildirimler", "notifications", result);
        renameSection(config, "forensik", "packet-forensics", result);
        renameSection(config, "harici-servisler", "external-services", result);

        // ── general section sub-keys ──
        renameKey(config, "general.dil", "general.language", result);
        renameKey(config, "general.onek", "general.prefix", result);
        renameKey(config, "general.log.aktif", "general.log.enabled", result);
        renameKey(config, "general.log.klasor", "general.log.folder", result);
        renameKey(config, "general.log.gunluk-dosya", "general.log.daily-file", result);
        renameKey(config, "general.log.log-saklama-gunu", "general.log.retention-days", result);

        // Fix language value: "tr" → "en"
        if ("tr".equals(config.getString("general.language"))) {
            config.set("general.language", "en");
            result.modifyKey("general.language");
        }

        // ── statistics section sub-keys ──
        renameKey(config, "statistics.aktif", "statistics.enabled", result);
        renameKey(config, "statistics.otomatik-kaydetme-dakika", "statistics.auto-save-minutes", result);
        renameKey(config, "statistics.max-saldiri-gecmisi", "statistics.max-attack-history", result);

        // ── verified-cache section sub-keys ──
        renameKey(config, "verified-cache.aktif", "verified-cache.enabled", result);
        renameKey(config, "verified-cache.sure-saat", "verified-cache.ttl-hours", result);
        renameKey(config, "verified-cache.bot-kontrolu-atla", "verified-cache.skip-bot-check", result);
        renameKey(config, "verified-cache.ip-kontrolu-atla", "verified-cache.skip-ip-check", result);

        // ── metrics section sub-keys ──
        renameKey(config, "metrics.aktif", "metrics.enabled", result);
        renameKey(config, "metrics.guncelleme-araligi-saniye", "metrics.update-interval-seconds", result);

        // ── threat-intelligence section sub-keys ──
        renameKey(config, "threat-intelligence.aktif", "threat-intelligence.enabled", result);
        renameKey(config, "threat-intelligence.ogrenme-modu", "threat-intelligence.learning-mode", result);
        renameKey(config, "threat-intelligence.ogrenme-suresi-saat", "threat-intelligence.learning-hours", result);
        renameKey(config, "threat-intelligence.min-ornek-sayisi", "threat-intelligence.min-samples", result);
        renameKey(config, "threat-intelligence.hassasiyet-carpani", "threat-intelligence.sensitivity", result);
        renameKey(config, "threat-intelligence.uyari-bekleme-saniye", "threat-intelligence.alert-cooldown-seconds", result);
        renameKey(config, "threat-intelligence.tampon-boyutu", "threat-intelligence.buffer-size", result);
        renameKey(config, "threat-intelligence.algilama-motoru", "threat-intelligence.detection-engine", result);
        renameKey(config, "threat-intelligence.adaptif-esik.aktif", "threat-intelligence.adaptive-threshold.enabled", result);
        renameKey(config, "threat-intelligence.adaptif-esik.min-ogrenme-haftasi", "threat-intelligence.adaptive-threshold.min-learning-weeks", result);
        renameKey(config, "threat-intelligence.adaptif-esik.gunduz-gece-ayri", "threat-intelligence.adaptive-threshold.day-night-separation", result);
        renameKey(config, "threat-intelligence.adaptif-esik.hafta-ici-sonu-ayri", "threat-intelligence.adaptive-threshold.weekday-weekend-separation", result);
        renameKey(config, "threat-intelligence.isolation-forest.agac-sayisi", "threat-intelligence.isolation-forest.tree-count", result);
        renameKey(config, "threat-intelligence.isolation-forest.ornek-boyutu", "threat-intelligence.isolation-forest.sample-size", result);
        renameKey(config, "threat-intelligence.isolation-forest.anomali-esigi", "threat-intelligence.isolation-forest.anomaly-threshold", result);
        renameKey(config, "threat-intelligence.isolation-forest.min-ornek", "threat-intelligence.isolation-forest.min-samples", result);
        renameKey(config, "threat-intelligence.isolation-forest.yeniden-olusturma-araligi", "threat-intelligence.isolation-forest.rebuild-interval", result);

        // ── trust-score section sub-keys ──
        renameKey(config, "trust-score.aktif", "trust-score.enabled", result);
        renameKey(config, "trust-score.baslangic-puani", "trust-score.initial-score", result);
        renameKey(config, "trust-score.saldiri-modu-bypass-min", "trust-score.attack-mode-bypass-min", result);
        renameKey(config, "trust-score.bot-kontrol-bypass-min", "trust-score.bot-check-bypass-min", result);
        renameKey(config, "trust-score.vpn-kontrol-bypass-min", "trust-score.vpn-check-bypass-min", result);
        renameKey(config, "trust-score.ihlal-basi-ceza", "trust-score.violation-penalty", result);
        renameKey(config, "trust-score.temiz-seans-basi-bonus", "trust-score.clean-session-bonus", result);
        renameKey(config, "trust-score.otomatik-kaydetme-dakika", "trust-score.auto-save-minutes", result);
        renameKey(config, "trust-score.veri-dosyasi", "trust-score.data-file", result);

        // ── forensics section sub-keys ──
        renameKey(config, "forensics.aktif", "forensics.enabled", result);
        renameKey(config, "forensics.max-anlık-goruntuler", "forensics.max-snapshots", result);
        renameKey(config, "forensics.kayit-dizini", "forensics.save-directory", result);
        renameKey(config, "forensics.metrik-kayit-aralik-saniye", "forensics.metric-interval-seconds", result);

        // ── packet-forensics section sub-keys ──
        renameKey(config, "packet-forensics.paket-kaydi.aktif", "packet-forensics.recording.enabled", result);
        renameKey(config, "packet-forensics.paket-kaydi.tampon-suresi-saniye", "packet-forensics.recording.buffer-seconds", result);
        renameKey(config, "packet-forensics.paket-kaydi.max-eszamanli-kayit", "packet-forensics.recording.max-concurrent", result);
        renameKey(config, "packet-forensics.paket-kaydi.otomatik-kayit-esik", "packet-forensics.recording.auto-record-threshold", result);
        renameKey(config, "packet-forensics.paket-kaydi.max-dosya-boyutu-mb", "packet-forensics.recording.max-file-mb", result);
        renameKey(config, "packet-forensics.paket-kaydi.saklama-gunu", "packet-forensics.recording.retention-days", result);

        // ── notifications section sub-keys ──
        renameKey(config, "notifications.discord.aktif", "notifications.discord.enabled", result);
        renameKey(config, "notifications.discord.bildirim-turleri", "notifications.discord.types", result);
        renameKey(config, "notifications.discord.toplama-suresi", "notifications.discord.batch-seconds", result);
        renameKey(config, "notifications.telegram.aktif", "notifications.telegram.enabled", result);
        renameKey(config, "notifications.telegram.bildirim-turleri", "notifications.telegram.types", result);
        renameKey(config, "notifications.slack.aktif", "notifications.slack.enabled", result);
        renameKey(config, "notifications.slack.kanal", "notifications.slack.channel", result);
        renameKey(config, "notifications.slack.bildirim-turleri", "notifications.slack.types", result);

        // ── discord-webhook section sub-keys ──
        renameKey(config, "discord-webhook.aktif", "discord-webhook.enabled", result);
        renameKey(config, "discord-webhook.bildirimler.saldiri-modu", "discord-webhook.notifications.attack-mode", result);
        renameKey(config, "discord-webhook.bildirimler.exploit-engelleme", "discord-webhook.notifications.exploit-block", result);
        renameKey(config, "discord-webhook.bildirimler.bot-kick", "discord-webhook.notifications.bot-kick", result);
        renameKey(config, "discord-webhook.bildirimler.panik-komutu", "discord-webhook.notifications.panic-command", result);
        renameKey(config, "discord-webhook.bildirimler.performans", "discord-webhook.notifications.performance", result);
        renameKey(config, "discord-webhook.toplama-suresi", "discord-webhook.batch-seconds", result);

        // ── web-panel section sub-keys ──
        renameKey(config, "web-panel.kimlik-dogrulama.aktif", "web-panel.auth.enabled", result);
        renameKey(config, "web-panel.kimlik-dogrulama.kullanici-adi", "web-panel.auth.username", result);
        renameKey(config, "web-panel.kimlik-dogrulama.sifre", "web-panel.auth.password", result);
        renameKey(config, "web-panel.max-olay-sayisi", "web-panel.max-events", result);
        renameKey(config, "web-panel.api.aktif", "web-panel.api.enabled", result);
        renameKey(config, "web-panel.sse.aktif", "web-panel.sse.enabled", result);
        renameKey(config, "web-panel.sse.heartbeat-saniye", "web-panel.sse.heartbeat-seconds", result);
        renameKey(config, "web-panel.jwt.token-suresi-dakika", "web-panel.jwt.token-expiry-minutes", result);
        renameKey(config, "web-panel.geo-harita.aktif", "web-panel.geo-map.enabled", result);
        renameKey(config, "web-panel.geo-harita.max-olay", "web-panel.geo-map.max-events", result);
        renameKey(config, "web-panel.geo-harita.guncelleme-saniye", "web-panel.geo-map.update-seconds", result);

        // ── anti-vpn section sub-keys ──
        renameKey(config, "anti-vpn.hosting-engelle", "anti-vpn.block-hosting", result);
        renameKey(config, "anti-vpn.admin-bildirim", "anti-vpn.admin-notify", result);
        renameKey(config, "anti-vpn.beyaz-liste", "anti-vpn.whitelist", result);
        renameKey(config, "anti-vpn.oyuncu-beyaz-liste", "anti-vpn.player-whitelist", result);
        renameKey(config, "anti-vpn.proxy-listesi.aktif", "anti-vpn.proxy-list.enabled", result);
        renameKey(config, "anti-vpn.proxy-listesi.url-listesi", "anti-vpn.proxy-list.urls", result);
        renameKey(config, "anti-vpn.proxy-listesi.indirme-timeout-ms", "anti-vpn.proxy-list.download-timeout-ms", result);
        renameKey(config, "anti-vpn.proxy-listesi.max-tekrar-deneme", "anti-vpn.proxy-list.max-retries", result);
        renameKey(config, "anti-vpn.proxy-listesi.otomatik-yenileme-dakika", "anti-vpn.proxy-list.auto-refresh-minutes", result);
        renameKey(config, "anti-vpn.api-kontrol.aktif", "anti-vpn.api-check.enabled", result);
        renameKey(config, "anti-vpn.api-kontrol.yedek-api-aktif", "anti-vpn.api-check.fallback-enabled", result);
        renameKey(config, "anti-vpn.cidr-engelleme.aktif", "anti-vpn.cidr-blocking.enabled", result);
        renameKey(config, "anti-vpn.cidr-engelleme.engellenen-araliklar", "anti-vpn.cidr-blocking.blocked-ranges", result);
        renameKey(config, "anti-vpn.asn-engelleme.aktif", "anti-vpn.asn-blocking.enabled", result);
        renameKey(config, "anti-vpn.asn-engelleme.engellenen-asnler", "anti-vpn.asn-blocking.blocked-asns", result);

        // ── attack-mode section sub-keys ──
        renameKey(config, "attack-mode.aksiyonlar.dogrulanmamis-ip-engelle", "attack-mode.actions.block-unverified-ips", result);
        renameKey(config, "attack-mode.aksiyonlar.siki-limitler", "attack-mode.actions.strict-limits", result);
        renameKey(config, "attack-mode.aksiyonlar.siki-limit-carpani", "attack-mode.actions.strict-limit-factor", result);
        renameKey(config, "attack-mode.aksiyonlar.otomatik-modul-etkinlestir", "attack-mode.actions.auto-enable-modules", result);
        renameKey(config, "attack-mode.aksiyonlar.otomatik-moduller", "attack-mode.actions.auto-modules", result);
        renameKey(config, "attack-mode.aksiyonlar.sadece-beyaz-liste", "attack-mode.actions.whitelist-only", result);
        renameKey(config, "attack-mode.aksiyonlar.discord-bildirim", "attack-mode.actions.discord-notify", result);
        // Fix module names in auto-modules list
        List<String> autoModules = config.getStringList("attack-mode.actions.auto-modules");
        if (autoModules.contains("paket-exploit") || autoModules.contains("jeton-kovasi")) {
            autoModules.replaceAll(m -> switch (m) {
                case "paket-exploit" -> "packet-exploit";
                case "jeton-kovasi" -> "token-bucket";
                default -> m;
            });
            config.set("attack-mode.actions.auto-modules", autoModules);
            result.modifyKey("attack-mode.actions.auto-modules");
        }

        // ── modules section: rename Turkish module names ──
        Map<String, String> moduleRenames = Map.ofEntries(
            Map.entry("cok-fazla-kitap", "too-many-books"),
            Map.entry("paket-gecikme", "packet-delay"),
            Map.entry("paket-exploit", "packet-exploit"),
            Map.entry("ozel-payload", "custom-payload"),
            Map.entry("komut-crash", "command-crash"),
            Map.entry("creative-item", "creative-items"),
            Map.entry("tabela-crash", "sign-crash"),
            Map.entry("kursu-crash", "lectern-crash"),
            Map.entry("harita-etiketi-crash", "map-label-crash"),
            Map.entry("gecersiz-slot", "invalid-slot"),
            Map.entry("kitap-crash", "book-crash"),
            Map.entry("inek-duplikasyon", "cow-duplication"),
            Map.entry("cevrimdisi-paket", "offline-packet"),
            Map.entry("envanter-duplikasyon", "inventory-duplication"),
            Map.entry("katir-duplikasyon", "mule-duplication"),
            Map.entry("portal-kirma", "portal-break"),
            Map.entry("bundle-duplikasyon", "bundle-duplication"),
            Map.entry("koordinat-normallestirme", "coordinate-normalize"),
            Map.entry("ors-craft-crash", "anvil-craft-crash"),
            Map.entry("entity-etkilesim-crash", "entity-interact-crash"),
            Map.entry("kum-cakil-sinirlandirici", "falling-block-limiter"),
            Map.entry("patlama-sinirlandirici", "explosion-limiter"),
            Map.entry("hareket-guvenligi", "movement-security"),
            Map.entry("gorsel-crasher", "visual-crasher"),
            Map.entry("gelismis-sohbet", "advanced-chat"),
            Map.entry("sifre-kontrol", "password-check"),
            Map.entry("piston-sinirlandirici", "piston-limiter"),
            Map.entry("akilli-lag-tespiti", "smart-lag"),
            Map.entry("gelismis-duplikasyon", "advanced-duplication"),
            Map.entry("baglanti-sinirlandirici", "connection-throttle"),
            Map.entry("bot-koruma", "anti-bot"),
            Map.entry("bal-kupu", "honeypot"),
            Map.entry("bot-korumasi", "bot-protection"),
            Map.entry("gelismis-payload", "advanced-payload"),
            Map.entry("bundle-kilit", "bundle-lock"),
            Map.entry("redstone-sinirlandirici", "redstone-limiter"),
            Map.entry("shulker-bayt", "shulker-byte"),
            Map.entry("item-temizleyici", "item-sanitizer"),
            Map.entry("jeton-kovasi", "token-bucket"),
            Map.entry("gorunum-mesafesi-maskeleme", "view-distance-mask"),
            Map.entry("depolama-entity-kilit", "storage-entity-lock")
        );

        for (Map.Entry<String, String> entry : moduleRenames.entrySet()) {
            String oldKey = "modules." + entry.getKey();
            String newKey = "modules." + entry.getValue();
            renameSection(config, oldKey, newKey, result);
        }

        // ── modules sub-keys: aktif→enabled, eylem→action, common renames ──
        // For each module now renamed, fix their sub-keys
        if (config.contains("modules")) {
            var modulesSection = config.getConfigurationSection("modules");
            if (modulesSection != null) {
                for (String moduleName : modulesSection.getKeys(false)) {
                    String base = "modules." + moduleName + ".";
                    renameKey(config, base + "aktif", base + "enabled", result);
                    renameKey(config, base + "eylem", base + "action", result);
                }
            }
        }

        // ── Common sub-key renames across all modules ──
        renamePrefixed(config, "modules", "chunk-basina-max-kitap", "max-books-per-chunk", result);
        renamePrefixed(config, "modules", "max-sayfa-uzunlugu", "max-page-length", result);
        renamePrefixed(config, "modules", "max-baslik-uzunlugu", "max-title-length", result);
        renamePrefixed(config, "modules", "max-sayfa-sayisi", "max-page-count", result);
        renamePrefixed(config, "modules", "max-sayfa-boyutu", "max-page-size", result);
        renamePrefixed(config, "modules", "max-toplam-kitap-boyutu", "max-total-book-size", result);
        renamePrefixed(config, "modules", "max-toplam-boyut", "max-total-size", result);
        renamePrefixed(config, "modules", "max-paket-boyutu", "max-packet-size", result);
        renamePrefixed(config, "modules", "max-paket-orani", "max-packet-rate", result);
        renamePrefixed(config, "modules", "zaman-penceresi-ms", "time-window-ms", result);
        renamePrefixed(config, "modules", "kara-liste-paketler", "blacklisted-packets", result);
        renamePrefixed(config, "modules", "tur-bazli-limitler", "type-limits", result);
        renamePrefixed(config, "modules", "izinli-kanallar", "allowed-channels", result);
        renamePrefixed(config, "modules", "max-payload-boyutu", "max-payload-size", result);
        renamePrefixed(config, "modules", "bilinmeyen-kanallari-engelle", "block-unknown-channels", result);
        renamePrefixed(config, "modules", "item-kara-liste", "item-blacklist", result);
        renamePrefixed(config, "modules", "max-nbt-boyutu-creative", "max-nbt-size-creative", result);
        renamePrefixed(config, "modules", "ozel-veriyi-soy", "strip-custom-data", result);
        renamePrefixed(config, "modules", "max-buyu-seviyesi", "max-enchant-level", result);
        renamePrefixed(config, "modules", "max-satir-uzunlugu", "max-line-length", result);
        renamePrefixed(config, "modules", "renk-kodlarini-temizle", "clean-color-codes", result);
        renamePrefixed(config, "modules", "ozel-karakterleri-engelle", "block-special-chars", result);
        renamePrefixed(config, "modules", "etiketleri-devre-disi-birak", "disable-labels", result);
        renamePrefixed(config, "modules", "max-etiket-sayisi", "max-label-count", result);
        renamePrefixed(config, "modules", "max-nbt-etiket", "max-nbt-tags", result);
        renamePrefixed(config, "modules", "max-nbt-derinlik", "max-nbt-depth", result);
        renamePrefixed(config, "modules", "max-nbt-boyut-byte", "max-nbt-bytes", result);
        renamePrefixed(config, "modules", "max-nbt-boyut-paket", "max-nbt-packet", result);
        renamePrefixed(config, "modules", "json-derinlik-limiti", "json-depth-limit", result);
        renamePrefixed(config, "modules", "kirkma-cooldown-ms", "shearing-cooldown-ms", result);
        renamePrefixed(config, "modules", "tolerans-suresi-ms", "tolerance-ms", result);
        renamePrefixed(config, "modules", "mantar-engelle", "block-mushroom", result);
        renamePrefixed(config, "modules", "su-kovasi-engelle", "block-water-bucket", result);
        renamePrefixed(config, "modules", "tiklama-cooldown-ms", "click-cooldown-ms", result);
        renamePrefixed(config, "modules", "birakma-cooldown-ms", "drop-cooldown-ms", result);
        renamePrefixed(config, "modules", "chunk-basina-max-frame", "max-frames-per-chunk", result);
        renamePrefixed(config, "modules", "chunk-basina-max-armor-stand", "max-armor-stands-per-chunk", result);
        renamePrefixed(config, "modules", "saniyede-max-chunk-yuklemesi", "max-chunk-loads-per-second", result);
        renamePrefixed(config, "modules", "max-entity-per-chunk-warn", "max-entities-per-chunk", result);
        renamePrefixed(config, "modules", "max-tile-entity-per-chunk-warn", "max-tile-entities-per-chunk", result);
        renamePrefixed(config, "modules", "anvil-max-isim-uzunlugu", "max-anvil-name-length", result);
        renamePrefixed(config, "modules", "max-etkilesim-mesafesi", "max-interaction-distance", result);
        renamePrefixed(config, "modules", "saniyede-max-etkilesim", "max-interactions-per-second", result);
        renamePrefixed(config, "modules", "max-bundle-derinligi", "max-bundle-depth", result);
        renamePrefixed(config, "modules", "max-dusen-blok-chunk", "max-falling-blocks-per-chunk", result);
        renamePrefixed(config, "modules", "birikme-engelle", "block-accumulation", result);
        renamePrefixed(config, "modules", "max-patlama-saniye", "max-explosions-per-second", result);
        renamePrefixed(config, "modules", "max-blok-hasari", "max-block-damage", result);
        renamePrefixed(config, "modules", "gecersiz-koordinat-engelle", "block-invalid-coords", result);
        renamePrefixed(config, "modules", "max-mesafe", "max-distance", result);
        renamePrefixed(config, "modules", "hizli-hareket-engelle", "block-fast-movement", result);
        renamePrefixed(config, "modules", "max-havai-fiseke-efekt", "max-firework-effects", result);
        renamePrefixed(config, "modules", "max-renk-per-efekt", "max-colors-per-effect", result);
        renamePrefixed(config, "modules", "max-havai-fisek-gucu", "max-firework-power", result);
        renamePrefixed(config, "modules", "max-partikul-paketi-saniye", "max-particle-packets-per-second", result);
        renamePrefixed(config, "modules", "unicode-filtre", "unicode-filter", result);
        renamePrefixed(config, "modules", "max-tab-istegi-saniye", "max-tab-requests-per-second", result);
        renamePrefixed(config, "modules", "komut-karakter-kontrol", "command-char-check", result);
        renamePrefixed(config, "modules", "max-piston-hareketi-saniye", "max-piston-moves-per-second", result);
        renamePrefixed(config, "modules", "sifir-tick-engelle", "block-zero-tick", result);
        renamePrefixed(config, "modules", "lag-esigi-ms", "lag-threshold-ms", result);
        renamePrefixed(config, "modules", "entity-esigi-chunk", "entity-threshold-per-chunk", result);
        renamePrefixed(config, "modules", "tile-entity-esigi-chunk", "tile-entity-threshold-per-chunk", result);
        renamePrefixed(config, "modules", "dakikada-max-baglanti", "max-connections-per-minute", result);
        renamePrefixed(config, "modules", "saldiri-dakikada-max", "attack-max-per-minute", result);
        renamePrefixed(config, "modules", "muaf-ipler", "exempt-ips", result);

        // anti-bot sub-keys
        renamePrefixed(config, "modules.anti-bot", "skor-esikleri", "score-thresholds", result);
        renamePrefixed(config, "modules.anti-bot", "kontroller", "checks", result);
        renamePrefixed(config, "modules.anti-bot", "saldiri-modu", "attack-mode", result);
        renamePrefixed(config, "modules.anti-bot", "beyaz-liste", "whitelist", result);
        renamePrefixed(config, "modules.anti-bot", "dogrulama", "verification", result);
        renamePrefixed(config, "modules.anti-bot", "kara-liste", "blacklist", result);
        renamePrefixed(config, "modules.anti-bot", "bildirimler", "notify", result);

        // honeypot sub-keys
        renamePrefixed(config, "modules.honeypot", "max-baglanti", "max-connections", result);
        renamePrefixed(config, "modules.honeypot", "zaman-asimi-ms", "timeout-ms", result);
        renamePrefixed(config, "modules.honeypot", "sahte-motd", "fake-motd", result);
        renamePrefixed(config, "modules.honeypot", "sahte-online", "fake-online", result);
        renamePrefixed(config, "modules.honeypot", "sahte-max", "fake-max", result);
        renamePrefixed(config, "modules.honeypot", "kara-listeye-ekle", "add-to-blacklist", result);
        renamePrefixed(config, "modules.honeypot", "kara-liste-suresi-dk", "blacklist-duration-minutes", result);
        renamePrefixed(config, "modules.honeypot", "discord-bildirim", "discord-notify", result);
        renamePrefixed(config, "modules.honeypot", "istatistik-dosyasi", "stats-file", result);

        // panic-mode sub-keys
        renamePrefixed(config, "modules.panic-mode", "min-oynama-suresi", "min-play-time", result);
        renamePrefixed(config, "modules.panic-mode", "log-dosyasi", "log-file", result);

        // ── Bump config-version ──
        config.set("config-version", "2.0.2");
        result.modifyKey("config-version");

        return result.durationMs(System.currentTimeMillis() - start).success(true).build();
    }

    /**
     * Renames an entire section by copying all values under oldPath to newPath and removing oldPath.
     */
    private void renameSection(FileConfiguration config, String oldPath, String newPath,
                                MigrationResult.Builder result) {
        if (!config.contains(oldPath)) return;
        var section = config.getConfigurationSection(oldPath);
        if (section != null) {
            for (String key : section.getKeys(true)) {
                Object value = section.get(key);
                if (value != null && !(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                    config.set(newPath + "." + key, value);
                }
            }
        } else {
            // Scalar value
            config.set(newPath, config.get(oldPath));
        }
        config.set(oldPath, null);
        result.renameKey(oldPath + " → " + newPath);
    }

    /**
     * Renames a single key from oldPath to newPath.
     */
    private void renameKey(FileConfiguration config, String oldPath, String newPath,
                            MigrationResult.Builder result) {
        if (!config.contains(oldPath)) return;
        config.set(newPath, config.get(oldPath));
        config.set(oldPath, null);
        result.renameKey(oldPath + " → " + newPath);
    }

    /**
     * Renames subKey within all children of parentSection.
     */
    private void renamePrefixed(FileConfiguration config, String parentSection,
                                  String oldSubKey, String newSubKey,
                                  MigrationResult.Builder result) {
        var section = config.getConfigurationSection(parentSection);
        if (section == null) return;
        for (String child : section.getKeys(false)) {
            String oldPath = parentSection + "." + child + "." + oldSubKey;
            String newPath = parentSection + "." + child + "." + newSubKey;
            renameKey(config, oldPath, newPath, result);
        }
    }
}
