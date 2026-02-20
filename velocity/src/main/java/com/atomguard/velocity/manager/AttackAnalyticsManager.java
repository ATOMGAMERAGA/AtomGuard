package com.atomguard.velocity.manager;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.data.AttackSnapshot;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Saldırı analitik ve raporlama motoru.
 */
public class AttackAnalyticsManager {

    private final AtomGuardVelocity plugin;
    private final Deque<AttackSnapshot> attackHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY = 100;

    // Aktif saldırı oturumu
    private volatile AttackSession currentAttack = null;

    public AttackAnalyticsManager(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    public void onAttackStart(int initialRate) {
        currentAttack = new AttackSession(System.currentTimeMillis());
        plugin.getLogManager().warn("Yeni saldırı oturumu başladı. Başlangıç hızı: " + initialRate + "/sn");
        
        plugin.getAuditLogger().log(
            com.atomguard.velocity.audit.AuditLogger.EventType.ATTACK_STARTED,
            null, null, "ddos", "rate=" + initialRate,
            com.atomguard.velocity.audit.AuditLogger.Severity.CRITICAL
        );
    }

    public void recordDuringAttack(int connectionRate, int blocked, String sourceIP) {
        if (currentAttack != null) {
            currentAttack.addDataPoint(connectionRate, blocked, sourceIP);
        }
    }

    public AttackSnapshot onAttackEnd() {
        if (currentAttack == null) return null;

        AttackSnapshot snapshot = currentAttack.toSnapshot();
        attackHistory.offerFirst(snapshot);
        while (attackHistory.size() > MAX_HISTORY) attackHistory.pollLast();

        // Veritabanına kaydet
        if (plugin.getStorageProvider() != null) {
            plugin.getStorageProvider().saveAttackSnapshot(snapshot);
        }

        String durationStr = formatDuration(snapshot.getDuration());
        
        plugin.getAlertManager().alertAttackEnded(
            durationStr,
            snapshot.getBlockedConnections(),
            snapshot.getPeakConnectionRate(),
            snapshot.getDominantSourceCIDR()
        );

        plugin.getAuditLogger().log(
            com.atomguard.velocity.audit.AuditLogger.EventType.ATTACK_ENDED,
            null, null, "ddos", 
            "duration=" + snapshot.getDuration() + "ms, blocked=" + snapshot.getBlockedConnections(),
            com.atomguard.velocity.audit.AuditLogger.Severity.INFO
        );

        currentAttack = null;
        return snapshot;
    }

    public List<AttackSnapshot> getRecentAttacks(int limit) {
        return attackHistory.stream().limit(limit).toList();
    }

    public AttackSummary getLast24hSummary() {
        long cutoff = System.currentTimeMillis() - 86_400_000L;
        List<AttackSnapshot> recent = attackHistory.stream()
            .filter(s -> s.getTimestamp() > cutoff).toList();
        
        int totalBlocked = recent.stream().mapToInt(AttackSnapshot::getBlockedConnections).sum();
        int peakRate = recent.stream().mapToInt(AttackSnapshot::getPeakConnectionRate).max().orElse(0);
        
        return new AttackSummary(recent.size(), totalBlocked, peakRate);
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remainingSec = seconds % 60;
        return minutes + "dk " + remainingSec + "s";
    }

    public record AttackSummary(int attackCount, int totalBlocked, int peakRate) {}

    private static class AttackSession {
        final long startTime;
        int peakRate = 0;
        int totalBlocked = 0;
        final Map<String, Integer> sourceFrequency = new ConcurrentHashMap<>();

        AttackSession(long startTime) { this.startTime = startTime; }

        void addDataPoint(int rate, int blocked, String ip) {
            synchronized (this) {
                peakRate = Math.max(peakRate, rate);
                totalBlocked += blocked;
            }
            if (ip != null) {
                // Sadece /24 subnet'i kaydediyoruz (dağınıklığı tespit için daha iyi)
                String subnet = extractSubnet(ip);
                sourceFrequency.merge(subnet, 1, Integer::sum);
            }
        }

        private String extractSubnet(String ip) {
            int lastDot = ip.lastIndexOf('.');
            if (lastDot > 0) return ip.substring(0, lastDot) + ".0/24";
            return ip; // IPv6 veya geçersiz ise aynen bırak
        }

        AttackSnapshot toSnapshot() {
            long duration = System.currentTimeMillis() - startTime;
            
            String topCIDR = "Bilinmiyor";
            List<String> topIPs = List.of();
            
            if (!sourceFrequency.isEmpty()) {
                topCIDR = sourceFrequency.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("Bilinmiyor");
                
                topIPs = sourceFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            }

            return AttackSnapshot.current(duration, peakRate, totalBlocked, topCIDR, topIPs, true);
        }
    }
}
