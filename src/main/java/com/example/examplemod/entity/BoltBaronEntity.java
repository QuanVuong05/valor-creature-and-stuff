package com.example.examplemod.entity;

import com.example.examplemod.init.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import java.util.Set;

public class BoltBaronEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> IS_AWAKENED =
            SynchedEntityData.defineId(BoltBaronEntity.class, EntityDataSerializers.BOOLEAN);
    /** Current shield, synced to clients so the custom boss bar can draw it. */
    /** Ticks since the ultimate started (0 = not in ultimate): synced so the client hitbox follows the lifted model. */
    private static final EntityDataAccessor<Integer> DATA_ULTIMATE_TICK =
            SynchedEntityData.defineId(BoltBaronEntity.class, EntityDataSerializers.INT);
    private int dimTick = 0;
    private static final double ULTIMATE_BOLT_BASE_HEIGHT = 3.2D * 0.85D * 0.7D; // bolt start height above the feet (no lift)
    /** Awaken form floats above the ground (idle_awaken root +8..10 px): its hitbox is extended upwards by this much. */
    private static final float AWAKEN_EXTRA_HEIGHT = 0.6F;
    private static final EntityDataAccessor<Float> DATA_SHIELD =
            SynchedEntityData.defineId(BoltBaronEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Boss bar is drawn client-side by BoltBaronBossBarOverlay (custom texture), no vanilla ServerBossEvent.

    // Animations matching bolt_baron & bolt_baron_awaken
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    // The awaken file uses its own names (*_awaken). GeckoLib keeps playing the already-baked animation of the old
    // model when the raw animation is unchanged, so without distinct names the awaken form kept the normal idle.
    private static final RawAnimation IDLE_AWAKEN_ANIM = RawAnimation.begin().thenLoop("idle_awaken");
    private static final RawAnimation WALK_AWAKEN_ANIM = RawAnimation.begin().thenLoop("walk_awaken");
    private static final RawAnimation ATTACK_1_AWAKEN_ANIM = RawAnimation.begin().thenPlay("attack_1_awaken");
    private static final RawAnimation ATTACK_2_AWAKEN_ANIM = RawAnimation.begin().thenPlay("attack_2_awaken");
    private static final RawAnimation ATTACK_3_AWAKEN_ANIM = RawAnimation.begin().thenPlay("attack_3_awaken");
    private static final RawAnimation DASH_ANIM = RawAnimation.begin().thenPlay("dash");
    // The awaken file has its own dash under a distinct name so the controller can never reuse the normal one
    private static final RawAnimation DASH_AWAKEN_ANIM = RawAnimation.begin().thenPlay("dash_awaken");
    private static final RawAnimation ATTACK_1_ANIM = RawAnimation.begin().thenPlay("attack_1");
    private static final RawAnimation ATTACK_2_ANIM = RawAnimation.begin().thenPlay("attack_2");
    private static final RawAnimation ATTACK_3_ANIM = RawAnimation.begin().thenPlay("attack_3");
    private static final RawAnimation BLAST_ANIM = RawAnimation.begin().thenPlay("blast");
    private static final RawAnimation ULTIMATE_ANIM = RawAnimation.begin().thenPlay("ultimate");
    private static final RawAnimation ULTIMATE_END_ANIM = RawAnimation.begin().thenPlay("ultimate_end");

    private static final UUID ULT_SPEED_UUID = UUID.fromString("5b1f1c2e-7a3d-4f55-9a0e-b017ba10a001");
    private static final double ULTIMATE_BOLT_HEIGHT = 30.0D; // height (blocks) of the ultimate finale particle
    private static final double ULTIMATE_SHOCKWAVE_RADIUS = 20.0D; // = ultimate range
    private static final int SHOCKWAVE_DELAY_TICKS = 2; // shockwave appears 0.1s after the finale bolt
    private int shockwaveDelay = 0;
    private Vec3 shockwavePos = Vec3.ZERO;
    private static final double BASIC_RANGE = 10.0D;
    private static final double AWAKEN_SPARK_SCALE = 2.0D; // awaken chain lightning squares are 2x bigger
    private static final int SHOCK_INTERVAL_TICKS = 5; // 0.25s between two ultimate shocks
    private static final int SHOCK_COUNT = 5;
    private static final int SHOCK_FIRST_TIMER = 28; // ultimateTimer value of the first shock (0.4s after the start)
    private static final double SPARK_SPACING = 0.12D; // distance (blocks) between two squares of a bolt
    private static final double EMPOWERED_RANGE = 16.0D;
    /** Below this distance the boss prefers to use a skill to push / get away from its target. */
    private static final double KEEP_DISTANCE = 10.0D;
    /** Pause between two keep-distance casts so the first one has time to take effect (0.5s). */
    private static final int KEEP_DISTANCE_DELAY_TICKS = 10;
    private int keepDistanceDelay = 0;

    // Timers & States
    private boolean hasTriggeredAwaken = false;
    /**
     * The ultimate animation is 2.0s (40 ticks) but the finale (damage, particle, model swap) fires at 35 ticks
     * (1.75s). The boss then stays frozen for 6 more ticks so nothing else plays until the animation is over.
     */
    private static final int POST_ULTIMATE_LOCK_TICKS = 6;
    private int postUltimateLock = 0;
    /** Delay between the start of the basic attack animation and the shot: 0.35s. */
    private static final int BASIC_ATTACK_DELAY_TICKS = 7;
    private int pendingBasicTicks = 0;
    private UUID pendingBasicTarget = null;
    private int ultimateTimer = -1; // 1.75s (35 ticks)
    private int skill1Cooldown = 0; // 7s (140 ticks)
    private int skill2Cooldown = 0; // 7s (140 ticks)
    private int basicAttackCooldown = 0;

    // Passive & Special States
    private int hitCounter = 0;
    private boolean hasSpecialSkillAvailable = false;
    private boolean hasEmpoweredBasicAttack = false;

    // Shield (valor_items:shield)
    private float internalShield = 0.0F;
    private int shieldTimer = 0;

    // Empowered basic attack sequence (3 successive waves)
    private int empoweredWaveTimer = -1;
    private Vec3 empoweredWaveDir = Vec3.ZERO;
    private Vec3 empoweredWaveOrigin = Vec3.ZERO;
    private boolean empoweredWaveHealed = false;
    private int lastBasicAttackAnim = -1;

    public BoltBaronEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_AWAKENED, false);
        this.entityData.define(DATA_SHIELD, 0.0F);
        this.entityData.define(DATA_ULTIMATE_TICK, 0);
    }

    public boolean isAwakened() {
        return this.entityData.get(IS_AWAKENED);
    }

    public void setAwakened(boolean awakened) {
        this.entityData.set(IS_AWAKENED, awakened);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D) // 1.5x speed
                .add(Attributes.ATTACK_DAMAGE, 30.0D)
                .add(Attributes.ARMOR, 300.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D) // 100% knockback immunity
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Complete immunity to knockback
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Stroll only when idle without target
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D) {
            @Override
            public boolean canUse() {
                return BoltBaronEntity.this.getTarget() == null && super.canUse();
            }
        });

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Retreat dash: the boss keeps facing the dash direction for the length of the dash animation (0.25s = 5 ticks). */
    private static final int DASH_TURN_HOLD_TICKS = 6;
    private int dashFaceTicks = 0;
    private float dashFaceYaw = 0.0F;

    /** Out-of-damage regeneration: after 8s without taking damage, heal 2% max health every second. */
    private static final int REGEN_DELAY_TICKS = 160;
    private static final int REGEN_INTERVAL_TICKS = 20;
    private static final float REGEN_PERCENT = 0.02F;
    private int ticksSinceDamage = 0;
    private int regenTimer = 0;

    /** AI fix: always face the target, even while chasing / using skills (except during a retreat dash). */
    @Override
    public void tick() {
        super.tick();
        // Hitbox: during the ultimate it grows upwards so it covers the model lifted by the animation (both sides)
        int ultTick = this.entityData.get(DATA_ULTIMATE_TICK);
        if (ultTick != this.dimTick) {
            this.dimTick = ultTick;
            this.refreshDimensions();
        }
        if (!this.level().isClientSide()) {
            if (this.shockwaveDelay > 0 && --this.shockwaveDelay == 0 && this.level() instanceof ServerLevel shockLevel) {
                this.spawnUltimateShockwave(shockLevel, this.shockwavePos);
            }
            if (this.pendingBasicTicks > 0) {
                if (!this.isAlive() || this.ultimateTimer > 0) {
                    this.pendingBasicTicks = 0; // cancelled
                } else if (--this.pendingBasicTicks == 0 && this.pendingBasicTarget != null
                        && ((ServerLevel) this.level()).getEntity(this.pendingBasicTarget) instanceof LivingEntity t
                        && t.isAlive()) {
                    this.fireBasicAttack(t);
                }
            }
            if (this.isAlive()) {
                if (this.ticksSinceDamage < REGEN_DELAY_TICKS) {
                    this.ticksSinceDamage++;
                } else if (this.getHealth() < this.getMaxHealth()) {
                    if (++this.regenTimer >= REGEN_INTERVAL_TICKS) {
                        this.regenTimer = 0;
                        this.heal(this.getMaxHealth() * REGEN_PERCENT);
                    }
                }
            }
            if (this.dashFaceTicks > 0) {
                this.dashFaceTicks--;
                this.setYRot(this.dashFaceYaw);
                this.setYHeadRot(this.dashFaceYaw);
                this.setYBodyRot(this.dashFaceYaw);
                return;
            }
            LivingEntity t = this.getTarget();
            if (t != null && t.isAlive()) {
                this.faceTarget(t);
            }
        } else if (this.isAwakened() && this.isAlive()) {
            this.spawnAwakenAura();
        }
    }

    // Awaken form aura: arcs of electricity flashing at random spots on the boss's model
    private static final int AURA_ARCS_PER_TICK = 2;
    // The awaken model floats above the entity's feet: idle_awaken / walk_awaken lift the root bone by 8-10 px
    // (0.5-0.625 block). The body of the model (legs to top of the head) is 60 px = 3.75 blocks tall.
    private static final double AURA_MODEL_HOVER = 0.55D;
    private static final double AURA_MODEL_HEIGHT = 3.75D;
    private static final double AURA_RADIUS = 0.8D; // roughly the width of the body with the arms

    /** Client side only (the awakened flag is synced): spawns the electric arcs of the aura on the model. */
    private void spawnAwakenAura() {
        for (int i = 0; i < AURA_ARCS_PER_TICK; i++) {
            double a = this.random.nextDouble() * (Math.PI * 2.0D);
            double r = AURA_RADIUS * (0.7D + this.random.nextDouble() * 0.6D); // around / on the surface of the body
            double x = this.getX() + Math.cos(a) * r;
            double y = this.getY() + AURA_MODEL_HOVER + AURA_MODEL_HEIGHT * (0.05D + this.random.nextDouble() * 0.9D);
            double z = this.getZ() + Math.sin(a) * r;
            // xSpeed carries this entity's id so the arcs follow the boss while it moves
            this.level().addParticle(ModParticles.ELECTRIC_AURA_PARTICLE.get(), x, y, z, this.getId(), 0.0D, 0.0D);
        }
    }

    /** Instantly turns the boss to face {@code dir} (no interpolation) and keeps it that way for a few ticks. */
    private void turnInstantly(Vec3 dir, int holdTicks) {
        if (dir.x * dir.x + dir.z * dir.z < 1.0E-6D) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        this.dashFaceYaw = yaw;
        this.dashFaceTicks = holdTicks;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.yRotO = yaw;
        this.yHeadRotO = yaw;
        this.yBodyRotO = yaw;
    }

    private void faceTarget(LivingEntity t) {
        double dx = t.getX() - this.getX();
        double dz = t.getZ() - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) return;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    private boolean isValidEnemy(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof BoltBaronEntity) return false;
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Magic damage attributed to the Baron; clears i-frames so multi-hit skills really hit every time. */
    private void dealMagic(LivingEntity victim, float amount) {
        victim.invulnerableTime = 0;
        victim.hurt(this.damageSources().indirectMagic(this, this), amount);
    }

    private List<LivingEntity> enemiesInRange(double range) {
        return this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range),
                e -> this.isValidEnemy(e) && this.distanceTo(e) <= range);
    }

    /** Shield amount as known on both sides (synced entity data). */
    public float getSyncedShield() {
        return this.entityData.get(DATA_SHIELD);
    }

    private void syncShieldAttribute() {
        this.entityData.set(DATA_SHIELD, Math.max(0.0F, this.internalShield));
        Attribute shieldAttr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("valor_items", "shield"));
        if (shieldAttr != null && this.getAttributes().hasAttribute(shieldAttr)) {
            this.getAttribute(shieldAttr).setBaseValue(this.internalShield);
        }
    }

    private void setUltimateSpeedBoost(boolean on) {
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(ULT_SPEED_UUID) != null;
        if (on && !has) {
            speed.addTransientModifier(new AttributeModifier(ULT_SPEED_UUID, "bolt_baron_ult_speed",
                    0.30D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!on && has) {
            speed.removeModifier(ULT_SPEED_UUID);
        }
    }

    public double getMagicPower() {
        Attribute magicAttr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("valor_items", "magic_power"));
        if (magicAttr != null && this.getAttributes().hasAttribute(magicAttr)) {
            return this.getAttributeValue(magicAttr);
        }
        return 30.0D; // Default magic power
    }

    public void addShield(float amount, int durationTicks) {
        this.internalShield += amount;
        this.shieldTimer = Math.max(this.shieldTimer, durationTicks);
        this.setAbsorptionAmount(this.internalShield);
        this.syncShieldAttribute();
    }

    /** No fall damage in awaken form, and during / right after the ultimate (the landing after the flight). */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions base = super.getDimensions(pose);
        float extra = this.isAwakened() ? AWAKEN_EXTRA_HEIGHT : 0.0F;
        if (this.dimTick > 0) {
            extra += (float) Math.max(0.0D, ultimateModelLift(this.dimTick * 0.05D));
        }
        return extra > 0.0F ? EntityDimensions.scalable(base.width, base.height + extra) : base;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (IS_AWAKENED.equals(key)) {
            this.refreshDimensions(); // the awaken hitbox is taller
        }
    }

    private boolean isFallImmune() {
        return this.isAwakened() || this.ultimateTimer > 0 || this.postUltimateLock > 0;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (this.isFallImmune()) {
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    /**
     * Height (blocks) of the model above the entity during the ultimate animation. The animation only moves the model
     * (root bone), the entity itself stays on the ground, so the bolts have to be lifted by the same amount.
     * Mirrors the root position keyframes of the "ultimate" animation: 0 -> 128 px (linear, 1.3333s), hold until
     * 1.4167s, then down to -6 px (easeInBack) at 1.5833s.
     */
    private static double ultimateModelLift(double t) {
        final double up = 128.0D / 16.0D;
        final double down = -6.0D / 16.0D;
        if (t <= 0.0D) return 0.0D;
        if (t <= 1.3333D) return up * (t / 1.3333D);
        if (t <= 1.4167D) return up;
        if (t < 1.5833D) {
            double p = (t - 1.4167D) / (1.5833D - 1.4167D);
            double c1 = 1.70158D;
            double ease = (c1 + 1.0D) * p * p * p - c1 * p * p; // easeInBack
            return up + (down - up) * ease;
        }
        return down;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL) && this.isFallImmune()) {
            return false;
        }

        if (source.is(DamageTypes.LIGHTNING_BOLT) || source.getDirectEntity() instanceof LightningBolt) {
            return false;
        }

        // Damage absorbed by shield first
        if (this.internalShield > 0.0F) {
            if (this.internalShield >= amount) {
                this.internalShield -= amount;
                amount = 0.0F;
            } else {
                amount -= this.internalShield;
                this.internalShield = 0.0F;
            }
            this.setAbsorptionAmount(this.internalShield);
            this.syncShieldAttribute();
            if (amount <= 0.0F) {
                this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 1.2F);
                return false;
            }
        }

        boolean damaged = super.hurt(source, amount);
        if (damaged) {
            // Real damage taken (not absorbed by the shield, not immune): stop regenerating and restart the 8s timer
            this.ticksSinceDamage = 0;
            this.regenTimer = 0;
        }
        return damaged;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        // Shield countdown
        if (this.shieldTimer > 0) {
            this.shieldTimer--;
            if (this.shieldTimer == 0) {
                this.internalShield = 0.0F;
                this.setAbsorptionAmount(0.0F);
                this.syncShieldAttribute();
            }
        }

        // Skill cooldowns
        if (this.skill1Cooldown > 0) this.skill1Cooldown--;
        if (this.skill2Cooldown > 0) this.skill2Cooldown--;
        if (this.basicAttackCooldown > 0) this.basicAttackCooldown--;

        // Ultimate Trigger: at <= 50% HP, exactly ONCE
        if (!this.hasTriggeredAwaken && this.getHealth() <= this.getMaxHealth() * 0.5F) {
            this.hasTriggeredAwaken = true;
            this.ultimateTimer = 36; // 35 ticks (1.75s) after this tick: finale particle + model swap only happen then
            this.empoweredWaveTimer = -1;
            this.noPhysics = true;   // Walk through terrain for 1.5s
            this.setNoGravity(true);
            this.setUltimateSpeedBoost(true); // +30% speed for 1.5s
            this.triggerAnim("attack", "ultimate");
            this.getNavigation().stop();
            this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.8F);
        }

        // Handle Ultimate Skill (1.5s duration)
        if (this.ultimateTimer > 0) {
            this.tickUltimate();
            return;
        }

        // Frozen until the ultimate animation has completely finished: no movement, no skills, no other animation
        if (this.postUltimateLock > 0) {
            this.postUltimateLock--;
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            if (this.postUltimateLock == POST_ULTIMATE_LOCK_TICKS - 1) {
                // One tick after the model swap: hand the controller over to the new model with the pose the
                // ultimate animation ends on (ultimate_end holds that final pose), so the new model's bones are
                // driven right away and the animation stays seamless until the 2.0s are over.
                this.triggerAnim("attack", "ultimate_end");
            }
            return;
        }

        // Handle Empowered Basic Attack wave sequence (3 successive waves)
        if (this.empoweredWaveTimer > 0) {
            this.tickEmpoweredWaves();
            return;
        }

        // Combat Target AI
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        // AI Fix: lock gaze/body onto the target right now (tick() keeps it locked afterwards)
        if (this.dashFaceTicks <= 0) {
            this.faceTarget(target);
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        double dist = this.distanceTo(target);
        boolean isEmpowered = this.hasEmpoweredBasicAttack;
        double basicRange = isEmpowered ? EMPOWERED_RANGE : BASIC_RANGE;

        boolean canUseSkill1 = this.hasSpecialSkillAvailable || this.skill1Cooldown <= 0;
        boolean canUseSkill2 = this.hasSpecialSkillAvailable || this.skill2Cooldown <= 0;

        // Keep-distance mechanic: while the target is closer than KEEP_DISTANCE the boss prefers to use a skill
        // to get away from it, using whichever skill is available:
        //  - Skill 2 (Thunder Orb): carries the enemy 14 blocks away with the orb, pushing it away from the boss
        //  - Skill 1 (Dash): dashes straight away from the target (only if there is room to retreat)
        // If no skill is available the normal behaviour below applies (stand and attack).
        if (this.keepDistanceDelay > 0) {
            this.keepDistanceDelay--;
        } else if (dist < KEEP_DISTANCE) {
            if (canUseSkill2) {
                this.executeSkill2(target);
                this.keepDistanceDelay = KEEP_DISTANCE_DELAY_TICKS;
                return;
            }
            if (canUseSkill1) {
                Vec3 away = this.retreatDirection(target);
                if (this.dashDestination(this.position(), away).distanceTo(this.position()) >= 3.0D) {
                    this.turnInstantly(away, DASH_TURN_HOLD_TICKS); // turn around, dash away, then face the target again
                    this.executeSkill1(away);
                    this.keepDistanceDelay = KEEP_DISTANCE_DELAY_TICKS;
                    return;
                }
            }
        }

        if (dist > basicRange) {
            // Target is outside basic range
            double excess = dist - basicRange;

            if (excess < 4.0D) {
                // Out of range by 1-3 blocks: prioritize Skill 2 (or special Skill 2)
                if (canUseSkill2) {
                    this.executeSkill2(target);
                    return;
                } else if (canUseSkill1) {
                    this.executeSkill1(target);
                    return;
                }
            } else {
                // Out of range by >= 4 blocks: prioritize Skill 1 (or special Skill 1) to chase
                if (canUseSkill1) {
                    this.executeSkill1(target);
                    return;
                } else if (canUseSkill2) {
                    this.executeSkill2(target);
                    return;
                }
            }

            // Skills on cooldown: walk toward target
            this.getNavigation().moveTo(target, 1.25D);
        } else {
            // Target is within basic range: stand and attack
            this.getNavigation().stop();
            if (this.basicAttackCooldown <= 0) {
                if (isEmpowered) {
                    this.executeEmpoweredBasicAttack(target);
                    this.basicAttackCooldown = 25;
                } else {
                    this.executeBasicAttack(target);
                    this.basicAttackCooldown = 20; // 1.0s cooldown
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Ultimate
    // ------------------------------------------------------------------
    private void tickUltimate() {
        this.ultimateTimer--;
        this.entityData.set(DATA_ULTIMATE_TICK, Math.max(0, 36 - this.ultimateTimer));
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setUltimateSpeedBoost(true);
        this.getNavigation().stop();

        // Fly toward target (phasing through terrain), speed already includes +30%
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5D, 0)
                    .subtract(this.position().add(0, this.getBbHeight() * 0.5D, 0));
            if (to.lengthSqr() > 2.25D) {
                Vec3 v = to.normalize().scale(0.35D * 1.3D);
                this.setDeltaMovement(v);
            } else {
                this.setDeltaMovement(Vec3.ZERO);
            }
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }

        // 5 shocks, one every 0.25s (timers 28, 23, 18, 13, 8): each shock gives a shield (6% max health) and strikes ONE
        // random target in range with chain lightning (same look as the awaken basic attack). The target is picked
        // again for every shock, so the same target can be shocked more than once.
        int shockIndex = (SHOCK_FIRST_TIMER - this.ultimateTimer) / SHOCK_INTERVAL_TICKS;
        if (this.ultimateTimer <= SHOCK_FIRST_TIMER && (SHOCK_FIRST_TIMER - this.ultimateTimer) % SHOCK_INTERVAL_TICKS == 0
                && shockIndex < SHOCK_COUNT && this.level() instanceof ServerLevel serverLevel) {
            this.addShield(this.getMaxHealth() * 0.06F, 80);

            List<LivingEntity> enemies = this.enemiesInRange(20.0D);
            if (!enemies.isEmpty()) {
                LivingEntity enemy = enemies.get(this.random.nextInt(enemies.size()));
                this.dealMagic(enemy, 10.0F + 0.10F * (float) this.getMagicPower());
                // The ultimate animation lifts the model: start the bolt from the model, not from the entity's feet
                double lift = ultimateModelLift((36 - this.ultimateTimer) * 0.05D);
                Vec3 boltOrigin = this.position().add(0.0D, ULTIMATE_BOLT_BASE_HEIGHT + lift, 0.0D);
                Vec3 impact = enemy.position().add(0.0D, enemy.getBbHeight() * 0.5D, 0.0D);
                this.drawLightningBolt(serverLevel, boltOrigin, impact, true);
            }
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.2F, 1.5F);
        }

        // End of the ultimate: finale strike & transform to Awaken form
        if (this.ultimateTimer == 0) {
            // Back to the normal hitbox before the terrain unstuck check
            this.entityData.set(DATA_ULTIMATE_TICK, 0);
            this.dimTick = 0;
            this.refreshDimensions();
            this.noPhysics = false;
            this.setNoGravity(false);
            this.setDeltaMovement(Vec3.ZERO);
            this.setUltimateSpeedBoost(false);
            this.resolveTerrainStuck();

            if (this.level() instanceof ServerLevel serverLevel) {
                float finaleDamage = 40.0F + 0.40F * (float) this.getMagicPower();
                for (LivingEntity enemy : this.enemiesInRange(20.0D)) {
                    this.dealMagic(enemy, finaleDamage);
                }

                // One huge lightning_bolt particle (30 blocks tall) at the boss's feet instead of vanilla bolts.
                // With count = 0 the offsets become the particle's velocity, which carries the height (xSpeed).
                for (ServerPlayer viewer : serverLevel.players()) {
                    if (viewer.distanceToSqr(this) <= 128.0D * 128.0D) {
                        serverLevel.sendParticles(viewer, ModParticles.LIGHTNING_BOLT_PARTICLE.get(), true,
                                this.getX(), this.getY(), this.getZ(), 0, ULTIMATE_BOLT_HEIGHT, 0.0D, 0.0D, 1.0D);
                    }
                }

                // The shockwave (ring + dust) follows the 30 block bolt after SHOCKWAVE_DELAY_TICKS: see tick()
                this.shockwavePos = this.position();
                this.shockwaveDelay = SHOCKWAVE_DELAY_TICKS;
                serverLevel.setWeatherParameters(0, 6000, true, true);
            }

            this.setAwakened(true);
            this.postUltimateLock = POST_ULTIMATE_LOCK_TICKS;
            this.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 4.0F, 0.7F);
        }
    }

    /**
     * Ground shockwave ring (radius carried by xSpeed) plus dust thrown outwards. Both are sent on the same tick, to the
     * same players (within 128 blocks, bypassing the particle limiter), so they always appear together. Visual only.
     */
    private void spawnUltimateShockwave(ServerLevel level, Vec3 pos) {
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(pos.x, pos.y, pos.z) <= 128.0D * 128.0D) {
                level.sendParticles(viewer, ModParticles.EXPLOSION_RING_PARTICLE.get(), true,
                        pos.x, pos.y, pos.z, 0, ULTIMATE_SHOCKWAVE_RADIUS, 0.0D, 0.0D, 1.0D);
            }
        }
        // count = 0: the offsets are the particle's velocity. Each puff is a random one of the 3 dust particles.
        for (int i = 0; i < 24; i++) {
            double a = (i + this.random.nextDouble()) / 24.0D * (Math.PI * 2.0D);
            double speed = 0.5D + this.random.nextDouble() * 0.5D;
            this.sendShockwaveDust(level, pos.x, pos.y + 0.2D, pos.z, Math.cos(a), 0.05D, Math.sin(a), speed);
        }
    }

    /** Sends one random dust particle to every player within 128 blocks (like the ring, so far viewers see it too). */
    private void sendShockwaveDust(ServerLevel level, double x, double y, double z,
                                   double dx, double dy, double dz, double speed) {
        SimpleParticleType dust = switch (this.random.nextInt(3)) {
            case 0 -> ModParticles.DUST_PARTICLE.get();
            case 1 -> ModParticles.DUST_MEDIUM_PARTICLE.get();
            default -> ModParticles.DUST_SMALL_PARTICLE.get();
        };
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(x, y, z) <= 128.0D * 128.0D) {
                level.sendParticles(viewer, dust, true, x, y, z, 0, dx, dy, dz, speed);
            }
        }
    }

    /** Unstuck logic after 1.5s ultimate noclip: teleport to nearest open space */
    private void resolveTerrainStuck() {
        if (this.level().noCollision(this, this.getBoundingBox())) {
            return;
        }
        BlockPos current = this.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int h = Mth.ceil(this.getBbHeight());
        for (int r = 1; r <= 12 && best == null; r++) {
            for (BlockPos pos : BlockPos.betweenClosed(current.offset(-r, -r, -r), current.offset(r, r, r))) {
                double d = pos.distSqr(current);
                if (d >= bestDist) continue;
                AABB box = this.getBoundingBox().move(
                        pos.getX() + 0.5D - this.getX(), pos.getY() - this.getY(), pos.getZ() + 0.5D - this.getZ());
                if (this.level().noCollision(this, box)) {
                    best = pos.immutable();
                    bestDist = d;
                }
            }
        }
        if (best != null) {
            this.teleportTo(best.getX() + 0.5D, best.getY(), best.getZ() + 0.5D);
        }
    }

    // ------------------------------------------------------------------
    // Skill 1 - Dash
    // ------------------------------------------------------------------
    /** Where a dash from {@code start} along {@code dir} ends: 8 blocks, passing through terrain < 8 blocks thick. */
    private Vec3 dashDestination(Vec3 start, Vec3 dir) {
        Vec3 intended = start.add(dir.scale(8.0D));
        Vec3 chest = start.add(0, this.getBbHeight() * 0.5D, 0);

        BlockHitResult hit = this.level().clip(new ClipContext(chest, chest.add(dir.scale(8.0D)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

        Vec3 finalDest = intended;
        if (hit.getType() != HitResult.Type.MISS) {
            // Wall in the way. If the landing spot is free the wall is < 8 blocks thick -> pass through.
            AABB landing = this.getBoundingBox().move(intended.subtract(start));
            if (!this.level().noCollision(this, landing)) {
                // Wall too thick (landing spot is still inside terrain): stop in front of it
                double back = this.getBbWidth() * 0.5D + 0.1D;
                finalDest = hit.getLocation().subtract(dir.scale(back)).subtract(0, this.getBbHeight() * 0.5D, 0);
                AABB front = this.getBoundingBox().move(finalDest.subtract(start));
                if (!this.level().noCollision(this, front)) {
                    finalDest = start;
                }
            }
        }
        return finalDest;
    }

    /** Horizontal direction pointing straight away from the target (falls back to the opposite of own facing). */
    private Vec3 retreatDirection(LivingEntity target) {
        Vec3 away = new Vec3(this.getX() - target.getX(), 0.0D, this.getZ() - target.getZ());
        if (away.lengthSqr() < 1.0E-4D) {
            return Vec3.directionFromRotation(0.0F, this.getYRot()).normalize().scale(-1.0D);
        }
        return away.normalize();
    }

    /** Dash 8 blocks along own facing (towards the target), pierce terrain <= 8 blocks thick, magic damage on path, shield, empowered attack */
    private void executeSkill1(LivingEntity target) {
        this.executeSkill1(Vec3.directionFromRotation(0.0F, this.getYRot()).normalize()); // own facing direction
    }

    /** Dash 8 blocks along {@code dir}, pierce terrain <= 8 blocks thick, magic damage on path, shield, empowered attack */
    private void executeSkill1(Vec3 dir) {
        if (this.hasSpecialSkillAvailable) {
            this.hasSpecialSkillAvailable = false; // special cast: normal cooldown is NOT touched and keeps ticking
        } else {
            this.skill1Cooldown = 140; // 7s
        }

        this.triggerAnim("attack", this.isAwakened() ? "dash_awaken" : "dash");
        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 1.6F);

        Vec3 start = this.position();
        Vec3 finalDest = this.dashDestination(start, dir);

        // Damage everything the dash passes through (before moving, using the real path)
        if (this.level() instanceof ServerLevel serverLevel) {
            float dashDamage = 25.0F + 0.30F * (float) this.getMagicPower();
            Vec3 a = start;
            Vec3 b = finalDest;
            AABB pathBox = new AABB(a, b).inflate(2.0D, 1.5D, 2.0D);
            double abx = b.x - a.x, abz = b.z - a.z;
            double lenSq = abx * abx + abz * abz;
            for (LivingEntity enemy : serverLevel.getEntitiesOfClass(LivingEntity.class, pathBox, this::isValidEnemy)) {
                double t = lenSq < 1.0E-6D ? 0.0D
                        : Mth.clamp(((enemy.getX() - a.x) * abx + (enemy.getZ() - a.z) * abz) / lenSq, 0.0D, 1.0D);
                double cx = a.x + abx * t, cz = a.z + abz * t;
                double dx = enemy.getX() - cx, dz = enemy.getZ() - cz;
                double reach = 1.0D + this.getBbWidth() * 0.5D + enemy.getBbWidth() * 0.5D;
                if (dx * dx + dz * dz <= reach * reach) {
                    this.dealMagic(enemy, dashDamage);
                }
            }

            double len = finalDest.subtract(start).length();
            Vec3 stepDir = len > 1.0E-4D ? finalDest.subtract(start).normalize() : dir;
            for (double d = 0; d <= len; d += 0.8D) {
                Vec3 p = start.add(stepDir.scale(d));
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1.0D, p.z, 3, 0.2D, 0.2D, 0.2D, 0.05D);
            }
        }

        this.teleportTo(finalDest.x, finalDest.y, finalDest.z);
        this.getNavigation().stop();

        // Gain shield scale 6% max health for 4s (80 ticks)
        this.addShield(this.getMaxHealth() * 0.06F, 80);

        // Grants empowered basic attack
        this.hasEmpoweredBasicAttack = true;
    }

    // ------------------------------------------------------------------
    // Skill 2 - Thunder Orb
    // ------------------------------------------------------------------
    private void executeSkill2(LivingEntity target) {
        if (this.hasSpecialSkillAvailable) {
            this.hasSpecialSkillAvailable = false;
        } else {
            this.skill2Cooldown = 140; // 7s
        }

        this.triggerAnim("attack", "blast");
        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 1.4F);

        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 origin = this.position().add(0, this.getEyeHeight() * 0.7D, 0);
            Vec3 targetCenter = target.position().add(0, target.getEyeHeight() * 0.5D, 0);
            Vec3 dir = targetCenter.subtract(origin).normalize();
            ThunderOrbEntity orb = new ThunderOrbEntity(serverLevel, this, dir, (float) this.getMagicPower());
            serverLevel.addFreshEntity(orb);
        }

        this.hasEmpoweredBasicAttack = true;
    }

    // ------------------------------------------------------------------
    // Basic attack (ranged lightning, 10 blocks, vanishes at max range)
    /**
     * Draws a jagged lightning bolt (not a straight line) between two points with spark particles.
     * The path is built by midpoint displacement; the main bolt also gets a few short forks.
     */
    private void drawLightningBolt(ServerLevel level, Vec3 from, Vec3 to, boolean withForks) {
        // Two independent jagged paths between the same points (visual only), so the bolt looks thicker
        this.drawLightningBoltPath(level, from, to, withForks);
        this.drawLightningBoltPath(level, from, to, withForks);
    }

    private void drawLightningBoltPath(ServerLevel level, Vec3 from, Vec3 to, boolean withForks) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 0.1D) return;

        List<Vec3> points = new ArrayList<>();
        points.add(from);
        points.add(to);
        double offset = Math.min(0.9D, length * 0.12D);
        for (int pass = 0; pass < 3; pass++) {
            List<Vec3> next = new ArrayList<>();
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = points.get(i);
                Vec3 b = points.get(i + 1);
                Vec3 mid = a.add(b).scale(0.5D).add(
                        (this.random.nextDouble() - 0.5D) * 2.0D * offset,
                        (this.random.nextDouble() - 0.5D) * 2.0D * offset,
                        (this.random.nextDouble() - 0.5D) * 2.0D * offset);
                next.add(a);
                next.add(mid);
            }
            next.add(points.get(points.size() - 1));
            points = next;
            offset *= 0.5D;
        }

        for (int i = 0; i < points.size() - 1; i++) {
            this.sparkLine(level, points.get(i), points.get(i + 1));
            // Short forks branching off the main bolt
            if (withForks && i > 0 && i < points.size() - 2 && this.random.nextInt(4) == 0) {
                Vec3 p = points.get(i);
                Vec3 forkEnd = p.add(
                        (this.random.nextDouble() - 0.5D) * 2.0D,
                        (this.random.nextDouble() - 0.5D) * 2.0D,
                        (this.random.nextDouble() - 0.5D) * 2.0D);
                Vec3 forkMid = p.add(forkEnd).scale(0.5D).add(
                        (this.random.nextDouble() - 0.5D) * 0.4D,
                        (this.random.nextDouble() - 0.5D) * 0.4D,
                        (this.random.nextDouble() - 0.5D) * 0.4D);
                this.sparkLine(level, p, forkMid);
                this.sparkLine(level, forkMid, forkEnd);
            }
        }
    }

    private void sparkLine(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-4D) return;
        Vec3 n = d.scale(1.0D / len);
        for (double t = 0.0D; t <= len; t += SPARK_SPACING) {
            Vec3 p = a.add(n.scale(t));
            // count = 0: the offsets become the particle's velocity, which carries the square's size multiplier (xSpeed)
            level.sendParticles(ModParticles.LIGHTNING_SPARK_PARTICLE.get(), p.x, p.y, p.z, 0, AWAKEN_SPARK_SCALE, 0.0D, 0.0D, 1.0D);
        }
    }

    /** Whether the projectile may hit this entity (same rule as the other attacks). */
    public boolean isValidProjectileTarget(LivingEntity e) {
        return this.isValidEnemy(e);
    }

    /** Called by the basic attack projectile (normal form) when it hits an enemy. */
    public void onBasicProjectileHit(LivingEntity hit) {
        this.applyBasicHit(hit);
    }

    /** Damage of a basic attack hit + the passive hit counter (every N hits -> one free skill cast). */
    private void applyBasicHit(LivingEntity hit) {
        float damage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) + this.getMagicPower() * 0.25D);
        this.dealMagic(hit, damage);

        // Passive: every N hits -> one special (free) cast of skill 1 or 2
        int needed = this.isAwakened() ? 3 : 6;
        this.hitCounter++;
        if (this.hitCounter >= needed) {
            this.hasSpecialSkillAvailable = true;
            this.hitCounter = 0;
            this.playSound(SoundEvents.PLAYER_LEVELUP, 1.5F, 1.5F);
        }
    }

    // ------------------------------------------------------------------
    private void executeBasicAttack(LivingEntity target) {
        this.startAttackAnimation();
        this.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.8F);
        // The bolt / projectile leaves the boss 0.35s after the animation starts (both forms)
        this.pendingBasicTicks = BASIC_ATTACK_DELAY_TICKS;
        this.pendingBasicTarget = target.getUUID();
    }

    /** Fires the basic attack (called {@link #BASIC_ATTACK_DELAY_TICKS} ticks after the animation started). */
    private void fireBasicAttack(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 origin = this.position().add(0, this.getEyeHeight() * 0.7D, 0);
        Vec3 aim = target.position().add(0, target.getEyeHeight() * 0.5D, 0);
        Vec3 dir = aim.subtract(origin).normalize();

        if (!this.isAwakened()) {
            // Normal form: fires a lightning projectile (gravity-less, 10 blocks range, stops at terrain, vanishes at the
            // end of its range). The damage and the passive are applied when it hits: see onBasicProjectileHit.
            serverLevel.addFreshEntity(new LightningProjectileEntity(serverLevel, this, origin, dir));
            return;
        }

        // Awaken form: chain lightning, not a projectile. It always hits the target when the centre of the target is
        // within BASIC_RANGE (measured from the Baron's centre), and passes through walls / blocks.
        Vec3 impact = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 bossCenter = this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D);
        boolean inRange = target.isAlive() && this.isValidEnemy(target)
                && bossCenter.distanceToSqr(impact) <= BASIC_RANGE * BASIC_RANGE;
        if (!inRange) {
            // Target moved out of range before the bolt fired: the bolt only reaches its maximum length, no hit
            this.drawLightningBolt(serverLevel, origin, origin.add(dir.scale(BASIC_RANGE)), true);
            return;
        }

        this.drawLightningBolt(serverLevel, origin, impact, true);

        LivingEntity hit = target;
        this.applyBasicHit(hit);

        // Awaken form: chain lightning, hops one target at a time, 5 blocks per hop, max 5 targets total
        if (this.isAwakened()) {
            float bounceDamage = (float) (this.getMagicPower() * 0.08D);
            Set<LivingEntity> hitEnemies = new HashSet<>();
            hitEnemies.add(this);
            hitEnemies.add(hit);

            LivingEntity source = hit;
            for (int b = 0; b < 4; b++) {
                LivingEntity next = null;
                double bestSq = 25.0D; // 5 blocks
                for (LivingEntity c : serverLevel.getEntitiesOfClass(LivingEntity.class,
                        source.getBoundingBox().inflate(5.0D),
                        e -> this.isValidEnemy(e) && !hitEnemies.contains(e))) {
                    double dSq = c.distanceToSqr(source);
                    if (dSq <= bestSq) {
                        bestSq = dSq;
                        next = c;
                    }
                }
                if (next == null) break;

                this.dealMagic(next, bounceDamage);
                hitEnemies.add(next);

                Vec3 c1 = source.position().add(0, source.getBbHeight() * 0.5D, 0);
                Vec3 c2 = next.position().add(0, next.getBbHeight() * 0.5D, 0);
                this.drawLightningBolt(serverLevel, c1, c2, false);
                source = next;
            }
        }
    }

    // ------------------------------------------------------------------
    // Empowered basic attack: 3 successive lightning waves, 5 blocks apart, 16 block range
    // ------------------------------------------------------------------
    private void executeEmpoweredBasicAttack(LivingEntity target) {
        this.hasEmpoweredBasicAttack = false;
        // Normal form plays attack_2, awaken form plays attack_3
        this.triggerAnim("attack", this.isAwakened() ? "attack_3_awaken" : "attack_2");
        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.1F);

        this.empoweredWaveTimer = 12; // waves fire at timer 8, 4, 0
        this.empoweredWaveOrigin = this.position();
        Vec3 flat = new Vec3(target.getX() - this.getX(), 0, target.getZ() - this.getZ());
        this.empoweredWaveDir = flat.lengthSqr() < 1.0E-6D ? Vec3.directionFromRotation(0, this.getYRot()) : flat.normalize();
        this.empoweredWaveHealed = false;
    }

    private void tickEmpoweredWaves() {
        this.empoweredWaveTimer--;
        this.getNavigation().stop();

        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 perp = new Vec3(-this.empoweredWaveDir.z, 0, this.empoweredWaveDir.x).normalize();
        float waveDamage = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5D + this.getMagicPower() * 0.5D);

        int wave;
        switch (this.empoweredWaveTimer) {
            case 8 -> wave = 1;
            case 4 -> wave = 2;
            case 0 -> wave = 3;
            default -> { return; }
        }

        Vec3 center = this.empoweredWaveOrigin.add(this.empoweredWaveDir.scale(5.0D * wave));
        // Each target can be hit only once per wave, even when the bolts' areas overlap
        Set<LivingEntity> hitThisWave = new HashSet<>();

        if (!this.isAwakened()) {
            // Normal form: one bolt per wave, each wave 5 blocks further
            this.strikeEmpoweredBolt(serverLevel, center, waveDamage, hitThisWave);
        } else {
            // Awaken form pattern: 1 bolt, then 2 bolts, then 3 bolts.
            // Every bolt keeps its full 6 x 6 damage area. Bolts are spaced 3 blocks apart, so the areas overlap,
            // but a target is only hit once per wave (hitThisWave) and there is no gap between bolts.
            if (wave == 1) {
                this.strikeEmpoweredBolt(serverLevel, center, waveDamage, hitThisWave);
            } else if (wave == 2) {
                this.strikeEmpoweredBolt(serverLevel, center.add(perp.scale(1.5D)), waveDamage, hitThisWave);
                this.strikeEmpoweredBolt(serverLevel, center.subtract(perp.scale(1.5D)), waveDamage, hitThisWave);
            } else {
                this.strikeEmpoweredBolt(serverLevel, center, waveDamage, hitThisWave);
                this.strikeEmpoweredBolt(serverLevel, center.add(perp.scale(3.0D)), waveDamage, hitThisWave);
                this.strikeEmpoweredBolt(serverLevel, center.subtract(perp.scale(3.0D)), waveDamage, hitThisWave);
            }
        }
    }

    /** Find the floor under (x, z), scanning down from just above the Baron's own height. */
    private double findGroundY(ServerLevel level, double x, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(this.getY()) + 2, Mth.floor(z));
        for (int i = 0; i < 16; i++) {
            if (level.getBlockState(p.below()).blocksMotion()) {
                return p.getY();
            }
            p.move(0, -1, 0);
        }
        return this.getY();
    }

    /**
     * One bolt: 6 x 6 blocks wide (aligned with the wave direction), 14 tall, 150% attack + 50% magic power,
     * heals 4% max HP once per empowered attack. Targets are assigned by their centre point.
     */
    private void strikeEmpoweredBolt(ServerLevel level, Vec3 pos, float damage, Set<LivingEntity> hitThisWave) {
        double groundY = this.findGroundY(level, pos.x, pos.z);
        Vec3 groundPos = new Vec3(pos.x, groundY, pos.z);

        // thunder_strike: 14 blocks tall (height carried by xSpeed since count = 0)
        level.sendParticles(ModParticles.THUNDER_STRIKE_PARTICLE.get(), groundPos.x, groundPos.y, groundPos.z, 0, 14.0D, 0.0D, 0.0D, 1.0D);
        level.playSound(null, groundPos.x, groundPos.y, groundPos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.2F, 1.3F);

        Vec3 dir = this.empoweredWaveDir;
        Vec3 perp = new Vec3(-dir.z, 0, dir.x).normalize();
        AABB search = new AABB(groundPos.x - 5.0D, groundPos.y, groundPos.z - 5.0D,
                groundPos.x + 5.0D, groundPos.y + 14.0D, groundPos.z + 5.0D);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, search, this::isValidEnemy)) {
            double dx = t.getX() - groundPos.x;
            double dz = t.getZ() - groundPos.z;
            double lateral = dx * perp.x + dz * perp.z;
            double along = dx * dir.x + dz * dir.z;
            if (lateral < -3.0D || lateral >= 3.0D || Math.abs(along) > 3.0D) continue;
            if (!hitThisWave.add(t)) continue; // safety: never hit twice in one wave
            this.dealMagic(t, damage);
            if (!this.empoweredWaveHealed) {
                this.heal(this.getMaxHealth() * 0.04F);
                this.empoweredWaveHealed = true;
            }
        }
    }

    public void startAttackAnimation() {
        if (!this.level().isClientSide()) {
            int attackId = this.random.nextInt(3);
            if (attackId == this.lastBasicAttackAnim) {
                attackId = (attackId + 1 + this.random.nextInt(2)) % 3;
            }
            this.lastBasicAttackAnim = attackId;
            this.triggerAnim("attack", "attack_" + (attackId + 1) + (this.isAwakened() ? "_awaken" : ""));
        }
    }

    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        this.startAttackAnimation();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("IsAwakened", this.isAwakened());
        compound.putBoolean("HasTriggeredAwaken", this.hasTriggeredAwaken);
        compound.putBoolean("HasSpecialSkillAvailable", this.hasSpecialSkillAvailable);
        compound.putBoolean("HasEmpoweredBasicAttack", this.hasEmpoweredBasicAttack);
        compound.putInt("HitCounter", this.hitCounter);
        compound.putFloat("InternalShield", this.internalShield);
        compound.putInt("ShieldTimer", this.shieldTimer);
        compound.putInt("Skill1Cooldown", this.skill1Cooldown);
        compound.putInt("Skill2Cooldown", this.skill2Cooldown);
        compound.putInt("UltimateTimer", this.ultimateTimer);
        compound.putInt("PostUltimateLock", this.postUltimateLock);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setAwakened(compound.getBoolean("IsAwakened"));
        this.hasTriggeredAwaken = compound.getBoolean("HasTriggeredAwaken");
        this.hasSpecialSkillAvailable = compound.getBoolean("HasSpecialSkillAvailable");
        this.hasEmpoweredBasicAttack = compound.getBoolean("HasEmpoweredBasicAttack");
        this.hitCounter = compound.getInt("HitCounter");
        this.internalShield = compound.getFloat("InternalShield");
        this.shieldTimer = compound.getInt("ShieldTimer");
        this.skill1Cooldown = compound.getInt("Skill1Cooldown");
        this.skill2Cooldown = compound.getInt("Skill2Cooldown");
        this.ultimateTimer = compound.contains("UltimateTimer") ? compound.getInt("UltimateTimer") : -1;
        this.postUltimateLock = compound.getInt("PostUltimateLock");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Main movement / idle controller
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            boolean awakened = this.isAwakened();
            if (state.isMoving()) {
                return state.setAndContinue(awakened ? WALK_AWAKEN_ANIM : WALK_ANIM);
            }
            return state.setAndContinue(awakened ? IDLE_AWAKEN_ANIM : IDLE_ANIM);
        }));

        // Combat / action attack controller with all animations
        controllers.add(new AnimationController<>(this, "attack", 5, state -> PlayState.STOP)
                .triggerableAnim("attack_1", ATTACK_1_ANIM)
                .triggerableAnim("attack_2", ATTACK_2_ANIM)
                .triggerableAnim("attack_3", ATTACK_3_ANIM)
                .triggerableAnim("attack_1_awaken", ATTACK_1_AWAKEN_ANIM)
                .triggerableAnim("attack_2_awaken", ATTACK_2_AWAKEN_ANIM)
                .triggerableAnim("attack_3_awaken", ATTACK_3_AWAKEN_ANIM)
                .triggerableAnim("dash", DASH_ANIM)
                .triggerableAnim("dash_awaken", DASH_AWAKEN_ANIM)
                .triggerableAnim("blast", BLAST_ANIM)
                .triggerableAnim("ultimate", ULTIMATE_ANIM)
                .triggerableAnim("ultimate_end", ULTIMATE_END_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
