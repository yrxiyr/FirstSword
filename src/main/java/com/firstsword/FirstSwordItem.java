package com.firstsword;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 初锋。
 * <ul>
 *   <li>右键 —— 以自身为中心落三波彩虹雷（见 {@link RainbowLightning}）</li>
 *   <li>长按 J —— 蓄力后朝视线方向 ±60° 的扇面砸流星群（见 {@link RainbowMeteor}，
 *       按键处理在 {@code client.MeteorKeyHandler}）</li>
 *   <li>按 K —— 朝准星方向瞬移并切开路径上的敌人（见 {@link BlinkStrike}）</li>
 * </ul>
 */
public class FirstSwordItem extends SwordItem
{
    /** 右键雷击：分几波 */
    private static final int WAVE_COUNT = 3;
    /** 右键雷击：每波几道 */
    private static final int BOLTS_PER_WAVE = 36;
    /** 右键雷击：落点半径（格） */
    private static final double STRIKE_RADIUS = 30.0;
    private static final int COOLDOWN_TICKS = 30;

    /** 流星群的冷却（tick），32 秒 */
    public static final int METEOR_COOLDOWN = 640;

    /** 冷却的硬下限（tick），词条怎么堆也不会低于 1 秒 */
    private static final int METEOR_COOLDOWN_MIN = 20;

    /** 这把剑每道彩虹雷的伤害（含词条加成） */
    public static float lightningDamage(ItemStack stack)
    {
        float damage = RainbowLightning.BASE_DAMAGE;

        for (SwordCore core : SwordCores.on(stack))
        {
            damage = core.lightningDamage(damage);
        }

        return damage;
    }

    /** 这把剑的流星群冷却（含词条减免） */
    public static int meteorCooldown(ItemStack stack)
    {
        int ticks = METEOR_COOLDOWN;

        for (SwordCore core : SwordCores.on(stack))
        {
            ticks = core.meteorCooldown(ticks);
        }

        return Math.max(METEOR_COOLDOWN_MIN, ticks);
    }

    /** 这把剑的瞬闪斩伤害（含词条加成），路径和落点分别传入各自的基数 */
    public static float blinkDamage(ItemStack stack, float base)
    {
        float damage = base;

        for (SwordCore core : SwordCores.on(stack))
        {
            damage = core.blinkDamage(damage);
        }

        return damage;
    }

    public FirstSwordItem(Tier tier, Properties properties)
    {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel)
        {
            RainbowLightning.summon(serverLevel, player, WAVE_COUNT, BOLTS_PER_WAVE, STRIKE_RADIUS,
                    lightningDamage(stack));
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0F, 0.6F);
        }

        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.firstsword.first_sword.rainbow"));
        tooltip.add(Component.translatable("tooltip.firstsword.first_sword.meteor"));
        tooltip.add(Component.translatable("tooltip.firstsword.first_sword.blink"));

        // 已装的词条
        for (SwordCore core : SwordCores.on(stack))
        {
            tooltip.add(Component.translatable("tooltip.firstsword.first_sword.core", core.displayName()));
        }

        super.appendHoverText(stack, context, tooltip, flag);
    }
}
