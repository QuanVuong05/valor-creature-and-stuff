package com.example.examplemod.client.renderer;

import com.example.examplemod.client.model.ThunderOrbModel;
import com.example.examplemod.entity.ThunderOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ThunderOrbRenderer extends GeoEntityRenderer<ThunderOrbEntity> {
    private static final int FULL_BRIGHT = 15728880;

    public ThunderOrbRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ThunderOrbModel());
        this.shadowRadius = 0.3F;
    }

    @Override
    public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha) {
        // Plain GeckoLib cube rendering, always full bright. The "inverted" (negative size) outline cubes keep
        // their natural winding: with back-face culling only their far inner faces are drawn, which is the
        // layered look shown in Blockbench. (The old code reversed their winding, which drew the NEAR faces
        // as an opaque-looking shell over the bright core.) Shading is removed by the unlit render type.
        super.renderCube(poseStack, cube, buffer, FULL_BRIGHT, packedOverlay, red, green, blue, alpha);
    }
}
