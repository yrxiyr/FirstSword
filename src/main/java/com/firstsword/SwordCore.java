package com.firstsword;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
