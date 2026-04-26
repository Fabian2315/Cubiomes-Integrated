package dev.cubiomes.integrated.client;

import org.lwjgl.glfw.GLFW;

import dev.cubiomes.integrated.client.screen.CubiomesDashboardScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public final class CubiomesIntegratedClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.categories.cubiomes_integrated";
    private static final String KEY_OPEN = "key.cubiomes_integrated.open_dashboard";

    private static KeyBinding openDashboardKey;

    @Override
    public void onInitializeClient() {
        openDashboardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_OPEN,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDashboardKey.wasPressed()) {
                client.setScreen(new CubiomesDashboardScreen(Text.literal("Cubiomes Integrated")));
            }
        });
    }
}
