package com.otectus.runicskills.gametest.spartanweaponry;

import com.oblivioussp.spartanweaponry.api.IWeaponTraitContainer;
import com.oblivioussp.spartanweaponry.api.WeaponMaterial;
import com.oblivioussp.spartanweaponry.api.WeaponTraits;
import com.oblivioussp.spartanweaponry.api.trait.IMeleeTraitCallback;
import com.oblivioussp.spartanweaponry.api.trait.WeaponTrait;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.TwoHandedExemption;
import com.otectus.runicskills.common.combat.TwoHandedWielding;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.CombatEventHandler;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Titan's Grip on a real Spartan Weaponry two-handed weapon.
 *
 * <p>Runs only under {@code -PspartanProfile=true}, registered from
 * {@code gametest.SpartanGameTests}. Spartan brings the case Better Combat alone cannot: its own
 * two-handed trait, which imposes Mining Fatigue and a damage reduction the moment both hands are
 * full. Revealing the shield turns those penalties on for exactly the players who took the perk, so
 * suppressing them is part of the feature rather than a nicety — and the suppression has to be
 * narrow, which is what the zombie controls here check. A non-player wielder, and the trait's own
 * arithmetic, are both untouched.
 *
 * <p>An iron halberd is two-handed by both routes at once in this profile: Spartan declares the
 * trait on the item, and {@code data/spartanweaponry/weapon_attributes/iron_halberd.json} inherits
 * {@code bettercombat:halberd}. That is the real-world combination, not a contrivance.
 */
@PrefixGameTestTemplate(false)
public class TitansGripHalberdGameTest {

    private static final String EMPTY = "empty";
    private static final float BASE_DAMAGE = 10.0F;
    private static final ResourceLocation HALBERD =
            new ResourceLocation("spartanweaponry", "iron_halberd");

    /** The shield is visible, raised and unpunished, and the perk pays its bonus. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID, timeoutTicks = 200)
    public static void halberdAndShieldWorkTogether(GameTestHelper helper) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousDrop = config.dropLockedItems;
        ServerPlayer plain = null;
        ServerPlayer titan = null;
        try {
            config.dropLockedItems = false;
            ItemStack halberd = halberd();
            plain = MockPlayers.onlineServerPlayer(helper, "spartan_titans_grip_plain");
            titan = MockPlayers.onlineServerPlayer(helper, "spartan_titans_grip_titan");
            freshen(plain);
            freshen(titan);
            clearPerk(plain, RegistryPerks.TITANS_GRIP);
            clearPerk(titan, RegistryPerks.TITANS_GRIP);
            arm(plain, halberd.copy(), new ItemStack(Items.SHIELD));
            arm(titan, halberd.copy(), new ItemStack(Items.SHIELD));

            assertTrue(TwoHandedWielding.isTwoHanded(TwoHandedWielding.realMainHand(titan)),
                    "an iron halberd is a two-handed weapon");
            assertTrue(plain.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
                    "without the perk the shield is hidden");

            enablePerk(titan, RegistryPerks.TITANS_GRIP);
            assertTrue(TwoHandedExemption.applies(titan), "halberd + shield + perk is exempt");
            assertTrue(titan.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                    "with the perk the shield is visible");

            titan.startUsingItem(InteractionHand.OFF_HAND);
            for (int i = 0; i < 6; i++) titan.doTick();
            assertTrue(titan.isBlocking(), "the shield actually blocks while a halberd is held");
            assertTrue(!titan.hasEffect(MobEffects.DIG_SLOWDOWN),
                    "six ticks of holding both did not apply Spartan's Mining Fatigue");

            // The damage bonus, through the same handler entry point the server uses.
            Zombie target = helper.spawn(EntityType.ZOMBIE, 2, 2, 3);
            target.setNoAi(true);
            titan.setGameMode(GameType.SURVIVAL);
            CombatEventHandler handler = new CombatEventHandler();
            LivingHurtEvent hit = new LivingHurtEvent(
                    target, titan.damageSources().playerAttack(titan), BASE_DAMAGE);
            handler.onLivingHurtStrengthAttacker(hit);
            float expected = BASE_DAMAGE * (1.0F + config.titansGripPercent / 100.0F);
            assertEquals(expected, hit.getAmount(), "Titan's Grip pays its melee bonus");
            RunicSkills.getLOGGER().info("TITANS_GRIP_SPARTAN reveal: sources={} plainOffhand=empty "
                            + "titanOffhand={} blocking={} miningFatigue={} pct={} base={} amount={}",
                    TwoHandedWielding.sourceNames(),
                    titan.getItemBySlot(EquipmentSlot.OFFHAND).getItem(),
                    titan.isBlocking(), titan.hasEffect(MobEffects.DIG_SLOWDOWN),
                    config.titansGripPercent, BASE_DAMAGE, hit.getAmount());
        } finally {
            config.dropLockedItems = previousDrop;
            MockPlayers.logOut(plain);
            MockPlayers.logOut(titan);
        }
        helper.succeed();
    }

    /**
     * The trait's two penalties, invoked exactly as Spartan invokes them.
     *
     * <p>The zombie is the control that keeps this honest: it holds the same halberd and the same
     * shield, it is not a player, and it gets both penalties in full. So the player's escape is the
     * exemption, not a mixin that switched the trait off for everybody.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID, timeoutTicks = 200)
    public static void twoHandedPenaltiesAreSuppressedOnlyForTheExempt(GameTestHelper helper) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousDrop = config.dropLockedItems;
        ServerPlayer titan = null;
        try {
            config.dropLockedItems = false;
            ItemStack halberd = halberd();
            Item item = halberd.getItem();
            if (!(item instanceof IWeaponTraitContainer<?> container)) {
                throw new GameTestAssertException("spartanweaponry:iron_halberd is not an "
                        + "IWeaponTraitContainer; the API this integration reads has changed");
            }
            WeaponTrait trait = container.getFirstWeaponTraitWithType(WeaponTraits.TYPE_TWO_HANDED);
            if (trait == null) {
                throw new GameTestAssertException("an iron halberd has no two_handed weapon trait; "
                        + "the trait this integration suppresses has moved");
            }
            IMeleeTraitCallback callback = trait.getMeleeCallback().orElseThrow(
                    () -> new GameTestAssertException("the two_handed trait exposes no melee callback"));
            WeaponMaterial material = container.getMaterial();

            titan = MockPlayers.onlineServerPlayer(helper, "spartan_penalty_titan");
            titan.setGameMode(GameType.SURVIVAL);
            freshen(titan);
            clearPerk(titan, RegistryPerks.TITANS_GRIP);
            arm(titan, halberd.copy(), new ItemStack(Items.SHIELD));
            enablePerk(titan, RegistryPerks.TITANS_GRIP);
            assertTrue(TwoHandedExemption.applies(titan), "the player under test is exempt");

            Zombie wielder = helper.spawn(EntityType.ZOMBIE, 2, 2, 3);
            wielder.setNoAi(true);
            wielder.setItemSlot(EquipmentSlot.MAINHAND, halberd.copy());
            wielder.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

            // onItemUpdate: Mining Fatigue for the zombie, nothing for the exempt player.
            callback.onItemUpdate(material, TwoHandedWielding.realMainHand(titan),
                    titan.level(), titan, titan.getInventory().selected, true);
            assertTrue(!titan.hasEffect(MobEffects.DIG_SLOWDOWN),
                    "the exempt player takes no Mining Fatigue from the two_handed trait");
            callback.onItemUpdate(material, wielder.getMainHandItem(),
                    wielder.level(), wielder, 0, true);
            assertTrue(wielder.hasEffect(MobEffects.DIG_SLOWDOWN),
                    "a non-exempt wielder still takes Spartan's Mining Fatigue "
                            + "(otherwise this test proves nothing)");

            // modifyDamageDealt: full damage for the exempt player, Spartan's reduction otherwise.
            DamageSource playerSource = titan.damageSources().playerAttack(titan);
            float forPlayer = callback.modifyDamageDealt(
                    material, BASE_DAMAGE, playerSource, titan, wielder);
            assertEquals(BASE_DAMAGE, forPlayer,
                    "the exempt player's hit is not reduced by the two_handed trait");
            float forZombie = callback.modifyDamageDealt(
                    material, BASE_DAMAGE, wielder.damageSources().mobAttack(wielder),
                    wielder, titan);
            assertTrue(forZombie < BASE_DAMAGE,
                    "a non-exempt wielder still loses damage to the two_handed trait "
                            + "(otherwise this test proves nothing), got " + forZombie);

            // And the exemption is still conditional: swap the shield for a torch and Spartan's
            // penalty applies again, because the perk's terms are no longer met.
            arm(titan, halberd.copy(), new ItemStack(Items.TORCH));
            assertTrue(!TwoHandedExemption.applies(titan), "a torch is not a shield");
            float withTorch = callback.modifyDamageDealt(
                    material, BASE_DAMAGE, playerSource, titan, wielder);
            assertEquals(BASE_DAMAGE, withTorch,
                    "Better Combat hides the torch, so Spartan sees one hand full either way");
            RunicSkills.getLOGGER().info("TITANS_GRIP_SPARTAN penalties: trait={} exemptFatigue={} "
                            + "controlFatigue={} exemptDamage={} controlDamage={}",
                    trait.getType(), titan.hasEffect(MobEffects.DIG_SLOWDOWN),
                    wielder.hasEffect(MobEffects.DIG_SLOWDOWN), forPlayer, forZombie);
        } finally {
            config.dropLockedItems = previousDrop;
            MockPlayers.logOut(titan);
        }
        helper.succeed();
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static ItemStack halberd() {
        Item item = ForgeRegistries.ITEMS.getValue(HALBERD);
        if (item == null || item == Items.AIR) {
            throw new GameTestAssertException("Spartan Weaponry is loaded but " + HALBERD
                    + " is not registered");
        }
        return new ItemStack(item);
    }

    /** Undoes anything a previous run left on the reused world save. See {@code TitansGripGameTest}. */
    private static void freshen(ServerPlayer player) {
        player.removeAllEffects();
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            player.getInventory().armor.set(i, ItemStack.EMPTY);
        }
    }

    private static void arm(ServerPlayer player, ItemStack mainHand, ItemStack offHand) {
        player.getInventory().items.set(player.getInventory().selected, mainHand);
        player.getInventory().offhand.set(0, offHand);
    }

    /**
     * Clears the perk state this test is about, because a gametest server reuses its world and a
     * player whose profile UUID comes from its name is loaded back with the previous run's
     * capability. See the same helper in {@code TitansGripGameTest}.
     */
    private static void clearPerk(ServerPlayer player,
                                  net.minecraftforge.registries.RegistryObject<Perk> registered) {
        if (registered == null) return;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return;
        Perk perk = registered.get();
        capability.setPerkRank(perk, 0);
        capability.setSkillLevel(perk.getSkill(), 0);
    }

    private static void enablePerk(ServerPlayer player, net.minecraftforge.registries.RegistryObject<Perk> registered) {
        if (registered == null) {
            throw new GameTestAssertException("Titan's Grip is not registered with Spartan Weaponry "
                    + "loaded; RegistryPerks' gate is wrong");
        }
        Perk perk = registered.get();
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) {
            throw new GameTestAssertException("the test player has no Runic Skills capability");
        }
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) {
            throw new GameTestAssertException("could not enable perk " + perk.getName()
                    + "; the fixture, not the perk, is broken");
        }
    }

    private static void assertTrue(boolean condition, String what) {
        if (!condition) throw new GameTestAssertException(what);
    }

    private static void assertEquals(float expected, float actual, String what) {
        if (Math.abs(expected - actual) > 1.0E-4F) {
            throw new GameTestAssertException(what + " (expected " + expected + ", got " + actual + ")");
        }
    }
}
