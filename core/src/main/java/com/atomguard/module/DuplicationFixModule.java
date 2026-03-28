package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Hibrit Duplikasyon Koruması
 *
 * Raporun 5.2 maddesi kapsamındaki:
 * - Shulkerception (İç içe Shulker)
 * - Portal/Donkey Dupe
 * korumalarını sağlar.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class DuplicationFixModule extends AbstractModule implements Listener {

    public DuplicationFixModule(@NotNull AtomGuard plugin) {
        super(plugin, "advanced-duplication", "Portal ve Shulker dupe koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable(); // HandlerList.unregisterAll(this) zaten super içinde
    }

    // --- Portal / Teleport Dupe Fixes ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortal(PlayerPortalEvent event) {
        if (!isEnabled()) return;
        // Portaldan geçerken GUI açık olmamalı
        event.getPlayer().closeInventory();
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isEnabled()) return;
        // Işınlanırken GUI açık olmamalı (özellikle binek üzerindeyken)
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || 
            event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.getPlayer().closeInventory();
        }
    }

    // --- Shulkerception Fixes ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled()) return;

        // Geçersiz slot kontrolü: dış tıklama (slot=-1), cursor slot vb.
        // Aksi hâlde ArrayIndexOutOfBoundsException riski var.
        if (event.getRawSlot() < 0 || event.getSlot() < 0) return;

        // Sadece Shulker Box envanterinde işlem yapılırken
        if (event.getInventory().getType() != InventoryType.SHULKER_BOX) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // 1. Shulker içine Shulker koyma kontrolü (Normal click)
        if (cursor != null && isShulkerBox(cursor.getType())) {
            event.setCancelled(true);
            debug(event.getWhoClicked().getName() + " shulker içine shulker koymaya çalıştı (Click)");
        }

        // 2. Hotbar swap tuşu ile koyma
        ClickType clickType = event.getClick();
        if (clickType == ClickType.SWAP_OFFHAND) {
            // F tuşu — offhand'deki item shulker'a taşınmak isteniyorsa engelle
            ItemStack offhand = event.getWhoClicked().getInventory().getItemInOffHand();
            if (isShulkerBox(offhand.getType())) {
                event.setCancelled(true);
                debug(event.getWhoClicked().getName() + " shulker içine shulker koymaya çalıştı (Offhand Swap)");
            }
        } else if (clickType.isKeyboardClick()) {
            int hotbarSlot = event.getHotbarButton();
            int invSize = event.getWhoClicked().getInventory().getSize();
            if (hotbarSlot >= 0 && hotbarSlot < invSize) {
                ItemStack swapped = event.getWhoClicked().getInventory().getItem(hotbarSlot);
                if (swapped != null && isShulkerBox(swapped.getType())) {
                    event.setCancelled(true);
                    debug(event.getWhoClicked().getName() + " shulker içine shulker koymaya çalıştı (Swap)");
                }
            }
        }
        
        // 3. Shift-Click ile Shulker içine gönderme
        // (Burada event.getInventory() hedef envanterdir, ancak shift click'te event.getClickedInventory() kaynak olabilir)
        // Eğer oyuncu envanterine tıklayıp Shulker'a göndermeye çalışıyorsa:
        if (event.isShiftClick() && event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
             if (current != null && isShulkerBox(current.getType())) {
                 event.setCancelled(true);
                 debug(event.getWhoClicked().getName() + " shulker içine shulker göndermeye çalıştı (Shift-Click)");
             }
        }
    }

    private boolean isShulkerBox(Material material) {
        return material.name().contains("SHULKER_BOX");
    }
}
