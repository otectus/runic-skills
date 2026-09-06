package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistryTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Who may fit a keystone, to what, and the sentence explaining a refusal.
 *
 * <p>One place rather than two because the answer is asked twice, from opposite ends of the same
 * take: {@link KeystoneStationRecipe} asks it while computing the station's result, so an
 * ineligible smith who happens to be the one the result is being computed for sees the reason in
 * the station rather than an unexplained blank; and {@link TConstructStationBridge#allowsTake} asks
 * it again at {@code Slot.mayPickup}, for the player actually taking the item.
 *
 * <p><b>The second ask is the authoritative one, and it has to be.</b> The station computes one
 * result and caches it for every player looking at it, with no player attached — 3.11's
 * {@code calcResult(Player)} is handed {@code null} on every path that fills that cache. So the
 * preview cannot be trusted to have been computed for the taker, and a service that validated only
 * there would hand a keystone to whoever clicked after an eligible smith walked away. Refusing at
 * {@code mayPickup} refuses before the native code consumes an input or delivers a stack, which is
 * §10.4's "material cost and server validation occur on commit" without a half-finished
 * transaction to unwind.
 */
final class KeystoneService {

    private KeystoneService() {
    }

    /**
     * Whether the keystone recipe applies to the item in the station's tool slot at all.
     *
     * <p>Deliberately does not ask whether a keystone is already fitted: a tool that has one still
     * matches, so the station can say so. A tool outside the allowlist does not match, so the
     * station goes on to whatever else its contents are a recipe for.
     */
    static boolean isEligibleTool(ItemStack stack) {
        return TConstructEquipmentAdapter.isNativeTool(stack)
                && stack.is(RegistryTags.Items.KEYSTONE_ELIGIBLE);
    }

    /** Whether {@code tool} already carries a keystone. One per tool, however many smiths work on it. */
    static boolean alreadyFitted(ToolStack tool) {
        return tool.getModifierLevel(KeystoneModifier.ID) > 0;
    }

    /**
     * Why {@code player} may not be fitted a keystone right now, or {@code null} when they may.
     *
     * <p>A {@code null} player is not a refusal here: it means nobody is asking, which is the state
     * the shared preview is computed in. The take guard never sees one.
     */
    static Component refusal(Player player) {
        // Nobody asking, or a client asking: 3.11 recomputes the preview on the client from the
        // synced recipe, and the client has no capability data to judge with. Answering "not a
        // smith" there would show every player an error the server disagrees with.
        if (player == null || player.level().isClientSide()) return null;
        if (!(player instanceof ServerPlayer smith) || player instanceof FakePlayer) {
            return Component.translatable("message.runicskills.tconstruct.keystone.no_smith");
        }
        if (RegistryPerks.TC_KEYSTONE_TINKER == null
                || !HandlerCommonConfig.HANDLER.instance().enableTConstructPerks
                || !TConstructCompatibilityStatus.current().supports(Capability.KEYSTONE)) {
            return Component.translatable("message.runicskills.tconstruct.keystone.unavailable");
        }
        if (!TConstructPerkHandler.active(smith, RegistryPerks.TC_KEYSTONE_TINKER, Capability.KEYSTONE)) {
            // The two reasons active() folds together are separated above, so "the server turned
            // it off" and "you have not earned it" are never reported as each other.
            return Component.translatable("message.runicskills.tconstruct.keystone.requires_perk",
                    RegistryPerks.TC_KEYSTONE_TINKER.get().requiredLevel);
        }
        return null;
    }
}
