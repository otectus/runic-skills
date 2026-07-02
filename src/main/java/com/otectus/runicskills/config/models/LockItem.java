package com.otectus.runicskills.config.models;

import com.google.gson.annotations.SerializedName;
import com.otectus.runicskills.RunicSkills;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class LockItem {

    // @SerializedName alternates: legacy lockItems.json5 written by the old YACL serializer
    // used snake_case keys (item, skills, skill, level). Without these, plain Gson silently
    // collapsed every lock entry to the no-arg default (minecraft:diamond / Strength 2),
    // breaking item gating. Same root cause as TitleModel.
    @SerializedName(value = "Item", alternate = {"item"})
    public String Item = "minecraft:diamond";

    // Mutable: /registeritem calls Skills.add()/.remove() on lock entries. An immutable
    // List.of(...) / Stream.toList() default threw UnsupportedOperationException on first launch.
    @SerializedName(value = "Skills", alternate = {"skills"})
    public List<Skill> Skills = new ArrayList<>(List.of(new Skill()));

    // Origin of this lock: null/absent = manual config (the default), or a generating provider id
    // (e.g. "iceandfire", "irons_spellbooks"). Optional in JSON so legacy lockItems.json5 (which has
    // no Source key) round-trips unchanged; Gson omits it on write while it is null, so manual entries
    // are not cluttered with a redundant "Source": "manual".
    @SerializedName(value = "Source", alternate = {"source"})
    public String Source;

    public LockItem() {
    }

    /** The lock origin, defaulting to "manual" when unset. */
    public String sourceOrManual() {
        return (Source == null || Source.isBlank()) ? "manual" : Source;
    }

    /** Fluent setter used by integration lock providers to stamp their origin. */
    public LockItem withSource(String source) {
        this.Source = source;
        return this;
    }

    public LockItem(String itemName) {
        Item = itemName;
    }

    public LockItem(String itemName, Skill... skills) {
        Item = itemName;
        Skills = new ArrayList<>(Arrays.asList(skills));
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

        @SerializedName(value = "Skill", alternate = {"skill"})
        public ESkill Skill;

        @SerializedName(value = "Level", alternate = {"level"})
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
