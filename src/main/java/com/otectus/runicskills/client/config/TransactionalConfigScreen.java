package com.otectus.runicskills.client.config;

import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Save failures stay on the actual editing screen with the detached draft available for retry. */
public final class TransactionalConfigScreen extends YACLScreen {
    private String failure;
    public TransactionalConfigScreen(YetAnotherConfigLib config, Screen parent) { super(config, parent); }
    @Override public void finishOrSave() {
        try {
            if (failure != null && !pendingChanges()) config.saveFunction().run();
            super.finishOrSave();
            failure = null;
        } catch (RuntimeException e) {
            failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            setSaveButtonMessage(Component.translatable("runicskills.config.save_failed"), Component.literal(failure));
        }
    }
    @Override public void tick() {
        if (failure != null) setSaveButtonMessage(Component.translatable("runicskills.config.save_failed"), Component.literal(failure));
        super.tick();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        if (failure != null) {
            var lines = font.split(Component.translatable("runicskills.config.save_failed_detail"), width - 16);
            graphics.flush(); graphics.pose().pushPose(); graphics.pose().translate(0, 0, 1000);
            graphics.fill(0, 22, width, 30 + lines.size() * font.lineHeight, 0xFF501010);
            for (int i=0; i<lines.size(); i++) graphics.drawString(font, lines.get(i), 8, 26+i*font.lineHeight, 0xFFFFFF);
            graphics.flush(); graphics.pose().popPose();
        }
    }
}
