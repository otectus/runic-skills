package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.client.core.Utils;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Generic transient "notice" banner for level-denial messages that are not tied to a specific
 * locked item — spell-gating (Iron's Spellbooks, Ars Nouveau) and Apotheosis affix-rarity gating.
 *
 * <p>These previously went to chat ({@code sendSystemMessage}) or the action bar
 * ({@code displayClientMessage(..., true)}), both of which render <em>behind</em> an open
 * container/inventory screen. Like {@link OverlaySkillGui}, this renders through a named Forge
 * HUD overlay layer ({@code runicskills:notice_overlay}) for the in-game case AND through a
 * {@link ScreenEvent.Render.Post} hook for the screen-open case, so the message always shows
 * over the GUI.
 */
@OnlyIn(Dist.CLIENT)
public class OverlayNoticeGui implements IGuiOverlay {
    public static final OverlayNoticeGui INSTANCE = new OverlayNoticeGui();

    private final Minecraft client = Minecraft.getInstance();
    private static Component message = null;
    private static int showTicks = 0;

    @Override
    public void render(ForgeGui gui, GuiGraphics matrixStack, float partialTick, int screenWidth, int screenHeight) {
        // In-game HUD pass (no screen open). Forge skips this pass while a Screen is up;
        // onScreenRender covers that case.
        draw(matrixStack);
    }

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        if (this.client.screen == null) return;
        draw(event.getGuiGraphics());
    }

    private void draw(GuiGraphics matrixStack) {
        if (this.client.level == null || this.client.player == null || showTicks <= 0 || message == null) return;

        int xOff = this.client.getWindow().getGuiScaledWidth() / 2;
        int yOff = this.client.getWindow().getGuiScaledHeight() / 3;
        int halfWidth = this.client.font.width(message) / 2;

        matrixStack.pose().pushPose();
        RenderSystem.enableBlend();
        float alpha = (showTicks < 20) ? (showTicks / 20.0F) : 1.0F;

        // Semi-transparent backdrop sized to the text.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * 0.6F);
        matrixStack.fill(xOff - halfWidth - 8, yOff - 6, xOff + halfWidth + 8, yOff + 14, Color.BLACK.getRGB());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        Utils.drawCenterWithShadow(matrixStack, message, xOff, yOff, 16733525);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrixStack.pose().popPose();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (showTicks > 0) showTicks--;
    }

    /** Display {@code component} as a transient over-GUI banner. */
    public static void show(Component component) {
        message = component;
        showTicks = 80;
    }
}
