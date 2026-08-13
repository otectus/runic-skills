package com.otectus.runicskills.registry.perks;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.ConfigParser;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TreasureHunterPerk {
    private static List<List<BlockDrops>> cachedItems = null;

    public static void invalidateCache() {
        cachedItems = null;
    }

    /**
     * Rolls a treasure drop, or returns {@code null} for no drop.
     *
     * <p>The configured probability is the width of the roll: the roll picks one slot out of it,
     * and only the first {@code getItems().size()} slots correspond to an actual entry, so a
     * larger value makes treasure rarer. A value of {@code 0} or less is treated as "never" —
     * previously it produced {@code floor(random * 0) == 0}, which selected the first entry on
     * *every* qualifying block break, the exact opposite of what a pack author writing 0 intends
     * (RS-129).
     */
    public static ItemStack drop(Player player) {
        List<List<BlockDrops>> groups = getItems();
        if (groups.isEmpty()) return null;

        int spread = (int) RegistryPerks.TREASURE_HUNTER.get().getActiveValue(player)[0];
        if (spread <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(spread);
        if (roll >= groups.size()) return null;

        List<BlockDrops> group = groups.get(roll);
        if (group.isEmpty()) return null;
        return group.get(ThreadLocalRandom.current().nextInt(group.size())).toStack();
    }

    public static List<List<BlockDrops>> getItems() {
        if (cachedItems != null) return cachedItems;

        List<List<BlockDrops>> dropList = new ArrayList<>();
        List<? extends String> configList = HandlerCommonConfig.HANDLER.instance().treasureHunterItemList;

        for (String getValue : configList) {
            try {
                List<BlockDrops> getItems = new ArrayList<>();
                if (getValue.contains("List[") && getValue.charAt(getValue.length() - 1) == ']') {
                    String newValue = getValue.split("List\\[")[1].substring(0, getValue.split("List\\[")[1].length() - 1);
                    int itemsSize = 1;
                    for (int i = 0; i < newValue.length(); ) {
                        if (newValue.charAt(i) == ';') itemsSize++;
                        i++;
                    }

                    // One BlockDrops per successfully parsed item. The previous version wrote into
                    // a fixed-size Item[] sized from the *declared* entry count and skipped the
                    // write when an id failed to parse, leaving a null hole; the read path then
                    // indexed that array with a position derived from the (shorter) list size and
                    // dereferenced the hole, throwing inside a BlockEvent.BreakEvent handler on
                    // every block break for every affected player (RS-130). Sizing from what
                    // actually parsed makes the hole unrepresentable.
                    for (int j = 0; j < itemsSize; j++) {
                        CompoundTag compoundTag = new CompoundTag();
                        String resource = newValue.split(";")[j];
                        String str1 = String.valueOf(resource.charAt(resource.length() - 1));
                        boolean bool = (resource.contains("{") && str1.equals("}"));
                        String str2 = bool ? resource.split("\\{")[0] : resource;

                        if (bool) {
                            String nbt = "{" + resource.split("\\{", 2)[1];
                            try {
                                compoundTag = TagParser.parseTag(nbt);
                            } catch (CommandSyntaxException e) {
                                RunicSkills.getLOGGER().warn(">> Skipping treasure hunter entry with invalid NBT '{}': {}", getValue, e.getMessage());
                                continue;
                            }
                        }

                        var parsedItem = ConfigParser.parseItem(str2, "TreasureHunter");
                        if (parsedItem.isEmpty()) continue;

                        getItems.add(new BlockDrops(parsedItem.get(), compoundTag));
                    }
                    if (!getItems.isEmpty()) dropList.add(getItems);
                    continue;
                }
                CompoundTag compound = new CompoundTag();
                String lastChar = String.valueOf(getValue.charAt(getValue.length() - 1));
                boolean containsNBT = (getValue.contains("{") && lastChar.equals("}"));
                String newResource = containsNBT ? getValue.split("\\{")[0] : getValue;

                if (containsNBT) {
                    String nbt = "{" + getValue.split("\\{", 2)[1];
                    try {
                        compound = TagParser.parseTag(nbt);
                    } catch (CommandSyntaxException e) {
                        RunicSkills.getLOGGER().warn(">> Skipping treasure hunter entry with invalid NBT '{}': {}", getValue, e.getMessage());
                        continue;
                    }
                }

                var parsedItem = ConfigParser.parseItem(newResource, "TreasureHunter");
                if (parsedItem.isEmpty()) continue;

                getItems.add(new BlockDrops(parsedItem.get(), compound));
                dropList.add(getItems);
            } catch (Exception e) {
                RunicSkills.getLOGGER().warn(">> Skipping invalid treasure hunter entry '{}': {}", getValue, e.getMessage());
            }
        }


        cachedItems = dropList;
        return dropList;
    }

    public static List<String> defaultItemList = Arrays.asList("minecraft:flint", "minecraft:clay_ball", "trashList[minecraft:feather;minecraft:bone_meal]", "lostToolList[minecraft:stick;minecraft:wooden_pickaxe{Damage:59};minecraft:wooden_shovel{Damage:59};minecraft:wooden_axe{Damage:59}]", "discList[minecraft:music_disc_13;minecraft:music_disc_cat;minecraft:music_disc_blocks;minecraft:music_disc_chirp;minecraft:music_disc_far;minecraft:music_disc_mall;minecraft:music_disc_mellohi;minecraft:music_disc_stal;minecraft:music_disc_strad;minecraft:music_disc_ward;minecraft:music_disc_11;minecraft:music_disc_wait]", "seedList[minecraft:beetroot_seeds;minecraft:wheat_seeds;minecraft:pumpkin_seeds;minecraft:melon_seeds;minecraft:brown_mushroom;minecraft:red_mushroom]", "mineralList[minecraft:raw_iron;minecraft:raw_gold;minecraft:raw_copper;minecraft:coal;minecraft:charcoal]");

    /** One resolvable treasure entry: an item plus the optional NBT its config entry carried. */
    public static final class BlockDrops {
        private final Item item;
        private final CompoundTag tag;

        public BlockDrops(Item item, CompoundTag tag) {
            this.item = item;
            this.tag = tag;
        }

        ItemStack toStack() {
            ItemStack stack = item.getDefaultInstance();
            if (tag != null && !tag.isEmpty()) stack.setTag(tag.copy());
            return stack;
        }
    }
}


