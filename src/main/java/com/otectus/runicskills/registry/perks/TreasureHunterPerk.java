package com.otectus.runicskills.registry.perks;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.ConfigParser;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TreasureHunterPerk {
    private static ArrayList<List<BlockDrops>> cachedItems = null;

    public static void invalidateCache() {
        cachedItems = null;
    }

    public static ItemStack drop() {
        int randomizer = (int) Math.floor(Math.random() * RegistryPerks.TREASURE_HUNTER.get().getValue()[0]);
        ItemStack stack = null;
        for (int i = 0; i < getItems().size(); i++) {
            List<BlockDrops> drops = getItems().get(i);
            if (randomizer == i) {
                int dropsRandom = (int) Math.floor(Math.random() * drops.size());
                for (int j = 0; j < drops.size(); j++) {
                    if (dropsRandom == j) {
                        Item itemStack = drops.get(j).getStack[dropsRandom];
                        stack = itemStack.getDefaultInstance();
                        stack.setTag(drops.get(j).getCompoundTag);
                    }
                }
            }
        }
        return stack;
    }

    public static ArrayList<List<BlockDrops>> getItems() {
        if (cachedItems != null) return cachedItems;

        ArrayList<List<BlockDrops>> dropList = new ArrayList<>();
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

                    Item[] arrayOfItem = new Item[itemsSize];
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
                        arrayOfItem[j] = parsedItem.get();

                        getItems.add(new BlockDrops(arrayOfItem, compoundTag));
                    }
                    dropList.add(getItems);
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
                Item[] getStack = new Item[1];
                getStack[0] = parsedItem.get();

                getItems.add(new BlockDrops(getStack, compound));
                dropList.add(getItems);
            } catch (Exception e) {
                RunicSkills.getLOGGER().warn(">> Skipping invalid treasure hunter entry '{}': {}", getValue, e.getMessage());
            }
        }


        cachedItems = dropList;
        return dropList;
    }

    public static List<String> defaultItemList = Arrays.asList("minecraft:flint", "minecraft:clay_ball", "trashList[minecraft:feather;minecraft:bone_meal]", "lostToolList[minecraft:stick;minecraft:wooden_pickaxe{Damage:59};minecraft:wooden_shovel{Damage:59};minecraft:wooden_axe{Damage:59}]", "discList[minecraft:music_disc_13;minecraft:music_disc_cat;minecraft:music_disc_blocks;minecraft:music_disc_chirp;minecraft:music_disc_far;minecraft:music_disc_mall;minecraft:music_disc_mellohi;minecraft:music_disc_stal;minecraft:music_disc_strad;minecraft:music_disc_ward;minecraft:music_disc_11;minecraft:music_disc_wait]", "seedList[minecraft:beetroot_seeds;minecraft:wheat_seeds;minecraft:pumpkin_seeds;minecraft:melon_seeds;minecraft:brown_mushroom;minecraft:red_mushroom]", "mineralList[minecraft:raw_iron;minecraft:raw_gold;minecraft:raw_copper;minecraft:coal;minecraft:charcoal]");

    public static final class BlockDrops {
        private final Item[] getStack;
        private final CompoundTag getCompoundTag;

        public BlockDrops(Item[] getStack, CompoundTag getCompoundTag) {
            this.getStack = getStack;
            this.getCompoundTag = getCompoundTag;
        }
    }
}


