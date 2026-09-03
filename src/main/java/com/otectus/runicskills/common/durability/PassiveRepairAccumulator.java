package com.otectus.runicskills.common.durability;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Predicate;

/**
 * The perks that contribute to the once-per-second passive repair of a player's equipped gear.
 *
 * <p>This list is a membership decision, not arithmetic, which is why it is a class of its own: it
 * accumulated perks that promise something else and then quietly implemented them as repair. Lucky
 * Break ("durability loss has a chance to be ignored") and Mending Boost ("Mending repair rate
 * increased") were both summed in here through 2.0.4 and are now implemented where the mechanics
 * they name actually happen — {@code MixItemStack} and {@code MixExperienceOrb} respectively
 * (RS-205-01/02). Anything added here in future has to survive the same question: does the tooltip
 * describe gear mending itself over time, with no trigger?
 *
 * <p>Standing of each current contributor against its {@code en_us.json} tooltip:
 *
 * <ul>
 *   <li><b>Auto Repair</b> — "Equipped items passively repair at %s rate". True passive repair;
 *       this perk is the reason the pass exists.</li>
 *   <li><b>Precision Tools</b> — "Tool durability increased by %s". Describes a larger durability
 *       pool, not repair over time.</li>
 *   <li><b>Runic Engineering</b> — "Runic items gain %s bonus effects when repaired". Describes an
 *       effect granted <em>by</em> a repair, not a repair.</li>
 *   <li><b>Tinker's Touch</b> — "Items you craft gain %s bonus durability". A property applied at
 *       craft time.</li>
 *   <li><b>Tool Smith</b> — "Repaired tools gain %s bonus efficiency". A property granted by a
 *       repair.</li>
 *   <li><b>Weapon Smith</b> — "Repaired weapons gain %s bonus damage". Likewise.</li>
 *   <li><b>Heritage Builder</b> — "Colony structures gain %s bonus durability". Concerns
 *       structures, not carried gear.</li>
 * </ul>
 *
 * <p>Only Auto Repair is unambiguously passive repair. The other six are kept here as they were,
 * because changing what they do is a design decision and not part of the change that moved Lucky
 * Break and Mending Boost out; they are recorded above so the next pass starts from a list rather
 * than from a re-reading of the language file.
 */
public final class PassiveRepairAccumulator {

    private PassiveRepairAccumulator() {
    }

    /**
     * Total repair rate, as a sum of configured percentages, for the perks the caller reports as
     * enabled.
     *
     * @param config  the configuration in force
     * @param enabled answers whether a perk is enabled for the player being ticked; passing the
     *                predicate rather than the player keeps this free of the capability lookup and
     *                of any assumption about which side it runs on
     */
    public static double rate(HandlerCommonConfig config, Predicate<RegistryObject<Perk>> enabled) {
        double rate = 0.0;
        if (enabled.test(RegistryPerks.AUTO_REPAIR))       rate += config.autoRepairPercent;
        if (enabled.test(RegistryPerks.PRECISION_TOOLS))   rate += config.precisionToolsPercent;
        if (enabled.test(RegistryPerks.RUNIC_ENGINEERING)) rate += config.runicEngineeringPercent;
        if (enabled.test(RegistryPerks.TINKERS_TOUCH))     rate += config.tinkersTouchPercent;
        if (enabled.test(RegistryPerks.TOOL_SMITH))        rate += config.toolSmithPercent;
        if (enabled.test(RegistryPerks.WEAPON_SMITH))      rate += config.weaponSmithPercent;
        if (enabled.test(RegistryPerks.HERITAGE_BUILDER))  rate += config.heritageBuilderPercent;
        return rate;
    }
}
