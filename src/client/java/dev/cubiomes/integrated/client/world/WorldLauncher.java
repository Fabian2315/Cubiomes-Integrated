package dev.cubiomes.integrated.client.world;

import java.lang.reflect.Method;

import dev.cubiomes.integrated.CubiomesIntegratedMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.text.Text;

public final class WorldLauncher {
    private WorldLauncher() {
    }

    public static void launchOrOpenCreateWorld(MinecraftClient client, long seed, Screen fallbackParent) {
        client.keyboard.setClipboard(Long.toString(seed));

        if (tryDirectCreate(client, seed)) {
            return;
        }

        // Fallback keeps workflow fast even when internals shift across mappings.
        openCreateWorldFallback(client, fallbackParent);
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Seed copied to clipboard: " + seed), false);
        }
    }

    private static void openCreateWorldFallback(MinecraftClient client, Screen fallbackParent) {
        try {
            Method showMethod = CreateWorldScreen.class.getMethod("show", MinecraftClient.class, Screen.class);
            showMethod.invoke(null, client, fallbackParent);
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method createWithParent = CreateWorldScreen.class.getMethod("create", Screen.class);
            Object screen = createWithParent.invoke(null, fallbackParent);
            if (screen instanceof Screen createWorldScreen) {
                client.setScreen(createWorldScreen);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method createWithClientAndParent = CreateWorldScreen.class.getMethod("create", MinecraftClient.class, Screen.class);
            Object screen = createWithClientAndParent.invoke(null, client, fallbackParent);
            if (screen instanceof Screen createWorldScreen) {
                client.setScreen(createWorldScreen);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean tryDirectCreate(MinecraftClient client, long seed) {
        try {
            // This method intentionally uses reflection to avoid hard coupling to
            // unstable world-creation internals across minor Minecraft updates.
            Method loaderGetter = MinecraftClient.class.getMethod("createIntegratedServerLoader");
            Object loader = loaderGetter.invoke(client);
            if (loader == null) {
                return false;
            }

            CubiomesIntegratedMod.LOGGER.info("Direct world launch entrypoint discovered, but concrete invocation must be mapped for this Yarn build. Falling back to CreateWorldScreen. Seed={}", seed);
            return false;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
