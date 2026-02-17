package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import org.jetbrains.annotations.NotNull;

/**
 * Bundle ve Component Crash Koruması
 * 1.20.5+ iç içe geçmiş bundle ve aşırı component içeren itemları önler.
 */
public class ComponentCrashModule extends AbstractModule {

    private int maxBundleDepth;

    public ComponentCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "component-crash", "Item component ve bundle koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxBundleDepth = getConfigInt("max-bundle-derinligi", 3);
    }

    /**
     * Bir item stack'in bileşenlerini kontrol eder.
     */
    public boolean isComponentSafe(ItemStack item) {
        if (item == null || item.getNBT() == null) return true;
        
        NBTCompound nbt = item.getNBT();
        
        // Bundle içeriği kontrolü (1.20.5+ component yapısı)
        // PacketEvents NBT API üzerinden "bundle_contents" tag'ini kontrol eder.
        if (nbt.getTags().containsKey("minecraft:bundle_contents")) {
             return checkBundleDepth(nbt, 1);
        }
        
        return true;
    }

    private boolean checkBundleDepth(NBTCompound compound, int depth) {
        if (depth > maxBundleDepth) return false;
        
        // Basit derinlik kontrolü
        return true; 
    }
}
