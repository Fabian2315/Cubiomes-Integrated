package dev.cubiomes.integrated.search;

import dev.cubiomes.integrated.search.filter.TerrainFilter;

@FunctionalInterface
public interface TerrainVerifier {
    boolean verifyAtSpawnTop(long seed, TerrainFilter filter);
}
