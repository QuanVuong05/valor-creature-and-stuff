package com.example.examplemod.client.model;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.BoltBaronEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BoltBaronModel extends GeoModel<BoltBaronEntity> {
    @Override
    public ResourceLocation getModelResource(BoltBaronEntity animatable) {
        if (animatable.isAwakened()) {
            return new ResourceLocation(ExampleMod.MODID, "geo/bolt_baron_awaken.geo.json");
        }
        return new ResourceLocation(ExampleMod.MODID, "geo/bolt_baron.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BoltBaronEntity animatable) {
        if (animatable.isAwakened()) {
            return new ResourceLocation(ExampleMod.MODID, "textures/entity/bolt_baron_awaken.png");
        }
        return new ResourceLocation(ExampleMod.MODID, "textures/entity/bolt_baron.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BoltBaronEntity animatable) {
        if (animatable.isAwakened()) {
            return new ResourceLocation(ExampleMod.MODID, "animations/bolt_baron_awaken.animation.json");
        }
        return new ResourceLocation(ExampleMod.MODID, "animations/bolt_baron.animation.json");
    }
}
