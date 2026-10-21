package com.ikunzaf.random;

import net.minecraft.server.level.ServerPlayer;

/**
 * 一个随机事件。apply 在服务端主线程执行，接收目标玩家。
 */
public interface RandomEvent {
	/** 事件名称（展示给玩家）。 */
	String name();

	/** 对玩家施加该事件。 */
	void apply(ServerPlayer player);

	static RandomEvent of(String name, java.util.function.Consumer<ServerPlayer> action) {
		return new RandomEvent() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public void apply(ServerPlayer player) {
				action.accept(player);
			}
		};
	}
}
