package com.atomguard.velocity.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class VelocityAlertManager {

    private final ProxyServer server;
    private final Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final HttpClient httpClient;

    private String discordWebhookUrl;
    private boolean discordEnabled;

    public VelocityAlertManager(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void configure(String webhookUrl, boolean enabled) {
        this.discordWebhookUrl = webhookUrl;
        this.discordEnabled = enabled;
    }

    public void sendAlert(String miniMessageText) {
        Component msg = mm.deserialize(miniMessageText);
        server.getAllPlayers().stream()
                .filter(p -> p.hasPermission("atomguard.admin"))
                .forEach(p -> p.sendMessage(msg));
    }

    public void sendDiscordAlert(String content) {
        if (!discordEnabled || discordWebhookUrl == null || discordWebhookUrl.isBlank()) return;
        String json = "{\"content\":\"" + escapeJson(content) + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(discordWebhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();
        httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> { logger.warn("Discord bildirimi gönderilemedi: {}", e.getMessage()); return null; });
    }

    public void sendDiscordEmbed(String title, String description, int color, Map<String, String> fields, String footer) {
        if (!discordEnabled || discordWebhookUrl == null || discordWebhookUrl.isBlank()) return;

        StringBuilder fieldsJson = new StringBuilder();
        if (fields != null && !fields.isEmpty()) {
            fieldsJson.append(",\"fields\":[");
            int i = 0;
            for (var entry : fields.entrySet()) {
                if (i > 0) fieldsJson.append(",");
                fieldsJson.append(String.format(
                    "{\"name\":\"%s\",\"value\":\"%s\",\"inline\":true}",
                    escapeJson(entry.getKey()), escapeJson(entry.getValue())));
                i++;
            }
            fieldsJson.append("]");
        }

        String json = String.format("""
            {"embeds":[{
                "title":"%s",
                "description":"%s",
                "color":%d,
                "timestamp":"%s",
                "footer":{"text":"%s"}
                %s
            }]}""",
            escapeJson(title), escapeJson(description), color,
            java.time.Instant.now().toString(),
            escapeJson(footer != null ? footer : "AtomGuard Velocity"),
            fieldsJson.toString());

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(discordWebhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(10))
            .build();
        httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
            .exceptionally(e -> { logger.warn("Discord embed gönderilemedi: {}", e.getMessage()); return null; });
    }

    public void alertAttackStarted(String rate, int blocked, String topSource) {
        String msg = "<red>[AtomGuard] ⚠ Saldırı tespit edildi! Hız: " + rate + " bağlantı/sn</red>";
        sendAlert(msg);
        
        sendDiscordEmbed(
            "⚠️ Saldırı Tespit Edildi",
            "Proxy sunucuya yüksek hacimli bağlantı saldırısı başladı.",
            0xFF0000,  // Kırmızı
            Map.of(
                "🔥 Bağlantı Hızı", rate + "/sn",
                "🛡️ Engellenen", String.valueOf(blocked),
                "📍 Ana Kaynak", topSource != null ? topSource : "Dağınık",
                "👥 Çevrimiçi", String.valueOf(server.getPlayerCount())
            ),
            "AtomGuard Security"
        );
        logger.warn("Saldırı tespit edildi! Hız: {}/sn, Ana Kaynak: {}", rate, topSource);
    }

    public void alertAttackEnded(String duration, long blocked, int peakRate, String dominantSource) {
        String msg = "<green>[AtomGuard] ✅ Saldırı sona erdi. Süre: " + duration + ", Engellenen: " + blocked + "</green>";
        sendAlert(msg);
        
        sendDiscordEmbed(
            "✅ Saldırı Sona Erdi",
            "Otomatik koruma saldırıyı başarıyla durdurdu.",
            0x00FF00, // Yeşil
            Map.of(
                "⏱️ Süre", duration,
                "🔥 Tepe Hız", peakRate + "/sn",
                "🛡️ Engellenen", String.valueOf(blocked),
                "📍 Ana Kaynak", dominantSource != null ? dominantSource : "Dağınık"
            ),
            "AtomGuard Security"
        );
    }

    public void alertAttackLevelChanged(Object prevLevel, Object newLevel, int rate) {
        sendDiscordEmbed(
            "🔄 Saldırı Seviyesi Değişti",
            "Otomatik eskalasyon durumu güncellendi.",
            0xFFA500, // Turuncu
            Map.of(
                "📉 Önceki Seviye", prevLevel.toString(),
                "📈 Yeni Seviye", newLevel.toString(),
                "⚡ Mevcut Hız", rate + "/sn"
            ),
            "AtomGuard Security"
        );
    }

    public void alertBotDetected(String ip, int score) {
        String msg = "<yellow>[AtomGuard] Bot tespiti! IP: " + ip + " Skor: " + score + "</yellow>";
        sendAlert(msg);
    }

    public void alertVPNDetected(String player, String ip) {
        String msg = "<gold>[AtomGuard] VPN tespiti! Oyuncu: " + player + " IP: " + ip + "</gold>";
        sendAlert(msg);
    }

    public void alertBanned(String ip, String reason) {
        String msg = "<red>[AtomGuard] IP yasaklandı: " + ip + " - Sebep: " + reason + "</red>";
        sendAlert(msg);
        sendDiscordAlert("🔨 AtomGuard: IP yasaklandı: " + ip + " - Sebep: " + reason);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
