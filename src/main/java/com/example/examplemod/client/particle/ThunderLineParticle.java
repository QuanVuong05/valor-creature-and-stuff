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
 * Thin streak left behind by a flying Thunder Orb. It is a ribbon along the direction given by the spawn call
 * (xSpeed/ySpeed/zSpeed = the orb's flight direction) that always turns its flat side to the camera. Length and
 * thickness are random per particle. It stays where it spawned, is always full bright and, at the end of its
 * lifetime, shrinks to nothing (no fading). Two particle types share this class, one per colour
 * (#61edff and #1e92c0).
 */
public class ThunderLineParticle extends TextureSheetParticle {
    private static final int MIN_LIFETIME = 8; // ticks
    private static final int EXTRA_LIFETIME = 5; // + random 0..4 ticks
    // Random size, in blocks
    private static final float MIN_LENGTH = 0.8F;
    private static final float MAX_LENGTH = 2.4F;
    private static final float MIN_WIDTH = 0.04F;
    private static final float MAX_WIDTH = 0.10F;
    // #61edff
    private static final float CYAN_R = 0x61 / 255.0F;
    private static final float CYAN_G = 0xED / 255.0F;
    private static final float CYAN_B = 0xFF / 255.0F;
    // #1e92c0
    private static final float BLUE_R = 0x1E / 255.0F;
    private static final float BLUE_G = 0x92 / 255.0F;
    private static final float BLUE_B = 0xC0 / 255.0F;

    private final float dirX;
    private final float dirY;
    private final float dirZ;
    private final float length;
    private final float width;

    protected ThunderLineParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites,
                                  double dx, double dy, double dz, float r, float g, float b) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.pickSprite(sprites);
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4D) { // no direction given: vertical streak
            dx = 0.0D;
            dy = 1.0D;
            dz = 0.0D;
            len = 1.0D;
        }
        this.dirX = (float) (dx / len);
        this.dirY = (float) (dy / len);
        this.dirZ = (float) (dz / len);
        this.length = Mth.lerp(this.random.nextFloat(), MIN_LENGTH, MAX_LENGTH);
        this.width = Mth.lerp(this.random.nextFloat(), MIN_WIDTH, MAX_WIDTH);
        this.lifetime = MIN_LIFETIME + this.random.nextInt(EXTRA_LIFETIME);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.alpha = 1.0F;
        // Streaks are long: make the bounding box big enough so they are not frustum-culled too early
        this.setBoundingBox(new AABB(x - 1.5D, y - 1.5D, z - 1.5D, x + 1.5D, y + 1.5D, z + 1.5D));
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
        // Full size for the first half of the lifetime, then shrinks smoothly to 0 right when it disappears
        float progress = Mth.clamp((this.age + partialTicks) / (float) this.lifetime, 0.0F, 1.0F);
        float scale = 1.0F - Mth.clamp((progress - 0.5F) * 2.0F, 0.0F, 1.0F);
        if (scale <= 0.0F) {
            return;
        }

        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float cy = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float cz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());

        // Ribbon: long axis = flight direction, width axis = perpendicular to it and to the view direction
        float wx = this.dirY * cz - this.dirZ * cy;
        float wy = this.dirZ * cx - this.dirX * cz;
        float wz = this.dirX * cy - this.dirY * cx;
        float wLen = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        if (wLen < 1.0E-4F) {
            return; // seen exactly end-on
        }
        float k = this.width * 0.5F * scale / wLen;
        wx *= k;
        wy *= k;
        wz *= k;
        float hl = this.length * 0.5F * scale;
        float ax = this.dirX * hl;
        float ay = this.dirY * hl;
        float az = this.dirZ * hl;

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);

        // Drawn on both sides so it is never back-face culled
        this.vertex(buffer, cx - ax - wx, cy - ay - wy, cz - az - wz, u0, v1, light);
        this.vertex(buffer, cx - ax + wx, cy - ay + wy, cz - az + wz, u0, v0, light);
        this.vertex(buffer, cx + ax + wx, cy + ay + wy, cz + az + wz, u1, v0, light);
        this.vertex(buffer, cx + ax - wx, cy + ay - wy, cz + az - wz, u1, v1, light);

        this.vertex(buffer, cx + ax - wx, cy + ay - wy, cz + az - wz, u1, v1, light);
        this.vertex(buffer, cx + ax + wx, cy + ay + wy, cz + az + wz, u1, v0, light);
        this.vertex(buffer, cx - ax + wx, cy - ay + wy, cz - az + wz, u0, v0, light);
        this.vertex(buffer, cx - ax - wx, cy - ay - wy, cz - az - wz, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    public static ParticleProvider<SimpleParticleType> cyan(SpriteSet sprites) {
        return new Provider(sprites, CYAN_R, CYAN_G, CYAN_B);
    }

    public static ParticleProvider<SimpleParticleType> blue(SpriteSet sprites) {
        return new Provider(sprites, BLUE_R, BLUE_G, BLUE_B);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float r;
        private final float g;
        private final float b;

        public Provider(SpriteSet sprites, float r, float g, float b) {
            this.sprites = sprites;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            // The speeds are the direction of the streak (the orb's flight direction)
            return new ThunderLineParticle(level, x, y, z, this.sprites, xSpeed, ySpeed, zSpeed, this.r, this.g, this.b);
        }
    }
}
