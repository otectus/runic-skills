package noppes.npcs.client.gui.player.tabs;

import net.minecraft.world.item.ItemStack;

/**
 * COMPILE-ONLY STUB of CustomNPCs' {@code InventoryTabVanilla}. See {@link AbstractTab} for what this
 * source set is and how drift is caught. Upstream declares a public no-arg constructor; Runic
 * Skills only ever instantiates it and calls the inherited {@code init(Screen)}.
 */
public class InventoryTabVanilla extends AbstractTab {

    public InventoryTabVanilla() {
        super(0, 0, 0, ItemStack.EMPTY);
    }

    @Override
    public void onTabClicked() {
    }

    @Override
    public boolean shouldAddToList() {
        return true;
    }
}
