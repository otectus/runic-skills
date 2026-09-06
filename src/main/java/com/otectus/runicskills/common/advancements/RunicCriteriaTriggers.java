package com.otectus.runicskills.common.advancements;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;

/**
 * The three advancement criteria a quest pack may hang work on (§14.5).
 *
 * <p>They are a deliberately thin integration route: a pack that wants "make your first Tinkers'
 * tool" as a quest writes an ordinary advancement against {@code runicskills:tinker_assembly} and
 * rewards it however it already rewards advancements. That is why there is no FTB Quests dependency
 * here and no assumed quest API — §14.5 forbids both, and an advancement is the one interface every
 * quest mod already reads.
 *
 * <p><b>Registered on every install, Tinkers' or not.</b> A criterion that exists only when an
 * optional mod is present would make a pack's advancement file fail to parse on a server without
 * it, which is a louder failure than a criterion that simply never fires. The triggers themselves
 * are fired from the Tinkers' bridge, so an install without that mod registers three criteria and
 * emits nothing.
 *
 * <p><b>This mod ships no advancement of its own.</b> The triggers are for packs; adding a Runic
 * advancement would put a toast and a chat line in front of every player on every server, which is
 * a pack's decision rather than this mod's.
 */
public final class RunicCriteriaTriggers {

    private RunicCriteriaTriggers() {
    }

    /** A native tool was assembled at a station by this player. */
    public static final RunicItemTrigger TINKER_ASSEMBLY =
            new RunicItemTrigger(new ResourceLocation(RunicSkills.MOD_ID, "tinker_assembly"));

    /** A native repair that actually restored durability, paid for with materials. */
    public static final RunicItemTrigger TINKER_PAID_REPAIR =
            new RunicItemTrigger(new ResourceLocation(RunicSkills.MOD_ID, "tinker_paid_repair"));

    /** The Great Work's three distinct operations completed inside its window. */
    public static final RunicItemTrigger GREAT_WORK =
            new RunicItemTrigger(new ResourceLocation(RunicSkills.MOD_ID, "great_work"));

    /**
     * Adds the three criteria to the vanilla registry.
     *
     * <p>Called from common setup through {@code enqueueWork}: {@link CriteriaTriggers} keeps a
     * plain {@code HashMap} that vanilla only ever writes from its own static initialiser, so the
     * write has to be on the main thread, and it has to be complete before the first advancement
     * file is parsed at world load.
     */
    public static void register() {
        CriteriaTriggers.register(TINKER_ASSEMBLY);
        CriteriaTriggers.register(TINKER_PAID_REPAIR);
        CriteriaTriggers.register(GREAT_WORK);
    }
}
