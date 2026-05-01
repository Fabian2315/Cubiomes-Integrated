#include <stdint.h>
#include <stdlib.h>

#include "../cubiomes-context/cubiomes/finders.h"
#include "../cubiomes-context/cubiomes/util.h"

#if defined(_WIN32)
#define CI_EXPORT __declspec(dllexport)
#else
#define CI_EXPORT __attribute__((visibility("default")))
#endif

typedef struct CiGenerator {
    Generator generator;
} CiGenerator;

CI_EXPORT int ci_mc_1_21_1(void) {
    return MC_1_21_1;
}

CI_EXPORT void *ci_generator_create(int mcVersion, uint32_t flags) {
    CiGenerator *handle = (CiGenerator *)calloc(1, sizeof(CiGenerator));
    if (!handle) {
        return NULL;
    }

    setupGenerator(&handle->generator, mcVersion, flags);
    return handle;
}

CI_EXPORT void ci_generator_destroy(void *generator) {
    if (generator) {
        free(generator);
    }
}

CI_EXPORT int ci_generator_apply_seed(void *generator, int dimension, uint64_t seed) {
    CiGenerator *handle = (CiGenerator *)generator;
    if (!handle) {
        return -1;
    }

    applySeed(&handle->generator, dimension, seed);
    return 0;
}

CI_EXPORT int ci_get_biome_at(void *generator, int scale, int x, int y, int z) {
    CiGenerator *handle = (CiGenerator *)generator;
    if (!handle) {
        return -1;
    }

    return getBiomeAt(&handle->generator, scale, x, y, z);
}

CI_EXPORT int ci_get_structure_pos(int structureType, int mcVersion, uint64_t seed, int regX, int regZ, Pos *outPos) {
    if (!outPos) {
        return 0;
    }

    return getStructurePos(structureType, mcVersion, seed, regX, regZ, outPos);
}

CI_EXPORT const char *ci_biome2str(int mcVersion, int biomeId) {
    return biome2str(mcVersion, biomeId);
}

CI_EXPORT const char *ci_struct2str(int structureType) {
    return struct2str(structureType);
}
