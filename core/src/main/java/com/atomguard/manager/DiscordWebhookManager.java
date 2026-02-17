package com.atomguard.manager;

import com.atomguard.AtomGuard;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Discord Webhook entegrasyonu.
 * Rate limiting ve toplu mesaj gonderimi destekler.
 */
public class DiscordWebhookManager {

    private final AtomGuard plugin;
    private final ScheduledExecutorService executor;

    // Config
    private volatile boolean enabled;
    private volatile String webhookUrl;
    private volatile int batchIntervalSeconds;

    // Rate limiting: sliding window - max 5 messages per minute
    private final ConcurrentLinkedDeque<Long> sentTimestamps = new ConcurrentLinkedDeque<>();
    private static final int MAX_PER_MINUTE = 5;

    // Buffered exploit messages for batching
    private final ConcurrentLinkedQueue<String> exploitBuffer = new ConcurrentLinkedQueue<>();

    // Notification toggles
    private volatile boolean notifySaldiriModu;
    private volatile boolean notifyExploitEngelleme;
    private volatile boolean notifyBotKick;
    private volatile boolean notifyPanikKomutu;
    private volatile boolean notifyPerformans;

    private ScheduledFuture<?> batchTask;
    private ScheduledFuture<?> tpsTask;

    public DiscordWebhookManager(AtomGuard plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AtomGuard-Discord");
            t.setDaemon(true);
            return t;
        });
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("discord-webhook.aktif", false);
        this.webhookUrl = plugin.getConfig().getString("discord-webhook.webhook-url", "");
        this.batchIntervalSeconds = plugin.getConfig().getInt("discord-webhook.toplama-suresi", 30);
        this.notifySaldiriModu = plugin.getConfig().getBoolean("discord-webhook.bildirimler.saldiri-modu", true);
        this.notifyExploitEngelleme = plugin.getConfig().getBoolean("discord-webhook.bildirimler.exploit-engelleme", true);
        this.notifyBotKick = plugin.getConfig().getBoolean("discord-webhook.bildirimler.bot-kick", true);
        this.notifyPanikKomutu = plugin.getConfig().getBoolean("discord-webhook.bildirimler.panik-komutu", true);
        this.notifyPerformans = plugin.getConfig().getBoolean("discord-webhook.bildirimler.performans", true);
    }

    /**
     * Starts the batch send task and TPS monitor.
     */
    public void start() {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) return;

        // Batch exploit messages
        if (batchTask != null) batchTask.cancel(false);
        batchTask = executor.scheduleAtFixedRate(this::flushExploitBuffer,
                batchIntervalSeconds, batchIntervalSeconds, TimeUnit.SECONDS);

        // TPS monitor - check every 30 seconds
        if (tpsTask != null) tpsTask.cancel(false);
        tpsTask = executor.scheduleAtFixedRate(() -> {
            if (!notifyPerformans) return;
            try {
                double tps = plugin.getServer().getTPS()[0];
                if (tps < 15.0) {
                    sendEmbed("Performans Uyarisi",
                            String.format("TPS dusuk: **%.2f**\nOnline: **%d** oyuncu",
                                    tps, plugin.getServer().getOnlinePlayers().size()),
                            0xFFA500); // Orange
                }
            } catch (Exception ignored) {}
        }, 30, 30, TimeUnit.SECONDS);

        plugin.getLogger().info("Discord Webhook Manager baslatildi.");
    }

    public void stop() {
        flushExploitBuffer();
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ═══════════════════════════════════════
    // Public notification methods
    // ═══════════════════════════════════════

    /**
     * Saldiri modu aktif/deaktif bildirimi.
     */
    public void notifyAttackMode(boolean activated, int connectionRate) {
        if (!enabled || !notifySaldiriModu) return;
        if (activated) {
            sendEmbed("Saldiri Modu AKTIF",
                    String.format("Baglanti hizi: **%d/sn**\nSunucu savunma moduna gecti.", connectionRate),
                    0xFF0000); // Red
        } else {
            sendEmbed("Saldiri Modu Deaktif",
                    "Sunucu normal duruma dondu.",
                    0x00FF00); // Green
        }
    }

    /**
     * Exploit engelleme bildirimi (buffer'a eklenir, toplu gonderilir).
     */
    public void notifyExploitBlocked(String moduleName, String playerName, String details) {
        if (!enabled || !notifyExploitEngelleme) return;
        exploitBuffer.add(String.format("**%s** | %s: %s", playerName, moduleName, details));
    }

    /**
     * Bot kick/ban bildirimi.
     */
    public void notifyBotAction(String playerName, String ip, String reason) {
        if (!enabled || !notifyBotKick) return;
        sendEmbed("Bot Tespit Edildi",
                String.format("Oyuncu: **%s**\nIP: `%s`\nSebep: %s", playerName, ip, reason),
                0xFFFF00); // Yellow
    }

    /**
     * Panik komutu bildirimi.
     */
    public void notifyPanic(String executorName, int bannedCount) {
        if (!enabled || !notifyPanikKomutu) return;
        sendEmbed("PANIK MODU",
                String.format("Calistiran: **%s**\nYasaklanan: **%d** oyuncu", executorName, bannedCount),
                0xFF0000); // Red
    }

    // ═══════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════

    private void flushExploitBuffer() {
        if (exploitBuffer.isEmpty()) return;

        List<String> batch = new ArrayList<>();
        String item;
        while ((item = exploitBuffer.poll()) != null && batch.size() < 25) {
            batch.add(item);
        }
        if (batch.isEmpty()) return;

        StringBuilder desc = new StringBuilder();
        for (String line : batch) {
            if (desc.length() + line.length() > 2000) break;
            desc.append(line).append("\n");
        }

        sendEmbed("Exploit Engelleme Raporu",
                desc.toString().trim() + "\n\n_Toplam: " + batch.size() + " engelleme_",
                0xFFFF00); // Yellow
    }

    private void sendEmbed(String title, String description, int color) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) return;

        // Rate limit check
        long now = System.currentTimeMillis();
        sentTimestamps.removeIf(ts -> now - ts > 60_000);
        if (sentTimestamps.size() >= MAX_PER_MINUTE) return;
        sentTimestamps.add(now);

        executor.submit(() -> {
            try {
                // Escape JSON special chars
                String safeTitle = escapeJson(title);
                String safeDesc = escapeJson(description);

                String json = String.format(
                    "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d,\"footer\":{\"text\":\"AtomGuard\"}}]}",
                    safeTitle, safeDesc, color
                );

                URL url = URI.create(webhookUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 429) {
                    // Rate limited by Discord, skip
                    plugin.getLogManager().debug("Discord rate limited.");
                } else if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogManager().warning("Discord webhook yanit kodu: " + responseCode);
                }
                conn.disconnect();
            } catch (IOException e) {
                plugin.getLogManager().warning("Discord webhook gonderilemedi: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "")
                   .replace("\t", "    ");
    }

    public boolean isEnabled() {
        return enabled;
    }
}
