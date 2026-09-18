package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.TwoHandedExemption;
import com.otectus.runicskills.common.combat.TwoHandedWielding;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.network.packet.client.TitansGripSyncCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.CombatEventHandler;
import com.otectus.runicskills.registry.events.TickEventHandler;
import com.otectus.runicskills.registry.perks.Perk;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

/**
 * Titan's Grip, against the mod that used to make it impossible.
 *
 * <p>Better Combat is an {@code implementation} dependency, so it is on the classpath of every run
 * configuration and these cases run in the default {@code runGameTestServer} batch. It defines no
 * two-handed vanilla item, so the fixture supplies one:
 * {@code src/gametest/resources/data/minecraft/weapon_attributes/stick.json} declares
 * {@code bettercombat:halberd} as its parent, which makes a plain stick two-handed to Better Combat
 * and to nothing else. A datapack entry on an item Better Combat does not itself describe is
 * deterministic in a way that borrowing another mod's weapon would not be.
 *
 * <p>The first case is the one that matters most, because it asserts the bug as a precondition: a
 * player with no perk, holding that stick and a shield, is told by {@code getItemBySlot} that the
 * off-hand is empty. Everything after it is only interesting because that is true.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class TitansGripGameTest {

    /** The perk's damage bonus is a percentage of the unmodified hit. */
    private static final float BASE_DAMAGE = 10.0F;

    /** The mod's own {@code SimpleChannel}; every packet asserted here travels on it. */
    private static final ResourceLocation RUNIC_CHANNEL =
            new ResourceLocation(RunicSkills.MOD_ID, "network");

    /**
     * The whole mechanical claim: Better Combat hides the off-hand, Titan's Grip gives it back, and
     * the shield can then actually be raised.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void titansGripRevealsAndUsesTheShield(GameTestHelper helper) {
        if (skipWithoutBetterCombat(helper, "titansGripRevealsAndUsesTheShield")) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousDrop = config.dropLockedItems;
        ServerPlayer plain = null;
        ServerPlayer titan = null;
        try {
            // Locked-item dropping is a different subsystem that would empty the very hands this
            // test is about; the online players here are ticked by the server for real.
            config.dropLockedItems = false;
            plain = MockPlayers.onlineServerPlayer(helper, "titans_grip_plain");
            titan = MockPlayers.onlineServerPlayer(helper, "titans_grip_titan");
            freshen(plain);
            freshen(titan);
            clearPerk(plain, RegistryPerks.TITANS_GRIP);
            clearPerk(titan, RegistryPerks.TITANS_GRIP);
            arm(plain, twoHanded(), new ItemStack(Items.SHIELD));
            arm(titan, twoHanded(), new ItemStack(Items.SHIELD));

            // Precondition, and the reason this perk never worked: the stack is in the inventory,
            // and the accessor every consumer uses says it is not.
            assertTrue(!TwoHandedWielding.realOffhand(plain).isEmpty(),
                    "the shield really is in the off-hand slot");
            assertTrue(TwoHandedWielding.isTwoHanded(TwoHandedWielding.realMainHand(plain)),
                    "the fixture stick is two-handed to Better Combat");
            ItemStack hiddenOffhand = plain.getItemBySlot(EquipmentSlot.OFFHAND);
            assertTrue(hiddenOffhand.isEmpty(),
                    "without the perk, Better Combat hides the off-hand from getItemBySlot");
            assertTrue(plain.getOffhandItem().isEmpty(),
                    "without the perk, getOffhandItem is empty too");
            assertTrue(!TwoHandedExemption.applies(plain), "no perk, no exemption");

            enablePerk(titan, RegistryPerks.TITANS_GRIP);
            assertTrue(TwoHandedExemption.applies(titan), "perk + two-handed weapon + shield");
            ItemStack revealedOffhand = titan.getItemBySlot(EquipmentSlot.OFFHAND);
            assertTrue(revealedOffhand.is(Items.SHIELD),
                    "with the perk, getItemBySlot returns the shield");
            assertTrue(titan.getOffhandItem().is(Items.SHIELD),
                    "with the perk, getOffhandItem returns the shield");
            assertTrue(titan.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.STICK),
                    "the main hand is untouched");
            assertTrue(titan.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                    "other slots are untouched");

            // A raised shield becomes a block after five ticks; six is the margin every other test
            // in this package uses.
            plain.startUsingItem(InteractionHand.OFF_HAND);
            titan.startUsingItem(InteractionHand.OFF_HAND);
            for (int i = 0; i < 6; i++) {
                plain.doTick();
                titan.doTick();
            }
            boolean plainBlocking = plain.isBlocking();
            boolean titanBlocking = titan.isBlocking();
            assertTrue(!plainBlocking,
                    "without the perk there is nothing in the off-hand to raise");
            assertTrue(titanBlocking, "with the perk the shield actually blocks");
            RunicSkills.getLOGGER().info("TITANS_GRIP_TEST reveal: sources={} plainOffhand={} "
                            + "titanOffhand={} plainBlocking={} titanBlocking={}",
                    TwoHandedWielding.sourceNames(), hiddenOffhand, revealedOffhand,
                    plainBlocking, titanBlocking);

            // An exemption is not a licence to reveal anything: a torch is still hidden, because a
            // torch is not what the perk describes.
            arm(titan, twoHanded(), new ItemStack(Items.TORCH));
            assertTrue(!TwoHandedExemption.applies(titan), "a torch is not a shield");
            assertTrue(titan.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
                    "Better Combat still hides a non-shield off-hand");

            // And a one-handed main hand is nothing to do with the perk either.
            arm(titan, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD));
            assertTrue(!TwoHandedExemption.applies(titan), "a sword is not a two-handed weapon");
            assertTrue(titan.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                    "and Better Combat was never hiding that off-hand in the first place");
        } finally {
            config.dropLockedItems = previousDrop;
            MockPlayers.logOut(plain);
            MockPlayers.logOut(titan);
        }
        helper.succeed();
    }

    /** The damage half: paid on a melee hit that meets the exemption, and on nothing else. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void titansGripBonusIsMeleeAndConditional(GameTestHelper helper) {
        if (skipWithoutBetterCombat(helper, "titansGripBonusIsMeleeAndConditional")) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousDrop = config.dropLockedItems;
        ServerPlayer player = null;
        try {
            config.dropLockedItems = false;
            player = MockPlayers.onlineServerPlayer(helper, "titans_grip_damage");
            freshen(player);
            clearPerk(player, RegistryPerks.TITANS_GRIP);
            arm(player, twoHanded(), new ItemStack(Items.SHIELD));
            net.minecraft.world.entity.monster.Zombie target =
                    helper.spawn(EntityType.ZOMBIE, 2, 2, 3);
            target.setNoAi(true);
            // A creative attacker is exempt from the whole outgoing-perk pass, which would make
            // every case below pass for the wrong reason.
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            CombatEventHandler handler = new CombatEventHandler();
            DamageSource melee = player.damageSources().playerAttack(player);

            float unarmed = hurt(handler, target, melee, player);
            assertEquals(BASE_DAMAGE, unarmed, "without the perk there is no bonus");

            enablePerk(player, RegistryPerks.TITANS_GRIP);
            float expected = BASE_DAMAGE * (1.0F + config.titansGripPercent / 100.0F);
            float withPerk = hurt(handler, target, melee, player);
            assertEquals(expected, withPerk,
                    "a melee hit that meets the exemption pays titansGripPercent");

            // Not melee: the player loosed it, but they did not swing a weapon held beside a
            // shield, which is the whole subject of the perk.
            Arrow arrow = new Arrow(helper.getLevel(), player);
            float shot = hurt(handler, target, player.damageSources().arrow(arrow, player), player);
            assertEquals(BASE_DAMAGE, shot, "an arrow is not a two-handed melee swing");

            arm(player, twoHanded(), new ItemStack(Items.TORCH));
            float torch = hurt(handler, target, melee, player);
            assertEquals(BASE_DAMAGE, torch, "no shield, no bonus");

            arm(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD));
            float sword = hurt(handler, target, melee, player);
            assertEquals(BASE_DAMAGE, sword, "a one-handed weapon earns nothing from this perk");
            RunicSkills.getLOGGER().info("TITANS_GRIP_TEST bonus: pct={} base={} noPerk={} "
                            + "withPerk={} arrow={} torchOffhand={} oneHandedWeapon={}",
                    config.titansGripPercent, BASE_DAMAGE, unarmed, withPerk, shot, torch, sword);
        } finally {
            config.dropLockedItems = previousDrop;
            MockPlayers.logOut(player);
        }
        helper.succeed();
    }

    /**
     * One-Handed is a perk for fighting with a hand free, and Better Combat's hidden off-hand was
     * handing it to two-handed builds holding a shield.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void oneHandedReadsTheRealOffhand(GameTestHelper helper) {
        if (skipWithoutBetterCombat(helper, "oneHandedReadsTheRealOffhand")) return;
        if (RegistryPerks.ONE_HANDED == null) {
            throw new GameTestAssertException("One-Handed is not registered; the fixture is broken");
        }
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousDrop = config.dropLockedItems;
        ServerPlayer player = null;
        try {
            config.dropLockedItems = false;
            player = MockPlayers.onlineServerPlayer(helper, "one_handed_offhand");
            freshen(player);
            clearPerk(player, RegistryPerks.TITANS_GRIP);
            enablePerk(player, RegistryPerks.ONE_HANDED);
            arm(player, twoHanded(), new ItemStack(Items.SHIELD));

            boolean shieldHidden = player.getOffhandItem().isEmpty();
            assertTrue(shieldHidden,
                    "Better Combat reports an empty off-hand, which is what used to grant this");
            TickEventHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            var withShield = oneHandedModifier(player);
            assertTrue(withShield == null, "a shield in the real off-hand denies One-Handed");

            // Positive control: with the off-hand genuinely empty the perk still pays out, so the
            // case above is about the shield and not about the perk being broken outright.
            arm(player, twoHanded(), ItemStack.EMPTY);
            TickEventHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            var withEmpty = oneHandedModifier(player);
            assertTrue(withEmpty != null, "an empty off-hand still grants One-Handed");
            RunicSkills.getLOGGER().info("TITANS_GRIP_TEST one-handed: getOffhandItemEmpty={} "
                            + "modifierWithShield={} modifierWithEmptyOffhand={}",
                    shieldHidden, withShield, withEmpty.getAmount());
        } finally {
            config.dropLockedItems = previousDrop;
            MockPlayers.logOut(player);
        }
        helper.succeed();
    }

    /**
     * The other half of the perk: a client drawing somebody else's Titan's Grip shield.
     *
     * <p>The capability is synced to its owner only, so a remote client's copy of a wielder's
     * capability holds no perk at all — {@code isEnabled} there is a certain {@code false}, and the
     * shield the server had already sent was hidden again on arrival. The fix is one flag, set from
     * {@code TitansGripSyncCP}; this is the assertion that the reveal hook actually honours it,
     * driven by setting the flag directly because a GameTest server has no client to receive the
     * packet. The packet that sets it is asserted by
     * {@link #titansGripStateIsSentToTrackingClients} and its bytes by {@code TitansGripSyncCPTest}.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void remoteFlagRevealsTheShieldForAPlayerWithoutTheLocalCapability(
            GameTestHelper helper) {
        if (skipWithoutBetterCombat(helper,
                "remoteFlagRevealsTheShieldForAPlayerWithoutTheLocalCapability")) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousDrop = config.dropLockedItems;
        ServerPlayer remote = null;
        try {
            config.dropLockedItems = false;
            remote = MockPlayers.onlineServerPlayer(helper, "titans_grip_remote");
            freshen(remote);
            // No perk, exactly as a remote client's copy of somebody else's capability has none.
            clearPerk(remote, RegistryPerks.TITANS_GRIP);
            arm(remote, twoHanded(), new ItemStack(Items.SHIELD));
            SkillCapability capability = SkillCapability.get(remote);
            if (capability == null) {
                throw new GameTestAssertException("the test player has no Runic Skills capability");
            }

            capability.setRemoteTitansGrip(false);
            assertTrue(!TwoHandedExemption.applies(remote),
                    "no perk and no flag is the bug's starting state");
            ItemStack beforeFlag = remote.getItemBySlot(EquipmentSlot.OFFHAND);
            assertTrue(beforeFlag.isEmpty(), "and the off-hand is hidden");

            capability.setRemoteTitansGrip(true);
            assertTrue(!RegistryPerks.TITANS_GRIP.get().isEnabled(remote),
                    "the flag is the only thing granting this; the perk itself is still unowned");
            assertTrue(TwoHandedExemption.applies(remote), "the flag satisfies the perk condition");
            ItemStack afterFlag = remote.getItemBySlot(EquipmentSlot.OFFHAND);
            assertTrue(afterFlag.is(Items.SHIELD),
                    "getItemBySlot returns the shield for a player the client only knows by flag");
            assertTrue(remote.getOffhandItem().is(Items.SHIELD), "and so does getOffhandItem");

            // The flag replaces one condition of three, not the rule. A bystander still must not see
            // a torch that Better Combat is hiding on purpose.
            arm(remote, twoHanded(), new ItemStack(Items.TORCH));
            assertTrue(!TwoHandedExemption.applies(remote), "a torch is still not a shield");
            assertTrue(remote.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
                    "and it stays hidden with the flag set");
            arm(remote, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD));
            assertTrue(!TwoHandedExemption.applies(remote),
                    "a one-handed main hand is still nothing to do with the perk");

            // And clearing it puts the presentation back, so a perk lost is a shield hidden again.
            arm(remote, twoHanded(), new ItemStack(Items.SHIELD));
            capability.setRemoteTitansGrip(false);
            assertTrue(remote.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
                    "clearing the flag hides the off-hand again");
            RunicSkills.getLOGGER().info("TITANS_GRIP_TEST remote flag: beforeFlag={} afterFlag={} "
                            + "perkOwned={}", beforeFlag, afterFlag,
                    RegistryPerks.TITANS_GRIP.get().isEnabled(remote));
        } finally {
            config.dropLockedItems = previousDrop;
            MockPlayers.logOut(remote);
        }
        helper.succeed();
    }

    /**
     * The send half: a perk change produces the packet, a sync that changes nothing does not, and a
     * newly-tracking client is told on sight.
     *
     * <p>Both players are real logins over an {@code EmbeddedChannel}, so every packet the server
     * writes is genuinely constructed and queued rather than mocked; the assertions read those
     * queues. What a GameTest cannot supply is a client that would <em>decode</em> them, so the
     * decode is asserted separately and headlessly by {@code TitansGripSyncCPTest}.
     *
     * <p>{@code TRACKING_ENTITY_AND_SELF} is asserted on its self leg rather than by waiting for the
     * chunk map to establish that one mock player tracks another: the self leg is unconditional for
     * a logged-in {@code ServerPlayer}, which makes the assertion about the product's dispatch
     * instead of about the harness's timing. The tracking leg is covered by driving the
     * {@code StartTracking} handler, which is the path a real tracking client takes anyway.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void titansGripStateIsSentToTrackingClients(GameTestHelper helper) {
        if (skipWithoutBetterCombat(helper, "titansGripStateIsSentToTrackingClients")) return;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousDrop = config.dropLockedItems;
        ServerPlayer wielder = null;
        ServerPlayer viewer = null;
        try {
            config.dropLockedItems = false;
            wielder = MockPlayers.onlineServerPlayer(helper, "titans_grip_wielder");
            viewer = MockPlayers.onlineServerPlayer(helper, "titans_grip_viewer");
            freshen(wielder);
            freshen(viewer);
            clearPerk(wielder, RegistryPerks.TITANS_GRIP);
            arm(wielder, twoHanded(), new ItemStack(Items.SHIELD));
            SkillCapability capability = SkillCapability.get(wielder);
            if (capability == null) {
                throw new GameTestAssertException("the test player has no Runic Skills capability");
            }
            EmbeddedChannel wielderChannel = MockPlayers.channelOf(wielder);
            EmbeddedChannel viewerChannel = MockPlayers.channelOf(viewer);
            if (wielderChannel == null || viewerChannel == null) {
                throw new GameTestAssertException("the mock players have no observable channel");
            }

            // A capability sync that does not move the perk sends nothing: the state is false and
            // false is what every client already assumes.
            capability.markTitansGripSent(false);
            wielderChannel.outboundMessages().clear();
            SyncSkillCapabilityCP.send(wielder);
            int afterNoChange = countTitansGripPackets(wielderChannel, wielder.getId());
            assertCount(0, afterNoChange, "a sync with the perk unowned sends no Titan's Grip packet");
            assertTrue(!capability.titansGripSent(), "and records nothing");

            // Granting it, through the same funnel every real edit path uses.
            enablePerk(wielder, RegistryPerks.TITANS_GRIP);
            wielderChannel.outboundMessages().clear();
            SyncSkillCapabilityCP.send(wielder);
            int afterGrant = countTitansGripPackets(wielderChannel, wielder.getId());
            assertCount(1, afterGrant, "granting the perk broadcasts exactly one packet");
            assertTrue(capability.titansGripSent(), "and the sent state is now true");

            // And the next sync for an unrelated reason does not repeat it.
            wielderChannel.outboundMessages().clear();
            SyncSkillCapabilityCP.send(wielder);
            int afterRepeat = countTitansGripPackets(wielderChannel, wielder.getId());
            assertCount(0, afterRepeat, "an unchanged value is not sent again");

            // A client that starts tracking the wielder is told without waiting for a change. Posted
            // on the real FORGE bus rather than called directly, so this also asserts that the
            // handler is actually subscribed — an @EventBusSubscriber that never registered would
            // pass a direct call and do nothing in a game.
            viewerChannel.outboundMessages().clear();
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, wielder));
            int afterTracking = countTitansGripPackets(viewerChannel, wielder.getId());
            assertCount(1, afterTracking, "a newly-tracking client is sent the current state");

            // Losing the perk is a change too, or the shield would stay revealed on every screen
            // that had already been told.
            clearPerk(wielder, RegistryPerks.TITANS_GRIP);
            wielderChannel.outboundMessages().clear();
            SyncSkillCapabilityCP.send(wielder);
            int afterRevoke = countTitansGripPackets(wielderChannel, wielder.getId());
            assertCount(1, afterRevoke, "losing the perk broadcasts the false as well");
            assertTrue(!capability.titansGripSent(), "and the sent state follows it down");

            // Nothing to tell a tracker about a player without the perk.
            viewerChannel.outboundMessages().clear();
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, wielder));
            assertCount(0, countTitansGripPackets(viewerChannel, wielder.getId()),
                    "tracking a player without the perk sends nothing");

            RunicSkills.getLOGGER().info("TITANS_GRIP_TEST sync: noChange={} grant={} repeat={} "
                            + "tracking={} revoke={} wielderId={}",
                    afterNoChange, afterGrant, afterRepeat, afterTracking, afterRevoke,
                    wielder.getId());
        } finally {
            config.dropLockedItems = previousDrop;
            MockPlayers.logOut(wielder);
            MockPlayers.logOut(viewer);
        }
        helper.succeed();
    }

    // --- fixtures -------------------------------------------------------------------------------

    /** A plain stick, which the datapack fixture in this source set makes two-handed. */
    private static ItemStack twoHanded() {
        return new ItemStack(Items.STICK);
    }

    /**
     * Undoes anything a previous run of this test left on the reused world save: lingering effects
     * and armour. The hands are set explicitly by {@link #arm}; nothing else here is.
     */
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

    private static net.minecraft.world.entity.ai.attributes.AttributeModifier oneHandedModifier(
            ServerPlayer player) {
        var instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        return instance == null ? null : instance.getModifier(RegistryAttributes.ONE_HANDED_UUID);
    }

    /** Posts one outgoing-damage evaluation through the handler and returns the resulting amount. */
    private static float hurt(CombatEventHandler handler, LivingEntity target, DamageSource source,
                              ServerPlayer player) {
        LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
        handler.onLivingHurtStrengthAttacker(event);
        return event.getAmount();
    }

    /**
     * Clears the perk state this test is about.
     *
     * <p>{@code MockPlayers} derives the profile UUID from the name, and a gametest server reuses
     * its world between runs, so a player built here is loaded back with whatever the previous run
     * left in its capability — including a perk rank. A "without the perk" case that trusts a fresh
     * player passes on the first run of a new world and fails on the second, which is exactly what
     * happened while this test was being written.
     */
    private static void clearPerk(ServerPlayer player, RegistryObject<Perk> registered) {
        if (registered == null) return;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return;
        Perk perk = registered.get();
        capability.setPerkRank(perk, 0);
        capability.setSkillLevel(perk.getSkill(), 0);
    }

    private static void enablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        if (registered == null) {
            throw new GameTestAssertException("the perk under test is not registered; with Better "
                    + "Combat present it must be");
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

    private static boolean skipWithoutBetterCombat(GameTestHelper helper, String test) {
        if (ModList.get().isLoaded("bettercombat")) return false;
        // Better Combat is an implementation dependency, so this should never fire; it exists so a
        // future build that drops it reports a skip instead of an unexplained failure.
        RunicSkills.getLOGGER().info("TITANS_GRIP_TEST SKIP {}: bettercombat absent", test);
        helper.succeed();
        return true;
    }

    /**
     * How many {@code TitansGripSyncCP} messages addressed to {@code entityId} are sitting in this
     * player's outbound queue.
     *
     * <p>Forge's {@code SimpleChannel} writes its messages inside a
     * {@link ClientboundCustomPayloadPacket} on the mod's channel, prefixed by a one-byte message
     * discriminator. The discriminator's value depends on registration order, which is not something
     * a test should assert, so this identifies the message by decoding the rest of the payload with
     * the packet's own reader and requiring it to consume the payload exactly. Every buffer is read
     * from a copy: the queued packets have not been encoded yet, and moving their reader index would
     * corrupt them.
     */
    private static int countTitansGripPackets(EmbeddedChannel channel, int entityId) {
        int found = 0;
        for (Object message : channel.outboundMessages()) {
            if (!(message instanceof ClientboundCustomPayloadPacket payload)) continue;
            if (!payload.getIdentifier().equals(RUNIC_CHANNEL)) continue;
            FriendlyByteBuf copy = new FriendlyByteBuf(payload.getData().copy());
            try {
                if (copy.readableBytes() < 2) continue;
                copy.readByte();
                TitansGripSyncCP decoded = new TitansGripSyncCP(copy);
                if (copy.readableBytes() == 0 && decoded.entityId() == entityId) found++;
            } catch (RuntimeException notThisMessage) {
                // Some other message on the same channel; a failed decode is how we know.
            } finally {
                copy.release();
            }
        }
        return found;
    }

    private static void assertCount(int expected, int actual, String what) {
        if (expected != actual) {
            throw new GameTestAssertException(what + " (expected " + expected
                    + " packets, saw " + actual + ")");
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
