package com.otectus.runicskills.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A native-resolution icon with vanilla button keyboard, sound, tooltip and narration behavior. */
public final class PowersIconButton extends Button {
    public static final int SIZE = 20;
    private static final ResourceLocation ICON = new ResourceLocation("runicskills", "textures/gui/powers.png");

    public PowersIconButton(int x, int y, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.translatable("screen.runicskills.powers.open"),
                onPress, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(Component.translatable("screen.runicskills.powers.open.tooltip")));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int edge = !this.active ? 0xFF57535C : isFocused() ? 0xFFF1D590
                : isHovered() ? 0xFF9CE4D9 : 0xFF8C8696;
        graphics.fill(x, y, x + SIZE, y + SIZE, 0xFF211D2B);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1,
                this.active && isHoveredOrFocused() ? 0xFF454153 : 0xFF312D3D);
        graphics.fill(x, y, x + SIZE, y + 1, edge);
        graphics.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, edge);
        graphics.fill(x, y + 1, x + 1, y + SIZE - 1, edge);
        graphics.fill(x + SIZE - 1, y + 1, x + SIZE, y + SIZE - 1, edge);
        if (!this.active) graphics.setColor(0.45F, 0.45F, 0.45F, 1.0F);
        graphics.blit(ICON, x + 2, y + 2, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
