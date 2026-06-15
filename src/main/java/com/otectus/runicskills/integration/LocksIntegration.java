package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class LocksIntegration {

    private static final String MOD_ID = "locks";
    private static final ResourceLocation KEY_ITEM_ID = new ResourceLocation(MOD_ID, "key");
    private static final ResourceLocation MASTER_KEY_ITEM_ID = new ResourceLocation(MOD_ID, "master_key");

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    // Key Forge: when the player crafts a basic locks:key, roll keyForgePercent
    // chance to substitute a locks:master_key of the same count. The substitution
    // works by zeroing the just-crafted key stack and adding a master_key to the
    // player's inventory. If the inventory has no room, the master_key drops at
    // the player's feet — standard Forge behavior.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!isModLoaded()) return;
        Player player = event.getEntity();
        if (player == null || player instanceof FakePlayer) return;
        if (RegistryPerks.KEY_FORGE == null || !RegistryPerks.KEY_FORGE.get().isEnabled(player)) return;

        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;

        ResourceLocation craftedId = ForgeRegistries.ITEMS.getKey(crafted.getItem());
        if (!KEY_ITEM_ID.equals(craftedId)) return;

        Item masterKey = ForgeRegistries.ITEMS.getValue(MASTER_KEY_ITEM_ID);
        if (masterKey == null) return;

        int chance = HandlerCommonConfig.HANDLER.instance().keyForgePercent;
        if (chance <= 0) return;
        if (ThreadLocalRandom.current().nextInt(100) >= chance) return;

        int count = crafted.getCount();
        crafted.setCount(0);
        ItemStack masterStack = new ItemStack(masterKey, count);
        if (!player.getInventory().add(masterStack)) {
            player.drop(masterStack, false);
        }
    }

    // --- Material Tiers ---

    private enum MaterialTier {
        WOOD("wood", 4, 0),
        COPPER("copper", 8, 0),
        GOLD("gold", 8, 0),
        IRON("iron", 10, 0),
        STEEL("steel", 14, 8),
        DIAMOND("diamond", 20, 12),
        NETHERITE("netherite", 28, 18);

        final String name;
        final int tinkeringLevel;
        final int dexterityLevel;

        MaterialTier(String name, int tinkeringLevel, int dexterityLevel) {
            this.name = name;
            this.tinkeringLevel = tinkeringLevel;
            this.dexterityLevel = dexterityLevel;
        }
    }

    // --- Main Entry Point ---

    public static List<LockItem> generateLockItems() {
        if (!HandlerCommonConfig.HANDLER.instance().locksEnableLockItems) {
            return List.of();
        }

        float multiplier = HandlerCommonConfig.HANDLER.instance().locksLevelMultiplier;
        List<LockItem> items = new ArrayList<>();

        generateLocks(items, multiplier);
        generateLockPicks(items, multiplier);
        generateMechanisms(items, multiplier);
        generateKeyItems(items, multiplier);

        RunicSkills.getLOGGER().debug("Locks Reforged Integration: Generated {} lock items", items.size());
        return items;
    }

    // --- Locks ---

    private static void generateLocks(List<LockItem> items, float multiplier) {
        for (MaterialTier tier : MaterialTier.values()) {
            addIfExists(items, tier.name + "_lock", multiplier,
                    new SkillReq("tinkering", tier.tinkeringLevel));
        }
    }

    // --- Lock Picks ---

    private static void generateLockPicks(List<LockItem> items, float multiplier) {
        for (MaterialTier tier : MaterialTier.values()) {
            if (tier.dexterityLevel > 0) {
                addIfExists(items, tier.name + "_lock_pick", multiplier,
                        new SkillReq("tinkering", tier.tinkeringLevel),
                        new SkillReq("dexterity", tier.dexterityLevel));
            } else {
                addIfExists(items, tier.name + "_lock_pick", multiplier,
                        new SkillReq("tinkering", tier.tinkeringLevel));
            }
        }
    }

    // --- Lock Mechanisms ---

    private static void generateMechanisms(List<LockItem> items, float multiplier) {
        addIfExists(items, "wood_lock_mechanism", multiplier,
                new SkillReq("tinkering", 2));
        addIfExists(items, "copper_lock_mechanism", multiplier,
                new SkillReq("tinkering", 6));
        addIfExists(items, "iron_lock_mechanism", multiplier,
                new SkillReq("tinkering", 8));
        addIfExists(items, "steel_lock_mechanism", multiplier,
                new SkillReq("tinkering", 12));
    }

    // --- Keys & Components ---

    private static void generateKeyItems(List<LockItem> items, float multiplier) {
        addIfExists(items, "spring", multiplier,
                new SkillReq("tinkering", 2));
        addIfExists(items, "key_blank", multiplier,
                new SkillReq("tinkering", 4));
        addIfExists(items, "key", multiplier,
                new SkillReq("tinkering", 6));
        addIfExists(items, "key_ring", multiplier,
                new SkillReq("tinkering", 10));
        addIfExists(items, "master_key", multiplier,
                new SkillReq("tinkering", 22),
                new SkillReq("intelligence", 14));
    }

    // --- Helpers ---

    private static int applyMultiplier(int baseLevel, float multiplier) {
        if (baseLevel <= 0) return 0;
        return Math.max(2, (int) Math.round(baseLevel * multiplier));
    }

    private static boolean itemExists(String itemId) {
        return ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId));
    }

    private record SkillReq(String skill, int level) {}

    private static void addIfExists(List<LockItem> items, String itemName, float multiplier, SkillReq... reqs) {
        String itemId = MOD_ID + ":" + itemName;
        if (!itemExists(itemId)) return;

        List<LockItem.Skill> skills = new ArrayList<>();
        for (SkillReq req : reqs) {
            int level = applyMultiplier(req.level, multiplier);
            if (level >= 2) {
                skills.add(new LockItem.Skill(req.skill, level));
            }
        }

        if (!skills.isEmpty()) {
            items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
        }
    }
}
