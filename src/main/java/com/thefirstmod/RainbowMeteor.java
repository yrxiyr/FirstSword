package com.thefirstmod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.joml.Vector3f;

/**
 * 流星。大流星砸在准星指到的位置，小流星围绕它斜着砸下来。
 *
 * 本体不渲染，靠客户端每 tick 撒的粒子形成沿飞行方向的拖尾。
 */
@EventBusSubscriber(modid = TheFirstMod.MODID)
public class RainbowMeteor extends Entity
{
    /** 准星射线的最远距离（格） */
    private static final double MAX_RANGE = 32.0D;
    /** 小流星围绕大流星落点散开的半径（格） */
    private static final double SCATTER_RADIUS = 18.0D;

    /** 下落速度（格 / tick） */
    private static final double FALL_SPEED = 2.0D;

    // ---- 大流星 ----
    private static final float BIG_DAMAGE = 64.0F;
    private static final double BIG_DAMAGE_RADIUS = 9.0D;
    private static final double BIG_KNOCKBACK = 2.0D;
    private static final int BIG_DESTROY_RADIUS = 9;
    /** 每一列的深度在这两个值之间随机，坑底才是坑坑洼洼而不是一块平板 */
    private static final int BIG_DESTROY_DEPTH_MIN = 4;
    private static final int BIG_DESTROY_DEPTH_MAX = 5;
    private static final int BIG_MAX_DESTROY = 3000;

    // ---- 小流星 ----
    private static final float SMALL_DAMAGE = 8.0F;
    private static final double SMALL_DAMAGE_RADIUS = 2.5D;
    private static final double SMALL_KNOCKBACK = 1.2D;
    private static final int SMALL_DESTROY_RADIUS = 2;
    private static final int SMALL_DESTROY_DEPTH = 1;
    private static final int SMALL_MAX_DESTROY = 40;
    private static final float SMALL_FIRE_CHANCE = 0.3F;
    private static final int SMALL_FIRE_ATTEMPTS = 1;

    /** 满蓄时的小流星数量；蓄力越短越少 */
    private static final int SMALL_MIN = 24;
    private static final int SMALL_MAX = 96;

    /** 小流星在这么多 tick 内陆续生成 */
    private static final int SMALL_SPREAD_TICKS = 37;
    /** 大流星压轴 */
    private static final int BIG_DELAY_TICKS = 40;

    /** 小流星的水平漂移速度（格 / tick），决定拖尾的倾斜程度 */
    private static final float MIN_DRIFT = 0.35F;
    private static final float MAX_DRIFT = 0.75F;

    /** 拖尾长度（格） */
    private static final double TRAIL_LENGTH = 8.0D;
    private static final int SMALL_TRAIL_POINTS = 6;
    private static final int BIG_TRAIL_POINTS = 10;

    /** 坑底铺火的覆盖率 */
    private static final float CRATER_FIRE_CHANCE = 0.6F;

    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BIG =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_DRIFT_X =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DRIFT_Z =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.FLOAT);

    private double targetY;
    private UUID ownerUuid;
    private Set<Entity> damaged;

    /** 待生成队列：小流星在 2 秒内陆续落，大流星最后落 */
    private static final List<Scheduled> PENDING = new ArrayList<>();

    public RainbowMeteor(EntityType<? extends RainbowMeteor> entityType, Level level)
    {
        super(entityType, level);
        this.noPhysics = true;
    }

    public void setColor(int rgb)
    {
        this.entityData.set(DATA_COLOR, rgb);
    }

    public int getColor()
    {
        return this.entityData.get(DATA_COLOR);
    }

    public void setBig(boolean big)
    {
        this.entityData.set(DATA_BIG, big);
    }

    public boolean isBig()
    {
        return this.entityData.get(DATA_BIG);
    }

    public void setDrift(float driftX, float driftZ)
    {
        this.entityData.set(DATA_DRIFT_X, driftX);
        this.entityData.set(DATA_DRIFT_Z, driftZ);
    }

    public float driftX()
    {
        return this.entityData.get(DATA_DRIFT_X);
    }

    public float driftZ()
    {
        return this.entityData.get(DATA_DRIFT_Z);
    }

    public void setTargetY(double targetY)
    {
        this.targetY = targetY;
    }

    public void setOwner(UUID ownerUuid)
    {
        this.ownerUuid = ownerUuid;
    }

    public void setDamaged(Set<Entity> damaged)
    {
        this.damaged = damaged;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_COLOR, 0xFFFFFF);
        builder.define(DATA_BIG, false);
        builder.define(DATA_DRIFT_X, 0.0F);
        builder.define(DATA_DRIFT_Z, 0.0F);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (this.level().isClientSide())
        {
            this.spawnTrailParticles();
            return;
        }

        this.setPos(this.getX() + this.driftX(), this.getY() - FALL_SPEED, this.getZ() + this.driftZ());

        if (this.getY() <= this.targetY || this.getY() < this.level().getMinBuildHeight())
        {
            this.impact();
            this.discard();
        }
    }

    /** 沿飞行方向的相反方向撒粒子，斜着的小流星会拉出斜的尾迹 */
    private void spawnTrailParticles()
    {
        if (this.level().getNearestPlayer(this, 64.0D) == null)
        {
            return;
        }

        boolean big = this.isBig();
        int points = big ? BIG_TRAIL_POINTS : SMALL_TRAIL_POINTS;
        float baseScale = big ? 3.0F : 1.6F;

        int rgb = this.getColor();
        Vector3f color = new Vector3f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F);

        // 速度方向单位向量（向下的分量为负），尾迹沿它的反方向铺
        double vx = this.driftX();
        double vy = -FALL_SPEED;
        double vz = this.driftZ();
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= length;
        vy /= length;
        vz /= length;

        for (int i = 0; i < points; i++)
        {
            double t = i / (double) (points - 1);
            double distance = t * TRAIL_LENGTH;
            double spread = (0.12D + t * 0.4D) * (big ? 2.0D : 1.0D);

            this.level().addParticle(
                    new DustParticleOptions(color, baseScale * (1.0F - (float) t * 0.6F)),
                    this.getX() - vx * distance + (this.random.nextDouble() - 0.5D) * spread,
                    this.getY() - vy * distance + (this.random.nextDouble() - 0.5D) * spread,
                    this.getZ() - vz * distance + (this.random.nextDouble() - 0.5D) * spread,
                    0.0D, 0.0D, 0.0D);
        }

        if (this.random.nextInt(big ? 2 : 3) == 0)
        {
            this.level().addParticle(ParticleTypes.FLAME,
                    this.getX(), this.getY(), this.getZ(), 0.0D, -0.1D, 0.0D);
        }

        if (this.random.nextInt(4) == 0)
        {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(), 0.0D, -0.2D, 0.0D);
        }
    }

    private void impact()
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        this.damageNearby(serverLevel);
        this.destroyTerrain(serverLevel);

        boolean big = this.isBig();
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                this.getX(), this.getY() + 0.5D, this.getZ(),
                big ? 12 : 2, big ? 2.0D : 0.4D, big ? 1.0D : 0.2D, big ? 2.0D : 0.4D, 0.0D);
    }

    private void damageNearby(ServerLevel serverLevel)
    {
        boolean big = this.isBig();
        double radius = big ? BIG_DAMAGE_RADIUS : SMALL_DAMAGE_RADIUS;
        float damage = big ? BIG_DAMAGE : SMALL_DAMAGE;
        double knockback = big ? BIG_KNOCKBACK : SMALL_KNOCKBACK;

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
                this.getX() - radius, this.getY() - 3.0D, this.getZ() - radius,
                this.getX() + radius, this.getY() + 4.0D, this.getZ() + radius);

        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area))
        {
            if (!(target instanceof Enemy) || !target.isAlive() || !this.damaged.add(target))
            {
                continue;
            }

            if (target.hurt(source, damage))
            {
                target.knockback(knockback,
                        this.getX() - target.getX(),
                        this.getZ() - target.getZ());
            }
        }
    }

    /** 砸坑并留火。跳过空气、液体、带方块实体的方块和不可破坏方块 */
    private void destroyTerrain(ServerLevel serverLevel)
    {
        boolean big = this.isBig();
        int radius = big ? BIG_DESTROY_RADIUS : SMALL_DESTROY_RADIUS;
        int minDepth = big ? BIG_DESTROY_DEPTH_MIN : SMALL_DESTROY_DEPTH;
        int maxDepth = big ? BIG_DESTROY_DEPTH_MAX : SMALL_DESTROY_DEPTH;
        int maxDestroy = big ? BIG_MAX_DESTROY : SMALL_MAX_DESTROY;

        BlockPos center = BlockPos.containing(this.getX(), this.getY() - 0.5D, this.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int destroyed = 0;

        for (int dx = -radius; dx <= radius && destroyed < maxDestroy; dx++)
        {
            for (int dz = -radius; dz <= radius && destroyed < maxDestroy; dz++)
            {
                if (dx * dx + dz * dz > radius * radius)
                {
                    continue;
                }

                // 每一列单独随机深度，坑底就有了起伏
                int depth = minDepth + this.random.nextInt(maxDepth - minDepth + 1);

                for (int dy = -depth; dy <= 1 && destroyed < maxDestroy; dy++)
                {
                    cursor.setWithOffset(center, dx, dy, dz);
                    BlockState state = serverLevel.getBlockState(cursor);

                    if (state.isAir() || state.liquid() || state.hasBlockEntity())
                    {
                        continue;
                    }

                    if (state.getDestroySpeed(serverLevel, cursor) < 0.0F)
                    {
                        continue;
                    }

                    serverLevel.destroyBlock(cursor, false);
                    destroyed++;
                }
            }
        }

        if (big)
        {
            this.fillCraterWithFire(serverLevel, center, radius);
            return;
        }

        for (int i = 0; i < SMALL_FIRE_ATTEMPTS; i++)
        {
            if (this.random.nextFloat() >= SMALL_FIRE_CHANCE)
            {
                continue;
            }

            int offsetX = this.random.nextInt(radius * 2 + 1) - radius;
            int offsetZ = this.random.nextInt(radius * 2 + 1) - radius;
            BlockPos column = center.offset(offsetX, 0, offsetZ);
            BlockPos firePos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);

            if (serverLevel.getBlockState(firePos).isAir()
                    && serverLevel.getBlockState(firePos.below()).isSolidRender(serverLevel, firePos.below()))
            {
                serverLevel.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    /** 在坑的每一列地表上放火 */
    private void fillCraterWithFire(ServerLevel serverLevel, BlockPos center, int radius)
    {
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                if (dx * dx + dz * dz > radius * radius)
                {
                    continue;
                }

                if (this.random.nextFloat() > CRATER_FIRE_CHANCE)
                {
                    continue;
                }

                BlockPos column = center.offset(dx, 0, dz);
                BlockPos firePos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);

                if (!serverLevel.getBlockState(firePos).isAir())
                {
                    continue;
                }

                BlockPos below = firePos.below();

                if (serverLevel.getBlockState(below).isSolidRender(serverLevel, below))
                {
                    serverLevel.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                }
            }
        }
    }

    /** 大流星砸在准星指向的位置，小流星围绕它斜着砸下来 */
    public static void cast(ServerLevel level, Player player, float power)
    {
        RandomSource random = level.random;

        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(MAX_RANGE));

        BlockHitResult hit = level.clip(new ClipContext(eye, reach,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        double x;
        double z;
        double groundY;

        if (hit.getType() == HitResult.Type.BLOCK)
        {
            BlockPos pos = hit.getBlockPos();
            x = pos.getX() + 0.5D;
            z = pos.getZ() + 0.5D;
            groundY = pos.getY() + 1.0D;
        }
        else
        {
            x = reach.x;
            z = reach.z;
            groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        }

        Set<Entity> damaged = new HashSet<>();

        int smallCount = Math.round(Mth.lerp(power, SMALL_MIN, SMALL_MAX));

        for (int i = 0; i < smallCount; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = SCATTER_RADIUS * Math.sqrt(random.nextDouble());

            double sx = x + Math.cos(angle) * distance;
            double sz = z + Math.sin(angle) * distance;
            int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx), Mth.floor(sz));

            // 水平漂移：方向和速度都随机，拖尾就有了斜度
            float driftAngle = random.nextFloat() * (float) (Math.PI * 2.0D);
            float driftSpeed = MIN_DRIFT + random.nextFloat() * (MAX_DRIFT - MIN_DRIFT);
            float driftX = (float) (Math.cos(driftAngle) * driftSpeed);
            float driftZ = (float) (Math.sin(driftAngle) * driftSpeed);

            schedule(level, player, damaged, sx, sz, sy,
                    45.0D + random.nextDouble() * 10.0D, false,
                    driftX, driftZ,
                    random.nextInt(SMALL_SPREAD_TICKS + 1));
        }

        // 大流星压轴，垂直落地
        schedule(level, player, damaged, x, z, groundY, 50.0D, true, 0.0F, 0.0F, BIG_DELAY_TICKS);
    }

    private static void schedule(ServerLevel level, Player player, Set<Entity> damaged,
                                 double x, double z, double groundY,
                                 double heightAbove, boolean big, float driftX, float driftZ, int delay)
    {
        PENDING.add(new Scheduled(level, player, damaged, x, z, groundY, heightAbove,
                big, driftX, driftZ, delay));
    }

    /** 一条待生成的流星记录 */
    private static final class Scheduled
    {
        final ServerLevel level;
        final Player player;
        final Set<Entity> damaged;
        final double x;
        final double z;
        final double groundY;
        final double heightAbove;
        final boolean big;
        final float driftX;
        final float driftZ;
        int ticksLeft;

        Scheduled(ServerLevel level, Player player, Set<Entity> damaged,
                  double x, double z, double groundY, double heightAbove,
                  boolean big, float driftX, float driftZ, int delay)
        {
            this.level = level;
            this.player = player;
            this.damaged = damaged;
            this.x = x;
            this.z = z;
            this.groundY = groundY;
            this.heightAbove = heightAbove;
            this.big = big;
            this.driftX = driftX;
            this.driftZ = driftZ;
            this.ticksLeft = delay;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        if (PENDING.isEmpty())
        {
            return;
        }

        Iterator<Scheduled> iterator = PENDING.iterator();

        while (iterator.hasNext())
        {
            Scheduled next = iterator.next();

            if (--next.ticksLeft <= 0)
            {
                iterator.remove();
                spawn(next.level, next.player, next.level.random, next.damaged,
                        next.x, next.z, next.groundY, next.heightAbove, next.big,
                        next.driftX, next.driftZ);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        PENDING.clear();
    }

    private static void spawn(ServerLevel level, Player player, RandomSource random, Set<Entity> damaged,
                              double x, double z, double groundY, double heightAbove, boolean big,
                              float driftX, float driftZ)
    {
        // 从目标位置往回推：飞多少 tick，就偏移多少水平距离
        double fallTicks = heightAbove / FALL_SPEED;
        double spawnX = x - driftX * fallTicks;
        double spawnZ = z - driftZ * fallTicks;

        RainbowMeteor meteor = new RainbowMeteor(TheFirstMod.RAINBOW_METEOR.get(), level);
        meteor.setPos(spawnX, groundY + heightAbove, spawnZ);
        meteor.setTargetY(groundY);
        meteor.setOwner(player.getUUID());
        meteor.setDamaged(damaged);
        meteor.setBig(big);
        meteor.setDrift(driftX, driftZ);
        meteor.setColor(RainbowLightning.randomColor(random));
        level.addFreshEntity(meteor);
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
