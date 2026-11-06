package com.chufeng.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import com.chufeng.ChuFeng;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import org.joml.Matrix4f;

/**
 * 蓄力时的全屏反色（时停那种感觉）。
 *
 * 用自定义 RenderType 把混合设成 {@code dst = src * (1 - dst)}：src 为白就是完整反相，
 * src 越暗反相越弱，于是随蓄力进度从轻微负片过渡到全反相。
 */
@EventBusSubscriber(modid = ChuFeng.MODID, value = Dist.CLIENT)
public class ChargeOverlay
{
    private static final RenderStateShard.TransparencyStateShard INVERT_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("chufeng_invert", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                        GlStateManager.DestFactor.ZERO);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderType INVERT = RenderType.create(
            "chufeng_invert",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(INVERT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        float strength = MeteorKeyHandler.getChargeProgress();

        if (strength <= 0.0F)
        {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Matrix4f matrix = graphics.pose().last().pose();
        float width = graphics.guiWidth();
        float height = graphics.guiHeight();

        VertexConsumer consumer = graphics.bufferSource().getBuffer(INVERT);

        consumer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(strength, strength, strength, 1.0F);
        consumer.addVertex(matrix, 0.0F, height, 0.0F).setColor(strength, strength, strength, 1.0F);
        consumer.addVertex(matrix, width, height, 0.0F).setColor(strength, strength, strength, 1.0F);
        consumer.addVertex(matrix, width, 0.0F, 0.0F).setColor(strength, strength, strength, 1.0F);

        // 不要在这里 endBatch：此时 GUI 自己的顶点还没提交，先画会被覆盖。
        // 让 GuiGraphics 在帧末统一 flush，我们的层是最后加入的，会画在最上面。
    }
}
