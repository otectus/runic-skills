package com.otectus.runicskills.integration;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
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

    // Stable UUIDs for each permanent modifier on attributeslib attributes.
    private static final UUID APOTH_CRIT_CHANCE_UUID   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c01");
    private static final UUID APOTH_CRIT_DAMAGE_UUID   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c02");
    private static final UUID APOTH_LIFE_STEAL_UUID    = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c03");
    private static final UUID APOTH_CURR_HP_DMG_UUID   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c04");
    private static final UUID APOTH_DODGE_UUID         = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c05");
    private static final UUID APOTH_ARROW_DMG_UUID     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c06");
    private static final UUID APOTH_ARROW_VEL_UUID     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c07");
    private static final UUID APOTH_MINING_SPEED_UUID  = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c08");
    private static final UUID APOTH_XP_GAINED_UUID     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c09");
    private static final UUID APOTH_PROT_PIERCE_UUID   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0a");
    private static final UUID APOTH_PROT_SHRED_UUID    = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0b");
    private static final UUID APOTH_GHOST_HP_UUID      = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0c");
    private static final UUID APOTH_HEAL_RECV_UUID     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0d");
    private static final UUID APOTH_OVERHEAL_UUID      = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0e");

    /** Idempotent permanent-modifier reconciliation, mirroring the ISS integration helper. */
    private static void reconcile(Player player, RegistryObject<Attribute> attrObj, UUID uuid,
                                  String name, boolean wanted, double value,
                                  AttributeModifier.Operation op) {
        if (attrObj == null || !attrObj.isPresent()) return;
        AttributeInstance inst = player.getAttribute(attrObj.get());
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(uuid);
        if (wanted) {
            if (existing != null && existing.getAmount() == value && existing.getOperation() == op) return;
            if (existing != null) inst.removeModifier(existing);
            inst.addPermanentModifier(new AttributeModifier(uuid, name, value, op));
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
