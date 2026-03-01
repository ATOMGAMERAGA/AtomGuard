package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Anvil ve Crafting Crash Koruması
 * 1.21.x aşırı uzun isim ve geçersiz recipe exploitlerini önler.
 */
public class AnvilCraftCrashModule extends AbstractModule implements Listener {

    private int maxRenameLength;

    public AnvilCraftCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "ors-craft-crash", "Anvil ve Crafting koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxRenameLength = getConfigInt("anvil-max-isim-uzunlugu", 50);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!isEnabled()) return;

        String renameText = event.getInventory().getRenameText();
        if (renameText != null && renameText.length() > maxRenameLength) {
            event.setResult(null); // Sonucu iptal et
            incrementBlockedCount();
            debug("Aşırı uzun anvil ismi engellendi: " + renameText.length());
        }
    }
}
