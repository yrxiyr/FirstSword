package com.firstsword;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 词条（核心）。
 *
 * 附属模组实现这个接口、在初始化时调用 {@link SwordCores#register}，
 * 主模组的初锋就会按词条改写技能数值。没有装任何词条时数值和原版完全一致。
 */
public interface SwordCore
{
    /** 词条 id，也是存进剑上的标识 */
    ResourceLocation id();

    /** 物品提示里显示的名字 */
    Component displayName();

    /** 物品提示里的一行效果说明 */
    default Component description()
    {
        return Component.empty();
    }

    /**
     * 词条被打到剑上时调用一次。
     * 需要改物品本身（比如攻击力这类走属性组件的）在这里做，只加不改数据组件的可以不管。
     */
    default void onAttach(ItemStack stack)
    {
    }

    /**
     * 瞬闪斩的伤害。路径和落点都走这条链，所以 +6 是两边同时 +6。
     *
     * @param base 当前值
     * @return 改写后的值
     */
    default float blinkDamage(float base)
    {
        return base;
    }

    /** 流星群的散布半径（格） */
    default double meteorScatterRadius(double base)
    {
        return base;
    }

    /** 小流星数量 */
    default int smallMeteorCount(int base)
    {
        return base;
    }

    /**
     * 中型流星数量。返回 0 就不生成。
     * 中型流星排在小流星之后、大流星之前落下。
     */
    default int mediumMeteorCount(RandomSource random)
    {
        return 0;
    }

    /** 法阵半径（格） */
    default double meteorFieldRadius(double base)
    {
        return base;
    }

    /**
     * 流星真正打中一只生物之后调用（每只每次只调一次）。
     *
     * @param level  流星所在的维度
     * @param owner  施法的玩家，伤害记在他头上；可能已下线
     * @param target 被命中的生物
     */
    default void onMeteorHit(ServerLevel level, Player owner, LivingEntity target)
    {
    }

    /**
     * 瞬闪斩最多能存几次。
     *
     * 冷却时长不变，只是把「一次机会」变成「几次机会」，
     * 每补一层仍然要等一个完整的冷却。
     *
     * @param base 当前值（默认 1）
     * @return 改写后的上限，至少 1
     */
    default int blinkCharges(int base)
    {
        return base;
    }

    /**
     * 瞬闪斩真正打中一只生物之后调用（路径和落点都算，同一次冲刺每只只调一次）。
     *
     * @param level  冲刺所在维度
     * @param owner  施法的玩家
     * @param target 被切中的生物
     */
    default void onBlinkHit(ServerLevel level, Player owner, LivingEntity target)
    {
    }

    /**
     * 瞬闪斩成功放出的瞬间调用一次（冲刺真正开始，还没结算伤害）。
     * 组合技也是先走瞬闪斩，所以这条会在两边都触发。
     *
     * @param player 施法的玩家
     * @param sword  手上的初锋
     */
    default void onBlinkCast(ServerPlayer player, ItemStack sword)
    {
    }

    /**
     * 大流星即将生成时调用一次，坐标是它的落点（不是出生点）。
     * 用来做「落地前先展开一个场」这类效果。
     */
    default void onBigMeteorIncoming(ServerLevel level, double x, double y, double z, Player owner)
    {
    }

    /**
     * 流星打中某只生物前的伤害修正。
     *
     * @param base   当前值（已经按体型算好的基础伤害）
     * @param target 即将被命中的生物
     * @return 改写后的伤害
     */
    default float meteorDamage(float base, LivingEntity target)
    {
        return base;
    }

    /**
     * 点按流星键（完全没有蓄力）时调用。
     *
     * 返回 true 表示这次点按被这个词条接管了：不召唤流星群，也不进冷却，
     * 后续词条不再收到通知。返回 false 就当作一次没按到，什么都不发生。
     *
     * @param level  玩家所在维度
     * @param player 点按的玩家
     * @param sword  手上的初锋
     * @return 是否接住了这次点按
     */
    default boolean onMeteorTap(ServerLevel level, Player player, ItemStack sword)
    {
        return false;
    }

    /**
     * 彩虹雷的落点半径（格）。默认 30。
     */
    default float lightningRadius(float base)
    {
        return base;
    }

    /**
     * 彩虹雷是否无视地形：返回 true 时不再贴地表生成，而是直接落在玩家当前高度，
     * 下界、洞穴、室内都能正常落雷。
     */
    default boolean lightningThroughBlocks()
    {
        return false;
    }

    /**
     * 彩虹雷每道的伤害。
     *
     * @param base 当前值（已经过前面几个词条的处理）
     * @return 改写后的值
     */
    default float lightningDamage(float base)
    {
        return base;
    }

    /**
     * 流星群冷却（tick）。
     *
     * @param base 当前值（已经过前面几个词条的处理）
     * @return 改写后的值
     */
    default int meteorCooldown(int base)
    {
        return base;
    }
}
