package dev.cubiomes.integrated.search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.cubiomes.integrated.nativebridge.NativeCubiomes;
import dev.cubiomes.integrated.nativebridge.NativeCubiomes.Pos;
import dev.cubiomes.integrated.search.filter.BiomeFilter;
import dev.cubiomes.integrated.search.filter.StructureFilter;

public final class SeedSearcher implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public CompletableFuture<List<SearchResult>> start(
        SearchConfig config,
        Consumer<SearchProgress> progressConsumer,
        Consumer<SearchResult> resultConsumer
    ) {
        cancelled.set(false);

        return CompletableFuture.supplyAsync(() -> runSearch(config, progressConsumer, resultConsumer), executor);
    }

    public void cancel() {
        cancelled.set(true);
    }

    private List<SearchResult> runSearch(
        SearchConfig config,
        Consumer<SearchProgress> progressConsumer,
        Consumer<SearchResult> resultConsumer
    ) {
        List<SearchResult> accepted = new ArrayList<>();
        AtomicLong scanned = new AtomicLong();
        AtomicLong stage1Passed = new AtomicLong();

        List<BiomeFilter> biomeFilters = config.biomeFilters().stream()
            .filter(BiomeFilter::enabled)
            .collect(Collectors.toList());
        List<StructureFilter> structureFilters = config.structureFilters().stream()
            .filter(StructureFilter::enabled)
            .collect(Collectors.toList());

        try (NativeCubiomes.NativeGenerator generator = NativeCubiomes.createGenerator(config.cubiomesMcVersion(), config.generatorFlags())) {
            for (long seed = config.startSeedInclusive(); seed < config.endSeedExclusive(); seed += Math.max(1, config.stride())) {
                if (cancelled.get() || accepted.size() >= config.maxResults()) {
                    break;
                }

                scanned.incrementAndGet();
                publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), accepted.size());
                generator.applySeed(0, seed);

                if (!passesBiomeFilters(generator, biomeFilters)) {
                    publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), accepted.size());
                    continue;
                }

                if (!passesStructureFilters(generator, config.cubiomesMcVersion(), seed, structureFilters)) {
                    publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), accepted.size());
                    continue;
                }

                stage1Passed.incrementAndGet();

                SearchResult result = new SearchResult(seed, "Cubiomes match");
                accepted.add(result);
                resultConsumer.accept(result);
                publishProgress(progressConsumer, scanned.get(), stage1Passed.get(), accepted.size());
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

    private static boolean passesStructureFilters(
        NativeCubiomes.NativeGenerator generator,
        int mcVersion,
        long seed,
        List<StructureFilter> structureFilters
    ) {
        for (StructureFilter filter : structureFilters) {
            Pos pos = new Pos();
            boolean found = generator.tryGetStructurePos(filter.structureType(), mcVersion, seed, filter.regionX(), filter.regionZ(), pos);
            if (!found) {
                return false;
            }
            if (!filter.inRange(pos.x, pos.z)) {
                return false;
            }
        }
        return true;
    }

    private static void publishProgress(Consumer<SearchProgress> progressConsumer, long scanned, long stage1Passed, long accepted) {
        progressConsumer.accept(new SearchProgress(scanned, stage1Passed, accepted));
    }

    @Override
    public void close() {
        cancel();
        executor.shutdownNow();
    }
}
