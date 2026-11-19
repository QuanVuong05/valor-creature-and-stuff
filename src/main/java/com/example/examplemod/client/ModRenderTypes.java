package com.example.examplemod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Custom render types. Extends RenderType only to reach the protected RenderStateShard constants.
 */
public class ModRenderTypes extends RenderType {
    private ModRenderTypes() {
        super("examplemod_unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    /**
     * Translucent and completely unlit (no directional face shading, no lightmap), like an emissive render type,
     * but with back-face culling and depth writing ON. Both are needed for the thunder orb:
     * the two shells have negative sizes (inside-out cubes), so with culling only their far inner faces show,
     * and the depth write lets the opaque core hide the far faces behind it - exactly like Blockbench shows it.
     * It uses the "eyes" shader (output = texture * vertex colour, nothing else: no normal, no light direction,
     * no lightmap), which is guaranteed shading-free, combined with normal alpha blending instead of additive.
     */
    private static final Function<ResourceLocation, RenderType> UNLIT_TRANSLUCENT = Util.memoize(texture ->
            create("examplemod_unlit_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(CULL)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false)));

    public static RenderType unlitTranslucent(ResourceLocation texture) {
        return UNLIT_TRANSLUCENT.apply(texture);
    }

    /**
     * Unlit overlay ("decal") drawn on top of an already rendered model, used for the boss. Differences to
     * {@link #unlitTranslucent}: no back-face culling (flat plane cubes show both sides) and NO depth write.
     * The boss is first drawn normally (cutout: correct depth, transparent pixels discarded, partly transparent
     * pixels opaque). This pass then repaints the same pixels at the very same depth (depth test LEQUAL) without
     * shading. Because it writes no depth, the transparent parts of planes can never hide anything behind them,
     * which is what produced the noisy / see-through planes when the whole model used one translucent pass.
     */
    private static final Function<ResourceLocation, RenderType> UNLIT_DECAL = Util.memoize(texture ->
            create("examplemod_unlit_decal", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false)));

    public static RenderType unlitDecal(ResourceLocation texture) {
        return UNLIT_DECAL.apply(texture);
    }
}
