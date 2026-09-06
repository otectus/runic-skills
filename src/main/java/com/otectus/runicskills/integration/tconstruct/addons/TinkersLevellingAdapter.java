package com.otectus.runicskills.integration.tconstruct.addons;

import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.scripting.TinkerScriptHooks;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import pyre.tinkerslevellingaddon.ImprovableModifier;
import pyre.tinkerslevellingaddon.util.ToolLevellingUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Tinkers' Levelling Addon: player progression and tool progression stay separate (§12.3).
 *
 * <p>The add-on owns tool experience, levels, slot and stat rewards, histories and its own cap.
 * Runic owns player levels. Seasoned Hands is the single deliberate point of contact between them,
 * and it moves in one direction only: it makes one <em>incoming</em> tool-experience award larger.
 * No tool experience becomes player experience, no player level grants a free slot, and nothing
 * here calls {@code addExperience} again — the award is scaled in place at the seam, which is what
 * §12.3 means by "changes the incoming positive legitimate award once" and by forbidding recursion.
 *
 * <p><b>Three awards get no multiplier, on purpose.</b> A non-positive amount (a command edit or a
 * correction), an award to a tool already at the add-on's cap, and an award with no identified
 * player action behind it. The last is the interesting one: block breaks and melee hits arrive
 * inside a Runic action frame opened by a seam this mod owns, while a {@code /tinkerslevelling
 * experience add} grant arrives inside nothing. §12.3 says an unknown origin gets no multiplier by
 * default, and the frame is how "unknown" is answered rather than guessed.
 *
 * <p><b>The carry belongs to one player and one tool.</b> A 10% bonus on an award of three is worth
 * 0.3, and truncating that per award would make the perk do nothing for small awards. The remainder
 * is held per player against the tool item it was earned on; a different tool drops it rather than
 * moving it, because §12.3 forbids transferring a carry between tools or players, and it is never
 * written to the item. See {@code TcAddonState} for why the granularity is the item and not the
 * stack, and why that is bounded rather than approximate.
 */
public final class TinkersLevellingAdapter {

    private TinkersLevellingAdapter() {
    }

    /** Nothing to install: the only seam is a mixin that calls straight into {@link #scaleExperience}. */
    public static void install() {
        // Deliberately empty. The contract with TcAddonRegistry is that this method exists and
        // returns; the perk's whole surface is the one gated mixin on ToolLevellingUtil.
    }

    /**
     * The tool experience an award should actually be worth, given who earned it and on what.
     *
     * <p>Called from {@code MixToolLevellingUtil} on the incoming argument of
     * {@code ToolLevellingUtil.addExperience(ToolStack, int, ServerPlayer)} — the source seam §12.3
     * names — so the add-on's own level-up, slot and stat handling runs exactly once, on a number
     * this perk has adjusted, rather than a second time on a number this mod added.
     *
     * @param tool   the tool being awarded, used for the cap check and the carry's identity
     * @param amount the award the add-on is about to apply
     * @param player who earned it, or {@code null} for an award with no player behind it
     * @return the amount to apply instead, never below {@code amount}
     */
    /**
     * The tool level each thread saw when it entered {@code addExperience}.
     *
     * <p>A level transition is only observable as a difference, and the add-on's own level-up runs
     * between the two ends of that one call. A {@code ThreadLocal} rather than a field because the
     * value belongs to one call on one thread, and the two injections that read and write it are
     * the only code that ever touches it.
     */
    private static final ThreadLocal<Integer> LEVEL_BEFORE_AWARD = new ThreadLocal<>();

    /** Records the tool's level before the add-on applies an award. */
    public static void beforeAward(ToolStack tool, ServerPlayer player) {
        if (tool == null || player == null) return;
        LEVEL_BEFORE_AWARD.set(tool.getPersistentData().getInt(ImprovableModifier.LEVEL_KEY));
    }

    /**
     * Publishes a real level transition to {@code RunicSkillsEvents.tinkerToolLevelChanged}.
     *
     * <p>Only a change is published: every award passes through this seam and almost none of them
     * cross a level, so posting on each would make the event an experience firehose rather than the
     * "after a real levelling add-on transition" §14.5 describes. Nothing here awards anything —
     * the observation is downstream of the add-on's own handling, and §12.3 forbids a second award.
     */
    public static void afterAward(ToolStack tool, int amount, ServerPlayer player) {
        Integer before = LEVEL_BEFORE_AWARD.get();
        LEVEL_BEFORE_AWARD.remove();
        if (before == null || tool == null || player == null) return;
        int after = tool.getPersistentData().getInt(ImprovableModifier.LEVEL_KEY);
        if (after == before) return;
        TinkerScriptHooks.postToolLevelChanged(player, new TinkerScriptHooks.ToolLevelChange(
                ForgeRegistries.ITEMS.getKey(tool.getItem()), before, after, "experience", amount));
    }

    public static int scaleExperience(ToolStack tool, int amount, ServerPlayer player) {
        if (amount <= 0 || tool == null || player == null) return amount;
        if (!TcAddonHooks.active(player, RegistryPerks.TC_SEASONED_HANDS,
                Capability.ADDON_TOOL_LEVELLING)) {
            return amount;
        }
        // An award with no identified player action behind it — a command grant, a script — is not
        // something the player did, so it is not something the perk pays on.
        if (!RunicActionContext.isOrdinaryUseBy(player.getUUID())) return amount;

        // The add-on's own cap, asked of the add-on. Reading its config field ourselves would be a
        // second copy of a rule that is theirs to change.
        int level = tool.getPersistentData().getInt(ImprovableModifier.LEVEL_KEY);
        if (!ToolLevellingUtil.canLevelUp(level)) return amount;

        int percent = HandlerCommonConfig.HANDLER.instance().tcSeasonedHandsPercent;
        if (percent <= 0) return amount;

        TcAddonState.Player state = TcAddonState.of(player.getUUID());
        if (state.seasonedHandsItem != tool.getItem()) {
            state.seasonedHandsCarry = 0.0;
            state.seasonedHandsItem = tool.getItem();
        }
        double total = amount * (percent / 100.0) + state.seasonedHandsCarry;
        int extra = (int) Math.floor(total);
        state.seasonedHandsCarry = total - extra;
        return extra <= 0 ? amount : amount + extra;
    }
}
