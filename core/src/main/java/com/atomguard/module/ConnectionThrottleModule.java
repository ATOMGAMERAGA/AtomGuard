package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * IP bazli baglanti hiz sinirlandirici.
 * Sliding window ile IP basina dakikada max baglanti sayisini sinirlar.
 */
public class ConnectionThrottleModule extends AbstractModule implements Listener {

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> connectionTimes = new ConcurrentHashMap<>();

    public ConnectionThrottleModule(@NotNull AtomGuard plugin) {
        super(plugin, "baglanti-sinirlandirici", "IP bazli baglanti hiz sinirlandirici");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override

    public void onDisable() {
        super.onDisable();
        connectionTimes.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        if (!isEnabled()) return;

        String ip = event.getAddress().getHostAddress();

        // Check exempt IPs
        List<String> exemptIps = plugin.getConfig().getStringList("moduller.baglanti-sinirlandirici.muaf-ipler");
        if (exemptIps != null && exemptIps.contains(ip)) return;

        // Determine limit based on attack mode
        int normalLimit = getConfigInt("dakikada-max-baglanti", 3);
        int attackLimit = getConfigInt("saldiri-dakikada-max", 1);
        boolean inAttack = plugin.getAttackModeManager().isAttackMode();
        int limit = inAttack ? attackLimit : normalLimit;

        // Sliding window: track connections in last 60 seconds
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> times = connectionTimes.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());

        // Clean old entries
        times.removeIf(ts -> now - ts > 60_000);

        if (times.size() >= limit) {
            incrementBlockedCount();
            logExploit(event.getName(), "Baglanti siniri asildi (IP: " + ip + ", " + times.size() + "/" + limit + "/dk)");

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&cCok fazla baglanti denemesi! Lutfen bir dakika bekleyin."));
            return;
        }

        times.add(now);
    }

    /**
     * Periodically clean stale entries to prevent memory leaks.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        connectionTimes.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(ts -> now - ts > 60_000);
            return entry.getValue().isEmpty();
        });
    }
}
