package com.atomguard.velocity.module;

import com.atomguard.api.module.IModule;
import com.atomguard.velocity.AtomGuardVelocity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class VelocityModule implements IModule {

    protected final AtomGuardVelocity plugin;
    protected final String name;
    protected final Logger logger;
    protected volatile boolean enabled = false;
    private final AtomicLong blockedCount = new AtomicLong(0);

    protected VelocityModule(AtomGuardVelocity plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.logger = plugin.getSlf4jLogger();
    }

    public final void enable() {
        if (isConfigActive()) {
            onEnable();
            enabled = true;
        } else {
            logger.info("Modül devre dışı (config): {}", name);
        }
    }

    public final void disable() {
        if (enabled) {
            onDisable();
            enabled = false;
        }
    }

    protected void onEnable() {}
    protected void onDisable() {}

    @Override
    public @NotNull String getName() { return name; }
    
    @Override
    public @NotNull String getDescription() {
        return "AtomGuard Velocity protection module: " + name;
    }

    @Override
    public boolean isEnabled() { return enabled; }
    
    @Override
    public long getBlockedCount() { return blockedCount.get(); }
    
    protected void incrementBlocked() { blockedCount.incrementAndGet(); }

    /** Bu modülün bağımlı olduğu modül isimleri */
    public java.util.List<String> getDependencies() {
        return java.util.List.of();
    }

    /** Bu modülün etkinleştirme sırası (düşük = önce) */
    public int getPriority() {
        return 100;
    }

    /** Yapılandırma yenilendiğinde çağrılır. */
    public void onConfigReload() {
        // Alt sınıflar override edebilir
    }

    protected boolean isAttackMode() { return plugin.isAttackMode(); }

    protected boolean isConfigActive() {
        return plugin.getConfigManager().getBoolean("moduller." + name + ".aktif", true);
    }

    protected boolean getConfigBoolean(String key, boolean def) {
        return plugin.getConfigManager().getBoolean("moduller." + name + "." + key, def);
    }

    protected int getConfigInt(String key, int def) {
        return plugin.getConfigManager().getInt("moduller." + name + "." + key, def);
    }

    protected long getConfigLong(String key, long def) {
        return plugin.getConfigManager().getLong("moduller." + name + "." + key, def);
    }

    protected double getConfigDouble(String key, double def) {
        return plugin.getConfigManager().getDouble("moduller." + name + "." + key, def);
    }

    protected String getConfigString(String key, String def) {
        return plugin.getConfigManager().getString("moduller." + name + "." + key, def);
    }

    protected List<String> getConfigStringList(String key) {
        return plugin.getConfigManager().getStringList("moduller." + name + "." + key);
    }
}
