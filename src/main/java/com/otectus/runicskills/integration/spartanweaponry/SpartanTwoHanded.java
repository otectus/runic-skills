package com.otectus.runicskills.integration.spartanweaponry;

import com.oblivioussp.spartanweaponry.api.IWeaponTraitContainer;
import com.oblivioussp.spartanweaponry.api.WeaponTraits;
import com.otectus.runicskills.common.combat.TwoHandedWielding;
import net.minecraft.world.item.ItemStack;

/**
 * Spartan Weaponry's answer to "is this two-handed?".
 *
 * <p>Spartan declares weapon traits on the item, not the stack: every weapon class implements
 * {@link IWeaponTraitContainer}, and the two-handed weapons carry a trait whose type is
 * {@code WeaponTraits.TYPE_TWO_HANDED}. Asking the item by trait type — rather than matching the
 * registry path against a list of weapon families, which is what Titan's Grip used to do — means a
 * new Spartan weapon, an add-on's weapon, or a pack that removes the trait are all handled without
 * a code change.
 *
 * <p>Loaded reflectively by {@link TwoHandedWielding#installSource}, so no common class names
 * {@code com.oblivioussp}.
 */
public final class SpartanTwoHanded implements TwoHandedWielding.Source {

    @Override
    public String name() {
        return "spartanweaponry";
    }

    @Override
    public boolean isTwoHanded(ItemStack stack) {
        return stack.getItem() instanceof IWeaponTraitContainer<?> container
                && container.hasWeaponTraitWithType(WeaponTraits.TYPE_TWO_HANDED);
    }
}
