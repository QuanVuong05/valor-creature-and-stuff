package com.example.examplemod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * One tiny square used to draw the lightning bolts of the basic attack. The texture is plain white and the colour is
 * tinted: starts white, then turns to #61edff as it ages. It stays where it spawned, is always full bright and
 * shrinks until it disappears (no fading).
 */
public class LightningSparkParticle extends TextureSheetParticle {
    private static final float SIZE = 0.05F; // blocks (default; xSpeed of the spawn call multiplies it)
    // #61edff
    private static final float END_R = 0x61 / 255.0F;
    private static final float END_G = 0xED / 255.0F;
    private static final float END_B = 0xFF / 255.0F;

    protected LightningSparkParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float sizeScale) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.pickSprite(sprites);
        this.quadSize = SIZE * sizeScale;
        this.lifetime = 6;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 1.0F;
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
        // White -> #61edff over the lifetime
        float progress = (float) this.age / (float) this.lifetime;
        this.rCol = Mth.lerp(progress, 1.0F, END_R);
        this.gCol = Mth.lerp(progress, 1.0F, END_G);
        this.bCol = Mth.lerp(progress, 1.0F, END_B);
    }

    @Override
    public float getQuadSize(float partialTick) {
        // Shrinks smoothly to 0 at the end of the lifetime
        float progress = Mth.clamp(((float) this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        return this.quadSize * (1.0F - progress);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            // xSpeed carries the size multiplier (0 = default size)
            float scale = xSpeed > 0.0D ? (float) xSpeed : 1.0F;
            return new LightningSparkParticle(level, x, y, z, this.sprites, scale);
        }
    }
}
