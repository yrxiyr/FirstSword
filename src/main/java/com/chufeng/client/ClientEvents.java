package com.chufeng.client;

import com.chufeng.ChuFeng;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = ChuFeng.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents
{
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ChuFeng.RAINBOW_LIGHTNING.get(), RainbowLightningRenderer::new);
        event.registerEntityRenderer(ChuFeng.RAINBOW_METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ChuFeng.METEOR_PORTAL.get(), MeteorPortalRenderer::new);
        event.registerEntityRenderer(ChuFeng.BLINK_TRAIL.get(), BlinkTrailRenderer::new);
        event.registerEntityRenderer(ChuFeng.SLASH_ARC.get(), SlashArcRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(KeyBindings.METEOR);
        event.register(KeyBindings.BLINK);
    }
}
