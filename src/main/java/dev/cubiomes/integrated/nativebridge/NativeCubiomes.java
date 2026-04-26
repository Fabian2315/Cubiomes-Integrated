package dev.cubiomes.integrated.nativebridge;

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
    private static final Cleaner CLEANER = Cleaner.create();
    private static final CubiomesLibrary LIB = load();

    private NativeCubiomes() {
    }

    public static int mcVersion1211() {
        return LIB.ci_mc_1_21_1();
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

        return Native.load("cubiomes_jna", CubiomesLibrary.class, Map.of(
            Library.OPTION_STRING_ENCODING, "UTF-8"
        ));
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

        Pointer ci_generator_create(int mcVersion, int flags);

        void ci_generator_destroy(Pointer generator);

        int ci_generator_apply_seed(Pointer generator, int dimension, long seed);

        int ci_get_biome_at(Pointer generator, int scale, int x, int y, int z);

        int ci_get_structure_pos(int structureType, int mcVersion, long seed, int regX, int regZ, Pos outPos);
    }
}
