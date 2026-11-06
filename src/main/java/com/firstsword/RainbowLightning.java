package com.firstsword;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * 彩虹闪电。
 *
 * 刻意不继承原版 LightningBolt：原版实体会点燃方块、给避雷针充能、清除铜锈，
 * 并且每次生成都会播放音量 10000 的雷声。这里只保留「一道彩色雷 + 一次范围伤害」。
 *
 * 一次释放由多道雷组成：第一道立刻落下，之后每隔 {@link #STRIKE_DELAY} tick
 * 在施法者周围随机位置补落下一道。
 */
public class RainbowLightning extends Entity
{
    /** 存在时间，8 tick = 0.4 秒 */
    public static final int LIFETIME = 8;

    /** 雷与雷之间的间隔（tick），3 tick ≈ 0.15 秒 */
    private static final int STRIKE_DELAY = 3;

    /** 每道闪电的基础伤害，15 点 = 7.5 颗心。词条会在此基础上改写 */
    public static final float BASE_DAMAGE = 15.0F;

    /** 这一道的实际伤害，由施法时算好并沿链条传给后续的雷 */
    private float damage = BASE_DAMAGE;

    /** 击退强度，原版普通攻击是 0.4 */
    private static final double KNOCKBACK = 1.2D;

    /** 伤害判定半径（格） */
    private static final double DAMAGE_RADIUS = 3.0D;

    /**
     * 彩虹色板。闪电走加色混合（blendFunc SRC_ALPHA, ONE），四层叠加后核心必然过曝，
     * 所以这里取「亮而仍有色相」的中间值：芯是白热的，外圈是彩色光晕。
     */
    private static final int[] PALETTE = {
            0xFF6B6B, // 红
            0xFFAA5C, // 橙
            0xFFE066, // 黄
            0x7CF07C, // 绿
            0x6FE3F0, // 青
            0x7FAEFF, // 蓝
            0xBE8CFF, // 紫
            0xFF8FC7  // 粉
    };

    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(RainbowLightning.class, EntityDataSerializers.INT);

    /** 渲染用的随机种子；客户端要读，所以是 public（和原版 LightningBolt 一致） */
    public long seed;

    private int life = LIFETIME;

    /** 这一发里，自己之后还要再落几道 */
    private int followUps;

    /** 施法者位置与范围，用来给后续的雷挑选随机落点 */
    private double anchorX;
    private double anchorZ;
    private double anchorRadius;

    /** 施法者，伤害记在他头上；后续的雷共享同一个 UUID */
    private UUID ownerUuid;

    /** 一发之内共用，保证同一个目标最多吃一次伤害 */
    private Set<Entity> damaged;

    public RainbowLightning(EntityType<? extends RainbowLightning> entityType, Level level)
    {
        super(entityType, level);
        this.seed = this.random.nextLong();
        this.noPhysics = true;
    }

    public void setColor(int rgb)
    {
        this.entityData.set(DATA_COLOR, rgb);
    }

    /** 0xRRGGBB */
    public int getColor()
    {
        return this.entityData.get(DATA_COLOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_COLOR, 0xFFFFFF);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (!this.level().isClientSide())
        {
            // tickCount 在 ServerLevel 里是调用 tick() 之前自增的，首帧为 1
            if (this.tickCount == 1)
            {
                this.strikeDamage();
            }

            if (this.followUps > 0 && this.tickCount == STRIKE_DELAY)
            {
                this.spawnFollowUp();
            }
        }

        if (--this.life < 0)
        {
            this.discard();
        }
    }

    /**
     * 释放一次：共 {@code waves} 波，每波 {@code boltsPerWave} 道。
     * 第一波立刻全部落下，之后每隔 {@link #STRIKE_DELAY} tick 补一波，
     * 每一道都会在施法者周围重新挑一个随机落点。
     */
    public static void summon(ServerLevel level, Player player, int waves, int boltsPerWave, double radius,
                              float damage)
    {
        RandomSource random = level.random;
        Set<Entity> damaged = new HashSet<>();

        for (int i = 0; i < boltsPerWave; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0;
            // 从 0 到 radius 均匀取距离：这样落点会向玩家身边聚集，
            // 近战范围内也有雷，而不是在远处铺一圈留个空心的圈
            double distance = radius * random.nextDouble();
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;

            RainbowLightning bolt = create(level, random, x, z);
            // 每一道各自负责自己后续的补落，所以三波是 16 条链并行推进
            bolt.followUps = waves - 1;
            bolt.damage = damage;
            bolt.anchorX = player.getX();
            bolt.anchorZ = player.getZ();
            bolt.anchorRadius = radius;
            bolt.ownerUuid = player.getUUID();
            bolt.damaged = damaged;
            level.addFreshEntity(bolt);
        }
    }

    /** 从色板里随机取一个颜色 */
    public static int randomColor(RandomSource random)
    {
        return PALETTE[random.nextInt(PALETTE.length)];
    }

    /**
     * 把颜色按比例向白色插值：0 = 原色，1 = 纯白。
     * dust 粒子没有自发光通道，想让粒子看起来更亮只能把颜色本身往白里推。
     */
    public static int brighten(int rgb, float amount)
    {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);

        return (r << 16) | (g << 8) | b;
    }

    private static RainbowLightning create(ServerLevel level, RandomSource random, double x, double z)
    {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));

        RainbowLightning bolt = new RainbowLightning(FirstSword.RAINBOW_LIGHTNING.get(), level);
        bolt.setPos(x, y, z);
        bolt.seed = random.nextLong();
        bolt.setColor(PALETTE[random.nextInt(PALETTE.length)]);
        return bolt;
    }

    /** 在施法者附近再挑一个随机落点，补落下一道 */
    private void spawnFollowUp()
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        RandomSource random = this.random;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = this.anchorRadius * random.nextDouble();
        double x = this.anchorX + Math.cos(angle) * distance;
        double z = this.anchorZ + Math.sin(angle) * distance;

        RainbowLightning next = create(serverLevel, random, x, z);
        next.followUps = this.followUps - 1;
        next.damage = this.damage;
        next.anchorX = this.anchorX;
        next.anchorZ = this.anchorZ;
        next.anchorRadius = this.anchorRadius;
        next.ownerUuid = this.ownerUuid;
        next.damaged = this.damaged;
        serverLevel.addFreshEntity(next);
    }

    /** 对落点附近的生物造成一次伤害 + 击退 */
    private void strikeDamage()
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        ServerPlayer owner = this.ownerUuid == null
                ? null
                : serverLevel.getServer().getPlayerList().getPlayer(this.ownerUuid);

        DamageSource source = owner != null
                ? serverLevel.damageSources().playerAttack(owner)
                : serverLevel.damageSources().lightningBolt();

        if (this.damaged == null)
        {
            this.damaged = new HashSet<>();
        }

        AABB area = new AABB(
                this.getX() - DAMAGE_RADIUS, this.getY() - 2.0D, this.getZ() - DAMAGE_RADIUS,
                this.getX() + DAMAGE_RADIUS, this.getY() + 6.0D, this.getZ() + DAMAGE_RADIUS);

        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area))
        {
            // 只打敌对生物：动物、村民、宠物、其他玩家都不会被雷劈到
            if (!(target instanceof Enemy) || !target.isAlive() || !this.damaged.add(target))
            {
                continue;
            }

            if (target.hurt(source, this.damage))
            {
                target.knockback(KNOCKBACK,
                        this.getX() - target.getX(),
                        this.getZ() - target.getZ());
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr)
    {
        return distanceSqr < 128.0 * 128.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound)
    {
    }
}
