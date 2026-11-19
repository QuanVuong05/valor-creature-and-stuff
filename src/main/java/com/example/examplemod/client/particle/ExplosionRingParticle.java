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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shockwave ring lying flat on the ground. The 10 frames of explosion_ring.png (48x48 each) play over 5 ticks (0.25s),
 * tinted #61edff, always full bright. The requested radius (blocks) is carried by xSpeed (0 = default radius).
 */
public class ExplosionRingParticle extends TextureSheetParticle {
    private static final int LIFETIME_TICKS = 5; // 0.25s
    private static final int FRAMES = 10;
    public static final float DEFAULT_RADIUS = 20.0F;
    private static final double GROUND_OFFSET = 0.05D; // avoids z-fighting with the floor
    // #61edff
    private static final float R = 0x61 / 255.0F;
    private static final float G = 0xED / 255.0F;
    private static final float B = 0xFF / 255.0F;

    private final SpriteSet sprites;
    private final float radius;

    protected ExplosionRingParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float radius) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.radius = radius;
        this.lifetime = LIFETIME_TICKS;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.rCol = R;
        this.gCol = G;
        this.bCol = B;
        this.alpha = 1.0F;
        this.sprite = sprites.get(0, FRAMES - 1);
        // Big bounding box so the ring is not frustum-culled when only a part of it is on screen
        this.setBoundingBox(new AABB(x - radius, y - 0.5D, z - radius, x + radius, y + 1.0D, z + radius));
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
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        // 10 frames over 5 ticks: pick the frame from the (interpolated) age
        float progress = Mth.clamp((this.age + partialTicks) / (float) this.lifetime, 0.0F, 0.999F);
        this.sprite = this.sprites.get((int) (progress * FRAMES), FRAMES - 1);

        Vec3 cam = camera.getPosition();
        double px = Mth.lerp(partialTicks, this.xo, this.x) - cam.x();
        double py = Mth.lerp(partialTicks, this.yo, this.y) - cam.y() + GROUND_OFFSET;
        double pz = Mth.lerp(partialTicks, this.zo, this.z) - cam.z();
        double r = this.radius;

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);

        // Flat horizontal quad, drawn on both sides so it is never back-face culled
        this.vertex(buffer, px - r, py, pz - r, u0, v0, light);
        this.vertex(buffer, px - r, py, pz + r, u0, v1, light);
        this.vertex(buffer, px + r, py, pz + r, u1, v1, light);
        this.vertex(buffer, px + r, py, pz - r, u1, v0, light);

        this.vertex(buffer, px + r, py, pz - r, u1, v0, light);
        this.vertex(buffer, px + r, py, pz + r, u1, v1, light);
        this.vertex(buffer, px - r, py, pz + r, u0, v1, light);
        this.vertex(buffer, px - r, py, pz - r, u0, v0, light);
    }

    private void vertex(VertexConsumer buffer, double x, double y, double z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            float radius = xSpeed > 0.0D ? (float) xSpeed : DEFAULT_RADIUS;
            return new ExplosionRingParticle(level, x, y, z, this.sprites, radius);
        }
    }
}
