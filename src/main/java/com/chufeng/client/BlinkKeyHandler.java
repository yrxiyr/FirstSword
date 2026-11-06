package com.chufeng.client;

import com.chufeng.BlinkPayload;
import com.chufeng.FirstSwordItem;
import com.chufeng.ChuFeng;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 按下 K 立刻发一次瞬闪请求。
 *
 * 用 isDown() 的上升沿而不是 consumeClick()：和流星蓄力用同一套状态，
 * 行为可预测，也不会因为某个界面吃掉 clickCount 而漏触发。
 */
@EventBusSubscriber(modid = ChuFeng.MODID, value = Dist.CLIENT)
public class BlinkKeyHandler
{
    private static boolean wasDown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        boolean down = KeyBindings.BLINK.isDown();
        boolean pressed = down && !wasDown;
        wasDown = down;

        if (!pressed)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null)
        {
            return;
        }

        boolean holdingSword = minecraft.player.getMainHandItem().getItem() instanceof FirstSwordItem
                || minecraft.player.getOffhandItem().getItem() instanceof FirstSwordItem;

        if (holdingSword)
        {
            PacketDistributor.sendToServer(new BlinkPayload());
        }
        else
        {
            // 没拿剑时给个反馈，不然按下去像没反应
            minecraft.player.displayClientMessage(
                    Component.translatable("message.chufeng.need_sword"), true);
        }
    }
}
