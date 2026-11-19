package com.example.examplemod.entity;

import com.example.examplemod.init.ModEntities;
import com.example.examplemod.init.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ThunderOrbEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> IS_EXPLODING =
            SynchedEntityData.defineId(ThunderOrbEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation EXPLODE_ANIM = RawAnimation.begin().thenPlay("explode");

    private Vec3 startPos = Vec3.ZERO;
    private Vec3 shootDirection = Vec3.ZERO;
    private UUID ownerUUID = null;
    private float magicPower = 0.0F;
    private int explodeTicks = 0;
    /** Enemies caught on the orb's path: they ride along with it until it detonates at max range. */
    private final List<LivingEntity> carried = new ArrayList<>();
    private static final double MAX_DISTANCE = 14.0D;
    // The "explode" animation plays from the moment the orb is fired: it spins, the explosion lands at 0.54s,
    // it shrinks at 0.58s and is gone at 0.67s. The orb covers its full range in exactly 11 ticks (0.55s) so the
    // detonation (particle + sound + damage) lines up with that keyframe.
    private static final int FLIGHT_TICKS = 11;
    private static final double FLY_SPEED = MAX_DISTANCE / FLIGHT_TICKS;
    private static final int REMOVE_TICK = 14; // after scale reaches 0 at 0.667s (13.3 ticks)

    public ThunderOrbEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.setNoGravity(true);
    }

    public ThunderOrbEntity(Level level, LivingEntity owner, Vec3 direction, float magicPower) {
        this(ModEntities.THUNDER_ORB.get(), level);
        this.ownerUUID = owner.getUUID();
        this.magicPower = magicPower;
        this.shootDirection = direction.normalize();
        this.setPos(owner.getX(), owner.getY() + owner.getEyeHeight() * 0.7D, owner.getZ());
        this.startPos = this.position();
        this.setDeltaMovement(this.shootDirection.scale(FLY_SPEED));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(IS_EXPLODING, false);
    }

    public boolean isExploding() {
        return this.entityData.get(IS_EXPLODING);
    }

    public void setExploding(boolean exploding) {
        this.entityData.set(IS_EXPLODING, exploding);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isExploding()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.explodeTicks++;
            // Removed once the animation has finished shrinking the orb (client and server alike)
            if (this.tickCount >= REMOVE_TICK) {
                this.discard();
            }
            return;
        }

        // Initialize startPos if empty
        if (this.startPos.equals(Vec3.ZERO)) {
            this.startPos = this.position();
        }

        if (this.level().isClientSide()) {
            // A plain Entity is not interpolated by the client, so without this the orb would only jump to each
            // position packet (stutter at this speed). Like vanilla projectiles, the client advances it by its
            // velocity every tick (received with the spawn packet); the server's position packets just correct it.
            Vec3 motion = this.getDeltaMovement();
            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
            this.spawnTrailLines(motion);
            return;
        }

        double traveled = this.position().distanceTo(this.startPos);
        double step = Math.min(FLY_SPEED, MAX_DISTANCE - traveled);

        // Stop at terrain
        Vec3 from = this.position();
        Vec3 to = from.add(this.shootDirection.scale(step + 0.4D));
        BlockHitResult blockHit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            Vec3 stop = blockHit.getLocation().subtract(this.shootDirection.scale(0.4D));
            this.setPos(stop.x, stop.y, stop.z);
            this.dragCarriedAlong();
            this.detonate();
            return;
        }

        // Fly forward with constant velocity, no gravity
        Vec3 next = from.add(this.shootDirection.scale(step));
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(this.shootDirection.scale(FLY_SPEED));

        // Grab every enemy the orb touches along the way and carry them with it
        List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(1.0D), this::isEnemy);
        for (LivingEntity e : nearby) {
            if (!this.carried.contains(e)) {
                this.carried.add(e);
            }
        }
        this.dragCarriedAlong();

        // Reached max range (14 blocks): explode
        if (traveled + step >= MAX_DISTANCE - 1.0E-3D) {
            this.detonate();
        }
    }

    // Trail: streaks left behind by the flying orb (client side, so they sit exactly on the orb's smooth path)
    private static final int TRAIL_CYAN_LINES = 3; // #61edff
    private static final int TRAIL_BLUE_LINES = 2; // #1e92c0
    private static final double TRAIL_SPREAD = 0.6D; // blocks around the orb's centre where a line can appear
    private static final double TRAIL_BACK = 1.2D; // lines can also start up to this far behind the orb

    /** Every tick of the flight: 3 cyan + 2 dark blue streaks at random spots (random size is done by the particle). */
    private void spawnTrailLines(Vec3 motion) {
        if (motion.lengthSqr() < 1.0E-6D) return;
        Vec3 dir = motion.normalize();
        double cx = this.getX();
        double cy = this.getY() + this.getBbHeight() * 0.5D;
        double cz = this.getZ();
        for (int i = 0; i < TRAIL_CYAN_LINES + TRAIL_BLUE_LINES; i++) {
            double back = this.random.nextDouble() * TRAIL_BACK;
            double x = cx - dir.x * back + (this.random.nextDouble() * 2.0D - 1.0D) * TRAIL_SPREAD;
            double y = cy - dir.y * back + (this.random.nextDouble() * 2.0D - 1.0D) * TRAIL_SPREAD;
            double z = cz - dir.z * back + (this.random.nextDouble() * 2.0D - 1.0D) * TRAIL_SPREAD;
            SimpleParticleType line = i < TRAIL_CYAN_LINES
                    ? ModParticles.THUNDER_LINE_CYAN_PARTICLE.get()
                    : ModParticles.THUNDER_LINE_BLUE_PARTICLE.get();
            // The speeds carry the streak's direction = the orb's flight direction
            this.level().addParticle(line, x, y, z, dir.x, dir.y, dir.z);
        }
    }

    private LivingEntity getOwner() {
        if (this.ownerUUID != null && this.level() instanceof ServerLevel sl) {
            Entity e = sl.getEntity(this.ownerUUID);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    private boolean isEnemy(LivingEntity e) {
        if (!e.isAlive() || e instanceof BoltBaronEntity) return false;
        if (this.ownerUUID != null && e.getUUID().equals(this.ownerUUID)) return false;
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Pull carried enemies to the orb's position so they end up exactly at the end of the skill's range. */
    private void dragCarriedAlong() {
        double cx = this.getX();
        double cy = this.getY() + this.getBbHeight() * 0.5D;
        double cz = this.getZ();
        this.carried.removeIf(e -> !e.isAlive() || e.isRemoved());
        for (LivingEntity e : this.carried) {
            double ty = cy - e.getBbHeight() * 0.5D;
            double dx = cx - e.getX(), dy = ty - e.getY(), dz = cz - e.getZ();
            if (!this.level().noCollision(e, e.getBoundingBox().move(dx, dy, dz))) {
                continue; // would end up inside a wall, skip this tick
            }
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0.0F;
            if (e instanceof ServerPlayer sp) {
                sp.connection.teleport(cx, ty, cz, sp.getYRot(), sp.getXRot());
            } else {
                e.moveTo(cx, ty, cz, e.getYRot(), e.getXRot());
            }
            e.hurtMarked = true;
        }
    }

    private void detonate() {
        if (this.isExploding()) return;
        this.setExploding(true);
        this.setDeltaMovement(Vec3.ZERO);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.THUNDER_ORB_EXPLOSION_PARTICLE.get(),
                    this.getX(), this.getY() + 0.5D, this.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);

            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 1.1F);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.2F, 1.4F);

            // Explosion 4x4 blocks
            AABB explodeBox = AABB.ofSize(this.getBoundingBox().getCenter(), 4.0D, 4.0D, 4.0D);
            List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, explodeBox, this::isEnemy);

            LivingEntity owner = this.getOwner();
            float damage = 25.0F + 0.3F * this.magicPower;
            for (LivingEntity target : targets) {
                target.invulnerableTime = 0;
                target.hurt(this.damageSources().indirectMagic(this, owner), damage);
                // Stun target for 0.5s (10 ticks)
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 255, false, false));
            }
        }
        this.carried.clear();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("Owner")) {
            this.ownerUUID = compound.getUUID("Owner");
        }
        this.magicPower = compound.getFloat("MagicPower");
        this.explodeTicks = compound.getInt("ExplodeTicks");
        this.setExploding(compound.getBoolean("IsExploding"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.ownerUUID != null) {
            compound.putUUID("Owner", this.ownerUUID);
        }
        compound.putFloat("MagicPower", this.magicPower);
        compound.putInt("ExplodeTicks", this.explodeTicks);
        compound.putBoolean("IsExploding", this.isExploding());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // The whole "explode" animation (spin -> explosion -> shrink -> vanish) plays once from the moment of the shot.
        controllers.add(new AnimationController<>(this, "controller", 0,
                state -> state.setAndContinue(EXPLODE_ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
