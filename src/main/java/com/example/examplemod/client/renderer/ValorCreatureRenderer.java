package com.example.examplemod.client.renderer;

import com.example.examplemod.client.model.ValorCreatureModel;
import com.example.examplemod.entity.ValorCreatureEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ValorCreatureRenderer extends GeoEntityRenderer<ValorCreatureEntity> {
    public ValorCreatureRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ValorCreatureModel());
        this.shadowRadius = 0.7F;
    }
}
