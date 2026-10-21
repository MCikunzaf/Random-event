package com.ikunzaf.random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 全部随机事件池（130+）。所有事件只在服务端执行。
 */
public final class RandomEvents {
	private static final List<RandomEvent> EVENTS = new ArrayList<>();

	private RandomEvents() {
	}

	public static List<RandomEvent> all() {
		return EVENTS;
	}

	// ---------- helpers ----------

	private static void ev(String name, java.util.function.Consumer<ServerPlayer> action) {
		EVENTS.add(RandomEvent.of(name, action));
	}

	private static void effect(ServerPlayer p, Holder<net.minecraft.world.effect.MobEffect> effect, int seconds, int amplifier) {
		p.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier, false, true));
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		p.getInventory().add(stack);
	}

	private static void give(ServerPlayer p, ItemStack stack, int count) {
		while (count > 0) {
			ItemStack s = stack.copyWithCount(Math.min(count, stack.getMaxStackSize()));
			p.getInventory().add(s);
			count -= s.getCount();
		}
	}

	private static ItemStack stack(net.minecraft.world.level.ItemLike item, int count) {
		return new ItemStack(item, count);
	}

	private static void enchant(ServerPlayer p, ItemStack stack, ResourceKey<Enchantment> key, int level) {
		stack.enchant(p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
	}

	private static void launch(ServerPlayer p, double dy) {
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x, dy, v.z);
		p.resetFallDistance();
		p.hurtMarked = true;
	}

	private static void spawnAround(ServerPlayer p, EntityType<? extends Entity> type, int n) {
		ServerLevel level = p.level();
		RandomSource r = p.getRandom();
		for (int i = 0; i < n; i++) {
			Entity e = type.create(level, EntitySpawnReason.EVENT);
			if (e == null) {
				continue;
			}
			double x = p.getX() + (r.nextDouble() - 0.5) * 8.0;
			double z = p.getZ() + (r.nextDouble() - 0.5) * 8.0;
			e.snapTo(x, p.getY(), z, r.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(e);
		}
	}

	private static void placeAbove(ServerPlayer p, BlockState state) {
		ServerLevel level = p.level();
		BlockPos pos = p.blockPosition().above(2);
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, 3);
		}
	}

	// ---------- 注册全部事件 ----------

	public static void init() {
		if (!EVENTS.isEmpty()) {
			return;
		}

		// ===== 移动 / 速度类 =====
		ev("速度 255（10 秒）—— 光速冲鸭", p -> effect(p, MobEffects.SPEED, 10, 255));
		ev("速度 III（30 秒）", p -> effect(p, MobEffects.SPEED, 30, 2));
		ev("缓慢 255（5 秒）—— 龟速罚站", p -> effect(p, MobEffects.SLOWNESS, 5, 255));
		ev("缓慢 II（15 秒）", p -> effect(p, MobEffects.SLOWNESS, 15, 1));
		ev("跳跃提升 255（10 秒）—— 一跳十格高", p -> effect(p, MobEffects.JUMP_BOOST, 10, 255));
		ev("跳跃提升 II（60 秒）", p -> effect(p, MobEffects.JUMP_BOOST, 60, 1));
		ev("月球漫步（缓降 + 跳跃 15 秒）", p -> {
			effect(p, MobEffects.SLOW_FALLING, 15, 0);
			effect(p, MobEffects.JUMP_BOOST, 15, 3);
		});
		ev("升天 50 格 + 缓降（飞天体验）", p -> {
			p.teleportTo(p.getX(), p.getY() + 50.0, p.getZ());
			p.resetFallDistance();
			effect(p, MobEffects.SLOW_FALLING, 15, 0);
		});
		ev("飞天送你一桶水（记得摆下来！）", p -> {
			p.teleportTo(p.getX(), p.getY() + 40.0, p.getZ());
			p.resetFallDistance();
			effect(p, MobEffects.SLOW_FALLING, 20, 0);
			give(p, stack(Items.WATER_BUCKET, 1));
		});
		ev("垂直起飞（弹射 5 格）", p -> launch(p, 1.5));
		ev("超级弹跳（弹射 12 格）", p -> launch(p, 2.5));
		ev("火箭升空（弹射 25 格）", p -> launch(p, 4.0));
		ev("向后击退 255（倒飞）", p -> launch(p, -1.0));
		ev("疾跑马拉松（速度 II + 跳跃 II，90 秒）", p -> {
			effect(p, MobEffects.SPEED, 90, 1);
			effect(p, MobEffects.JUMP_BOOST, 90, 1);
		});

		// ===== 战斗类 =====
		ev("力量 255（10 秒）—— 一拳超人", p -> effect(p, MobEffects.STRENGTH, 10, 255));
		ev("力量 II（60 秒）", p -> effect(p, MobEffects.STRENGTH, 60, 1));
		ev("抗性 255（10 秒）—— 无敌瞬间", p -> effect(p, MobEffects.RESISTANCE, 10, 255));
		ev("抗性 II（60 秒）", p -> effect(p, MobEffects.RESISTANCE, 60, 1));
		ev("急迫 255（10 秒）—— 秒挖方块", p -> effect(p, MobEffects.HASTE, 10, 255));
		ev("急迫 II（120 秒）", p -> effect(p, MobEffects.HASTE, 120, 1));
		ev("挖掘疲劳 III（20 秒）—— 挖不动了", p -> effect(p, MobEffects.MINING_FATIGUE, 20, 2));
		ev("虚弱 II（30 秒）", p -> effect(p, MobEffects.WEAKNESS, 30, 1));
		ev("再生 II（30 秒）—— 回血机器", p -> effect(p, MobEffects.REGENERATION, 30, 1));
		ev("不死图腾体验（抗性 + 吸收 + 生命提升 60 秒）", p -> {
			effect(p, MobEffects.RESISTANCE, 60, 1);
			effect(p, MobEffects.ABSORPTION, 60, 3);
			effect(p, MobEffects.HEALTH_BOOST, 60, 1);
		});
		ev("吸收 IV（60 秒）", p -> effect(p, MobEffects.ABSORPTION, 60, 3));
		ev("生命提升 255（10 秒）—— 半血秒回", p -> effect(p, MobEffects.HEALTH_BOOST, 10, 255));
		ev("回满血 + 饱食度（天使关怀）", p -> {
			p.setHealth(p.getMaxHealth());
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(20.0F);
		});
		ev("血量剩半（心惊肉跳）", p -> p.setHealth(Math.max(1.0F, p.getMaxHealth() / 2.0F)));
		ev("一点血挑战（+ 抗性保命 10 秒）", p -> {
			p.setHealth(1.0F);
			effect(p, MobEffects.RESISTANCE, 10, 4);
		});
		ev("瞬间治疗 255", p -> effect(p, MobEffects.INSTANT_HEALTH, 1, 255));
		ev("瞬间伤害（疼！）", p -> effect(p, MobEffects.INSTANT_DAMAGE, 1, 0));
		ev("主手武器变神装（锋利 X）", p -> {
			ItemStack hand = p.getMainHandItem();
			if (!hand.isEmpty()) {
				enchant(p, hand, Enchantments.SHARPNESS, 10);
			} else {
				give(p, stack(Items.WOODEN_SWORD, 1));
			}
		});
		ev("赐予击退之棍（击退 X 木棍）", p -> {
			ItemStack stick = stack(Items.STICK, 1);
			enchant(p, stick, Enchantments.KNOCKBACK, 10);
			give(p, stick);
		});

		// ===== 负面整蛊类 =====
		ev("反胃 255（5 秒）—— 天旋地转", p -> effect(p, MobEffects.NAUSEA, 5, 255));
		ev("失明 255（10 秒）", p -> effect(p, MobEffects.BLINDNESS, 10, 255));
		ev("黑暗降临（10 秒）", p -> effect(p, MobEffects.DARKNESS, 10, 0));
		ev("中毒 II（10 秒）", p -> effect(p, MobEffects.POISON, 10, 1));
		ev("凋零 II（5 秒）—— 危险！", p -> effect(p, MobEffects.WITHER, 5, 1));
		ev("饥饿 255（10 秒）", p -> effect(p, MobEffects.HUNGER, 10, 255));
		ev("漂浮 255（5 秒）—— 身体不受控制", p -> effect(p, MobEffects.LEVITATION, 5, 255));
		ev("漂浮 I（10 秒）", p -> effect(p, MobEffects.LEVITATION, 10, 0));
		ev("着火 3 秒（火中作乐）", p -> p.setRemainingFireTicks(60));
		ev("神奇灭火器（清除火焰）", p -> p.clearFire());
		ev("负面大礼包（失明 + 反胃 + 虚弱 + 缓慢 5 秒）", p -> {
			effect(p, MobEffects.BLINDNESS, 5, 0);
			effect(p, MobEffects.NAUSEA, 5, 0);
			effect(p, MobEffects.WEAKNESS, 5, 0);
			effect(p, MobEffects.SLOWNESS, 5, 1);
		});
		ev("正面大礼包（速度 + 急迫 + 力量 + 跳跃 + 夜视 + 抗性 60 秒）", p -> {
			effect(p, MobEffects.SPEED, 60, 1);
			effect(p, MobEffects.HASTE, 60, 1);
			effect(p, MobEffects.STRENGTH, 60, 1);
			effect(p, MobEffects.JUMP_BOOST, 60, 1);
			effect(p, MobEffects.NIGHT_VISION, 60, 0);
			effect(p, MobEffects.RESISTANCE, 60, 0);
		});
		ev("清除所有负面效果", p -> {
			for (MobEffectInstance inst : p.getActiveEffects()) {
				if (!inst.getEffect().value().isBeneficial()) {
					p.removeEffect(inst.getEffect());
				}
			}
		});

		// ===== buff 辅助类 =====
		ev("夜视（5 分钟）", p -> effect(p, MobEffects.NIGHT_VISION, 300, 0));
		ev("隐身（60 秒）", p -> effect(p, MobEffects.INVISIBILITY, 60, 0));
		ev("发光（60 秒）—— 行走的灯塔", p -> effect(p, MobEffects.GLOWING, 60, 0));
		ev("缓降（30 秒）", p -> effect(p, MobEffects.SLOW_FALLING, 30, 0));
		ev("水下呼吸（120 秒）", p -> effect(p, MobEffects.WATER_BREATHING, 120, 0));
		ev("防火（120 秒）", p -> effect(p, MobEffects.FIRE_RESISTANCE, 120, 0));
		ev("海豚恩惠（60 秒）", p -> effect(p, MobEffects.DOLPHINS_GRACE, 60, 0));
		ev("潮涌能量（60 秒）—— 海中神力", p -> effect(p, MobEffects.CONDUIT_POWER, 60, 0));
		ev("幸运（60 秒）", p -> effect(p, MobEffects.LUCK, 60, 1));
		ev("不幸（60 秒）", p -> effect(p, MobEffects.UNLUCK, 60, 1));
		ev("饱和（瞬间吃饱）", p -> effect(p, MobEffects.SATURATION, 1, 255));

		// ===== 富豪类 =====
		ev("一组钻石（暴富！）", p -> give(p, stack(Items.DIAMOND, 1), 64));
		ev("一组下界合金锭（天降横财）", p -> give(p, stack(Items.NETHERITE_INGOT, 1), 64));
		ev("一颗绿宝石（小富即安）", p -> give(p, stack(Items.EMERALD, 1)));
		ev("单个腐肉（就这？）", p -> give(p, stack(Items.ROTTEN_FLESH, 1)));
		ev("一组腐肉（垃圾山）", p -> give(p, stack(Items.ROTTEN_FLESH, 1), 64));
		ev("附魔金苹果（神仙待遇）", p -> give(p, stack(Items.ENCHANTED_GOLDEN_APPLE, 1)));
		ev("不死图腾（保命神器）", p -> give(p, stack(Items.TOTEM_OF_UNDYING, 1)));
		ev("末影珍珠 ×16", p -> give(p, stack(Items.ENDER_PEARL, 1), 16));
		ev("鞘翅（起飞！）", p -> give(p, stack(Items.ELYTRA, 1)));
		ev("一组烟花火箭（配鞘翅食用）", p -> give(p, stack(Items.FIREWORK_ROCKET, 1), 64));
		ev("一组经验瓶", p -> give(p, stack(Items.EXPERIENCE_BOTTLE, 1), 64));
		ev("一组蛋糕（派对时间）", p -> give(p, stack(Items.CAKE, 1), 64));
		ev("一组金胡萝卜", p -> give(p, stack(Items.GOLDEN_CARROT, 1), 64));
		ev("一组甜浆果", p -> give(p, stack(Items.SWEET_BERRIES, 1), 64));
		ev("一组海绵（吸水专家）", p -> give(p, stack(Items.SPONGE, 1), 64));
		ev("一组骨头（狗子狂喜）", p -> give(p, stack(Items.BONE, 1), 64));
		ev("一组末影之眼", p -> give(p, stack(Items.ENDER_EYE, 1), 64));
		ev("龙蛋（收藏品！）", p -> give(p, stack(Items.DRAGON_EGG, 1)));
		ev("一组纸（文盲救星）", p -> give(p, stack(Items.PAPER, 1), 64));
		ev("一组书架（装修大师）", p -> give(p, stack(Items.BOOKSHELF, 1), 64));
		ev("一个音符盒（大喇叭）", p -> give(p, stack(Items.NOTE_BLOCK, 1)));
		ev("一个潜影盒（移动仓库）", p -> give(p, stack(Items.SHULKER_BOX, 1)));
		ev("一组仙人掌（夹谁谁知道）", p -> give(p, stack(Items.CACTUS, 1), 64));
		ev("命名牌 ×5", p -> give(p, stack(Items.NAME_TAG, 1), 5));
		ev("一组 TNT（拆迁办）", p -> give(p, stack(Items.TNT, 1), 64));
		ev("打火石（纵火犯工具）", p -> give(p, stack(Items.FLINT_AND_STEEL, 1)));
		ev("一个重生锚（下界的新家）", p -> give(p, stack(Items.RESPAWN_ANCHOR, 1)));
		ev("三叉戟（激流 III）", p -> {
			ItemStack trident = stack(Items.TRIDENT, 1);
			enchant(p, trident, Enchantments.RIPTIDE, 3);
			give(p, trident);
		});
		ev("弓 + 一组箭", p -> {
			give(p, stack(Items.BOW, 1));
			give(p, stack(Items.ARROW, 1), 64);
		});
		ev("弩 + 多重射击烟花", p -> {
			ItemStack crossbow = stack(Items.CROSSBOW, 1);
			enchant(p, crossbow, Enchantments.MULTISHOT, 1);
			give(p, crossbow);
			give(p, stack(Items.FIREWORK_ROCKET, 1), 16);
		});
		ev("一组刷怪蛋（生物派对）", p -> {
			give(p, stack(Items.CREEPER_SPAWN_EGG, 1), 8);
			give(p, stack(Items.ZOMBIE_SPAWN_EGG, 1), 8);
		});

		// ===== 装备类 =====
		ev("神剑（下界合金 + 锋利 255）", p -> {
			ItemStack sword = stack(Items.NETHERITE_SWORD, 1);
			enchant(p, sword, Enchantments.SHARPNESS, 255);
			give(p, sword);
		});
		ev("下界合金套装（四件套）", p -> {
			give(p, stack(Items.NETHERITE_HELMET, 1));
			give(p, stack(Items.NETHERITE_CHESTPLATE, 1));
			give(p, stack(Items.NETHERITE_LEGGINGS, 1));
			give(p, stack(Items.NETHERITE_BOOTS, 1));
		});
		ev("全套皮革装备（平民套装）", p -> {
			give(p, stack(Items.LEATHER_HELMET, 1));
			give(p, stack(Items.LEATHER_CHESTPLATE, 1));
			give(p, stack(Items.LEATHER_LEGGINGS, 1));
			give(p, stack(Items.LEATHER_BOOTS, 1));
		});
		ev("荆棘 255 头盔（谁都别碰我）", p -> {
			ItemStack helmet = stack(Items.DIAMOND_HELMET, 1);
			enchant(p, helmet, Enchantments.THORNS, 255);
			give(p, helmet);
		});

		// ===== 液体 / 方块整蛊类 =====
		ev("一桶水（解渴神器）", p -> give(p, stack(Items.WATER_BUCKET, 1)));
		ev("一桶岩浆（小心脚下！）", p -> give(p, stack(Items.LAVA_BUCKET, 1)));
		ev("脚下变冰（溜冰场）", p -> p.level()
				.setBlock(p.blockPosition().below(), Blocks.ICE.defaultBlockState(), 3));
		ev("脚下变岩浆块（烫脚舞）", p -> p.level()
				.setBlock(p.blockPosition().below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 3));
		ev("头顶长出沙子（活埋预警）", p -> placeAbove(p, Blocks.SAND.defaultBlockState()));
		ev("头顶长出仙人掌", p -> placeAbove(p, Blocks.CACTUS.defaultBlockState()));
		ev("脚下来一发 TNT（3 秒后炸）", p -> {
			ServerLevel level = p.level();
			PrimedTnt tnt = EntityTypes.TNT.create(level, EntitySpawnReason.EVENT);
			if (tnt != null) {
				tnt.snapTo(p.getX(), p.getY(), p.getZ(), 0.0F, 0.0F);
				tnt.setFuse(60);
				level.addFreshEntity(tnt);
			}
		});
		ev("天降流沙（头顶 10 格掉沙子）", p -> FallingBlockEntity.fall(p.level(),
				new BlockPos((int) p.getX(), (int) p.getY() + 10, (int) p.getZ()),
				Blocks.SAND.defaultBlockState()));
		ev("雷劈！（天打雷劈）", p -> {
			ServerLevel level = p.level();
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
			if (bolt != null) {
				bolt.snapTo(p.getX(), p.getY(), p.getZ());
				level.addFreshEntity(bolt);
			}
		});

		// ===== 生物类 =====
		ev("苦力怕突袭 ×5（跑！）", p -> spawnAround(p, EntityTypes.CREEPER, 5));
		ev("僵尸围城 ×8", p -> spawnAround(p, EntityTypes.ZOMBIE, 8));
		ev("蜜蜂来袭 ×10（蜇你没商量）", p -> spawnAround(p, EntityTypes.BEE, 10));
		ev("天降鸡雨 ×20（卡丁鸡风暴）", p -> {
			ServerLevel level = p.level();
			for (int i = 0; i < 20; i++) {
				Entity chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.EVENT);
				if (chicken == null) {
					continue;
				}
				chicken.snapTo(p.getX(), p.getY() + 15.0, p.getZ(), p.getRandom().nextFloat() * 360.0F, 0.0F);
				level.addFreshEntity(chicken);
			}
		});
		ev("忠诚护卫 ×5（狼群）", p -> spawnAround(p, EntityTypes.WOLF, 5));
		ev("女巫来了（药水警告）", p -> spawnAround(p, EntityTypes.WITCH, 1));
		ev("铁傀儡空降（惊不惊喜）", p -> spawnAround(p, EntityTypes.IRON_GOLEM, 1));
		ev("骷髅射手小队 ×4", p -> spawnAround(p, EntityTypes.SKELETON, 4));
		ev("蜘蛛侠（爬行者 ×3）", p -> spawnAround(p, EntityTypes.SPIDER, 3));
		ev("末影人注视着你", p -> spawnAround(p, EntityTypes.ENDERMAN, 1));
		ev("恶魂一声吼", p -> spawnAround(p, EntityTypes.GHAST, 1));
		ev("幻翼袭击 ×3（熬夜惩罚）", p -> spawnAround(p, EntityTypes.PHANTOM, 3));
		ev("悦灵助手 ×2（帮你捡垃圾）", p -> spawnAround(p, EntityTypes.ALLAY, 2));
		ev("羊驼吐口水 ×5", p -> spawnAround(p, EntityTypes.LLAMA, 5));
		ev("友善的村民 ×3（可以交易了）", p -> spawnAround(p, EntityTypes.VILLAGER, 3));
		ev("骑猪出行（豪猪座驾）", p -> {
			ServerLevel level = p.level();
			Entity pig = EntityTypes.PIG.create(level, EntitySpawnReason.EVENT);
			if (pig != null) {
				pig.snapTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0F);
				level.addFreshEntity(pig);
				p.startRiding(pig);
			}
		});
		ev("骑马奔驰（白马王子）", p -> {
			ServerLevel level = p.level();
			Entity horse = EntityTypes.HORSE.create(level, EntitySpawnReason.EVENT);
			if (horse != null) {
				horse.snapTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0F);
				level.addFreshEntity(horse);
				p.startRiding(horse);
			}
		});

		// ===== 传送类 =====
		ev("随机传送 ±1000 格（说走就走）", p -> {
			RandomSource r = p.getRandom();
			double x = p.getX() + (r.nextDouble() - 0.5) * 2000.0;
			double z = p.getZ() + (r.nextDouble() - 0.5) * 2000.0;
			p.teleportTo(x, p.getY(), z);
		});
		ev("随机传送 ±50 格（小范围闪现）", p -> {
			RandomSource r = p.getRandom();
			double x = p.getX() + (r.nextDouble() - 0.5) * 100.0;
			double z = p.getZ() + (r.nextDouble() - 0.5) * 100.0;
			p.teleportTo(x, p.getY(), z);
		});
		ev("传送到世界出生点", p -> {
			BlockPos spawn = p.level().getRespawnData().pos();
			p.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
		});
		ev("原地升天 3 格（小小惊吓）", p -> p.teleportTo(p.getX(), p.getY() + 3.0, p.getZ()));
		ev("交换 XYZ（大挪移）", p -> p.teleportTo(p.getZ(), p.getY(), p.getX()));
		ev("设置重生点为当前位置（安家落户）", p -> p.setRespawnPosition(
				new net.minecraft.server.level.ServerPlayer.RespawnConfig(
						net.minecraft.world.level.storage.LevelData.RespawnData.of(
								p.level().dimension(), p.blockPosition(), p.getYRot(), 0.0F),
						true),
				true));

		// ===== 经验 / 状态类 =====
		ev("经验 +30 级（升级狂喜）", p -> p.giveExperienceLevels(30));
		ev("经验 +500 点", p -> p.giveExperiencePoints(500));
		ev("食物清零（饿肚子）", p -> p.getFoodData().setFoodLevel(0));
		ev("掉落主手物品（手滑了）", p -> {
			ItemStack hand = p.getMainHandItem();
			if (!hand.isEmpty()) {
				p.drop(hand, false);
				p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		});
		ev("随机散财（丢 3 组背包物品）", p -> {
			var inv = p.getInventory();
			for (int i = 0; i < 3; i++) {
				int slot = p.getRandom().nextInt(inv.getContainerSize());
				ItemStack s = inv.getItem(slot);
				if (!s.isEmpty()) {
					p.drop(s, true);
					inv.setItem(slot, ItemStack.EMPTY);
				}
			}
		});

		// ===== 环境 / 时间类 =====
		ev("脚下变金块（意外之财）", p -> p.level()
				.setBlock(p.blockPosition().below(), Blocks.GOLD_BLOCK.defaultBlockState(), 3));
		ev("TNT 环绕（快跑！）", p -> {
			ServerLevel level = p.level();
			for (int i = 0; i < 4; i++) {
				PrimedTnt tnt = EntityTypes.TNT.create(level, EntitySpawnReason.EVENT);
				if (tnt == null) {
					continue;
				}
				double angle = Math.PI * 2 * i / 4.0;
				tnt.snapTo(p.getX() + Math.cos(angle) * 4.0, p.getY(), p.getZ() + Math.sin(angle) * 4.0);
				tnt.setFuse(100);
				level.addFreshEntity(tnt);
			}
		});

		// ===== 玩笑类 =====
		ev("什么都没发生（真的吗？）", p -> {
		});
		ev("恭喜获得一颗钻石！", p -> give(p, stack(Items.DIAMOND, 1)));
	}
}
