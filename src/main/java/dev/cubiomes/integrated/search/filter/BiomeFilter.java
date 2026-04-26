package dev.cubiomes.integrated.search.filter;

public record BiomeFilter(
	boolean enabled,
	int biomeId,
	int scale,
	int x,
	int y,
	int z
) {
}
