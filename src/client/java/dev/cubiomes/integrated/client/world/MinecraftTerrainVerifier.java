package dev.cubiomes.integrated.client.world;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.cubiomes.integrated.CubiomesIntegratedMod;
import dev.cubiomes.integrated.search.TerrainVerifier;
import dev.cubiomes.integrated.search.filter.TerrainFilter;

public final class MinecraftTerrainVerifier implements TerrainVerifier {
    private final AtomicBoolean warningLogged = new AtomicBoolean(false);

    @Override
    public boolean verifyAtSpawnTop(long seed, List<TerrainFilter> filters) {
        if (filters.isEmpty()) {
            return true;
        }

        if (warningLogged.compareAndSet(false, true)) {
            // Hook point for exact block-level checks:
            // build NoiseConfig + NoiseChunkGenerator for the requested seed,
            // compute spawn-height using Heightmap logic, then inspect top-state.
            CubiomesIntegratedMod.LOGGER.warn("Terrain verification hook is currently a lightweight placeholder; integrate ChunkGenerator-based block checks here for exact top-block validation.");
        }

        // Keep Stage 2 non-blocking by allowing candidates through until the
        // full world-gen simulation is wired in.
        return true;
    }
}
