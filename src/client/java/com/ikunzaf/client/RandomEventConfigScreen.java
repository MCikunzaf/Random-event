package com.ikunzaf.client;

import com.ikunzaf.IkunzafNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 右 Shift 打开的随机事件设置界面：设定间隔秒数、启停、手动触发。
 */
@Environment(EnvType.CLIENT)
public class RandomEventConfigScreen extends Screen {
	private static final int MIN_SECONDS = 1;
	private static final int MAX_SECONDS = 3600;

	private EditBox intervalBox;
	private Button toggleButton;
	private int interval = 30;
	private boolean enabled = false;

	public RandomEventConfigScreen() {
		super(Component.literal("随机事件设置"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = 60;

		// 从客户端缓存读取服务端同步来的真实配置，避免每次打开都重置
		interval = IkunzafClient.ClientState.intervalSeconds;
		enabled = IkunzafClient.ClientState.enabled;

		intervalBox = new EditBox(this.font, centerX - 50, y, 100, 20, Component.literal("间隔秒数"));
		intervalBox.setValue(String.valueOf(interval));
		intervalBox.setHint(Component.literal("1 - 3600"));
		intervalBox.setResponder(s -> {
			try {
				interval = Integer.parseInt(s.trim());
			} catch (NumberFormatException e) {
				interval = 30;
			}
		});
		addRenderableWidget(intervalBox);
		y += 34;

		// -10 -1 +1 +10
		addRenderableWidget(Button.builder(Component.literal("-10s"), b -> adjust(-10))
				.bounds(centerX - 105, y, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("-1s"), b -> adjust(-1))
				.bounds(centerX - 51, y, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+1s"), b -> adjust(1))
				.bounds(centerX + 1, y, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+10s"), b -> adjust(10))
				.bounds(centerX + 55, y, 50, 20).build());
		y += 26;

		toggleButton = Button.builder(Component.literal(toggleLabel()), b -> {
			enabled = !enabled;
			b.setMessage(Component.literal(toggleLabel()));
		}).bounds(centerX - 105, y, 102, 20).build();
		addRenderableWidget(toggleButton);

		addRenderableWidget(Button.builder(Component.literal("立刻触发一次"), b -> {
			sendConfig();
			ClientPlayNetworking.send(new IkunzafNetworking.TriggerNowPayload());
		}).bounds(centerX + 3, y, 102, 20).build());
		y += 26;

		addRenderableWidget(Button.builder(Component.literal("保存并关闭"), b -> {
			sendConfig();
			onClose();
		}).bounds(centerX - 51, y, 102, 20).build());
	}

	private String toggleLabel() {
		return enabled ? "状态：已启用（点击停用）" : "状态：已停用（点击启用）";
	}

	private void adjust(int delta) {
		interval = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, interval + delta));
		intervalBox.setValue(String.valueOf(interval));
	}

	private void sendConfig() {
		int seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, interval));
		IkunzafClient.ClientState.enabled = enabled;
		IkunzafClient.ClientState.intervalSeconds = seconds;
		ClientPlayNetworking.send(new IkunzafNetworking.SetConfigPayload(enabled, seconds));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 30, 0xFFFF55);
		graphics.centeredText(this.font, Component.literal("按右 Shift 打开此界面；到点自动触发一个随机事件"),
				this.width / 2, 44, 0xAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
