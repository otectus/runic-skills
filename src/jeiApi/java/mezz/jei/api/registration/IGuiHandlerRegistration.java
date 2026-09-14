package mezz.jei.api.registration;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Compile-only public API signature verified against JEI 15.56.0.205. Never packaged. */
public interface IGuiHandlerRegistration {
    <T extends AbstractContainerScreen<?>> void addGuiContainerHandler(
            Class<? extends T> screenClass, IGuiContainerHandler<T> handler);
}
