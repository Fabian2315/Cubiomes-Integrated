package dev.cubiomes.integrated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public final class CubiomesIntegratedMod implements ModInitializer {
    public static final String MOD_ID = "cubiomes_integrated";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Cubiomes Integrated initialized");
    }
}
