package com.firstsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.firstsword.RainbowLightning;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import org.joml.Matrix4f;

/**
 * 闪电渲染的直接拷贝，唯一区别是颜色由实体提供，而不是写死成 0.45/0.45/0.5。
 * 算法改自原版 LightningBoltRenderer。
 */
public class RainbowLightningRenderer extends EntityRenderer<RainbowLightning>
{
    private static final float ALPHA = 0.3F;

    public RainbowLightningRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(RainbowLightning entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
        float[] offsetsX = new float[8];
        float[] offsetsZ = new float[8];
        float originX = 0.0F;
        float originZ = 0.0F;
        RandomSource shapeRandom = RandomSource.create(entity.seed);

        // 先算出一条主干的走向
        for (int i = 7; i >= 0; i--)
        {
            offsetsX[i] = originX;
            offsetsZ[i] = originZ;
            originX += (float) (shapeRandom.nextInt(11) - 5);
            originZ += (float) (shapeRandom.nextInt(11) - 5);
        }

        int rgb = entity.getColor();
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        // 随存在时间线性淡出，避免“啪”一下消失
        float alpha = ALPHA;

        // 横放的雷：主轴沿 (dirX, dirZ)。两个分量都是 0 就是普通竖直落雷
        float dirX = entity.dirX();
        float dirZ = entity.dirZ();
        float dirLength = Mth.sqrt(dirX * dirX + dirZ * dirZ);
        boolean horizontal = dirLength > 1.0E-4F;

        if (horizontal)
        {
            dirX /= dirLength;
            dirZ /= dirLength;
        }

        float perpX = horizontal ? -dirZ : 0.0F;
        float perpZ = horizontal ? dirX : 0.0F;

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());

        // 穿墙模式（森罗核心）下向下也延伸一份：整条雷变成上下贯穿的光柱，
        // 站在高处也能劈到下面的怪
        int passes = entity.downward() ? 2 : 1;

        for (int pass = 0; pass < passes; pass++)
        {
            if (pass == 1)
            {
                poseStack.pushPose();
                // 平移而不是镜像：镜像会翻转顶点绕序，被背面剔除掉
                poseStack.translate(0.0D, -15.0D, 0.0D);
            }

            Matrix4f matrix = poseStack.last().pose();

        // 四层，每层宽度不同，叠出闪电的光晕
        for (int layer = 0; layer < 4; layer++)
        {
            RandomSource random = RandomSource.create(entity.seed);

            // 三条分支：主干 + 两条随机分叉
            for (int branch = 0; branch < 3; branch++)
            {
                int start = 7;
                int end = 0;
                if (branch > 0)
                {
                    start = 7 - branch;
                    end = start - 2;
                }

                float nextX = offsetsX[start] - originX;
                float nextZ = offsetsZ[start] - originZ;

                for (int index = start; index >= end; index--)
                {
                    float prevX = nextX;
                    float prevZ = nextZ;

                    if (branch == 0)
                    {
                        nextX += (float) (random.nextInt(11) - 5);
                        nextZ += (float) (random.nextInt(11) - 5);
                    }
                    else
                    {
                        nextX += (float) (random.nextInt(31) - 15);
                        nextZ += (float) (random.nextInt(31) - 15);
                    }

                    float widthA = 0.1F + (float) layer * 0.2F;
                    if (branch == 0)
                    {
                        widthA *= (float) index * 0.1F + 1.0F;
                    }

                    float widthB = 0.1F + (float) layer * 0.2F;
                    if (branch == 0)
                    {
                        widthB *= ((float) index - 1.0F) * 0.1F + 1.0F;
                    }

                    quad(matrix, consumer, horizontal, dirX, dirZ, perpX, perpZ, nextX, nextZ, index, prevX, prevZ, red, green, blue, alpha,
                            widthA, widthB, false, false, true, false);
                    quad(matrix, consumer, horizontal, dirX, dirZ, perpX, perpZ, nextX, nextZ, index, prevX, prevZ, red, green, blue, alpha,
                            widthA, widthB, true, false, true, true);
                    quad(matrix, consumer, horizontal, dirX, dirZ, perpX, perpZ, nextX, nextZ, index, prevX, prevZ, red, green, blue, alpha,
                            widthA, widthB, true, true, false, true);
                    quad(matrix, consumer, horizontal, dirX, dirZ, perpX, perpZ, nextX, nextZ, index, prevX, prevZ, red, green, blue, alpha,
                            widthA, widthB, false, true, false, false);
                }
            }
        }

            if (pass == 1)
            {
                poseStack.popPose();
            }
        }
    }

    /**
     * 一个闪电段。
     *
     * 竖直模式：Y 轴就是主轴，抖动铺在 X/Z 平面上（原版写法）。
     * 横放模式：主轴换成 (dirX, dirZ)，原来的 X 抖动变成侧向、Z 抖动变成上下，
     * 整条雷就是「立起来的光柱被放倒」，画法一点没变。
     */
    private static void quad(Matrix4f matrix, VertexConsumer consumer,
                             boolean horizontal, float dirX, float dirZ, float perpX, float perpZ,
                             float x1, float z1, int index, float x2, float z2,
                             float red, float green, float blue, float alpha,
                             float widthA, float widthB,
                             boolean flip1, boolean flip2, boolean flip3, boolean flip4)
    {
        if (horizontal)
        {
            vertex(consumer, matrix, dirX, dirZ, perpX, perpZ,
                    x1 + (flip1 ? widthB : -widthB), z1 + (flip2 ? widthB : -widthB), index,
                    red, green, blue, alpha);
            vertex(consumer, matrix, dirX, dirZ, perpX, perpZ,
                    x2 + (flip1 ? widthA : -widthA), z2 + (flip2 ? widthA : -widthA), index + 1,
                    red, green, blue, alpha);
            vertex(consumer, matrix, dirX, dirZ, perpX, perpZ,
                    x2 + (flip3 ? widthA : -widthA), z2 + (flip4 ? widthA : -widthA), index + 1,
                    red, green, blue, alpha);
            vertex(consumer, matrix, dirX, dirZ, perpX, perpZ,
                    x1 + (flip3 ? widthB : -widthB), z1 + (flip4 ? widthB : -widthB), index,
                    red, green, blue, alpha);
            return;
        }

        consumer.addVertex(matrix, x1 + (flip1 ? widthB : -widthB), (float) (index * 16), z1 + (flip2 ? widthB : -widthB))
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2 + (flip1 ? widthA : -widthA), (float) ((index + 1) * 16), z2 + (flip2 ? widthA : -widthA))
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2 + (flip3 ? widthA : -widthA), (float) ((index + 1) * 16), z2 + (flip4 ? widthA : -widthA))
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1 + (flip3 ? widthB : -widthB), (float) (index * 16), z1 + (flip4 ? widthB : -widthB))
                .setColor(red, green, blue, alpha);
    }

    /** 横放模式下的一枚顶点：沿主轴铺开，抖动变成侧向 + 上下 */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               float dirX, float dirZ, float perpX, float perpZ,
                               float jitterX, float jitterZ, int index,
                               float red, float green, float blue, float alpha)
    {
        // 横放时光柱总长压到 7 格左右，并以实体位置为中心
        float along = index * 10.0F - 35.0F;

        consumer.addVertex(matrix,
                        dirX * along + perpX * jitterX,
                        jitterZ,
                        dirZ * along + perpZ * jitterX)
                .setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(RainbowLightning entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
