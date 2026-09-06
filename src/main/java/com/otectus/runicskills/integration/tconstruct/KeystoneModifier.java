package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.RunicSkills;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.build.VolatileDataModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;

/**
 * The one extra upgrade slot a smith fits at the station, and the only thing this modifier does.
 *
 * <p>Spec §10.4 calls it paid permanent craftsmanship, and every decision here follows from that
 * word rather than from the perk that authorises it.
 *
 * <p><b>Why the slot is granted, not spent.</b> An ordinary Tinkers' modifier costs an upgrade slot
 * to apply; this one is the slot. {@link KeystoneStationRecipe} therefore charges no slot, and this
 * hook adds one, so the net effect on the tool is exactly the {@code +1} §10.2 promises. A modifier
 * that cost one and granted one would be an elaborate way to change nothing.
 *
 * <p><b>Why {@code VOLATILE_DATA} and not persistent data.</b> Volatile data is recomputed from the
 * modifier list on every rebuild, which means the slot is a function of "this tool carries a
 * keystone" rather than a number written once that a later rebuild could drop or double. Part
 * swaps, renames, repairs and tool XP levels all rebuild, and all of them leave it at one (§10.4:
 * "preview refresh, part swap, rename, tool XP level-up, death, and reload cannot apply it again").
 *
 * <p><b>Level one, always.</b> The recipe refuses a tool that already carries the modifier, so a
 * second keystone cannot be bought; this hook ignores {@code entry.getLevel()} for the same reason,
 * so a level written by anything other than that recipe still grants exactly one slot.
 *
 * <p><b>And it survives this mod being switched off.</b> The slot lives in the tool's own data,
 * computed by a modifier the tool itself names. Disabling the service stops new keystones; it does
 * not reach back into an item somebody already paid for and invalidate the modifiers standing in
 * the slot they bought — which is the failure §10.4 spends a paragraph forbidding.
 */
public class KeystoneModifier extends Modifier implements VolatileDataModifierHook {

    /** This modifier's id, referenced by the station service and by pack data. */
    public static final ModifierId ID = new ModifierId(RunicSkills.MOD_ID, "keystone");

    @Override
    protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(this, ModifierHooks.VOLATILE_DATA);
    }

    @Override
    public void addVolatileData(IToolContext context, ModifierEntry entry, ToolDataNBT data) {
        data.addSlots(SlotType.UPGRADE, 1);
    }
}
