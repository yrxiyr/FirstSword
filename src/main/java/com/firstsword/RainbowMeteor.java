package com.firstsword;

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
import net.minecraft.world.item.ItemStack;
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
@EventBusSubscriber(modid = FirstSword.MODID)
public class RainbowMeteor extends Entity
{
    /** 准星射线的最远距离（格） */
    private static final double MAX_RANGE = 32.0D;
    /** 小流星围绕大流星落点散开的半径（格） */
    private static final double SCATTER_RADIUS = 18.0D;

    /** 下落速度（格 / tick） */
    private static final double FALL_SPEED = 2.0D;

    // ---- 大流星 ----
    /** 大流星伤害，96 点 = 48 颗心，能秒掉绝大多数精英怪但打不死坚守者 */
    private static final float BIG_DAMAGE = 96.0F;
    /** 伤害半径 = 2 倍砸坑半径，冲击波一直推到整个小流星散布区 */
    private static final double BIG_DAMAGE_RADIUS = 18.0D;
    private static final double BIG_KNOCKBACK = 2.0D;
    private static final int BIG_DESTROY_RADIUS = 9;
    /** 每一列的深度在这两个值之间随机，坑底才是坑坑洼洼而不是一块平板 */
    private static final int BIG_DESTROY_DEPTH_MIN = 4;
    private static final int BIG_DESTROY_DEPTH_MAX = 5;
    private static final int BIG_MAX_DESTROY = 3000;

    // ---- 中型流星（由词条启用）----
    private static final float MEDIUM_DAMAGE = 36.0F;
    /** 爆炸半径 3 格 */
    private static final double MEDIUM_DAMAGE_RADIUS = 3.0D;
    private static final double MEDIUM_KNOCKBACK = 1.6D;
    private static final int MEDIUM_DESTROY_RADIUS = 3;
    private static final int MEDIUM_DESTROY_DEPTH = 2;
    private static final int MEDIUM_MAX_DESTROY = 200;
    /** 中型流星排在这么多 tick 内落下，早于大流星 */
    private static final int MEDIUM_DELAY_MIN = 8;
    private static final int MEDIUM_DELAY_MAX = 34;

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

    /** 所有流星落地时与地面的夹角（度）。竖直下落是 90°，这里让它们统一斜着插下来 */
    private static final double IMPACT_ANGLE = 45.0D;

    /**
     * 水平漂移速度（格 / tick），由下落速度和倾角推出来：
     * tan(夹角) = 竖直速度 / 水平速度，所以水平 = 2.0 / tan(45°) = 2.0
     */
    private static final double DRIFT_SPEED = FALL_SPEED / Math.tan(Math.toRadians(IMPACT_ANGLE));

    /** 拖尾长度（格） */
    private static final double TRAIL_LENGTH = 8.0D;
    private static final int SMALL_TRAIL_POINTS = 9;
    private static final int BIG_TRAIL_POINTS = 16;

    /** 拖尾粒子尺寸 */
    private static final float SMALL_TRAIL_SCALE = 2.0F;
    private static final float BIG_TRAIL_SCALE = 3.6F;

    /**
     * 拖尾粒子往白色提亮的比例：0 = 色板原色，1 = 纯白。
     * dust 粒子没有自发光通道，想让尾迹“亮”只能把颜色本身往白里推。
     */
    private static final float TRAIL_WHITEN = 0.55F;

    /** 拖尾粒子在这一距离内才生成（格） */
    private static final double TRAIL_RANGE = 128.0D;

    /** 坑底铺火的覆盖率 */
    private static final float CRATER_FIRE_CHANCE = 0.6F;

    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BIG =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_MEDIUM =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_DRIFT_X =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DRIFT_Z =
            SynchedEntityData.defineId(RainbowMeteor.class, EntityDataSerializers.FLOAT);

    private double targetY;
    private UUID ownerUuid;
    private Set<Entity> damaged;

    /** 这一发流星带着的词条，在施法时定下来 */
    private List<SwordCore> cores = List.of();

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

    public void setMedium(boolean medium)
    {
        this.entityData.set(DATA_MEDIUM, medium);
    }

    public boolean isMedium()
    {
        return this.entityData.get(DATA_MEDIUM);
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

    public void setCores(List<SwordCore> cores)
    {
        this.cores = cores;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_COLOR, 0xFFFFFF);
        builder.define(DATA_BIG, false);
        builder.define(DATA_MEDIUM, false);
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
        if (this.level().getNearestPlayer(this, TRAIL_RANGE) == null)
        {
            return;
        }

        boolean big = this.isBig();
        boolean medium = !big && this.isMedium();

        int points = big ? BIG_TRAIL_POINTS : medium ? 12 : SMALL_TRAIL_POINTS;
        float baseScale = big ? BIG_TRAIL_SCALE : medium ? 2.6F : SMALL_TRAIL_SCALE;

        // 闪烁：每颗流星用自己的相位做正弦脉动，整片雨看起来是一闪一闪的
        float phase = (this.tickCount + this.getId() * 0.37F) * 0.9F;
        baseScale *= 0.7F + 0.3F * Mth.sin(phase);

        int rgb = RainbowLightning.brighten(this.getColor(), TRAIL_WHITEN);
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
            double spread = (0.12D + t * 0.4D) * (big ? 2.0D : medium ? 1.5D : 1.0D);

            this.level().addParticle(
                    new DustParticleOptions(color, baseScale * (1.0F - (float) t * 0.6F)),
                    this.getX() - vx * distance + (this.random.nextDouble() - 0.5D) * spread,
                    this.getY() - vy * distance + (this.random.nextDouble() - 0.5D) * spread,
                    this.getZ() - vz * distance + (this.random.nextDouble() - 0.5D) * spread,
                    0.0D, 0.0D, 0.0D);
        }

        // 大流星每 tick 都拖火，小流星隔一 tick；火花也跟着加密一档
        if (this.random.nextInt(big ? 1 : 2) == 0)
        {
            this.level().addParticle(ParticleTypes.FLAME,
                    this.getX(), this.getY(), this.getZ(), 0.0D, -0.1D, 0.0D);
        }

        if (this.random.nextInt(2) == 0)
        {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(), 0.0D, -0.2D, 0.0D);
        }

        // 闪烁只给中型和大流星——小流星数量太多，亮点会糊成一片，反而看不出来
        if (big || medium)
        {
            // 每几 tick 在原地炸开一圈亮白星点，肉眼能清楚看到在闪
            if (this.tickCount % (big ? 2 : 3) == 0)
            {
                int burst = big ? 10 : 6;

                for (int i = 0; i < burst; i++)
                {
                    double ox = (this.random.nextDouble() - 0.5D) * 1.6D;
                    double oy = (this.random.nextDouble() - 0.5D) * 1.6D;
                    double oz = (this.random.nextDouble() - 0.5D) * 1.6D;

                    this.level().addParticle(ParticleTypes.END_ROD,
                            this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                            0.0D, 0.0D, 0.0D);
                }
            }

            if (this.random.nextInt(2) == 0)
            {
                this.level().addParticle(ParticleTypes.FIREWORK,
                        this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }

            // 大一号的色尘光晕，让「闪」有一个实体感的核心
            if (this.tickCount % 4 == 0)
            {
                this.level().addParticle(
                        new DustParticleOptions(color, big ? 6.5F : 4.5F),
                        this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }
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
        boolean medium = !big && this.isMedium();

        int count = big ? 12 : medium ? 5 : 2;
        double spread = big ? 2.0D : medium ? 0.9D : 0.4D;

        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                this.getX(), this.getY() + 0.5D, this.getZ(),
                count, spread, spread * 0.5D, spread, 0.0D);

        // 落地闪光：火星四溅 + 一圈亮白亮点，大/中型再补一记白闪
        serverLevel.sendParticles(ParticleTypes.FIREWORK,
                this.getX(), this.getY() + 0.5D, this.getZ(),
                big ? 48 : medium ? 20 : 7, spread * 1.5D, spread, spread * 1.5D, 0.25D);

        serverLevel.sendParticles(ParticleTypes.END_ROD,
                this.getX(), this.getY() + 0.5D, this.getZ(),
                big ? 24 : medium ? 10 : 3, spread, spread * 0.6D, spread, 0.15D);

        if (big || medium)
        {
            serverLevel.sendParticles(ParticleTypes.FLASH,
                    this.getX(), this.getY() + 0.8D, this.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void damageNearby(ServerLevel serverLevel)
    {
        boolean big = this.isBig();
        boolean medium = !big && this.isMedium();

        double radius = big ? BIG_DAMAGE_RADIUS : medium ? MEDIUM_DAMAGE_RADIUS : SMALL_DAMAGE_RADIUS;
        float damage = big ? BIG_DAMAGE : medium ? MEDIUM_DAMAGE : SMALL_DAMAGE;
        double knockback = big ? BIG_KNOCKBACK : medium ? MEDIUM_KNOCKBACK : SMALL_KNOCKBACK;

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

        // 竖直范围也随半径放大：大流星是一团球形冲击波，不是一块贴着地面的薄板
        double down = big ? radius * 0.5D : 3.0D;
        double up = big ? radius * 0.7D : 4.0D;

        AABB area = new AABB(
                this.getX() - radius, this.getY() - down, this.getZ() - radius,
                this.getX() + radius, this.getY() + up, this.getZ() + radius);

        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area))
        {
            if (!(target instanceof Enemy) || !target.isAlive() || !this.damaged.add(target))
            {
                continue;
            }

            float finalDamage = damage;

            for (SwordCore core : this.cores)
            {
                finalDamage = core.meteorDamage(finalDamage, target);
            }

            if (target.hurt(source, finalDamage))
            {
                target.knockback(knockback,
                        this.getX() - target.getX(),
                        this.getZ() - target.getZ());

                // 词条在真正打中之后触发（打在无敌帧上的不算）
                if (owner != null)
                {
                    for (SwordCore core : this.cores)
                    {
                        core.onMeteorHit(serverLevel, owner, target);
                    }
                }
            }
        }
    }

    /** 砸坑并留火。跳过空气、液体、带方块实体的方块和不可破坏方块 */
    private void destroyTerrain(ServerLevel serverLevel)
    {
        boolean big = this.isBig();
        boolean medium = !big && this.isMedium();

        int radius = big ? BIG_DESTROY_RADIUS : medium ? MEDIUM_DESTROY_RADIUS : SMALL_DESTROY_RADIUS;
        int minDepth = big ? BIG_DESTROY_DEPTH_MIN : medium ? MEDIUM_DESTROY_DEPTH : SMALL_DESTROY_DEPTH;
        int maxDepth = big ? BIG_DESTROY_DEPTH_MAX : medium ? MEDIUM_DESTROY_DEPTH : SMALL_DESTROY_DEPTH;
        int maxDestroy = big ? BIG_MAX_DESTROY : medium ? MEDIUM_MAX_DESTROY : SMALL_MAX_DESTROY;

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
    public static void cast(ServerLevel level, Player player, float power, ItemStack sword)
    {
        RandomSource random = level.random;

        // 施法时把词条定下来，之后玩家换手也不影响这一发流星
        List<SwordCore> cores = SwordCores.on(sword);

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

        // 小流星之间共用一份去重集合：同一发里不会对同一个怪叠加几十次 8 点
        Set<Entity> damaged = new HashSet<>();

        int smallCount = Math.round(Mth.lerp(power, SMALL_MIN, SMALL_MAX));

        // 词条可以改散布范围、小流星数量、中型流星数量、法阵半径
        double scatter = SCATTER_RADIUS;
        int mediumCount = 0;
        double fieldRadius = MeteorPortal.RADIUS;

        for (SwordCore core : cores)
        {
            scatter = core.meteorScatterRadius(scatter);
            smallCount = core.smallMeteorCount(smallCount);
            mediumCount += core.mediumMeteorCount(random);
            fieldRadius = core.meteorFieldRadius(fieldRadius);
        }

        // 整场流星雨共用一个入射方位角：所有流星平行地斜插下来，才像同一场雨。
        // 方位取玩家视线的水平方向，法阵就会开在准星前方五十格的空中，始终在视野里；
        // 流星朝玩家这边斜插下来，而不是从背后飞过去
        Vec3 look = player.getLookAngle();
        double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
        double azimuth = lookLength < 0.05D
                ? random.nextDouble() * Math.PI * 2.0D
                : Math.atan2(look.z / lookLength, look.x / lookLength);

        float driftX = (float) (Math.cos(azimuth) * DRIFT_SPEED);
        float driftZ = (float) (Math.sin(azimuth) * DRIFT_SPEED);

        // 法阵开在大流星的出生点上，平面垂直于飞行方向。所有流星都在这个平面上出生，
        // 所以它们看上去是从法阵里飞出来的。法阵要先于流星出现，必须在这里就生成
        double fallTicks = 50.0D / FALL_SPEED;
        double speed = Math.sqrt(driftX * driftX + FALL_SPEED * FALL_SPEED + driftZ * driftZ);

        MeteorPortal.open(level,
                x - driftX * fallTicks, groundY + 50.0D, z - driftZ * fallTicks,
                driftX / speed, -FALL_SPEED / speed, driftZ / speed,
                fieldRadius);

        for (int i = 0; i < smallCount; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = scatter * Math.sqrt(random.nextDouble());

            double sx = x + Math.cos(angle) * distance;
            double sz = z + Math.sin(angle) * distance;
            int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx), Mth.floor(sz));

            schedule(level, player, damaged, cores, sx, sz, sy,
                    45.0D + random.nextDouble() * 10.0D, false, false,
                    driftX, driftZ,
                    random.nextInt(SMALL_SPREAD_TICKS + 1));
        }

        // 中型流星：和小流星一样散开，但排在大流星之前落下
        for (int i = 0; i < mediumCount; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = scatter * Math.sqrt(random.nextDouble());

            double sx = x + Math.cos(angle) * distance;
            double sz = z + Math.sin(angle) * distance;
            int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx), Mth.floor(sz));

            schedule(level, player, damaged, cores, sx, sz, sy,
                    45.0D + random.nextDouble() * 10.0D, false, true,
                    driftX, driftZ,
                    MEDIUM_DELAY_MIN + random.nextInt(MEDIUM_DELAY_MAX - MEDIUM_DELAY_MIN + 1));
        }

        // 大流星必须独立去重：小流星在 18 格内铺得极密（满蓄 96 颗、每颗 2.5 格判定），
        // 落点附近的怪几乎都被小流星标记过了。共用集合的话大流星一个也打不到
        Set<Entity> bigDamaged = new HashSet<>();
        schedule(level, player, bigDamaged, cores, x, z, groundY, 50.0D, true, false,
                driftX, driftZ, BIG_DELAY_TICKS);
    }

    private static void schedule(ServerLevel level, Player player, Set<Entity> damaged, List<SwordCore> cores,
                                 double x, double z, double groundY,
                                 double heightAbove, boolean big, boolean medium, float driftX, float driftZ, int delay)
    {
        PENDING.add(new Scheduled(level, player, damaged, cores, x, z, groundY, heightAbove,
                big, medium, driftX, driftZ, delay));
    }

    /** 一条待生成的流星记录 */
    private static final class Scheduled
    {
        final ServerLevel level;
        final Player player;
        final Set<Entity> damaged;
        final List<SwordCore> cores;
        final double x;
        final double z;
        final double groundY;
        final double heightAbove;
        final boolean big;
        final boolean medium;
        final float driftX;
        final float driftZ;
        int ticksLeft;

        Scheduled(ServerLevel level, Player player, Set<Entity> damaged, List<SwordCore> cores,
                  double x, double z, double groundY, double heightAbove,
                  boolean big, boolean medium, float driftX, float driftZ, int delay)
        {
            this.level = level;
            this.player = player;
            this.damaged = damaged;
            this.cores = cores;
            this.x = x;
            this.z = z;
            this.groundY = groundY;
            this.heightAbove = heightAbove;
            this.big = big;
            this.medium = medium;
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
                spawn(next.level, next.player, next.level.random, next.damaged, next.cores,
                        next.x, next.z, next.groundY, next.heightAbove, next.big, next.medium,
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
                              List<SwordCore> cores,
                              double x, double z, double groundY, double heightAbove,
                              boolean big, boolean medium,
                              float driftX, float driftZ)
    {
        // 从目标位置往回推：飞多少 tick，就偏移多少水平距离
        double fallTicks = heightAbove / FALL_SPEED;
        double spawnX = x - driftX * fallTicks;
        double spawnZ = z - driftZ * fallTicks;

        RainbowMeteor meteor = new RainbowMeteor(FirstSword.RAINBOW_METEOR.get(), level);
        meteor.setPos(spawnX, groundY + heightAbove, spawnZ);
        meteor.setTargetY(groundY);
        meteor.setOwner(player.getUUID());
        meteor.setDamaged(damaged);
        meteor.setCores(cores);
        meteor.setBig(big);
        meteor.setMedium(medium);
        meteor.setDrift(driftX, driftZ);
        meteor.setColor(RainbowLightning.randomColor(random));
        level.addFreshEntity(meteor);

        // 大流星：提前通知词条（落点坐标），用来做落地前的场
        if (big)
        {
            for (SwordCore core : cores)
            {
                core.onBigMeteorIncoming(level, x, groundY, z, player);
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr)
    {
        // 本体只有 0.5 格，默认可视距离按碰撞箱算是 32 格——流星从 50 格高砸下来，
        // 离远了光效会整个消失，这里放大到 160 格
        return distanceSqr < 160.0D * 160.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        // 光效比本体大得多，剔除盒要按光效算，否则屏幕边缘会提前被剔掉
        return super.getBoundingBoxForCulling().inflate(4.5D);
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
