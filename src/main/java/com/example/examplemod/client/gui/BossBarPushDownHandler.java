package com.example.examplemod.client.gui;

import com.example.examplemod.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pushes the regular (vanilla-style) boss bars down while the Bolt Baron bar is on screen,
 * so they sit right below it instead of being drawn underneath / over it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class BossBarPushDownHandler {
    private static boolean pushed = false;

    // LOWEST + receiveCanceled so we only push when nobody cancelled the overlay (a cancelled Pre has no Post to pop it)
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.isCanceled() || event.getOverlay() != VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type()) {
            return;
        }
        int shift = BoltBaronBossBarOverlay.getBarBottom(Minecraft.getInstance());
        if (shift <= 0) {
            return;
        }
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(0.0F, shift, 0.0F);
        pushed = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (pushed && event.getOverlay() == VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type()) {
            event.getGuiGraphics().pose().popPose();
            pushed = false;
        }
    }
}
