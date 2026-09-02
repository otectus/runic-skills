package com.otectus.runicskills.client.event;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.gui.DrawTabs;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Clears {@link DrawTabs}' click latch when the vanilla inventory closes.
 *
 * <p>{@code DrawTabs.checkMouse} is set by {@code mouseClicked} and consumed by the next
 * {@code DrawTabs.render}. If the click that armed it is also the click that closed the screen,
 * the latch survives into the next screen and fires a tab switch nobody asked for, so it has to be
 * cleared on close.
 *
 * <p><b>Why this is not in {@code MixInventoryScreen}.</b> That is where the reset used to live, as
 * a plain {@code public void onClose()} on the mixin. {@code InventoryScreen} does not declare
 * {@code onClose} — it inherits it — so the mixin method was an <i>implicit overwrite</i> rather
 * than an injection, and any other mod doing the same thing to the same class wins. Highlighter
 * does exactly that, and Mixin resolved the tie against us on every start:
 * {@code Method overwrite conflict for m_7379_ in runicskills.mixins.json:MixInventoryScreen,
 * previously written by com.anthonyhilyard.highlighter.mixin.InventoryScreenMixin. Skipping method.}
 * The reset simply never ran. An {@code @Inject} was not an option either, for the same reason the
 * overwrite happened: there is no {@code onClose} in the target class to inject into.
 *
 * <p>Forge's {@link ScreenEvent.Closing} is fired from {@code Minecraft#setScreen} for the outgoing
 * screen, which is the single funnel every close path reaches — Escape, the inventory key, and
 * opening a different screen all end in {@code setScreen}, and {@code Screen#onClose} itself routes
 * through {@code popGuiLayer}, which delegates to {@code setScreen(null)} when no Forge GUI layer is
 * stacked. Listening there composes with every other mod instead of competing with it.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT)
public final class InventoryTabsCloseHandler {

    private InventoryTabsCloseHandler() {
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof InventoryScreen) {
            DrawTabs.onClose();
        }
    }
}
