package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Magic-tree and scholarly perks, all previously registered with config, tooltips and textures but
 * no runtime effect at all (RS10-004).
 *
 * <p>Several were written against a magic mod's vocabulary — "spell damage", "elemental" — and are
 * reinterpreted here against what vanilla actually has, so they work in every pack rather than
 * naming systems a player may never see.
 */
public class ArcanePerkHandler {

    /** How many of each mob type a player has killed, for Monster Compendium. */
    private static final Map<UUID, Map<ResourceLocation, Integer>> STUDIED = new ConcurrentHashMap<>();

    /** Bounds the per-player study map: vanilla has well under this many hostile types. */
    private static final int MAX_STUDIED_TYPES = 512;

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STUDIED.remove(event.getEntity().getUUID());
    }

    // ── Outgoing damage ─────────────────────────────────────────────────────────────────────

    /**
     * The damage-side magic perks.
     *
     * <p>{@code LOW} priority so these percentages land after other mods' flat adjustments and
     * before armour, which is where a multiplier belongs.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOutgoingDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        LivingEntity target = event.getEntity();
        if (target == null) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        DamageSource source = event.getSource();
        double bonus = 0.0;

        // Spell Amplifier — "all spell damage". Vanilla's word for spell damage is magic damage.
        if (enabled(RegistryPerks.SPELL_AMPLIFIER, player)
                && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) {
            bonus += config.spellAmplifierPercent / 100.0;
        }

        // Elemental Master — "elemental spell damage". Fire, ice and lightning are the elements
        // vanilla models as damage types.
        if (enabled(RegistryPerks.ELEMENTAL_MASTER, player) && isElemental(source)) {
            bonus += config.elementalMasterPercent / 100.0;
        }

        // Void Magic — "ender-based damage". The End's own creatures are what that means here.
        if (enabled(RegistryPerks.VOID_MAGIC, player) && isEnderKin(target)) {
            bonus += config.voidMagicPercent / 100.0;
        }

        // Monster Compendium — "studied mobs take more damage". Study is what you have already
        // killed: the more of a type you have fought, the better you fight it.
        if (enabled(RegistryPerks.MONSTER_COMPENDIUM, player)) {
            int studied = studyCount(player, target.getType());
            if (studied > 0) {
                // Logarithmic, and capped: the tenth kill should teach far less than the first, and
                // a player who has farmed one mob for hours must not become unkillable against it.
                double scaled = Math.log10(studied + 1) * (config.monsterCompendiumPercent / 100.0);
                bonus += Math.min(config.monsterCompendiumPercent / 100.0 * 3.0, scaled);
            }
        }

        if (bonus != 0.0) event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
    }

    /**
     * Summoner — "summoned creatures are stronger".
     *
     * <p>Applied to damage the summon deals rather than as an attribute, so it covers whatever the
     * creature actually attacks with. {@link OwnableEntity} is vanilla's "this belongs to someone",
     * which is what a summon is here.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSummonDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof OwnableEntity summon)) return;
        if (!(summon.getOwner() instanceof Player owner) || owner.level().isClientSide()) return;
        if (!enabled(RegistryPerks.SUMMONER, owner)) return;

        double bonus = HandlerCommonConfig.HANDLER.instance().summonerPercent / 100.0;
        if (bonus <= 0) return;
        event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
    }

    // ── Study ───────────────────────────────────────────────────────────────────────────────

    /** Records what a player has killed, which is what Monster Compendium reads. */
    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!enabled(RegistryPerks.MONSTER_COMPENDIUM, player)) return;

        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (type == null) return;
        Map<ResourceLocation, Integer> counts =
                STUDIED.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
        if (counts.size() >= MAX_STUDIED_TYPES && !counts.containsKey(type)) return;
        counts.merge(type, 1, Integer::sum);
    }

    private static int studyCount(Player player, EntityType<?> type) {
        Map<ResourceLocation, Integer> counts = STUDIED.get(player.getUUID());
        if (counts == null) return 0;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null ? 0 : counts.getOrDefault(id, 0);
    }

    // ── Philosopher's Stone ─────────────────────────────────────────────────────────────────

    /**
     * Philosopher's Stone — "killed mobs have a chance to drop transmuted items".
     *
     * <p>Transmutes what the mob was already going to drop rather than adding loot on top: the
     * legend is about turning base material into precious, not about producing more of it. The drop
     * that changes is replaced in place, so drop counts and other mods' additions are untouched.
     */
    @SubscribeEvent
    public void onMobDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!enabled(RegistryPerks.PHILOSOPHERS_STONE, player)) return;

        double chance = HandlerCommonConfig.HANDLER.instance().philosophersStonePercent / 100.0;
        if (chance <= 0) return;

        for (net.minecraft.world.entity.item.ItemEntity drop : event.getDrops()) {
            if (player.getRandom().nextDouble() >= chance) continue;
            ItemStack transmuted = transmute(player, drop.getItem());
            if (!transmuted.isEmpty()) drop.setItem(transmuted);
        }
    }

    /** The metals and gems a transmutation produces, in ascending value. */
    private static final net.minecraft.world.item.Item[] TRANSMUTATIONS = {
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.LAPIS_LAZULI,
            Items.REDSTONE, Items.QUARTZ, Items.DIAMOND, Items.EMERALD,
    };

    private static ItemStack transmute(Player player, ItemStack original) {
        if (original.isEmpty()) return ItemStack.EMPTY;
        net.minecraft.world.item.Item into =
                TRANSMUTATIONS[player.getRandom().nextInt(TRANSMUTATIONS.length)];
        if (original.is(into)) return ItemStack.EMPTY;
        // One transmuted item per drop stack, whatever the stack held: turning sixty rotten flesh
        // into sixty diamonds is not a perk, it is an exploit.
        return new ItemStack(into, 1);
    }

    // ── Astral Projection ───────────────────────────────────────────────────────────────────

    /**
     * Astral Projection — "view the area around you in a radius".
     *
     * <p>Seeing through walls is what vanilla's glowing outline does, so the perk marks the living
     * things in range. Refreshed on a slow clock and short-lived, so it tracks the player rather
     * than accumulating, and it never touches entities the player owns — an outlined pet is noise,
     * not information.
     */
    @SubscribeEvent
    public void onProjectionTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 40 != 0) return;
        if (!enabled(RegistryPerks.ASTRAL_PROJECTION, player)) return;

        double radius = HandlerCommonConfig.HANDLER.instance().astralProjectionAmplifier;
        if (radius <= 0) return;

        AABB around = player.getBoundingBox().inflate(radius);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, around)) {
            if (mob instanceof OwnableEntity owned && owned.getOwner() == player) continue;
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    private static boolean enabled(
            net.minecraftforge.registries.RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
            Player player) {
        return perk != null && perk.get() != null && perk.get().isEnabled(player);
    }

    private static boolean isElemental(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_LIGHTNING)
                || source.is(DamageTypeTags.IS_FREEZING)
                || source.is(DamageTypes.FREEZE);
    }

    /**
     * Whether a creature belongs to the End.
     *
     * <p>Matched on the entity's own registry id rather than a hardcoded class list, so an addon's
     * End creature counts without a code change — the same reasoning the Powers schools use.
     */
    private static boolean isEnderKin(LivingEntity target) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("ender") || path.contains("shulker") || path.contains("endermite");
    }
}
