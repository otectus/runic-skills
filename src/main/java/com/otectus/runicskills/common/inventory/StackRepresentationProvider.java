package com.otectus.runicskills.common.inventory;

import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who owns the stack-count representation in this installation.
 *
 * <p>An item count is stored in NBT and written into ordinary Minecraft item packets. Exactly one
 * mod may decide the format of both: two codecs on one byte stream is not a compatibility layer,
 * it is corruption with a startup message. This enum is that single decision, and everything
 * downstream — which mixins apply, which counts are legal, whether Pack Mule may grant extra
 * capacity, whether a large saved count is data or damage — reads it rather than assuming.
 *
 * <p>The choice is made <b>once, from {@link LoadingModList}, before the affected classes are
 * transformed</b>, which is why it may not consult {@code ModList.get()} (it does not exist yet)
 * or gameplay configuration (it is not loaded yet, is per-world, and a wire format cannot be
 * renegotiated after item bytes have started flowing — reference document §4.3). It is identical on
 * both physical sides because the mod list is.
 *
 * <p>Presence of another stack mod is never on its own a reason to hand over the representation.
 * Delegation happens only for an artifact whose count format this release has actually read; see
 * {@code docs/COMPATIBILITY_LEDGER_2.2.1.md} for the inspection that produced the pin below.
 */
public enum StackRepresentationProvider {
    /**
     * Runic Skills' own extended representation: integer {@code Count} in NBT, and a byte on the
     * wire until {@code -128} introduces a bounded positive VarInt. Bounded by
     * {@link StackCapacityMath#MAX_SERIALIZED_COUNT}, which is a limit of <i>this</i> format and
     * not a statement about what any item count may legally be.
     */
    RUNIC(StackCapacityMath.MAX_SERIALIZED_COUNT, true, true, true),
    /**
     * A verified foreign representation. Runic Skills writes no count bytes of its own, applies no
     * count validator, and grants no extra capacity; it records what the foreign format can carry
     * so that a large count coming back out of it is recognised as valid data.
     */
    EXTERNAL_DELEGATED(Integer.MAX_VALUE, false, false, false),
    /**
     * Another mod is transforming the same seams and this release has not read its format. Runic
     * Skills claims nothing: it installs no codec, guesses no wire format, rejects no count and
     * clamps nothing. The bound is {@link Integer#MAX_VALUE} because "unknown" must not become
     * "invalid" — a count this mod cannot classify is still somebody's property (invariant 10).
     */
    UNSUPPORTED_OVERLAP(Integer.MAX_VALUE, false, false, false);

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills");

    /** Bigger Stacks' mod id. */
    private static final String BIGGER_STACKS = "biggerstacks";

    /**
     * The one Bigger Stacks build whose count representation has been read byte for byte:
     * NBT {@code Count} byte clamped to 127 plus an integer {@code BigCount} above it, a read path
     * that already accepts Runic Skills' integer {@code Count}, and a fixed 4-byte int on the wire.
     *
     * <p>Pinned by declared version rather than by file hash on purpose: ForgeGradle rewrites
     * third-party jars for the Mojang-mapped development runtime, which changes the hash and leaves
     * the manifest version alone. The observed hash is reported in the diagnostic instead, against
     * the value recorded in the ledger.
     */
    private static final String VERIFIED_BIGGER_STACKS = "1.20.1-2026.06.17";

    /** SHA-256 of the artifact that was inspected, for the diagnostic only. */
    private static final String INSPECTED_BIGGER_STACKS_SHA256 =
            "70840af81e6f734a6ea9c5eecf1f540eb470e42ce8339a5f4cb381a3cfc36204";

    private static volatile StackRepresentationProvider selected;
    private static volatile String detail = "not selected yet";

    private final int maxRepresentableCount;
    private final boolean ownsNbtCount;
    private final boolean ownsNetworkCount;
    private final boolean grantsPackMuleCapacity;

    StackRepresentationProvider(int maxRepresentableCount, boolean ownsNbtCount,
            boolean ownsNetworkCount, boolean grantsPackMuleCapacity) {
        this.maxRepresentableCount = maxRepresentableCount;
        this.ownsNbtCount = ownsNbtCount;
        this.ownsNetworkCount = ownsNetworkCount;
        this.grantsPackMuleCapacity = grantsPackMuleCapacity;
    }

    /** The largest count this representation can carry without losing information. */
    public int maxRepresentableCount() {
        return maxRepresentableCount;
    }

    /** Whether Runic Skills serializes the count into item packets itself. */
    public boolean ownsNetworkCount() {
        return ownsNetworkCount;
    }

    /** Whether Runic Skills serializes the count into item NBT itself. */
    public boolean ownsNbtCount() {
        return ownsNbtCount;
    }

    /**
     * Whether Pack Mule may raise a player slot's capacity above the item's own maximum.
     *
     * <p>False for every foreign provider. Bigger Stacks rewrites {@code Container.getMaxStackSize},
     * {@code Slot.getMaxStackSize} and {@code IItemHandler.getSlotLimit} for all subclasses through
     * its own transformer library — the same three destination seams Pack Mule adjusts — and
     * reference document §4.5 rules out stacking a Runic multiplier on top of a foreign limit in
     * this implementation. The perk is deferred with a reported reason rather than silently
     * layered; Runic Skills-only installations are untouched.
     */
    public boolean grantsPackMuleCapacity() {
        return grantsPackMuleCapacity;
    }

    /** The selected owner, chosen on first call and unchanged for the lifetime of the process. */
    public static StackRepresentationProvider selected() {
        StackRepresentationProvider current = selected;
        return current != null ? current : select();
    }

    /** One sentence naming the observed environment and why this owner was chosen. */
    public static String selectionDetail() {
        selected();
        return detail;
    }

    private static synchronized StackRepresentationProvider select() {
        if (selected != null) return selected;
        StackRepresentationProvider choice;
        try {
            choice = choose();
        } catch (RuntimeException | LinkageError failure) {
            // The mod list is the only input; if it cannot be read there is also no evidence that
            // anything else is transforming these seams, and refusing to serialize at all would be
            // worse than the format this mod has always used.
            choice = RUNIC;
            detail = "the loading mod list could not be read (" + failure + "); "
                    + "assuming the Runic Skills representation";
        }
        selected = choice;
        LOGGER.info("Runic Skills stack representation: {} — {}. Max representable count {}; "
                + "Runic NBT count hook {}, Runic packet count hook {}, Pack Mule capacity {}.",
                choice, detail, choice.maxRepresentableCount,
                choice.ownsNbtCount ? "active" : "inactive",
                choice.ownsNetworkCount ? "active" : "inactive",
                choice.grantsPackMuleCapacity ? "available" : "deferred");
        return choice;
    }

    private static StackRepresentationProvider choose() {
        LoadingModList list = LoadingModList.get();
        ModFileInfo biggerStacks = list == null ? null : list.getModFileById(BIGGER_STACKS);
        if (biggerStacks == null) {
            detail = "no other stack-representation mod was found";
            return RUNIC;
        }
        String version = "unknown";
        for (IModInfo mod : biggerStacks.getMods()) {
            if (BIGGER_STACKS.equals(mod.getModId())) version = mod.getVersion().toString();
        }
        String sha256 = com.otectus.runicskills.integration.common.IntegrationRuntime
                .sha256(biggerStacks.getFile().getFilePath());
        if (!VERIFIED_BIGGER_STACKS.equals(version)) {
            detail = "Bigger Stacks " + version + " (sha256 " + sha256 + ") owns item counts, but this"
                    + " release has only read the representation of " + VERIFIED_BIGGER_STACKS + "."
                    + " Runic Skills is installing no count codec of its own and is neither"
                    + " validating nor rewriting stored counts; existing data is left exactly as it"
                    + " is. Pack Mule capacity is deferred. Install Bigger Stacks "
                    + VERIFIED_BIGGER_STACKS + " for a supported combination";
            return UNSUPPORTED_OVERLAP;
        }
        detail = "Bigger Stacks " + version + " owns item counts (sha256 " + sha256
                + (INSPECTED_BIGGER_STACKS_SHA256.equals(sha256) ? ", matching the inspected artifact"
                        : ", differing from the inspected " + INSPECTED_BIGGER_STACKS_SHA256
                                + " — expected for a remapped development jar")
                + "); Runic Skills delegates count persistence and count packets to it";
        return EXTERNAL_DELEGATED;
    }
}
