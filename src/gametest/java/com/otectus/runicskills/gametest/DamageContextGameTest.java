package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runic-emitted damage happens once, and does not cause more of itself (RS-205-08/09).
 *
 * <p>The 2.0.2 report behind this was a maxed multi-skill build attacking an Iron Golem: a crash
 * during the fight, and then a world that crashed on every load. What could be confirmed from the
 * source is the shape of the risk rather than the exception — several {@code hurt} calls emitted
 * from inside {@code LivingHurtEvent} listeners, each guarded, if at all, only against itself. So
 * what is asserted here is the shape: how many times the event is posted for the entity being hit,
 * and what {@link DamageContext} says each of those posts is. A counter is the only instrument
 * that catches "it fired twice"; an assertion on the final health cannot tell one 10-point hit
 * from two 5-point ones.
 *
 * <p>Both cases end by checking that the stack is empty. A frame left behind is the modern form of
 * the old {@code ThreadLocal<Boolean>} that stayed set after an exception, and it would disable
 * the outgoing perk table for the rest of the session rather than crash anything, which is exactly
 * the kind of failure that goes unreported for three releases.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class DamageContextGameTest {

    private static final String EMPTY = "empty";

    /** Limit Breaker's bonus for this test: distinctive, and far from any vanilla damage figure. */
    private static final float LIMIT_BREAKER_DAMAGE = 5.0F;

    /** The primary blow, dealt by hand so the assertion does not depend on attack-cooldown timing. */
    private static final float PRIMARY_DAMAGE = 3.0F;

    /** A container synchronizer that sends nothing, for a player with nowhere to send it. */
    private static final ContainerSynchronizer SILENT = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items,
                                    ItemStack carried, int[] data) {
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {
        }

        @Override
        public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
        }
    };

    // -- Limit Breaker -------------------------------------------------------------------------

    /**
     * A forced Limit Breaker proc against an Iron Golem lands exactly one extra blow, of exactly
     * the configured size, and that blow is a {@code LIMIT_BREAKER} hit rather than a second
     * primary one that the outgoing perk table would scale again.
     */
    @GameTest(template = EMPTY)
    public static void limitBreakerAddsExactlyOneBlow(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "damage_context_limit_breaker", false);
        LivingEntity golem = spawn(helper, EntityType.IRON_GOLEM, player);
        HurtLog log = new HurtLog(golem);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previousProbability = config.limitBreakerProbability;
        float previousAmplifier = config.limitBreakerAmplifier;
        float healthBefore;
        try {
            // 1-in-1 so the proc is certain: this test is about what one proc does, not about
            // whether it happens.
            config.limitBreakerProbability = 1;
            config.limitBreakerAmplifier = LIMIT_BREAKER_DAMAGE;
            RegistryPerks.refreshFromConfig();
            enablePerk(player, RegistryPerks.LIMIT_BREAKER);

            log.register();
            healthBefore = golem.getHealth();
            // The real trigger: Player#attack posts this event before it deals its own damage.
            MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, golem));
            // The swing itself. The bonus blow above started the golem's damage cooldown, so it is
            // cleared here — vanilla would have dealt the primary first, and this test is not
            // about the order the two arrive in.
            golem.invulnerableTime = 0;
            golem.hurt(golem.damageSources().playerAttack(player), PRIMARY_DAMAGE);
        } finally {
            log.unregister();
            config.limitBreakerProbability = previousProbability;
            config.limitBreakerAmplifier = previousAmplifier;
            RegistryPerks.refreshFromConfig();
        }

        List<Hurt> hurts = log.hurts();
        if (hurts.size() != 2) {
            throw new GameTestAssertException("one swing with Limit Breaker should hurt the golem "
                    + "twice (primary + bonus), but LivingHurtEvent was posted " + hurts.size()
                    + " times: " + log.describe());
        }
        long bonusHits = hurts.stream()
                .filter(hurt -> hurt.origin == DamageContext.Origin.LIMIT_BREAKER).count();
        if (bonusHits != 1) {
            throw new GameTestAssertException("expected exactly one LIMIT_BREAKER hit, got "
                    + bonusHits + ": " + log.describe());
        }
        long primaryHits = hurts.stream()
                .filter(hurt -> hurt.origin == DamageContext.Origin.PRIMARY).count();
        if (primaryHits != 1) {
            throw new GameTestAssertException("expected exactly one PRIMARY hit, got "
                    + primaryHits + ": " + log.describe());
        }
        for (Hurt hurt : hurts) {
            if (hurt.origin == DamageContext.Origin.LIMIT_BREAKER && hurt.amount != LIMIT_BREAKER_DAMAGE) {
                throw new GameTestAssertException("the Limit Breaker blow arrived at "
                        + hurt.amount + " damage; the configured bonus is " + LIMIT_BREAKER_DAMAGE
                        + ", and a secondary hit must not be scaled by the outgoing perk table");
            }
        }

        float lost = healthBefore - golem.getHealth();
        float expected = LIMIT_BREAKER_DAMAGE + PRIMARY_DAMAGE;
        if (Math.abs(lost - expected) > 0.01F) {
            throw new GameTestAssertException("the golem lost " + lost + " health; one primary blow "
                    + "plus one Limit Breaker bonus is " + expected + " (" + log.describe() + ")");
        }
        assertStackEmpty();
        helper.succeed();
    }

    // -- Bulwark -------------------------------------------------------------------------------

    /**
     * Bulwark reflecting the whole of a hit does not reflect its own reflect. The perk had no
     * re-entry guard of any kind before 2.0.5; at a high enough percentage each bounce fed the
     * next, and every bounce also re-ran the outgoing perk table on the attacker.
     */
    @GameTest(template = EMPTY)
    public static void bulwarkReflectsOnceAndNeverItself(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "damage_context_bulwark", true);
        LivingEntity zombie = spawn(helper, EntityType.ZOMBIE, player);
        HurtLog zombieLog = new HurtLog(zombie);
        HurtLog playerLog = new HurtLog(player);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.bulwarkPercent;
        try {
            config.bulwarkPercent = 100;
            RegistryPerks.refreshFromConfig();
            enablePerk(player, RegistryPerks.BULWARK);

            zombieLog.register();
            playerLog.register();
            // The event is posted rather than the hit dealt, for the same reason the Limit Breaker
            // case posts AttackEntityEvent: a ServerPlayer built outside the login flow carries 60
            // ticks of spawn invulnerability, and ServerPlayer#hurt refuses every source while it
            // lasts — so a real hurt() call would never reach the handler under test, and running
            // the player through sixty ticks to burn it down would drag in the whole packet path.
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player,
                    player.damageSources().mobAttack(zombie), 6.0F));
        } finally {
            zombieLog.unregister();
            playerLog.unregister();
            config.bulwarkPercent = previous;
            RegistryPerks.refreshFromConfig();
        }

        if (zombieLog.hurts().size() != 1) {
            throw new GameTestAssertException("a blocked hit should reflect onto the attacker "
                    + "exactly once; the zombie was hurt " + zombieLog.hurts().size()
                    + " times: " + zombieLog.describe());
        }
        if (zombieLog.hurts().get(0).origin != DamageContext.Origin.BULWARK_REFLECT) {
            throw new GameTestAssertException("the reflect reached the zombie as "
                    + zombieLog.hurts().get(0).origin + "; it must be BULWARK_REFLECT so the "
                    + "outgoing perk table leaves it alone");
        }
        if (playerLog.hurts().size() != 1) {
            throw new GameTestAssertException("the player should be hurt once, by the zombie; a "
                    + "reflect of the reflect would hurt them again. Got "
                    + playerLog.hurts().size() + ": " + playerLog.describe());
        }
        assertStackEmpty();
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    /** One {@code LivingHurtEvent} post, with what the context said about it at the time. */
    private record Hurt(float amount, DamageContext.Origin origin, int depth) {
    }

    /**
     * Counts the hurt events one entity receives while it is registered.
     *
     * <p>{@code LOWEST} so the origin recorded is the one every product handler has already acted
     * on, and so the amount is the one that will actually be applied.
     */
    private static final class HurtLog {

        private final Entity subject;
        private final List<Hurt> hurts = new ArrayList<>();
        private boolean registered;

        HurtLog(Entity subject) {
            this.subject = subject;
        }

        void register() {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }

        /** Must run in a {@code finally}: a listener left on the bus would poison later tests. */
        void unregister() {
            if (!registered) return;
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onHurt(LivingHurtEvent event) {
            if (event.getEntity() != subject) return;
            hurts.add(new Hurt(event.getAmount(), DamageContext.current().origin(),
                    DamageContext.depth()));
        }

        List<Hurt> hurts() {
            return hurts;
        }

        String describe() {
            return hurts.toString();
        }
    }

    private static void assertStackEmpty() {
        if (DamageContext.depth() != 0) {
            throw new GameTestAssertException("a damage frame was left on the stack (depth "
                    + DamageContext.depth() + ", " + DamageContext.describe()
                    + "); every scope must pop its own frame, exception or not");
        }
    }

    /** Puts a living entity of {@code type} next to the player, inside the test's own structure. */
    private static <T extends LivingEntity> T spawn(GameTestHelper helper, EntityType<T> type,
                                                    ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        T entity = type.create(level);
        if (entity == null) {
            throw new GameTestAssertException("could not create a " + type.getDescriptionId());
        }
        Vec3 at = player.position().add(1.0D, 0.0D, 0.0D);
        entity.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(entity)) {
            throw new GameTestAssertException("could not add a " + type.getDescriptionId()
                    + " to the test level");
        }
        return entity;
    }

    /**
     * A server player standing in the test structure.
     *
     * <p>{@code blocking} returns a player that reports itself as blocking. Bulwark requires it,
     * and the vanilla route there — hold a shield, start using it, then tick for six ticks — would
     * put a connectionless player through the whole {@code ServerPlayer#tick} path to establish
     * one boolean the perk reads.
     */
    private static ServerPlayer newPlayer(GameTestHelper helper, String name, boolean blocking) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        ServerPlayer player = blocking
                ? new ServerPlayer(server, level, profile) {
                    @Override
                    public boolean isBlocking() {
                        return true;
                    }
                }
                : new ServerPlayer(server, level, profile);
        // A player built outside the login flow has no connection; nothing here may broadcast.
        player.containerMenu.setSynchronizer(SILENT);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** Gives the player the perk: its skill at the required level, and one rank taken. */
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
}
