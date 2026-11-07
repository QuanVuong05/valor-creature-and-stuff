package com.example.examplemod.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ValorCreatureEntity extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Animation definitions matching the JSON animation keys
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK_1_ANIM = RawAnimation.begin().thenPlay("attack_1");
    private static final RawAnimation ATTACK_2_ANIM = RawAnimation.begin().thenPlay("attack_2");
    private static final RawAnimation ATTACK_3_ANIM = RawAnimation.begin().thenPlay("attack_3");
    private static final RawAnimation ULTIMATE_ANIM = RawAnimation.begin().thenPlay("ultimate");

    public ValorCreatureEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D) // Faster by 1.5x (0.28 -> 0.42)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 300.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D) // 100% Knockback resistance
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Absolute immunity to knockback
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ValorAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public void startAttackAnimation() {
        if (!this.level().isClientSide()) {
            int attackId = this.random.nextInt(3) + 1;
            this.triggerAnim("attack", "attack_" + attackId);
        }
    }

    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        this.startAttackAnimation();
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

        // Combat / action attack controller with 3 combo attacks
        controllers.add(new AnimationController<>(this, "attack", 5, state -> PlayState.STOP)
                .triggerableAnim("attack_1", ATTACK_1_ANIM)
                .triggerableAnim("attack_2", ATTACK_2_ANIM)
                .triggerableAnim("attack_3", ATTACK_3_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * Custom Melee Attack Goal with windup delay matched to the animation strike frame (8 ticks / 0.4s).
     * If the player steps back / dodges outside attack reach before the strike lands, the attack misses!
     */
    public static class ValorAttackGoal extends MeleeAttackGoal {
        private final ValorCreatureEntity creature;
        private int animDelay = 0;

        public ValorAttackGoal(ValorCreatureEntity mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(mob, speedModifier, followingTargetEvenIfNotSeen);
            this.creature = mob;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.animDelay > 0) {
                LivingEntity target = this.creature.getTarget();
                return target != null && target.isAlive();
            }
            return super.canContinueToUse();
        }

        @Override
        public void start() {
            super.start();
            this.animDelay = 0;
        }

        @Override
        public void stop() {
            super.stop();
            this.animDelay = 0;
        }

        @Override
        public void tick() {
            if (this.animDelay > 0) {
                this.animDelay--;
                this.creature.getNavigation().stop();
                LivingEntity target = this.creature.getTarget();
                if (target != null) {
                    this.creature.getLookControl().setLookAt(target, 30.0F, 30.0F);
                }
                if (this.animDelay == 0) {
                    if (target != null && target.isAlive()) {
                        double reachSqr = this.getAttackReachSqr(target);
                        if (this.creature.isWithinMeleeAttackRange(target) || this.creature.distanceToSqr(target) <= reachSqr) {
                            this.creature.doHurtTarget(target);
                        }
                    }
                }
                return;
            }

            super.tick();
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distToEnemySqr) {
            double reach = this.getAttackReachSqr(target);
            if (distToEnemySqr <= reach && this.isTimeToAttack() && this.animDelay <= 0) {
                this.resetAttackCooldown();
                this.creature.startAttackAnimation();
                this.animDelay = 9; // 9 ticks = 0.45s matching the strike keyframe in attack_1/2/3
            }
        }

        @Override
        protected double getAttackReachSqr(LivingEntity target) {
            return Math.max(super.getAttackReachSqr(target), 9.0D); // at least 3.0 blocks reach for tall boss mob
        }

        @Override
        protected int getAttackInterval() {
            return 20; // 20 ticks (1.0s) cooldown between attacks
        }
    }
}
