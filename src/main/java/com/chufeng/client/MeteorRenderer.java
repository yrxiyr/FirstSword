package com.chufeng.client;

import com.mojang.blaze3d.vertex.PoseStack;

import com.chufeng.RainbowMeteor;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * 流星本体不渲染——视觉完全由 {@link RainbowMeteor} 在客户端每 tick 撒的粒子构成。
 * 注册一个空渲染器只是为了让实体图有个合法条目。
 */
public class MeteorRenderer extends EntityRenderer<RainbowMeteor>
{
    public MeteorRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(RainbowMeteor entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
    }

    @Override
    public ResourceLocation getTextureLocation(RainbowMeteor entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
