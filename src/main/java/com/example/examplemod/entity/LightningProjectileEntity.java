package com.example.examplemod.entity;

import com.example.examplemod.init.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * Basic attack projectile of the Bolt Baron (normal form).
 * Flies in a straight line without gravity, stops at terrain, hits the first valid enemy and disappears
 * after {@link #RANGE} blocks. Rendered by LightningProjectileRenderer (model lightning_projectile.json, no shading).
 */
public class LightningProjectileEntity extends Entity {
    /** Same range as the boss' basic attack. */
    public static final double RANGE = 10.0D;
    /** Blocks per tick. */
    private static final double SPEED = 1.5D;

    private UUID ownerUUID = null;
    private double traveled = 0.0D;

    public LightningProjectileEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public LightningProjectileEntity(Level level, LivingEntity owner, Vec3 origin, Vec3 direction) {
        this(ModEntities.LIGHTNING_PROJECTILE.get(), level);
        this.ownerUUID = owner.getUUID();
        Vec3 dir = direction.normalize();
        this.setPos(origin.x, origin.y - this.getBbHeight() * 0.5D, origin.z);
        this.setDeltaMovement(dir.scale(SPEED));

        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0D / Math.PI));
        float pitch = (float) (-(Mth.atan2(dir.y, horizontal) * (180.0D / Math.PI)));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    @Override
    protected void defineSynchedData() {
    }

    private Vec3 center() {
        return this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = this.getDeltaMovement();

        if (this.level().isClientSide()) {
            // A plain Entity is not interpolated by the client: advance it by its velocity (received with the
            // spawn packet); the server's position packets only correct it. Removal comes from the server.
            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        if (!(this.ownerUUID != null && serverLevel.getEntity(this.ownerUUID) instanceof BoltBaronEntity owner) || !owner.isAlive()) {
            this.discard();
            return;
        }

        Vec3 start = this.center();
        double step = Math.min(SPEED, RANGE - this.traveled);
        Vec3 end = start.add(motion.normalize().scale(step));

        // Stops at terrain
        BlockHitResult blockHit = serverLevel.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = blockHit.getType() != HitResult.Type.MISS;
        Vec3 limit = blocked ? blockHit.getLocation() : end;

        // Same hit area as the old instant bolt: ray + 1 block candidate box, default 0.3 pick margin
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(serverLevel, this, start, limit,
                new AABB(start, limit).inflate(1.0D),
                e -> e instanceof LivingEntity le && owner.isValidProjectileTarget(le));
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity hit) {
            Vec3 p = entityHit.getLocation();
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 8, 0.2D, 0.2D, 0.2D, 0.1D);
            owner.onBasicProjectileHit(hit);
            this.discard();
            return;
        }
        if (blocked) {
            this.discard();
            return;
        }

        this.setPos(end.x, end.y - this.getBbHeight() * 0.5D, end.z);
        this.traveled += step;
        if (this.traveled >= RANGE - 1.0E-4D) {
            this.discard(); // end of range: disappears
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
        this.traveled = tag.getDouble("Traveled");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
        tag.putDouble("Traveled", this.traveled);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
