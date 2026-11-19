package com.example.examplemod.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Puff of dust used by the ultimate shockwave. One class drives the three dust particles (dust, dust_medium,
 * dust_small): each is a sprite sheet of 16x16 frames, 9 columns per animation. Every row of the sheet is a
 * variant of the same animation, so a particle picks one row at random and plays its 9 frames over its lifetime.
 * The sprite list in the particle JSON is the rows one after the other (row 0 frames 0-8, row 1 frames 0-8...).
 * The velocity given by the spawn call is slowed down by friction, so the dust is thrown out and then settles.
 * Tinted #1e92c0.
 */
public class DustParticle extends TextureSheetParticle {
    private static final int FRAMES = 9; // frames per animation (one row of the sheet)
    // The first frames of every row are a tiny puff that slowly grows, so the dust would look like it shows up a few
    // ticks after the shockwave ring (which is full size from tick 0). Starting at frame 2 makes the dust burst out
    // together with the ring. Set to 0 to play the whole animation.
    private static final int START_FRAME = 2;
    private static final int MIN_LIFETIME = 10; // ticks
    private static final int EXTRA_LIFETIME = 4; // + random 0..3 ticks
    private static final float FRICTION = 0.92F; // velocity kept every tick

    // Half-width of the quad in blocks (a 16px frame is drawn 2 * size blocks wide), +-20% random
    private static final float LARGE_SIZE = 0.9F;
    private static final float MEDIUM_SIZE = 0.65F;
    private static final float SMALL_SIZE = 0.4F;

    // Tint #1e92c0 (multiplied with the white-ish texture)
    private static final float R = 0x1E / 255.0F;
    private static final float G = 0x92 / 255.0F;
    private static final float B = 0xC0 / 255.0F;

    private final SpriteSet sprites;
    private final int spriteCount;
    private final int firstFrame; // index of frame 0 of the chosen variant row

    protected DustParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                           SpriteSet sprites, int variants, float size) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.spriteCount = variants * FRAMES;
        this.firstFrame = this.random.nextInt(variants) * FRAMES;
        this.quadSize = size * (0.8F + this.random.nextFloat() * 0.4F);
        this.lifetime = MIN_LIFETIME + this.random.nextInt(EXTRA_LIFETIME);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = FRICTION;
        // Spawn velocity (plus a little spread so the puffs do not all travel in lockstep)
        this.xd = xd + (this.random.nextDouble() - 0.5D) * 0.04D;
        this.yd = yd + (this.random.nextDouble() - 0.5D) * 0.02D;
        this.zd = zd + (this.random.nextDouble() - 0.5D) * 0.04D;
        this.rCol = R;
        this.gCol = G;
        this.bCol = B;
        this.alpha = 1.0F;
        this.setFrame(0.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        // Pick the frame from the interpolated age so the animation stays smooth between ticks
        this.setFrame((this.age + partialTicks) / (float) this.lifetime);
        super.render(buffer, camera, partialTicks);
    }

    private void setFrame(float progress) {
        int frame = Mth.clamp(START_FRAME + (int) (progress * (FRAMES - START_FRAME)), 0, FRAMES - 1);
        // get(i, n - 1) returns exactly sprite i of the n listed in the particle JSON
        this.sprite = this.sprites.get(this.firstFrame + frame, this.spriteCount - 1);
    }

    // dust.png: 2 variants, dust_medium.png: 1 variant, dust_small.png: 2 variants
    public static ParticleProvider<SimpleParticleType> large(SpriteSet sprites) {
        return new Provider(sprites, 2, LARGE_SIZE);
    }

    public static ParticleProvider<SimpleParticleType> medium(SpriteSet sprites) {
        return new Provider(sprites, 1, MEDIUM_SIZE);
    }

    public static ParticleProvider<SimpleParticleType> small(SpriteSet sprites) {
        return new Provider(sprites, 2, SMALL_SIZE);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final int variants;
        private final float size;

        public Provider(SpriteSet sprites, int variants, float size) {
            this.sprites = sprites;
            this.variants = variants;
            this.size = size;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            // The speeds are the velocity of the dust (spawn with count = 0)
            return new DustParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites, this.variants, this.size);
        }
    }
}
