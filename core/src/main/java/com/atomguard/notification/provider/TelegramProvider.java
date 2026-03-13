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
 * Telegram Bot API bildirim saglayicisi.
 * Mesajlari Markdown formatinda gonderir.
 */
public class TelegramProvider implements NotificationProvider {

    private static final String NAME = "telegram";
    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final AtomGuard plugin;
    private volatile String botToken;
    private volatile String chatId;
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    public TelegramProvider(AtomGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    private void reload() {
        this.botToken = plugin.getConfig().getString("notifications.telegram.bot-token", "");
        this.chatId = plugin.getConfig().getString("notifications.telegram.chat-id", "");
        this.enabled.set(plugin.getConfig().getBoolean("notifications.telegram.enabled", false)
                && botToken != null && !botToken.isEmpty()
                && chatId != null && !chatId.isEmpty());
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
        String text = formatMarkdown(message);
        String json = buildRequestJson(text);
        String url = API_BASE + botToken + "/sendMessage";

        try {
            HttpClientUtil.postAsync(url, json,
                    Map.of("Content-Type", "application/json"), HTTP_TIMEOUT).join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Telegram mesaji gonderilemedi: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> sendAsync(NotificationMessage message) {
        if (!isEnabled()) return CompletableFuture.completedFuture(null);

        String text = formatMarkdown(message);
        String json = buildRequestJson(text);
        String url = API_BASE + botToken + "/sendMessage";

        return HttpClientUtil.postAsync(url, json,
                        Map.of("Content-Type", "application/json"), HTTP_TIMEOUT)
                .thenApply(response -> (Void) null)
                .exceptionally(e -> {
                    plugin.getLogger().log(Level.WARNING, "Telegram mesaji gonderilemedi: " + e.getMessage());
                    return null;
                });
    }

    @Override
    public void start() {
        reload();
        if (isEnabled()) {
            plugin.getLogger().info("Telegram bildirim saglayicisi baslatildi.");
        }
    }

    @Override
    public void stop() {
        // No resources to clean up
    }

    // ═══════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════

    private String formatMarkdown(NotificationMessage message) {
        StringBuilder sb = new StringBuilder();

        // Severity emoji indicator
        String indicator = switch (message.severity()) {
            case INFO -> "ℹ️";
            case WARNING -> "⚠️";
            case CRITICAL -> "🚨";
        };

        sb.append(indicator).append(" *").append(escapeMarkdown(message.title())).append("*\n\n");
        sb.append(escapeMarkdown(message.description()));

        if (message.fields() != null && !message.fields().isEmpty()) {
            sb.append("\n\n");
            for (var entry : message.fields().entrySet()) {
                sb.append("*").append(escapeMarkdown(entry.getKey())).append(":* ")
                        .append(escapeMarkdown(entry.getValue())).append("\n");
            }
        }

        sb.append("\n_AtomGuard_");
        return sb.toString();
    }

    private String buildRequestJson(String text) {
        return String.format(
                "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"Markdown\",\"disable_web_page_preview\":true}",
                escapeJson(chatId),
                escapeJson(text)
        );
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        // Escape Markdown special characters (except * and _ which we use for formatting)
        return text.replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("`", "\\`");
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
