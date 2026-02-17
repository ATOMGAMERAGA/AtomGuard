package com.atomguard.module.antibot;

import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AttackTracker {
    private final AntiBotModule module;
    private volatile boolean underAttack = false;
    private final AtomicLong lastBotDetection = new AtomicLong(0);
    private final AtomicInteger recentConnectionCount = new AtomicInteger(0);
    private final ConcurrentLinkedDeque<String> recentUsernames = new ConcurrentLinkedDeque<>();

    public AttackTracker(AntiBotModule module) {
        this.module = module;
    }

    public void recordConnection() {
        recentConnectionCount.incrementAndGet();
    }
    
    public void recordUsername(String username) {
        recentUsernames.addLast(username);
        if (recentUsernames.size() > 50) recentUsernames.removeFirst();
    }

    public void evaluateAttackStatus() {
        int connections = recentConnectionCount.getAndSet(0);
        int threshold = module.getConfigInt("saldiri-modu.tetikleme-esigi", 15);

        if (connections >= threshold) {
            if (!underAttack) {
                underAttack = true;
                notifyAdmins("<gold>⚠ Bot saldırısı tespit edildi! Son 5 saniyede " + connections + " bağlantı.");
            }
            lastBotDetection.set(System.currentTimeMillis());
        }

        if (underAttack) {
            long elapsed = System.currentTimeMillis() - lastBotDetection.get();
            long cooldown = module.getConfigInt("saldiri-modu.kapanma-suresi-sn", 60) * 1000L;
            if (elapsed > cooldown) {
                underAttack = false;
                notifyAdmins("<green>✅ Bot saldırısı sona erdi. Normal moda dönüldü.");
            }
        }
    }

    private void notifyAdmins(String message) {
        String permission = module.getConfigString("bildirimler.admin-izin", "atomguard.antibot.notify");
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(permission))
                .forEach(p -> p.sendMessage(module.getPlugin().getMessageManager().parse(message)));
        module.getPlugin().getLogManager().info(message);
    }

    public boolean isUnderAttack() {
        return underAttack;
    }
    
    public List<String> getRecentUsernames() {
        return new ArrayList<>(recentUsernames);
    }
    
    public long getAttackCooldownMs() {
        return module.getConfigInt("saldiri-modu.kapanma-suresi-sn", 60) * 1000L;
    }
}
