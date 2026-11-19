package com.example.examplemod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Small flash of electric arcs used as the aura of the awakened Bolt Baron. The 32 frames of electric_N.png are
 * different arc shapes, so the particle shows a new random one every tick (a flickering arc). At the end of its
 * lifetime it shrinks to nothing (no fading).
 * The entity id is carried by xSpeed: the particle keeps its spawn offset relative to that entity and follows it,
 * so the aura stays on the boss's model while it walks or dashes instead of being left behind.
 * The texture is white and tinted: starts white, then turns to #61edff. Always full bright.
 */
public class ElectricAuraParticle extends TextureSheetParticle {
    private static final int MIN_LIFETIME = 4; // ticks
    private static final int EXTRA_LIFETIME = 3; // + random 0..2 ticks
    private static final float SIZE = 0.1625F; // half-width in blocks (a 16px frame is drawn 2 * size wide), +-30% random
    // #61edff
    private static final float END_R = 0x61 / 255.0F;
    private static final float END_G = 0xED / 255.0F;
    private static final float END_B = 0xFF / 255.0F;

    private final SpriteSet sprites;
    private final Entity follow; // null = stays where it spawned
    private final double offX;
    private final double offY;
    private final double offZ;

    protected ElectricAuraParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, int entityId) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.follow = entityId > 0 ? level.getEntity(entityId) : null;
        if (this.follow != null) {
            this.offX = x - this.follow.getX();
            this.offY = y - this.follow.getY();
            this.offZ = z - this.follow.getZ();
        } else {
            this.offX = 0.0D;
            this.offY = 0.0D;
            this.offZ = 0.0D;
        }
        this.quadSize = SIZE * (0.7F + this.random.nextFloat() * 0.6F);
        this.lifetime = MIN_LIFETIME + this.random.nextInt(EXTRA_LIFETIME);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 1.0F;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880; // full bright
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        // Stay on the entity: same previous/current positions as the entity so the rendering interpolates like it
        if (this.follow != null && !this.follow.isRemoved()) {
            this.xo = this.follow.xo + this.offX;
            this.yo = this.follow.yo + this.offY;
            this.zo = this.follow.zo + this.offZ;
            this.setPos(this.follow.getX() + this.offX, this.follow.getY() + this.offY, this.follow.getZ() + this.offZ);
        }

        // New random arc shape every tick = flicker
        this.pickSprite(this.sprites);

        // White -> #61edff over the lifetime
        float progress = (float) this.age / (float) this.lifetime;
        this.rCol = Mth.lerp(progress, 1.0F, END_R);
        this.gCol = Mth.lerp(progress, 1.0F, END_G);
        this.bCol = Mth.lerp(progress, 1.0F, END_B);
    }

    @Override
    public float getQuadSize(float partialTick) {
        // Full size for the first half of the lifetime, then shrinks smoothly to 0 right when it disappears
        float progress = Mth.clamp(((float) this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        float shrink = 1.0F - Mth.clamp((progress - 0.5F) * 2.0F, 0.0F, 1.0F);
        return this.quadSize * shrink;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            // xSpeed carries the id of the entity to follow (0 = none)
            return new ElectricAuraParticle(level, x, y, z, this.sprites, (int) xSpeed);
        }
    }
}
