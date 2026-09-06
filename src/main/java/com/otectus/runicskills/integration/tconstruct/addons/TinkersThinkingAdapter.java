package com.otectus.runicskills.integration.tconstruct.addons;

import com.otectus.runicskills.common.util.DurationMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.integration.tconstruct.TConstructEquipmentAdapter;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Tinkers' Thinking: three perks keyed on what the add-on's own handlers leave behind.
 *
 * <p><b>Nothing here names a Thinking class, and that is not caution — it is the only design the
 * jar admits.</b> Both of its triggers are driven by
 * {@code TinkerDataCapability.TinkerDataKey}s ({@code ModDataKeys.SculkStruggle} and
 * {@code .SculkCatalyse}), and that type declares neither {@code equals} nor {@code hashCode} while
 * {@code TinkerDataKey.of} allocates a fresh instance every call. A key rebuilt from its
 * {@code ResourceLocation} therefore cannot match the one the add-on put in the holder's map, so
 * "read the level the add-on stored" is not a thing any consumer can do, with or without a compile
 * dependency. What the add-on <em>does</em> leave is two mob effects, and those are registry
 * objects that any mod can look up by id.
 *
 * <p><b>What was read, out of {@code Tinkers-Thinking-0.1.6.6.3.jar}:</b>
 * <ul>
 *   <li>{@code common.library.OnDeath.onLivingDying(LivingDeathEvent)} — when the wearer's
 *       {@code SculkStruggle} level is positive and they already carry
 *       {@code tinkers_thinking:sculk_power}, it cancels the death, sets health to 1 and applies
 *       {@code tinkers_thinking:last_effort}. <b>It spends no durability at all</b>, which is why
 *       Last Thought is a reprieve that follows the save rather than a discount on its cost: there
 *       is no cost to discount.</li>
 *   <li>{@code common.library.OnExpPickUp.onPlayerPickupXp(PlayerXpEvent.PickupXp)} — when the
 *       wearer's {@code SculkCatalyse} level is positive and they carry
 *       {@code tinkers_thinking:sculk_power}, it converts the orb into {@code 60} ticks of that
 *       effect per point, cancels the pickup and discards the orb. The player keeps none of the
 *       experience, which is what Studied Recall returns a share of.</li>
 *   <li>{@code data/tinkers_thinking/tinkering/modifiers/} — the melee modifier ids
 *       {@link TraitFeatureRegistry.Feature#THINKING_EMBELLISHMENT} lists.</li>
 * </ul>
 *
 * <p><b>Contributions, never replacements.</b> The save, the conversion and the modifiers all stay
 * entirely the add-on's. This mod does not grant, extend, repeat or cancel any of them; it adds one
 * bounded Runic share to a channel the perk handler already composes and caps.
 */
public final class TinkersThinkingAdapter {

    /**
     * The effect the death save applies, and the only public trace it leaves.
     *
     * <p>Read from {@code common.register.ModEffects.last_effort}. Nothing else in the add-on
     * applies it, so its arrival during a cancelled death identifies the save unambiguously.
     */
    private static final ResourceLocation LAST_EFFORT =
            new ResourceLocation("tinkers_thinking", "last_effort");

    /** {@code ModEffects.sculk_power} — the precondition the experience conversion itself checks. */
    private static final ResourceLocation SCULK_POWER =
            new ResourceLocation("tinkers_thinking", "sculk_power");

    private TinkersThinkingAdapter() {
    }

    /** Registers the three contributions and the one notification they need. */
    public static void install() {
        TcAddonHooks.addDeathResolutionObserver(TinkersThinkingAdapter::onDeathResolved);
        TcAddonHooks.addWearAvoidanceContributor(TinkersThinkingAdapter::lastThoughtAvoidance);
        TcAddonHooks.addExperienceContributor(TinkersThinkingAdapter::studiedRecallShare);
        TcAddonHooks.addMeleeDamageContributor(TinkersThinkingAdapter::embellishedFocusBonus);
    }

    /**
     * Last Thought's trigger: a death the add-on refused.
     *
     * <p>Both halves are required. {@code survived} alone would arm on a totem, another mod's save
     * or a cancelled death from anything at all; the effect alone would arm on a
     * {@code /effect give}. Together they are the add-on's save and nothing else.
     */
    private static void onDeathResolved(ServerPlayer player, boolean survived) {
        if (!survived) return;
        if (!TcAddonHooks.active(player, RegistryPerks.TC_THINKING_LAST_THOUGHT,
                Capability.ADDON_THINKING_DEATH_TRIGGER)) {
            return;
        }
        MobEffect lastEffort = ForgeRegistries.MOB_EFFECTS.getValue(LAST_EFFORT);
        if (lastEffort == null || !player.hasEffect(lastEffort)) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        TcAddonState.of(player.getUUID()).lastThoughtUntil = TcAddonHooks.now(player)
                + DurationMath.secondsToTicks(config.tcThinkingLastThoughtSeconds);
    }

    /**
     * Last Thought's share of the add-on wear-avoidance channel, or zero.
     *
     * <p>Player-scoped rather than tool-scoped on purpose: the perk is a reprieve granted to
     * someone who has just been dragged back from a death, and restricting it to one stack would
     * make it depend on which item happened to be in hand at the moment they were saved.
     *
     * <p>No cap of its own: the caller sums this with the other add-on contributions and clamps the
     * total once with {@code tconstructNewWearAvoidanceCap}, which then joins the single avoidance
     * sum every other wear perk shares.
     */
    private static double lastThoughtAvoidance(ServerPlayer player, ItemStack stack) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_THINKING_LAST_THOUGHT,
                Capability.ADDON_THINKING_DEATH_TRIGGER)) {
            return 0.0;
        }
        TcAddonState.Player state = TcAddonState.peek(player.getUUID());
        if (state == null || state.lastThoughtUntil <= 0L) return 0.0;
        if (TcAddonHooks.now(player) > state.lastThoughtUntil) {
            state.lastThoughtUntil = 0L;
            return 0.0;
        }
        return HandlerCommonConfig.HANDLER.instance().tcThinkingLastThoughtPercent / 100.0;
    }

    /**
     * Studied Recall's share of an experience award the add-on consumed, or zero.
     *
     * <p>The caller only asks when a pickup was actually cancelled, and multiplies this by the orb
     * that was destroyed — so the returned amount is bounded above by what the player would have
     * collected had the add-on not been installed. It cannot be a net gain, which is the whole
     * reason it is expressed as a share of a consumed award rather than as an amount.
     *
     * <p>{@code sculk_power} is the add-on's own precondition for cancelling, read from
     * {@code OnExpPickUp}. Requiring it here is what stops the perk from paying out on a pickup
     * some unrelated mod cancelled.
     */
    private static double studiedRecallShare(ServerPlayer player) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_THINKING_STUDIED_RECALL,
                Capability.ADDON_THINKING_XP_TRIGGER)) {
            return 0.0;
        }
        MobEffect sculkPower = ForgeRegistries.MOB_EFFECTS.getValue(SCULK_POWER);
        if (sculkPower == null || !player.hasEffect(sculkPower)) return 0.0;
        return HandlerCommonConfig.HANDLER.instance().tcThinkingStudiedRecallPercent / 100.0;
    }

    /**
     * Embellished Focus' share of the one melee channel, or zero.
     *
     * <p>The modifier is read off the weapon by registry id, which answers for a trait the tool was
     * built with and an upgrade a smith applied alike. The add-on's own conditions on those
     * modifiers — undead, nether, a particular mob — stay the add-on's and are not re-implemented
     * here; this asks only whether the weapon carries one.
     */
    private static double embellishedFocusBonus(ServerPlayer player) {
        if (!TcAddonHooks.active(player, RegistryPerks.TC_THINKING_EMBELLISHED_FOCUS,
                Capability.ADDON_THINKING_EMBELLISHMENT)) {
            return 0.0;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!TConstructEquipmentAdapter.isNativeTool(weapon)) return 0.0;
        ToolStack tool = ToolStack.from(weapon);
        if (tool.isBroken()) return 0.0;
        if (!TraitFeatureRegistry.present(tool, TraitFeatureRegistry.Feature.THINKING_EMBELLISHMENT)) {
            return 0.0;
        }
        return HandlerCommonConfig.HANDLER.instance().tcThinkingEmbellishedFocusPercent / 100.0;
    }
}
