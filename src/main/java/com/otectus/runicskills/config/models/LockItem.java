package com.otectus.runicskills.config.models;

import com.otectus.runicskills.RunicSkills;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LockItem {

    public String Item = "minecraft:diamond";

    public List<Skill> Skills = List.of(new Skill());

    public LockItem() {
    }

    public LockItem(String itemName) {
        Item = itemName;
    }

    public LockItem(String itemName, Skill... skills) {
        Item = itemName;
        Skills = Arrays.stream(skills).toList();
    }

    @Override
    public String toString() {
        if (Skills.stream().anyMatch(Objects::isNull)) {
            RunicSkills.getLOGGER().info(">> Found null skill at item {}", this.Item);
        }
        List<String> strings;
        try {
            strings = Skills.stream().map(Skill::toString).toList();
        } catch (NullPointerException e) {
            RunicSkills.getLOGGER().info(">> Found null skill at item {}", this.Item);
            strings = new ArrayList<>();
        }
        return Item + " [" + String.join(", ", strings) + "]";
    }

    public static class Skill {

        public ESkill Skill;

        public int Level;

        public Skill(String skillName, int level) {
            try {
                Skill = ESkill.valueOf(StringUtils.capitalize(skillName));
            } catch (IllegalArgumentException e) {
                RunicSkills.getLOGGER().info(">> Wrong skill name {}", skillName);
                Skill = ESkill.Strength;
            }
            Level = level;
        }

        public Skill() {
            Skill = ESkill.Strength;
            Level = 2;
        }

        @Override
        public String toString() {
            return String.format("%s:%d", Skill.toString(), Level);
        }
    }
}
