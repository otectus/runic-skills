package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Skill-requirement warning overlay. Since 1.2.0 this renders through the named
 * Forge overlay layer {@code runicskills:skill_overlay} (registered via
 * {@code RegisterGuiOverlaysEvent}) instead of piggy-backing on the F3 debug
 * event — resource packs can now relocate it via the standard above/below
 * overlay APIs. The tick subscriber remains on the Forge bus.
 */
@OnlyIn(Dist.CLIENT)
public class OverlaySkillGui implements IGuiOverlay {
    public static final OverlaySkillGui INSTANCE = new OverlaySkillGui();

    private final Minecraft client = Minecraft.getInstance();
    private static List<Skills> skills = null;
    private static int showTicks = 0;

    @Override
    public void render(ForgeGui gui, GuiGraphics matrixStack, float partialTick, int screenWidth, int screenHeight) {
        // In-game HUD pass (no screen open). Forge skips this whole pass while a Screen
        // is up, which is exactly why the warning used to vanish behind the crafting menu;
        // the onScreenRender hook below covers the screen-open case.
        draw(matrixStack);
    }

    /**
     * Mirror the warning over open container/inventory screens. Forge HUD overlays do not
     * render while a {@link net.minecraft.client.gui.screens.Screen} is open, so without this
     * the "you can't use this item" warning is invisible when a player tries to craft a locked
     * item (the lockpick-in-crafting-table case). The two paths are mutually exclusive — the
     * HUD overlay only fires with no screen, this only fires with one — so there is no
     * double-draw.
     */
    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        if (this.client.screen == null) return;
        draw(event.getGuiGraphics());
    }

    private void draw(GuiGraphics matrixStack) {
        if (this.client.level != null && this.client.player != null && showTicks > 0 && skills != null && this.client.player.getCapability(RegistryCapabilities.SKILL).isPresent()) {
            matrixStack.pose().pushPose();
            int xOff = this.client.getWindow().getGuiScaledWidth() / 2;
            int yOff = this.client.getWindow().getGuiScaledHeight() / 4;

            MutableComponent overlayMessage = Component.translatable("overlay.skill.message");
            int overlayWidth = this.client.font.width(overlayMessage) / 2;

            RenderSystem.enableBlend();
            for (int i = 0; i < 16; i++) {
                float f = (showTicks < 40) ? (0.003F * i / 40.0F * showTicks) : (0.003F * i);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, f);
                matrixStack.fill(xOff - overlayWidth - 14 - 16 - i, yOff - 11 - 16 - i, xOff + overlayWidth + 14 + 16 - i, yOff + 45 + 16 - i, Color.BLACK.getRGB());
            }
            float alpha = (showTicks < 40) ? (0.025F * showTicks) : 1.0F;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            Utils.drawCenterWithShadow(matrixStack, overlayMessage, xOff, yOff, 16733525);

            SkillCapability localCap = SkillCapability.getLocal();
            for (int j = 0; j < skills.size(); j++) {
                Skills abilities = skills.get(j);
                String level = Integer.toString(abilities.getSkillLvl());
                // Hoisted + null-guarded: getLocal() can transiently return null even when the cap
                // is present (resolve race during join); also avoids a lookup every loop iteration.
                boolean met = (localCap != null && localCap.getSkillLevel(abilities.getSkill()) >= abilities.getSkillLvl());

                int x = xOff + j * 24 - skills.size() * 12;
                int y = yOff + 15;

                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                matrixStack.blit(abilities.getSkill().getLockedTexture(abilities.getSkillLvl()), x, y, 0.0F, 0.0F, 16, 16, 16, 16);
                Utils.drawCenterWithShadow(matrixStack, level, x + 16, y + 12, met ? 5635925 : 16733525);
            }

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            matrixStack.pose().popPose();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (showTicks > 0) showTicks--;
    }

    public static void showWarning(String skill) {
        List<Skills> requirements = HandlerSkill.getValue(skill);
        if (requirements == null || requirements.isEmpty()) {
            // No requirements registered for this item; nothing to render.
            skills = null;
            showTicks = 0;
            return;
        }
        skills = requirements;
        showTicks = Math.min(60 + 15 * requirements.size(), 150);
    }
}


