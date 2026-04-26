package dev.cubiomes.integrated.search;

import java.util.List;

import dev.cubiomes.integrated.search.filter.BiomeFilter;
import dev.cubiomes.integrated.search.filter.StructureFilter;
import dev.cubiomes.integrated.search.filter.TerrainFilter;

public record SearchConfig(
    long startSeedInclusive,
    long endSeedExclusive,
    int stride,
    int maxResults,
    int cubiomesMcVersion,
    int generatorFlags,
    List<StructureFilter> structureFilters,
    List<BiomeFilter> biomeFilters,
    TerrainFilter terrainFilter
) {
    public long candidateCount() {
        return Math.max(0L, endSeedExclusive - startSeedInclusive);
    }
}
