package com.chufeng;

import java.util.List;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import io.netty.buffer.ByteBuf;

/** 自定义数据组件 */
public final class ModComponents
{
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(ChuFeng.MODID);

    /**
     * 剑上装着的词条 id 列表。
     * 必须 networkSynchronized，否则客户端拿不到，物品提示里显示不出词条。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> CORES =
            DATA_COMPONENTS.registerComponentType("cores", builder -> builder
                    .persistent(ResourceLocation.CODEC.listOf())
                    .networkSynchronized(ByteBufCodecs.<ByteBuf, ResourceLocation>list()
                            .apply(ResourceLocation.STREAM_CODEC)));

    private ModComponents()
    {
    }
}
