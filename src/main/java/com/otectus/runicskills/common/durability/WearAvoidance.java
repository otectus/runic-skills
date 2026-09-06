package com.otectus.runicskills.common.durability;

import com.otectus.runicskills.common.util.ProcRoll;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The one place this mod decides how much durability a player does not spend.
 *
 * <p>Every perk whose tooltip promises reduced wear — Unbreakable, Unbreaking Mastery, Gadgeteer,
 * Lock Expert, Lucky Break, Precision Tools — contributes to a single probability here, and that
 * probability is applied once. The alternative, a cap per perk, is what spec §5.2 forbids: five
 * perks each capped at 90% is not a 90% cap.
 *
 * <p>The arithmetic was previously inline in {@code MixItemStack}, which is the vanilla seam. It
 * moved here unchanged when a second seam appeared: Tinkers' deliberately spends durability outside
 * {@code ItemStack#hurt}, so its own {@code ToolDamageUtil} redirect has to reach the same numbers.
 * Two copies of a probability sum is two answers to "what does Lucky Break do", so there is one.
 *
 * <p><b>Ordering.</b> Native modifiers run first and this stage runs on what they left — the
 * Tinkers' seam is the call from {@code damage} into {@code directDamage}, after the whole
 * {@code TOOL_DAMAGE} hook chain. Vanilla has no such chain, so the vanilla seam is the head of
 * {@code hurt}, before its own Unbreaking roll, which is where it has always been.
 */
public final class WearAvoidance {

    /**
     * Never free: an item that could take no damage at all would be unbreakable in the literal
     * sense, which no combination of configured percentages should be able to grant by accident.
     */
    public static final double MAX_AVOIDANCE = 0.90D;

    /**
     * Above this many points in one hit, the per-point loop is replaced by a sampled count.
     *
     * <p>Vanilla wear is almost always one point and never approaches this, so every vanilla hit
     * still takes the exact per-point path it always did. A modded tool can be handed a very large
     * amount in a single call — §5.2 and D09 both say so — and looping over it is unbounded work
     * driven by another mod's number.
     */
    private static final int BERNOULLI_LIMIT = 256;

    /**
     * One more reason a point of durability is not spent, supplied from outside this class.
     *
     * <p>The six perks above are vanilla-answerable: "is this armour", "does it have Unbreaking".
     * The Tinkers' perks that also reduce wear are not — Slime Steward asks whether a tool's
     * overslime is exhausted, and Repair Memory asks whether this player still holds charges from a
     * repair — and neither question can be asked from common code without importing the very
     * classes {@code checkSidedImports} keeps out of it.
     *
     * <p>So they are installed rather than inlined. What matters is that they land in <em>this</em>
     * sum: §13.2 is explicit that new percentage-point contributions join the existing formulas
     * under one clamp, and a second Tinkers-only cap would let the same contributions run twice.
     */
    @FunctionalInterface
    public interface Contributor {

        /** The probability this contributor adds for {@code user} on {@code stack}; {@code 0} for none. */
        double avoidance(ServerPlayer user, ItemStack stack);
    }

    /**
     * A bounded, deterministic adjustment applied after the probabilistic stage.
     *
     * <p>One Power — Last Temper — is specified as an exception to probability: when ordinary wear
     * would break a tool that is still usable, the pending loss is reduced just enough to leave one
     * durability. That cannot be expressed as another term in the sum, because the sum is a chance
     * and this is a promise, and it cannot be expressed at the Tinkers' seam either, because the
     * seam must not know which Powers exist. So the stage is here, after sampling, where the number
     * of points about to be spent is finally known.
     *
     * <p>A clamp may only ever <em>reduce</em>: the contract is checked by {@link #reduce}, so a
     * clamp that returned more than it was given cannot turn a wear reduction into extra wear.
     */
    @FunctionalInterface
    public interface Clamp {

        /**
         * @param spending the points that would be spent after the probabilistic stage
         * @return the points to spend instead, within {@code [0, spending]}
         */
        int clamp(ServerPlayer user, ItemStack stack, int spending);
    }

    /**
     * Installed contributors, in registration order.
     *
     * <p>Copy-on-write: registration happens once during mod loading and reads happen on every
     * durability point spent by every player, so a lock on the read path would be paid forever to
     * protect a list that never changes again.
     */
    private static final java.util.List<Contributor> CONTRIBUTORS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Installed clamps, in registration order. Copy-on-write for the same reason. */
    private static final java.util.List<Clamp> CLAMPS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private WearAvoidance() {
    }

    /** Adds {@code contributor} to the single avoidance sum. Called from an integration bootstrap. */
    public static void addContributor(Contributor contributor) {
        if (contributor != null) CONTRIBUTORS.add(contributor);
    }

    /** Adds {@code clamp} to the post-sampling stage. Called from an integration bootstrap. */
    public static void addClamp(Clamp clamp) {
        if (clamp != null) CLAMPS.add(clamp);
    }

    /**
     * The combined probability that one point of durability is not spent, on {@code stack}, for
     * {@code user}. Always within {@code [0, MAX_AVOIDANCE]}.
     */
    public static double avoidance(ServerPlayer user, ItemStack stack) {
        if (user == null || stack == null || stack.isEmpty()) return 0.0D;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double avoided = 0.0D;

        // "Armor durability loss reduced by X%" — armour only, as the tooltip says.
        if (stack.getItem() instanceof ArmorItem
                && RegistryPerks.UNBREAKABLE != null
                && RegistryPerks.UNBREAKABLE.get().isEnabled(user)) {
            avoided += config.unbreakablePercent / 100.0D;
        }

        // "Unbreaking enchantment chance increased by X%" — only meaningful on an item that HAS
        // Unbreaking, which is what makes this different from the blanket reduction above.
        if (RegistryPerks.UNBREAKING_MASTERY != null
                && RegistryPerks.UNBREAKING_MASTERY.get().isEnabled(user)
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack) > 0) {
            avoided += config.unbreakingMasteryPercent / 100.0D;
        }

        // Gadgeteer — "Mechanical items are more effective". A gadget's only stat is how long it
        // keeps working, so that is what "more effective" buys.
        if (RegistryPerks.GADGETEER != null
                && RegistryPerks.GADGETEER.get().isEnabled(user)
                && isMechanical(stack)) {
            avoided += config.gadgeteerPercent / 100.0D;
        }

        // Lock Expert — "All locks take less time to pick". Locks Reforged spends a lock pick's
        // durability on every attempt, so a pick that survives more attempts is a cheaper lock.
        if (RegistryPerks.LOCK_EXPERT != null
                && RegistryPerks.LOCK_EXPERT.get().isEnabled(user)
                && isLockPick(stack)) {
            avoided += config.lockExpertPercent / 100.0D;
        }

        // Lucky Break — "Tool durability loss has a %s chance to be ignored". Eligibility is a
        // real rule rather than whatever was equipped: see DurabilityPerkRules, which keeps armour
        // out (Unbreakable already owns armour) and lets a pack name its own tools by tag.
        if (RegistryPerks.LUCKY_BREAK != null
                && RegistryPerks.LUCKY_BREAK.get().isEnabled(user)
                && DurabilityPerkRules.isLuckyBreakEligible(stack)) {
            avoided += ProcRoll.chance01(config.luckyBreakPercent);
        }

        // Precision Tools — "Tool durability increased by X%". A larger durability pool is a
        // property of an item, but this perk belongs to a PLAYER, and getMaxDamage has no player to
        // ask; so the same promise is kept from the other side, by not spending points. See
        // DurabilityMath.bonusDurabilityToAvoidance: ignoring each point with probability
        // X/(100+X) gives an expected lifetime of exactly 1 + X/100, which is what the tooltip
        // says. The cap below only bites past a configured 900%, so the +X% promise is exact for
        // every value a pack would plausibly set.
        if (RegistryPerks.PRECISION_TOOLS != null
                && RegistryPerks.PRECISION_TOOLS.get().isEnabled(user)
                && DurabilityPerkRules.isTool(stack)) {
            avoided += DurabilityMath.bonusDurabilityToAvoidance(config.precisionToolsPercent);
        }

        for (Contributor contributor : CONTRIBUTORS) {
            double extra = contributor.avoidance(user, stack);
            // A contributor that reports a nonsense number contributes nothing rather than
            // poisoning the sum: §13.2 rejects NaN and infinity at the boundary, and the boundary
            // for an installed contributor is here.
            if (Double.isFinite(extra) && extra > 0.0D) avoided += extra;
        }

        if (!Double.isFinite(avoided) || avoided <= 0.0D) return 0.0D;
        return Math.min(MAX_AVOIDANCE, avoided);
    }

    /**
     * How many of {@code amount} points are actually spent, after this stage.
     *
     * <p>Always {@code 0 <= result <= amount}: §5.2 requires checked arithmetic and a result
     * bounded by the original, because the input is another mod's integer and a negative or an
     * inflated one would turn a wear reduction into a wear increase.
     */
    public static int reduce(ServerPlayer user, ItemStack stack, int amount, RandomSource random) {
        if (amount <= 0 || random == null) return Math.max(0, amount);
        double avoided = avoidance(user, stack);
        int spending = amount;
        if (avoided > 0.0D) {
            int spared = sample(amount, avoided, random);
            spending = Math.max(0, Math.min(amount, amount - spared));
        }
        return applyClamps(user, stack, spending);
    }

    /**
     * Runs the installed clamps over {@code spending}, each bounded by what it was handed.
     *
     * <p>Order is registration order and every step is re-bounded, so two clamps compose to the
     * smaller of the two rather than to whichever ran last.
     */
    private static int applyClamps(ServerPlayer user, ItemStack stack, int spending) {
        if (spending <= 0 || CLAMPS.isEmpty()) return Math.max(0, spending);
        int result = spending;
        for (Clamp clamp : CLAMPS) {
            int clamped = clamp.clamp(user, stack, result);
            if (clamped < 0) clamped = 0;
            if (clamped < result) result = clamped;
        }
        return result;
    }

    /**
     * Points spared out of {@code amount}, each with probability {@code chance}.
     *
     * <p>Rolling per point rather than scaling and rounding keeps a 10% perk meaningful on the
     * single-point hits that make up almost all durability loss, where rounding would negate every
     * hit or none of them. Past {@link #BERNOULLI_LIMIT} the loop is replaced by one normal
     * approximation of the same binomial — the mean and variance are the ones the loop would have
     * had, the work is constant, and at those counts the per-point exactness the loop exists to
     * preserve is no longer distinguishable.
     */
    private static int sample(int amount, double chance, RandomSource random) {
        if (amount <= BERNOULLI_LIMIT) {
            int spared = 0;
            for (int i = 0; i < amount; i++) {
                if (random.nextDouble() < chance) spared++;
            }
            return spared;
        }
        double mean = amount * chance;
        double deviation = Math.sqrt(amount * chance * (1.0D - chance));
        long sampled = Math.round(mean + random.nextGaussian() * deviation);
        return (int) Math.max(0L, Math.min(amount, sampled));
    }

    /** A Locks Reforged lock pick, the tool Lock Expert makes go further. */
    public static boolean isLockPick(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "locks".equals(id.getNamespace()) && id.getPath().endsWith("_lock_pick");
    }

    /**
     * Whether an item is mechanical — something with moving parts, as opposed to a blade or a
     * pickaxe that is simply a shaped piece of metal.
     *
     * <p>The vanilla set is listed by class where one exists and by item where it does not, and a
     * modded item is matched on its own registry id, so a pack's gadgets qualify without this
     * needing to know about them. Bows are deliberately absent: a bow is drawn, not wound.
     */
    public static boolean isMechanical(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof CrossbowItem
                || item instanceof ShearsItem
                || item instanceof FishingRodItem
                || item instanceof FlintAndSteelItem
                || item instanceof BrushItem) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("gadget") || path.contains("mechanical") || path.contains("clockwork")
                || path.contains("on_a_stick");
    }
}
