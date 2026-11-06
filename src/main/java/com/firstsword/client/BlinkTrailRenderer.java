package com.firstsword.client;

import com.mojang.blaze3d.vertex.PoseStack;

import com.firstsword.BlinkTrail;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** 斩痕本体不渲染，视觉全部由 {@link BlinkTrail} 在客户端撒的粒子构成 */
public class BlinkTrailRenderer extends EntityRenderer<BlinkTrail>
{
    public BlinkTrailRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(BlinkTrail entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
    }

    @Override
    public ResourceLocation getTextureLocation(BlinkTrail entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
