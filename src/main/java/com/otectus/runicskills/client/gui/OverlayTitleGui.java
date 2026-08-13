package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.client.core.TitleQueue;
import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.awt.*;

/**
 * Title-earned overlay. Since 1.2.0 rendered through the named Forge overlay
 * layer {@code runicskills:title_overlay} (registered via
 * {@code RegisterGuiOverlaysEvent}) instead of the F3 debug event.
 */
@OnlyIn(Dist.CLIENT)
public class OverlayTitleGui implements IGuiOverlay {
    public static final OverlayTitleGui INSTANCE = new OverlayTitleGui();

    private final Minecraft client = Minecraft.getInstance();
    public static TitleQueue list = new TitleQueue();
    public static int timerTicks = 110;
    public static int showTicks = 110;
    private static int scaleTick = 0;

    @Override
    public void render(ForgeGui gui, GuiGraphics matrixStack, float partialTick, int screenWidth, int screenHeight) {
        if (this.client.level == null || this.client.player == null || showTicks < 0) return;
        if (!this.client.player.getCapability(RegistryCapabilities.SKILL).isPresent()) return;
        if (list.count() <= 0) {
            showTicks = 0;
            return;
        }

        Title getTitle = list.peek();
        if (getTitle == null) {
            // Defensive companion to the null guard in TitleOverlayCP#handle. A null entry used
            // to throw here before the dequeue below could run, so the queue stayed wedged and
            // the exception repeated every frame for the rest of the session (RS-022).
            list.dequeue();
            showTicks = (list.count() > 0) ? 40 : timerTicks;
            return;
        }

        // scaleTick == 0 means "fully collapsed", i.e. nothing to draw. Rendering it anyway
        // divided the screen dimensions by a zero scale and fed Infinity into the transform
        // every time a title finished animating (RS-083).
        if (scaleTick > 0) {
            float scale2 = 0.05625F * scaleTick;
            matrixStack.pose().pushPose();
            matrixStack.pose().scale(scale2, scale2, 1.0F);
            int xOff2 = (int) (this.client.getWindow().getGuiScaledWidth() / scale2 / 2.0F);
            int yOff2 = (int) (this.client.getWindow().getGuiScaledHeight() / scale2 / 4.0F);
            Utils.drawCenterWithShadow(matrixStack, Component.translatable("overlay.title.you_gain_a_title"), xOff2, yOff2 - 10, Color.WHITE.getRGB());
            matrixStack.pose().popPose();

            float scale1 = 0.1F * scaleTick;
            matrixStack.pose().pushPose();
            matrixStack.pose().scale(scale1, scale1, 1.0F);
            int xOff1 = (int) (this.client.getWindow().getGuiScaledWidth() / scale1 / 2.0F);
            int yOff1 = (int) (this.client.getWindow().getGuiScaledHeight() / scale1 / 4.0F);
            Utils.drawCenterWithShadow(matrixStack, Component.translatable("overlay.title.format", Component.translatable(getTitle.getKey()).withStyle(ChatFormatting.BOLD)), xOff1, yOff1, Color.WHITE.getRGB());
            matrixStack.pose().popPose();
        }

        if (showTicks == 0) {
            list.dequeue();
            showTicks = (list.count() > 0) ? 40 : timerTicks;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (list.count() > 0 && showTicks == timerTicks) Utils.getTitleSound();
        if (showTicks > 0) {
            showTicks--;
        }
        // The pop-in is stepped on the 20 Hz client tick, not per rendered frame. Driving it from
        // render() made the animation run four times faster on a 240 Hz display than on a 60 Hz
        // one, and stall entirely whenever something suppressed the overlay pass (RS-083).
        scaleTick = list.count() > 0
                ? Mth.clamp(showTicks < 20 ? scaleTick - 1 : scaleTick + 1, 0, 20)
                : 0;
    }

    public static void showWarning() {
        if (list.count() <= 1)
            showTicks = timerTicks;
    }
}


