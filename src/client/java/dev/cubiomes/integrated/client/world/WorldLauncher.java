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
        CreateWorldScreen.show(client, fallbackParent);
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Seed copied to clipboard: " + seed), false);
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
