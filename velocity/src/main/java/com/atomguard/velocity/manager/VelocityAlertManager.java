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

    public void alertAttackStarted(String rate) {
        String msg = "<red>[AtomGuard] Saldırı tespit edildi! Hız: " + rate + " bağlantı/sn</red>";
        sendAlert(msg);
        sendDiscordAlert("⚠️ AtomGuard: Saldırı tespit edildi! Hız: " + rate + " bağlantı/sn");
        logger.warn("Saldırı tespit edildi! Hız: {}", rate);
    }

    public void alertAttackEnded(String duration, long blocked) {
        String msg = "<green>[AtomGuard] Saldırı sona erdi. Süre: " + duration + ", Engellenen: " + blocked + "</green>";
        sendAlert(msg);
        sendDiscordAlert("✅ AtomGuard: Saldırı sona erdi. Süre: " + duration + ", Engellenen: " + blocked);
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
