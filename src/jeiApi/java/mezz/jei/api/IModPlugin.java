package mezz.jei.api;

import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

/** Compile-only public API signatures verified against JEI 15.56.0.205. Never packaged. */
public interface IModPlugin {
    ResourceLocation getPluginUid();

    default void registerGuiHandlers(IGuiHandlerRegistration registration) {
        throw new AssertionError("JEI API mirror — the real implementation loads at runtime");
    }
}
