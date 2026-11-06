package com.chufeng.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import com.chufeng.SlashArc;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * 刀光：平面里的一段月牙形彩虹弧，两端收细、整体淡出。
 *
 * 几何全程序化生成，不用贴图：把平面内的极坐标 (角度, 半径) 映射成顶点，
 * 宽度按 sin(pi*t) 走——中间最厚，两个刀尖是尖的。
 */
public class SlashArcRenderer extends EntityRenderer<SlashArc>
{
    private static final RenderType ARC = RenderType.create(
            "chufeng_slash_arc",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
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

    /** 弧线张角（弧度），约 137° */
    private static final float SWEEP = 2.4F;
    /** 弧线最宽处的厚度（格） */
    private static final float MAX_WIDTH = 0.5F;
    private static final int SEGMENTS = 24;

    public SlashArcRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(SlashArc entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
        float age = entity.tickCount + partialTicks;
        float progress = age / SlashArc.LIFETIME;

        if (progress >= 1.0F)
        {
            return;
        }

        // 平方衰减，刀光消失得更干脆
        float alpha = (1.0F - progress) * (1.0F - progress);

        Vec3 axis = new Vec3(entity.axisX(), entity.axisY(), entity.axisZ());

        if (axis.lengthSqr() < 1.0E-6D)
        {
            axis = new Vec3(0.0D, 0.0D, 1.0D);
        }

        axis = axis.normalize();

        // 平面内的一组正交基
        Vec3 u = new Vec3(0.0D, 1.0D, 0.0D).cross(axis);

        if (u.lengthSqr() < 1.0E-6D)
        {
            u = new Vec3(1.0D, 0.0D, 0.0D);
        }

        u = u.normalize();

        Vec3 v = axis.cross(u).normalize();

        // 平面内再按 roll 转一下，刀光朝向才够「乱」
        float roll = entity.getYRot() * ((float) Math.PI / 180.0F);
        float cos = (float) Math.cos(roll);
        float sin = (float) Math.sin(roll);

        Vec3 right = u.scale(cos).add(v.scale(sin));
        Vec3 up = u.scale(-sin).add(v.scale(cos));

        VertexConsumer consumer = buffer.getBuffer(ARC);
        Matrix4f matrix = poseStack.last().pose();

        // 稍微向外扩散一点，像刀势扫过去
        float radius = SlashArc.RADIUS * (0.9F + 0.35F * progress);
        float hue = entity.getYRot() / 360.0F;

        arc(consumer, matrix, right, up, radius, hue, alpha);
    }

    private static void arc(VertexConsumer consumer, Matrix4f matrix, Vec3 u, Vec3 v,
                            float radius, float hueBase, float alpha)
    {
        for (int i = 0; i < SEGMENTS; i++)
        {
            float t0 = i / (float) SEGMENTS;
            float t1 = (i + 1) / (float) SEGMENTS;

            float a0 = SWEEP * (t0 - 0.5F);
            float a1 = SWEEP * (t1 - 0.5F);

            float w0 = MAX_WIDTH * (float) Math.sin(Math.PI * t0);
            float w1 = MAX_WIDTH * (float) Math.sin(Math.PI * t1);

            float alpha0 = alpha * (0.25F + 0.75F * (float) Math.sin(Math.PI * t0));
            float alpha1 = alpha * (0.25F + 0.75F * (float) Math.sin(Math.PI * t1));

            int c0 = RainbowHue.rgb(hueBase + t0);
            int c1 = RainbowHue.rgb(hueBase + t1);

            polar(consumer, matrix, u, v, a0, radius - w0 * 0.5F, c0, alpha0);
            polar(consumer, matrix, u, v, a0, radius + w0 * 0.5F, c0, alpha0);
            polar(consumer, matrix, u, v, a1, radius + w1 * 0.5F, c1, alpha1);
            polar(consumer, matrix, u, v, a1, radius - w1 * 0.5F, c1, alpha1);
        }
    }

    /** 平面内极坐标 → 世界方向偏移 */
    private static void polar(VertexConsumer consumer, Matrix4f matrix, Vec3 u, Vec3 v,
                              float angle, float distance, int rgb, float alpha)
    {
        double c = Math.cos(angle) * distance;
        double s = Math.sin(angle) * distance;

        consumer.addVertex(matrix,
                        (float) (u.x * c + v.x * s),
                        (float) (u.y * c + v.y * s),
                        (float) (u.z * c + v.z * s))
                .setColor(
                        ((rgb >> 16) & 0xFF) / 255.0F,
                        ((rgb >> 8) & 0xFF) / 255.0F,
                        (rgb & 0xFF) / 255.0F,
                        alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(SlashArc entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
