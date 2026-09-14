package mezz.jei.api.gui.handlers;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;

/** Compile-only public API signature verified against JEI 15.56.0.205. Never packaged. */
public interface IGuiContainerHandler<T extends AbstractContainerScreen<?>> {
    default List<Rect2i> getGuiExtraAreas(T screen) {
        throw new AssertionError("JEI API mirror — the real implementation loads at runtime");
    }
}
