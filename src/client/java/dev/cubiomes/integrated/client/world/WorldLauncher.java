package dev.cubiomes.integrated.client.world;

import java.lang.reflect.Method;

import dev.cubiomes.integrated.CubiomesIntegratedMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;

public final class WorldLauncher {
    private static Long pendingSeed;

    private WorldLauncher() {
    }

    public static void launchOrOpenCreateWorld(MinecraftClient client, long seed, Screen fallbackParent) {
        client.keyboard.setClipboard(Long.toString(seed));

        pendingSeed = seed;

        if (tryDirectCreate(client, seed)) {
            pendingSeed = null;
            return;
        }

        // Fallback keeps workflow fast even when internals shift across mappings.
        if (!openCreateWorldFallback(client, fallbackParent)) {
            pendingSeed = null;
        }
    }

    public static void handleCreateWorldScreenInitialized(MinecraftClient client, Screen screen) {
        if (pendingSeed == null || !(screen instanceof CreateWorldScreen createWorldScreen)) {
            return;
        }

        long seed = pendingSeed;
        pendingSeed = null;

        if (!setWorldSeed(createWorldScreen, seed)) {
            CubiomesIntegratedMod.LOGGER.warn("CreateWorldScreen opened, but the seed field could not be updated for seed {}", seed);
            return;
        }

        if (!invokeCreate(createWorldScreen)) {
            CubiomesIntegratedMod.LOGGER.warn("CreateWorldScreen opened with seed {}, but create action could not be invoked", seed);
        }
    }

    private static boolean openCreateWorldFallback(MinecraftClient client, Screen fallbackParent) {
        try {
            Method showMethod = CreateWorldScreen.class.getMethod("show", MinecraftClient.class, Screen.class);
            showMethod.invoke(null, client, fallbackParent);
            return true;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method createWithParent = CreateWorldScreen.class.getMethod("create", Screen.class);
            Object screen = createWithParent.invoke(null, fallbackParent);
            if (screen instanceof Screen createWorldScreen) {
                client.setScreen(createWorldScreen);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method createWithClientAndParent = CreateWorldScreen.class.getMethod("create", MinecraftClient.class, Screen.class);
            Object screen = createWithClientAndParent.invoke(null, client, fallbackParent);
            if (screen instanceof Screen createWorldScreen) {
                client.setScreen(createWorldScreen);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return false;
    }

    private static boolean setWorldSeed(CreateWorldScreen screen, long seed) {
        try {
            Method getUiState = CreateWorldScreen.class.getMethod("getUiState");
            Object uiState = getUiState.invoke(screen);
            Method setSeed = uiState.getClass().getMethod("setSeed", String.class);
            setSeed.invoke(uiState, Long.toString(seed));
            return true;
        } catch (ReflectiveOperationException ex) {
            CubiomesIntegratedMod.LOGGER.error("Failed to set create-world seed {}", seed, ex);
            return false;
        }
    }

    private static boolean invokeCreate(CreateWorldScreen screen) {
        try {
            Method onCreate = CreateWorldScreen.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            onCreate.invoke(screen);
            return true;
        } catch (ReflectiveOperationException ex) {
            CubiomesIntegratedMod.LOGGER.error("Failed to trigger create-world action", ex);
            return false;
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
