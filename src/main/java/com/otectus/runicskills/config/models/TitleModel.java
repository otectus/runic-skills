package com.otectus.runicskills.config.models;

import com.google.gson.annotations.SerializedName;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.conditions.ConditionImpl;
import com.otectus.runicskills.handler.HandlerConditions;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TitleModel {
    // @SerializedName alternates let plain Gson read both the current Pascal-case format AND
    // legacy files written by the old YACL serializer, which named nested-POJO fields in
    // snake_case (title_id, hide_requirements, ...). Without these, every entry in a legacy
    // titles.json5 silently fell back to the no-arg constructor default ("rookie"), collapsing
    // the whole list to a single title. New writes use the primary (Pascal) name.
    @SerializedName(value = "TitleId", alternate = {"title_id"})
    public String TitleId;

    @SerializedName(value = "Conditions", alternate = {"conditions"})
    public List<String> Conditions = new ArrayList<>();

    @SerializedName(value = "Default", alternate = {"default"})
    public boolean Default;

    @SerializedName(value = "HideRequirements", alternate = {"hide_requirements"})
    public Boolean HideRequirements = false;

    private transient Title _title;

    public Title getTitle() {
        return _title;
    }

    /**
     * Re-attaches this model to an already-registered {@link Title}. Needed after
     * {@code /skillsreload}: the reload replaces titleList with fresh Gson-built instances whose
     * transient {@code _title} is null, while the Forge title registry (frozen at startup) still
     * holds the original Title objects.
     */
    public void bind(Title title) {
        _title = title;
    }

    public TitleModel() {
        TitleId = "rookie";
        Conditions = new ArrayList<>();
        Default = true;
        HideRequirements = false;
    }

    public TitleModel(String titleID, List<String> conditions, boolean isDefault) {
        TitleId = titleID;
        Conditions = conditions;
        Default = isDefault;
        HideRequirements = false;
    }

    public TitleModel(String titleID, List<String> conditions, boolean isDefault, boolean hideRequirements) {
        TitleId = titleID;
        Conditions = conditions;
        Default = isDefault;
        HideRequirements = hideRequirements;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", TitleId, String.join("=", Conditions), Default);
    }

    /**
     * The string-independent parts of one condition entry: {@code type/variable/comparator/expected}.
     * Pure (no Minecraft/registry types) so parsing is headless-testable; returns {@code null} for
     * a malformed entry. The condition-type lookup happens separately in {@link #parsedConditions()}.
     */
    public record ParsedParts(String type, String variable, EComparator comparator, String expected) {

        @org.jetbrains.annotations.Nullable
        public static ParsedParts parse(String condition) {
            String[] split = condition.split("/");
            if (split.length != 4) return null;
            try {
                return new ParsedParts(split[0], split[1], EComparator.valueOf(split[2].toUpperCase()), split[3]);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** A fully resolved condition; {@code null} entries mark malformed/unknown conditions (logged once). */
    private record Parsed(ConditionImpl<?> impl, ParsedParts parts) {}

    private transient List<Parsed> _parsedConditions;

    /**
     * Parses and resolves {@link #Conditions} once per config load instead of re-splitting every
     * string and re-resolving every comparator/condition type on each periodic title scan (every
     * 200 ticks per player, across ~90 titles). Config reloads replace TitleModel instances, so
     * the cache invalidates naturally. Malformed entries are logged once here, kept as {@code null},
     * and permanently fail the title — same outcome as the previous per-scan skip.
     */
    private List<Parsed> parsedConditions() {
        if (_parsedConditions == null) {
            List<Parsed> parsed = new ArrayList<>(Conditions.size());
            for (String condition : Conditions) {
                ParsedParts parts = ParsedParts.parse(condition);
                if (parts == null) {
                    RunicSkills.getLOGGER().error(">> Error! Title {} has a wrong formatted condition '{}'. (General/Comparator)", TitleId, condition);
                    parsed.add(null);
                    continue;
                }
                Optional<ConditionImpl<?>> conditionImpl = HandlerConditions.getConditionByName(parts.type());
                if (conditionImpl.isEmpty()) {
                    RunicSkills.getLOGGER().error(">> Error! Title {} has a wrong formatted condition '{}'. (Condition type)", TitleId, condition);
                    parsed.add(null);
                    continue;
                }
                parsed.add(new Parsed(conditionImpl.get(), parts));
            }
            _parsedConditions = parsed;
        }
        return _parsedConditions;
    }

    public boolean CheckRequirements(ServerPlayer serverPlayer) {

        // If the title should be given by default then lets ignore conditions.
        if (Default) return true;

        int passedConditions = 0;
        for (Parsed condition : parsedConditions()) {
            if (condition == null) continue; // malformed — logged once at parse, can never pass

            try {
                condition.impl().ProcessVariable(condition.parts().variable(), serverPlayer);
            } catch (Exception e) {
                RunicSkills.getLOGGER().error(">> Error! Title {} failed to process condition variable '{}': {}", TitleId, condition.parts().variable(), e.getMessage());
                continue;
            }
            if (condition.impl().MeetCondition(condition.parts().expected(), condition.parts().comparator())) {
                passedConditions++;
            }
        }

        return passedConditions == Conditions.size();
    }

    public RegistryObject<Title> registry(DeferredRegister<Title> TITLES) {
        _title = register(TitleId, Default);
        return TITLES.register(TitleId, () -> _title);
    }

    private Title register(String name, boolean requirement) {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, name);
        return new Title(key, requirement, this.HideRequirements);
    }

    private boolean Compare(int a, int b, EComparator comparator) {
        return switch (comparator) {
            case EQUALS -> a == b;
            case GREATER -> a > b;
            case LESS -> a < b;
            case GREATER_OR_EQUAL -> a >= b;
            case LESS_OR_EQUAL -> a <= b;
            default -> false;
        };
    }

    public enum EConditionType {

        Skill,
        Stat,
        EntityKilled,
        Special
    }

    public enum EComparator {
        EQUALS,
        GREATER,
        LESS,
        GREATER_OR_EQUAL,
        LESS_OR_EQUAL
    }

}