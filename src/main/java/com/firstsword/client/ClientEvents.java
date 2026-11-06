package com.firstsword.client;

import com.firstsword.FirstSword;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = FirstSword.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents
{
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(FirstSword.RAINBOW_LIGHTNING.get(), RainbowLightningRenderer::new);
        event.registerEntityRenderer(FirstSword.RAINBOW_METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(FirstSword.METEOR_PORTAL.get(), MeteorPortalRenderer::new);
        event.registerEntityRenderer(FirstSword.BLINK_TRAIL.get(), BlinkTrailRenderer::new);
        event.registerEntityRenderer(FirstSword.SLASH_ARC.get(), SlashArcRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(KeyBindings.METEOR);
        event.register(KeyBindings.BLINK);
    }
}
