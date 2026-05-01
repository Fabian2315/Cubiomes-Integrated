package dev.cubiomes.integrated.client.world;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.cubiomes.integrated.CubiomesIntegratedMod;
import dev.cubiomes.integrated.search.TerrainVerifier;
import dev.cubiomes.integrated.search.filter.TerrainFilter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

public final class MinecraftTerrainVerifier implements TerrainVerifier {
    private static final Heightmap.Type HEIGHTMAP_TYPE = Heightmap.Type.WORLD_SURFACE_WG;
    private static final ChunkGenerator OVERWORLD_GENERATOR;
    private static final Set<String> SURFACE_DECORATED_BLOCK_IDS = Set.of(
        "minecraft:grass_block",
        "minecraft:mycelium",
        "minecraft:podzol",
        "minecraft:red_sand",
        "minecraft:sand"
    );

    private final Set<String> invalidBlockWarnings = new HashSet<>();
    private final Set<String> surfaceSamplingWarnings = new HashSet<>();
    private final WrapperLookup builtinLookup = BuiltinRegistries.createWrapperLookup();

    static {
        WrapperLookup lookup = BuiltinRegistries.createWrapperLookup();
        RegistryEntry<ChunkGeneratorSettings> settings = lookup
            .getWrapperOrThrow(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS)
            .getOrThrow(ChunkGeneratorSettings.OVERWORLD);
        RegistryEntry<MultiNoiseBiomeSourceParameterList> biomes = lookup
            .getWrapperOrThrow(net.minecraft.registry.RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        OVERWORLD_GENERATOR = new NoiseChunkGenerator(MultiNoiseBiomeSource.create(biomes), settings);
    }

    @Override
    public boolean verifyAtSpawnTop(long seed, List<TerrainFilter> filters) {
        if (filters.isEmpty()) {
            return true;
        }

        RegistryEntryLookup.RegistryLookup registryLookup = builtinLookup.createRegistryLookup();
        NoiseConfig noiseConfig = NoiseConfig.create(registryLookup, ChunkGeneratorSettings.OVERWORLD, seed);
        HeightLimitView heightLimitView = HeightLimitView.create(OVERWORLD_GENERATOR.getMinimumY(), OVERWORLD_GENERATOR.getWorldHeight());

        for (TerrainFilter filter : filters) {
            Block requiredBlock = resolveBlock(filter.requiredTopBlockId());
            if (requiredBlock == null) {
                return false;
            }

            if (!matchesAnyColumnWithinRadius(OVERWORLD_GENERATOR, noiseConfig, heightLimitView, filter, requiredBlock)) {
                return false;
            }
        }

        return true;
    }

    private Block resolveBlock(String blockId) {
        if (SURFACE_DECORATED_BLOCK_IDS.contains(blockId) && surfaceSamplingWarnings.add(blockId)) {
            CubiomesIntegratedMod.LOGGER.warn(
                "Block filter '{}' relies on biome surface decoration and may under-match in fast sampling mode; use stone-like blocks for strict testing.",
                blockId
            );
        }

        Identifier id = Identifier.tryParse(blockId);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            if (invalidBlockWarnings.add(blockId)) {
                CubiomesIntegratedMod.LOGGER.warn("Terrain filter references an unknown block id '{}'; this filter will fail until corrected.", blockId);
            }
            return null;
        }

        return Registries.BLOCK.get(id);
    }

    private static boolean matchesAnyColumnWithinRadius(
        ChunkGenerator generator,
        NoiseConfig noiseConfig,
        HeightLimitView heightLimitView,
        TerrainFilter filter,
        Block requiredBlock
    ) {
        int radius = Math.max(0, filter.spawnRadiusBlocks());
        int minY = filter.minTopY();
        int maxY = filter.maxTopY();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int topY = generator.getHeight(x, z, HEIGHTMAP_TYPE, heightLimitView, noiseConfig);
                int blockY = topY - 1;
                if (heightLimitView.isOutOfHeightLimit(blockY)) {
                    continue;
                }
                if (blockY < minY || blockY > maxY) {
                    continue;
                }

                BlockState topState = sampleTopState(generator, noiseConfig, heightLimitView, x, z, blockY);
                if (topState != null && topState.isOf(requiredBlock)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static BlockState sampleTopState(
        ChunkGenerator generator,
        NoiseConfig noiseConfig,
        HeightLimitView heightLimitView,
        int x,
        int z,
        int y
    ) {
        VerticalBlockSample column = generator.getColumnSample(x, z, heightLimitView, noiseConfig);
        if (heightLimitView.isOutOfHeightLimit(y)) {
            return null;
        }
        return column.getState(y);
    }
}
