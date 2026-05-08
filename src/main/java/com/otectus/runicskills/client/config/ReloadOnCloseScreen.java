package com.otectus.runicskills.client.config;

import com.otectus.runicskills.config.storage.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Wraps a YACL-generated config screen and re-loads the backing {@link ConfigHolder} after
 * the YACL screen closes. YACL writes user edits to disk on its own Save button; without
 * this wrapper, the {@code HandlerCommonConfig.HANDLER.instance()} POJO that the rest of
 * the mod reads would still hold the pre-edit values until the next {@code /skillsreload}
 * or join-time sync.
 *
 * <p>All interactive behaviour is delegated to the wrapped screen. Only {@link #onClose()}
 * is intercepted to perform the reload.
 */
public final class ReloadOnCloseScreen extends Screen {

    private final Screen delegate;
    private final Screen parent;
    private final ConfigHolder<?> holder;

    public ReloadOnCloseScreen(Screen delegate, Screen parent, ConfigHolder<?> holder) {
        super(delegate.getTitle() != null ? delegate.getTitle() : Component.empty());
        this.delegate = delegate;
        this.parent = parent;
        this.holder = holder;
    }

    @Override
    protected void init() {
        // Initialise the wrapped screen with the same dimensions and minecraft instance as us.
        Minecraft mc = Minecraft.getInstance();
        delegate.init(mc, this.width, this.height);
    }

    @Override
    public void resize(Minecraft mc, int width, int height) {
        super.resize(mc, width, height);
        delegate.resize(mc, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        delegate.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return delegate.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return delegate.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return delegate.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return delegate.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return delegate.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return delegate.charTyped(c, modifiers);
    }

    @Override
    public void tick() {
        delegate.tick();
    }

    @Override
    public void onClose() {
        // YACL has written user edits to disk by this point; reload the holder so the rest
        // of the mod sees the new values without requiring a /skillsreload.
        try {
            holder.load();
        } catch (Exception ignored) {
            // ConfigHolder.load() already logs failures; swallow here to avoid leaking the
            // exception into the screen-close path.
        }
        Minecraft.getInstance().setScreen(parent);
    }
}
