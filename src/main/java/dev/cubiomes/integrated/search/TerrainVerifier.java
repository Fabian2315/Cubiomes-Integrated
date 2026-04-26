package dev.cubiomes.integrated.search;

import java.util.List;

import dev.cubiomes.integrated.search.filter.TerrainFilter;

@FunctionalInterface
public interface TerrainVerifier {
    boolean verifyAtSpawnTop(long seed, List<TerrainFilter> filters);
}
