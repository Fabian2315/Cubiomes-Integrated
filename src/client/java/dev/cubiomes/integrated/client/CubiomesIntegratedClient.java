package dev.cubiomes.integrated.client;

import org.lwjgl.glfw.GLFW;

import dev.cubiomes.integrated.client.screen.CubiomesDashboardScreen;
import dev.cubiomes.integrated.client.world.WorldLauncher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public final class CubiomesIntegratedClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.categories.cubiomes_integrated";
    private static final String KEY_OPEN = "key.cubiomes_integrated.open_dashboard";
    private static final Text DASHBOARD_BUTTON_TEXT = Text.literal("Open Seed Dashboard");

    private static KeyBinding openDashboardKey;

    @Override
    public void onInitializeClient() {
        openDashboardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_OPEN,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY
        ));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(DASHBOARD_BUTTON_TEXT, button -> openDashboard(client, screen))
                    .dimensions(scaledWidth - 114, 8, 106, 20)
                    .build());
            }

            WorldLauncher.handleCreateWorldScreenInitialized(client, screen);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDashboardKey.wasPressed()) {
                openDashboard(client, client.currentScreen);
            }
        });
    }

    private static void openDashboard(MinecraftClient client, Screen parent) {
        client.setScreen(new CubiomesDashboardScreen(Text.literal("Cubiomes Integrated"), parent));
    }
}
