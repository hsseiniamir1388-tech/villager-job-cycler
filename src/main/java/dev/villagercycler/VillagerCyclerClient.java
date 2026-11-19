package dev.villagercycler;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class VillagerCyclerClient implements ClientModInitializer {
    public static final CycleController CONTROLLER = new CycleController();

    private static KeyBinding menuKey;
    private static KeyBinding toggleKey;
    private boolean prevToggleRaw;
    private boolean prevEscRaw;

    @Override
    public void onInitializeClient() {
        CyclerConfig.load();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.villagercycler.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.villagercycler"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.villagercycler.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.villagercycler"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            CONTROLLER.reset();
            return;
        }
        long handle = client.getWindow().getHandle();

        // Emergency stop: ESC works even when a screen is open.
        boolean esc = InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_ESCAPE);
        if (esc && !prevEscRaw && CONTROLLER.isRunning()) {
            CONTROLLER.stop(client, "Emergency stop (ESC).");
        }
        prevEscRaw = esc;

        // The toggle key also stops while the trade screen is open (keybindings don't fire in screens).
        boolean raw = InputUtil.isKeyPressed(handle, KeyBindingHelper.getBoundKeyOf(toggleKey).getCode());
        if (raw && !prevToggleRaw && CONTROLLER.isRunning() && client.currentScreen instanceof MerchantScreen) {
            CONTROLLER.stop(client, "Stopped.");
        }
        prevToggleRaw = raw;

        while (menuKey.wasPressed()) {
            if (client.currentScreen == null) client.setScreen(new ConfigScreen(null));
        }
        while (toggleKey.wasPressed()) {
            CONTROLLER.onToggleKey(client);
        }
        CONTROLLER.tick(client);
    }
}
