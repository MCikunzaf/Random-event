package com.ikunzaf;

import com.ikunzaf.random.RandomEvent;
import com.ikunzaf.random.RandomEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端随机事件管理器：按玩家维护开关与间隔，每刻计时，到点触发一个随机事件。
 */
public final class RandomEventManager {
	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
	private static final int MIN_INTERVAL_SECONDS = 1;
	private static final int MAX_INTERVAL_SECONDS = 3600;
	private static final int DEFAULT_INTERVAL_SECONDS = 30;

	private RandomEventManager() {
	}

	/** 每个玩家的配置与计时状态（服务端内存态，重启重置）。 */
	public static final class State {
		public boolean enabled;
		public int intervalSeconds = DEFAULT_INTERVAL_SECONDS;
		public int ticksLeft = DEFAULT_INTERVAL_SECONDS * 20;
	}

	public static State get(UUID playerId) {
		return STATES.computeIfAbsent(playerId, id -> new State());
	}

	public static int clampInterval(int seconds) {
		return Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, seconds));
	}

	/** 服务端每刻驱动（END_SERVER_TICK）。 */
	public static void tick(MinecraftServer server) {
		if (RandomEvents.all().isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State state = STATES.get(player.getUUID());
			if (state == null || !state.enabled) {
				continue;
			}
			if (--state.ticksLeft <= 0) {
				state.ticksLeft = state.intervalSeconds * 20;
				trigger(player);
			}
		}
	}

	/** 立即对玩家触发一个随机事件。 */
	public static void trigger(ServerPlayer player) {
		List<RandomEvent> events = RandomEvents.all();
		if (events.isEmpty()) {
			return;
		}
		RandomEvent event = events.get(player.getRandom().nextInt(events.size()));
		String label = event.name();
		try {
			event.apply(player);
		} catch (Exception e) {
			IkunzafMod.LOGGER.error("随机事件 [{}] 执行失败", label, e);
			label = label + "（执行出错）";
		}
		player.sendSystemMessage(Component.literal("§6[随机事件]§r " + label));
	}
}
