# Runic Skills — Public Event API

Five `PlayerEvent` subclasses live on the **Forge bus** (`MinecraftForge.EVENT_BUS`). Subscribers can observe and (where applicable) cancel skill-level-ups, passive-level changes, perk toggles, title unlocks, and Power procs.

| Event | Cancelable | Fires from | Fields |
|---|---|---|---|
| [`SkillLevelUpEvent`](../src/main/java/com/otectus/runicskills/event/SkillLevelUpEvent.java) | ✅ | `ProgressionService.setSkillLevel`, after clamping and before mutation — so it covers the screen, the level-up packet **and** the admin commands, which used to write straight into the capability | `Skill skill`, `int oldLevel`, `int newLevel` |
| [`PassiveLevelUpEvent`](../src/main/java/com/otectus/runicskills/event/PassiveLevelUpEvent.java) | ✅ | `AdjustPassiveSP.handle` after validation, before mutation | `Passive passive`, `int oldLevel`, `int newLevel` |
| [`PerkToggleEvent.Pre`](../src/main/java/com/otectus/runicskills/event/PerkToggleEvent.java) | ✅ | `TogglePerkSP.handle` after built-in validation, before rank/cooldown write | `Perk perk`, `int oldRank`, `int newRank`, `boolean wasEnabled`, `boolean isEnabled` |
| [`PerkToggleEvent.Post`](../src/main/java/com/otectus/runicskills/event/PerkToggleEvent.java) | ❌ | after rank/cooldown write, before client sync | (same as Pre) |
| [`TitleEarnedEvent`](../src/main/java/com/otectus/runicskills/event/TitleEarnedEvent.java) | ❌ | `Title.setRequirement` when `unlockTitle` flips false→true | `Title title` |
| [`PowerProcEvent`](../src/main/java/com/otectus/runicskills/event/PowerProcEvent.java) | ❌ | `PowerDispatch.fireProc`, **after** the Power's behaviour has committed | `Power power`, `Entity target` (nullable), `Vec3 origin`, `int variant`, `int intensity`, `boolean critical`, `boolean ownerOnly` |

## Cancellation semantics

When a `Pre` or level-up event is cancelled:
- **`SkillLevelUpEvent`** — increment is aborted. XP is not consumed (the bypass happens before the XP deduction). No `SyncSkillCapabilityCP` is sent. Client UI continues showing the pre-attempt state.
- **`PassiveLevelUpEvent`** — both up and down directions; subscribers should filter on `newLevel > oldLevel` if they only want one direction. Attribute reconciliation is skipped on cancel.

  **Changed in 2.0.0.** It used to fire once per level crossed, because the screen sent one packet
  per level and the server applied them one at a time — which the rate limiter then silently
  discarded all but the first of. `AdjustPassiveSP` carries a signed amount and the server applies
  it atomically, so the event now fires **once per accepted request**, with `oldLevel` and
  `newLevel` spanning the whole move. A subscriber counting events to count levels needs
  `newLevel - oldLevel` instead.
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
ForgeEvents.onEvent('com.otectus.runicskills.event.SkillLevelUpEvent', event => {
    const player = event.entity
    const skill = event.skill
    console.info(`${player.username} leveled ${skill.name} to ${event.newLevel}`)
})
```

The legacy pre-1.2.0 KubeJS surface still works through a back-compat shim
(`kubejs/events/LevelUpEvent` posted reflectively from `KubeJSIntegration.postLevelUpEvent`). New
scripts should subscribe to the Forge event directly.

**Two things changed about the shim in 2.0.1.** It used to be posted from the Skills screen's click
handler — on the *client* — so a script cancelling it only suppressed that one packet, and
anything that levelled a skill by another route walked straight past. It is posted from
`ProgressionService` now, on the server, where a cancellation is authoritative like every other one.

It is still `@Deprecated(forRemoval = true)`. Earlier revisions of this document said removal
"would require a major version bump"; 2.0.0 *was* that bump and the shim stayed, so that wording was
promising a schedule nobody was keeping. **The commitment now is concrete: it is removed in 2.1.0.**

## Stability commitment

The first four events are public API since 1.2.0; `PowerProcEvent` since 2.0.1. The class signatures
(fields, getters, `@Cancelable` status) won't change across minor versions. Field semantics (when the
event fires, what mutation has happened) won't change without a `CHANGELOG` migration note. Removal
would require a major version bump.

**`PassiveLevelUpEvent`'s firing semantics did change in 2.0.0** — see the note above. It carried a
`CHANGELOG` entry, which is what that commitment requires, but it is the kind of change to read for
before upgrading a subscriber.

If you need a new event surface that doesn't exist yet, open an issue at [github.com/otectus/runicskills/issues](https://github.com/otectus/runicskills/issues).
