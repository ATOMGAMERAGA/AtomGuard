package com.atomguard.limbo;

import org.bukkit.*;
import org.bukkit.plugin.Plugin;

/**
 * Doğrulama dünyası yöneticisi.
 *
 * <p>Void dünya oluşturur. Spawn platformu:
 * <ul>
 *   <li>Y=64: Taş blok (1x1)
 *   <li>Y=65: Oyuncu spawn (havada — yerçekimiyle düşer)
 * </ul>
 *
 * <p>Oyuncu Y=65'te spawn olur, yerçekimiyle Y=64'e (platforma) düşer.
 * Bu düşüş {@link com.atomguard.limbo.check.GravityCheck} tarafından analiz edilir.
 */
public class LimboWorldManager {

    private static final String WORLD_NAME = "atomguard_limbo";
    /** Spawn yüksekliği — platform burada */
    public static final int PLATFORM_Y = 64;
    /** Oyuncu spawn Y'si — bir blok yukarısı (havada) */
    public static final double SPAWN_Y  = PLATFORM_Y + 1.0;

    private final Plugin plugin;
    private World limboWorld;

    public LimboWorldManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Limbo dünyasını oluştur veya yükle.
     */
    public World getOrCreateWorld() {
        // Zaten yüklüyse döndür
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            limboWorld = existing;
            ensurePlatform();
            return limboWorld;
        }

        // Yeni void dünya oluştur
        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.generator(new VoidChunkGenerator());

        limboWorld = creator.createWorld();
        if (limboWorld == null) {
            plugin.getLogger().severe("[Limbo] Dünya oluşturulamadı!");
            return null;
        }

        // Dünya ayarları
        limboWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        limboWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        limboWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        limboWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        limboWorld.setGameRule(GameRule.DO_FIRE_TICK, false);
        limboWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        limboWorld.setDifficulty(Difficulty.PEACEFUL);
        limboWorld.setAutoSave(false);

        ensurePlatform();

        plugin.getLogger().info("[Limbo] Void dünya oluşturuldu: " + WORLD_NAME);
        return limboWorld;
    }

    /** Spawn platformunu garantiye al (sunucu yeniden başlayınca blok kaybolabilir). */
    private void ensurePlatform() {
        if (limboWorld == null) return;
        var block = limboWorld.getBlockAt(0, PLATFORM_Y, 0);
        if (block.getType() != Material.STONE) {
            block.setType(Material.STONE);
        }
    }

    /** Oyuncu spawn konumu. */
    public Location getSpawnLocation() {
        if (limboWorld == null) return null;
        return new Location(limboWorld, 0.5, SPAWN_Y, 0.5, 0, 0);
    }

    public World getLimboWorld() { return limboWorld; }
}
