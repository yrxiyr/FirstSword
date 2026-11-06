package com.firstsword;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 冲刺时留下的一道刀光。纯视觉，本体不渲染——几何全在 {@code SlashArcRenderer} 里程序化生成。
 *
 * 位置是刀光的圆心，法线（axis）决定它躺在哪个平面上，yRot 既当平面内的起始角，也当色相偏移。
 */
public class SlashArc extends Entity
{
    /** 存活时间（tick），8 tick = 0.4 秒 */
    public static final int LIFETIME = 8;

    /** 弧线半径（格） */
    public static final float RADIUS = 1.5F;

    private static final EntityDataAccessor<Float> DATA_AXIS_X =
            SynchedEntityData.defineId(SlashArc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Y =
            SynchedEntityData.defineId(SlashArc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Z =
            SynchedEntityData.defineId(SlashArc.class, EntityDataSerializers.FLOAT);

    public SlashArc(EntityType<? extends SlashArc> entityType, Level level)
    {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_AXIS_X, 0.0F);
        builder.define(DATA_AXIS_Y, 0.0F);
        builder.define(DATA_AXIS_Z, 1.0F);
    }

    public float axisX()
    {
        return this.entityData.get(DATA_AXIS_X);
    }

    public float axisY()
    {
        return this.entityData.get(DATA_AXIS_Y);
    }

    public float axisZ()
    {
        return this.entityData.get(DATA_AXIS_Z);
    }

    /**
     * 开一道刀光。
     *
     * @param at   弧线的圆心
     * @param axis 刀光所在平面的法线，随便给一个非零向量就行
     * @param roll 弧线在平面里的起始角（度），同时决定色相
     */
    public static void open(ServerLevel level, Vec3 at, Vec3 axis, float roll)
    {
        SlashArc arc = new SlashArc(FirstSword.SLASH_ARC.get(), level);
        arc.setPos(at.x, at.y, at.z);
        arc.entityData.set(DATA_AXIS_X, (float) axis.x);
        arc.entityData.set(DATA_AXIS_Y, (float) axis.y);
        arc.entityData.set(DATA_AXIS_Z, (float) axis.z);
        arc.setYRot(roll);
        level.addFreshEntity(arc);
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
        // 实体尺寸是 0，默认按碰撞箱算的可视距离恒为 0，必须自己给
        return distanceSqr < 128.0D * 128.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        // 同样因为尺寸是 0，默认的剔除盒是个点，刀光会被视锥剔除掉
        return super.getBoundingBoxForCulling().inflate(RADIUS + 1.0F);
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
