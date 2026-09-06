package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.BlockBreakCommittedEvent;
import com.otectus.runicskills.common.actions.ProjectileSnapshot;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The two native actions that happen outside every vanilla seam this mod already has: an area
 * harvest, and a ranged launch.
 *
 * <p><b>Area harvest (H8, C05).</b> A Tinkers' hammer does not break its extra blocks through
 * {@code ServerPlayerGameMode.destroyBlock} — it cancels the vanilla break entirely and runs
 * {@code ToolHarvestLogic}, which walks its own block list. So {@code MixServerPlayerGameMode}'s
 * committed-break event, which Treasure Hunter and anything else that pays for a break listens to,
 * simply never fires for any of the nine blocks. This bridge is where those breaks are published
 * instead, from the one place in {@code ToolHarvestLogic} that is past both the protection check
 * and {@code Block.playerDestroy} — the same condition vanilla drops loot under.
 *
 * <p>Protection stays entirely native: each child already runs {@code ForgeHooks.onBlockBreakEvent}
 * with a real {@code ServerPlayer}, so a claim mod refuses the child exactly as it refuses a
 * hand-mined block, and a refused child returns before reaching the seam below. Runic therefore
 * cannot generate a drop, an experience point or a combo tick for a block a protection mod said no
 * to — not because it checks, but because it is never told.
 *
 * <p><b>Ranged launch (H9, C03/C04).</b> The snapshot is written from
 * {@link EntityJoinLevelEvent} while a {@link ActionOrigin#RANGED} scope opened by the launcher's
 * own release method is on the stack. Two seams rather than one because each answers half the
 * question: the mixin knows <em>who fired what</em>, the event knows <em>which entities that
 * produced</em>. A multishot spawning three arrows spawns them inside one scope, so all three take
 * the same root action id, which is §9.2's "each retains the same root ID" without counting
 * projectiles. A projectile that appears outside any launch — a dispenser, a command, another mod —
 * is never given a snapshot and is therefore ineligible for everything downstream.
 *
 * <p>A registered modifier hook was the alternative and was rejected: Tinkers' would only run it on
 * a tool that carries the modifier, so it would either miss unmodified bows or require this mod to
 * attach a hidden modifier to every launcher a player touches.
 */
public final class TConstructCombatBridge {

    /**
     * What is being fired on this thread, for the entity-join handler to read.
     *
     * <p>Thread-local rather than a field, for the same reason {@code RunicActionContext} is: two
     * players can be in the middle of a release on two threads on a server that dispatches that
     * way, and the launcher is only meaningful for the duration of one of them. Cleared in the
     * {@code finally} half of the injection pair that set it.
     */
    private static final ThreadLocal<Launch> LAUNCH = new ThreadLocal<>();

    /** The launcher and hand of one release in progress. */
    private record Launch(ItemStack launcher, InteractionHand hand) {
    }

    /**
     * Opens a ranged action for {@code shooter} and remembers what they are firing.
     *
     * @return whether a frame was pushed; the caller must pass it back to {@link #endLaunch}
     */
    public static boolean beginLaunch(LivingEntity shooter, ItemStack launcher, InteractionHand hand) {
        if (!(shooter instanceof ServerPlayer player) || shooter instanceof FakePlayer) return false;
        boolean opened = RunicActionContext.enter(ActionOrigin.RANGED, player.getUUID());
        if (opened) LAUNCH.set(new Launch(launcher == null ? ItemStack.EMPTY : launcher, hand));
        return opened;
    }

    /** Closes the ranged action {@link #beginLaunch} opened, if it opened one. */
    public static void endLaunch(boolean opened) {
        if (!opened) return;
        LAUNCH.remove();
        RunicActionContext.exit();
    }

    /**
     * Snapshots a projectile that was created inside a native launch.
     *
     * <p>{@code EntityJoinLevelEvent} fires synchronously from {@code ServerLevel.addFreshEntity},
     * which the launcher calls while its release method — and therefore the scope — is still on the
     * stack. That is what makes the actor here the actor of this launch rather than of whatever
     * else the server did this tick.
     */
    @SubscribeEvent
    public void onProjectileSpawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Projectile projectile)) return;
        if (!TConstructCompatibilityStatus.current().supports(Capability.PROJECTILES)) return;

        RunicActionContext.Frame frame = RunicActionContext.current();
        if (frame.origin() != ActionOrigin.RANGED || frame.actor() == null) return;
        // The shooter Tinkers' set on the entity is the authority on ownership; an open scope only
        // says a launch is happening, not that this entity belongs to it.
        if (!(projectile.getOwner() instanceof ServerPlayer owner)
                || !owner.getUUID().equals(frame.actor())) {
            return;
        }

        Launch launch = LAUNCH.get();
        ItemStack launcher = launch == null ? ItemStack.EMPTY : launch.launcher();
        InteractionHand hand = launch == null ? InteractionHand.MAIN_HAND : launch.hand();
        // The scalar the snapshot was built to carry: Resonant Return's prepared launch, decided
        // here and never re-read from the player afterwards, so switching or dropping the launcher
        // in flight cannot change what this projectile is worth (C03). Claimed once per launch, so
        // a multishot cannot turn one prepared throw into several enhanced hits.
        double bonus = TConstructPowerDispatcher.launchBonus(owner);
        ProjectileSnapshot.write(projectile, owner.getUUID(), frame.rootId(), hand, launcher, bonus);
    }

    /**
     * Records a genuine native return of a thrown tool to the player who threw it.
     *
     * <p>C04's distinction, made where it is actually visible. A thrown tool with the returning
     * behaviour flies back under its own power, and only during that flight does the entity have
     * physics disabled; a tool lying on the ground that a player walks over reaches the very same
     * pickup method with physics on. Nothing here infers a return from a pickup, and nothing infers
     * it from an item appearing in an inventory — a command that puts the tool in a backpack is
     * neither a pickup nor a return, and produces no mark because it never reaches this seam.
     */
    public static void onProjectileReturned(Entity projectile, Player receiver) {
        if (projectile == null || !(receiver instanceof ServerPlayer player)) return;
        if (!TConstructCompatibilityStatus.current().supports(Capability.PROJECTILES)) return;
        ProjectileSnapshot.markReturned(projectile, player.getUUID());
        // The one perk and the one Power that turn on a genuine return rather than on a pickup,
        // both armed at the same seam that tells the two apart.
        TConstructPerkHandler.onThrownToolReturned(player);
        TConstructPowerDispatcher.onProjectileReturned(player);
    }

    /**
     * Opens one block of a native area harvest as its own action, sharing the swing's root id.
     *
     * @return whether a frame was pushed; the caller must pass it back to {@link #endAoeChild}
     */
    public static boolean beginAoeChild(ServerPlayer player) {
        if (player == null) return false;
        return RunicActionContext.enter(ActionOrigin.NATIVE_AOE_CHILD, player.getUUID());
    }

    /** Closes the child action {@link #beginAoeChild} opened, if it opened one. */
    public static void endAoeChild(boolean opened) {
        if (opened) RunicActionContext.exit();
    }

    /**
     * Publishes a block that a native harvest actually broke and dropped.
     *
     * <p>Posted from past {@code Block.playerDestroy}, so it carries the same promise as the vanilla
     * seam it mirrors: this block is gone and its loot is out. Whether it was the block the player
     * aimed at or one of the extras is readable from {@link RunicActionContext#current()}, and every
     * one of them shares a root action id, so a listener that wants to pay once per swing can.
     */
    public static void onNativeBlockBroken(ServerLevel level, BlockPos pos, BlockState state,
                                           ServerPlayer player, ItemStack tool) {
        if (level == null || pos == null || state == null || player == null) return;
        if (!TConstructCompatibilityStatus.current().supports(Capability.HARVEST_AOE)) return;
        MinecraftForge.EVENT_BUS.post(new BlockBreakCommittedEvent(
                level, pos.immutable(), state, player, tool == null ? ItemStack.EMPTY : tool.copy()));
    }
}
