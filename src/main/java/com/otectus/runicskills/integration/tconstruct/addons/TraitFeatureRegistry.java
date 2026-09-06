package com.otectus.runicskills.integration.tconstruct.addons;

import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runic's semantic capability keys, mapped to the modifier ids the installed add-ons really use.
 *
 * <p>§12.2 asks for exactly this and gives the reason: <em>never identify a modded benefit by
 * translated tooltip text.</em> A material called "mana steel" establishes nothing about a charging
 * API, so each key below names registry ids read out of the tested binary — TCIntegrations
 * {@code 2.0.25.19}, whose {@code assets/tcintegrations/lang/en_us.json} and modifier registration
 * class list them — rather than a name a player sees.
 *
 * <p><b>Why ids and not classes.</b> The adapter could ask {@code instanceof ManaModifier}, and that
 * would break the first time the add-on renamed a class or moved a trait onto a shared base. A
 * modifier id is the add-on's own public identity for the feature, it survives refactors, and it is
 * what a pack author would write. Levels are read through {@code IToolContext.getModifierLevel},
 * which answers for traits and upgrades alike.
 *
 * <p><b>Unknown is a real answer.</b> A key with no matching modifier on a tool means the tool does
 * not have that feature, and the perk that needed it does nothing — never a guess from the material
 * name, the namespace, or the fact that the add-on is installed.
 */
public final class TraitFeatureRegistry {

    /** One capability a perk needs to see, independent of which add-on happens to supply it. */
    public enum Feature {

        /**
         * A tool that repairs itself by spending Botania mana.
         *
         * <p>{@code tcintegrations:mana} — its {@code ManaModifier} requests exact mana for the
         * tool and then sets native damage directly, which is why the discount has to be applied at
         * the request rather than at a repair listener (§12.2).
         */
        BOTANIA_MANA_REPAIR(new ResourceLocation("tcintegrations", "mana")),

        /**
         * Armour that repairs itself by spending Ars Nouveau source.
         *
         * <p>{@code tcintegrations:ars_nouveau} — the armour modifier extending TCIntegrations'
         * base Ars modifier, which removes mana and updates tool damage in one inventory tick.
         */
        ARS_MANA_REPAIR(new ResourceLocation("tcintegrations", "ars_nouveau")),

        /**
         * A tool that can attack from the offhand.
         *
         * <p>{@code tcintegrations:mechanical_arm}, which extends Tinkers' own
         * {@code OffhandAttackModifier}: the attack is native, and Runic only observes it (§12.2).
         */
        OFFHAND_MELEE(new ResourceLocation("tcintegrations", "mechanical_arm")),

        /**
         * Equipment carrying Malum's soul-stained trait.
         *
         * <p>{@code tcintegrations:soul_stained}, applied by the {@code tcintegrations:soul_stained_steel}
         * material. Its own magical secondary damage stays its own; Runic reads presence only.
         */
        SOUL_STAINED(new ResourceLocation("tcintegrations", "soul_stained")),

        /**
         * A tool that gains its own experience and levels.
         *
         * <p>{@code tinkerslevellingaddon:improvable}. Player progression and tool progression stay
         * separate quantities (§12.3); this key only says which tools have the second one.
         */
        TOOL_LEVELLING(new ResourceLocation("tinkerslevellingaddon", "improvable")),

        /**
         * A weapon carrying one of Tinkers' Thinking's own melee modifiers.
         *
         * <p>Read out of {@code Tinkers-Thinking-0.1.6.6.3.jar}'s
         * {@code data/tinkers_thinking/tinkering/modifiers/}: these are every modifier that
         * declares a {@code tconstruct:conditional_melee_damage} module, plus the two that add
         * melee behaviour through an attribute or the add-on's own melee module. Nothing here was
         * taken from a tooltip or from the {@code modifer/melee/} package name, which does not
         * match the registered id set.
         *
         * <p>{@code clay}, {@code hellish}, {@code bane_of_pigs} and {@code overdose} are
         * conditional — the condition is the add-on's and stays the add-on's. This key says only
         * that the weapon carries the modifier, which is the whole of what the perk asks.
         */
        THINKING_EMBELLISHMENT(
                new ResourceLocation("tinkers_thinking", "attack_advanced"),
                new ResourceLocation("tinkers_thinking", "bane_of_pigs"),
                new ResourceLocation("tinkers_thinking", "clay"),
                new ResourceLocation("tinkers_thinking", "hellish"),
                new ResourceLocation("tinkers_thinking", "overdose"),
                new ResourceLocation("tinkers_thinking", "lightly_attack"),
                new ResourceLocation("tinkers_thinking", "sharp_circumstance")),

        /**
         * A Tinkers' Jewelry piece that can spend durability to refuse a death.
         *
         * <p>{@code tinkersjewelry:undying}. Its own {@code DamageItemEvents.undying} scans the
         * wearer's curios for a {@code ModifiableItem} carrying this modifier, calls
         * {@code ToolDamageUtil.damage} on it and cancels {@code LivingDeathEvent} — so the
         * durability the save costs arrives at the H1 seam like any other wear.
         */
        JEWELRY_UNDYING(new ResourceLocation("tinkersjewelry", "undying")),

        /**
         * A Tinkers' Jewelry piece carrying the modifier its own book calls a polish.
         *
         * <p>Recorded for completeness and <b>not</b> used as a repair signal.
         * {@code data/tinkersjewelry/tinkering/modifiers/polish.json} is a single
         * {@code tconstruct:modifier_slot} grant of one upgrade slot: it has no repair semantics of
         * any kind. Polished Facet therefore keys on the piece being a jewelry-material modifiable
         * item, which is what a station repair of one actually is.
         */
        JEWELRY_POLISH(new ResourceLocation("tinkersjewelry", "polish")),

        /**
         * Tinkers' Jewelry's private storage modifier. Reserved, and deliberately unused.
         *
         * <p>{@code tinkersjewelry:subspace} adds the {@code tinkersjewelry:subspace} attribute,
         * which sizes the container behind {@code SubSpaceCapability}. There is no Runic channel
         * for inventory volume, so {@code tc_subspace_reserve} is not registered; the key exists so
         * the diagnostic and the manifest can name the modifier they are declining to use.
         */
        JEWELRY_SUBSPACE(new ResourceLocation("tinkersjewelry", "subspace"));

        private final Set<ModifierId> ids;

        Feature(ResourceLocation... modifiers) {
            this.ids = List.of(modifiers).stream().map(ModifierId::new)
                    .collect(Collectors.toUnmodifiableSet());
        }

        /** The registry ids this key maps to, for the diagnostic and the tests. */
        public Set<ModifierId> ids() {
            return ids;
        }
    }

    private TraitFeatureRegistry() {
    }

    /** Whether {@code tool} carries any modifier this feature maps to. */
    public static boolean present(IToolContext tool, Feature feature) {
        if (tool == null || feature == null) return false;
        for (ModifierId id : feature.ids()) {
            if (tool.getModifierLevel(id) > 0) return true;
        }
        return false;
    }

    /**
     * The namespace Tinkers' Jewelry registers its materials under.
     *
     * <p>A namespace rather than the 37 ids read from
     * {@code data/tinkersjewelry/tinkering/materials/definition/}, and that is a deliberate
     * narrowing of §12.2's rule rather than a hole in it. The list is the add-on's content
     * catalogue, not its API: 1.2.0 ships {@code amethyst} through {@code xychorium_gem_red}, and
     * every point release adds more. A perk keyed on the enumerated list would silently stop
     * recognising a gem the day the add-on added one, which is a worse failure than the one the id
     * rule exists to prevent. What the perks actually need to know is "this material came from
     * Tinkers' Jewelry", and the namespace is the add-on's own answer to that.
     */
    public static final String JEWELRY_MATERIAL_NAMESPACE = "tinkersjewelry";
}
