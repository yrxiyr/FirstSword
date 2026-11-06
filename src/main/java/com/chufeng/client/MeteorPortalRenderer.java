package com.chufeng.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import com.chufeng.MeteorPortal;

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
 * 空中法阵的渲染：外圈粗环 + 内圈细环 + 24 根辐条 + 六芒星，全部程序化生成，不用贴图。
 *
 * 法阵摆在垂直于飞行方向的平面里，所以顶点是先在「平面坐标」里算好，
 * 再用两条正交基向量映射回世界方向的。
 */
public class MeteorPortalRenderer extends EntityRenderer<MeteorPortal>
{
    private static final float TWO_PI = (float) (Math.PI * 2.0D);

    private static final int RING_SEGMENTS = 96;
    private static final int SPOKES = 24;

    /** 旋转速度（弧度 / tick） */
    private static final float ROTATION_SPEED = 0.006F;
    /** 色相旋转速度（每 tick 转过的色相比例） */
    private static final float HUE_SPEED = 0.0025F;

    /** 淡入 / 淡出的时长（tick） */
    private static final float FADE_IN = 8.0F;
    private static final float FADE_OUT = 14.0F;

    /**
     * 专用渲染层：加法混合 + 不写深度，法阵只往画面上加光，不会挡住后面的东西。
     * 用 POSITION_COLOR，不需要贴图。
     */
    private static final RenderType PORTAL = RenderType.create(
            "chufeng_meteor_portal",
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

    public MeteorPortalRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(MeteorPortal entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
        float age = entity.tickCount + partialTicks;
        float alpha = fade(age);

        if (alpha <= 0.01F)
        {
            return;
        }

        Vec3 dir = new Vec3(entity.dirX(), entity.dirY(), entity.dirZ());

        if (dir.lengthSqr() < 1.0E-6D)
        {
            dir = new Vec3(0.0D, -1.0D, 0.0D);
        }

        dir = dir.normalize();

        // 在垂直于飞行方向的平面里取一组正交基
        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(dir);

        if (right.lengthSqr() < 1.0E-6D)
        {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }

        right = right.normalize();

        Vec3 up = dir.cross(right).normalize();

        VertexConsumer consumer = buffer.getBuffer(PORTAL);
        Matrix4f matrix = poseStack.last().pose();

        float spin = age * ROTATION_SPEED;
        float hue = age * HUE_SPEED;
        float pulse = 0.82F + 0.18F * (float) Math.sin(age * 0.35D);

        // 外圈粗环，正着转
        ring(consumer, matrix, right, up, 0.93F, 1.0F, spin, hue, alpha * pulse);

        // 内圈细环，反着转，色相错开
        ring(consumer, matrix, right, up, 0.79F, 0.815F, -spin * 1.6F, hue + 0.35F, alpha * pulse * 0.8F);

        // 两道环之间的辐条
        spokes(consumer, matrix, right, up, 0.815F, 0.93F, -spin * 1.6F, hue, alpha * pulse * 0.6F);

        // 内圈里的六芒星
        hexagram(consumer, matrix, right, up, 0.52F, spin * 0.5F, hue, alpha * pulse * 0.85F);
    }

    /** 一道圆环，颜色沿圆周扫过整个色相 */
    private static void ring(VertexConsumer consumer, Matrix4f matrix, Vec3 right, Vec3 up,
                             float inner, float outer, float spin, float hueBase, float alpha)
    {
        for (int i = 0; i < RING_SEGMENTS; i++)
        {
            float a0 = spin + TWO_PI * i / RING_SEGMENTS;
            float a1 = spin + TWO_PI * (i + 1) / RING_SEGMENTS;

            int c0 = RainbowHue.rgb(a0 / TWO_PI + hueBase);
            int c1 = RainbowHue.rgb(a1 / TWO_PI + hueBase);

            polar(consumer, matrix, right, up, inner, a0, c0, alpha);
            polar(consumer, matrix, right, up, outer, a0, c0, alpha);
            polar(consumer, matrix, right, up, outer, a1, c1, alpha);
            polar(consumer, matrix, right, up, inner, a1, c1, alpha);
        }
    }

    /** 圆环之间的放射状短条 */
    private static void spokes(VertexConsumer consumer, Matrix4f matrix, Vec3 right, Vec3 up,
                               float from, float to, float spin, float hueBase, float alpha)
    {
        // 把 0.4 格的宽度换算成弧度：半径越大，同样的角宽对应的弧长越长
        float halfAngle = 0.4F / (to * MeteorPortal.RADIUS);

        for (int i = 0; i < SPOKES; i++)
        {
            float angle = spin + TWO_PI * i / SPOKES;
            int rgb = RainbowHue.rgb(angle / TWO_PI + hueBase);

            polar(consumer, matrix, right, up, from, angle - halfAngle, rgb, alpha);
            polar(consumer, matrix, right, up, to, angle - halfAngle, rgb, alpha);
            polar(consumer, matrix, right, up, to, angle + halfAngle, rgb, alpha);
            polar(consumer, matrix, right, up, from, angle + halfAngle, rgb, alpha);
        }
    }

    /** 两个交叠的等边三角形，画成细线 */
    private static void hexagram(VertexConsumer consumer, Matrix4f matrix, Vec3 right, Vec3 up,
                                 float radius, float spin, float hueBase, float alpha)
    {
        double r = radius * MeteorPortal.RADIUS;

        for (int triangle = 0; triangle < 2; triangle++)
        {
            float base = spin + (float) (Math.PI * 0.5D) + triangle * TWO_PI / 6.0F;

            for (int edge = 0; edge < 3; edge++)
            {
                float a0 = base + TWO_PI * edge / 3.0F;
                float a1 = base + TWO_PI * (edge + 1) / 3.0F;

                int rgb = RainbowHue.rgb((a0 + a1) * 0.5F / TWO_PI + hueBase);

                line(consumer, matrix, right, up,
                        Math.cos(a0) * r, Math.sin(a0) * r,
                        Math.cos(a1) * r, Math.sin(a1) * r,
                        0.22F, rgb, alpha);
            }
        }
    }

    /** 平面坐标下的一条线段，按半宽扩成一个矩形 */
    private static void line(VertexConsumer consumer, Matrix4f matrix, Vec3 right, Vec3 up,
                             double x0, double y0, double x1, double y1,
                             float halfWidth, int rgb, float alpha)
    {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 1.0E-6D)
        {
            return;
        }

        double nx = -dy / length * halfWidth;
        double ny = dx / length * halfWidth;

        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        plane(consumer, matrix, right, up, x0 - nx, y0 - ny, red, green, blue, alpha);
        plane(consumer, matrix, right, up, x1 - nx, y1 - ny, red, green, blue, alpha);
        plane(consumer, matrix, right, up, x1 + nx, y1 + ny, red, green, blue, alpha);
        plane(consumer, matrix, right, up, x0 + nx, y0 + ny, red, green, blue, alpha);
    }

    /** 极坐标 → 平面坐标 → 世界坐标 */
    private static void polar(VertexConsumer consumer, Matrix4f matrix, Vec3 right, Vec3 up,
                              float radius, float angle, int rgb, float alpha)
    {
        double r = radius * MeteorPortal.RADIUS;

        plane(consumer, matrix, right, up,
                Math.cos(angle) * r, Math.sin(angle) * r,
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                alpha);
    }

    /** 把平面坐标 (u, v) 用正交基映射成实体本地的世界方向偏移 */
    private static void plane(VertexConsumer consumer, Matrix4f matrix, Vec3 right, Vec3 up,
                              double u, double v, float red, float green, float blue, float alpha)
    {
        consumer.addVertex(matrix,
                        (float) (right.x * u + up.x * v),
                        (float) (right.y * u + up.y * v),
                        (float) (right.z * u + up.z * v))
                .setColor(red, green, blue, alpha);
    }

    /** 淡入淡出，外加一点点呼吸 */
    private static float fade(float age)
    {
        float fadeIn = Math.min(age / FADE_IN, 1.0F);
        float fadeOut = Math.min((MeteorPortal.LIFETIME - age) / FADE_OUT, 1.0F);

        return Math.max(0.0F, Math.min(fadeIn, fadeOut));
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorPortal entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
