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

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
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

                    quad(matrix, consumer, nextX, nextZ, index, prevX, prevZ, red, green, blue, alpha,
                            widthA, widthB, false, false, true, false);
                    quad(matrix, consumer, nextX, nextZ, index, prevX, prevZ, red, green, blue, alpha,
                            widthA, widthB, true, false, true, true);
                    quad(matrix, consumer, nextX, nextZ, index, prevX, prevZ, red, green, blue, alpha,
                            widthA, widthB, true, true, false, true);
                    quad(matrix, consumer, nextX, nextZ, index, prevX, prevZ, red, green, blue, alpha,
                            widthA, widthB, false, true, false, false);
                }
            }
        }
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer,
                             float x1, float z1, int index, float x2, float z2,
                             float red, float green, float blue, float alpha,
                             float widthA, float widthB,
                             boolean flip1, boolean flip2, boolean flip3, boolean flip4)
    {
        consumer.addVertex(matrix, x1 + (flip1 ? widthB : -widthB), (float) (index * 16), z1 + (flip2 ? widthB : -widthB))
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2 + (flip1 ? widthA : -widthA), (float) ((index + 1) * 16), z2 + (flip2 ? widthA : -widthA))
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2 + (flip3 ? widthA : -widthA), (float) ((index + 1) * 16), z2 + (flip4 ? widthA : -widthA))
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1 + (flip3 ? widthB : -widthB), (float) (index * 16), z1 + (flip4 ? widthB : -widthB))
                .setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(RainbowLightning entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
