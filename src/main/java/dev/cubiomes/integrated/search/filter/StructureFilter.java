package dev.cubiomes.integrated.search.filter;

import dev.cubiomes.integrated.nativebridge.NativeCubiomes;

public record StructureFilter(
    boolean enabled,
    NativeCubiomes.StructureType structureType,
    int regionX,
    int regionZ,
    int minX,
    int maxX,
    int minZ,
    int maxZ
) {
    public boolean inRange(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
