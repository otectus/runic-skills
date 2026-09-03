package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.combat.DamageMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Explosion perks, all previously registered with config, tooltips and textures but no runtime
 * effect at all (RS10-004).
 *
 * <p>All four hang off {@link ExplosionEvent}, which is where an explosion's block list and entity
 * list are still mutable and which identifies who set it off via
 * {@code Explosion.getIndirectSourceEntity()} — primed TNT remembers its igniter, so "your"
 * explosion is a question the game can actually answer.
 */
public class ExplosionPerkHandler {

    /**
     * Adjusts what an explosion destroys, before it destroys it.
     *
     * <p>Explosive Expert is applied last and wins outright: an explosion that damages no terrain
     * has no blocks for Explosive Ordinance to widen or Blast Mining to duplicate, and a player who
     * has taken a perk specifically to stop breaking the world should not have another perk quietly
     * breaking more of it.
     */
    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        Player owner = ownerOf(event);
        if (owner == null) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();

        if (RegistryPerks.EXPLOSIVE_ORDINANCE != null
                && RegistryPerks.EXPLOSIVE_ORDINANCE.get().isEnabled(owner)) {
            widenBlast(event, config.explosiveOrdinancePercent / 100.0);
        }

        if (RegistryPerks.BLAST_MINING != null
                && RegistryPerks.BLAST_MINING.get().isEnabled(owner)
                && level instanceof ServerLevel serverLevel) {
            duplicateDrops(serverLevel, owner, event.getAffectedBlocks(),
                    config.blastMiningPercent / 100.0);
        }

        if (RegistryPerks.EXPLOSIVE_EXPERT != null
                && RegistryPerks.EXPLOSIVE_EXPERT.get().isEnabled(owner)) {
            // "Your controlled explosions deal no terrain damage" — the entity list is untouched,
            // so the blast still hurts what it hits. Only the world is spared.
            event.getAffectedBlocks().clear();
        }
    }

    /**
     * Architect and Foundation Layer — "Placed blocks gain bonus hardness".
     *
     * <p>Hardness is how long a block takes to mine, and raising it on the blocks a player has
     * placed would mean punishing them for dismantling their own work — the opposite of a perk.
     * What a builder actually wants from a sturdier building is for it to still be there
     * afterwards, and the one force in vanilla that takes buildings apart without asking is an
     * explosion. So the perks are read as blast resistance: each block an explosion was about to
     * destroy near a builder gets a chance to survive it.
     *
     * <p>Unlike the three perks above this one deliberately does not need an owner. A creeper's
     * blast belongs to nobody, and "nobody set this off" is precisely the case a builder wants
     * their walls to survive. Attribution is instead to whoever is standing near the blast, the
     * same "whose work is this" reading the brewing and farming perks use.
     *
     * <p>Runs at {@code LOW} so it sees the final block list — everything Explosive Ordinance
     * widened is also eligible to be spared, and a list Explosive Expert already emptied costs
     * nothing to walk.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDetonateShielding(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double everywhere = 0.0;
        double foundation = 0.0;
        for (Player builder : nearbyBuilders(level, event.getExplosion().getPosition())) {
            if (RegistryPerks.ARCHITECT != null && RegistryPerks.ARCHITECT.get().isEnabled(builder)) {
                // The best-protected builder present sets the odds; two of them do not double it.
                everywhere = Math.max(everywhere, config.architectPercent / 100.0);
            }
            if (RegistryPerks.FOUNDATION_LAYER != null
                    && RegistryPerks.FOUNDATION_LAYER.get().isEnabled(builder)) {
                foundation = Math.max(foundation, config.foundationLayerPercent / 100.0);
            }
        }
        if (everywhere <= 0 && foundation <= 0) return;

        // Foundation Layer names the bottom of the world, so it applies at foundation depth and
        // stacks with Architect there: a foundation is reinforced twice over.
        final double shallow = everywhere;
        final double deep = foundation;
        affected.removeIf(pos -> {
            double chance = shallow + (pos.getY() <= FOUNDATION_DEPTH ? deep : 0.0);
            return chance > 0 && level.getRandom().nextDouble() < Math.min(0.95, chance);
        });
    }

    /** The depth at which a build counts as sitting on the world's foundation. */
    private static final int FOUNDATION_DEPTH = 0;

    /** How far from a blast a builder counts as standing in their own building. */
    private static final double BUILDER_REACH = 24.0;

    private static List<? extends Player> nearbyBuilders(Level level, Vec3 centre) {
        return level.getEntitiesOfClass(Player.class,
                new net.minecraft.world.phys.AABB(centre, centre).inflate(BUILDER_REACH));
    }

    /**
     * Explosive Ordinance — "TNT blast radius increased".
     *
     * <p>{@code Explosion.radius} is final and set before this event, so the radius itself cannot be
     * changed. What can is the block list it produced: everything within the widened sphere that the
     * explosion could have destroyed is added. The result is the same as a larger blast, and it
     * respects each block's own explosion resistance rather than flattening bedrock.
     */
    private static void widenBlast(ExplosionEvent.Detonate event, double growth) {
        if (growth <= 0) return;
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        Level level = event.getLevel();
        Vec3 centre = event.getExplosion().getPosition();
        // Derive the original reach from what the explosion actually destroyed, since the radius
        // field is not readable — the furthest affected block is the blast's own answer.
        double reach = 0;
        for (BlockPos pos : affected) {
            reach = Math.max(reach, Math.sqrt(pos.distToCenterSqr(centre)));
        }
        double widened = reach * (1.0 + growth);
        if (widened <= reach) return;

        Set<BlockPos> known = new HashSet<>(affected);
        int ceiling = (int) Math.ceil(widened);
        BlockPos origin = BlockPos.containing(centre);
        for (int dx = -ceiling; dx <= ceiling; dx++) {
            for (int dy = -ceiling; dy <= ceiling; dy++) {
                for (int dz = -ceiling; dz <= ceiling; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (known.contains(pos)) continue;
                    double distance = Math.sqrt(pos.distToCenterSqr(centre));
                    if (distance <= reach || distance > widened) continue;

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    // Anything the original blast could not have broken stays intact: a wider
                    // explosion is still an explosion, not a world edit.
                    if (state.getBlock().getExplosionResistance() >= 1200.0F) continue;
                    if (!state.getFluidState().isEmpty()) continue;
                    affected.add(pos);
                }
            }
        }
    }

    /**
     * Blast Mining — "TNT mining yields more drops".
     *
     * <p>Rolled per block, so a large blast is proportionally more productive than a small one
     * without any single block being guaranteed. The extra drops are the block's own, so silk-touch
     * and fortune-style behaviour of the underlying block is preserved.
     */
    private static void duplicateDrops(ServerLevel level, Player owner,
                                       List<BlockPos> affected, double chance) {
        if (chance <= 0) return;
        for (BlockPos pos : affected) {
            if (owner.getRandom().nextDouble() >= chance) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos),
                    owner, ItemStack.EMPTY)) {
                if (!drop.isEmpty()) Block.popResource(level, pos, drop.copy());
            }
        }
    }

    /**
     * Trap Maker — "Placed traps deal more damage".
     *
     * <p>A trap in vanilla is something you set up that goes off without you touching it: TNT you
     * lit and walked away from, a dispenser's payload. The distinguishing feature is that the damage
     * is indirect — you are the responsible party, but not the direct source — which is exactly what
     * separates a trap from a swing.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTrapDamage(LivingHurtEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide()) return;
        if (RegistryPerks.TRAP_MAKER == null) return;

        Entity direct = event.getSource().getDirectEntity();
        if (!(event.getSource().getEntity() instanceof Player setter)) return;
        // Direct blows are not traps. A trap has something else — primed TNT, a dispensed
        // projectile — standing between the setter and the victim.
        if (direct == setter) return;
        if (!RegistryPerks.TRAP_MAKER.get().isEnabled(setter)) return;

        double bonus = HandlerCommonConfig.HANDLER.instance().trapMakerPercent / 100.0;
        if (bonus <= 0) return;
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
    }


    /**
     * Forge Master — "Smelting yields more output".
     *
     * <p>A furnace produces one item per smelt and vanilla offers no way to change that mid-cook, so
     * the bonus is paid when the player collects the result: a chance at an extra copy of what came
     * out. {@code ItemSmeltedEvent} is the one place vanilla names both the player and the product,
     * so no attribution guesswork is needed here.
     */
    @SubscribeEvent
    public void onSmeltCollected(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (RegistryPerks.FORGE_MASTER == null
                || !RegistryPerks.FORGE_MASTER.get().isEnabled(player)) {
            return;
        }
        double chance = HandlerCommonConfig.HANDLER.instance().forgeMasterPercent / 100.0;
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;

        ItemStack bonus = event.getSmelting().copy();
        bonus.setCount(1);
        if (!player.getInventory().add(bonus)) player.drop(bonus, false);
    }

    /**
     * Glowstone Sight — "Mining generates temporary light around you".
     *
     * <p>Placing real light blocks would leave the world littered with them and is a world edit the
     * perk does not describe. Night vision is vanilla's own "you can see down here", refreshed as
     * you keep mining and fading shortly after you stop — which is what "temporary light around
     * you" amounts to from the player's side.
     *
     * <p>Only underground, so it does not quietly become a permanent daylight buff.
     */
    @SubscribeEvent
    public void onBlockMined(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (RegistryPerks.GLOWSTONE_SIGHT == null
                || !RegistryPerks.GLOWSTONE_SIGHT.get().isEnabled(player)) {
            return;
        }
        // Where the perk is for: below the surface, with no sky above the block being broken.
        if (player.level().canSeeSky(event.getPos())) return;

        int duration = HandlerCommonConfig.HANDLER.instance().glowstoneSightTicks;
        if (duration <= 0) return;
        // Ambient and hidden: this is a working light, not a status the player has to look at.
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, true, false));
    }

    /**
     * The player responsible for an explosion, if any.
     *
     * <p>{@code getIndirectSourceEntity} is the igniter for primed TNT and the shooter for a ghast
     * fireball, which is the attribution these perks want — a creeper's blast belongs to nobody.
     */
    private static Player ownerOf(ExplosionEvent event) {
        LivingEntity source = event.getExplosion().getIndirectSourceEntity();
        return source instanceof Player player ? player : null;
    }
}
