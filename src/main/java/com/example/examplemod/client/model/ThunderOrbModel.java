package com.example.examplemod.client.model;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.client.ModRenderTypes;
import com.example.examplemod.entity.ThunderOrbEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ThunderOrbModel extends GeoModel<ThunderOrbEntity> {
    @Override
    public ResourceLocation getModelResource(ThunderOrbEntity animatable) {
        return new ResourceLocation(ExampleMod.MODID, "geo/thunder_orb.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ThunderOrbEntity animatable) {
        return new ResourceLocation(ExampleMod.MODID, "textures/entity/thunder_orb.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ThunderOrbEntity animatable) {
        return new ResourceLocation(ExampleMod.MODID, "animations/thunder_orb.animation.json");
    }

    @Override
    public RenderType getRenderType(ThunderOrbEntity animatable, ResourceLocation texture) {
        // Unlit (no shading, no darkening) translucent type with culling + depth write, see ModRenderTypes
        return ModRenderTypes.unlitTranslucent(texture);
    }
}
