# Runic Skills — Public Event API

Seven events live on the **Forge bus** (`MinecraftForge.EVENT_BUS`): six `PlayerEvent` subclasses plus `BlockBreakCommittedEvent`, which extends `net.minecraftforge.eventbus.api.Event` directly rather than `PlayerEvent`. Subscribers can observe and (where applicable) cancel skill-level-ups, passive-level changes, perk toggles, title unlocks, Power procs, and committed block breaks.

| Event | Cancelable | Fires from | Fields |
|---|---|---|---|
| [`SkillLevelUpEvent`](../src/main/java/com/otectus/runicskills/event/SkillLevelUpEvent.java) | ✅ | `ProgressionService.setSkillLevel`, after clamping and before mutation — so it covers the screen, the level-up packet **and** the admin commands, which used to write straight into the capability. The KubeJS server post follows this event and is also cancelable. | `Skill skill`, `int oldLevel`, `int newLevel` |
| [`PassiveLevelUpEvent`](../src/main/java/com/otectus/runicskills/event/PassiveLevelUpEvent.java) | ✅ | `AdjustPassiveSP.handle` after validation, before mutation | `Passive passive`, `int oldLevel`, `int newLevel` |
| [`PerkToggleEvent.Pre`](../src/main/java/com/otectus/runicskills/event/PerkToggleEvent.java) | ✅ | `TogglePerkSP.handle` after built-in validation, before rank/cooldown write | `Perk perk`, `int oldRank`, `int newRank`, `boolean wasEnabled`, `boolean isEnabled` |
| [`PerkToggleEvent.Post`](../src/main/java/com/otectus/runicskills/event/PerkToggleEvent.java) | ❌ | after rank/cooldown write, before client sync | (same as Pre) |
| [`TitleEarnedEvent`](../src/main/java/com/otectus/runicskills/event/TitleEarnedEvent.java) | ❌ | `Title.setRequirement` when `unlockTitle` flips false→true | `Title title` |
| [`PowerProcEvent`](../src/main/java/com/otectus/runicskills/event/PowerProcEvent.java) | ❌ | `PowerDispatch.fireProc`, **after** the Power's behaviour has committed | `Power power`, `Entity target` (nullable), `Vec3 origin`, `int variant`, `int intensity`, `boolean critical`, `boolean ownerOnly` |
| [`BlockBreakCommittedEvent`](../src/main/java/com/otectus/runicskills/common/actions/BlockBreakCommittedEvent.java) | ❌ | `ServerPlayerGameMode.destroyBlock` after the block is removed and `Block.playerDestroy` has run (loot is dropped); also from native area-harvest children in `ToolHarvestLogic.breakBlock` via the Tinkers' Construct integration | `ServerLevel level`, `BlockPos pos` (immutable), `BlockState state` (pre-removal), `ServerPlayer player`, `ItemStack tool` (copy, pre-damage) |

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

Since 2.0.5, Runic Skills posts its own custom event to KubeJS server_scripts, where a cancellation is authoritative and charges no XP. See [`docs/KUBEJS.md`](KUBEJS.md) for the full reference:

```js
RunicSkillsEvents.skillLevelUp(event => {
    if (event.cause !== 'purchase') return;  // Let ops use /skills
    if (event.skill.name === 'magic' && event.newLevel > 30) {
        event.deny('Magic is capped at 30');
    }
})
```

This is the supported KubeJS route. The old `ForgeEvents` bridge is not available in server_scripts.

## Advancement criteria (quest-pack integration)

Three vanilla-style advancement criteria, registered on every install whether or not Tinker's
Construct is present (`common/advancements/RunicCriteriaTriggers.java`, registered from
`FMLCommonSetupEvent` via `enqueueWork` — `RunicSkills.java:220-222`). They are fired from the
committed, once-per-take points inside the Tinkers' station bridge, never from a preview, a quote,
or a hopper transfer, so a server without Tinkers' Construct registers all three and simply never
fires them.

| Criterion id | Fires when |
|---|---|
| `runicskills:tinker_assembly` | A native Tinkers' tool is assembled at a station (`TConstructStationBridge.onStationCraft`, on `CraftOperationKind.ASSEMBLY`) |
| `runicskills:tinker_paid_repair` | A native station repair actually restores durability, paid for with materials (same call site, on `CraftOperationKind.REPAIR` with `nativeRestored > 0`) |
| `runicskills:great_work` | The Great Work Power's three distinct station operations (assembly, repair, cast) all complete inside its window (`TConstructPowerDispatcher.greatWorkStep`, on the Power proccing) |

Each takes the same optional `item` condition as any vanilla `ItemPredicate` criterion:

```json
"criteria": {
  "first_tool": {
    "trigger": "runicskills:tinker_assembly",
    "conditions": { "item": { "items": ["tconstruct:pickaxe"] } }
  }
}
```

This mod ships no advancement of its own against these criteria — they exist for a quest pack to
build on. See [`KUBEJS.md`](KUBEJS.md#tinkers-construct-tinkering-events) for the KubeJS-side
`tinkerOperationCheck` / `tinkerOperationCompleted` / `tinkerToolLevelChanged` events, which observe
the same station operations from scripts rather than from advancement files.

## Stability commitment

The first four events are public API since 1.2.0; `PowerProcEvent` since 2.0.1. The class signatures
(fields, getters, `@Cancelable` status) won't change across minor versions. Field semantics (when the
event fires, what mutation has happened) won't change without a `CHANGELOG` migration note. Removal
would require a major version bump.

**`PassiveLevelUpEvent`'s firing semantics did change in 2.0.0** — see the note above. It carried a
`CHANGELOG` entry, which is what that commitment requires, but it is the kind of change to read for
before upgrading a subscriber.

If you need a new event surface that doesn't exist yet, open an issue at [github.com/otectus/runicskills/issues](https://github.com/otectus/runicskills/issues).

---

## Network protocol and packets

The mod syncs gameplay state to clients via a versioned custom Forge network channel. Clients on a
mismatched protocol version are refused at join with a clear error. The current protocol version is
**13** (since 2.1.0); the version matrix in [`../README.md`](../README.md#version-matrix) lists
which releases use which protocol.

### Protocol 13 (2.1.0): Mining prediction

`TConstructMiningCP` sends the server's temporary mining-speed bonus and its selected hotbar slot
to that player. The client uses the bounded snapshot for block-breaking prediction; it cannot
request or authorize a bonus. Protocol 12 peers are refused during channel negotiation.

### Protocol 12 (2.0.7+): Workshop networking

Three packets for Tinkers' Construct workshop focus, added in 2.0.7:

| Packet | Direction | Purpose |
|---|---|---|
| `WorkshopFocusSP` | Client → Server | Player requests to focus a controller, associate a casting block, or release their focus |
| `WorkshopStatusCP` | Server → Client | Server sends the player's current focus status (controller position, remaining time, bonus %, etc.), at most twice per second |
| `StationQuoteCP` | Server → Client | Server sends a preview of what the player would receive from the station they are looking at, only when inputs or result change |

**Validation rules for `WorkshopFocusSP` (server-side):**
- Sender must be a real player and rate-limited (one per ~250ms).
- The container ID must match the player's currently open menu.
- The revision token must match the player's current focus token.
- Only after token validation does the server check world state: distance (before chunk loads), chunk-loaded state (before Tinkers' type checks).
- A packet that names a coordinate outside chunk-loaded space is refused without the server ever asking whether that chunk should exist.

The two client-bound packets carry presentation data only — quotes and focus status — never authorization. Taking a crafted item or modifying a focus goes through the native menus and the server-authoritative control flow.

### Protocol evolution

| Version | Added | Removed | Changed |
|---|---|---|---|
| 13 | `TConstructMiningCP` | — | Temporary Tinkers' mining bonuses synchronized for client prediction |
| 12 | `WorkshopFocusSP`, `WorkshopStatusCP`, `StationQuoteCP` | — | — |
| 11 | `PowerOverridesSyncCP`, `PowerProcCP`, `PowerEquipSP` | — | `GameplayConfigCP` payload (1,133 fields generated instead of 128 hand-listed); `PassiveLevelUpSP`/`PassiveLevelDownSP` replaced by `AdjustPassiveSP` (batched, handles bulk clicks) |
| 10 | (see 2.0.0 release) | `CommonConfigSyncCP`, `DynamicConfigSyncCP` | `SyncSkillCapabilityCP` (resource locations are now length-bounded instead of 32,767-character strings) |
| 9 | — | — | `PassiveLevelUpSP`/`PassiveLevelDownSP` payloads (passive-level arrays moved from packed strings to length-prefixed varints) |
