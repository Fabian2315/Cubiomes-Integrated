package dev.cubiomes.integrated.search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import dev.cubiomes.integrated.nativebridge.NativeCubiomes;
import dev.cubiomes.integrated.nativebridge.NativeCubiomes.Pos;
import dev.cubiomes.integrated.search.filter.BiomeFilter;
import dev.cubiomes.integrated.search.filter.StructureFilter;
import dev.cubiomes.integrated.search.filter.TerrainFilter;

public final class SeedSearcher implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public CompletableFuture<List<SearchResult>> start(
        SearchConfig config,
        TerrainVerifier terrainVerifier,
        Consumer<SearchProgress> progressConsumer,
        Consumer<SearchResult> resultConsumer
    ) {
        cancelled.set(false);

        return CompletableFuture.supplyAsync(() -> runSearch(config, terrainVerifier, progressConsumer, resultConsumer), executor);
    }

    public void cancel() {
        cancelled.set(true);
    }

    private List<SearchResult> runSearch(
        SearchConfig config,
        TerrainVerifier terrainVerifier,
        Consumer<SearchProgress> progressConsumer,
        Consumer<SearchResult> resultConsumer
    ) {
        List<SearchResult> accepted = new ArrayList<>();
        AtomicLong scanned = new AtomicLong();
        AtomicLong stage1Passed = new AtomicLong();
        AtomicLong stage2Checked = new AtomicLong();

        TerrainFilter terrainFilter = config.terrainFilter();
        int stage2Budget = terrainFilter.enabled() && terrainFilter.slowFilterWarningEnabled()
            ? Math.max(1, terrainFilter.javaVerificationBudget())
            : Integer.MAX_VALUE;

        try (NativeCubiomes.NativeGenerator generator = NativeCubiomes.createGenerator(config.cubiomesMcVersion(), config.generatorFlags())) {
            for (long seed = config.startSeedInclusive(); seed < config.endSeedExclusive(); seed += Math.max(1, config.stride())) {
                if (cancelled.get() || accepted.size() >= config.maxResults()) {
                    break;
                }

                scanned.incrementAndGet();
                generator.applySeed(0, seed);

                if (!passesBiomeFilters(generator, config.biomeFilters())) {
                    publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), stage2Checked.get(), accepted.size());
                    continue;
                }

                if (!passesStructureFilters(generator, config, seed)) {
                    publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), stage2Checked.get(), accepted.size());
                    continue;
                }

                stage1Passed.incrementAndGet();

                if (terrainFilter.enabled()) {
                    if (stage2Checked.get() >= stage2Budget) {
                        publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), stage2Checked.get(), accepted.size());
                        continue;
                    }
                    stage2Checked.incrementAndGet();
                    if (!terrainVerifier.verifyAtSpawnTop(seed, terrainFilter)) {
                        publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), stage2Checked.get(), accepted.size());
                        continue;
                    }
                }

                SearchResult result = new SearchResult(seed, terrainFilter.enabled() ? "Stage1+Stage2 match" : "Stage1 match");
                accepted.add(result);
                resultConsumer.accept(result);
                publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), stage2Checked.get(), accepted.size());
            }
        }

        return accepted;
    }

    private static boolean passesBiomeFilters(NativeCubiomes.NativeGenerator generator, List<BiomeFilter> biomeFilters) {
        for (BiomeFilter filter : biomeFilters) {
            int biome = generator.getBiomeAt(filter.scale(), filter.x(), filter.y(), filter.z());
            if (biome != filter.biomeId()) {
                return false;
            }
        }
        return true;
    }

    private static boolean passesStructureFilters(NativeCubiomes.NativeGenerator generator, SearchConfig config, long seed) {
        for (StructureFilter filter : config.structureFilters()) {
            Pos pos = new Pos();
            int regionX = 0;
            int regionZ = 0;
            boolean found = generator.tryGetStructurePos(filter.structureType(), config.cubiomesMcVersion(), seed, regionX, regionZ, pos);
            if (!found) {
                return false;
            }
            if (!filter.inRange(pos.x, pos.z)) {
                return false;
            }
        }
        return true;
    }

    private static void publishProgress(Consumer<SearchProgress> progressConsumer, long scanned, long stage1Passed, long stage2Checked, int accepted) {
        progressConsumer.accept(new SearchProgress(scanned, stage1Passed, stage2Checked, accepted));
    }

    @Override
    public void close() {
        cancel();
        executor.shutdownNow();
    }
}
