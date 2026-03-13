package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;

public abstract class AbstractCheck {
    protected final AntiBotModule module;
    protected final String name;

    public AbstractCheck(AntiBotModule module, String name) {
        this.module = module;
        this.name = name;
    }

    public abstract int calculateThreatScore(PlayerProfile profile);

    public boolean isEnabled() {
        return module.getConfigBoolean("checks." + name + ".enabled", true);
    }

    public double getAttackModeMultiplier() {
        return module.getConfigDouble("checks." + name + ".attack-multiplier", 1.0);
    }

    public String getName() {
        return name;
    }
}
