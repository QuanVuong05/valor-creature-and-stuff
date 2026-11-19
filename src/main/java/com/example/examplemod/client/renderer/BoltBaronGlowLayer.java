package com.example.examplemod.client.renderer;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.client.ModRenderTypes;
import com.example.examplemod.entity.BoltBaronEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Unlit (no shading) pass over the boss, drawn after the normal pass:
 * - normal form: only the opaque pixels of "bolt_baron_glow" are repainted full bright;
 * - awaken form: the whole awaken texture is repainted full bright (the boss is fully unlit).
 * The pass has no depth write and does not cull, see ModRenderTypes.unlitDecal.
 * The red flash when the boss is hurt is a red tint of this pass, so it never switches the shading back on.
 */
public class BoltBaronGlowLayer extends GeoRenderLayer<BoltBaronEntity> {
    private static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/bolt_baron_glow.png");
    private static final int FULL_BRIGHT = 15728880;

    public BoltBaronGlowLayer(GeoRenderer<BoltBaronEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, BoltBaronEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        if (animatable.isInvisible()) {
            return;
        }
        // This unlit render type has no overlay, so the red hurt flash is done with a colour tint (stays unlit)
        boolean flash = animatable.hurtTime > 0 || animatable.deathTime > 0;
        float g = flash ? 0.55F : 1.0F;
        ResourceLocation texture = animatable.isAwakened() ? this.getTextureResource(animatable) : GLOW_TEXTURE;
        RenderType unlit = ModRenderTypes.unlitDecal(texture);
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, unlit,
                bufferSource.getBuffer(unlit), partialTick, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, g, g, 1.0F);
    }
}
