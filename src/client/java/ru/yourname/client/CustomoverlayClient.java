package ru.yourname.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CustomoverlayClient implements ClientModInitializer {
	public static KeyBinding keyEditMode;
	public static KeyBinding keyAddField;
	public static final KeyBinding.Category CATEGORY = new KeyBinding.Category("category.customoverlay");

	@Override
	public void onInitializeClient() {
		keyEditMode = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.customoverlay.editmode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY
		));
		keyAddField = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.customoverlay.addfield", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY
		));

		ConfigManager.load();

		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			OverlayRenderer.render(drawContext);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.currentScreen == null) {
				while (keyEditMode.wasPressed()) client.setScreen(new EditOverlayScreen());
				while (keyAddField.wasPressed()) client.setScreen(new AddFieldScreen());
			}
		});
	}
}
