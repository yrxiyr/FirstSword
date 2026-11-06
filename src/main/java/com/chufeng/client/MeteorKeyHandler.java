package com.chufeng.client;

import com.chufeng.FirstSwordItem;
import com.chufeng.MeteorCastPayload;
import com.chufeng.ChuFeng;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 长按 J 蓄力，松手把 power 发给服务端。
 *
 * 客户端只负责计时和发请求，真正的生成与伤害都在服务端（{@code ModNetwork}）完成。
 */
@EventBusSubscriber(modid = ChuFeng.MODID, value = Dist.CLIENT)
public class MeteorKeyHandler
{
    /** 至少按住这么久才算蓄力（tick） */
    private static final int MIN_CHARGE = 8;
    /** 蓄力上限（tick），3 秒，满蓄 32 颗小流星 */
    private static final int MAX_CHARGE = 60;

    private static int charge;

    /**
     * 0 或 1，供蓄力反色读取。
     *
     * 反相用 {@code out = src * (1 - dst)} 实现，src 取中间值会得到发灰的"半反相"，
     * 不好看，所以这里做成开关：蓄力到 35% 才整屏翻转，更有"时停"的感觉。
     */
    public static float getChargeProgress()
    {
        return charge >= MAX_CHARGE * 0.35F ? 1.0F : 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null)
        {
            charge = 0;
            return;
        }

        boolean holdingSword = minecraft.player.getMainHandItem().getItem() instanceof FirstSwordItem
                || minecraft.player.getOffhandItem().getItem() instanceof FirstSwordItem;

        if (KeyBindings.METEOR.isDown() && holdingSword)
        {
            if (charge < MAX_CHARGE)
            {
                charge++;
            }

            return;
        }

        if (charge > 0)
        {
            if (charge >= MIN_CHARGE)
            {
                float power = Mth.clamp(
                        (charge - MIN_CHARGE) / (float) (MAX_CHARGE - MIN_CHARGE), 0.0F, 1.0F);
                PacketDistributor.sendToServer(new MeteorCastPayload(power));
            }

            charge = 0;
        }
    }
}
