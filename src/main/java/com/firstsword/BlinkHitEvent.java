package com.firstsword;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.Event;

/**
 * 瞬闪斩真正打中一只生物时，发到游戏总线上的事件。
 *
 * 和 {@link SwordCore#onBlinkHit} 的区别：那个只对「剑上已经装着这个词条」的玩家触发，
 * 这个事件是谁都能听的，所以附属模组可以用它来做「命中累计」类的解锁条件，
 * 不需要玩家先把词条装上。
 */
public class BlinkHitEvent extends Event
{
    private final ServerLevel level;
    private final ServerPlayer player;
    private final LivingEntity target;

    public BlinkHitEvent(ServerLevel level, ServerPlayer player, LivingEntity target)
    {
        this.level = level;
        this.player = player;
        this.target = target;
    }

    public ServerLevel level()
    {
        return this.level;
    }

    public ServerPlayer player()
    {
        return this.player;
    }

    public LivingEntity target()
    {
        return this.target;
    }
}
