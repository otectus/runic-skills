# Runic Skills — Public Event API

Since 1.2.0. Four `PlayerEvent` subclasses live on the **Forge bus** (`MinecraftForge.EVENT_BUS`). Subscribers can observe and (where applicable) cancel skill-level-ups, passive-level changes, perk toggles, and title unlocks.

| Event | Cancelable | Fires from | Fields |
|---|---|---|---|
| [`SkillLevelUpEvent`](../src/main/java/com/otectus/runicskills/event/SkillLevelUpEvent.java) | ✅ | `SkillLevelUpSP.handle` after validation, before mutation | `Skill skill`, `int oldLevel`, `int newLevel` |
| [`PassiveLevelUpEvent`](../src/main/java/com/otectus/runicskills/event/PassiveLevelUpEvent.java) | ✅ | `PassiveLevelUpSP.handle` and `PassiveLevelDownSP.handle` after validation, before mutation | `Passive passive`, `int oldLevel`, `int newLevel` |
| [`PerkToggleEvent.Pre`](../src/main/java/com/otectus/runicskills/event/PerkToggleEvent.java) | ✅ | `TogglePerkSP.handle` after built-in validation, before rank/cooldown write | `Perk perk`, `int oldRank`, `int newRank`, `boolean wasEnabled`, `boolean isEnabled` |
| [`PerkToggleEvent.Post`](../src/main/java/com/otectus/runicskills/event/PerkToggleEvent.java) | ❌ | after rank/cooldown write, before client sync | (same as Pre) |
| [`TitleEarnedEvent`](../src/main/java/com/otectus/runicskills/event/TitleEarnedEvent.java) | ❌ | `Title.setRequirement` when `unlockTitle` flips false→true | `Title title` |

## Cancellation semantics

When a `Pre` or level-up event is cancelled:
- **`SkillLevelUpEvent`** — increment is aborted. XP is not consumed (the bypass happens before the XP deduction). No `SyncSkillCapabilityCP` is sent. Client UI continues showing the pre-attempt state.
- **`PassiveLevelUpEvent`** — both up and down directions; subscribers should filter on `newLevel > oldLevel` if they only want one direction. Attribute reconciliation is skipped on cancel.
- **`PerkToggleEvent.Pre`** — rank change and cooldown application are both skipped. A `SyncSkillCapabilityCP` resynchronizes the client to undo any optimistic UI state.

Subscribers should treat cancellation as authoritative: don't queue follow-up packets from a cancelled handler.

## Example — Java mod subscriber

```java
@Mod.EventBusSubscriber(modid = "myaddon", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MyAddon {
    @SubscribeEvent
    public static void onSkillUp(SkillLevelUpEvent event) {
        Player player = event.getEntity();
        if (event.getSkill() == RegistrySkills.STRENGTH.get() && event.getNewLevel() >= 32) {
            player.sendSystemMessage(Component.literal("Strength capped!"));
        }
    }

    @SubscribeEvent
    public static void onPerkToggle(PerkToggleEvent.Pre event) {
        // Reject Wisdom-tree perks on weekends (silly example)
        if (event.getPerk().getSkill() == RegistrySkills.WISDOM.get()
                && LocalDate.now().getDayOfWeek().getValue() >= 6) {
            event.setCanceled(true);
        }
    }
}
```

## Example — KubeJS script (server_scripts/)

KubeJS forwards Forge events through its native `ForgeEvents` bridge:

```js
ForgeEvents.onEvent('net.minecraftforge.event.entity.player.PlayerEvent$SkillLevelUpEvent', event => {
    const player = event.entity
    const skill = event.skill
    console.info(`${player.username} leveled ${skill.name} to ${event.newLevel}`)
})
```

The legacy `SkillLevelUpEventJS` surface (pre-1.2.0) still works through a back-compat shim. New scripts should subscribe to the Forge event directly so they don't depend on the shim, which is marked `@Deprecated(forRemoval = true)` and slated for removal in a future major.

## Stability commitment

These four events are part of the public API since 1.2.0. The class signatures (fields, getters, `@Cancelable` status) won't change across minor versions. Field semantics (when the event fires, what mutation has happened) won't change without a `CHANGELOG` migration note. Removal would require a major version bump.

If you need a new event surface that doesn't exist yet, open an issue at [github.com/otectus/runicskills/issues](https://github.com/otectus/runicskills/issues).
