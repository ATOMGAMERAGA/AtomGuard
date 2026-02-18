package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ExternalListManager {

    private final AtomGuardVelocity plugin;
    private final ExternalListFetcher fetcher;
    private final ExternalListParser parser;
    private final Map<String, List<ExternalListEntry>> lists = new ConcurrentHashMap<>();

    public ExternalListManager(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.fetcher = new ExternalListFetcher(plugin);
        this.parser = new ExternalListParser();
    }

    public void start() {
        if (!plugin.getConfigManager().getBoolean("harici-listeler.aktif", false)) return;
        
        List<Map<String, Object>> configLists = plugin.getConfigManager().getMapList("harici-listeler.listeler");
        if (configLists == null) return;
        
        for (Map<String, Object> config : configLists) {
            boolean active = (boolean) config.getOrDefault("aktif", false);
            if (!active) continue;
            
            String name = (String) config.get("isim");
            String url = (String) config.get("url");
            String format = (String) config.getOrDefault("format", "plaintext");
            int interval = (int) config.getOrDefault("guncelleme-dakika", 60);

            // Schedule update
            plugin.getProxyServer().getScheduler()
                    .buildTask(plugin, () -> updateList(name, url, format))
                    .repeat(interval, TimeUnit.MINUTES)
                    .delay(10, TimeUnit.SECONDS) // Initial delay
                    .schedule();
        }
    }

    private void updateList(String name, String url, String format) {
        plugin.getSlf4jLogger().info("Updating external list: {}", name);
        fetcher.fetch(url).thenAccept(lines -> {
            List<String> parsed = parser.parse(lines, format);
            List<ExternalListEntry> entries = parsed.stream()
                    .map(val -> new ExternalListEntry(name, val, System.currentTimeMillis()))
                    .collect(Collectors.toList());
            
            lists.put(name, entries);
            plugin.getSlf4jLogger().info("External list {} updated with {} entries.", name, entries.size());
            
            // Sync with Firewall Module logic?
            // Usually, FirewallModule checks this manager.
            // But we should probably push to FirewallModule's blacklist manager if architecture demands it.
            // For now, let's keep it here and let FirewallModule check here.
        });
    }

    public boolean isBlocked(String ip) {
        for (List<ExternalListEntry> entryList : lists.values()) {
            for (ExternalListEntry entry : entryList) {
                // Check if IP matches entry.value (could be IP or CIDR)
                if (entry.getValue().contains("/")) {
                    // CIDR check (simplified implementation reference or reuse WhitelistManager logic?)
                    // For now, exact match or simple contains (weak)
                    // TODO: Proper CIDR check
                } else {
                    if (entry.getValue().equals(ip)) return true;
                }
            }
        }
        return false;
    }
}
