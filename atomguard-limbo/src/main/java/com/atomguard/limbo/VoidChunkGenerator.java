package com.atomguard.limbo;

import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Tamamen boş (void) dünya chunk generator.
 *
 * <p>Üretilen chunk'larda hiçbir blok yoktur — tamamen hava.
 * Spawn platformu {@link LimboWorldManager} tarafından manuel yerleştirilir.
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public @NotNull ChunkData generateChunkData(
            @NotNull org.bukkit.World world,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull BiomeGrid biome) {
        // Tamamen boş chunk — hiçbir blok oluşturma
        return createChunkData(world);
    }

    @Override
    public boolean shouldGenerateNoise() { return false; }

    @Override
    public boolean shouldGenerateSurface() { return false; }

    @Override
    public boolean shouldGenerateCaves() { return false; }

    @Override
    public boolean shouldGenerateDecorations() { return false; }

    @Override
    public boolean shouldGenerateMobs() { return false; }

    @Override
    public boolean shouldGenerateStructures() { return false; }

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return null;
    }
}
