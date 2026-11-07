package com.example.examplemod.client.model;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.ValorCreatureEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ValorCreatureModel extends GeoModel<ValorCreatureEntity> {
    @Override
    public ResourceLocation getModelResource(ValorCreatureEntity animatable) {
        return new ResourceLocation(ExampleMod.MODID, "geo/valor_creature.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ValorCreatureEntity animatable) {
        return new ResourceLocation(ExampleMod.MODID, "textures/entity/valor_creature.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ValorCreatureEntity animatable) {
        return new ResourceLocation(ExampleMod.MODID, "animations/valor_creature.animation.json");
    }
}
