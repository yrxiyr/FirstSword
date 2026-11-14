package com.firstsword;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = FirstSword.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetwork
{
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        event.registrar("1").playToServer(
                MeteorCastPayload.TYPE,
                MeteorCastPayload.STREAM_CODEC,
                ModNetwork::handleMeteorCast);

        event.registrar("1").playToServer(
                BlinkPayload.TYPE,
                BlinkPayload.STREAM_CODEC,
                ModNetwork::handleBlink);
    }

    private static void handleBlink(BlinkPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            if (!(context.player() instanceof ServerPlayer player))
            {
                return;
            }

            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();

            ItemStack sword = main.getItem() instanceof FirstSwordItem ? main
                    : off.getItem() instanceof FirstSwordItem ? off
                    : ItemStack.EMPTY;

            if (sword.isEmpty())
            {
                return;
            }

            int max = FirstSwordItem.blinkCharges(sword);
            int remaining = BlinkStrike.getRemainingCooldown(player, player.serverLevel().getGameTime(), max);

            if (remaining > 0)
            {
                // 冷却不走物品冷却，界面上没有提示，用动作栏补一个
                player.displayClientMessage(Component.translatable(
                        "message.firstsword.blink_cooldown",
                        Mth.ceil(remaining / 20.0F)), true);
                return;
            }

            if (BlinkStrike.cast(player.serverLevel(), player, sword) && max > 1)
            {
                // 多层充能时补一个剩余次数提示
                player.displayClientMessage(Component.translatable(
                        "message.firstsword.blink_charges",
                        BlinkStrike.getCharges(player, max, player.serverLevel().getGameTime()),
                        max), true);
            }
        });
    }

    private static void handleMeteorCast(MeteorCastPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            if (!(context.player() instanceof ServerPlayer player))
            {
                return;
            }

            // 服务端自己校验：手上（主/副）得拿着初锋，并且不在冷却中
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();

            ItemStack sword = main.getItem() instanceof FirstSwordItem ? main
                    : off.getItem() instanceof FirstSwordItem ? off
                    : ItemStack.EMPTY;

            if (sword.isEmpty())
            {
                return;
            }

            if (payload.power() < 0.0F)
            {
                // 点按：没有一点蓄力。交给词条接管（破袭核心会在这里给 buff），
                // 没有词条接住就当作没按到，不召唤流星也不进冷却
                for (SwordCore core : SwordCores.on(sword))
                {
                    if (core.onMeteorTap(player.serverLevel(), player, sword))
                    {
                        break;
                    }
                }

                return;
            }

            if (player.getCooldowns().isOnCooldown(sword.getItem()))
            {
                return;
            }

            float power = Mth.clamp(payload.power(), 0.0F, 1.0F);
            RainbowMeteor.cast(player.serverLevel(), player, power, sword);
            player.getCooldowns().addCooldown(sword.getItem(), FirstSwordItem.meteorCooldown(sword));
        });
    }
}
