package dev.cubiomes.integrated.nativebridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.lang.ref.Cleaner;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public final class NativeCubiomes {
    private static final String NATIVE_PATH_ENV = "CUBIOMES_NATIVE_LIB";
    private static final String NATIVE_PATH_PROPERTY = "cubiomes.native.lib";
    private static final String BUNDLED_NATIVE_RESOURCE = "/native/" + System.mapLibraryName("cubiomes_jna");
    private static final Cleaner CLEANER = Cleaner.create();
    private static final CubiomesLibrary LIB = load();

    private NativeCubiomes() {
    }

    public static int mcVersion1211() {
        return LIB.ci_mc_1_21_1();
    }

    public static String biomeName(int mcVersion, int biomeId) {
        return LIB.ci_biome2str(mcVersion, biomeId);
    }

    public static String structureName(int structureType) {
        return LIB.ci_struct2str(structureType);
    }

    public static NativeGenerator createGenerator(int mcVersion, int flags) {
        Pointer handle = LIB.ci_generator_create(mcVersion, flags);
        if (handle == null || Pointer.nativeValue(handle) == 0L) {
            throw new IllegalStateException("Failed to create cubiomes generator");
        }
        return new NativeGenerator(handle);
    }

    private static CubiomesLibrary load() {
        String configured = System.getProperty(NATIVE_PATH_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(NATIVE_PATH_ENV);
        }

        if (configured != null && !configured.isBlank()) {
            return Native.load(configured, CubiomesLibrary.class);
        }

        String bundled = extractBundledNativeLibrary();
        if (bundled != null) {
            return Native.load(bundled, CubiomesLibrary.class);
        }

        return Native.load("cubiomes_jna", CubiomesLibrary.class, Map.of(
            Library.OPTION_STRING_ENCODING, "UTF-8"
        ));
    }

    private static String extractBundledNativeLibrary() {
        try (InputStream inputStream = NativeCubiomes.class.getResourceAsStream(BUNDLED_NATIVE_RESOURCE)) {
            if (inputStream == null) {
                return null;
            }

            Path extractedLibrary = Files.createTempFile("cubiomes_jna-", System.mapLibraryName("cubiomes_jna"));
            Files.copy(inputStream, extractedLibrary, StandardCopyOption.REPLACE_EXISTING);
            extractedLibrary.toFile().deleteOnExit();
            return extractedLibrary.toAbsolutePath().toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to extract bundled cubiomes native library", exception);
        }
    }

    public static final class NativeGenerator implements AutoCloseable {
        private final Pointer handle;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final Cleaner.Cleanable cleanable;

        private NativeGenerator(Pointer handle) {
            this.handle = handle;
            this.cleanable = CLEANER.register(this, new GeneratorCleanup(handle));
        }

        public void applySeed(int dimension, long seed) {
            ensureOpen();
            int rc = LIB.ci_generator_apply_seed(handle, dimension, seed);
            if (rc != 0) {
                throw new IllegalStateException("Failed to apply seed to cubiomes generator");
            }
        }

        public int getBiomeAt(int scale, int x, int y, int z) {
            ensureOpen();
            return LIB.ci_get_biome_at(handle, scale, x, y, z);
        }

        public boolean tryGetStructurePos(StructureType structureType, int mcVersion, long seed, int regX, int regZ, Pos out) {
            ensureOpen();
            return LIB.ci_get_structure_pos(structureType.ordinal(), mcVersion, seed, regX, regZ, out) != 0;
        }

        private void ensureOpen() {
            if (closed.get()) {
                throw new IllegalStateException("Native generator already closed");
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                cleanable.clean();
            }
        }
    }

    private static final class GeneratorCleanup implements Runnable {
        private final Pointer handle;

        private GeneratorCleanup(Pointer handle) {
            this.handle = handle;
        }

        @Override
        public void run() {
            if (handle != null && Pointer.nativeValue(handle) != 0L) {
                LIB.ci_generator_destroy(handle);
            }
        }
    }

    public static final class Pos extends Structure {
        public static final List<String> FIELDS = List.of("x", "z");

        public int x;
        public int z;

        @Override
        protected List<String> getFieldOrder() {
            return FIELDS;
        }
    }

    public enum StructureType {
        FEATURE,
        DESERT_PYRAMID,
        JUNGLE_TEMPLE,
        SWAMP_HUT,
        IGLOO,
        VILLAGE,
        OCEAN_RUIN,
        SHIPWRECK,
        MONUMENT,
        MANSION,
        OUTPOST,
        RUINED_PORTAL,
        RUINED_PORTAL_N,
        ANCIENT_CITY,
        TREASURE,
        MINESHAFT,
        DESERT_WELL,
        GEODE,
        FORTRESS,
        BASTION,
        END_CITY,
        END_GATEWAY,
        END_ISLAND,
        TRAIL_RUINS,
        TRIAL_CHAMBERS
    }

    private interface CubiomesLibrary extends Library {
        int ci_mc_1_21_1();

        String ci_biome2str(int mcVersion, int biomeId);

        String ci_struct2str(int structureType);

        Pointer ci_generator_create(int mcVersion, int flags);

        void ci_generator_destroy(Pointer generator);

        int ci_generator_apply_seed(Pointer generator, int dimension, long seed);

        int ci_get_biome_at(Pointer generator, int scale, int x, int y, int z);

        int ci_get_structure_pos(int structureType, int mcVersion, long seed, int regX, int regZ, Pos outPos);
    }
}
