package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.equipment.EquipmentProfile;
import com.otectus.runicskills.common.equipment.EquipmentRole;
import com.otectus.runicskills.common.equipment.RequirementDecision;
import com.otectus.runicskills.common.rules.PackRule;
import com.otectus.runicskills.common.rules.PackRuleIndex;
import com.otectus.runicskills.integration.lock.LockAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.item.IModifiable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The loaded pack rules, asked about one native tool.
 *
 * <p>The loader that reads those rules deals only in ids, numbers and role names, so it works on a
 * server that has never had Tinker's Construct. Two of the §13.3 selectors cannot be answered in
 * that vocabulary, though — a material <em>tier</em> and a tool's <em>roles</em> are facts about
 * native content — and this is where they are turned into numbers the index can compare. That
 * split is why the loader is in {@code common/rules} and this class is not.
 *
 * <p><b>The verdict this returns is always an allow.</b> {@link TConstructRuleSource#rule} is not
 * given the player, so a rule source cannot know whether the requirements it found are met. It
 * reports what the pack requires and {@link TConstructRequirementResolver} — which has the player —
 * decides. Anything else would have this class inventing a player or the resolver ignoring the
 * pack's numbers.
 */
public final class TConstructPackRuleSource implements TConstructRuleSource {

    /**
     * Definition id to the roles its items hold, rebuilt when the ruleset changes.
     *
     * <p>Roles come from item tags, so the table is only correct for the current datapack — and the
     * rules revision moves on exactly the reload that could have changed those tags, which makes it
     * a usable staleness signal without a second listener. It is built only when some loaded rule
     * actually narrows by role, because the build is a pass over the item registry.
     */
    private static volatile Map<ResourceLocation, Set<EquipmentRole>> rolesByDefinition = Map.of();

    private static volatile int rolesRevision = -1;

    @Override
    public Optional<RequirementDecision> rule(ResourceLocation definitionId,
                                              List<ResourceLocation> materialIds,
                                              LockAction action) {
        PackRuleIndex index = PackRuleIndex.get();
        if (index.size() == 0) return Optional.empty();

        Set<ResourceLocation> materials = new LinkedHashSet<>(materialIds);
        Optional<PackRule> rule = index.useRequirement(definitionId, materials,
                roles(index, definitionId), tier(materialIds), action);
        return rule.map(matched -> new RequirementDecision(true, matched.requirements(),
                List.of("pack:" + matched.id()), List.of(), null));
    }

    /**
     * The highest tier across these materials, or {@code -1} when none could be determined.
     *
     * <p>Same rule as the automatic profile's: an unknown material is not a tier, and {@code -1}
     * makes a {@code native_material_tiers} selector decline rather than match tier zero — §7.3
     * forbids undetermined material data producing a lock.
     */
    private static int tier(List<ResourceLocation> materialIds) {
        if (!MaterialRegistry.isFullyLoaded()) return -1;
        int tier = -1;
        for (ResourceLocation id : materialIds) {
            if (id == null) continue;
            IMaterial material = MaterialRegistry.getMaterial(new MaterialId(id));
            if (material == null || material == IMaterial.UNKNOWN) continue;
            tier = Math.max(tier, material.getTier());
        }
        return tier;
    }

    /** The roles every item of this definition holds, from the table below. */
    private static Set<EquipmentRole> roles(PackRuleIndex index, ResourceLocation definitionId) {
        if (!index.usesRoleSelectors()) return Set.of();
        if (rolesRevision != index.revision()) {
            rolesByDefinition = buildRoles();
            rolesRevision = index.revision();
        }
        return rolesByDefinition.getOrDefault(definitionId, Set.of());
    }

    /**
     * One pass over the item registry, mapping each native tool definition to its roles.
     *
     * <p>The roles are read from the same adapter every other part of this mod asks, on a default
     * stack of each item, because a role is a property of the item's tags rather than of the tool
     * in the player's hand. A definition with several items — the same tool in several forms —
     * contributes the union, so a rule about a role is true of the definition when it is true of
     * any of its items.
     */
    private static Map<ResourceLocation, Set<EquipmentRole>> buildRoles() {
        Map<ResourceLocation, Set<EquipmentRole>> table = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof IModifiable modifiable)) continue;
            ItemStack stack = new ItemStack(item);
            if (!TConstructEquipmentAdapter.isNativeTool(stack)) continue;
            Optional<EquipmentProfile> profile = TConstructEquipmentAdapter.INSTANCE.profile(stack);
            if (profile.isEmpty()) continue;
            ResourceLocation definitionId = modifiable.getToolDefinition().getId();
            table.computeIfAbsent(definitionId, id -> EnumSet.noneOf(EquipmentRole.class))
                    .addAll(profile.get().roles());
        }
        return Map.copyOf(table);
    }
}
