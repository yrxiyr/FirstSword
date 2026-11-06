package com.chufeng;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端长按 J 蓄力结束后发给服务端的「放流星群」请求 */
public record MeteorCastPayload(float power) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<MeteorCastPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ChuFeng.MODID, "meteor_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MeteorCastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, MeteorCastPayload::power,
                    MeteorCastPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
