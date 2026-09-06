package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.TcAddonPresence;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Status;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

/**
 * The negative half of §18.3 C08 for the add-on perks: what happens when the add-on is not there.
 *
 * <p>Registered whenever Tinkers' is loaded, so it runs in the plain M1 profile — where none of the
 * add-ons are installed and every one of these perks must be dormant — and again in the add-on
 * profiles, where the same assertions must still hold for whichever add-ons that profile leaves out.
 * That is why each case is written as an invariant rather than as an expected value: "the perk
 * exists exactly when its add-on does" and "the capability is ABSENT exactly when something it needs
 * is missing" are true in every profile, and a test that only passed in one of them would stop
 * proving anything the moment the profile changed.
 *
 * <p>The reserved id is the exception, and deliberately so: {@code tc_medallion_concord} has no
 * artifact anywhere, so it is unregistered in every profile and its capability reads
 * {@link Status#UPSTREAM_UNAVAILABLE} in every profile.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests}.
 */
@PrefixGameTestTemplate(false)
public class TcAddonAbsenceGameTest {

    private static final String EMPTY = "empty";

    /** A perk is registered exactly when the add-on that owns it is installed. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void addonPerksExistOnlyWithTheirAddon(GameTestHelper helper) {
        expectPerk(RegistryPerks.TC_MANA_POLISHER, TcAddonPresence.TCINTEGRATIONS, "tc_mana_polisher");
        expectPerk(RegistryPerks.TC_SOURCE_TEMPERING, TcAddonPresence.TCINTEGRATIONS, "tc_source_tempering");
        expectPerk(RegistryPerks.TC_CLOCKWORK_ALTERNATION, TcAddonPresence.TCINTEGRATIONS,
                "tc_clockwork_alternation");
        expectPerk(RegistryPerks.TC_SOULSTEEL_RESOLVE, TcAddonPresence.TCINTEGRATIONS, "tc_soulsteel_resolve");
        expectPerk(RegistryPerks.TC_SEASONED_HANDS, TcAddonPresence.TINKERS_LEVELLING, "tc_seasoned_hands");
        expectPerk(RegistryPerks.TC_BANQUET_OF_CINDERS, TcAddonPresence.TINKERS_DELIGHT, "tc_banquet_of_cinders");
        expectPerk(RegistryPerks.TC_CHARGED_CRAFT, TcAddonPresence.TINKERS_ADVANCED, "tc_charged_craft");
        expectPerk(RegistryPerks.TC_THINKING_LAST_THOUGHT, TcAddonPresence.TINKERS_THINKING,
                "tc_thinking_last_thought");
        expectPerk(RegistryPerks.TC_THINKING_STUDIED_RECALL, TcAddonPresence.TINKERS_THINKING,
                "tc_thinking_studied_recall");
        expectPerk(RegistryPerks.TC_THINKING_EMBELLISHED_FOCUS, TcAddonPresence.TINKERS_THINKING,
                "tc_thinking_embellished_focus");
        expectPerk(RegistryPerks.TC_JEWELER_SETTING, TcAddonPresence.TINKERS_JEWELRY, "tc_jeweler_setting");
        expectPerk(RegistryPerks.TC_GEM_ATTUNEMENT, TcAddonPresence.TINKERS_JEWELRY, "tc_gem_attunement");
        expectPerk(RegistryPerks.TC_UNDYING_LUSTRE, TcAddonPresence.TINKERS_JEWELRY, "tc_undying_lustre");
        expectPerk(RegistryPerks.TC_POLISHED_FACET, TcAddonPresence.TINKERS_JEWELRY, "tc_polished_facet");
        helper.succeed();
    }

    /** A capability is available only when every mod it needs is installed, and says which is not. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void addonCapabilitiesNameWhatIsMissing(GameTestHelper helper) {
        expectCapability(Capability.ADDON_BOTANIA_REPAIR_CHARGE,
                TcAddonPresence.TCINTEGRATIONS, TcAddonPresence.BOTANIA);
        expectCapability(Capability.ADDON_ARS_ARMOR_REPAIR,
                TcAddonPresence.TCINTEGRATIONS, TcAddonPresence.ARS_NOUVEAU);
        expectCapability(Capability.ADDON_OFFHAND_MELEE,
                TcAddonPresence.TCINTEGRATIONS, TcAddonPresence.CREATE);
        expectCapability(Capability.ADDON_SOUL_STAINED,
                TcAddonPresence.TCINTEGRATIONS, TcAddonPresence.MALUM);
        expectCapability(Capability.ADDON_TOOL_LEVELLING, TcAddonPresence.TINKERS_LEVELLING);
        expectCapability(Capability.ADDON_CULINARY_EFFECT,
                TcAddonPresence.TINKERS_DELIGHT, TcAddonPresence.FARMERS_DELIGHT);
        expectCapability(Capability.ADDON_TOOL_ENERGY,
                TcAddonPresence.TINKERS_ADVANCED, TcAddonPresence.ETSTLIB);
        expectCapability(Capability.ADDON_THINKING_DEATH_TRIGGER, TcAddonPresence.TINKERS_THINKING);
        expectCapability(Capability.ADDON_THINKING_XP_TRIGGER, TcAddonPresence.TINKERS_THINKING);
        expectCapability(Capability.ADDON_THINKING_EMBELLISHMENT, TcAddonPresence.TINKERS_THINKING);
        expectCapability(Capability.ADDON_JEWELRY_MATERIAL, TcAddonPresence.TINKERS_JEWELRY);
        expectCapability(Capability.ADDON_JEWELRY_GEM_ATTRIBUTES,
                TcAddonPresence.TINKERS_JEWELRY, TcAddonPresence.CURIOS);
        expectCapability(Capability.ADDON_JEWELRY_UNDYING,
                TcAddonPresence.TINKERS_JEWELRY, TcAddonPresence.CURIOS);
        expectCapability(Capability.ADDON_JEWELRY_POLISH, TcAddonPresence.TINKERS_JEWELRY);
        helper.succeed();
    }

    /**
     * Medallion Concord is reserved, not shipped: no perk, and a diagnostic that says why.
     *
     * <p>§10.3 forbids a selectable no-op. The id survives so that a save which somehow holds it is
     * not corrupted by its absence, and the reason survives so a player can be told the difference
     * between "you did not install it" and "there is nothing to install".
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void medallionConcordIsReservedAndUnregistered(GameTestHelper helper) {
        for (Perk perk : RegistryPerks.PERKS_REGISTRY.get().getValues()) {
            if ("tc_medallion_concord".equals(perk.getName())) {
                throw new GameTestAssertException("tc_medallion_concord is registered; §10.3 says a "
                        + "content id with no upstream artifact stays reserved rather than shipping "
                        + "as a selectable no-op");
            }
        }
        TConstructCompatibilityStatus.Entry entry =
                TConstructCompatibilityStatus.current().entry(Capability.ADDON_MEDALLION);
        if (entry.status() != Status.UPSTREAM_UNAVAILABLE) {
            throw new GameTestAssertException("the medallion capability reports " + entry.status()
                    + "; it must be UPSTREAM_UNAVAILABLE in every profile");
        }
        if (!entry.reason().contains("Ingenuity")) {
            throw new GameTestAssertException("the medallion reason does not name the add-on that "
                    + "does not exist: " + entry.reason());
        }
        helper.succeed();
    }

    /**
     * Subspace is reserved, not shipped, for the reason the jar gave rather than a missing artifact.
     *
     * <p>The sibling of the medallion case one class of failure over. Tinkers' Jewelry <em>is</em>
     * published and the {@code tinkersjewelry:subspace} modifier <em>does</em> exist; what does not
     * exist is a Runic channel for private inventory volume. §10.3's rule is the same either way,
     * and so is the invariant: no perk in any profile, and a diagnostic that says which of the two
     * reasons applies.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void subspaceIsReservedAndUnregistered(GameTestHelper helper) {
        for (Perk perk : RegistryPerks.PERKS_REGISTRY.get().getValues()) {
            if ("tc_subspace_reserve".equals(perk.getName())) {
                throw new GameTestAssertException("tc_subspace_reserve is registered; the subspace "
                        + "modifier is inventory storage and has no channel to join, so §10.3 keeps "
                        + "the id reserved rather than shipping a selectable no-op");
            }
        }
        TConstructCompatibilityStatus.Entry entry =
                TConstructCompatibilityStatus.current().entry(Capability.ADDON_JEWELRY_SUBSPACE);
        if (entry.status() != Status.UPSTREAM_UNAVAILABLE) {
            throw new GameTestAssertException("the subspace capability reports " + entry.status()
                    + "; it must be UPSTREAM_UNAVAILABLE in every profile");
        }
        if (!entry.reason().contains("inventory storage")) {
            throw new GameTestAssertException("the subspace reason does not say why there is no "
                    + "channel for it: " + entry.reason());
        }
        helper.succeed();
    }

    /**
     * Tinkers' Katanas is data-only, and the diagnostic says so rather than inventing an adapter.
     *
     * <p>The jar ships no Java classes at all: its katana and fuma shuriken are JsonThings tool
     * definitions registered into {@code tconstruct:modifiable/melee/primary}, {@code /ranged},
     * {@code /one_handed}, {@code /durability} and {@code /bonus_slots}. So the invariant is the
     * opposite of every other add-on's — installed means SUPPORTED with no adapter, no perk and no
     * config flag, because the core seams already reach the content.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void katanasIsDataOnlyAndNeedsNoAdapter(GameTestHelper helper) {
        TConstructCompatibilityStatus.Entry entry =
                TConstructCompatibilityStatus.current().entry(Capability.ADDON_KATANAS_CONTENT);
        boolean loaded = TcAddonPresence.isLoaded(TcAddonPresence.TINKERS_KATANAS);
        Status expected = loaded ? Status.SUPPORTED : Status.ABSENT;
        if (entry.status() != expected) {
            throw new GameTestAssertException("tinkers_katanas loaded=" + loaded + " but the "
                    + "capability reports " + entry.status() + " rather than " + expected);
        }
        if (loaded && !entry.reason().contains("no adapter")) {
            throw new GameTestAssertException("the katanas reason does not say the content is "
                    + "covered without an adapter: " + entry.reason());
        }
        helper.succeed();
    }

    private static void expectPerk(RegistryObject<Perk> perk, String modId, String id) {
        boolean loaded = TcAddonPresence.isLoaded(modId);
        if (loaded && perk == null) {
            throw new GameTestAssertException(modId + " is installed but " + id + " is dormant");
        }
        if (!loaded && perk != null) {
            throw new GameTestAssertException(modId + " is not installed but " + id
                    + " was registered; an add-on perk must not exist without its add-on");
        }
    }

    private static void expectCapability(Capability capability, String... required) {
        TConstructCompatibilityStatus.Entry entry =
                TConstructCompatibilityStatus.current().entry(capability);
        String missing = null;
        for (String modId : required) {
            if (!TcAddonPresence.isLoaded(modId)) {
                missing = modId;
                break;
            }
        }
        if (missing == null) {
            if (entry.status() != Status.SUPPORTED) {
                throw new GameTestAssertException(capability + " reports " + entry.status() + " ("
                        + entry.reason() + ") with every mod it needs installed");
            }
            return;
        }
        if (entry.status() != Status.ABSENT) {
            throw new GameTestAssertException(capability + " reports " + entry.status()
                    + " while " + missing + " is not installed; it must be ABSENT");
        }
        if (entry.reason() == null || entry.reason().isBlank()) {
            throw new GameTestAssertException(capability + " is ABSENT with no reason");
        }
    }
}
