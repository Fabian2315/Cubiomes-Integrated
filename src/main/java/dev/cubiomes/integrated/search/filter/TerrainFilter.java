package dev.cubiomes.integrated.search.filter;

public record TerrainFilter(
    boolean enabled,
    String requiredTopBlockId,
    int spawnRadiusBlocks,
    int minTopY,
    int maxTopY,
    boolean slowFilterWarningEnabled,
    int javaVerificationBudget
) {
    public static TerrainFilter disabled() {
        return new TerrainFilter(false, "", 0, Integer.MIN_VALUE, Integer.MAX_VALUE, true, 64);
    }
}
