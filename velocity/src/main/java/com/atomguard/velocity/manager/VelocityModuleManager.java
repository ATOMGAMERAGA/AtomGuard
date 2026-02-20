package com.atomguard.velocity.manager;

import com.atomguard.velocity.module.VelocityModule;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityModuleManager {

    private final Logger logger;
    private final Map<String, VelocityModule> modules = new ConcurrentHashMap<>();

    public VelocityModuleManager(Logger logger) {
        this.logger = logger;
    }

    public void register(VelocityModule module) {
        modules.put(module.getName(), module);
        logger.debug("Modül kaydedildi: {}", module.getName());
    }

    public void enableAll() {
        modules.values().stream()
            .sorted(java.util.Comparator.comparingInt(VelocityModule::getPriority))
            .forEach(m -> {
                // Bağımlılık Kontrolü
                for (String dep : m.getDependencies()) {
                    VelocityModule depModule = modules.get(dep);
                    if (depModule == null || !depModule.isEnabled()) {
                        logger.warn("Modül '{}' aktif edilemedi: bağımlılık '{}' eksik veya devre dışı.", m.getName(), dep);
                        return;
                    }
                }
                
                try {
                    m.enable();
                    logger.info("Modül aktif: {} (Öncelik: {})", m.getName(), m.getPriority());
                } catch (Exception e) {
                    logger.error("Modül başlatılamadı {}: {}", m.getName(), e.getMessage());
                }
            });
    }

    public void disableAll() {
        modules.values().forEach(m -> {
            try {
                m.disable();
            } catch (Exception e) {
                logger.error("Modül kapatılamadı {}: {}", m.getName(), e.getMessage());
            }
        });
    }

    public VelocityModule getModule(String name) {
        return modules.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T extends VelocityModule> T getModule(Class<T> type) {
        return (T) modules.values().stream()
                .filter(m -> type.isAssignableFrom(m.getClass()))
                .findFirst().orElse(null);
    }

    public boolean toggle(String name) {
        VelocityModule m = modules.get(name);
        if (m == null) return false;
        if (m.isEnabled()) { m.disable(); return false; }
        else { m.enable(); return true; }
    }

    public Collection<VelocityModule> getAll() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public int getEnabledCount() {
        return (int) modules.values().stream().filter(VelocityModule::isEnabled).count();
    }

    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        modules.values().forEach(m -> stats.put(m.getName(), m.getBlockedCount()));
        return stats;
    }
}
