package com.thefirstmod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 流星从里面飞出来的空中法阵。纯视觉，不参与任何判定。
 *
 * 法阵摆在「流星生成平面」的中心——也就是大流星的出生点，平面垂直于飞行方向。
 * 所有流星都在这个平面上出生，所以看起来像是从法阵里钻出来的。
 */
public class MeteorPortal extends Entity
{
    /** 存活时间（tick），60 = 3 秒。小流星在第 0~37 tick 陆续生成，大流星第 40 tick */
    public static final int LIFETIME = 60;

    /** 法阵半径（格）。要罩住小流星 ±18 的散布和出生高度的浮动 */
    public static final float RADIUS = 24.0F;

    private static final EntityDataAccessor<Float> DATA_DIR_X =
            SynchedEntityData.defineId(MeteorPortal.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Y =
            SynchedEntityData.defineId(MeteorPortal.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Z =
            SynchedEntityData.defineId(MeteorPortal.class, EntityDataSerializers.FLOAT);

    public MeteorPortal(EntityType<? extends MeteorPortal> entityType, Level level)
    {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_DIR_X, 0.0F);
        builder.define(DATA_DIR_Y, -1.0F);
        builder.define(DATA_DIR_Z, 0.0F);
    }

    public float dirX()
    {
        return this.entityData.get(DATA_DIR_X);
    }

    public float dirY()
    {
        return this.entityData.get(DATA_DIR_Y);
    }

    public float dirZ()
    {
        return this.entityData.get(DATA_DIR_Z);
    }

    /**
     * 开启法阵。
     *
     * @param x/y/z     流星生成平面的中心（大流星的出生点）
     * @param dirX/Y/Z  流星的飞行方向，法阵平面垂直于它
     */
    public static void open(ServerLevel level, double x, double y, double z,
                            double dirX, double dirY, double dirZ)
    {
        MeteorPortal portal = new MeteorPortal(TheFirstMod.METEOR_PORTAL.get(), level);
        portal.setPos(x, y, z);
        portal.entityData.set(DATA_DIR_X, (float) dirX);
        portal.entityData.set(DATA_DIR_Y, (float) dirY);
        portal.entityData.set(DATA_DIR_Z, (float) dirZ);
        level.addFreshEntity(portal);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (!this.level().isClientSide() && this.tickCount >= LIFETIME)
        {
            this.discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr)
    {
        // 法阵可能开在离玩家一百多格的空中，默认按碰撞箱算的可视距离是 0，必须自己给
        return distanceSqr < 192.0D * 192.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        // 实体本体是 0 尺寸，但法阵直径 48 格；按本体算会被视锥剔除掉
        return super.getBoundingBoxForCulling().inflate(RADIUS);
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
