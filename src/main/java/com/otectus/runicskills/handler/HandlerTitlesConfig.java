package com.otectus.runicskills.handler;

import com.google.gson.GsonBuilder;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.Configuration;
import com.otectus.runicskills.config.models.TitleModel;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class HandlerTitlesConfig {

    public static ConfigClassHandler<HandlerTitlesConfig> HANDLER = ConfigClassHandler.createBuilder(HandlerTitlesConfig.class)
            .id(new ResourceLocation(RunicSkills.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(Configuration.getAbsoluteDirectory().resolve("runicskills.titles.json5"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting)
                    .setJson5(true)
                    .build())
            .build();

    @SerialEntry(comment = "Titles list")
    // Every title defined here are the default titles from the original mod
    public List<TitleModel> titleList = List.of(new TitleModel(),
            new TitleModel("fighter", List.of("skill/Strength/greater_or_equal/16"), false),
            new TitleModel("fighter_great", List.of("skill/Strength/greater_or_equal/32"), false),
            new TitleModel("warrior", List.of("skill/Constitution/greater_or_equal/16"), false),
            new TitleModel("warrior_great", List.of("skill/Constitution/greater_or_equal/32"), false),
            new TitleModel("ranger", List.of("skill/Dexterity/greater_or_equal/16"), false),
            new TitleModel("ranger_great", List.of("skill/Dexterity/greater_or_equal/32"), false),
            new TitleModel("tank", List.of("skill/Endurance/greater_or_equal/16"), false),
            new TitleModel("tank_great", List.of("skill/Endurance/greater_or_equal/32"), false),
            new TitleModel("alchemist", List.of("skill/Intelligence/greater_or_equal/16"), false),
            new TitleModel("alchemist_great", List.of("skill/Intelligence/greater_or_equal/32"), false),
            new TitleModel("miner", List.of("skill/Building/greater_or_equal/16"), false),
            new TitleModel("miner_great", List.of("skill/Building/greater_or_equal/32"), false),
            new TitleModel("magician", List.of("skill/Magic/greater_or_equal/16"), false),
            new TitleModel("magician_great", List.of("skill/Magic/greater_or_equal/32"), false),
            new TitleModel("lucky_one", List.of("skill/Fortune/greater_or_equal/16"), false),
            new TitleModel("lucky_one_great", List.of("skill/Fortune/greater_or_equal/32"), false),
            new TitleModel("dragon_slayer", List.of("EntityKilled/ender_dragon/greater_or_equal/10"), false),
            new TitleModel("player_killer", List.of("EntityKilled/player/greater_or_equal/100"), false),
            new TitleModel("mob_killer", List.of("stat/mob_kills/greater_or_equal/100"), false),
            new TitleModel("mob_killer_great", List.of("stat/mob_kills/greater_or_equal/1000"), false),
            new TitleModel("mob_killer_master", List.of("stat/mob_kills/greater_or_equal/10000"), false),
            new TitleModel("hero", List.of("stat/raid_win/greater_or_equal/100"), false),
            new TitleModel("villain", List.of("EntityKilled/villager/greater_or_equal/100"), false),
            new TitleModel("fisherman", List.of("stat/fish_caught/greater_or_equal/100"), false),
            new TitleModel("fisherman_great", List.of("stat/fish_caught/greater_or_equal/1000"), false),
            new TitleModel("fisherman_master", List.of("stat/fish_caught/greater_or_equal/10000"), false),
            new TitleModel("enchanter", List.of("stat/enchant_item/greater_or_equal/10"), false),
            new TitleModel("enchanter_great", List.of("stat/enchant_item/greater_or_equal/100"), false),
            new TitleModel("enchanter_master", List.of("stat/enchant_item/greater_or_equal/1000"), false),
            new TitleModel("survivor", List.of("stat/time_since_death/greater_or_equal/2400000"), false),
            new TitleModel("businessman", List.of("stat/traded_with_villager/greater_or_equal/100"), false),
            new TitleModel("driver_boat", List.of("stat/boat_one_cm/greater_or_equal/1000000"), false),
            new TitleModel("driver_cart", List.of("stat/minecart_one_cm/greater_or_equal/1000000"), false),
            new TitleModel("rider_horse", List.of("stat/horse_one_cm/greater_or_equal/1000000"), false),
            new TitleModel("rider_pig", List.of("stat/pig_one_cm/greater_or_equal/1000000"), false),
            new TitleModel("rider_strider", List.of("stat/strider_one_cm/greater_or_equal/1000000"), false),
            new TitleModel("traveler_nether", List.of("special/dimension/equals/minecraft:the_nether"), false, true),
            new TitleModel("traveler_end", List.of("special/dimension/equals/minecraft:the_end"), false, true),

            // Hybrid build titles
            new TitleModel("battlemage", List.of("skill/Strength/greater_or_equal/20", "skill/Magic/greater_or_equal/20"), false),
            new TitleModel("spellblade", List.of("skill/Dexterity/greater_or_equal/20", "skill/Magic/greater_or_equal/20"), false),
            new TitleModel("paladin", List.of("skill/Constitution/greater_or_equal/20", "skill/Wisdom/greater_or_equal/20"), false),
            new TitleModel("runesmith", List.of("skill/Building/greater_or_equal/32", "skill/Wisdom/greater_or_equal/32"), false),
            new TitleModel("warlord", List.of("skill/Strength/greater_or_equal/32", "skill/Endurance/greater_or_equal/32"), false),
            new TitleModel("juggernaut", List.of("skill/Endurance/greater_or_equal/32", "skill/Constitution/greater_or_equal/32"), false),
            new TitleModel("shadow", List.of("skill/Dexterity/greater_or_equal/32", "skill/Fortune/greater_or_equal/32"), false),

            // Mastery titles
            new TitleModel("archmage", List.of("skill/Magic/greater_or_equal/32"), false),
            new TitleModel("sage", List.of("skill/Wisdom/greater_or_equal/32"), false),

            // Global level titles
            new TitleModel("grandmaster", List.of("GlobalLevel/global/greater_or_equal/224"), false),
            new TitleModel("transcendent", List.of("GlobalLevel/global/greater_or_equal/256"), false),

            // Boss kill titles - Ice and Fire
            new TitleModel("fire_dragon_slayer", List.of("EntityKilled/iceandfire:fire_dragon/greater_or_equal/1"), false),
            new TitleModel("ice_dragon_slayer", List.of("EntityKilled/iceandfire:ice_dragon/greater_or_equal/1"), false),
            new TitleModel("lightning_dragon_slayer", List.of("EntityKilled/iceandfire:lightning_dragon/greater_or_equal/1"), false),

            // Boss kill titles - Cataclysm
            new TitleModel("abyssal_champion", List.of("EntityKilled/cataclysm:the_leviathan/greater_or_equal/1"), false),
            new TitleModel("ancient_slayer", List.of("EntityKilled/cataclysm:netherite_monstrosity/greater_or_equal/1"), false),

            // Thematic hybrid titles
            new TitleModel("blood_mage", List.of("skill/Magic/greater_or_equal/24", "skill/Constitution/greater_or_equal/20"), false),
            new TitleModel("polymath", List.of("skill/Strength/greater_or_equal/16", "skill/Constitution/greater_or_equal/16", "skill/Dexterity/greater_or_equal/16", "skill/Endurance/greater_or_equal/16", "skill/Intelligence/greater_or_equal/16", "skill/Building/greater_or_equal/16", "skill/Wisdom/greater_or_equal/16", "skill/Magic/greater_or_equal/16", "skill/Fortune/greater_or_equal/16", "skill/Tinkering/greater_or_equal/16"), false)
    );
}
