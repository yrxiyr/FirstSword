package com.chufeng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * 初锋死亡不掉落。
 *
 * 做法不是「拦掉落」——原版 dropAll() 在生成掉落物时就已经把东西从背包里清掉了，
 * 拦下来等于把它销毁。所以要在死亡事件里先把剑取出来暂存，重生后再塞回去。
 */
@EventBusSubscriber(modid = ChuFeng.MODID)
public final class SoulboundSword
{
    /** 死亡时暂存的剑，按玩家 UUID 记 */
    private static final Map<UUID, List<ItemStack>> KEPT = new HashMap<>();

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        Inventory inventory = player.getInventory();
        List<ItemStack> kept = new ArrayList<>();

        // 0~35 主背包、36~39 盔甲、40 副手
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);

            if (stack.getItem() instanceof FirstSwordItem)
            {
                kept.add(stack.copy());
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }

        if (!kept.isEmpty())
        {
            KEPT.put(player.getUUID(), kept);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.Clone event)
    {
        if (!event.isWasDeath())
        {
            return;
        }

        List<ItemStack> kept = KEPT.remove(event.getOriginal().getUUID());

        if (kept == null)
        {
            return;
        }

        for (ItemStack stack : kept)
        {
            if (!event.getEntity().getInventory().add(stack))
            {
                event.getEntity().drop(stack, false);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        KEPT.clear();
    }

    private SoulboundSword()
    {
    }
}
