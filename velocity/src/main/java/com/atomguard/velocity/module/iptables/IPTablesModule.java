package com.atomguard.velocity.module.iptables;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import java.util.concurrent.TimeUnit;

public class IPTablesModule extends VelocityModule {

    private final IPTablesManager manager;
    private final IPTablesRuleStore ruleStore;

    public IPTablesModule(AtomGuardVelocity plugin) {
        super(plugin, "iptables-entegrasyon");
        this.manager = new IPTablesManager(plugin);
        this.ruleStore = new IPTablesRuleStore();
    }

    @Override
    public void onEnable() {
        // Cleanup task for expired bans
        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::cleanupExpiredRules)
            .repeat(1, TimeUnit.MINUTES)
            .schedule();
            
        logger.info("IPTables modülü başlatıldı.");
    }

    @Override
    public void onDisable() {
        if (getConfigBoolean("kural-temizleme.kapatma-temizle", true)) {
            manager.flushAtomGuardRules();
        }
        manager.shutdown();
        logger.info("IPTables modülü durduruldu.");
    }
    
    public void banIp(String ip, long durationMs, String reason) {
        if (!isEnabled()) return;
        
        manager.banIP(ip, durationMs).thenAccept(success -> {
            if (success) {
                IPTablesRule rule = new IPTablesRule(ip, null, "DROP", System.currentTimeMillis() + durationMs);
                ruleStore.addRule(ip, rule);
                
                String msg = plugin.getMessageManager().getRaw("iptables.ban-eklendi")
                        .replace("{ip}", ip)
                        .replace("{sure}", String.valueOf(durationMs / 1000));
                logger.info("IPTables ban: " + msg);
            }
        });
    }

    public void unbanIp(String ip) {
        if (!isEnabled()) return;

        manager.unbanIP(ip).thenAccept(success -> {
            if (success) {
                ruleStore.removeRule(ip);
                String msg = plugin.getMessageManager().getRaw("iptables.ban-kaldirildi")
                        .replace("{ip}", ip);
                logger.info("IPTables unban: " + msg);
            }
        });
    }
    
    public void banSubnet(String subnet) {
        if (!isEnabled() || !getConfigBoolean("subnet-ban-aktif", false)) return;
        
        manager.banSubnet(subnet).thenAccept(success -> {
            if (success) {
                // Add a permanent rule for subnet for now
                IPTablesRule rule = new IPTablesRule(subnet, subnet, "DROP", Long.MAX_VALUE); 
                ruleStore.addRule(subnet, rule);
                logger.info("Subnet engellendi: " + subnet);
            }
        });
    }

    private void cleanupExpiredRules() {
        if (!isEnabled()) return;
        
        for (IPTablesRule rule : ruleStore.getExpiredRules()) {
            manager.unbanIP(rule.getIp()).thenAccept(success -> {
                if (success) {
                    ruleStore.removeRule(rule.getIp());
                    logger.info("Süresi dolan IPTables kuralı kaldırıldı: {}", rule.getIp());
                }
            });
        }
    }
    
    public IPTablesManager getManager() {
        return manager;
    }
}