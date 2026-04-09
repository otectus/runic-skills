package com.otectus.runicskills.registry.skill;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class Skill {
    public final int index;
    public final ResourceLocation key;
    public final ResourceLocation[] lockedTexture;
    public final ResourceLocation background;
    public List<Perk> list = new ArrayList<>();

    public Skill(int index, ResourceLocation key, ResourceLocation[] lockedTexture, ResourceLocation background) {
        this.index = index;
        this.key = key;
        this.lockedTexture = lockedTexture;
        this.background = background;
    }

    public Skill get() {
        return this;
    }

    public String getName() {
        return this.key.getPath();
    }

    public String getKey() {
        return "skill." + this.key.toLanguageKey();
    }

    public String getDescription() {
        return getKey() + ".description";
    }

    public void setList(List<Perk> list) {
        this.list = list;
    }

    public List<Perk> getPerks(Skill skill) {
        List<Perk> list = new ArrayList<>();
        for (int i = 0; i < RegistryPerks.PERKS_REGISTRY.get().getValues().stream().toList().size(); i++) {
            Perk perk = RegistryPerks.PERKS_REGISTRY.get().getValues().stream().toList().get(i);
            if (perk.getSkill() == skill) list.add(perk);
        }
        return list;
    }

    public List<Passive> getPassives(Skill skill) {
        List<Passive> list = new ArrayList<>();
        for (int i = 0; i < RegistryPassives.PASSIVES_REGISTRY.get().getValues().stream().toList().size(); i++) {
            Passive passive = RegistryPassives.PASSIVES_REGISTRY.get().getValues().stream().toList().get(i);
            if (passive.getSkill() == skill) list.add(passive);
        }
        return list;
    }


    public int getLevel() {
        return SkillCapability.getLocal().getSkillLevel(this);
    }

    public int getLevel(Player player) {
        return SkillCapability.get(player).getSkillLevel(this);
    }

    public MutableComponent getRank(int skillLevel) {
        MutableComponent rank = Component.translatable("skill.runicskills.rank.0");
        for (int i = 0; i < 9; i++) {
            if (skillLevel >= (HandlerCommonConfig.HANDLER.instance().skillMaxLevel / 8) * i) {
                rank = Component.translatable("skill.runicskills.rank." + i);
            }
        }
        return rank;
    }

    public ResourceLocation getLockedTexture(int fromLevel) {
        int size = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        int textureListSize = this.lockedTexture.length;

        int index = Math.floorDiv((fromLevel * textureListSize), size);
        index = index == textureListSize ? index - 1 : index;

        if (getLevel() > size){
            SkillCapability.getLocal().setSkillLevel(this, size);
        }

        // If you upgrade a perk to max level and then change the skillMaxLevel option to a lower one
        // This will throw an ArrayIndexOutOfBoundsException.
        if (index >= 4) {
            index = 3;
        }

        return this.lockedTexture[index];
    }

    public ResourceLocation getLockedTexture() {
        int size = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        int textureListSize = this.lockedTexture.length;

        if (getLevel() > size){
            SkillCapability.getLocal().setSkillLevel(this, size);
        }

        int index = Math.floorDiv((getLevel() * textureListSize), size);
        index = index == textureListSize ? index - 1 : index;

        // If you upgrade a perk to max level and then change the skillMaxLevel option to a lower one
        // This will throw an ArrayIndexOutOfBoundsException.
        if (index >= 4) {
            index = 3;
        }

        return this.lockedTexture[index];
    }
}


