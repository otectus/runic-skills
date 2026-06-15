package com.otectus.runicskills.integration;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class MowziesMobsIntegration {

    private static final String MOD_ID = "mowziesmobs";

    // Curated allow-list of Mowzie's Mobs 1.20.1 weapon IDs. The runtime resolver
    // (resolveMowzieWeapons) silently drops entries whose registry name is absent,
    // so adding/removing weapons here cannot crash the game — at worst Mowzie's
    // Might just won't trigger for an unlisted weapon.
    private static final Set<String> MOWZIE_WEAPON_PATHS = Set.of(
            "geomancer_staff",
            "ice_crystal",
            "naga_fang_dagger",
            "solar_spear_item",
            "axe_of_a_thousand_metals_item",
            "wrought_axe",
            "spear",
            "dart",
            "wing_blade",
            "spear_throwing"
    );

    private static volatile Set<Item> mowzieWeaponCache;

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static Set<Item> resolveMowzieWeapons() {
        Set<Item> cached = mowzieWeaponCache;
        if (cached != null) return cached;
        Set<Item> built = new HashSet<>();
        for (String path : MOWZIE_WEAPON_PATHS) {
            ResourceLocation rl = new ResourceLocation(MOD_ID, path);
            if (!ForgeRegistries.ITEMS.containsKey(rl)) continue;
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null) built.add(item);
        }
        mowzieWeaponCache = built;
        return built;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!isModLoaded()) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player) || player.isCreative()) return;

        Entity target = event.getEntity();

        // Boss Hunter: bonus damage against Mowzie's mobs (target-based).
        ResourceLocation targetType = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (targetType != null && MOD_ID.equals(targetType.getNamespace())) {
            if (RegistryPerks.BOSS_HUNTER != null && RegistryPerks.BOSS_HUNTER.get().isEnabled(player)) {
                float bonus = HandlerCommonConfig.HANDLER.instance().bossHunterPercent / 100.0f;
                event.setAmount(event.getAmount() * (1.0f + bonus));
            }
        }

        // Mowzie's Might: bonus damage when wielding a Mowzie weapon (item-based).
        // Composes multiplicatively with Boss Hunter; the player chose Strength + Mowzie
        // for exactly this synergy.
        if (RegistryPerks.MOWZIES_MIGHT != null && RegistryPerks.MOWZIES_MIGHT.get().isEnabled(player)) {
            Item mainHand = player.getMainHandItem().getItem();
            if (resolveMowzieWeapons().contains(mainHand)) {
                float bonus = HandlerCommonConfig.HANDLER.instance().mowziesMightPercent / 100.0f;
                event.setAmount(event.getAmount() * (1.0f + bonus));
            }
        }
    }
}
