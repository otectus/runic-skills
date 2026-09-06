package com.otectus.runicskills.common.crafting;

import com.otectus.runicskills.common.equipment.EquipmentProfile;
import com.otectus.runicskills.common.equipment.EquipmentProfileService;
import com.otectus.runicskills.common.rules.PackRule;
import com.otectus.runicskills.common.rules.PackRuleIndex;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryTags;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Whether a craft may be paid a bonus copy of its result, and how many.
 *
 * <p>Assembly Line and Mass Production copied whatever the event handed them, with no idea what the
 * craft had been. Because {@code ItemCraftedEvent} fires for every recipe type in the game, that
 * paid out for repairing two damaged pickaxes into one, for compressing ingots into a block and
 * decompressing them back, and for any modded menu that fires the event — each a way to end a
 * cycle with more material than it started with (RS207-01, RS207-10).
 *
 * <p>The rules below are therefore <b>default-deny</b>. A craft earns a copy only when it plainly
 * made something new out of something else, and every refusal names a specific way the reward could
 * otherwise be turned into a loop. That is deliberately conservative: an unrewarded legitimate
 * craft is a disappointment, a rewarded illegitimate one is an economy.
 */
public final class CraftRewardPolicy {

    private CraftRewardPolicy() {
    }

    /**
     * How many extra copies of the result this craft may be paid, across every perk together.
     *
     * @return {@code 0} when no reward is allowed; otherwise the configured cap
     */
    public static int allowedExtraOutputs(CraftOperationContext context, HandlerCommonConfig config) {
        int cap = Math.max(0, config.craftRewardMaxExtraOutputs);
        if (cap == 0) return 0;
        if (context == null || context.fakePlayer()) return 0;

        ItemStack result = context.result();
        if (result.isEmpty()) return 0;

        // A pack's outright refusal, before anything else gets a say. Denied beats allowed.
        if (result.is(RegistryTags.Items.CRAFT_REWARD_DENIED)) return 0;

        // A pack rule about this exact result. Only a refusal is honoured here: §13.3 step 5 says
        // an allow rule may not override the mandatory exclusions below, so a rule that permits an
        // extra output leaves every one of them standing and simply declines to add a refusal of
        // its own. The lookup is skipped entirely when no pack has written a reward rule.
        if (PackRuleIndex.get().hasCraftRewardPolicies()) {
            String provider = EquipmentProfileService.profile(result)
                    .map(EquipmentProfile::providerId).orElse(null);
            Optional<PackRule> rule = PackRuleIndex.get()
                    .craftRewardPolicy(provider, ForgeRegistries.ITEMS.getKey(result.getItem()));
            if (rule.isPresent() && !rule.get().allowExtraOutput()) return 0;
        }

        // Only a manufacture. CONVERSION is compression and decompression; REPAIR, PART_SWAP,
        // MODIFY, RENAME and RECYCLE all return an item that already existed; UNKNOWN is a craft
        // this mod could not classify and therefore cannot vouch for.
        if (context.kind() != CraftOperationKind.MANUFACTURE) return 0;

        // Equipment is never duplicated, tag or no tag. An unstackable or damageable result is the
        // shape of every tool, weapon and piece of armour in the game, and a second one of those is
        // worth incomparably more than a second plank.
        if (result.getMaxStackSize() == 1) return 0;
        if (result.isDamageableItem()) return 0;

        // A stack that carries an inventory, a tank or an energy buffer duplicates its CONTENTS
        // when it is copied, which is a larger reward than the item and is invisible in the item.
        if (result.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                || result.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()
                || result.getCapability(ForgeCapabilities.ENERGY).isPresent()) {
            return 0;
        }

        boolean packAllowed = result.is(RegistryTags.Items.CRAFT_REWARD_ALLOWED);

        // NBT on a result means the craft carried something forward — an enchantment, a stored
        // position, a written page. Copying it copies that too. A pack that knows its own recipe is
        // harmless can lift this one.
        if (result.hasTag() && !packAllowed) return 0;

        // Everything consumed was one item type: the compression shape, whatever the recipe id
        // says. Lifted by the tag because a pack may legitimately ship a one-ingredient recipe that
        // is a real manufacture.
        if (context.distinctInputItems() <= 1 && !packAllowed) return 0;

        // A craft that hands back a bucket or a bottle did not consume what it appears to have
        // consumed, so its true cost is lower than the recipe reads and a copy is worth more than
        // it looks. Not liftable: the discrepancy is in the recipe, not in the pack's judgement.
        if (!context.containerItems().isEmpty()) return 0;

        return cap;
    }
}
