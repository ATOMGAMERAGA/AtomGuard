package com.atomguard.velocity.module;

import com.atomguard.velocity.AtomGuardVelocity;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class VelocityModule {

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

    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public long getBlockedCount() { return blockedCount.get(); }
    protected void incrementBlocked() { blockedCount.incrementAndGet(); }

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
