package com.example.examplemod.client.renderer;

import com.example.examplemod.client.model.ThunderOrbModel;
import com.example.examplemod.entity.ThunderOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

public class ThunderOrbRenderer extends GeoEntityRenderer<ThunderOrbEntity> {
    public ThunderOrbRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ThunderOrbModel());
        this.shadowRadius = 0.3F;
    }

    @Override
    public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        // Outline cubes in Blockbench have negative dimensions (e.g. [-22, -22, -22] or [-18, -18, -18])
        boolean isInvertedOutline = cube.size().x < 0 || cube.size().y < 0 || cube.size().z < 0;

        // Render thunder orb with full brightness glowing in the dark
        int glowingLight = 15728880;

        RenderUtils.translateToPivotPoint(poseStack, cube);
        RenderUtils.rotateMatrixAroundCube(poseStack, cube);
        RenderUtils.translateAwayFromPivotPoint(poseStack, cube);

        Matrix3f normalMatrix = poseStack.last().normal();
        Matrix4f poseMatrix = poseStack.last().pose();

        for (GeoQuad quad : cube.quads()) {
            if (quad != null) {
                Vector3f normal = normalMatrix.transform(new Vector3f(quad.normal()));
                RenderUtils.fixInvertedFlatCube(cube, normal);

                if (isInvertedOutline) {
                    // For inverted outline cubes: reverse winding order and invert normals so the outline renders faithfully
                    normal.negate();
                    GeoVertex[] vertices = quad.vertices();
                    for (int i = vertices.length - 1; i >= 0; i--) {
                        GeoVertex vertex = vertices[i];
                        Vector3f position = vertex.position();
                        Vector4f vector4f = poseMatrix.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0F));
                        buffer.vertex(vector4f.x(), vector4f.y(), vector4f.z(), red, green, blue, alpha, vertex.texU(), vertex.texV(), packedOverlay, glowingLight, normal.x(), normal.y(), normal.z());
                    }
                } else {
                    this.createVerticesOfQuad(quad, poseMatrix, normal, buffer, glowingLight, packedOverlay, red, green, blue, alpha);
                }
            }
        }
    }
}
