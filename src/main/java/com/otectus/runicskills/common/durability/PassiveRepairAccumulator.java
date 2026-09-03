package com.otectus.runicskills.common.durability;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Predicate;

/**
 * The perks that contribute to the once-per-second passive repair of a player's equipped gear.
 *
 * <p>There is exactly one, and this class exists to keep it that way. The list is a membership
 * decision rather than arithmetic, and for several releases it was where perks went when nobody had
 * implemented what their tooltip said: at its peak it summed seven, of which one described passive
 * repair. The test any candidate has to pass is still the same — <em>does the tooltip describe gear
 * mending itself over time, with no trigger?</em>
 *
 * <ul>
 *   <li><b>Auto Repair</b> — "Equipped items passively repair at %s rate". True passive repair;
 *       this perk is the reason the pass exists, and it is now the only term.</li>
 * </ul>
 *
 * <p>Everything else that used to be summed here now lives where the mechanic it names actually
 * happens:
 *
 * <ul>
 *   <li><b>Lucky Break</b> ("durability loss has a chance to be ignored") and <b>Precision Tools</b>
 *       ("tool durability increased by %s") — {@code MixItemStack}, on the {@code hurt} call that
 *       spends the point. Precision Tools converts its percentage to a per-point avoidance through
 *       {@link DurabilityMath#bonusDurabilityToAvoidance}, because a per-player perk cannot enlarge
 *       an item's pool but can decline to spend from it.</li>
 *   <li><b>Mending Boost</b> ("Mending repair rate increased") — {@code MixExperienceOrb}, where an
 *       orb is converted to durability.</li>
 *   <li><b>Tinker's Touch</b> ("items you craft gain %s bonus durability") — stamped onto the result
 *       in {@code MixCraftingMenu} and read back by {@code MixItemStack#getMaxDamage}.</li>
 *   <li><b>Tool Smith</b> ("repaired tools gain %s bonus efficiency") — stamped at the anvil by
 *       {@code AnvilPerkHandler} and read by {@code PerkEffectsHandler#onBreakSpeed}.</li>
 *   <li><b>Weapon Smith</b> ("repaired weapons gain %s bonus damage") — stamped at the anvil and
 *       read by {@code SmithingPerkHandler} as an attack-damage modifier.</li>
 *   <li><b>Runic Engineering</b> ("runic items gain %s bonus effects when repaired") — an
 *       enchantment level on repair, in {@code AnvilPerkHandler}.</li>
 *   <li><b>Heritage Builder</b> ("colony structures gain %s bonus durability") — the optional
 *       MineColonies integration, which is the only place colony structures exist.</li>
 * </ul>
 */
public final class PassiveRepairAccumulator {

    private PassiveRepairAccumulator() {
    }

    /**
     * Total repair rate, as a sum of configured percentages, for the perks the caller reports as
     * enabled. The sum has one term; it stays a sum because the shape of the question — "which
     * perks are passive repair?" — is what this class answers, and a future perk that genuinely is
     * passive repair belongs on a line beside Auto Repair rather than in a rewritten method.
     *
     * @param config  the configuration in force
     * @param enabled answers whether a perk is enabled for the player being ticked; passing the
     *                predicate rather than the player keeps this free of the capability lookup and
     *                of any assumption about which side it runs on
     */
    public static double rate(HandlerCommonConfig config, Predicate<RegistryObject<Perk>> enabled) {
        double rate = 0.0;
        if (enabled.test(RegistryPerks.AUTO_REPAIR)) rate += config.autoRepairPercent;
        return rate;
    }
}
