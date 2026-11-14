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

    /** 横放方向；两个都是 0 表示普通竖直落雷 */
    private static final EntityDataAccessor<Float> DATA_DIR_X =
            SynchedEntityData.defineId(RainbowLightning.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Z =
            SynchedEntityData.defineId(RainbowLightning.class, EntityDataSerializers.FLOAT);

    /** 是否向下也镜像一份（森罗核心穿墙时用） */
    private static final EntityDataAccessor<Boolean> DATA_DOWNWARD =
            SynchedEntityData.defineId(RainbowLightning.class, EntityDataSerializers.BOOLEAN);

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

    /** 无视地形，直接在当前高度生成（森罗核心） */
    private boolean throughBlocks;

    /** 根顶生成时用的高度，补落也用它 */
    private double spawnY;

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

    /** 把这道雷横过来，沿 (dirX, dirZ) 方向躺平；不调用就是普通竖直落雷 */
    public void setHorizontal(float dirX, float dirZ)
    {
        this.entityData.set(DATA_DIR_X, dirX);
        this.entityData.set(DATA_DIR_Z, dirZ);
    }

    public float dirX()
    {
        return this.entityData.get(DATA_DIR_X);
    }

    public float dirZ()
    {
        return this.entityData.get(DATA_DIR_Z);
    }

    /** 向下也镜像一份：光柱上下贯穿 */
    public void setDownward(boolean downward)
    {
        this.entityData.set(DATA_DOWNWARD, downward);
    }

    public boolean downward()
    {
        return this.entityData.get(DATA_DOWNWARD);
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
        builder.define(DATA_DIR_X, 0.0F);
        builder.define(DATA_DIR_Z, 0.0F);
        builder.define(DATA_DOWNWARD, false);
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
        summon(level, player, waves, boltsPerWave, radius, damage, false);
    }

    /**
     * 【throughBlocks】为 true 时，落雷不再贴地表生成，而是直接落在玩家当前高度——
     * 下界（地表高度是基岩顶）、洞穴、室内都能正常落雷，看起来就是穿过了方块。
     */
    public static void summon(ServerLevel level, Player player, int waves, int boltsPerWave, double radius,
                              float damage, boolean throughBlocks)
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

            RainbowLightning bolt = throughBlocks
                    ? create(level, random, x, z, player.getY())
                    : create(level, random, x, z);
            // 每一道各自负责自己后续的补落，所以三波是 16 条链并行推进
            bolt.followUps = waves - 1;
            bolt.throughBlocks = throughBlocks;
            bolt.spawnY = player.getY();

            if (throughBlocks)
            {
                bolt.setDownward(true);
            }
            bolt.damage = damage;
            bolt.anchorX = player.getX();
            bolt.anchorZ = player.getZ();
            bolt.anchorRadius = radius;
            bolt.ownerUuid = player.getUUID();
            bolt.damaged = damaged;
            level.addFreshEntity(bolt);
        }
    }

    /**
     * 以玩家为中心，在 [{@code minRadius}, {@code maxRadius}] 的环带里随机落下若干道雷。
     *
     * 和 {@link #summon} 的区别是内圈可以留空：用来做「身周炸开一圈」这类效果，
     * 不会在玩家脚下也劈一道。{@code damaged} 由调用方持有，多批共用一份去重集合。
     */
    public static void summonBurst(ServerLevel level, Player player, int bolts,
                                   double minRadius, double maxRadius, float damage,
                                   Set<Entity> damaged)
    {
        RandomSource random = level.random;

        for (int i = 0; i < bolts; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;

            RainbowLightning bolt = create(level, random, x, z);
            bolt.followUps = 0;
            bolt.damage = damage;
            bolt.anchorX = player.getX();
            bolt.anchorZ = player.getZ();
            bolt.anchorRadius = maxRadius;
            bolt.ownerUuid = player.getUUID();
            bolt.damaged = damaged;
            level.addFreshEntity(bolt);
        }
    }

    /**
     * 横着炸开一圈：在 [{ minRadius}, { maxRadius}] 的环带里随机落若干道**横向**的雷，
     * 方向朝外。和竖直落雷是同一个实体，只是渲染时主轴换成了水平方向。
     */
    public static void summonHorizontalBurst(ServerLevel level, Player player, int bolts,
                                             double minRadius, double maxRadius, float damage,
                                             Set<Entity> damaged)
    {
        RandomSource random = level.random;

        for (int i = 0; i < bolts; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;

            RainbowLightning bolt = create(level, random, x, z);
            bolt.setHorizontal((float) Math.cos(angle), (float) Math.sin(angle));
            bolt.followUps = 0;
            bolt.damage = damage;
            bolt.anchorX = player.getX();
            bolt.anchorZ = player.getZ();
            bolt.anchorRadius = maxRadius;
            bolt.ownerUuid = player.getUUID();
            bolt.damaged = damaged;
            level.addFreshEntity(bolt);
        }
    }

    /**
     * 在指定位置放一道横向彩雷。位置和朝向由调用方决定（比如铺满一个长方体）。
     */
    public static void spawnHorizontalBolt(ServerLevel level, Player owner,
                                           double x, double y, double z,
                                           float dirX, float dirZ,
                                           float damage, Set<Entity> damaged)
    {
        RainbowLightning bolt = create(level, level.random, x, z);
        bolt.setPos(x, y, z);
        bolt.setHorizontal(dirX, dirZ);
        bolt.followUps = 0;
        bolt.damage = damage;
        bolt.anchorX = x;
        bolt.anchorZ = z;
        bolt.anchorRadius = 0.0D;
        bolt.ownerUuid = owner.getUUID();
        bolt.damaged = damaged;
        level.addFreshEntity(bolt);
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

    private static RainbowLightning create(ServerLevel level, RandomSource random, double x, double z, double y)
    {
        RainbowLightning bolt = new RainbowLightning(FirstSword.RAINBOW_LIGHTNING.get(), level);
        bolt.setPos(x, y, z);
        bolt.seed = random.nextLong();
        bolt.setColor(PALETTE[random.nextInt(PALETTE.length)]);
        return bolt;
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

        RainbowLightning next = this.throughBlocks
                ? create(serverLevel, random, x, z, this.spawnY)
                : create(serverLevel, random, x, z);
        next.throughBlocks = this.throughBlocks;
        next.spawnY = this.spawnY;
        next.setDownward(this.downward());
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

        // 穿墙模式的光柱上下贯穿，判定也跟着拉高
        double down = this.throughBlocks ? 15.0D : 2.0D;
        double up = this.throughBlocks ? 10.0D : 6.0D;

        AABB area = new AABB(
                this.getX() - DAMAGE_RADIUS, this.getY() - down, this.getZ() - DAMAGE_RADIUS,
                this.getX() + DAMAGE_RADIUS, this.getY() + up, this.getZ() + DAMAGE_RADIUS);

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
