package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Harici liste yöneticisi — uzak URL'lerden IP/CIDR kara listelerini periyodik olarak çeker ve yönetir.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
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
        if (!plugin.getConfigManager().getBoolean("external-lists.enabled", false)) return;
        
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
                if (entry.getValue().contains("/")) {
                    if (matchesCidr(ip, entry.getValue())) return true;
                } else {
                    if (entry.getValue().equals(ip)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Bir IP adresinin CIDR notasyonundaki bir aralığa dahil olup olmadığını kontrol eder.
     * Örnek: "192.168.1.100" → "192.168.1.0/24" → true
     */
    private boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;

            InetAddress targetAddr = InetAddress.getByName(ip);
            InetAddress networkAddr = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);

            byte[] targetBytes = targetAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();

            if (targetBytes.length != networkBytes.length) return false; // IPv4 vs IPv6 karışıklığı

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            // Tam byte'lar eşleşmeli
            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != networkBytes[i]) return false;
            }

            // Kalan bit'ler için maske uygula
            if (remainingBits > 0 && fullBytes < targetBytes.length) {
                int mask = 0xFF & (0xFF << (8 - remainingBits));
                if ((targetBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) return false;
            }

            return true;
        } catch (Exception e) {
            plugin.getSlf4jLogger().debug("CIDR eşleştirme hatası ({} ~ {}): {}", ip, cidr, e.getMessage());
            return false;
        }
    }
}
