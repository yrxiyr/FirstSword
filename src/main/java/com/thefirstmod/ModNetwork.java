package com.thefirstmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TheFirstMod.MODID, bus = EventBusSubscriber.Bus.MOD)
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

            int remaining = BlinkStrike.getRemainingCooldown(player, player.serverLevel().getGameTime());

            if (remaining > 0)
            {
                // 冷却不走物品冷却，界面上没有提示，用动作栏补一个
                player.displayClientMessage(Component.translatable(
                        "message.thefirstmod.blink_cooldown",
                        Mth.ceil(remaining / 20.0F)), true);
                return;
            }

            BlinkStrike.cast(player.serverLevel(), player);
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

            if (sword.isEmpty() || player.getCooldowns().isOnCooldown(sword.getItem()))
            {
                return;
            }

            float power = Mth.clamp(payload.power(), 0.0F, 1.0F);
            RainbowMeteor.cast(player.serverLevel(), player, power);
            player.getCooldowns().addCooldown(sword.getItem(), FirstSwordItem.METEOR_COOLDOWN);
        });
    }
}
