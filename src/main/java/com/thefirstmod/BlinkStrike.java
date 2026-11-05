package com.thefirstmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 瞬闪斩：朝准星方向冲出去，路径上的敌人被切开。
 *
 * 不是瞬移，而是连续 0.1 秒（2 tick）的推进，每 tick 4 格，合计 8 格。
 * 做法是每 tick 给玩家覆盖一次速度并置 hurtMarked，让客户端也拿到同一份速度，
 * 这样镜头是连续移动的，不会有瞬移那种一跳一跳的晕眩感。
 *
 * 冲刺时给 0.1 的向上速度，让脚离地：原版视角摇晃只在 onGround 时累积，
 * 离地就没有那种颠簸感了。
 *
 * 冷却单独记，不占物品冷却，否则放完流星 32 秒内都用不了位移。
 */
@EventBusSubscriber(modid = TheFirstMod.MODID)
public final class BlinkStrike
{

    /** 冲刺总距离（格） */
    private static final double MAX_DISTANCE = 8.0D;
    /** 冲刺持续时间（tick），2 tick = 0.1 秒 */
    private static final int DASH_TICKS = 2;
    /** 每 tick 的位移，8 / 2 = 4.0 格 */
    private static final double SPEED = MAX_DISTANCE / DASH_TICKS;

    /**
     * 冲刺时给玩家的向上速度。让脚离地一点点：
     * 原版视角摇晃（bob）只在 onGround 时累积，离地就没有那种一颠一颠的抖动了
     */
    private static final double FLOAT_UP = 0.1D;

    /** 路径判定半径（格） */
    private static final double PATH_RADIUS = 2.4D;
    /** 路径伤害 */
    private static final float PATH_DAMAGE = 30.0F;

    /** 落点补刀的半径与伤害 */
    private static final double LANDING_RADIUS = 3.5D;
    private static final float LANDING_DAMAGE = 20.0F;

    /** 击退强度，原版普通攻击是 0.4 */
    private static final double KNOCKBACK = 1.4D;

    /** 砍到没能秒掉的怪：大幅减速，等于撞上了一堵墙 */
    private static final double SLOW_ON_SURVIVE = 0.15D;
    /** 砍到直接被秒掉的怪：不减速，否则一路砍一路顿，手感很粘 */
    private static final double SLOW_ON_KILL = 1.0D;

    /** 遇到一格高的台阶时最多抬高这么多格，免得一路爬上垂直的墙 */
    private static final double MAX_STEP_UP = 1.5D;

    /** 停下时炸开几道刀光 */
    private static final int STOP_SLASHES = 8;

    /** 冲刺路径上每隔多少格铺一道刀光 */
    private static final double PATH_SLASH_SPACING = 0.8D;

    /** 冷却（tick），8 秒 */
    public static final int COOLDOWN = 160;

    /** 每个玩家的冷却结束时刻，按游戏时间记 */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    /** 正在冲刺的玩家 */
    private static final List<Dash> ACTIVE = new ArrayList<>();

    private BlinkStrike()
    {
    }

    /** 剩余冷却 tick，0 表示可用 */
    public static int getRemainingCooldown(ServerPlayer player, long gameTime)
    {
        Long readyAt = READY_AT.get(player.getUUID());
        return readyAt == null ? 0 : (int) Math.max(0L, readyAt - gameTime);
    }

    public static void cast(ServerLevel level, ServerPlayer player)
    {
        long now = level.getGameTime();

        if (getRemainingCooldown(player, now) > 0)
        {
            return;
        }

        Vec3 look = player.getLookAngle();

        // 只用水平分量：玩家站在地面时脚底贴着方块顶面，视线稍微向下就会钻进地里
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);

        if (horizontal.lengthSqr() < 1.0E-6D)
        {
            // 正上 / 正下方看时水平分量退化，退回用朝向
            horizontal = Vec3.directionFromRotation(0.0F, player.getYRot());
        }

        Vec3 direction = horizontal.normalize();

        Dash dash = new Dash(player, level, direction, player.position());
        ACTIVE.add(dash);

        // 立刻推第一下，松手当 tick 就开始动
        push(dash);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);

        READY_AT.put(player.getUUID(), now + COOLDOWN);
    }

    /**
     * 覆盖玩家速度，并把速度同步给客户端。
     * 速度乘以这次的减速系数：砍到没死的怪会被拖慢，砍死的不太影响。
     */
    private static void push(Dash dash)
    {
        ServerPlayer player = dash.player;
        double speed = SPEED * dash.speedScale;

        player.setDeltaMovement(dash.direction.x * speed, FLOAT_UP, dash.direction.z * speed);
        player.hurtMarked = true;
        player.resetFallDistance();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        if (ACTIVE.isEmpty())
        {
            return;
        }

        Iterator<Dash> iterator = ACTIVE.iterator();

        while (iterator.hasNext())
        {
            Dash dash = iterator.next();
            ServerPlayer player = dash.player;

            if (!player.isAlive() || player.hasDisconnected() || player.level() != dash.level)
            {
                iterator.remove();
                continue;
            }

            Vec3 current = player.position();
            Vec3 previous = dash.previous;

            // 边走边结算：这一 tick 身边一定范围内的敌人都被切开
            damageAround(dash);

            // 每 tick 留一段斩痕：冲得快，隔一 tick 会断成一节一节的
            double chest = player.getBbHeight() * 0.6D;

            BlinkTrail.open(dash.level,
                    previous.add(0.0D, chest, 0.0D),
                    current.add(0.0D, chest, 0.0D));

            dash.previous = current;
            dash.ticksLeft--;

            if (dash.ticksLeft <= 0)
            {
                damageLanding(dash);

                // 收刀：整条路径铺满刀光，落点再炸开一圈
                spawnPathSlashes(dash, chest);
                spawnStopBurst(dash, chest);

                // 最后这一 tick 命中的怪也要刹住：把剩下的速度按减速后的值重新推一次，
                // 否则玩家会带着冲刺原速继续滑出去，看起来就是「冲过头」
                push(dash);

                dash.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.6F);

                iterator.remove();
                continue;
            }

            // 撞上一格高的台阶：往上提一格继续。抬升上限锁死，且提上去的位置必须是空的，
            // 否则会把人塞进墙里
            if (player.horizontalCollision && player.getY() - dash.startY < MAX_STEP_UP)
            {
                AABB raised = player.getBoundingBox().move(0.0D, 1.0D, 0.0D);

                if (dash.level.noBlockCollision(player, raised))
                {
                    player.teleportTo(dash.level,
                            player.getX(), player.getY() + 1.0D, player.getZ(),
                            player.getYRot(), player.getXRot());
                }
            }

            push(dash);
        }
    }

    /**
     * 沿整条冲刺路径铺刀光。
     *
     * 冲刺只有 2 tick，逐 tick 撒的话分布太粗；收刀时路径已经确定，
     * 直接按最终落点一次性铺满整条线，间距由 {@link #PATH_SLASH_SPACING} 控制。
     */
    private static void spawnPathSlashes(Dash dash, double chest)
    {
        RandomSource random = dash.level.random;
        Vec3 delta = dash.player.position().subtract(dash.start);
        double length = delta.length();

        if (length < 0.5D)
        {
            return;
        }

        int count = Math.max(4, Mth.ceil(length / PATH_SLASH_SPACING));

        for (int i = 0; i < count; i++)
        {
            // 均匀铺开，取每段的中点，两端都不会贴边
            Vec3 at = dash.start.add(delta.scale((i + 0.5D) / count));

            // 上下左右抖一下，免得整齐成一条线
            Vec3 jittered = at.add(
                    (random.nextDouble() - 0.5D) * 0.8D,
                    (random.nextDouble() - 0.5D) * 0.8D,
                    (random.nextDouble() - 0.5D) * 0.8D);

            Vec3 axis = new Vec3(
                    random.nextDouble() - 0.5D,
                    random.nextDouble() - 0.5D,
                    random.nextDouble() - 0.5D);

            SlashArc.open(dash.level,
                    jittered.add(0.0D, chest, 0.0D),
                    axis,
                    random.nextFloat() * 360.0F);
        }
    }

    /**
     * 收刀：在冲刺停下的位置炸开一圈刀光。
     *
     * 不再沿路撒——冲刺是 4 格/tick，轨迹上的刀光等玩家停下时早就全在身后了，
     * 所以只在这一下给反馈。角度以冲刺方向为中心铺开，保证大半落在视野里。
     */
    private static void spawnStopBurst(Dash dash, double chest)
    {
        RandomSource random = dash.level.random;
        Vec3 at = dash.player.position();

        for (int i = 0; i < STOP_SLASHES; i++)
        {
            // 以冲刺方向为中线，左右各铺 120°
            double spread = (random.nextDouble() - 0.5D) * Math.PI * 1.33D;
            double distance = 0.8D + random.nextDouble() * 1.6D;

            double cos = Math.cos(spread);
            double sin = Math.sin(spread);

            double x = dash.direction.x * cos - dash.direction.z * sin;
            double z = dash.direction.x * sin + dash.direction.z * cos;

            Vec3 offset = new Vec3(x * distance, 0.0D, z * distance);

            // 随便一个非零向量当平面法线，每道刀光的朝向都不一样
            Vec3 axis = new Vec3(
                    random.nextDouble() - 0.5D,
                    random.nextDouble() - 0.5D,
                    random.nextDouble() - 0.5D);

            SlashArc.open(dash.level,
                    at.add(offset).add(0.0D, chest + (random.nextDouble() - 0.5D) * 0.8D, 0.0D),
                    axis,
                    random.nextFloat() * 360.0F);
        }
    }

    /**
     * 结算「上一帧位置 → 当前位置」这一整段经过的范围。
     * 每 tick 要跨 4 格，只在末尾放一个盒子会漏掉中途被跨过去的怪。
     */
    private static void damageAround(Dash dash)
    {
        ServerPlayer player = dash.player;
        DamageSource source = dash.level.damageSources().playerAttack(player);

        Vec3 start = dash.previous;
        Vec3 end = player.position();
        Vec3 delta = end.subtract(start);
        double lengthSqr = delta.lengthSqr();

        AABB area = new AABB(start, end).inflate(PATH_RADIUS, 1.5D, PATH_RADIUS);

        for (LivingEntity target : dash.level.getEntitiesOfClass(LivingEntity.class, area))
        {
            if (target == player || !(target instanceof Enemy) || !target.isAlive())
            {
                continue;
            }

            // 扫掠盒是个长方体，四个角会伸到线外，所以再算一次到线段本身的距离
            Vec3 center = target.getBoundingBox().getCenter();
            double t = lengthSqr < 1.0E-6D
                    ? 0.0D
                    : Mth.clamp(center.subtract(start).dot(delta) / lengthSqr, 0.0D, 1.0D);

            if (start.add(delta.scale(t)).distanceToSqr(center) > PATH_RADIUS * PATH_RADIUS)
            {
                continue;
            }

            hit(dash, target, source, PATH_DAMAGE);
        }
    }

    private static void damageLanding(Dash dash)
    {
        ServerPlayer player = dash.player;
        Vec3 at = player.position();
        DamageSource source = dash.level.damageSources().playerAttack(player);

        AABB area = new AABB(
                at.x - LANDING_RADIUS, at.y - 2.0D, at.z - LANDING_RADIUS,
                at.x + LANDING_RADIUS, at.y + 3.5D, at.z + LANDING_RADIUS);

        for (LivingEntity target : dash.level.getEntitiesOfClass(LivingEntity.class, area))
        {
            if (target == player || !(target instanceof Enemy) || !target.isAlive())
            {
                continue;
            }

            hit(dash, target, source, LANDING_DAMAGE);
        }
    }

    private static void hit(Dash dash, LivingEntity target, DamageSource source, float damage)
    {
        if (!dash.damaged.add(target) || !target.hurt(source, damage))
        {
            return;
        }

        // 原版暴击特效：白星粒子从被击中的位置炸开
        dash.level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                16, 0.35D, 0.35D, 0.35D, 0.25D);

        // 砍完还站着的，说明这一刀没能带走它——刹车；秒掉的只是顿一下
        dash.speedScale *= target.isAlive() ? SLOW_ON_SURVIVE : SLOW_ON_KILL;

        Vec3 thrust = dash.direction.lengthSqr() > 1.0E-6D
                ? dash.direction
                : target.position().subtract(dash.player.position());

        if (thrust.lengthSqr() > 1.0E-6D)
        {
            // knockback 内部是「减去」传入的方向，所以这里要取反
            target.knockback(KNOCKBACK, -thrust.x, -thrust.z);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        READY_AT.clear();
        ACTIVE.clear();
    }

    /** 一次冲刺的状态 */
    private static final class Dash
    {
        final ServerPlayer player;
        final ServerLevel level;
        final Vec3 direction;
        final Vec3 start;
        final Set<Entity> damaged = new HashSet<>();
        final double startY;

        Vec3 previous;
        int ticksLeft = DASH_TICKS;

        /** 冲刺速度倍率，砍到怪就往下扣 */
        double speedScale = 1.0D;

        Dash(ServerPlayer player, ServerLevel level, Vec3 direction, Vec3 start)
        {
            this.player = player;
            this.level = level;
            this.direction = direction;
            this.start = start;
            this.previous = start;
            this.startY = start.y;
        }
    }
}
