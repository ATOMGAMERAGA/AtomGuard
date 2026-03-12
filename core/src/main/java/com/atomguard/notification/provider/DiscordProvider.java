package com.atomguard.notification.provider;

import com.atomguard.AtomGuard;
import com.atomguard.notification.NotificationMessage;
import com.atomguard.notification.NotificationProvider;
import com.atomguard.util.HttpClientUtil;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Discord Webhook bildirim saglayicisi.
 * Embed formati, rate limiting (5/dakika) ve toplu mesaj destegi.
 */
public class DiscordProvider implements NotificationProvider {

    private static final String NAME = "discord";
    private static final int MAX_PER_MINUTE = 5;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final AtomGuard plugin;
    private volatile String webhookUrl;
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    // Rate limiting: sliding window
    private final ConcurrentLinkedDeque<Long> sentTimestamps = new ConcurrentLinkedDeque<>();

    public DiscordProvider(AtomGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    private void reload() {
        this.webhookUrl = plugin.getConfig().getString("bildirimler.discord.webhook-url", "");
        this.enabled.set(plugin.getConfig().getBoolean("bildirimler.discord.aktif", false)
                && webhookUrl != null && !webhookUrl.isEmpty());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public void send(NotificationMessage message) {
        if (!isEnabled()) return;
        if (!acquireRateSlot()) return;

        int color = severityToColor(message.severity());
        String json = buildEmbedJson(message.title(), message.description(), message.fields(), color);

        try {
            HttpClientUtil.postAsync(webhookUrl, json,
                    Map.of("Content-Type", "application/json"), HTTP_TIMEOUT).join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Discord webhook gonderilemedi: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> sendAsync(NotificationMessage message) {
        if (!isEnabled()) return CompletableFuture.completedFuture(null);
        if (!acquireRateSlot()) return CompletableFuture.completedFuture(null);

        int color = severityToColor(message.severity());
        String json = buildEmbedJson(message.title(), message.description(), message.fields(), color);

        return HttpClientUtil.postAsync(webhookUrl, json,
                        Map.of("Content-Type", "application/json"), HTTP_TIMEOUT)
                .thenApply(response -> (Void) null)
                .exceptionally(e -> {
                    plugin.getLogger().log(Level.WARNING, "Discord webhook gonderilemedi: " + e.getMessage());
                    return null;
                });
    }

    @Override
    public void start() {
        reload();
        if (isEnabled()) {
            plugin.getLogger().info("Discord bildirim saglayicisi baslatildi.");
        }
    }

    @Override
    public void stop() {
        // No resources to clean up - HttpClientUtil is shared
    }

    // ═══════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════

    private boolean acquireRateSlot() {
        long now = System.currentTimeMillis();
        sentTimestamps.removeIf(ts -> now - ts > 60_000);
        if (sentTimestamps.size() >= MAX_PER_MINUTE) return false;
        sentTimestamps.add(now);
        return true;
    }

    private int severityToColor(NotificationMessage.Severity severity) {
        return switch (severity) {
            case INFO -> 0x5865F2;     // Discord indigo
            case WARNING -> 0xFFA500;  // Orange
            case CRITICAL -> 0xFF0000; // Red
        };
    }

    private String buildEmbedJson(String title, String description,
                                   Map<String, String> fields, int color) {
        StringBuilder fieldsJson = new StringBuilder();
        if (fields != null && !fields.isEmpty()) {
            fieldsJson.append(",\"fields\":[");
            boolean first = true;
            for (var entry : fields.entrySet()) {
                if (!first) fieldsJson.append(",");
                fieldsJson.append(String.format("{\"name\":\"%s\",\"value\":\"%s\",\"inline\":true}",
                        escapeJson(entry.getKey()), escapeJson(entry.getValue())));
                first = false;
            }
            fieldsJson.append("]");
        }

        return String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d%s,\"footer\":{\"text\":\"AtomGuard\"}}]}",
                escapeJson(title),
                escapeJson(description),
                color,
                fieldsJson
        );
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "    ");
    }
}
