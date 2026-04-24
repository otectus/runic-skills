package com.otectus.runicskills.integration;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import vazkii.botania.api.mana.ManaDiscountEvent;
import vazkii.botania.api.mana.ManaItemsEvent;
import vazkii.botania.api.mana.ManaProficiencyEvent;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Botania hard-integration entry point. Instantiated only when Botania is loaded
 * (via {@code RunicSkills#tryLoadIntegration}); every {@code vazkii.botania.*} import in
 * this mod lives here or in {@link BotaniaCompat}.
 *
 * <p>Soft effects (those that do not need a Botania class to fire) are kept here anyway
 * for code-organization reasons: the whole class only executes when Botania is loaded,
 * which matches the "Botania-flavored perks only activate when Botania is installed"
 * rule from the integration plan. The per-perk {@code RegistryObject != null} guards are
 * still present for defense in depth.
 *
 * <p>Still-unimplemented perks (see the integration plan): Petal-Reader (tooltip inject),
 * Agricultor's Eye, Sparkle-Sense, Manaseer's Lens, Still Listener, Loot-Hunter's
 * Intuition, Cartographer-Prospector, Dowser's Twig, Lazy Swap, Mirror's Read,
 * Corporea Query, Elven Knowledge, Gaia's Witness, Oracle of the Nine Runes, Relic
 * Attunement, Flügel's Grace, Manastorm, Verdant Pulse, Lens Velocity, Lens Potency,
 * Band of Aura, Pixie Affinity, Unbound Step. These need client-side rendering,
 * keybinds, custom capabilities, or command dispatch; they are intentionally deferred.
 */
public class BotaniaIntegration {

    private static final int POOL_SCAN_INTERVAL_TICKS = 10;
    private static final int MAGNETITE_TICK_INTERVAL = 5;

    private static final UUID BOTANIA_STONE_ROOTED_UUID   = UUID.fromString("f1c9a0bf-7af7-4c62-9b3a-4a9f8c7b1e01");
    private static final UUID BOTANIA_TERRASTEEL_HP_UUID  = UUID.fromString("f1c9a0bf-7af7-4c62-9b3a-4a9f8c7b1e02");
    private static final UUID BOTANIA_TERRASTEEL_TGH_UUID = UUID.fromString("f1c9a0bf-7af7-4c62-9b3a-4a9f8c7b1e03");
    private static final UUID BOTANIA_CROWN_ENT_UUID      = UUID.fromString("f1c9a0bf-7af7-4c62-9b3a-4a9f8c7b1e04");
    private static final UUID BOTANIA_CROWN_BLK_UUID      = UUID.fromString("f1c9a0bf-7af7-4c62-9b3a-4a9f8c7b1e05");
    private static final UUID BOTANIA_FAR_REACH_ENT_UUID  = UUID.fromString("f1c9a0bf-7af7-4c62-9b3a-4a9f8c7b1e06");
    private static final UUID BOTANIA_FAR_REACH_BLK_UUID  = UUID.fromString("f1c9a0bf-7af7-4c62-9b3a-4a9f8c7b1e07");

    private static final Random RNG = new Random();

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("botania");
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  HARD INTEGRATION — Botania-specific events
    // ════════════════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onManaProficiency(ManaProficiencyEvent event) {
        Player player = event.getEntityPlayer();
        if (player == null || player.isCreative()) return;
        if (RegistryPerks.RUNE_OF_MANA_RESONANCE != null
                && RegistryPerks.RUNE_OF_MANA_RESONANCE.get().isEnabled(player)) {
            event.setProficient(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onManaDiscount(ManaDiscountEvent event) {
        Player player = event.getEntityPlayer();
        if (player == null || player.isCreative()) return;
        if (RegistryPerks.RUNE_OF_WATER_TIDEWOVEN != null
                && RegistryPerks.RUNE_OF_WATER_TIDEWOVEN.get().isEnabled(player)
                && player.isInWaterOrRain()) {
            float discount = HandlerCommonConfig.HANDLER.instance().botaniaTidewovenDiscount;
            event.setDiscount(event.getDiscount() + discount);
        }
    }

    @SubscribeEvent
    public void onManaItems(ManaItemsEvent event) {
        // TODO: Band of Aura: Passive Channel — backed by a virtual mana item. Requires
        // a per-player accessory inventory or a curios slot to attach to.
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  PER-TICK — Inner Wellspring, Magnetite, static attribute modifiers
    // ════════════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        // Inner Wellspring — half-second throttle, Botania API read
        if (player.tickCount % POOL_SCAN_INTERVAL_TICKS == 0
                && RegistryPerks.INNER_WELLSPRING != null
                && RegistryPerks.INNER_WELLSPRING.get().isEnabled(player)) {
            int perTick = (int) RegistryPerks.INNER_WELLSPRING.get().getValue()[0];
            if (perTick > 0) {
                int drained = BotaniaCompat.drainNearbyPool(
                        player.level(), player.blockPosition(), perTick);
                if (drained > 0) BotaniaCompat.chargePlayerMana(player, drained);
            }
        }

        // Magnetite — 5-tick throttle, pull nearby items inward (vanilla-side behavior)
        if (player.tickCount % MAGNETITE_TICK_INTERVAL == 0
                && RegistryPerks.MAGNETITE != null
                && RegistryPerks.MAGNETITE.get().isEnabled(player)
                && !player.isShiftKeyDown()) {
            int radius = (int) RegistryPerks.MAGNETITE.get().getValue()[0];
            pullItemsToward(player, radius);
        }

        // Static attribute modifiers — Stone-Rooted, Terrasteel Ascension, reach perks
        applyStoneRooted(player);
        applyTerrasteelAscension(player);
        applyCrownOfReach(player);
        applyFarReach(player);
    }

    private static void pullItemsToward(Player player, int radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, box);
        Vec3 center = player.position().add(0, 0.5, 0);
        for (ItemEntity e : items) {
            if (!e.isAlive() || e.hasPickUpDelay()) continue;
            Vec3 dir = center.subtract(e.position());
            double dist = dir.length();
            if (dist < 0.5) continue;
            Vec3 pull = dir.normalize().scale(Math.min(0.35, dist * 0.1));
            e.setDeltaMovement(e.getDeltaMovement().add(pull));
        }
    }

    private static void applyStoneRooted(ServerPlayer player) {
        if (RegistryPerks.RUNE_OF_EARTH_STONE_ROOTED == null) return;
        boolean enabled = RegistryPerks.RUNE_OF_EARTH_STONE_ROOTED.get().isEnabled(player)
                && isStandingOnStoneFamily(player);
        float amount = (float) RegistryPerks.RUNE_OF_EARTH_STONE_ROOTED.get().getValue()[0];
        new RegistryAttributes.RegisterAttribute(player, Attributes.ARMOR, amount, BOTANIA_STONE_ROOTED_UUID)
                .amplifyAttribute(enabled);
    }

    private static void applyTerrasteelAscension(ServerPlayer player) {
        if (RegistryPerks.TERRASTEEL_ASCENSION == null) return;
        boolean enabled = RegistryPerks.TERRASTEEL_ASCENSION.get().isEnabled(player);
        float hp = (float) RegistryPerks.TERRASTEEL_ASCENSION.get().getValue()[0];
        new RegistryAttributes.RegisterAttribute(player, Attributes.MAX_HEALTH, hp, BOTANIA_TERRASTEEL_HP_UUID)
                .amplifyAttribute(enabled);
        new RegistryAttributes.RegisterAttribute(player, Attributes.ARMOR_TOUGHNESS, 1.0f, BOTANIA_TERRASTEEL_TGH_UUID)
                .amplifyAttribute(enabled);
    }

    private static void applyCrownOfReach(ServerPlayer player) {
        if (RegistryPerks.CROWN_OF_REACH == null) return;
        boolean enabled = RegistryPerks.CROWN_OF_REACH.get().isEnabled(player);
        float bonus = (float) RegistryPerks.CROWN_OF_REACH.get().getValue()[0];
        new RegistryAttributes.RegisterAttribute(player, ForgeMod.ENTITY_REACH.get(), bonus, BOTANIA_CROWN_ENT_UUID)
                .amplifyAttribute(enabled);
        new RegistryAttributes.RegisterAttribute(player, ForgeMod.BLOCK_REACH.get(), bonus, BOTANIA_CROWN_BLK_UUID)
                .amplifyAttribute(enabled);
    }

    private static void applyFarReach(ServerPlayer player) {
        if (RegistryPerks.BOTANIA_FAR_REACH == null) return;
        boolean enabled = RegistryPerks.BOTANIA_FAR_REACH.get().isEnabled(player);
        float bonus = (float) RegistryPerks.BOTANIA_FAR_REACH.get().getValue()[0];
        new RegistryAttributes.RegisterAttribute(player, ForgeMod.ENTITY_REACH.get(), bonus, BOTANIA_FAR_REACH_ENT_UUID)
                .amplifyAttribute(enabled);
        new RegistryAttributes.RegisterAttribute(player, ForgeMod.BLOCK_REACH.get(), bonus, BOTANIA_FAR_REACH_BLK_UUID)
                .amplifyAttribute(enabled);
    }

    private static boolean isStandingOnStoneFamily(Player player) {
        BlockPos below = player.blockPosition().below();
        BlockState state = player.level().getBlockState(below);
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(BlockTags.STONE_BRICKS);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  COMBAT — attacker-side (player dealing damage)
    // ════════════════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurtAttacker(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof Player player) || player.isCreative()) return;
        LivingEntity victim = event.getEntity();
        if (victim == null || victim == player) return;

        // Rune of Fire: Emberheart — flat fire damage on attacks
        if (RegistryPerks.RUNE_OF_FIRE_EMBERHEART != null
                && RegistryPerks.RUNE_OF_FIRE_EMBERHEART.get().isEnabled(player)) {
            float extra = (float) RegistryPerks.RUNE_OF_FIRE_EMBERHEART.get().getValue()[0];
            if (extra > 0.0f) event.setAmount(event.getAmount() + extra);
            victim.setSecondsOnFire(Math.max(victim.getRemainingFireTicks() / 20, 2));
        }

        // Summer: Solar Conduit — +damage multiplier during day
        if (RegistryPerks.SOLAR_CONDUIT != null
                && RegistryPerks.SOLAR_CONDUIT.get().isEnabled(player)
                && player.level().isDay()
                && player.level().canSeeSky(player.blockPosition())) {
            int pct = (int) RegistryPerks.SOLAR_CONDUIT.get().getValue()[0];
            event.setAmount(event.getAmount() * (1.0f + pct / 100.0f));
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  COMBAT — target-side (player taking damage)
    // ════════════════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurtTarget(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;
        DamageSource source = event.getSource();

        // Rune of Air: Featherstep — reduced fall damage
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                && RegistryPerks.RUNE_OF_AIR_FEATHERSTEP != null
                && RegistryPerks.RUNE_OF_AIR_FEATHERSTEP.get().isEnabled(player)) {
            float mult = (float) RegistryPerks.RUNE_OF_AIR_FEATHERSTEP.get().getValue()[0];
            event.setAmount(event.getAmount() * mult);
        }

        // Winter: Frostbound — cold retaliation on melee attackers
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player
                && source.getDirectEntity() == source.getEntity() // melee (not projectile)
                && RegistryPerks.FROSTBOUND != null
                && RegistryPerks.FROSTBOUND.get().isEnabled(player)) {
            float coldDmg = (float) RegistryPerks.FROSTBOUND.get().getValue()[0];
            int slowSeconds = (int) RegistryPerks.FROSTBOUND.get().getValue()[1];
            if (coldDmg > 0) attacker.hurt(player.damageSources().freeze(), coldDmg);
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowSeconds * 20, 0));
        }

        // Envy: Mirrored Wrath — reflect damage percentage
        if (source.getEntity() instanceof LivingEntity attacker2 && attacker2 != player
                && RegistryPerks.ENVY_MIRRORED_WRATH != null
                && RegistryPerks.ENVY_MIRRORED_WRATH.get().isEnabled(player)) {
            int pct = HandlerCommonConfig.HANDLER.instance().botaniaMirroredWrathPercent;
            float reflected = event.getAmount() * pct / 100.0f;
            if (reflected > 0.0f) attacker2.hurt(player.damageSources().thorns(player), reflected);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  CRITICAL HIT — Wrath: Thundercall
    // ════════════════════════════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onCriticalHit(CriticalHitEvent event) {
        if (!event.isVanillaCritical()) return;
        Player player = event.getEntity();
        if (player == null || player.isCreative()) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (RegistryPerks.THUNDERCALL == null
                || !RegistryPerks.THUNDERCALL.get().isEnabled(player)) return;

        int pct = (int) RegistryPerks.THUNDERCALL.get().getValue()[0];
        if (RNG.nextInt(100) >= pct) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        // Find a chain target nearby (not the primary victim) to mirror Thundercaller flavor.
        AABB box = target.getBoundingBox().inflate(6.0);
        LivingEntity chain = null;
        for (LivingEntity candidate : serverLevel.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == target || candidate == player) continue;
            if (!candidate.isAlive() || candidate instanceof Player) continue;
            chain = candidate;
            break;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (bolt != null) {
            Entity strikeAt = chain != null ? chain : target;
            bolt.moveTo(Vec3.atCenterOf(strikeAt.blockPosition()));
            bolt.setVisualOnly(false);
            serverLevel.addFreshEntity(bolt);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  LIVING DEATH — Autumn: Harvest Tithe (bonus gem/nugget drop)
    // ════════════════════════════════════════════════════════════════════════════

    private static final ItemStack[] HARVEST_TITHE_DROPS = new ItemStack[] {
            new ItemStack(Items.IRON_NUGGET),
            new ItemStack(Items.GOLD_NUGGET),
            new ItemStack(Items.AMETHYST_SHARD),
            new ItemStack(Items.QUARTZ),
            new ItemStack(Items.EMERALD),
            new ItemStack(Items.LAPIS_LAZULI),
    };

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.isCreative()) return;
        if (RegistryPerks.HARVEST_TITHE == null
                || !RegistryPerks.HARVEST_TITHE.get().isEnabled(player)) return;

        int pct = (int) RegistryPerks.HARVEST_TITHE.get().getValue()[0];
        if (RNG.nextInt(100) >= pct) return;

        LivingEntity victim = event.getEntity();
        ItemStack drop = HARVEST_TITHE_DROPS[RNG.nextInt(HARVEST_TITHE_DROPS.length)].copy();
        ItemEntity ent = new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(), drop);
        victim.level().addFreshEntity(ent);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  BLOCK BREAK — Green Thumb, Livingbark Student
    // ════════════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isCreative()) return;
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;
        BlockState state = event.getState();

        if (RegistryPerks.BOTANIA_GREEN_THUMB != null
                && RegistryPerks.BOTANIA_GREEN_THUMB.get().isEnabled(player)
                && (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.TALL_FLOWERS))) {
            int oneInN = (int) RegistryPerks.BOTANIA_GREEN_THUMB.get().getValue()[0];
            if (RNG.nextInt(Math.max(1, oneInN)) == 0) {
                ItemStack drop = state.getBlock().asItem().getDefaultInstance();
                if (!drop.isEmpty()) {
                    BlockPos pos = event.getPos();
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
                }
            }
        }

        if (RegistryPerks.BOTANIA_LIVINGBARK_STUDENT != null
                && RegistryPerks.BOTANIA_LIVINGBARK_STUDENT.get().isEnabled(player)
                && state.is(BlockTags.LOGS)) {
            int pct = (int) RegistryPerks.BOTANIA_LIVINGBARK_STUDENT.get().getValue()[0];
            if (RNG.nextInt(100) < pct) {
                // Give an oak sapling as the safe default; the tree-type-aware version is a polish task.
                BlockPos pos = event.getPos();
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(Items.OAK_SAPLING)));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  ITEM USE FINISH — Gluttony: Cake Combustion
    //
    //  Forager's Palate (XP-gain buff on eat) would live here too; it needs a
    //  short-lived capability flag to hook into XpPickupEvent, which is a small
    //  follow-up task.
    // ════════════════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;
        if (!event.getItem().isEdible()) return;

        if (RegistryPerks.CAKE_COMBUSTION != null
                && RegistryPerks.CAKE_COMBUSTION.get().isEnabled(player)
                && player.getFoodData().getFoodLevel() >= 20) {
            int seconds = (int) RegistryPerks.CAKE_COMBUSTION.get().getValue()[0];
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, seconds * 20, 0));
            player.level().playSound(null, player, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }
}
