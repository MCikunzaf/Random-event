package com.ikunzaf;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 随机事件网络协议：
 * C2S SetConfig（保存设置）、TriggerNow（手动触发）、RequestSync（打开 GUI 时请求当前配置）；
 * S2C SyncConfig（服务端把该玩家的实际配置推回去，客户端据此显示）。
 */
public final class IkunzafNetworking {
	private IkunzafNetworking() {
	}

	/** 设置随机事件配置：开关 + 间隔秒数。 */
	public record SetConfigPayload(boolean enabled, int intervalSeconds) implements CustomPacketPayload {
		public static final Type<SetConfigPayload> TYPE =
				new Type<>(IkunzafMod.id("set_random_event_config"));

		public static final StreamCodec<ByteBuf, SetConfigPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, SetConfigPayload::enabled,
				ByteBufCodecs.VAR_INT, SetConfigPayload::intervalSeconds,
				SetConfigPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 手动立刻触发一次随机事件。 */
	public record TriggerNowPayload() implements CustomPacketPayload {
		public static final Type<TriggerNowPayload> TYPE =
				new Type<>(IkunzafMod.id("trigger_random_event_now"));

		public static final StreamCodec<ByteBuf, TriggerNowPayload> CODEC =
				StreamCodec.unit(new TriggerNowPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 客户端打开 GUI 前请求服务端同步当前配置。 */
	public record RequestSyncPayload() implements CustomPacketPayload {
		public static final Type<RequestSyncPayload> TYPE =
				new Type<>(IkunzafMod.id("request_random_event_sync"));

		public static final StreamCodec<ByteBuf, RequestSyncPayload> CODEC =
				StreamCodec.unit(new RequestSyncPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 服务端推送：该玩家当前的随机事件配置。 */
	public record SyncConfigPayload(boolean enabled, int intervalSeconds) implements CustomPacketPayload {
		public static final Type<SyncConfigPayload> TYPE =
				new Type<>(IkunzafMod.id("sync_random_event_config"));

		public static final StreamCodec<ByteBuf, SyncConfigPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, SyncConfigPayload::enabled,
				ByteBufCodecs.VAR_INT, SyncConfigPayload::intervalSeconds,
				SyncConfigPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static void sendSync(ServerPlayer player) {
		RandomEventManager.State state = RandomEventManager.get(player.getUUID());
		ServerPlayNetworking.send(player,
				new SyncConfigPayload(state.enabled, state.intervalSeconds));
	}

	public static void register() {
		// 新版 Fabric API 要求先注册 payload 类型（codec），再注册接收器
		PayloadTypeRegistry.serverboundPlay().register(SetConfigPayload.TYPE, SetConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TriggerNowPayload.TYPE, TriggerNowPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestSyncPayload.TYPE, RequestSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SetConfigPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				RandomEventManager.State state =
						RandomEventManager.get(context.player().getUUID());
				state.enabled = payload.enabled();
				state.intervalSeconds = RandomEventManager.clampInterval(payload.intervalSeconds());
				state.ticksLeft = state.intervalSeconds * 20;
				IkunzafMod.LOGGER.debug("玩家 {} 设置随机事件: enabled={}, interval={}s",
						context.player().getName().getString(), state.enabled, state.intervalSeconds);
				// 回推实际生效的配置（含 clamp 后的值），保证 GUI 显示与服务端一致
				sendSync(context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(TriggerNowPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> RandomEventManager.trigger(context.player()));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestSyncPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> sendSync(context.player()));
		});
	}
}
