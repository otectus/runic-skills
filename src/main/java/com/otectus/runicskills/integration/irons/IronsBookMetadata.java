package com.otectus.runicskills.integration.irons;

import com.otectus.runicskills.integration.lock.IronsBookProfile;
import com.otectus.runicskills.integration.lock.IronsBookMetadataSource;
import com.otectus.runicskills.integration.lock.IronsSpellbooksLockProvider;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads what an Iron's spellbook chassis actually is, so the equipment gate can be a statement about
 * the item instead of about its name.
 *
 * <p>Loaded only from behind the Iron's presence check — {@link #install()} is called by
 * {@code IronsSpellbooksIntegration}, which itself loads through
 * {@code RunicSkills.tryLoadIntegration("irons_spellbooks", …)}. Nothing in the always-loaded lock
 * registry names this class or anything it imports; it reaches the registry the other way round, by
 * installing itself as an {@link IronsBookMetadataSource}.
 *
 * <h2>Never touch the player's book</h2>
 *
 * <p>Spec §5.2 is explicit: query static metadata or a detached inspection stack, never initialise
 * or mutate the real spell container to classify it. So every read here is against
 * {@code new ItemStack(item)} — a stack that exists for the length of one method call, belongs to
 * nobody, and is discarded. {@link IPresetSpellContainer#initializeSpellContainer(ItemStack)} is
 * called on <em>that</em> stack, which is how a unique book's locked preset spells become
 * countable without writing NBT into anything a player owns.
 *
 * <h2>Why the container and not the class</h2>
 *
 * <p>{@code ISpellbook} is an empty marker interface in 3.16.3 and carries no data at all, and
 * addon books do not reliably extend {@link SpellBook}: in Apprentice's Codex alone, one book
 * extends {@code SpellBook}, two extend {@code UniqueSpellBook}, one implements
 * {@code ISpellbook} + {@code IPresetSpellContainer} over plain {@code Item}, one implements
 * {@code ISpellbook} without {@code IPresetSpellContainer}, and two implement
 * {@code IPresetSpellContainer} without {@code ISpellbook}. Keying on the class hierarchy would
 * describe Iron's own books and misread everyone else's. Keying on the container — which every book
 * that can hold a spell must have — describes all of them.
 *
 * <p>An item that answers none of these reads is reported as unknown rather than as zero. Empty is
 * "I could not tell you", and the caller falls back to a conservative anchor; zero would mean "this
 * chassis is inert", which would silently unlock it.
 */
public final class IronsBookMetadata implements IronsBookMetadataSource {

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills/irons-books");

    /**
     * Placeholder modifier owner. Iron's reads only the slot identifier and index off the context,
     * and the returned modifiers are counted, never applied, so the UUID is never compared to
     * anything.
     */
    private static final UUID INSPECTION_ID = new UUID(0L, 0L);

    /**
     * Chassis reads are cached by item id.
     *
     * <p>A chassis is a property of the registered item, not of any stack of it, so the answer
     * cannot change while the game is running — and the rule build asks about every item in the
     * namespace, once per reload. {@code Optional} values are cached too: an item that could not be
     * read is not worth re-attempting on every rebuild.
     */
    private final Map<String, Optional<IronsBookProfile>> cache = new ConcurrentHashMap<>();

    /** Installs this reader into the lock provider. Idempotent. */
    public static void install() {
        IronsSpellbooksLockProvider.installMetadataSource(new IronsBookMetadata());
        LOGGER.debug("Iron's spellbook metadata adapter installed");
    }

    @Override
    public Optional<IronsBookProfile> profile(String itemId) {
        if (itemId == null || itemId.isBlank()) return Optional.empty();
        return cache.computeIfAbsent(itemId, IronsBookMetadata::read);
    }

    /** Drops cached reads. Used when the registries a read depends on have been rebuilt. */
    public void invalidate() {
        cache.clear();
    }

    private static Optional<IronsBookProfile> read(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return Optional.empty();
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return Optional.empty();
        try {
            return chassis(item);
        } catch (RuntimeException | LinkageError e) {
            // An addon book whose container read throws must degrade to "unknown", not take the
            // whole rule build down with it.
            LOGGER.warn("Could not read the Iron's chassis of {}; it will use the conservative "
                    + "book anchor instead", itemId, e);
            return Optional.empty();
        }
    }

    private static Optional<IronsBookProfile> chassis(Item item) {
        ItemStack inspection = new ItemStack(item);
        if (item instanceof IPresetSpellContainer preset) {
            // On the detached stack only. This is what fills in a unique book's locked spells.
            preset.initializeSpellContainer(inspection);
        }

        int maxSlots;
        int presetSlots = 0;
        if (ISpellContainer.isSpellContainer(inspection)) {
            ISpellContainer container = ISpellContainer.get(inspection);
            maxSlots = Math.max(0, container.getMaxSpellCount());
            for (SpellSlot slot : container.getActiveSpells()) {
                if (slot != null && slot.isLocked()) presetSlots++;
            }
        } else if (item instanceof SpellBook book) {
            // A book that declares its capacity but declined to build a container for a stack with
            // no owner. Capacity is still a fact about the chassis.
            maxSlots = Math.max(0, book.getMaxSpellSlots());
        } else {
            return Optional.empty();
        }

        return Optional.of(new IronsBookProfile(maxSlots, presetSlots, attributeModifiers(item, inspection)));
    }

    /**
     * How many attribute modifiers the chassis grants while worn.
     *
     * <p>Iron's books are Curios items whose modifiers are declared against the {@code spellbook}
     * slot, so they are invisible to {@link Item#getDefaultAttributeModifiers(EquipmentSlot)} and
     * have to be asked for through the Curios seam with that slot named. A book that is not a
     * Curios item is asked the vanilla question instead. Either read failing counts as zero
     * modifiers rather than as an unreadable chassis: capacity is the dominant term, and refusing
     * to describe a book at all because its bonus list could not be counted would throw away the
     * part that was read successfully.
     */
    private static int attributeModifiers(Item item, ItemStack inspection) {
        try {
            if (item instanceof ICurioItem curio) {
                var modifiers = curio.getAttributeModifiers(
                        new SlotContext(Curios.SPELLBOOK_SLOT, null, 0, false, true),
                        INSPECTION_ID, inspection);
                return modifiers == null ? 0 : modifiers.size();
            }
            var vanilla = item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND);
            return vanilla == null ? 0 : vanilla.size();
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }
}
