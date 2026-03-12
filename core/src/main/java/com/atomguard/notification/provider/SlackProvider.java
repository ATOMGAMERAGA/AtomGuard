package com.atomguard.notification.provider;

import com.atomguard.AtomGuard;
import com.atomguard.notification.NotificationMessage;
import com.atomguard.notification.NotificationProvider;
import com.atomguard.util.HttpClientUtil;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Slack Webhook bildirim saglayicisi.
 * Slack Block Kit formati ile mesaj gonderir.
 */
public class SlackProvider implements NotificationProvider {

    private static final String NAME = "slack";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final AtomGuard plugin;
    private volatile String webhookUrl;
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    public SlackProvider(AtomGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    private void reload() {
        this.webhookUrl = plugin.getConfig().getString("bildirimler.slack.webhook-url", "");
        this.enabled.set(plugin.getConfig().getBoolean("bildirimler.slack.aktif", false)
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
        String json = buildBlockKitJson(message);

        try {
            HttpClientUtil.postAsync(webhookUrl, json,
                    Map.of("Content-Type", "application/json"), HTTP_TIMEOUT).join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Slack mesaji gonderilemedi: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> sendAsync(NotificationMessage message) {
        if (!isEnabled()) return CompletableFuture.completedFuture(null);

        String json = buildBlockKitJson(message);

        return HttpClientUtil.postAsync(webhookUrl, json,
                        Map.of("Content-Type", "application/json"), HTTP_TIMEOUT)
                .thenApply(response -> (Void) null)
                .exceptionally(e -> {
                    plugin.getLogger().log(Level.WARNING, "Slack mesaji gonderilemedi: " + e.getMessage());
                    return null;
                });
    }

    @Override
    public void start() {
        reload();
        if (isEnabled()) {
            plugin.getLogger().info("Slack bildirim saglayicisi baslatildi.");
        }
    }

    @Override
    public void stop() {
        // No resources to clean up
    }

    // ═══════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════

    private String buildBlockKitJson(NotificationMessage message) {
        String severityLabel = switch (message.severity()) {
            case INFO -> ":information_source: INFO";
            case WARNING -> ":warning: WARNING";
            case CRITICAL -> ":rotating_light: CRITICAL";
        };

        StringBuilder blocks = new StringBuilder();
        blocks.append("[");

        // Header block
        blocks.append("{\"type\":\"header\",\"text\":{\"type\":\"plain_text\",\"text\":\"")
                .append(escapeJson(message.title()))
                .append("\"}},");

        // Severity + description section
        blocks.append("{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"")
                .append(escapeJson(severityLabel))
                .append("\\n\\n")
                .append(escapeJson(message.description()))
                .append("\"}},");

        // Fields section (if any)
        if (message.fields() != null && !message.fields().isEmpty()) {
            blocks.append("{\"type\":\"section\",\"fields\":[");
            boolean first = true;
            for (var entry : message.fields().entrySet()) {
                if (!first) blocks.append(",");
                blocks.append("{\"type\":\"mrkdwn\",\"text\":\"*")
                        .append(escapeJson(entry.getKey()))
                        .append("*\\n")
                        .append(escapeJson(entry.getValue()))
                        .append("\"}");
                first = false;
            }
            blocks.append("]},");
        }

        // Divider
        blocks.append("{\"type\":\"divider\"},");

        // Footer context
        blocks.append("{\"type\":\"context\",\"elements\":[{\"type\":\"mrkdwn\",\"text\":\"AtomGuard | ")
                .append(escapeJson(message.timestamp().toString()))
                .append("\"}]}");

        blocks.append("]");

        return "{\"blocks\":" + blocks + "}";
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
