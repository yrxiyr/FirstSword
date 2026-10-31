package com.thefirstmod.client;

import com.thefirstmod.TheFirstMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = TheFirstMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents
{
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(TheFirstMod.RAINBOW_LIGHTNING.get(), RainbowLightningRenderer::new);
        event.registerEntityRenderer(TheFirstMod.RAINBOW_METEOR.get(), MeteorRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(KeyBindings.METEOR);
    }
}
