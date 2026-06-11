package com.otectus.runicskills.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Vanilla-only fallback shown when the YACL config UI cannot be built — either YACL is absent,
 * or a present-but-incompatible YACL version throws a {@link LinkageError} while the screen is
 * constructed (e.g. a modpack author installed "the most recent" YACL whose API has drifted from
 * the {@code 3.5.0} build this mod compiles against).
 *
 * <p>This class references no {@code dev.isxander.yacl3.*} type, so it is safe to classload from
 * the config-screen factory even when YACL is missing from the runtime classpath. It exists to turn
 * the previous silent no-op ("clicking Configure does nothing") into a visible pointer at the log,
 * per the audit's definition of done.
 */
public final class YaclUnavailableScreen extends Screen {

    private final Screen parent;
    private final Component body;

    public YaclUnavailableScreen(Screen parent, Component body) {
        super(Component.translatable("runicskills.config.unavailable.title"));
        this.parent = parent;
        this.body = body;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 100, this.height / 2 + 50, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);

        List<FormattedCharSequence> lines = this.font.split(this.body, this.width - 80);
        int y = this.height / 2 - 28;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, (this.width - this.font.width(line)) / 2, y, 0xC0C0C0);
            y += this.font.lineHeight + 2;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
