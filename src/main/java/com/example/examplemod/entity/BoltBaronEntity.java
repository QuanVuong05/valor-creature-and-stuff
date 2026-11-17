package com.example.examplemod.entity;

import com.example.examplemod.init.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
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

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Custom Boss Bar
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("Bolt Baron"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10
    );

    // Animations matching bolt_baron & bolt_baron_awaken
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation DASH_ANIM = RawAnimation.begin().thenPlay("dash");
    private static final RawAnimation ATTACK_1_ANIM = RawAnimation.begin().thenPlay("attack_1");
    private static final RawAnimation ATTACK_2_ANIM = RawAnimation.begin().thenPlay("attack_2");
    private static final RawAnimation ATTACK_3_ANIM = RawAnimation.begin().thenPlay("attack_3");
    private static final RawAnimation BLAST_ANIM = RawAnimation.begin().thenPlay("blast");
    private static final RawAnimation ULTIMATE_ANIM = RawAnimation.begin().thenPlay("ultimate");

    private static final UUID ULT_SPEED_UUID = UUID.fromString("5b1f1c2e-7a3d-4f55-9a0e-b017ba10a001");
    private static final double BASIC_RANGE = 10.0D;
    private static final double EMPOWERED_RANGE = 16.0D;

    // Timers & States
    private boolean hasTriggeredAwaken = false;
    private int ultimateTimer = -1; // 1.5s (30 ticks)
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
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
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

    /** AI fix: always face the target, even while chasing / using skills. */
    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            LivingEntity t = this.getTarget();
            if (t != null && t.isAlive()) {
                this.faceTarget(t);
            }
        }
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

    private void syncShieldAttribute() {
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

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
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

        return super.hurt(source, amount);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        // Update BossBar progress
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

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
            this.ultimateTimer = 30; // 1.5 seconds (30 ticks)
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
        this.faceTarget(target);
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double dist = this.distanceTo(target);
        boolean isEmpowered = this.hasEmpoweredBasicAttack;
        double basicRange = isEmpowered ? EMPOWERED_RANGE : BASIC_RANGE;

        boolean canUseSkill1 = this.hasSpecialSkillAvailable || this.skill1Cooldown <= 0;
        boolean canUseSkill2 = this.hasSpecialSkillAvailable || this.skill2Cooldown <= 0;

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

        // First 1.0s (timers 28, 24, 20, 16, 12): shock targets 5 times & receive shield 5 times
        if (this.ultimateTimer >= 10 && this.ultimateTimer % 4 == 0 && this.level() instanceof ServerLevel serverLevel) {
            this.addShield(this.getMaxHealth() * 0.07F, 80);

            float shockDamage = 10.0F + 0.10F * (float) this.getMagicPower();
            for (LivingEntity enemy : this.enemiesInRange(20.0D)) {
                this.dealMagic(enemy, shockDamage);
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, enemy.getX(), enemy.getY() + 1.0D, enemy.getZ(),
                        12, 0.4D, 0.6D, 0.4D, 0.1D);
            }
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.2F, 1.5F);
        }

        // End of 1.5s: finale strike & transform to Awaken form
        if (this.ultimateTimer == 0) {
            this.noPhysics = false;
            this.setNoGravity(false);
            this.setDeltaMovement(Vec3.ZERO);
            this.setUltimateSpeedBoost(false);
            this.resolveTerrainStuck();

            if (this.level() instanceof ServerLevel serverLevel) {
                float finaleDamage = 40.0F + 0.40F * (float) this.getMagicPower();
                for (LivingEntity enemy : this.enemiesInRange(20.0D)) {
                    this.dealMagic(enemy, finaleDamage);
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                    if (bolt != null) {
                        bolt.moveTo(enemy.position());
                        bolt.setVisualOnly(true); // damage is already dealt above, no fire / extra damage
                        serverLevel.addFreshEntity(bolt);
                    }
                }

                LightningBolt selfBolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (selfBolt != null) {
                    selfBolt.moveTo(this.position());
                    selfBolt.setVisualOnly(true);
                    serverLevel.addFreshEntity(selfBolt);
                }
                serverLevel.setWeatherParameters(0, 6000, true, true);
            }

            this.setAwakened(true);
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            this.bossEvent.setName(Component.literal("Bolt Baron [Awakened]"));
            this.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 4.0F, 0.7F);
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
    /** Dash 8 blocks along own facing, pierce terrain <= 8 blocks thick, magic damage on path, shield, empowered attack */
    private void executeSkill1(LivingEntity target) {
        if (this.hasSpecialSkillAvailable) {
            this.hasSpecialSkillAvailable = false; // special cast: normal cooldown is NOT touched and keeps ticking
        } else {
            this.skill1Cooldown = 140; // 7s
        }

        this.triggerAnim("attack", "dash");
        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 1.6F);

        Vec3 start = this.position();
        Vec3 dir = Vec3.directionFromRotation(0.0F, this.getYRot()).normalize(); // own facing direction
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
    // ------------------------------------------------------------------
    private void executeBasicAttack(LivingEntity target) {
        this.startAttackAnimation();
        this.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.8F);

        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 origin = this.position().add(0, this.getEyeHeight() * 0.7D, 0);
        Vec3 aim = target.position().add(0, target.getEyeHeight() * 0.5D, 0);
        Vec3 dir = aim.subtract(origin).normalize();

        // Gravity-less bolt: travels in a straight line, stops at terrain, disappears after 10 blocks
        Vec3 end = origin.add(dir.scale(BASIC_RANGE));
        BlockHitResult blockHit = serverLevel.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(serverLevel, this, origin, end,
                new AABB(origin, end).inflate(1.0D),
                e -> e instanceof LivingEntity le && this.isValidEnemy(le));
        Vec3 impact = entityHit != null ? entityHit.getLocation() : end;

        double reach = origin.distanceTo(impact);
        for (double d = 0.5D; d <= reach; d += 0.5D) {
            Vec3 p = origin.add(dir.scale(d));
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05D, 0.05D, 0.05D, 0.01D);
        }

        if (entityHit == null || !(entityHit.getEntity() instanceof LivingEntity hit)) {
            return; // missed: does not count toward the passive
        }

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
                Vec3 bDir = c2.subtract(c1);
                double bLen = bDir.length();
                Vec3 bNorm = bDir.normalize();
                for (double d = 0; d < bLen; d += 0.4D) {
                    Vec3 bp = c1.add(bNorm.scale(d));
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, bp.x, bp.y, bp.z, 1, 0.02D, 0.02D, 0.02D, 0.01D);
                }
                source = next;
            }
        }
    }

    // ------------------------------------------------------------------
    // Empowered basic attack: 3 successive lightning waves, 5 blocks apart, 16 block range
    // ------------------------------------------------------------------
    private void executeEmpoweredBasicAttack(LivingEntity target) {
        this.hasEmpoweredBasicAttack = false;
        this.triggerAnim("attack", "attack_3");
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

        if (!this.isAwakened()) {
            // Normal form: one bolt per wave, each wave 5 blocks further
            this.strikeEmpoweredBolt(serverLevel, center, waveDamage);
        } else {
            // Awaken form pattern: 1 bolt, then 2 bolts, then 3 bolts (3 blocks apart)
            if (wave == 1) {
                this.strikeEmpoweredBolt(serverLevel, center, waveDamage);
            } else if (wave == 2) {
                this.strikeEmpoweredBolt(serverLevel, center.add(perp.scale(1.5D)), waveDamage);
                this.strikeEmpoweredBolt(serverLevel, center.subtract(perp.scale(1.5D)), waveDamage);
            } else {
                this.strikeEmpoweredBolt(serverLevel, center, waveDamage);
                this.strikeEmpoweredBolt(serverLevel, center.add(perp.scale(3.0D)), waveDamage);
                this.strikeEmpoweredBolt(serverLevel, center.subtract(perp.scale(3.0D)), waveDamage);
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

    /** One bolt: 3x3 area, 150% attack + 50% magic power, heals 2% max HP once per empowered attack */
    private void strikeEmpoweredBolt(ServerLevel level, Vec3 pos, float damage) {
        double groundY = this.findGroundY(level, pos.x, pos.z);
        Vec3 groundPos = new Vec3(pos.x, groundY, pos.z);

        level.sendParticles(ModParticles.LIGHTNING_BOLT_PARTICLE.get(), groundPos.x, groundPos.y, groundPos.z, 0, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, groundPos.x, groundPos.y + 0.2D, groundPos.z, 16, 0.5D, 0.3D, 0.5D, 0.2D);
        level.playSound(null, groundPos.x, groundPos.y, groundPos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.2F, 1.3F);

        AABB box = new AABB(groundPos.x - 1.5D, groundPos.y - 1.0D, groundPos.z - 1.5D,
                groundPos.x + 1.5D, groundPos.y + 3.0D, groundPos.z + 1.5D);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, this::isValidEnemy)) {
            this.dealMagic(t, damage);
            if (!this.empoweredWaveHealed) {
                this.heal(this.getMaxHealth() * 0.02F);
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
            this.triggerAnim("attack", "attack_" + (attackId + 1));
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
        if (this.isAwakened()) {
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            this.bossEvent.setName(Component.literal("Bolt Baron [Awakened]"));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Main movement / idle controller
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }));

        // Combat / action attack controller with all animations
        controllers.add(new AnimationController<>(this, "attack", 5, state -> PlayState.STOP)
                .triggerableAnim("attack_1", ATTACK_1_ANIM)
                .triggerableAnim("attack_2", ATTACK_2_ANIM)
                .triggerableAnim("attack_3", ATTACK_3_ANIM)
                .triggerableAnim("dash", DASH_ANIM)
                .triggerableAnim("blast", BLAST_ANIM)
                .triggerableAnim("ultimate", ULTIMATE_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
