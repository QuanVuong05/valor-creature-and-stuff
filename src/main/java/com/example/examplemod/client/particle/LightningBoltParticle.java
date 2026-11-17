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
import net.minecraft.world.phys.Vec3;

/**
 * A single, upright lightning strike. The sprite sequence is owned by the
 * particle description, so this class deliberately lets {@link SpriteSet}
 * distribute its frames over the particle's lifetime.
 */
public class LightningBoltParticle extends TextureSheetParticle {
    private static final int LIFETIME_TICKS = 16;
    private static final float BOLT_HEIGHT = 7.0F;
    private static final float BOLT_WIDTH = BOLT_HEIGHT * (200.0F / 214.0F);

    private final SpriteSet sprites;
    private final boolean mirrored;
    private final float sizeScale;
    private final float flickerPhase;

    public LightningBoltParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.lifetime = LIFETIME_TICKS;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 1.0F;
        this.mirrored = this.random.nextBoolean();
        this.sizeScale = 0.9F + this.random.nextFloat() * 0.25F;
        this.flickerPhase = this.random.nextFloat() * Mth.TWO_PI;
        this.updateSprite();
    }

    private void updateSprite() {
        // SpriteSet maps the 12 declared frames across the 16-tick lifetime.
        // Passing a pre-scaled frame index here skips frames and can select the
        // wrong sprite when the number of frames changes.
        this.setSprite(this.sprites.get(this.age, this.lifetime));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880; // full bright, glowing
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.updateSprite();
            // Fade during the last four ticks without a hard visual cutoff.
            int left = this.lifetime - this.age;
            float fade = left < 4 ? Math.max(0.0F, left / 4.0F) : 1.0F;
            this.alpha = fade * fade;
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());

        // Keep the quad vertical, while turning it around the Y axis to face the player.
        float yawRad = camera.getYRot() * Mth.DEG_TO_RAD;
        float rx = -Mth.cos(yawRad);
        float rz = -Mth.sin(yawRad);
        float flicker = 1.0F + Mth.sin((this.age + partialTicks) * 2.5F + this.flickerPhase) * 0.035F;
        float hw = BOLT_WIDTH * this.sizeScale * flicker * 0.5F;

        float bottom = py;
        float top = py + BOLT_HEIGHT * this.sizeScale;

        float u0 = this.mirrored ? this.getU1() : this.getU0();
        float u1 = this.mirrored ? this.getU0() : this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);

        this.renderQuad(buffer, px, pz, rx, rz, hw * 1.2F, bottom, top, u0, u1, v0, v1, light,
                0.70F, 0.82F, 1.0F, this.alpha * 0.24F);
        this.renderQuad(buffer, px, pz, rx, rz, hw, bottom, top, u0, u1, v0, v1, light,
                this.rCol, this.gCol, this.bCol, this.alpha);
    }

    private void renderQuad(VertexConsumer buffer, float px, float pz, float rx, float rz, float halfWidth,
                            float bottom, float top, float u0, float u1, float v0, float v1, int light,
                            float red, float green, float blue, float alpha) {
        float leftX = px - rx * halfWidth;
        float leftZ = pz - rz * halfWidth;
        float rightX = px + rx * halfWidth;
        float rightZ = pz + rz * halfWidth;

        buffer.vertex(leftX, bottom, leftZ).uv(u0, v1).color(red, green, blue, alpha).uv2(light).endVertex();
        buffer.vertex(leftX, top, leftZ).uv(u0, v0).color(red, green, blue, alpha).uv2(light).endVertex();
        buffer.vertex(rightX, top, rightZ).uv(u1, v0).color(red, green, blue, alpha).uv2(light).endVertex();
        buffer.vertex(rightX, bottom, rightZ).uv(u1, v1).color(red, green, blue, alpha).uv2(light).endVertex();
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new LightningBoltParticle(level, x, y, z, this.sprites);
        }
    }
}
