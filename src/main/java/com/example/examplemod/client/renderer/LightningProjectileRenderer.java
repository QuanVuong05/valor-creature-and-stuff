package com.example.examplemod.client.renderer;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.client.ModRenderTypes;
import com.example.examplemod.entity.LightningProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Renders the lightning_projectile block-style JSON model (registered with ModelEvent.RegisterAdditional)
 * without any shading: full bright, no directional face darkening.
 */
public class LightningProjectileRenderer extends EntityRenderer<LightningProjectileEntity> {
    public static final ResourceLocation MODEL = new ResourceLocation(ExampleMod.MODID, "entity/lightning_projectile");
    private static final int FULL_BRIGHT = 15728880;
    // null = quads without a cull face (every element of this model), then the six directions
    private static final Direction[] FACES = {null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST};

    public LightningProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(LightningProjectileEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(LightningProjectileEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);

        float yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        // The model points towards -Z (arrow-head end at z = -1, long tail towards +Z): turn -Z into the flight direction
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(-xRot));
        // Model coordinates are in 1/16 block: put the model origin on the head of the projectile
        // (x = 8 is the middle of the bolt, y = 0 its centre line, z = -1 its tip)
        poseStack.translate(-0.5D, 0.0D, 1.0D / 16.0D);

        VertexConsumer consumer = buffer.getBuffer(ModRenderTypes.unlitDecal(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        RandomSource random = RandomSource.create(42L);
        for (Direction face : FACES) {
            for (BakedQuad quad : model.getQuads(null, face, random)) {
                consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
        poseStack.popPose();
    }
}
