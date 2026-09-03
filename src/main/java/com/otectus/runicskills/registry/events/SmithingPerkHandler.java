package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Weapon Smith — "Repaired weapons gain %s bonus damage" — read back off the weapon.
 *
 * <p>The producing half is {@code AnvilPerkHandler#applyRepairBonuses}, which stamps the configured
 * percentage onto the result of a repair. This is where that number becomes damage, and it is a
 * separate class from {@code PerkEffectsHandler} because it is not a perk effect at the point of
 * use: the perk belonged to whoever swung the hammer, and by the time this runs the item may be in
 * anyone's hands. There is deliberately no perk check here at all — the property travels with the
 * weapon, which is what the tooltip promises and what makes a smith's work worth trading for.
 *
 * <p>{@link ItemAttributeModifierEvent} rather than a {@code LivingHurtEvent} bonus, because an
 * attribute modifier is what vanilla renders: the item's tooltip grows a green "+10% Attack Damage"
 * line for free, and the value is consistent between the tooltip the client draws and the damage
 * the server computes. {@code MULTIPLY_TOTAL} makes it a percentage of the finished attack rather
 * than of the base weapon damage, so "+10%" means what a player reads it as.
 *
 * <p>Config values are baked into the item at repair time; see {@link ItemBonusTags}.
 *
 * <p><b>Hot path.</b> This event fires whenever attributes are recomputed — on every equip and
 * unequip, and on every tooltip render — for stacks that almost never carry the stamp. The slot
 * check and the allocation-free {@link ItemBonusTags#read} come before anything that allocates.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public class SmithingPerkHandler {

    /**
     * The identity of the Weapon Smith damage modifier, aliased from the one owner table.
     *
     * <p>Every modifier UUID this mod applies is declared in {@code RunicAttributeModifiers} and
     * nowhere else, so the owner inventory and the code that applies it cannot drift (RS10-002).
     * This one is {@code Scope.ITEM}: it lives on the weapon, and vanilla adds and removes it as
     * the stack is equipped.
     */
    private static final UUID WEAPON_SMITH_DAMAGE_UUID = RunicAttributeModifiers.WEAPON_SMITH_DAMAGE;

    /** The modifier's name; namespaced so a player reading NBT can see which mod added it. */
    private static final String WEAPON_SMITH_DAMAGE_NAME = "runicskills:weapon_smith";

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) return;

        ItemStack stack = event.getItemStack();
        int bonus = ItemBonusTags.read(stack, ItemBonusTags.WEAPON_SMITH);
        if (bonus <= 0) return;

        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                WEAPON_SMITH_DAMAGE_UUID, WEAPON_SMITH_DAMAGE_NAME, bonus / 100.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
