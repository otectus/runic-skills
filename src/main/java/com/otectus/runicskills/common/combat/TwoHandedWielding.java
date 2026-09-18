package com.otectus.runicskills.common.combat;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * "Is this a two-handed weapon?", asked without naming a single third-party class.
 *
 * <p>Two mods answer that question for 1.20.1 and they answer it from unrelated data: Better Combat
 * reads its own {@code weapon_attributes} datapack registry, Spartan Weaponry reads a trait list
 * declared on the item. Neither ships an interface the other implements, and neither may be named
 * from common code — an installation without them would resolve the class and fail. So common code
 * asks this, and each installed mod contributes a {@link Source} that is loaded reflectively at
 * construction time and never mentioned anywhere else.
 *
 * <p>The class also owns the <em>real</em> slot readers. Better Combat's common mixin on
 * {@link Player#getItemBySlot} returns {@link ItemStack#EMPTY} for the off-hand whenever either hand
 * holds a two-handed weapon, so every vanilla accessor — {@code getOffhandItem}, equipment sync,
 * rendering — reports an empty off-hand for exactly the players this mod's Titan's Grip perk is
 * about. {@link #realMainHand} and {@link #realOffhand} read the inventory lists directly, which is
 * the same place Better Combat's own injector reads from, so they see the truth no matter how many
 * mods have injected into the accessor. They are also the only safe readers for code that runs
 * <em>inside</em> a {@code getItemBySlot} wrapper, where calling the accessor again would recurse.
 */
public final class TwoHandedWielding {

    /** One mod's opinion about one stack. Implementations live under {@code integration/}. */
    public interface Source {
        /** The contributing mod id, for logging and for the diagnostic listing. */
        String name();

        /** Whether that mod considers {@code stack} a two-handed weapon. Never called with empty. */
        boolean isTwoHanded(ItemStack stack);
    }

    /**
     * Copy-on-write because installation happens once, during the mod constructor, and reads happen
     * on the render thread and both logical sides for the rest of the process.
     */
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private TwoHandedWielding() {
    }

    /** Adds a source. Idempotent per source name, so a double install cannot double-count. */
    public static void addSource(Source source) {
        if (source == null) return;
        for (Source existing : SOURCES) {
            if (existing.name().equals(source.name())) return;
        }
        SOURCES.add(source);
    }

    /**
     * Loads {@code fqcn} as a {@link Source} when {@code modId} is present.
     *
     * <p>Same shape, and the same reasoning, as {@code RunicSkills.tryLoadIntegration}: the class
     * names third-party types, so it must never be resolved on an installation without them, and a
     * source that cannot be built degrades to "this mod contributes no opinion" rather than to a
     * failed mod load.
     *
     * @return whether a source was installed
     */
    public static boolean installSource(String modId, String fqcn) {
        if (ModList.get() == null || !ModList.get().isLoaded(modId)) return false;
        try {
            Object instance = Class.forName(fqcn).getDeclaredConstructor().newInstance();
            if (!(instance instanceof Source source)) return false;
            addSource(source);
            return true;
        } catch (Exception | NoClassDefFoundError e) {
            com.otectus.runicskills.RunicSkills.getLOGGER().warn(
                    "Failed to install two-handed weapon source {} for mod {}", fqcn, modId, e);
            return false;
        }
    }

    /** The installed source names, in installation order. Diagnostics only. */
    public static List<String> sourceNames() {
        return SOURCES.stream().map(Source::name).toList();
    }

    /** Whether any installed source calls {@code stack} a two-handed weapon. */
    public static boolean isTwoHanded(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Source source : SOURCES) {
            if (source.isTwoHanded(stack)) return true;
        }
        return false;
    }

    /**
     * The stack actually in the selected hotbar slot, read past every mixin on
     * {@link Player#getItemBySlot}.
     */
    public static ItemStack realMainHand(Player player) {
        Inventory inventory = player == null ? null : player.getInventory();
        if (inventory == null) return ItemStack.EMPTY;
        ItemStack stack = inventory.getSelected();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /**
     * The stack actually in the off-hand slot, read past every mixin on
     * {@link Player#getItemBySlot}.
     */
    public static ItemStack realOffhand(Player player) {
        Inventory inventory = player == null ? null : player.getInventory();
        if (inventory == null || inventory.offhand == null || inventory.offhand.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = inventory.offhand.get(0);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /**
     * Whether {@code stack} is something a player could raise as a shield.
     *
     * <p>{@link ShieldItem} covers vanilla and every mod that subclasses it; the Forge tool action
     * covers the ones that do not (Spartan Shields' tower shields among them). Either is enough:
     * the perk is about carrying a shield, not about carrying <em>that</em> shield.
     */
    public static boolean isBlockable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof ShieldItem
                || stack.canPerformAction(ToolActions.SHIELD_BLOCK);
    }
}
