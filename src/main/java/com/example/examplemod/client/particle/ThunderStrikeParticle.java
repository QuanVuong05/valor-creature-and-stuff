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
 * Thunder strike particle (empowered attack bolt, 14 blocks by default). The bolt is a vertical quad whose base sits on the spawn point and that
 * always turns around the Y axis to face the camera (so it stays upright like a real bolt).
 * Frames come from particles/lightning_bolt.json and are spread over the particle's lifetime.
 * The requested height (in blocks) is carried by xSpeed (0 = default height).
 */
public class ThunderStrikeParticle extends TextureSheetParticle {
    private static final int LIFETIME_TICKS = 8;
    public static final float DEFAULT_HEIGHT = 14.0F;
    private static final float ASPECT = 64.0F / 231.0F; // texture width / height

    private final SpriteSet sprites;
    private final boolean mirrored;
    private final float height;
    private final float width;

    public ThunderStrikeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float height) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.height = height;
        this.width = height * ASPECT;
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
        this.setSpriteFromAge(sprites);
        // Large bounding box so the bolt is never frustum-culled.
        double r = Math.max(this.height, this.width);
        this.setBoundingBox(new AABB(x - r, y - 1.0D, z - r, x + r, y + this.height + 1.0D, z + r));
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
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        double px = Mth.lerp(partialTicks, this.xo, this.x) - cam.x();
        double py = Mth.lerp(partialTicks, this.yo, this.y) - cam.y();
        double pz = Mth.lerp(partialTicks, this.zo, this.z) - cam.z();

        // Horizontal "right" vector perpendicular to the direction towards the camera.
        double len = Math.sqrt(px * px + pz * pz);
        double rx;
        double rz;
        if (len < 1.0E-4D) {
            rx = 1.0D;
            rz = 0.0D;
        } else {
            rx = -pz / len;
            rz = px / len;
        }
        double hw = this.width * 0.5D;
        double top = this.height;

        float u0 = this.mirrored ? this.getU1() : this.getU0();
        float u1 = this.mirrored ? this.getU0() : this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);

        // bottom-left, top-left, top-right, bottom-right (both sides drawn so it is never back-face culled)
        this.vertex(buffer, px - rx * hw, py, pz - rz * hw, u0, v1, light);
        this.vertex(buffer, px - rx * hw, py + top, pz - rz * hw, u0, v0, light);
        this.vertex(buffer, px + rx * hw, py + top, pz + rz * hw, u1, v0, light);
        this.vertex(buffer, px + rx * hw, py, pz + rz * hw, u1, v1, light);

        this.vertex(buffer, px + rx * hw, py, pz + rz * hw, u1, v1, light);
        this.vertex(buffer, px + rx * hw, py + top, pz + rz * hw, u1, v0, light);
        this.vertex(buffer, px - rx * hw, py + top, pz - rz * hw, u0, v0, light);
        this.vertex(buffer, px - rx * hw, py, pz - rz * hw, u0, v1, light);
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
            float height = xSpeed > 0.0D ? (float) xSpeed : DEFAULT_HEIGHT;
            return new ThunderStrikeParticle(level, x, y, z, this.sprites, height);
        }
    }
}
