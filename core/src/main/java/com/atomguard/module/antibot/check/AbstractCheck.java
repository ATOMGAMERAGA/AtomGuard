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
        return module.getConfigBoolean("kontroller." + name + ".aktif", true);
    }

    public double getAttackModeMultiplier() {
        return module.getConfigDouble("kontroller." + name + ".saldiri-carpani", 1.0);
    }

    public String getName() {
        return name;
    }
}
