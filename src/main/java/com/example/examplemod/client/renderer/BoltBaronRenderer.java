package com.example.examplemod.client.renderer;

import com.example.examplemod.client.model.BoltBaronModel;
import com.example.examplemod.entity.BoltBaronEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BoltBaronRenderer extends GeoEntityRenderer<BoltBaronEntity> {
    public BoltBaronRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new BoltBaronModel());
        this.shadowRadius = 0.7F;
        // Unlit glow texture of the normal form (bolt_baron_glow.png)
        this.addRenderLayer(new BoltBaronGlowLayer(this));
    }
}
