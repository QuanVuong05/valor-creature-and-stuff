package com.example.examplemod.client.gui;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.BoltBaronEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import java.util.UUID;

/**
 * Custom boss bar for Bolt Baron.
 * - bolt_baron_base.png: 5px strips = bar background (y 29), health (y 34), shield (y 39)
 *   Shield is drawn Liên Quân Mobile style: a white segment right after the current health, on the same bar.
 * - bolt_baron_bar_overlay.png: frame drawn on top
 * Both textures are 256x64 and share the same canvas, so they are drawn at the same position.
 */
public class BoltBaronBossBarOverlay implements IGuiOverlay {
    private static final ResourceLocation BASE =
            new ResourceLocation(ExampleMod.MODID, "textures/gui/bolt_baron_base.png");
    private static final ResourceLocation OVERLAY =
            new ResourceLocation(ExampleMod.MODID, "textures/gui/bolt_baron_bar_overlay.png");
    // Awaken form: same layout (256x64 canvas, bar strips at y 29 / 34 / 39), different artwork
    private static final ResourceLocation BASE_AWAKEN =
            new ResourceLocation(ExampleMod.MODID, "textures/gui/bolt_baron_awaken_base.png");
    private static final ResourceLocation OVERLAY_AWAKEN =
            new ResourceLocation(ExampleMod.MODID, "textures/gui/bolt_baron_awaken_bar_overlay.png");

    private static final int TEX_W = 256;
    private static final int TEX_H = 64;

    // Bar region inside bolt_baron_base.png
    private static final int BAR_U = 39;
    private static final int BAR_W = 178;
    private static final int BG_V = 29;
    private static final int HP_V = 34;
    private static final int SHIELD_V = 39;
    private static final int BAR_H = 5;

    // Distance from the top of the screen to the top of the 256x64 canvas
    private static final int TOP_OFFSET = 2;
    /** Height (GUI pixels from the top of the screen) taken by the artwork of each bar: other boss bars go below it. */
    private static final int BAR_BOTTOM = 52;        // normal frame: art ends at y 50 + TOP_OFFSET
    private static final int BAR_BOTTOM_AWAKEN = 56; // awaken frame: art ends at y 54 + TOP_OFFSET
    // Max distance (blocks) at which the bar is shown
    private static final double SHOW_RANGE = 64.0D;

    /** How fast the shield segment follows the real value (higher = faster). */
    private static final float SHIELD_SMOOTHING = 9.0F;

    private UUID trackedBoss = null;
    private float displayedShield = 0.0F;
    private long lastNanos = 0L;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        BoltBaronEntity boss = findNearestBoss(mc);
        if (boss == null) return;

        float maxHealth = Math.max(1.0F, boss.getMaxHealth());
        float health = Mth.clamp(boss.getHealth(), 0.0F, maxHealth);

        // Smoothly follow the real shield value (shrinks gradually when the shield is hit)
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Mth.clamp((now - lastNanos) / 1.0E9F, 0.0F, 0.25F);
        lastNanos = now;
        float targetShield = getShield(boss);
        if (!boss.getUUID().equals(trackedBoss)) {
            trackedBoss = boss.getUUID();
            displayedShield = targetShield;
        } else {
            displayedShield += (targetShield - displayedShield) * (1.0F - (float) Math.exp(-dt * SHIELD_SMOOTHING));
            if (Math.abs(targetShield - displayedShield) < 0.01F) displayedShield = targetShield;
        }
        float shield = Math.max(0.0F, displayedShield);

        // Liên Quân style: health + shield share one bar. If they exceed max health the bar rescales,
        // so the whole shield is always visible and the bar never overflows.
        float denominator = Math.max(maxHealth, health + shield);

        ResourceLocation base = boss.isAwakened() ? BASE_AWAKEN : BASE;
        ResourceLocation frame = boss.isAwakened() ? OVERLAY_AWAKEN : OVERLAY;

        int x = (screenWidth - TEX_W) / 2;
        int y = TOP_OFFSET;

        // 1) background of the bar (top strip of the base texture)
        g.blit(base, x + BAR_U, y + BG_V, BAR_U, BG_V, BAR_W, BAR_H, TEX_W, TEX_H);

        // 2) health (bottom strip of the base texture) drawn ON TOP of the background (same position), cropped from the right
        int hpWidth = Mth.clamp(Mth.ceil(BAR_W * health / denominator), 0, BAR_W);
        if (hpWidth > 0) {
            g.blit(base, x + BAR_U, y + BG_V, BAR_U, HP_V, hpWidth, BAR_H, TEX_W, TEX_H);
        }

        // 2b) shield: white segment starting right after the current health (hidden when there is no shield)
        if (shield > 0.001F) {
            int totalWidth = Mth.clamp(Mth.ceil(BAR_W * (health + shield) / denominator), 0, BAR_W);
            int shieldWidth = totalWidth - hpWidth;
            if (shieldWidth > 0) {
                g.blit(base, x + BAR_U + hpWidth, y + BG_V, BAR_U + hpWidth, SHIELD_V, shieldWidth, BAR_H, TEX_W, TEX_H);
            }
        }

        // 3) frame on top
        g.blit(frame, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
    }

    /** Current shield of the boss (synced entity data, kept in step with valor_items:shield on the server). */
    private static float getShield(BoltBaronEntity boss) {
        return Math.max(0.0F, boss.getSyncedShield());
    }

    /** True while this custom boss bar is being drawn (same conditions as render()). */
    public static boolean isShown(Minecraft mc) {
        return mc.level != null && mc.player != null && !mc.options.hideGui && findNearestBoss(mc) != null;
    }

    /** Space (GUI pixels from the top) taken by the bar currently on screen, 0 if none. */
    public static int getBarBottom(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.options.hideGui) return 0;
        BoltBaronEntity boss = findNearestBoss(mc);
        if (boss == null) return 0;
        return boss.isAwakened() ? BAR_BOTTOM_AWAKEN : BAR_BOTTOM;
    }

    private static BoltBaronEntity findNearestBoss(Minecraft mc) {
        BoltBaronEntity best = null;
        double bestDist = SHOW_RANGE * SHOW_RANGE;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof BoltBaronEntity boss && boss.isAlive()) {
                double d = boss.distanceToSqr(mc.player);
                if (d < bestDist) {
                    bestDist = d;
                    best = boss;
                }
            }
        }
        return best;
    }
}
