package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.crafting.CraftOperationKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.Objects;

/**
 * What one player would get from a native station right now, and what it was computed from.
 *
 * <p>Spec §6.2 asks for this shape by name, and the reason is a duplication bug waiting to happen:
 * the native result is computed once and cached on the block entity, shared by everyone looking at
 * the station. Baking player A's perks into that cache would let player B take A's bonus. So the
 * cache is never touched — a quote is a private answer to "what would <em>you</em> get", derived
 * from the shared base and thrown away.
 *
 * <p>The fingerprints and revision identify display updates. Taking remains a native menu action:
 * the server re-evaluates the current operation rather than trusting a previously displayed quote.
 * The client does not submit this revision as authorization for a take.
 *
 * @param player            who the quote is for; a quote is never valid for anybody else
 * @param menuId            the container id it was issued against
 * @param recipeId          the native recipe that matched, or {@code null} when none did
 * @param kind              what that recipe does, as this mod classifies operations
 * @param inputFingerprint  a cheap hash of the station's inputs at quote time
 * @param baseFingerprint   the same for the native result before any Runic change
 * @param revision          a monotonically increasing number, unique per issuing server session
 * @param preview           the result this player would actually receive, Runic changes included
 */
public record StationQuote(UUID player, int menuId, ResourceLocation recipeId,
                           CraftOperationKind kind, int inputFingerprint, int baseFingerprint,
                           long revision, ItemStack preview) {

    /** Whether this quote still describes the station it was taken from. */
    public boolean matches(int currentInputFingerprint, int currentBaseFingerprint) {
        return inputFingerprint == currentInputFingerprint && baseFingerprint == currentBaseFingerprint;
    }

    /** A quote refresh includes player-specific bonuses and recipe changes, excluding revision. */
    public boolean sameOffer(StationQuote other) {
        return other != null && player.equals(other.player) && menuId == other.menuId
                && Objects.equals(recipeId, other.recipeId) && kind == other.kind
                && inputFingerprint == other.inputFingerprint && baseFingerprint == other.baseFingerprint
                && ItemStack.matches(preview, other.preview);
    }
}
