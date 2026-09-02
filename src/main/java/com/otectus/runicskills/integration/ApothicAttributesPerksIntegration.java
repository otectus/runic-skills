package com.otectus.runicskills.integration;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import dev.shadowsoffire.attributeslib.api.ALObjects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * The Apothic Attributes (attributeslib) half of the Apotheosis integration: ten pure-attribute
 * perks reconciled on a throttled tick against {@code ALObjects.Attributes.*}.
 *
 * <p>Split out of {@link ApotheosisIntegration} (1.5.3 review, M-5): that class previously
 * referenced ALObjects directly, so an AttributesLib version mismatch threw
 * {@code NoClassDefFoundError} during class init and took down the WHOLE integration — including
 * affix-rarity and gem gating, which never needed AttributesLib. Loaded separately via
 * {@code tryLoadIntegration}, a failure here now degrades only these attribute perks.</p>
 */
public class ApothicAttributesPerksIntegration {

    /**
     * Whether this integration should do anything right now: Apothic Attributes is installed
     * <em>and</em> {@code enableApotheosisIntegration} is on in the configuration in force.
     *
     * <p>The toggle used to be read once, in the mod constructor, to decide whether to register
     * this subscriber at all — so turning it off on a running server left the handlers registered
     * and firing, and turning it on could not register a subscriber that had been skipped
     * (RS10-011). The adapter is now registered whenever its upstream mod is present and every
     * entry point asks this instead, which makes the toggle work live in both directions.
     */
    public static boolean isActive() {
        return ApotheosisIntegration.isModLoaded() && ApothicAttributesIntegration.isModLoaded() && HandlerCommonConfig.HANDLER.instance().enableApotheosisIntegration;
    }


    // Stable UUIDs for each modifier on attributeslib attributes, owned centrally so the
    // migration purge cannot miss them (RS10-002).
    private static final UUID APOTH_CRIT_CHANCE_UUID   = RunicAttributeModifiers.APOTH_CRIT_CHANCE;
    private static final UUID APOTH_CRIT_DAMAGE_UUID   = RunicAttributeModifiers.APOTH_CRIT_DAMAGE;
    private static final UUID APOTH_LIFE_STEAL_UUID    = RunicAttributeModifiers.APOTH_LIFE_STEAL;
    private static final UUID APOTH_CURR_HP_DMG_UUID   = RunicAttributeModifiers.APOTH_CURR_HP_DMG;
    private static final UUID APOTH_DODGE_UUID         = RunicAttributeModifiers.APOTH_DODGE;
    private static final UUID APOTH_ARROW_DMG_UUID     = RunicAttributeModifiers.APOTH_ARROW_DMG;
    private static final UUID APOTH_ARROW_VEL_UUID     = RunicAttributeModifiers.APOTH_ARROW_VEL;
    private static final UUID APOTH_MINING_SPEED_UUID  = RunicAttributeModifiers.APOTH_MINING_SPEED;
    private static final UUID APOTH_XP_GAINED_UUID     = RunicAttributeModifiers.APOTH_XP_GAINED;
    private static final UUID APOTH_PROT_PIERCE_UUID   = RunicAttributeModifiers.APOTH_PROT_PIERCE;
    private static final UUID APOTH_PROT_SHRED_UUID    = RunicAttributeModifiers.APOTH_PROT_SHRED;
    private static final UUID APOTH_GHOST_HP_UUID      = RunicAttributeModifiers.APOTH_GHOST_HP;
    private static final UUID APOTH_HEAL_RECV_UUID     = RunicAttributeModifiers.APOTH_HEAL_RECV;
    private static final UUID APOTH_OVERHEAL_UUID      = RunicAttributeModifiers.APOTH_OVERHEAL;

    /**
     * Idempotent transient-modifier reconciliation, mirroring the ISS integration helper.
     *
     * <p>Transient, not permanent. This handler only exists when Apotheosis and AttributesLib are
     * both installed and the integration toggle is on; a permanent modifier would be serialised
     * into the player's attribute NBT and then survive removing any of those three, with nothing
     * left able to take it back off (RS10-002). The values below are re-derived every tenth tick,
     * so none of them needs to persist.
     */
    private static void reconcile(Player player, RegistryObject<Attribute> attrObj, UUID uuid,
                                  String name, boolean wanted, double value,
                                  AttributeModifier.Operation op) {
        // See IronsSpellbooksIntegration.reconcileModifier: gating here rather than at the tick
        // handler is what makes disabling the integration take the bonuses back off (RS10-011).
        wanted = wanted && isActive();
        if (attrObj == null || !attrObj.isPresent()) return;
        AttributeInstance inst = player.getAttribute(attrObj.get());
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(uuid);
        if (wanted) {
            if (existing != null && existing.getAmount() == value && existing.getOperation() == op) return;
            if (existing != null) inst.removeModifier(existing);
            inst.addTransientModifier(new AttributeModifier(uuid, name, value, op));
        } else if (existing != null) {
            inst.removeModifier(existing);
        }
    }

    @SubscribeEvent
    public void onPlayerTickApothicAttributes(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if ((player.tickCount % 10) != 0) return;

        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();

        // Apothic Critical Mastery → CRIT_CHANCE + CRIT_DAMAGE
        boolean critMastery = RegistryPerks.APOTHIC_CRITICAL_MASTERY != null
                && RegistryPerks.APOTHIC_CRITICAL_MASTERY.get().isEnabled(player);
        reconcile(player, ALObjects.Attributes.CRIT_CHANCE, APOTH_CRIT_CHANCE_UUID,
                "runicskills:apoth_crit_chance", critMastery, c.apothCriticalMasteryChancePercent / 100.0,
                AttributeModifier.Operation.ADDITION);
        reconcile(player, ALObjects.Attributes.CRIT_DAMAGE, APOTH_CRIT_DAMAGE_UUID,
                "runicskills:apoth_crit_damage", critMastery, c.apothCriticalMasteryDamagePercent / 100.0,
                AttributeModifier.Operation.ADDITION);

        // Vampiric Fangs → LIFE_STEAL
        reconcileSimple(player, RegistryPerks.VAMPIRIC_FANGS, ALObjects.Attributes.LIFE_STEAL,
                APOTH_LIFE_STEAL_UUID, "runicskills:vampiric_fangs",
                c.vampiricFangsPercent / 100.0);

        // Reaper's Edge → CURRENT_HP_DAMAGE
        reconcileSimple(player, RegistryPerks.REAPERS_EDGE, ALObjects.Attributes.CURRENT_HP_DAMAGE,
                APOTH_CURR_HP_DMG_UUID, "runicskills:reapers_edge",
                c.reapersEdgePercent / 100.0);

        // Evasive → DODGE_CHANCE
        reconcileSimple(player, RegistryPerks.EVASIVE, ALObjects.Attributes.DODGE_CHANCE,
                APOTH_DODGE_UUID, "runicskills:evasive",
                c.evasivePercent / 100.0);

        // Arrow Mastery → ARROW_DAMAGE + ARROW_VELOCITY (multiplicative)
        boolean arrowMastery = RegistryPerks.ARROW_MASTERY != null
                && RegistryPerks.ARROW_MASTERY.get().isEnabled(player);
        reconcile(player, ALObjects.Attributes.ARROW_DAMAGE, APOTH_ARROW_DMG_UUID,
                "runicskills:arrow_mastery_dmg", arrowMastery, c.arrowMasteryDamagePercent / 100.0,
                AttributeModifier.Operation.MULTIPLY_BASE);
        reconcile(player, ALObjects.Attributes.ARROW_VELOCITY, APOTH_ARROW_VEL_UUID,
                "runicskills:arrow_mastery_vel", arrowMastery, c.arrowMasteryVelocityPercent / 100.0,
                AttributeModifier.Operation.MULTIPLY_BASE);

        // Earthbreaker → MINING_SPEED (multiplicative)
        reconcileSimpleMul(player, RegistryPerks.EARTHBREAKER, ALObjects.Attributes.MINING_SPEED,
                APOTH_MINING_SPEED_UUID, "runicskills:earthbreaker",
                c.earthbreakerPercent / 100.0);

        // Apothic Scholar → EXPERIENCE_GAINED (multiplicative)
        reconcileSimpleMul(player, RegistryPerks.APOTHIC_SCHOLAR, ALObjects.Attributes.EXPERIENCE_GAINED,
                APOTH_XP_GAINED_UUID, "runicskills:apoth_scholar",
                c.apothScholarPercent / 100.0);

        // Spectral Ward → PROT_PIERCE (flat) + PROT_SHRED (percent 0..1)
        boolean spectralWard = RegistryPerks.SPECTRAL_WARD != null
                && RegistryPerks.SPECTRAL_WARD.get().isEnabled(player);
        reconcile(player, ALObjects.Attributes.PROT_PIERCE, APOTH_PROT_PIERCE_UUID,
                "runicskills:spectral_ward_pierce", spectralWard, c.spectralWardPierce,
                AttributeModifier.Operation.ADDITION);
        reconcile(player, ALObjects.Attributes.PROT_SHRED, APOTH_PROT_SHRED_UUID,
                "runicskills:spectral_ward_shred", spectralWard, c.spectralWardShredPercent / 100.0,
                AttributeModifier.Operation.ADDITION);

        // Ghostbound → GHOST_HEALTH (flat)
        reconcileSimpleFlat(player, RegistryPerks.GHOSTBOUND, ALObjects.Attributes.GHOST_HEALTH,
                APOTH_GHOST_HP_UUID, "runicskills:ghostbound",
                c.ghostboundBonus);

        // Heart of the Healer → HEALING_RECEIVED + OVERHEAL (multiplicative/percent)
        boolean heartHealer = RegistryPerks.HEART_OF_THE_HEALER != null
                && RegistryPerks.HEART_OF_THE_HEALER.get().isEnabled(player);
        reconcile(player, ALObjects.Attributes.HEALING_RECEIVED, APOTH_HEAL_RECV_UUID,
                "runicskills:heart_healer_recv", heartHealer, c.heartHealerReceivedPercent / 100.0,
                AttributeModifier.Operation.ADDITION);
        reconcile(player, ALObjects.Attributes.OVERHEAL, APOTH_OVERHEAL_UUID,
                "runicskills:heart_healer_overheal", heartHealer, c.heartHealerOverhealPercent / 100.0,
                AttributeModifier.Operation.ADDITION);
    }

    private static void reconcileSimple(Player player, RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                        RegistryObject<Attribute> attr, UUID uuid, String name, double value) {
        boolean enabled = perk != null && perk.get().isEnabled(player);
        reconcile(player, attr, uuid, name, enabled, enabled ? value : 0,
                AttributeModifier.Operation.ADDITION);
    }

    private static void reconcileSimpleMul(Player player, RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                           RegistryObject<Attribute> attr, UUID uuid, String name, double value) {
        boolean enabled = perk != null && perk.get().isEnabled(player);
        reconcile(player, attr, uuid, name, enabled, enabled ? value : 0,
                AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private static void reconcileSimpleFlat(Player player, RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                            RegistryObject<Attribute> attr, UUID uuid, String name, double value) {
        boolean enabled = perk != null && perk.get().isEnabled(player);
        reconcile(player, attr, uuid, name, enabled, enabled ? value : 0,
                AttributeModifier.Operation.ADDITION);
    }
}
