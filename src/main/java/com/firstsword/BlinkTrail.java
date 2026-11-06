package com.firstsword;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * 瞬闪斩留下的彩虹斩痕。本体不渲染，靠客户端每 tick 沿线段撒粒子。
 *
 * 实体位置就是斩痕起点，终点从同步数据里读。
 */
public class BlinkTrail extends Entity
{
    /** 存活时间（tick） */
    public static final int LIFETIME = 14;

    /** 粒子生成距离（格） */
    private static final double TRAIL_RANGE = 96.0D;

    private static final EntityDataAccessor<Float> DATA_END_X =
            SynchedEntityData.defineId(BlinkTrail.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_END_Y =
            SynchedEntityData.defineId(BlinkTrail.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_END_Z =
            SynchedEntityData.defineId(BlinkTrail.class, EntityDataSerializers.FLOAT);

    public BlinkTrail(EntityType<? extends BlinkTrail> entityType, Level level)
    {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_END_X, 0.0F);
        builder.define(DATA_END_Y, 0.0F);
        builder.define(DATA_END_Z, 0.0F);
    }

    public static void open(ServerLevel level, Vec3 start, Vec3 end)
    {
        BlinkTrail trail = new BlinkTrail(FirstSword.BLINK_TRAIL.get(), level);
        trail.setPos(start.x, start.y, start.z);
        trail.entityData.set(DATA_END_X, (float) end.x);
        trail.entityData.set(DATA_END_Y, (float) end.y);
        trail.entityData.set(DATA_END_Z, (float) end.z);
        level.addFreshEntity(trail);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (this.level().isClientSide())
        {
            if (this.tickCount <= LIFETIME)
            {
                this.spawnTrailParticles();
            }

            return;
        }

        if (this.tickCount >= LIFETIME)
        {
            this.discard();
        }
    }

    private void spawnTrailParticles()
    {
        if (this.level().getNearestPlayer(this, TRAIL_RANGE) == null)
        {
            return;
        }

        // 越往后越淡：强度和数量都按剩余时间平方衰减，斩痕像是被"抽走"
        float remain = 1.0F - Math.min(this.tickCount / (float) LIFETIME, 1.0F);
        float strength = remain * remain;

        Vec3 start = this.position();
        Vec3 end = new Vec3(
                this.entityData.get(DATA_END_X),
                this.entityData.get(DATA_END_Y),
                this.entityData.get(DATA_END_Z));
        Vec3 delta = end.subtract(start);

        int count = 6 + (int) (22.0F * strength);
        double spread = 0.35D * strength;

        for (int i = 0; i < count; i++)
        {
            Vec3 point = start.add(delta.scale(this.random.nextDouble()));

            int rgb = RainbowLightning.brighten(RainbowLightning.randomColor(this.random), 0.6F);
            Vector3f color = new Vector3f(
                    ((rgb >> 16) & 0xFF) / 255.0F,
                    ((rgb >> 8) & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F);

            this.level().addParticle(
                    new DustParticleOptions(color, 1.4F + 1.6F * strength),
                    point.x + (this.random.nextDouble() - 0.5D) * spread,
                    point.y + (this.random.nextDouble() - 0.5D) * spread,
                    point.z + (this.random.nextDouble() - 0.5D) * spread,
                    0.0D, 0.0D, 0.0D);
        }

        if (this.random.nextInt(3) == 0)
        {
            Vec3 point = start.add(delta.scale(this.random.nextDouble()));
            this.level().addParticle(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
        }
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
