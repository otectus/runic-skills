package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RegistrySkills {

    private static final ResourceKey<Registry<Skill>> SKILLS_KEY = ResourceKey.createRegistryKey(new ResourceLocation(RunicSkills.MOD_ID, "skills"));
    private static final DeferredRegister<Skill> SKILLS = DeferredRegister.create(SKILLS_KEY, RunicSkills.MOD_ID);
    public static Supplier<IForgeRegistry<Skill>> SKILLS_REGISTRY = SKILLS.makeRegistry(() -> new RegistryBuilder<Skill>().disableSaving());

    // 2x5 grid layout:
    // [0] Strength     [1] Constitution
    // [2] Dexterity    [3] Endurance
    // [4] Intelligence [5] Building
    // [6] Wisdom       [7] Magic
    // [8] Fortune      [9] Tinkering
    public static final RegistryObject<Skill> STRENGTH = SKILLS.register("strength", () -> register(0, "strength", HandlerResources.STRENGTH_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/yellow_terracotta.png")));
    public static final RegistryObject<Skill> CONSTITUTION = SKILLS.register("constitution", () -> register(1, "constitution", HandlerResources.CONSTITUTION_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/red_terracotta.png")));
    public static final RegistryObject<Skill> DEXTERITY = SKILLS.register("dexterity", () -> register(2, "dexterity", HandlerResources.DEXTERITY_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/blue_terracotta.png")));
    public static final RegistryObject<Skill> ENDURANCE = SKILLS.register("endurance", () -> register(3, "endurance", HandlerResources.ENDURANCE_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/cyan_terracotta.png")));
    public static final RegistryObject<Skill> INTELLIGENCE = SKILLS.register("intelligence", () -> register(4, "intelligence", HandlerResources.INTELLIGENCE_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/orange_terracotta.png")));
    public static final RegistryObject<Skill> BUILDING = SKILLS.register("building", () -> register(5, "building", HandlerResources.BUILDING_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/brown_terracotta.png")));
    public static final RegistryObject<Skill> WISDOM = SKILLS.register("wisdom", () -> register(6, "wisdom", HandlerResources.WISDOM_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/light_blue_terracotta.png")));
    public static final RegistryObject<Skill> MAGIC = SKILLS.register("magic", () -> register(7, "magic", HandlerResources.MAGIC_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/purple_terracotta.png")));
    public static final RegistryObject<Skill> FORTUNE = SKILLS.register("fortune", () -> register(8, "fortune", HandlerResources.FORTUNE_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/lime_terracotta.png")));
    public static final RegistryObject<Skill> TINKERING = SKILLS.register("tinkering", () -> register(9, "tinkering", HandlerResources.TINKERING_LOCKED_ICON, new ResourceLocation("minecraft:textures/block/gray_terracotta.png")));

    public static void load(IEventBus eventBus) {
        SKILLS.register(eventBus);
    }

    private static Skill register(int index, String name, ResourceLocation[] lockedTexture, ResourceLocation background) {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, name);
        return new Skill(index, key, lockedTexture, background);
    }

    private static volatile List<Skill> cachedValues;
    private static volatile Map<String, Skill> cachedByName;

    public static List<Skill> getCachedValues() {
        if (cachedValues == null) {
            cachedValues = List.copyOf(SKILLS_REGISTRY.get().getValues());
        }
        return cachedValues;
    }

    public static Skill getSkill(String skillName) {
        if (cachedByName == null) {
            cachedByName = getCachedValues().stream()
                    .collect(Collectors.toUnmodifiableMap(Skill::getName, Skill::get));
        }
        return cachedByName.get(skillName.toLowerCase());
    }
}


