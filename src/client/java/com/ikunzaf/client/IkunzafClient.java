package com.ikunzaf.client;

import com.ikunzaf.IkunzafMod;
import com.ikunzaf.IkunzafNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

public class IkunzafClient implements ClientModInitializer {
	private static KeyMapping openConfigKey;

	/** 客户端缓存的配置（由服务端推送，打开 GUI 时显示真实值）。 */
	public static final class ClientState {
		public static boolean enabled = false;
		public static int intervalSeconds = 30;
	}

	private static boolean pendingOpen = false;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(IkunzafMod.id("main"));
		openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.ikunzaf.open_config",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openConfigKey.consumeClick()) {
				if (client.player != null) {
					// 先向服务端请求当前配置，收到 SyncConfig 后再打开 GUI
					pendingOpen = true;
					ClientPlayNetworking.send(new IkunzafNetworking.RequestSyncPayload());
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(IkunzafNetworking.SyncConfigPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				ClientState.enabled = payload.enabled();
				ClientState.intervalSeconds = payload.intervalSeconds();
				if (pendingOpen) {
					pendingOpen = false;
					Minecraft.getInstance().setScreenAndShow(new RandomEventConfigScreen());
				}
			});
		});
	}
}
