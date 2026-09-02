package com.otectus.runicskills.registry.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * The loot perks, applied through Forge's global loot modifier system.
 *
 * <p>Five perks in the Fortune tree describe better loot from a particular <em>source</em> — chests,
 * bosses, fishing, dragon hoards — and all five were registered with config, tooltips and textures
 * but no runtime effect at all (RS10-004). None of them can be done from an entity or block event,
 * because by the time an item exists the loot table has already decided what it is.
 *
 * <p>A global loot modifier is the place where that decision is still open, and — contrary to a
 * note this project carried for some time — the loot context does identify the player: vanilla puts
 * whoever opened a container into {@code LootContextParams.THIS_ENTITY}, and a kill puts the killer
 * into {@code KILLER_ENTITY}. So these perks can be attributed properly rather than by proximity.
 *
 * <p>One modifier handles all five rather than one per perk: they share a context lookup and a
 * table-id test, and Forge applies modifiers in a datapack-defined order that is easier to reason
 * about with a single entry.
 */
public class RunicLootModifier extends LootModifier {

    public static final Supplier<Codec<RunicLootModifier>> CODEC =
            Suppliers.memoize(() -> RecordCodecBuilder.create(instance ->
                    codecStart(instance).apply(instance, RunicLootModifier::new)));

    /** Kept out of a static initialiser cycle with the registry below. */
    private static final class Suppliers {
        static <T> Supplier<T> memoize(Supplier<T> delegate) {
            return new Supplier<>() {
                private T value;
                @Override public T get() {
                    if (value == null) value = delegate.get();
                    return value;
                }
            };
        }
    }

    public RunicLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        Player player = playerOf(context);
        if (player == null || loot.isEmpty()) return loot;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        ResourceLocation table = context.getQueriedLootTableId();

        // Jackpot — "a chance for triple loot from chests". Only containers: a mob is not a chest,
        // and tripling a boss drop is what Cataclysm Spoils is for.
        if (isChest(table) && enabled(RegistryPerks.JACKPOT, player)
                && roll(context, config.jackpotPercent)) {
            multiply(loot, 3);
            return loot;
        }

        // Cataclysm Spoils — reinterpreted from a mod that is not a dependency here to the thing it
        // described: the loot a boss leaves behind. Vanilla's bosses are the ones that drop through
        // a loot table with a killer attached.
        if (enabled(RegistryPerks.CATACLYSM_SPOILS, player) && isBossDrop(context)
                && roll(context, config.cataclysmSpoilsPercent)) {
            multiply(loot, 2);
            return loot;
        }

        // Dragon Hoard — "dragon loot tables yield more". The End's treasure is vanilla's dragon
        // hoard: the dragon itself, and the cities built on what it left.
        if (enabled(RegistryPerks.DRAGON_HOARD, player) && isEndTreasure(table)
                && roll(context, config.dragonHoardPercent)) {
            multiply(loot, 2);
            return loot;
        }

        // Fisherman's Luck — reinterpreted from a fishing mod to vanilla's own fishing table.
        if (enabled(RegistryPerks.FISHERMANS_LUCK, player) && isFishing(table)
                && roll(context, config.fishermansLuckPercent)) {
            multiply(loot, 2);
            return loot;
        }

        // Apotheosis Gems — "gem quality chance increased". The note this project carried for years
        // said the gem loot context could not identify the player; it can, and the same lookup every
        // perk above uses proves it. What was actually missing was the moment: a gem's rarity is
        // decided inside loot generation, and by the time it exists as an item in the world the
        // decision is made. This is that moment.
        //
        // Guarded on the mod being present, because the upgrade is Apotheosis's own operation; in a
        // pack without it the perk does not register at all (see RegistryPerks).
        if (enabled(RegistryPerks.APOTHEOSIS_GEMS, player)
                && com.otectus.runicskills.integration.ApotheosisIntegration.isModLoaded()) {
            for (ItemStack stack : loot) {
                if (roll(context, config.apotheosisGemsPercent)) {
                    com.otectus.runicskills.integration.ApotheosisIntegration.upgradeGemRarity(stack);
                }
            }
        }

        // Ethereal Luck — "magical loot sources yield more". What makes a source magical, in
        // vanilla's own terms, is that it produced something enchanted.
        if (enabled(RegistryPerks.ETHEREAL_LUCK, player) && containsEnchanted(loot)
                && roll(context, config.etherealLuckPercent)) {
            multiply(loot, 2);
        }
        return loot;
    }

    @Override
    public Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    // -- context ------------------------------------------------------------------------------

    /**
     * The player this loot is for.
     *
     * <p>{@code THIS_ENTITY} is what a container puts there when a player opens it;
     * {@code KILLER_ENTITY} is what a kill puts there. Checking both covers chests and mobs without
     * ever guessing from proximity.
     */
    private static Player playerOf(LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (entity instanceof Player player) return player;
        entity = context.getParamOrNull(LootContextParams.KILLER_ENTITY);
        return entity instanceof Player player ? player : null;
    }

    private static boolean enabled(RegistryObject<com.otectus.runicskills.registry.perks.Perk> perk,
                                   Player player) {
        return perk != null && perk.get() != null && perk.get().isEnabled(player);
    }

    /** Uses the loot context's own random source, so a seeded world stays reproducible. */
    private static boolean roll(LootContext context, double percent) {
        return percent > 0 && context.getRandom().nextDouble() < percent / 100.0;
    }

    /**
     * Duplicates the rolled loot.
     *
     * <p>Copies rather than raising stack counts in place: a loot list can hold damaged or
     * enchanted items whose count must stay one, and growing those would produce stacks the game
     * cannot represent.
     */
    private static void multiply(ObjectArrayList<ItemStack> loot, int factor) {
        ObjectArrayList<ItemStack> original = new ObjectArrayList<>(loot);
        for (int copy = 1; copy < factor; copy++) {
            for (ItemStack stack : original) loot.add(stack.copy());
        }
    }

    private static boolean isChest(ResourceLocation table) {
        return table != null && table.getPath().startsWith("chests/");
    }

    private static boolean isFishing(ResourceLocation table) {
        return table != null && table.getPath().startsWith("gameplay/fishing");
    }

    private static boolean isEndTreasure(ResourceLocation table) {
        if (table == null) return false;
        String path = table.getPath();
        return path.contains("end_city") || path.contains("ender_dragon") || path.contains("dragon");
    }

    /** A boss is something that drops through a loot table and takes a deliberate kill to reach. */
    private static boolean isBossDrop(LootContext context) {
        Entity victim = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (victim == null) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        if (id == null) return false;
        String path = id.getPath();
        return path.equals("ender_dragon") || path.equals("wither") || path.equals("warden")
                || path.equals("elder_guardian");
    }

    private static boolean containsEnchanted(ObjectArrayList<ItemStack> loot) {
        for (ItemStack stack : loot) {
            if (stack.isEnchanted()) return true;
        }
        return false;
    }
}
