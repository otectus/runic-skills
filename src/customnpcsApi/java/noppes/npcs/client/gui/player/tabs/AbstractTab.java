package noppes.npcs.client.gui.player.tabs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * COMPILE-ONLY STUB of CustomNPCs' {@code noppes.npcs.client.gui.player.tabs.AbstractTab}.
 *
 * <p>This is not upstream code. It is a hand-written signature-only mirror of the surface Runic
 * Skills compiles against, so a fresh clone builds with no third-party jar present — CustomNPCs
 * has no Maven coordinate, and our Skills tab genuinely subclasses this type in order to inherit
 * CustomNPCs' own tab rendering, hover brightening and selected state verbatim. The bodies are
 * unreachable: this source set is {@code compileOnly} and never ships in the mod jar, and at
 * runtime the real CustomNPCs classes are the ones loaded.
 *
 * <p>Verified with {@code javap} against
 * {@code CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260711.jar}. These members must stay
 * byte-compatible with it:
 * <ul>
 *   <li>{@code <init>(IIILnet/minecraft/world/item/ItemStack;)V}</li>
 *   <li>{@code init(Lnet/minecraft/client/gui/screens/Screen;)Lnoppes/npcs/client/gui/player/tabs/AbstractTab;}</li>
 *   <li>{@code onTabClicked()V}</li>
 *   <li>{@code shouldAddToList()Z}</li>
 *   <li>{@code public int id}</li>
 *   <li>{@code protected java.lang.Class screenClass}</li>
 * </ul>
 *
 * <p>Drift is not a compile error at the consumer's end, so it is caught at runtime instead: the
 * reflective probe in {@code CustomNpcsIntegration#registerClientTabs()} checks the constructor,
 * {@code init(Screen)}, {@code id} and {@code onTabClicked} before the client integration class is
 * ever loaded, and falls back to Runic Skills' own tab strip if any of them is missing.
 */
public abstract class AbstractTab extends AbstractButton {

    public int id;
    ResourceLocation texture;
    ItemStack renderStack;
    protected boolean hover;
    protected Class screenClass;

    public AbstractTab(int id, int x, int y, ItemStack icon) {
        super(x, y, 28, 32, Component.literal(""));
        this.id = id;
        this.renderStack = icon;
    }

    /**
     * Positions the tab for the given screen. Upstream computes a per-screen anchor and then sets
     * {@code x = anchorX + id * 28}, {@code y = anchorY - 28}, returning {@code this}.
     */
    public AbstractTab init(Screen screen) {
        return this;
    }

    public abstract void onTabClicked();

    public abstract boolean shouldAddToList();

    // Concrete in the real class. Left concrete here so our subclass is not forced to override
    // CustomNPCs' rendering: overriding it would replace the very drawing we subclass to inherit.
    @Override
    public void onPress() {
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
