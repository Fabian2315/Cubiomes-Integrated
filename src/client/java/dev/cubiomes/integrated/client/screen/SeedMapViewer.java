package dev.cubiomes.integrated.client.screen;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import dev.cubiomes.integrated.CubiomesIntegratedMod;
import net.minecraft.util.Util;

public final class SeedMapViewer {
    private SeedMapViewer() {
    }

    public static String buildSeedMapUrl(long seed) {
        return "https://www.chunkbase.com/apps/seed-map#seed=" + seed;
    }

    public static boolean openInBrowser(String url) {
        try {
            Util.getOperatingSystem().open(url);
            return true;
        } catch (Exception exception) {
            CubiomesIntegratedMod.LOGGER.warn("Minecraft URL opener failed for {}. Trying Desktop API fallback.", url, exception);
        }

        if (!Desktop.isDesktopSupported()) {
            CubiomesIntegratedMod.LOGGER.warn("Desktop browsing is not supported on this platform.");
            return false;
        }

        try {
            Desktop.getDesktop().browse(new URI(url));
            return true;
        } catch (IOException | URISyntaxException exception) {
            CubiomesIntegratedMod.LOGGER.error("Failed to open seed map URL {}", url, exception);
            return false;
        }
    }
}
