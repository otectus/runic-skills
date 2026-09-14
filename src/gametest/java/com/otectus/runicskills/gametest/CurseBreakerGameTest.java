package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CurseBreakerGameTest {
    @GameTest(template = "empty")
    public static void cursedBooksBecomeOrdinaryBooksWhenThePerkIsActive(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "curse_breaker_book");
        var perk = RegistryPerks.CURSE_BREAKER.get();
        SkillCapability capability = SkillCapability.get(player);
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) throw new GameTestAssertException("could not enable Curse Breaker");

        ItemStack cursed = EnchantedBookItem.createForEnchantment(
                new EnchantmentInstance(Enchantments.BINDING_CURSE, 1));
        cursed.setHoverName(Component.literal("A kept name"));
        GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory());
        menu.getSlot(0).set(cursed);
        ItemStack result = menu.getSlot(2).getItem();
        if (!result.is(Items.BOOK) || !EnchantmentHelper.getEnchantments(result).isEmpty()
                || !result.getHoverName().getString().equals("A kept name")) {
            throw new GameTestAssertException("Curse Breaker did not produce an uncursed named book: " + result);
        }
        // Preview generation must not mutate the source book before a take.
        if (EnchantmentHelper.getEnchantments(cursed).getOrDefault(Enchantments.BINDING_CURSE, 0) != 1) {
            throw new GameTestAssertException("grindstone preview mutated the input book");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cursedBooksKeepTheirCurseWithoutThePerk(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "no_curse_breaker_book");
        ItemStack cursed = EnchantedBookItem.createForEnchantment(
                new EnchantmentInstance(Enchantments.VANISHING_CURSE, 1));
        GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory());
        menu.getSlot(0).set(cursed);
        ItemStack result = menu.getSlot(2).getItem();
        if (!result.is(Items.ENCHANTED_BOOK)
                || EnchantmentHelper.getEnchantments(result).getOrDefault(Enchantments.VANISHING_CURSE, 0) != 1) {
            throw new GameTestAssertException("grindstone removed a curse without Curse Breaker");
        }
        helper.succeed();
    }
}
