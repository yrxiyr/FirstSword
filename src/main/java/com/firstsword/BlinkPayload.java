package com.firstsword;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端按下 K 后发给服务端的「瞬闪斩」请求。没有参数，方向和距离都由服务端自己算 */
public record BlinkPayload() implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<BlinkPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(FirstSword.MODID, "blink"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlinkPayload> STREAM_CODEC =
            StreamCodec.unit(new BlinkPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
