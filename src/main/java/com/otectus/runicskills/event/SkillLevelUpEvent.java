package com.otectus.runicskills.event;

import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired on the Forge bus from {@code ProgressionService.setSkillLevel} when a player's skill level
 * is about to change: after the requested level has been clamped to the configured range, and
 * before the capability is written.
 *
 * <p><b>Cancelling skips the mutation; nothing is rolled back.</b> Earlier versions of this Javadoc
 * described a post-mutation event whose cancellation undid the write in the same tick. That was
 * never a good contract — a rollback is a second write, visible to anything watching the
 * capability — and it stopped being true when every mutation path was consolidated. A cancelled
 * event now means the level simply never changed, and the caller receives
 * {@code Outcome.denial() == CANCELLED}.
 *
 * <p>It fires for every path into the service: the purchase packet, all three {@code /skills}
 * command forms, and any other Java caller. It also fires for <em>decreases</em>, despite the
 * name — an operator's {@code subtract} is a level change like any other, and the name is kept
 * because it is public API. Check {@link #getOldLevel()} against {@link #getNewLevel()} if a
 * subscriber only cares about increases.
 *
 * <p>The KubeJS {@code RunicSkillsEvents.skillLevelUp} event is posted immediately after this one,
 * for increases only, and can cancel independently. This event runs first, so a Java subscriber's
 * veto costs nothing in script execution.
 *
 * <p>Public API since 1.2.0.
 */
@Cancelable
public class SkillLevelUpEvent extends PlayerEvent {
    private final Skill skill;
    private final int oldLevel;
    private final int newLevel;

    public SkillLevelUpEvent(Player player, Skill skill, int oldLevel, int newLevel) {
        super(player);
        this.skill = skill;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Skill getSkill() {
        return skill;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
