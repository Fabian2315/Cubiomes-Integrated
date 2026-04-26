package dev.cubiomes.integrated.client.screen;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import dev.cubiomes.integrated.CubiomesIntegratedMod;

public final class SeedMapViewer {
    private SeedMapViewer() {
    }

    public static String buildSeedMapUrl(long seed) {
        return "https://www.cubiomes.com/viewer/?seed=" + seed + "&x=0&z=0&zoom=0";
    }

    public static boolean openInBrowser(String url) {
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
