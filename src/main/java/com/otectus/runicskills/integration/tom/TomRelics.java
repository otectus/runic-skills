package com.otectus.runicskills.integration.tom;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
/** Explicit 6.3.0 advanced identities, verified against the native two-step smithing recipe chains. */
public final class TomRelics {
    private static final Set<String> ADVANCED=Set.of(
        "traveloptics:abyssal_tidecaller_level_three",
        "traveloptics:abyssal_tidecaller_level_two",
        "traveloptics:charged_sands_level_three",
        "traveloptics:charged_sands_level_two",
        "traveloptics:cursed_wraithblade_level_three",
        "traveloptics:cursed_wraithblade_level_two",
        "traveloptics:flames_of_eldritch_level_three",
        "traveloptics:flames_of_eldritch_level_two",
        "traveloptics:galenic_polarizer_level_three",
        "traveloptics:galenic_polarizer_level_two",
        "traveloptics:gauntlet_of_extinction_level_three",
        "traveloptics:gauntlet_of_extinction_level_two",
        "traveloptics:harbingers_wrath_level_three",
        "traveloptics:harbingers_wrath_level_two",
        "traveloptics:infernal_devastator_level_three",
        "traveloptics:infernal_devastator_level_two",
        "traveloptics:mechanized_wraithblade_level_three",
        "traveloptics:mechanized_wraithblade_level_two",
        "traveloptics:scourge_of_the_sands_level_three",
        "traveloptics:scourge_of_the_sands_level_two",
        "traveloptics:stellothorn_level_three",
        "traveloptics:stellothorn_level_two",
        "traveloptics:the_obliterator_level_three",
        "traveloptics:the_obliterator_level_two",
        "traveloptics:thorns_of_oblivion_level_three",
        "traveloptics:thorns_of_oblivion_level_two",
        "traveloptics:trident_of_the_eternal_maelstrom_level_three",
        "traveloptics:trident_of_the_eternal_maelstrom_level_two",
        "traveloptics:voidstrike_reaper_level_three",
        "traveloptics:voidstrike_reaper_level_two");
    private TomRelics() {}
    public static boolean verified() {return ADVANCED.stream().allMatch(id->{var item=ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(id));return item!=null && item!=net.minecraft.world.item.Items.AIR;});}
    public static boolean advanced(ItemStack stack) {
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        return !stack.isEmpty() && id!=null && ADVANCED.contains(id.toString());
    }
}
