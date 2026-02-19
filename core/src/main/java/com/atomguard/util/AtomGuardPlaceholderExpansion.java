package com.atomguard.util;

import com.atomguard.AtomGuard;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for AtomGuard.
 */
public class AtomGuardPlaceholderExpansion extends PlaceholderExpansion {

    private final AtomGuard plugin;

    public AtomGuardPlaceholderExpansion(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "atomguard";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AtomGuard";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("blocked_total")) {
            return String.valueOf(plugin.getModuleManager().getTotalBlockedCount());
        }

        if (params.equalsIgnoreCase("blocked_all_time")) {
            if (plugin.getStatisticsManager() != null) {
                return String.valueOf(plugin.getStatisticsManager().getTotalBlockedAllTime());
            }
            return "0";
        }

        if (params.equalsIgnoreCase("active_modules")) {
            return String.valueOf(plugin.getModuleManager().getEnabledModuleCount());
        }

        if (params.equalsIgnoreCase("total_modules")) {
            return String.valueOf(plugin.getModuleManager().getTotalModuleCount());
        }

        if (params.equalsIgnoreCase("attack_mode")) {
            return plugin.getAttackModeManager().isAttackMode() ? "Saldırı Altında" : "Normal";
        }
        
        if (params.equalsIgnoreCase("connection_rate")) {
            return String.valueOf(plugin.getAttackModeManager().getCurrentRate());
        }

        if (player != null) {
            if (params.equalsIgnoreCase("player_reputation")) {
                // Placeholder for future reputation score if available
                return "100";
            }
        }

        return null;
    }
}
