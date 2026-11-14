package com.firstsword.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import com.firstsword.RainbowMeteor;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * 中型 / 大流星的光效：面向镜头的圆形光晕 + 十字星芒。
 *
 * 原版粒子做不出这种形状（都是固定贴图的小点），所以这里程序化生成：
 * 光晕是一圈由内向外的渐变三角扇，星芒是八个从中心向外收尖的三角。
 * 全部走加法混合，看上去是自发光的一片。
 *
 * 小流星不画——数量太多，满天光斑会糊成一片。
 */
public class MeteorRenderer extends EntityRenderer<RainbowMeteor>
{
    /**
     * 专用渲染层：加法混合 + 不写深度，光效只往画面上加光，不会挡住后面的东西。
     */
    private static final RenderType FLARE = RenderType.create(
            "firstsword_meteor_flare",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * 小流星是否也画光效。
     * 满蓄 + 摧灭核心时同屏可能有两百多颗小流星，卡的话把这里改成 false 就退回只有中/大流星有光效。
     */
    private static final boolean SMALL_FLARE = true;

    public MeteorRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(RainbowMeteor entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
        boolean big = entity.isBig();
        boolean medium = !big && entity.isMedium();
        boolean small = !big && !medium;

        if (small && !SMALL_FLARE)
        {
            return;
        }

        float age = entity.tickCount + partialTicks;

        // 明暗脉动：每颗流星相位不同，整片雨才是错开着一闪一闪的
        float pulse = 0.72F + 0.28F * Mth.sin(age * 1.8F + entity.getId() * 0.37F);

        int rgb = entity.getColor();
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        poseStack.pushPose();
        // 面向镜头：光效永远正对玩家，不会因为角度看过去变成一条线
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(FLARE);

        if (small)
        {
            // 小流星只要一个白色小光点：两环、8 段，20 个顶点就够
            halo(consumer, matrix, 0.3F, 8, 2, 1.0F, 1.0F, 1.0F, 0.55F * pulse);
            poseStack.popPose();
            return;
        }

        // 大流星 0.8 / 中型 0.65 的基准亮度
        float alpha = (big ? 0.8F : 0.65F) * pulse;

        float haloRadius = (big ? 2.6F : 1.7F) * (0.9F + 0.1F * pulse);
        float spikeLength = (big ? 4.0F : 2.6F) * (0.9F + 0.1F * pulse);

        int segments = big ? 28 : 20;
        int rings = big ? 5 : 4;

        // 彩色光晕：由内向外衰减，整体压淡一档
        halo(consumer, matrix, haloRadius, segments, rings, red, green, blue, alpha * 0.38F);
        // 中心一点白热：半径小到看不出轮廓，只是让中心有个亮点
        halo(consumer, matrix, big ? 0.26F : 0.18F, segments, rings, 1.0F, 1.0F, 1.0F, alpha * 0.9F);
        // 十字星芒
        star(consumer, matrix, spikeLength, red, green, blue, alpha);

        poseStack.popPose();
    }

    /**
     * 由内向外的渐变光晕：几层同心环叠起来，越往外越淡。
     *
     * 单个三角扇是从中心直接插值到边缘的线性渐变，看起来仍像一块实心圆盘；
     * 分环 + 平方衰减之后，内圈浓、外圈几乎透明，边缘就化开了。
     */
    private static void halo(VertexConsumer consumer, Matrix4f matrix, float radius,
                             int segments, int rings,
                             float red, float green, float blue, float alpha)
    {
        for (int ring = 0; ring < rings; ring++)
        {
            float t0 = ring / (float) rings;
            float t1 = (ring + 1) / (float) rings;

            float a0 = alpha * (1.0F - t0) * (1.0F - t0);
            float a1 = alpha * (1.0F - t1) * (1.0F - t1);

            float r0 = radius * t0;
            float r1 = radius * t1;

            for (int i = 0; i < segments; i++)
            {
                float ang0 = Mth.TWO_PI * i / segments;
                float ang1 = Mth.TWO_PI * (i + 1) / segments;

                float c0 = Mth.cos(ang0);
                float s0 = Mth.sin(ang0);
                float c1 = Mth.cos(ang1);
                float s1 = Mth.sin(ang1);

                // 两个三角形拼成一个环带
                vertex(consumer, matrix, c0 * r0, s0 * r0, red, green, blue, a0);
                vertex(consumer, matrix, c0 * r1, s0 * r1, red, green, blue, a1);
                vertex(consumer, matrix, c1 * r1, s1 * r1, red, green, blue, a1);

                vertex(consumer, matrix, c0 * r0, s0 * r0, red, green, blue, a0);
                vertex(consumer, matrix, c1 * r1, s1 * r1, red, green, blue, a1);
                vertex(consumer, matrix, c1 * r0, s1 * r0, red, green, blue, a0);
            }
        }
    }

    /** 八条星芒：四个长的摆成十字，四个短的在斜角 */
    private static void star(VertexConsumer consumer, Matrix4f matrix, float length,
                             float red, float green, float blue, float alpha)
    {
        for (int i = 0; i < 8; i++)
        {
            float angle = Mth.TWO_PI * i / 8.0F;
            boolean diagonal = (i & 1) == 1;

            float len = diagonal ? length * 0.55F : length;
            float halfWidth = (diagonal ? 0.09F : 0.15F) * (length / 3.0F);

            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            float px = -sin * halfWidth;
            float py = cos * halfWidth;

            vertex(consumer, matrix, -px, -py, red, green, blue, alpha);
            vertex(consumer, matrix, px, py, red, green, blue, alpha);
            vertex(consumer, matrix, cos * len, sin * len, red, green, blue, 0.0F);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               float x, float y, float red, float green, float blue, float alpha)
    {
        consumer.addVertex(matrix, x, y, 0.0F).setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(RainbowMeteor entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
