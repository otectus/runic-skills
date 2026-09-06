package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.durability.RepairSource;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Every repair this mod pays for on a native tool, and the native rules that apply to it.
 *
 * <p>Tinkers' splits a repair in two. The recipe works out how much durability a given material is
 * worth on a given tool, scales it by the tool's own repair factor, and only then calls
 * {@code ToolDamageUtil.repair}, which does nothing but subtract. So the helper is the sink, not
 * the rule — and a Runic repair that called only the sink would be a repair with none of the
 * tool's own rules applied to it. Spec §5.3: "for Runic-origin Auto Repair, evaluate the native
 * repair factor explicitly before calling the native repair helper; the helper does not supply it
 * for you."
 *
 * <p>The factor is the composed {@code REPAIR_FACTOR} hook over the tool's modifiers, which is the
 * same composition {@code TinkerStationRepairRecipe} performs. A modifier that forbids repair
 * returns zero and this returns zero points spent — the honest answer, and the one that stops a
 * perk from quietly repairing something the tool says cannot be repaired.
 *
 * <p><b>Sources this mod did not originate are not amplified.</b> {@link RepairSource#UNKNOWN} is
 * repaired at face value with the native factor applied and nothing added, because §5.3 says an
 * unattributable repair proceeds unchanged and earns no bonus. The rest of that section — the
 * bonus-restoration mode and its fractional carry — lands with the station bridge in S3; this
 * class is the read side that S3 builds on.
 */
public final class TConstructRepairBridge {

    private TConstructRepairBridge() {
    }

    /**
     * Mends {@code stack} by up to {@code points}, and reports how many were actually spent.
     *
     * <p>The report is the committed change, read back from the tool rather than assumed from the
     * request: §5.3 admits only positive committed repair as eligible for anything, and a budget
     * that credited itself for the request would drain on a tool that took none of it.
     */
    public static int repair(ItemStack stack, int points, RepairSource source) {
        if (!TConstructEquipmentAdapter.isNativeTool(stack) || points <= 0) return 0;

        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken() || tool.isUnbreakable()) return 0;
        int damage = tool.getDamage();
        if (damage <= 0) return 0;

        float factor = repairFactor(tool);
        if (factor <= 0.0F) return 0;

        int scaled = (int) Math.floor(Math.min(points, damage) * (double) factor);
        if (scaled <= 0) return 0;

        ToolDamageUtil.repair(tool, scaled);
        // The committed difference, not the requested amount: the helper clamps to the remaining
        // damage, and a caller holding a budget must only be charged for what it bought.
        return Math.max(0, damage - tool.getDamage());
    }

    /**
     * The tool's own repair multiplier, composed across its modifiers exactly as the station recipe
     * composes it. {@code 1.0} on a tool with no opinion, {@code 0.0} on one that refuses repair.
     */
    public static float repairFactor(ToolStack tool) {
        float factor = 1.0F;
        for (ModifierEntry entry : tool.getModifierList()) {
            factor = entry.getHook(ModifierHooks.REPAIR_FACTOR).getRepairFactor(tool, entry, factor);
            if (factor <= 0.0F) return 0.0F;
        }
        return factor;
    }
}
