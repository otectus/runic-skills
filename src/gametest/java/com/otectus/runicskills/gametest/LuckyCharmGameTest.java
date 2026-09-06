package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.effects.IncomingEffectPolicy;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lucky Charm shortens a harmful effect once, as it arrives (RS207-04).
 *
 * <p>It used to listen to {@code MobEffectEvent.Added} and call {@code addEffect} again with a
 * rebuilt instance. Three things followed. The event fires after vanilla has already merged the
 * effect, so every other listener saw the full duration first and then a second application of the
 * same effect arriving from this mod. The rebuild used a three-argument constructor, which drops
 * ambient state, icon and particle visibility, Forge's curative items and the factor data. And a
 * second {@code addEffect} from inside the event is exactly the shape that cascades if any other
 * mod reacts to it.
 *
 * <p>So the tests assert the observable consequences: one application, the right duration, and an
 * instance that still carries everything it arrived with.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class LuckyCharmGameTest {

    private static final String EMPTY = "empty";

    /** A 200-tick poison at 25% becomes 150 ticks, and is applied exactly once. */
    @GameTest(template = EMPTY)
    public static void aHarmfulEffectIsShortenedOnceOnArrival(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "lucky_charm_shorten");
        enablePerk(player, RegistryPerks.LUCKY_CHARM);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.luckyCharmPercent;
        AtomicInteger additions = new AtomicInteger();
        Object counter = new Object() {
            @SubscribeEvent
            public void onAdded(MobEffectEvent.Added event) {
                if (event.getEntity() == player) additions.incrementAndGet();
            }
        };
        MinecraftForge.EVENT_BUS.register(counter);
        try {
            config.luckyCharmPercent = 25;
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));

            MobEffectInstance applied = player.getEffect(MobEffects.POISON);
            if (applied == null || applied.getDuration() != 150) {
                throw new GameTestAssertException("a 200-tick poison at 25% Lucky Charm became "
                        + (applied == null ? "nothing" : applied.getDuration() + " ticks")
                        + "; expected 150");
            }
            if (additions.get() != 1) {
                throw new GameTestAssertException("the effect was applied " + additions.get()
                        + " times; shortening it must not be a second application (RS207-04)");
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
            config.luckyCharmPercent = previous;
            player.removeAllEffects();
        }
        helper.succeed();
    }

    /** Re-application shortens the new instance, not the already-shortened one again. */
    @GameTest(template = EMPTY)
    public static void reapplicationIsShortenedFromTheFullDuration(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "lucky_charm_reapply");
        enablePerk(player, RegistryPerks.LUCKY_CHARM);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.luckyCharmPercent;
        try {
            config.luckyCharmPercent = 25;
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));

            MobEffectInstance applied = player.getEffect(MobEffects.POISON);
            if (applied == null || applied.getDuration() != 300) {
                throw new GameTestAssertException("re-applying a 400-tick poison left "
                        + (applied == null ? "nothing" : applied.getDuration() + " ticks")
                        + "; expected 300, one 25% cut of the incoming duration");
            }
        } finally {
            config.luckyCharmPercent = previous;
            player.removeAllEffects();
        }
        helper.succeed();
    }

    /** An infinite effect is left exactly as it arrived: a share of "forever" means nothing. */
    @GameTest(template = EMPTY)
    public static void anInfiniteEffectIsUntouched(GameTestHelper helper) {
        MobEffectInstance infinite = new MobEffectInstance(MobEffects.POISON, -1, 0);
        MobEffectInstance shortened = IncomingEffectPolicy.shorten(infinite, 0.25);
        if (shortened != infinite) {
            throw new GameTestAssertException("an infinite effect was rebuilt by the shortening"
                    + " policy; it must be handed back untouched");
        }
        helper.succeed();
    }

    /**
     * Everything the instance carries survives the shortening.
     *
     * <p>This is the regression that matters most: the previous implementation rebuilt the instance
     * from a three-argument constructor, so a beacon's ambient effect arrived non-ambient, an
     * icon-hidden effect arrived with an icon, and Forge's curative list was replaced with the
     * default one. The NBT round-trip is what keeps all of it, including the hidden-effect chain
     * that vanilla uses to remember a weaker effect a stronger one displaced.
     */
    @GameTest(template = EMPTY)
    public static void shorteningKeepsEverythingElse(GameTestHelper helper) {
        MobEffectInstance hidden = new MobEffectInstance(MobEffects.POISON, 100, 0);
        MobEffectInstance original = new MobEffectInstance(MobEffects.POISON, 200, 1,
                true, false, false, hidden, java.util.Optional.empty());
        original.setCurativeItems(List.of(new ItemStack(Items.GOLDEN_APPLE)));

        MobEffectInstance shortened = IncomingEffectPolicy.shorten(original, 0.25);

        if (shortened.getDuration() != 150) {
            throw new GameTestAssertException("expected 150 ticks, got " + shortened.getDuration());
        }
        if (shortened.getAmplifier() != original.getAmplifier()
                || !shortened.isAmbient()
                || shortened.isVisible()
                || shortened.showIcon()) {
            throw new GameTestAssertException("shortening changed the amplifier or one of the"
                    + " ambient/particle/icon flags; only the duration may move");
        }
        if (shortened.getCurativeItems().size() != 1
                || shortened.getCurativeItems().get(0).getItem() != Items.GOLDEN_APPLE) {
            throw new GameTestAssertException("shortening lost the curative items, so what cures"
                    + " the effect silently changed");
        }
        helper.succeed();
    }

    /** With the perk off, the instance vanilla was handed is the instance vanilla gets. */
    @GameTest(template = EMPTY)
    public static void withoutThePerkNothingIsRebuilt(GameTestHelper helper) {
        MobEffectInstance incoming = new MobEffectInstance(MobEffects.POISON, 200, 0);
        if (IncomingEffectPolicy.shorten(incoming, 0.0) != incoming) {
            throw new GameTestAssertException("a zero cut rebuilt the instance; \"no perk behaves"
                    + " exactly like vanilla\" has to be true by construction");
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    private static void enablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        Perk perk = registered.get();
        SkillCapability capability = player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) {
            throw new GameTestAssertException("could not enable perk " + perk.getName()
                    + " for the test player; the fixture, not the perk, is broken");
        }
    }

    /**
     * A player with a working packet sink. Applying an effect makes the server send an update
     * packet, so a bare {@code new ServerPlayer(...)} would fault on its null connection before the
     * assertion ran -- see {@link MockPlayers}.
     */
    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        return MockPlayers.connectedServerPlayer(helper, name);
    }
}
