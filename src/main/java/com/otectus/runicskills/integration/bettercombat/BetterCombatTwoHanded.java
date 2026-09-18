package com.otectus.runicskills.integration.bettercombat;

import com.otectus.runicskills.common.combat.TwoHandedWielding;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * Better Combat's answer to "is this two-handed?".
 *
 * <p>Better Combat keeps weapon attributes in a datapack-driven registry keyed by item id (with an
 * NBT override per stack), and {@code WeaponRegistry.getAttributes(ItemStack)} is the public read.
 * A stack the pack has not described returns {@code null}, which is not an error — it just means
 * this mod has no opinion about it.
 *
 * <p>This is the same predicate Better Combat's own {@code PlayerEntityMixin} uses to decide to
 * hide the off-hand, which is deliberate: the exemption must trigger on exactly the stacks that
 * caused the hiding, never on a superset.
 *
 * <p>Loaded reflectively by {@link TwoHandedWielding#installSource}, so no common class names
 * {@code net.bettercombat}.
 */
public final class BetterCombatTwoHanded implements TwoHandedWielding.Source {

    @Override
    public String name() {
        return "bettercombat";
    }

    @Override
    public boolean isTwoHanded(ItemStack stack) {
        WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
        return attributes != null && attributes.isTwoHanded();
    }
}
