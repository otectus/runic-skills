package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;
import net.minecraft.world.item.*;

/** Verified vanilla native material identities; unknown modded tiers keep the documented fallback. */
public final class NativeMaterialLocks {
    public enum Outcome { REQUIREMENTS, UNRESTRICTED, UNHANDLED, UNDETERMINED }
    public record Resolution(Outcome outcome, String evidence, Integer reference, LockItem rule) {}
    private NativeMaterialLocks() {}

    public static Resolution resolve(String id, Item item, int fallback, float multiplier) {
        if (item == null || item instanceof BlockItem || item.isEdible()
                || (!(item instanceof TieredItem) && id.matches(".*_(head|blade|handle|part|blueprint|ingot|nugget|repair_kit)$")))
            return new Resolution(Outcome.UNHANDLED, "native non-equipment/component exclusion", null, null);
        Integer tier = null;
        String evidence = "unhandled non-equipment";
        if (item instanceof TieredItem tool && tool.getTier() instanceof Tiers nativeTier) {
            tier = switch (nativeTier) { case WOOD -> 0; case STONE -> 2; case GOLD -> 6;
                case IRON -> 8; case DIAMOND -> 16; case NETHERITE -> 24; };
            evidence = "native vanilla tier: " + nativeTier.name();
        } else if (item instanceof ArmorItem armor && armor.getMaterial() instanceof ArmorMaterials material) {
            tier = switch (material) { case LEATHER -> 0; case CHAIN -> 4; case GOLD -> 6; case IRON -> 8;
                case DIAMOND -> 16; case NETHERITE -> 24; case TURTLE -> 6; };
            evidence = "native vanilla armor material: " + material.name();
        }
        if (tier != null && tier == 0) return new Resolution(Outcome.UNRESTRICTED, evidence, 0, LockItem.unrestricted(id));
        LockItem generated = LockGen.gearLock(id, tier == null ? fallback : tier, multiplier);
        if (generated == null) return new Resolution(Outcome.UNHANDLED, evidence, null, null);
        if (tier == null) evidence = "namespace fallback; material not verified";
        return new Resolution(tier == null ? Outcome.UNDETERMINED : Outcome.REQUIREMENTS, evidence, tier, generated);
    }
}
