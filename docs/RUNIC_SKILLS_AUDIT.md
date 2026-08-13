# Runic Skills Comprehensive Technical Audit

**Subject:** Runic Skills `1.6.1` — Minecraft 1.20.1 / Forge 47.3.0 / Java 17
**Mod ID:** `runicskills` · **Group:** `com.otectus` · **Licence:** Apache-2.0
**Audit date:** 13 August 2026
**Tree audited:** `C:\Projects\runic-skills` at commit `0b8be65` ("Release 1.6.1: hide disabled content from UI"), **plus the five uncommitted working-tree files** (`RunicSkillsClient.java`, `client/screen/RunicSkillsScreen.java`, `client/integration/L2TabsClientIntegration.java`, `integration/L2TabsIntegration.java`, `mixin/MixInventoryScreen.java`) — the audited content matches your working directory exactly.

**Method:** static analysis only. The tree was reconstructed into an isolated Linux workspace and read in full: every `.java` file under `src/main` and `src/test`, all resources, `build.gradle`, `gradle.properties`, `settings.gradle`, `mods.toml`, `pack.mcmeta`, `runicskills.mixins.json`, the CI workflow, and all repository documentation. Nine independent audit passes were run (persistence, networking, events/XP, progression, client/UI, configuration, integrations/mixins, build/tests/docs, cross-system), each producing evidence-backed findings, followed by a cross-system reconciliation pass. Nothing in the repository was modified.

**Not verified:** the build was not executed (`./gradlew` could not reach the Gradle distribution from the sandbox), no client or dedicated server was launched, and no runtime profiling was performed. Findings that depend on runtime behaviour are marked **Confidence: Medium** or **Low** and say so explicitly.

**Scale of the subject:** 244 Java source files, 34,469 lines of main source, 1,706 lines of test source across 20 JUnit 5 classes. 10 aptitudes, 471 perks, 39 passives, 75 powers, ~90 configurable titles, 18 network packets, 14 mixins, 145 `@SubscribeEvent` handlers across 36 classes, ~40 integrated third-party mods, 1,141 configuration fields, 2,061 English translation keys.

**Result:** 195 distinct findings — 4 Critical, 40 High, 5 Medium-High, 86 Medium, 5 Low-Medium, 52 Low, 3 Enhancement.

---

## 1. Executive Summary

### Overall code health

Runic Skills is a **large, ambitious, and unusually well-engineered mod in its core** that is being let down by its periphery. This is not a codebase that needs rescuing; it is a codebase that has grown past the point where its original patterns scale, and the seams are now visible in predictable places.

The parts that are genuinely good are good on purpose, not by accident. The capability lifecycle is textbook-correct in the two places most mods get wrong (`PlayerEvent.Clone` does `reviveCaps` → `copyFrom` → `invalidateCaps`; a single `EntityJoinLevelEvent` hook covers login, respawn *and* dimension change because `ServerLevel#addEntity` is the common funnel for all three). `FakePlayer` is excluded at capability-attach time, which structurally closes the entire "Create deployer farms my skill XP" bug class that plagues comparable mods. No client→server packet carries a UUID, entity ID, or target selector — every handler operates on `context.getSender()`, making cross-player action and entity-ID spoofing *impossible by construction* rather than merely validated away. `TogglePerkSP` performs nine independent server-side checks before committing and resyncs the client on every rejection path. The network protocol version has been bumped for payload-only changes, with inline comments explaining the rule. Pure gameplay mathematics has been deliberately extracted into Forge-free, unit-tested classes (`ExperienceMath`, `SkillLevelUpMath`, `PerkCapMath`, `PacketBounds`) so that client and server share one predicate that cannot drift. Two of the existing tests are source-scanning invariant guards — a sophisticated technique most mods never reach for — and they are why the texture audit came back with exactly **0 missing and 0 orphaned** textures across 561 files.

Against that, the mod carries three structural weaknesses that account for most of the serious findings, and a fourth that is a straightforward oversight.

### The most serious findings

**1. Four unbounded resource/progression faucets (Critical).** `LOCKSMITH` grants vanilla XP on *any* container open with no cooldown and no per-container state — and vanilla XP is the single authoritative currency for skill level-ups, so this is a direct progression bypass funded by spamming right-click on a crafting table. `SILK_TOUCH_MASTERY` pops the block item *in addition to* the vanilla drops without cancelling the break — a literal duplicator for every block in the game. `LUCKY_DROP` re-scans the world a tick after a mob death and multiplies the stack size of any freshly-spawned `ItemEntity` nearby, including a stack the player manually dropped. And a single malformed character anywhere in `runicskills.common.json5` causes `ConfigHolder.load()` to discard all 1,141 settings, run on defaults, and then **overwrite the operator's file with those defaults** — silent, total, and signalled only by one `WARN` line.

**2. Config-derived content in synced Forge registries (High, and the single highest-priority High).** All five custom registries (`perks`, `passives`, `powers`, `skills`, `titles`) are created with `disableSaving()` but never `disableSync()`. In Forge 1.20.1 a custom registry defaults to `sync = true`, so its contents participate in the login handshake — but 470 of 471 perks are registered conditionally on a `<name>RequiredLevel` value read from a local config file, every title is registered from `runicskills.titles.json5`, and Powers are skipped entirely when Iron's Spellbooks is absent. Neither config file reaches the client before the handshake. Any server that customises its titles or perk gates differently from its clients is a login failure or a silent ID remap.

**3. Server-authority gaps that are narrow but real.** `playersMaxGlobalLevel` — a limit operators explicitly tune through `/globallimit` — is enforced *only in the client GUI*. A patched client can walk past it to 352 global levels against a 256 cap, and because the perk budget scales from earned global level, also grant itself ~48 extra perk slots. Separately, title unlocks are monotonic with no re-lock branch, so the op-gated `administrator` title is permanently retained after de-op and `SetPlayerTitleSP` authorises off that stale persisted flag — a staff-impersonation vector on a public server, since `displayTitlesAsPrefix` defaults to true.

**4. Per-tick churn that is invisible in singleplayer and expensive at scale.** `TickEventHandler#onPlayerTick` unconditionally re-applies mob effects and rebuilds attribute modifiers *every tick, per player*, each producing a clientbound packet — roughly 40 packets/second/player for two perks that never change state. The same handler has no logical-side guard at all and mutates item stacks on the client. `PowerEventDispatcher#onPlayerTick` pushes a position snapshot into a `synchronized` static map every tick for every player regardless of whether they have any Power equipped, and schedules its AABB scans on `gameTime % N`, which synchronises every eligible player onto the same tick.

### Major architectural strengths

- **Sided discipline is a first-class concern**, not an afterthought. There is a custom Gradle task (`:checkSidedImports`) enforcing it, a mixin plugin gating optional-mod mixins, an `@OnlyIn(Dist.CLIENT)` nested-handler pattern in `PowerProcCP`, and a `ClientProxy` method-reference indirection for YACL. The dedicated-server classloading question — the classic reason Forge mods work in dev and die on servers — has clearly been thought about and mostly solved.
- **Optional-dependency isolation is well understood.** `RunicSkills.tryLoadIntegration(modid, FQCN)` behind `ModList.isLoaded`, the `RunicQuestBridge` NOOP facade, `ApothicAttributesIntegration` as a foreign-type-free holder, and the `LinkageError` quarantine around L2Tabs are all correct, deliberate patterns.
- **Server-side re-derivation is the default posture** for the mutation packets. The client's claim is generally only *which* thing, never *how much*.
- **The extracted math layer** is the right architectural instinct and should be extended, not reversed.
- **Documentation and changelog volume is high** — 430 KB of it — including an integration matrix, a smoke-test checklist, and a public API document. It is stale in places, but the habit exists.

### Major architectural weaknesses

- **`HandlerCommonConfig` is a 4,836-line class with 1,103 public mutable fields**, mirrored by hand into a 519-line positional wire format (`CommonConfigSyncCP`) with no test enforcing that the two agree in name, type, or order. Adding a config field is a four-place edit that is easy to half-complete.
- **The annotation that declares a constraint and the code that enforces it are two different systems.** `@IntField`/`@FloatField` (1,042 fields) drive the YACL UI; `@Clamp` (9 fields) is what actually runs on a server. 1,033 range-annotated fields reach runtime unvalidated.
- **Perk and Passive objects snapshot config values into `final` fields inside their `DeferredRegister` suppliers**, at mod construction. `Perk.getValue()` then memoises into `cachedValues` and never invalidates. Consequently the server→client config sync writes ~128 values into a POJO that the consuming objects stopped reading at boot, and `/skillsreload` cannot change a perk's magnitude or level gate.
- **Perk behaviour is metadata-in-registry, logic-everywhere-else.** 471 perks are pure metadata; their effects are scattered across `PerkEffectsHandler`, `CombatEventHandler`, `CraftingEventHandler`, `InteractionEventHandler`, `TickEventHandler`, 14 mixins and ~20 integration classes. Two declarative tables (`REDUCTIONS`, `ATTRS`) show the right direction; most perks predate them.
- **No save-schema version field exists anywhere.** Migration today is tag-type sniffing plus three hardcoded legacy key names. Both serialization directions iterate the *registry*, never the NBT, so any key whose registry entry is currently absent is silently dropped on the next autosave — and since titles come from an editable config file, an operator removing a title destroys every player's record of having earned it.

### Multiplayer readiness

**Good, with two specific holes.** Per-player state is uniformly keyed by UUID; no `Player` object is retained in a static field except one `ThreadLocal` memo; cross-player contamination is structurally prevented on the packet layer. The two holes are the `playersMaxGlobalLevel` client-only enforcement and the monotonic `administrator` title. A third, `ApotheosisIntegration.resolveInteractor`, compares `level.getGameTime()` against `server.getTickCount()` — two unrelated clocks — so on any world past its first restart the attribution window never closes and gem socketing is credited to whoever last right-clicked *any block anywhere* since boot.

### Dedicated-server readiness

**Likely fine today; fragile by construction.** Seven server→client packet classes reference `net.minecraft.client.*` inside their handler bodies. This does *not* break a dedicated server in Forge 1.20.1 — every assignment is type-identical, so the JVM's split verifier never needs a subtype check and never resolves the client class, and `RuntimeDistCleaner`'s trap is never sprung. But that safety is a property of the current bytecode shape, not of the design: one added branch merging two client types, or one client-typed field, converts it into a total server-boot failure whose stack trace points at `ServerNetworking.init`, far from the edit that caused it. More concerning, `Title` and `Perk` — both common-package classes — depend on `client.core.Utils`, whose static initialiser calls `Minecraft.getInstance()`. **CI has no dedicated-server boot smoke test**, and the project's own `SMOKE_TESTS.md` records that server-side classloading is its worst historical regression class.

### Modpack compatibility

**Cooperative in intent, occasionally proprietary in practice.** The mod integrates ~40 mods and mostly does so respectfully. The compatibility risks are concentrated in three mixins that cancel-and-reimplement vanilla methods: `MixLivingEntity` rebuilds every player `MobEffectInstance` through the 3-argument constructor, silently discarding `ambient`, `visible`, `showIcon`, `hiddenEffect` and Forge's `curativeItems`, and substitutes the affected player for the real effect source; `MixCraftingMenu` empties the crafting result *after* vanilla has already sent the slot packet, producing a desync; `MixForgeGui` cancels `renderAir` unconditionally and *assigns* rather than increments Forge's shared `rightHeight` HUD cursor, displacing every other mod's overlay. `GenericNamespaceLockProvider` classifies other mods' entire item registries by substring match — Aquaculture fishing rods are auto-gated behind the Magic aptitude. And `mods.toml` declares 7 optional dependencies for ~40 integrated mods, so load ordering that the code comments explicitly depend on is currently working by alphabetical accident.

### Performance outlook

**Correct but wasteful, and the waste scales with the wrong variables.** Nothing runs off the main thread (verified — this is a genuinely clean result). The costs are: unconditional per-tick effect and attribute re-application (~40 packets/s/player); `Perk.isEnabled()` building a `modid:path` string before it can short-circuit, called ~100× per melee hit; 19 `LivingHurtEvent` listeners registered by this mod alone; `CLEAVE` re-entering `hurt()` from *inside* a `LivingHurtEvent` handler with the full outgoing perk stack applied to each splash target; `DrawTabs.render` allocating two `Screen` objects, a `RecipeBookComponent` and a GameProfile-tagged `ItemStack` **every frame**; and five registry caches memoised for the JVM lifetime. In a 300-mod pack with 20+ players these compound.

### Maintainability outlook

**The two god-files are less of a problem than they look; the config sync is more of one.** `RegistryPerks` (4,419 lines, 472 registrations) is repetitive but mechanically uniform and is guarded by three source-scanning tests — splitting it would add risk without adding safety, and this audit explicitly recommends against it. `HandlerCommonConfig` is the real debt, not because of its size but because of the hand-mirrored wire format and the 1,033-field validation gap. The highest-leverage maintainability investment in this codebase is a single reflection-based test asserting that `CommonConfigSyncCP` and `HandlerCommonConfig` agree.

---

## 2. Project Architecture

### 2.1 Entry points and lifecycle

`RunicSkills` (`@Mod("runicskills")`, 12 KB) is the single entry point. In its constructor it: loads the four JSON5 config holders through `Configuration`; registers all five `DeferredRegister`s plus items, effects, attributes, sounds and command arguments onto the mod event bus; calls `ServerNetworking.init()`; instantiates `RegistryCommonEvents`; and reflectively loads each integration through `tryLoadIntegration(modid, FQCN)` guarded by `ModList.isLoaded`. `RunicSkillsClient` holds the client-side setup, keybind registration and GUI overlay registration behind `Dist.CLIENT`-gated `@EventBusSubscriber` classes.

Event registration deliberately uses **both** mechanisms and documents why: `@Mod.EventBusSubscriber` registers only `static` handlers (via `EventBus.registerClass`) while `MinecraftForge.EVENT_BUS.register(instance)` registers only non-static ones (via `registerObject`). The two sets are disjoint, so nothing double-fires — a trap most mods fall into, correctly reasoned through here (`RegistryCommonEvents` lines 30–36).

### 2.2 Package structure

```
com.otectus.runicskills
├── RunicSkills.java / RunicSkillsClient.java   entry points
├── registry/            5 custom Forge registries + attributes/effects/items/sounds/tags
│   ├── events/          7 handlers — the gameplay engine (3,367 LOC)
│   ├── perks/ passive/ powers/ skill/ title/   definition types + datapack reload listeners
├── common/
│   ├── capability/      SkillCapability (27 KB) — all persistent player state
│   ├── command/         9 commands + 2 Brigadier argument types
│   ├── powers/          PowerRuntime — transient Powers state services
│   └── util/            Forge-free, unit-tested math (ExperienceMath, PerkCapMath, …)
├── network/             SimpleChannel, rate limiter, 11 S2C + 7 C2S packets
├── config/              JSON5 holder, @Clamp, condition types, models, YACL controllers
├── handler/             10 config/handler classes incl. HandlerCommonConfig (236 KB)
├── integration/         35 mod integrations + lock providers + FTB Quests bridge
├── mixin/               14 mixins + RunicSkillsMixinPlugin
├── client/              screens, HUD overlays, tooltips, client config UI
├── event/               4 public Forge events exposed to other mods
└── kubejs/              KubeJS plugin + scriptable events
```

### 2.3 The five subsystems

**Skills (Aptitudes).** Ten hardcoded `Skill` objects in a custom registry. A `Skill` carries an index, a four-entry locked-icon array, a background texture and an optional datapack `SkillVisuals` override. Skills have **no behaviour** — they are a grouping key plus a level counter stored in `SkillCapability.skillLevel` (`Map<String,Integer>`, keyed by registry *path*, seeded to 1).

**Perks.** 471 `Perk` objects, all single-rank in practice despite rank machinery existing. A `Perk` is pure metadata: key, skill supplier, `requiredLevel`, texture, and a `Value[]` of config values *snapshotted at registration*. `Perk#isEnabled(player)` is the universal gate and re-checks `isDisabled` + skill level + rank ≥ 1 on every call, so perks self-heal on skill loss. Activation is constrained by four independent mechanisms — `maxActivePerks`/`perksPerGlobalLevel` via `PerkCapMath.computeEffectiveCap`, the school-attunement cap, datapack `PerkGroup`s, and a perk-swap cooldown — all enforced **only** in `TogglePerkSP` on the 0→1 transition.

**Passives.** 39 `Passive` objects (some conditional on optional mods), each binding one vanilla or modded `Attribute`, a hardcoded UUID string, a config value and a `levelsRequired[]` array. The single applier is `RegistryAttributes.modifierAttributes(ServerPlayer)`, which iterates all passives and does remove-by-UUID then `addPermanentModifier` with `Operation.ADDITION` and value `configValue / levels * playerLevel`.

**Powers.** 75 `Power` objects (45 Iron's-Spellbooks-school + 30 cross-cutting) in a fifth registry. Equipped slots live on the capability as three fields (5 Marks / 3 Seals / 1 Crown). All behaviour is in one 1,063-line `PowerEventDispatcher`, registered **only when Iron's Spellbooks is present**. Transient runtime state (spell history, proc windows, internal cooldowns, a position ring buffer) lives in static maps in `PowerRuntime`. Datapack `PowerOverrides` can retune level gate, ICD and arbitrary named values.

**Titles.** Registered from `runicskills.titles.json5` at mod construction. Each `TitleModel` holds `type/variable/comparator/expected` condition strings resolved through a `HandlerConditions` factory map into per-title `ConditionImpl` instances. Evaluation is a two-call stateful protocol (`ProcessVariable` then `MeetCondition`) run by `RegistryTitles.serverPlayerTitles` on join, clone, passive level-up, and **every 200 ticks per player**. Unlocks are monotonic. The selected title is projected onto the player through both `setCustomName` and a `PlayerEvent.NameFormat` prefix.

**Prerequisite structure.** There is no perk tree — the only gate is a flat skill level. The only declared prerequisite relationships are the Power tier rules (Seal ⇒ same-school Mark, Crown ⇒ same-school Seal), which are documented but **not implemented**. There are therefore no cycles, orphans or recursive traversals to audit, and no startup validation of level gates against `skillMaxLevel`.

### 2.4 Persistence

A single Forge capability, `RegistryCapabilities.SKILL`, registered via `RegisterCapabilitiesEvent` and attached in `AttachCapabilitiesEvent<Entity>` to every `Player` that is not a `FakePlayer`, under `runicskills:skills`. The provider is `LazySkillCapability` (`ICapabilitySerializable<CompoundTag>`) wrapping one eagerly-constructed `SkillCapability`. Forge's `CapabilityDispatcher` calls `serializeNBT`/`deserializeNBT` as part of the player's `ForgeCaps` tag inside `playerdata/<uuid>.dat`, so save/load is entirely vanilla-driven and identical on dedicated and integrated servers.

`SkillCapability` holds four registry-shaped maps (11 skills, 39 passives, 472 perks, ~79 titles), a selected-title string, a generic `perkCooldowns` map, and the Powers block. **Serialization is registry-driven in both directions** and keys are `"<kind>." + entry.getName()`, where `getName()` returns the ResourceLocation **path only**. There is no data-version field and no orphan retention.

### 2.5 Execution flows

The primary progression flow:

```
Player action (block break / kill / craft / interact)
  → Forge event on the server (BlockEvent.BreakEvent, LivingHurtEvent, ItemCraftedEvent, …)
  → registry/events/* handler  →  Perk#isEnabled(player) gate  →  effect applied
  → (some paths) giveExperiencePoints  →  vanilla XP is the progression currency
```

The level-up flow is **client-initiated, server-authoritative**:

```
RunicSkillsScreen click
  → SkillLevelUpSP (C→S; payload = skill NAME only)
  → PacketRateLimiter.allow(player, "skill_level_up", 2)
  → server re-derives cost via ExperienceMath.requiredPoints and balance via spendableXp
  → SkillLevelUpEvent (cancellable, public API)
  → capability.addSkillLevel  →  RegistryAttributes.modifierAttributes  →  RegistryTitles.serverPlayerTitles
  → SyncSkillCapabilityCP (S→C, PacketDistributor.PLAYER, full serialized tag)
  → client deserializes into its mirror capability  →  HUD/GUI read SkillCapability.getLocal()
```

The join flow:

```
PlayerLoggedInEvent      → ConfigSyncCP + DynamicConfigSyncCP + CommonConfigSyncCP
                           + PerkGroupsSyncCP + PowerOverridesSyncCP
EntityJoinLevelEvent     → RegistryAttributes.modifierAttributes
  (login, respawn AND      + RegistryTitles.serverPlayerTitles
   dimension change)       + FTB Quests bridge refresh
                           + SyncSkillCapabilityCP
PlayerEvent.Clone        → reviveCaps → copyFrom → invalidateCaps
```

### 2.6 Networking

One `SimpleChannel` (`ServerNetworking`), `PROTOCOL_VERSION = "8"`, 18 registered messages — 11 server→client, 7 client→server — each with an explicit `NetworkDirection`, which is what makes it impossible for a client to invoke a client-side handler on the server. Every handler uses `context.enqueueWork` and `setPacketHandled(true)`. Client→server packets are rate-limited per player per packet-type by tick delta.

### 2.7 Configuration

Four JSON5 files under `config/RunicSkills/` (`common` — 1,141 fields, `lockItems` — 435 default entries, `titles` — ~90, `convergence-items` — 46), each owned by a `ConfigHolder<T>` bound to a plain-POJO class, plus one genuine Forge `ModConfig.Type.CLIENT` TOML spec (`HandlerConfigClient`, 7 options — the cleanest config surface in the mod). The whole design is an **ex-YACL system**: `ConfigHolder`'s javadoc documents honestly that YACL's `ConfigClassHandler` static initialiser crashed dedicated servers and was replaced with plain Gson, keeping the field annotations. That migration is the origin of most of the configuration findings in this report — the annotations describing the constraints and the code enforcing them became two different systems, and only one of them runs on a server.

### 2.8 Public API surface

Four Forge events on the main bus (`SkillLevelUpEvent`, `PassiveLevelUpEvent`, `PerkToggleEvent` with Pre/Post, `TitleEarnedEvent`), the `RegistryCapabilities.SKILL` capability, five queryable custom registries, a KubeJS plugin exposing a level-up event, and FTB Quests task types. `docs/API_EVENTS.md` documents this and makes a stability commitment.

---

## 3. Critical Findings

### [RS-001] `LOCKSMITH` awards vanilla XP on `PlayerContainerEvent.Open` — an unlimited, zero-cost XP farm, and XP is the skill-progression currency

**Severity:** Critical | **Confidence:** High | **Impact:** Critical | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java — `CraftingEventHandler#onContainerOpen` (lines 107–122, grant at line 118)

**Problem**

Every `PlayerContainerEvent.Open` rolls `nextInt(locksmithProbability)` (default `4`) and on `0` calls `serverPlayer.giveExperiencePoints(5 + safeCrackerAmplifier)`. `PlayerContainerEvent.Open` fires for *any* menu the player opens — chest, barrel, ender chest, furnace, crafting table, villager trade — with no cooldown, no per-container state, and no "first open" tracking. 

**Why it matters**

`ExperienceMath`/`SkillLevelUpSP` make vanilla XP points the single authoritative currency for skill level-ups, so this is not just a cosmetic XP faucet, it is a direct progression bypass. 

**Failure scenario**

A player stands at a crafting table and spams right-click; at a conservative 4 opens/sec that is 1 open × 25% × 5 XP = ~5 XP/sec, ~300 XP/min, from nothing. With a hold-right-click macro it is orders of magnitude more. Level 32 in every skill costs ~2 200 XP points per level (`skillFirstCostLevel=5`, `skillMaxLevel=32`), which this trivially funds. Every other XP grant in the mod is bounded by a consumed resource (grindstone disenchant, fishing catch, mob kill); this one is not. 

**Fix**

Track opened container positions per player (or use `BlockPos` + a cooldown map) so each distinct container pays out at most once per N minutes, or move the reward to a genuinely consuming action (unlocking a locked container).

### [RS-002] `SILK_TOUCH_MASTERY` drops the block *in addition to* its normal drops — unbounded duplication of any block

**Severity:** Critical | **Confidence:** High | **Impact:** High | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java — `PerkEffectsHandler#onBlockBreak` (lines 537–540)

**Problem**

On `BlockEvent.BreakEvent` (which fires *before* the block is removed and before vanilla drops are computed) the handler calls `Block.popResource(level, pos, new ItemStack(state.getBlock()))` and then returns without cancelling or suppressing the vanilla break. The player receives both the silk-touch item and the normal loot. 

**Why it matters**

This is a literal item duplicator for every block in the game, not just ores. 

**Failure scenario**

Mine a diamond ore → receive 1 diamond ore block *and* 1 diamond; place the ore back and repeat indefinitely. With deepslate/redstone/lapis the ratio is worse. Even for plain stone, break→get stone item + cobblestone. 

**Fix**

Only pop the block item when the vanilla drop is suppressed — cancel the event and call `level.removeBlock(pos, false)` yourself, or gate on `state.getBlock().asItem()` differing from the normal drop and roll it as a *replacement* rather than an addition.

### [RS-003] `LUCKY_DROP` multiplies the stack size of *any* freshly-spawned item entity near the corpse — item duplication

**Severity:** Critical | **Confidence:** Medium | **Impact:** High | **Effort:** Moderate

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java — `CraftingEventHandler#onEntityDrops` (lines 161–199)

**Problem**

The deferred task scans `level.getEntities(null, AABB(pos±1))`, filters to `ItemEntity` with `tickCount <= 1`, and for anything not reference-equal to an entry in the mob's `getAllSlots()` list does `itemStack.setCount(itemStack.getCount() * value[1])`. There is no check that the item entity actually came from this mob's death. Also note `equipment.contains(itemStack)` relies on `ItemStack` reference identity (`ItemStack` does not override `equals`), so the exclusion is fragile. 

**Why it matters**

Any item on the ground within a 2×2×2 box that was spawned in the same or previous tick gets its count multiplied. 

**Failure scenario**

Player holds a stack of 64 diamonds, presses Q to drop it at the exact moment a mob dies at their feet with `LUCKY_DROP` procing → 64 becomes 64 × `value[1]`. This is reproducible with modest timing and trivially automatable. 

**Fix**

Capture the identity of the `ItemEntity` objects the mod itself created from `event.getDrops()` (they are available directly on the event) and multiply only those, instead of re-scanning the world a tick later.

### [RS-004] One bad character resets all 1141 settings to defaults and overwrites the file

**Severity:** Critical | **Confidence:** High | **Impact:** A pack's entire balance silently reverts on a typo | **Effort:** M

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java#load` (~line 95–115)

**Problem**

Parsing is all-or-nothing. Any `JsonSyntaxException` — wrong scalar type, trailing comma in an object, a `NaN`/hex literal, a mangled string — sends control to the `catch`, which logs a single `WARN`, copies the file to `<name>.invalid`, and then **writes the defaults over the original file** (line 108–111). Verified failures include:

```
{"skillMaxLevel":"abc"}      -> NumberFormatException  -> full reset
{"skillMaxLevel":true}       -> Expected NUMBER but was BOOLEAN -> full reset
{"disabledPerks":"berserker"}-> Expected BEGIN_ARRAY   -> full reset
{"a":NaN} / {"a":0x10}       -> NumberFormatException  -> full reset
```

**Why it matters**

`runicskills.common.json5` is a single 1141-key document. There is no per-section isolation, so a typo in one Iron's-Spellbooks field discards the pack's `skillMaxLevel`, `disabledPerks`, lock master toggle and every other tuning. On a headless server the only signal is one WARN line, and by the time anyone notices, the file on disk is defaults. The mod's *own* datapack listeners do this correctly — `PowerOverridesReloadListener.apply` (~line 37–45) has a per-file try/catch precisely so "one bad file doesn't block the others".

**Failure scenario**

Admin edits `"ironsSpellPowerValue": 2,5` (European decimal comma) on a live server, restarts. Server boots with stock balance; the admin's `.invalid` backup exists but nothing in-game or in chat says so.

**Fix**

Parse to a `JsonObject` first, then bind field-by-field (`Gson.fromJson(element, field.getGenericType())` per key inside a try/catch), so a bad key falls back to its default and logs *that key*, leaving the other 1140 intact. Do not overwrite the source file when parsing failed — keep the broken file in place and run on defaults for the session, so the operator's edits aren't destroyed. Escalate the log to `ERROR` and, on a server, also surface it to ops on join.

---


---

## 4. High Priority Findings

### [RS-005] No save-schema version field anywhere in the capability NBT

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Small

**Affected code:**
- common/capability/SkillCapability.java — serializeNBT (~462)

**Problem**

Writes flat `skill.*`/`passive.*`/`perk.*`/`title.*`/`power*` keys, no `dataVersion`. Only migration is tag-type sniffing (TAG_BYTE -> legacy boolean perk, ~522) and 3 hardcoded legacy key names (counterAttackTimer, counterAttack, limitBreakerCooldown, ~548-558). No hook for future renames/unit changes.
Why: pack updating to a version that renames a perk or changes stored units has no way to distinguish old data or run a one-shot fixup.
Scenario: 1.7.0 changes perkRank from "rank index" to "points spent"; every save silently reinterprets rank 3 as 3 points; players over-budget; TogglePerkSP freezes them.

**Fix**

`nbt.putInt("dataVersion", CURRENT)`, read at top of deserializeNBT (absent=0), ordered List<Migration>. Keep byte/int sniffing as migration 0->1.

### [RS-006] Unknown NBT keys silently discarded on next save (no orphan pass-through)

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Moderate

**Affected code:**
- common/capability/SkillCapability.java — deserializeNBT/serializeNBT (~467-537)

**Problem**

Both directions iterate the REGISTRY, never the NBT. Any key whose registry entry is absent is never read and never re-written; first autosave erases it.
Why: Titles are registered FROM A CONFIG FILE (TitleModel#registry via RegistryTitles#load), so an operator editing titles.json5 destroys player data. Same for a temporarily-removed addon.
Scenario: admin removes custom title `dragonslayer` to test, restarts, players log in/out once, admin re-adds -> everyone who earned it is locked again with no record.

**Fix**

Retain unknown keys in a `CompoundTag unknownTags` and `nbt.merge(unknownTags)` at end of serializeNBT (registry-owned keys written after so they win). Bound it with a timestamp.

### [RS-007] Attached LazyOptional never invalidated — stale capability survives death/dimension change

**Severity:** High | **Confidence:** Medium | **Impact:** High | **Effort:** Trivial

**Affected code:**
- common/capability/LazySkillCapability.java (~14-30)

**Problem**

`LazyOptional.of(this::createPlayerAbility)` created once; no `invalidate()`; PlayerLifecycleHandler#onAttachCapabilitiesPlayer (~100-106) never calls `event.addListener(...)`. invalidateCaps() stops NEW getCapability calls resolving but an already-handed-out LazyOptional keeps resolving to the dead player's SkillCapability forever.
Why: caching the LazyOptional per entity is the canonical Forge hot-path idiom, and the mod exposes RegistryCapabilities.SKILL publicly plus PerkToggleEvent/SkillLevelUpEvent/TitleEarnedEvent, inviting addons to do exactly that.

**Fix**

Add `public void invalidate() { optional.invalidate(); }` and `event.addListener(p::invalidate)` in the attach handler.

### [RS-008] Server-side skill level-up never enforces playersMaxGlobalLevel — global cap is client-only

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Trivial

**Affected code:**
- network/packet/common/SkillLevelUpSP.java — handle (~57)

**Problem**

Handler re-derives per-skill cap and XP affordability only. The only playersMaxGlobalLevel check in the codebase is in the client GUI (RunicSkillsScreen:467, :971) and in the op-only /globallimit command that SETS the value — so it is clearly an intended server-authoritative limit.
Why: the cap operators tune to bound total player power is enforced by the client. Worse, RegistryPerks.effectivePerkCap derives the scaled perk budget from getEarnedGlobalLevelForPerkBudget(), so exceeding the global cap also inflates the active-perk allowance when perksPerGlobalLevel > 0.
Scenario: defaults 11 skills x skillMaxLevel 32 = 352 possible vs playersMaxGlobalLevel 256. A patched client keeps sending SkillLevelUpSP past 256; each passes (skill<32, XP affordable). Reaches 352 global levels — 96 past the cap — plus ~48 extra perk slots at 0.5/level.

**Fix**

After the null guard add `if (capability.getGlobalLevel() >= cfg.playersMaxGlobalLevel) { SyncSkillCapabilityCP.send(player); return; }`. Better: extract into SkillLevelUpMath so client and server share one unit-tested predicate, as canAfford already is.

### [RS-009] Per-tick `addEffect` re-application sends a mob-effect packet every tick, per player

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/TickEventHandler.java — `TickEventHandler#onPlayerTick` (lines 64–69)

**Problem**

`new RegistryEffects.AddEffect(serverPlayer, CAT_EYES.isEnabled(player), NIGHT_VISION).add(210)` runs on **every** tick. `RegistryEffects.AddEffect.add` unconditionally builds a `MobEffectInstance(duration=210)` and calls `player.addEffect`. In 1.20.1, `LivingEntity.addEffect` on an existing instance calls `MobEffectInstance.update`, which returns `true` whenever `this.duration < other.duration` — always true here, because the stored instance decayed to 209 last tick. `update` returning true invokes `onEffectUpdated`, and `ServerPlayer.onEffectUpdated` sends a `ClientboundUpdateMobEffectPacket`. 

**Why it matters**

This is an unconditional 20 packets/sec/player for `CAT_EYES` and another 20 for `DIAMOND_SKIN` (line 67), plus a `MobEffectInstance` allocation each. 

**Failure scenario**

On a 100-player server where both perks are common, ~4 000 effect packets/sec are emitted for state that never changes; on a poor connection the player's effect HUD also flickers. 

**Fix**

Only re-apply when the effect is absent or nearly expired — `MobEffectInstance cur = player.getEffect(effect); if (cur == null || cur.getDuration() < 40) player.addEffect(...)`.

### [RS-010] Per-tick attribute modifier churn marks `ARMOR`/`ATTACK_DAMAGE` dirty every tick and writes *permanent* (NBT-persisted) modifiers

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/TickEventHandler.java — `TickEventHandler#onPlayerTick` (lines 54–59)

*Independently corroborated in the progression pass.*

**Problem**

`RegistryAttributes.RegisterAttribute#amplifyAttribute` (RegistryAttributes.java:97–110) unconditionally does `removeModifier(old)` then `addPermanentModifier(new)` with no "value unchanged" short-circuit; `TickEventHandler` calls it every tick for `ONE_HANDED` (ATTACK_DAMAGE) and `DIAMOND_SKIN` (ARMOR). Both `removeModifier` and `addModifier` call `AttributeInstance.setDirty()`. `ARMOR` is client-syncable in vanilla, so `ServerPlayer` drains `getDirtyAttributes()` each tick and emits a `ClientboundUpdateAttributesPacket`. 

**Why it matters**

This is a guaranteed per-tick attribute packet per player with the perk active, on top of the effect packets above, plus a per-tick `AttributeModifier` allocation and an invalidated attribute-value cache. `addPermanentModifier` also means the modifier is serialised into player NBT on every world save. 

**Failure scenario**

A player sneaking with `DIAMOND_SKIN` generates 20 attribute-update packets/sec forever; if they log out while sneaking, the +armor modifier is persisted and applied on next login before the first tick reconciles it. Contrast with `PerkEffectsHandler.onAttributeTick` (line 341), which correctly does an idempotent compare-then-skip and uses `addTransientModifier`. 

**Fix**

Give `RegisterAttribute.amplifyAttribute` the same `existing != null && existing.getAmount() == amount → return` guard, use `addTransientModifier`, and throttle the caller to every 10–20 ticks like the other reconcilers.

Severity: Medium | Confidence: High | Impact: Per-player, per-tick attribute resync | Effort: S
File: `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/events/TickEventHandler.java` — `TickEventHandler#onPlayerTick` (lines 54-59)

Problem: `RegisterAttribute#amplifyAttribute` (RegistryAttributes.java:101-109) unconditionally does
`removeModifier(old)` then `addPermanentModifier(new)` — there is no "unchanged, skip" check. It is
invoked twice per player **per tick** for `ONE_HANDED` (ATTACK_DAMAGE) and `DIAMOND_SKIN` (ARMOR).
Both `AttributeInstance.removeModifier` and `addPermanentModifier` call `setDirty()`, which enqueues
the attribute into `AttributeMap#getDirtyAttributes`; `ServerEntity` then ships a
`ClientboundUpdateAttributesPacket` every tick.

Why it matters: 20 attribute-recomputes and 20 packets per second per online player, for state that
changes maybe once a minute. `PerkEffectsHandler#onAttributeTick:342` already implements the correct
pattern (`if (existing != null && existing.getAmount() == amount) continue;`) and even documents why.

Failure scenario: 40-player server → ~1600 redundant attribute packets/second plus the associated
`getValue()` cache invalidation on ATTACK_DAMAGE (read on every swing).

Fix: Port the amount-equality short-circuit into `amplifyAttribute`, and throttle the whole block to
`tickCount % 5` or drive it off equipment/pose change events.

---

### [RS-011] `COUNTER_ATTACK` grants a permanent, never-expiring ATTACK_DAMAGE bonus — `setCounterAttack(true)` is a no-op

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/common/capability/SkillCapability.java — `SkillCapability#setCounterAttack` (~line 150)

**Problem**

`public void setCounterAttack(boolean set) { if (!set) setCooldown(COOLDOWN_COUNTER_ATTACK, 0); }` — passing `true` does nothing, so `getCounterAttack()` (which reads `getCooldown(COOLDOWN_COUNTER_ATTACK) > 0`) is permanently `false`. `CombatEventHandler#onAttackEntity` (CombatEventHandler.java:315–321) sets the flag, then applies `RegisterAttribute(player, ATTACK_DAMAGE, sourceDamage * pct/100, COUNTER_ATTACK_UUID).amplifyAttribute(true)` — an `addPermanentModifier`. The two consumers that would clear it, `TickEventHandler#onPlayerTick` line 43 (`if (provider.getCounterAttack())`) and `CombatEventHandler#onPlayerAttackEntity` line 163, both gate on the always-false flag and never run. Secondary bug: even if the flag worked, `setCounterAttackTimer(timer + 1)` (TickEventHandler:44) is stored in the same `perkCooldowns` map that `provider.tickCooldowns()` (line 52) decrements by 1 on the same tick — the timer would be pinned and never reach the expiry threshold. 

**Why it matters**

The perk is described as a short retaliation window; it is actually a permanent, NBT-persisted attack-damage buff that refreshes to the value of whatever last hit you. 

**Failure scenario**

Player takes one hit from a Wither (high `ATTACK_DAMAGE`), gains a large permanent `ATTACK_DAMAGE` modifier, logs out and back in with it still applied, and nothing in the codebase can ever remove it. 

**Fix**

Make `setCounterAttack(true)` set a real cooldown (`setCooldown(COOLDOWN_COUNTER_ATTACK, windowTicks)`), stop storing the timer in the auto-decrementing cooldown map, and use `addTransientModifier`.

### [RS-012] `DOUBLE_DOWN` duplicates the drops of *every* block, with no player-placed-block check

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Moderate

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java — `PerkEffectsHandler#onBlockBreak` (line 532)

**Problem**

`oreDrop(player, level, pos, state, tool, true, RegistryPerks.DOUBLE_DOWN, c.doubleDownPercent)` passes `condition = true`, so unlike every sibling call (which requires `isOre`, `getY() < 0`, etc.) it applies to any block. `oreDrop` re-runs `Block.getDrops(...)` and pops a full extra copy. 

**Why it matters**

Combined with a place-and-break loop this is a self-sustaining resource multiplier for stackable blocks. 

**Failure scenario**

A player with `DOUBLE_DOWN` at 20% places and breaks the same cobblestone repeatedly; expected yield per cycle is 1.2 cobblestone from an input of 1 — net-positive, so an AFK/auto-clicker setup produces unbounded material. 

**Fix**

Restrict `DOUBLE_DOWN` to `isOre` like its neighbours, and/or tag player-placed blocks (`BlockPlaceEvent` → block-entity/chunk NBT marker) and skip bonus drops for them.

### [RS-013] `VEIN_MINER` uses `Level.destroyBlock`, which fires no `BlockEvent.BreakEvent` — bypasses land-claim/protection mods and loses Fortune/Silk Touch

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java — `PerkEffectsHandler#veinMine` (line 563)

**Problem**

The flood-fill calls `level.destroyBlock(n.immutable(), true, player)`. In 1.20.1 `Level.destroyBlock` does not post `BlockEvent.BreakEvent`, does not consult `ForgeHooks.onBlockBreakEvent`, and calls `Block.dropResources(state, level, pos, be, entity, ItemStack.EMPTY)` — with an **empty tool**. 

**Why it matters**

(a) protection mods (FTB Chunks, GriefPrevention, Apotheosis, spawn protection, `/gamerule` checks) hook `BreakEvent` and are therefore completely bypassed for the 47 cascaded blocks; (b) because the tool is `ItemStack.EMPTY`, the loot table sees no Fortune and no Silk Touch, so a Fortune III pickaxe silently yields base drops on every vein-mined block; (c) `Stats.BLOCK_MINED` is never awarded, so the mod's own `BlockMinedCondition` title requirements under-count. 

**Failure scenario**

A player stands one block outside a claim boundary, breaks an ore that is connected to a vein inside the claim, and mines 47 protected blocks that the claim mod never sees. 

**Fix**

Route each cascaded break through `ServerPlayerGameMode.destroyBlock` or at minimum post `new BlockEvent.BreakEvent(level, pos, state, player)` and abort on cancel; pass the real `tool` to `Block.dropResources`.

### [RS-014] `CLEAVE` re-enters `hurt()` inside a `LivingHurtEvent` handler, and its splash hits receive the full outgoing perk stack

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Moderate

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java — `CombatEventHandler#onLivingHurtStrengthAttacker` (lines 553–571)

*Independently corroborated in the integrations/mixins pass.*

**Problem**

At `EventPriority.NORMAL`, inside the dispatch of a `LivingHurtEvent`, the handler does `player.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(cleaveRangeBlocks))` and calls `other.hurt(playerAttack, splash)` for each. The `CLEAVING` guard is checked only by *this* method — `PerkEffectsHandler.onOutgoingDamage` (line 588) has no such guard, so every splash hit re-runs the entire outgoing bonus table (`STRATEGIC_MIND`, `BRUTAL_SWING`, …), `BLOOD_FURY` life-steal, the unconditional `TRACKING` glowing effect, and a `CHAIN_LIGHTNING_STRIKE` roll that can spawn a `LightningBolt` entity. 

**Why it matters**

One swing fans out into N nested `hurt()` calls, each dispatching all 19 `LivingHurtEvent` listeners this mod registers plus every listener from the other 299 mods. Nested re-entrant damage from inside a damage event is also a well-known source of incompatibility with combat overhauls and ward/absorption mods that keep per-hit state. 

**Failure scenario**

A player swings into a 20-mob spawner room. 20 splash `hurt()` calls × ~19 mod listeners each, 20 `BLOOD_FURY` heals off splash damage, 20 `GLOWING` effect packets, and up to 20 lightning bolt entities spawned in a single tick — a multi-hundred-millisecond tick spike. 

**Fix**

Check `CLEAVING` in `onOutgoingDamage` too (or move the guard into a shared helper), defer the splash to the next tick via `server.submit(new TickTask(...))` so it is not re-entrant, and cap the number of splashed targets.

Severity: Medium | Confidence: Medium-High | Impact: Grief, conflicts with protection mods | Effort: Low
File: src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java — `#onOutgoingDamage` CLEAVE block (~line 552-572)

Problem: `player.level().getEntitiesOfClass(LivingEntity.class, box)` → `other.hurt(...)` for every
entity that is not the target, not the player, and not `isAlliedTo`. There is no check for
`other instanceof Player` (PvP off), for tamed/owned entities, for `Entity#isAttackable`, or for
`ForgeHooks.onPlayerAttackTarget` / `AttackEntityEvent`.

Why it matters: `isAlliedTo` only covers scoreboard teams. Villagers, other players' pets, passive
livestock and other players in a claim are all valid splash targets, and protection mods that hook
`AttackEntityEvent`/`PlayerInteractEvent` never see the splash hits.

Failure scenario: A player with Cleave fights a mob next to a villager trading hall inside a
protected FTB Chunks claim. Every villager in a `cleaveRangeBlocks` radius takes damage; the claim
mod never gets a chance to veto because the hits go straight to `LivingEntity#hurt`.

Fix: Skip `Player` targets unless `level().getServer().isPvpAllowed()`, skip `TamableAnimal` with
an owner other than the attacker, and post `AttackEntityEvent` (or use
`player.attack(entity)` semantics) so protection mods can cancel.

Related: `veinMine` (PerkEffectsHandler.java:~549-568) calls `level.destroyBlock(pos, true, player)`
up to 48 times without firing `BlockEvent.BreakEvent` per block — claim/protection mods that gate on
`BreakEvent` will not see the cascade.

---

### [RS-015] Config-derived content lives in *synced* Forge registries — client/server registry mismatch on login

**Severity:** High | **Confidence:** Medium-High | **Impact:** Players cannot join | **Effort:** M

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryPerks.java` — `RegistryPerks` (line 32); same at `RegistryPassives.java:36`, `RegistrySkills.java:24`, `RegistryTitles.java:31`, `RegistryPowers.java:46`

*Independently corroborated in the config, integrations/mixins, persistence passes.*

**Problem**

All five custom registries are built with `new RegistryBuilder<T>().disableSaving()` and
**never** call `disableSync()`. In Forge 1.20.1 a custom registry defaults to `sync = true`, so its
contents participate in the login handshake registry snapshot and the client must contain every
entry the server has. But the *contents of these registries are derived from local config files*:

* `RegistryPerks.java:34-42` — `HandlerCommonConfig...oneHandedRequiredLevel < 0 ? null : PERKS.register(...)`.
  470 of 471 perks are registered conditionally on a `<name>RequiredLevel` value read from
  `runicskills.common.json5` at class-init.
* `RegistryTitles.java:53-66` — every title is registered from `runicskills.titles.json5`
  (`title.registry(TITLES)` → `TITLES.register(title.TitleId, ...)`).
* `RegistryPowers.java` — `issPower()` skips registration when `irons_spellbooks` is absent.

Neither config file is shipped by the server to the client before the handshake
(`CommonConfigSyncCP` is sent at `PlayerLoggedInEvent`, i.e. *after* registry sync).

**Why it matters**

Registry membership is part of the connection contract; deriving it from a
per-installation file means two installations of the same mod version are not connection-compatible.
The mod even ships a client-facing YACL editor for these exact fields.

**Failure scenario**

A player sets `berserkerRequiredLevel: -1` in their own
`config/runicskills.common.json5` (or edits it through the in-game YACL screen), or simply keeps a
`runicskills.titles.json5` with one extra custom title removed relative to the server. On connect,
Forge's registry snapshot injection reports `runicskills:berserker` (or the missing title) as a
missing entry and aborts the login with a "missing registry entries" screen. Nothing in the mod
logs a useful cause.

**Fix**

Add `.disableSync()` to all five `RegistryBuilder`s — none of these types are ever transmitted
by numeric registry id (every packet sends `Perk#getName()` / `Title#getName()` strings, see
`TogglePerkSP:41`, `SetPlayerTitleSP:32`, `PowerEquipSP:218`). Better still, always register every
perk/title and represent "disabled" purely through the existing `disabledPerks` runtime list, which
is already server-authoritative and synced.

---

Severity: High | Confidence: Medium-High | Impact: Failed logins or silently missing perks when client and server config differ | Effort: M
File: `src/main/java/com/otectus/runicskills/registry/RegistryPerks.java` — `PERKS_REGISTRY` (~line 32) and 471 `…RequiredLevel < 0 ? null : PERKS.register(...)` sites (e.g. ~line 197–212)
Also: `src/main/java/com/otectus/runicskills/registry/RegistryTitles.java#load` (~line 36–76)

Problem: all five custom registries are built with `new RegistryBuilder<T>().disableSaving()` — saving is disabled, **sync is not** (Forge's `RegistryBuilder` defaults `sync = true`). Registry *contents* are then derived from a per-instance config file: 471 perks are conditionally registered based on `…RequiredLevel < 0`, and the entire title registry is built by iterating `HandlerTitlesConfig.titleList` (`RegistryTitles.load`, line ~53–66) — i.e. from `runicskills.titles.json5`.

Why it matters: Forge synchronises modded registries during the login handshake. Registry membership must be a function of the *jar*, not of a user-editable file. Because `runicskills.common.json5` and `runicskills.titles.json5` live in `config/RunicSkills/` (per-instance, see finding on file location), a client and server running the identical mod jar can legitimately hold different registry contents.

Failure scenario: a pack author adds a custom title `dungeon_lord` to the server's `runicskills.titles.json5`, or sets `berserkerRequiredLevel: -1` on the client to hide a perk. On join, the server's registry snapshot contains an id the client never registered; Forge reports missing registry entries and refuses/degrades the connection, or (best case) injects a dummy the rest of the code then dereferences.

Fix: register the full set unconditionally and treat `-1` / `disabledPerks` purely as a runtime *enablement* filter (the mod already has `RegistryPerks.isDisabled` + `DisabledContentMatcher` for exactly this, and `hideDisabledPerks` for UI). For titles, either register a fixed built-in set and express custom titles as a datapack (see the data-driven finding), or add `.disableSync()` to the title/perk `RegistryBuilder` and accept that the registry is server-local. Verify the current behaviour on a dev server with mismatched configs before choosing.

---

Severity: High | Confidence: High | Impact: Clients cannot join | Effort: Low
File: src/main/java/com/otectus/runicskills/registry/RegistryPerks.java — `RegistryPerks` (~line 31-32); same pattern in RegistryPassives.java:35-36, RegistryPowers.java:44-46, RegistrySkills.java:23-24, RegistryTitles.java:30-31

Problem: `PERKS.makeRegistry(() -> new RegistryBuilder<Perk>().disableSaving())` disables *saving*
but not *sync*. Forge synchronises custom registries during the login handshake and rejects clients
that are missing entries the server has. Registry contents here are conditional twice over:
`!IronsSpellbooksIntegration.isModLoaded() ? null : PERKS.register(...)` (RegistryPerks.java:332,
341, 350, …, ~40 sites; RegistryPassives.java:77-79, 82-84, 87-97) and
`HandlerCommonConfig...RequiredLevel < 0 ? null : PERKS.register(...)` (RegistryPerks.java:34, 43,
54, … — the common config is a local JSON5 file, not server-authoritative at construct time).

Why it matters: The mod's own state is already synced by dedicated packets (`ConfigSyncCP`,
`PerkGroupsSyncCP`, `DynamicConfigSyncCP`, `SyncSkillCapabilityCP`), so Forge's registry sync buys
nothing and only adds a hard client/server equality requirement on a registry that is *designed*
to differ.

Failure scenario: A public server runs Iron's Spells 'n Spellbooks; a player connects with Runic
Skills but without ISS. The server's `runicskills:perks` registry contains `mana_efficiency`,
`fire_attunement`, … which the client's does not → Forge handshake fails with "Missing or unknown
registry entries" and the player is disconnected. Same outcome if an admin sets
`berserkerRequiredLevel = -1` server-side and players keep the default config.

Fix: Add `.disableSync()` to every `RegistryBuilder` here. The mod already ships its own sync layer.

---

Severity: High | Confidence: Medium | Impact: High | Effort: Moderate
File: registry/RegistryTitles.java — load (~52-70)
Problem: one Forge registry entry per config entry, into registry made with `.disableSaving()`. disableSaving does NOT disable sync; registry is negotiated at login. Ids re-derived from registration order (= config list order) every boot.
Scenario: server adds a title to its titles.json5; clients on shipped config get registry-mismatch login failure, or silently mismatched ids for every title after it.
Fix: (a) `.disableSync()` — capability sync already sends title *names* not ids, so this is viable; or (b) move titles to a datapack with a SimpleJsonResourceReloadListener.

### [RS-016] Flipping `apothicDelegate*` at runtime NPEs on every critical hit

**Severity:** High | **Confidence:** High | **Impact:** Server crash / repeated exception | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/RunicSkills.java` — `RunicSkills#attributeSetup` (~line 207); paired read at `registry/events/CombatEventHandler.java:257-261`

**Problem**

`attributeSetup` decides *at mod-load time* whether to attach the mod's own attributes to
entity types:

```java
if (!apothicLoaded || !config.apothicDelegateCritDamage)
    event.add(type, RegistryAttributes.CRITICAL_DAMAGE.get());
```

`CombatEventHandler#onPlayerCriticalHit` re-reads the same flag **live** every crit:

```java
boolean apothicHandlesCritDamage = ApothicAttributesIntegration.isModLoaded()
        && HandlerCommonConfig.HANDLER.instance().apothicDelegateCritDamage;
if (!apothicHandlesCritDamage) {
    float attribute = (float) event.getEntity().getAttributeValue(RegistryAttributes.CRITICAL_DAMAGE.get());
```

`apothicDelegateCritDamage` is `@AutoGen(category = "common")` (`HandlerCommonConfig.java:4404-4407`),
i.e. editable in-game via YACL, and `/skillsreload` → `Configuration.reloadAll()` also re-reads it
from disk. `EntityAttributeModificationEvent` fires only once per launch, so the two can disagree.

**Why it matters**

`LivingEntity#getAttributeValue` → `AttributeMap#getValue` → `getInstance(...)`
returns `null` for an attribute that was never added to the entity type, and the vanilla method
dereferences it unguarded.

**Failure scenario**

Pack has Apothic Attributes; defaults (`true`) apply, so
`runicskills:critical_damage` is never attached to any entity type. An admin turns
"delegate crit damage" off in the config screen (or on disk + `/skillsreload`). The very next
critical hit by any player throws `NullPointerException` inside `CriticalHitEvent` on the server
thread. Same shape for `apothicDelegateMiningSpeed` / `apothicDelegateArrowDamage` wherever the
corresponding attributes are read.

**Fix**

Snapshot the delegation decision once at `attributeSetup` into a static and read that
everywhere, or unconditionally register all three custom attributes (they default to 0 and cost
nothing) and only branch on which one to *write*. Additionally mark the three fields as
restart-required in the config UI.

---

### [RS-017] A non-numeric title-condition value throws out of the title scan and kills the join/tick

**Severity:** High | **Confidence:** High | **Impact:** Player cannot join; server tick exception | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/config/models/TitleModel.java` — `TitleModel#CheckRequirements` (line 146); `config/conditions/IntegerConditionImpl.java:78`

*Independently corroborated in the events/XP pass.*

**Problem**

`CheckRequirements` wraps only `ProcessVariable` in try/catch:

```java
try { condition.impl().ProcessVariable(...); }
catch (Exception e) { ...; continue; }
if (condition.impl().MeetCondition(condition.parts().expected(), condition.parts().comparator())) {
```

`MeetCondition` is *outside* the guard, and `IntegerConditionImpl#MeetCondition` does a bare
`Integer.parseInt(value)`. `ParsedParts.parse` (TitleModel.java:85-93) validates the segment count
and the comparator name but never validates that `expected` is numeric — `TitleConditionParseTest`
confirms that is the intended parse contract.

**Why it matters**

The call chain is `EntityJoinLevelEvent` → `RegistryTitles.syncTitles` →
`serverPlayerTitles` → `CheckRequirements` (`PlayerLifecycleHandler.java:153`) and the 200-tick
scan (`TickEventHandler.java:79`). Forge's event bus does not swallow exceptions.

**Failure scenario**

A pack author writes `skill/Strength/greater_or_equal/thirty` (or `30.5`, or
copies the string-style `special/dimension/equals/...` form onto a `Stat` condition) into
`runicskills.titles.json5`. Every player join throws `NumberFormatException` inside
`ServerLevel.addFreshEntity`; the player is disconnected and the same exception then repeats every
200 ticks for every online player.

**Fix**

Move the `MeetCondition` call inside the same try/catch (treat a throw as "condition failed"),
and validate `expected` at parse time for integer-typed conditions so the error is reported once at
load with the offending title id instead of at runtime.

---

Severity: Medium
Confidence: High
Impact: High   Effort: Trivial
File: src/main/java/com/otectus/runicskills/config/models/TitleModel.java — `TitleModel#CheckRequirements` (lines 139–145)
Problem: the `try/catch (Exception)` wraps only `condition.impl().ProcessVariable(...)`. The very next line calls `condition.impl().MeetCondition(expected, comparator)` unguarded, and `IntegerConditionImpl.MeetCondition` (config/conditions/IntegerConditionImpl.java:12) starts with `int parsedValue = Integer.parseInt(value);`. `ParsedParts.parse` (TitleModel.java:86) validates only that the entry has 4 slash-separated fields and a valid comparator — the expected value is never validated as numeric. Why it matters: this runs from `RegistryTitles.syncTitles` → `TickEventHandler#onPlayerTickLow` every 200 ticks per player, so the exception propagates out of a `PlayerTickEvent` handler. Failure scenario: a pack author writes `EntityKilled/minecraft:zombie/GREATER_OR_EQUAL/fifty` in `titles.json5`; ten seconds after any player joins, the server throws `NumberFormatException` inside `Player.tick()` and hard-crashes with "Ticking player", repeatedly on every restart. Also note `getProcessedValue()` returns a boxed `Integer` that is auto-unboxed in the switch — a null there would NPE the same way. Fix: move `MeetCondition` inside the same try/catch, and validate the expected value at parse time in `ParsedParts.parse` so a bad entry is logged once and permanently fails rather than throwing.

### [RS-018] Perk attribute modifiers are `addPermanentModifier` and are orphaned when the perk is disabled

**Severity:** High | **Confidence:** High | **Impact:** Permanent unremovable stat bonus in save data | **Effort:** M

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryAttributes.java` — `RegistryAttributes.RegisterAttribute#amplifyAttribute` (lines 97-110); callers at `registry/events/TickEventHandler.java:54-59` and `registry/events/CombatEventHandler.java:166,319`

*Independently corroborated in the persistence pass.*

**Problem**

`amplifyAttribute` always uses `instance.addPermanentModifier(newModifier)`. Permanent
modifiers are serialised into the player's `Attributes` NBT. Three of its four callers apply
*conditional, per-tick* state through it:

* `ONE_HANDED` → `Attributes.ATTACK_DAMAGE`, condition = offhand empty (TickEventHandler:55)
* `DIAMOND_SKIN` → `Attributes.ARMOR`, condition = sneaking (TickEventHandler:58)
* `COUNTER_ATTACK` → `Attributes.ATTACK_DAMAGE`, condition = a timed window (CombatEventHandler:319)

Reconciliation only happens inside `if (RegistryPerks.X != null)` blocks, i.e. only while the perk
is registered. Compare `PerkEffectsHandler#onAttributeTick` (line 323-346), which correctly uses
`addTransientModifier`.

**Why it matters**

A saved permanent modifier survives the disappearance of the code that maintains it.
There is no startup sweep that removes runicskills-owned modifiers for perks that no longer exist.

**Failure scenario**

A player logs out while sneaking with Diamond Skin active — the ARMOR bonus is
written to their `playerdata` NBT. The admin then sets `diamondSkinRequiredLevel: -1` (or removes
the mod). `RegistryPerks.DIAMOND_SKIN` is now `null`, the `if (... != null)` block never runs, and
the player keeps the bonus armour forever with no in-game way to remove it. Same for One-Handed's
attack damage and a Counter-Attack window that was open at logout.

**Fix**

Use `addTransientModifier` for all four — none of these need to survive a restart, and they are
recomputed within one tick of login anyway. Additionally, on `PlayerLoggedInEvent`, sweep the three
known UUIDs (`RegistryAttributes.COUNTER_ATTACK_UUID` / `ONE_HANDED_UUID` / `DIAMOND_SKIN_UUID`) off
their attributes so existing saves heal.

---

Severity: Medium | Confidence: High | Impact: Medium | Effort: Small
File: registry/RegistryAttributes.java — modifierAttributes (~75), RegisterAttribute#amplifyAttribute (~97)
Problem: `instance.addPermanentModifier(newModifier)` is serialized into the player's vanilla Attributes NBT. Reconciliation only iterates RegistryPassives.getCachedValues(), so a passive that leaves the registry (or whose attributeUuid changes) is never visited and its saved modifier never removed.
Why: this is player data OUTSIDE the mod's capability — /respec doesn't touch it, and it survives uninstalling the mod entirely.
Scenario: 1.7.0 removes `iron_lungs`; players keep permanent +4 MAX_HEALTH forever, including after uninstall.
Fix: record every UUID the mod has ever issued in a static KNOWN_MODIFIER_UUIDS set (or namespace the modifier names and match `startsWith("runicskills")`), and strip mod-owned-but-unclaimed modifiers first in modifierAttributes.

### [RS-019] Perk magnitudes and required levels are frozen at registration; `/skillsreload` silently doesn't apply

**Severity:** High | **Confidence:** High | **Impact:** Config edits appear to work but don't; two sources of truth | **Effort:** M

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryPerks.java` — perk declarations (e.g. lines 34-42) and `#register` (~line 4307); `registry/perks/Perk.java#getValue` (line 127)

*Independently corroborated in the config pass.*

**Problem**

Every perk captures its config values as primitives at registry-fill time:

```java
new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().oneHandedAmplifier)
```

`Perk.getValue()` then memoises the extracted doubles into `cachedValues` (Perk.java:128-132), and
`requiredLevel` is likewise a `final` field. `/skillsreload` → `HandlerSkill.ForceRefresh()` →
`Configuration.reloadAll()` replaces the config instance but cannot touch either.

Meanwhile `PerkEffectsHandler` reads the config **live** for ~40 perks
(`c -> c.sprintMasterPercent`, `cfg().steadfastPercent`, …, PerkEffectsHandler.java:275-317, 348-380).

**Why it matters**

The same perk's magnitude can come from two different sources with different
values, and the UI tooltip (`Perk#getMutableDescription`, which uses `getValue()`) always shows the
stale one.

**Failure scenario**

An admin doubles `berserkerPercent` and `sprintMasterPercent` in
`runicskills.common.json5` and runs `/skillsreload`. Sprint Master immediately uses the new value
(PerkEffectsHandler table); Berserker keeps the old one (`getActiveValue` in
CombatEventHandler:266) and the tooltip shows the old number for both. Raising
`berserkerRequiredLevel` has no effect at all until restart, yet raising `disabledPerks` does — so
the reload is partially applied with no warning.

**Fix**

Make `Perk` hold a `ToDoubleFunction<HandlerCommonConfig>`-style accessor (or at minimum a
`Supplier<Double>`) instead of a captured primitive, and drop `cachedValues`. Alternatively, have
`ForceRefresh()` log loudly that level/magnitude changes require a restart.

---

Severity: High | Confidence: High | Impact: Client shows and predicts with the *client's own* config values while the server enforces its own | Effort: L
File: `src/main/java/com/otectus/runicskills/registry/perks/Perk.java` — `Perk` fields (~line 21–54, 127–132)
Also: `src/main/java/com/otectus/runicskills/registry/passive/Passive.java` (~line 22–32), `src/main/java/com/otectus/runicskills/registry/RegistryPassives.java#ATTACK_DAMAGE` (~line 38), `src/main/java/com/otectus/runicskills/registry/RegistryPerks.java#TREASURE_HUNTER` (~line 197–205), `src/main/java/com/otectus/runicskills/network/packet/client/CommonConfigSyncCP.java#handle` (~line 417–509), `src/main/java/com/otectus/runicskills/network/packet/client/DynamicConfigSyncCP.java#handle` (~line 222–275)

Problem: `Perk.requiredLevel`, `Perk.rankLevelRequirements`, `Perk.configValues`, `Passive.attributeValue` and `Passive.levelsRequired` are all `final` and are read out of `HandlerCommonConfig.HANDLER.instance()` **inside the `DeferredRegister.register(...)` suppliers**, i.e. once at mod construction. `Perk.getValue()` additionally memoises into `cachedValues` (line ~128) and never invalidates. The two sync packets then write ~128 values back into the `HandlerCommonConfig` POJO on the client — a POJO which, for these ~110 perk/passive values, nothing reads again after boot.

Why it matters: the README (line 313) states "the server is authoritative for the common config. On join, the server pushes its values to each client; the local `runicskills.common.json5` on the client is read for display defaults only." That contract is not met. `PerkTooltip.java:62` renders `perk.getMutableDescription(...)` → `getValue()` → the client's boot-time snapshot; `PassiveTooltip.java:20-22` renders `passive.getValue() / passive.levelsRequired.length` — same. Every `*RequiredLevel`, `*Percent`, `*Amplifier`, `*Probability` and `*Value` the server takes the trouble to serialise is written into a field with no live reader.

Failure scenario: server sets `berserkerPercent = 10` and `berserkerRequiredLevel = 8`. Client keeps stock `30`/`30`. On join both packets arrive and are applied to the POJO. The player's perk screen still shows "requires level 30" and "30% health", the perk button is greyed out at level 8 even though the server would accept it, and the tooltip damage numbers are wrong. `/skillsreload` re-sends the same packets and changes nothing, on either side — the *server's* `Perk` objects are equally frozen, so a server admin editing `berserkerPercent` and reloading sees no gameplay change either.

Fix: make `Perk`/`Passive` read config lazily through a supplier rather than snapshotting: replace `Value(ValueType, Object)` holding a boxed number with `Value(ValueType, DoubleSupplier)` bound to `() -> HandlerCommonConfig.HANDLER.instance().berserkerPercent`, drop `cachedValues`/`cachedRankedValues` (or version-stamp them against a `ConfigHolder` generation counter bumped in `load()`). Same for `Passive.attributeValue`/`levelsRequired`. Until that lands, the two sync packets should be documented as covering only the ~18 fields that *are* read live (`enableItemLocks`, `disabledPerks/Passives/Powers`, `maxActivePerks`, `perksPerGlobalLevel`, `skillMaxLevel`, `skillLevelUp*`, …), and the README claim corrected.

---

### [RS-020] Equipped Powers are never re-validated: they survive respec, skill loss and level rollback

**Severity:** High | **Confidence:** High | **Impact:** Progression bypass | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/common/command/RespecCommand.java` — `RespecCommand#respec` (lines 41-53); `registry/events/PowerEventDispatcher.java#isEquipped` (line 1049)

**Problem**

`respec` resets skills to 1, passives to 0 and perk ranks to 0, but never touches
`SkillCapability.equippedMarks` / `equippedSeals` / `equippedCrown`. The dispatcher's gate is:

```java
public static boolean isEquipped(Player player, RegistryObject<Power> ro) {
    ...
    return cap != null && cap.isPowerEquipped(p);
}
```

— membership only. `Power#meetsSkillRequirement` (Power.java:103-109) exists but is **dead code**
(zero call sites); the only level check lives inline in `PowerEquipSP` at equip time
(`PowerEquipSP.java:260-267`). `SkillLevelCommand#setSkill/subtractSkill` similarly never
re-validate.

**Why it matters**

Perks *are* re-gated every use (`Perk#isEnabled` re-reads the skill level), so the
two systems disagree; a Crown power requires Magic 90 to slot but 0 to keep.

**Failure scenario**

Player reaches Magic 90, equips `HERALD_OF_DAWN` (Crown) + 5 Marks + 3 Seals, then
an admin runs `/respec <player>`. All skills drop to 1, all perks clear, but every Power stays
equipped and fully functional at skill level 1. Same via `/skills <p> magic subtract 89`.

**Fix**

Add a `revalidatePowers(SkillCapability, Player)` pass that unequips anything failing
`meetsSkillRequirement` / `isDisabled` / `requiredModId`, and call it from `RespecCommand`,
`SkillLevelCommand` (set/subtract), `EntityJoinLevelEvent`, and after a power-overrides reload.

---

### [RS-021] Bulk passive level-up/down (Shift ×5 / Ctrl ×10 / Alt = max) is silently defeated by the server rate limiter

**Severity:** High | **Confidence:** High | **Impact:** Advertised feature applies exactly 1 level instead of 5/10/N, silently | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `RunicSkillsScreen#handleDetailClick` (~line 994–1010), `#bulkClickAmount` (~line 188)
- `src/main/java/com/otectus/runicskills/network/PacketRateLimiter.java` — `PacketRateLimiter#allow` (~line 23), `network/packet/common/PassiveLevelUpSP.java:42`, `PassiveLevelDownSP.java:41`

*Independently corroborated in the networking pass.*

**Problem**

The client implements bulk levelling by sending N *separate* `PassiveLevelUpSP` /
`PassiveLevelDownSP` packets in a single mouse-click frame:

```java
int amount = bulkClickAmount(remaining);           // 1 / 5 / 10 / remaining
for (int i = 0; i < amount; i++) PassiveLevelUpSP.send(passive);
```

All N packets arrive on the server within the same tick and each handler calls
`PacketRateLimiter.allow(player, "passive_level_up", 2)`. The limiter records
`lastTick = server.getTickCount()` on the first packet; every subsequent packet in that tick
(and the next) sees `currentTick - lastTick == 0 < 2` and returns `false`, so the handler
returns before touching the capability. Exactly one level is applied. There is no
acknowledgement packet, so the client never learns the other N−1 were dropped — the icon just
shows +1 after the resync.

**Why it matters**

The 1.2.0 changelog feature "Shift ×5, Ctrl ×10, Alt = clear/max" does not
work at all on any server (including integrated/LAN, which also ticks the limiter). A player
Alt-clicking to respec a 20-level passive has to click 20 times and will conclude the mod is
broken.

**Failure scenario**

Passive at level 0/10, skill level high enough for all tiers. Player
Ctrl-clicks the `+` region. Client sends 10 `PassiveLevelUpSP`. Server applies 1, drops 9.
Icon shows `1`. Player Ctrl-clicks again → 1 more. Same for Alt-clear on level-down.

**Fix**

Make bulk an explicit protocol concept — add an `int amount` (var-int, clamped
server-side to `passive.levelsRequired.length`) to `PassiveLevelUpSP`/`PassiveLevelDownSP` and
loop the validated increment inside the single handler, so one packet = one rate-limit slot.
Alternatively give the limiter a token-bucket allowance (e.g. burst 32 / refill 2 per tick) for
these two packet types, but the single-packet-with-amount form is both cheaper and lets the
server report how many were actually applied.

---

Severity: Medium | Confidence: High | Impact: Medium | Effort: Small
File: client/screen/RunicSkillsScreen.java — handleDetailClick (~998, ~1009)
Problem: GUI emits N independent packets in one client tick (`for (i<amount) PassiveLevelUpSP.send(passive)`). PacketRateLimiter.allow(player,"passive_level_up",2) keys on server tick; the first records lastTick=T, every subsequent packet in the burst sees delta 0 or 1 (<2) and returns early. All but one increment are dropped with no feedback — and the rejection path returns BEFORE SyncSkillCapabilityCP.send, so the client isn't even resynced by the dropped packets.
Why: a documented 1.2.0 feature (comment at ~1006 asserts "Server validates each increment independently") does not work. Alt-clear on a 10-level passive removes one level per click. Players read this as data loss.
Fix: make bulk one packet — add an `amount` field (writeVarInt, decode-validated 1..64) and loop the VALIDATED increment server-side in a single handler invocation, stopping at the first failed check. One rate-limit slot per user gesture, every step still re-derived.

### [RS-022] An unknown title id from the server enqueues `null` and NPEs the HUD overlay every frame

**Severity:** High | **Confidence:** High | **Impact:** Repeating exception in the GUI render loop; title queue permanently wedged | **Effort:** Trivial

**Affected code:**
- `src/main/java/com/otectus/runicskills/network/packet/client/TitleOverlayCP.java` — `TitleOverlayCP#handle` (~line 158–161)
- `src/main/java/com/otectus/runicskills/client/gui/OverlayTitleGui.java` — `OverlayTitleGui#render` (~line 40, 60)

*Independently corroborated in the networking pass.*

**Problem**

The handler resolves the wire string against the registry and enqueues the result
without a null check:

```java
Title title = RegistryTitles.getTitle(this.title);
OverlayTitleGui.list.enqueue(title);      // may be null
OverlayTitleGui.showWarning();
```

`OverlayTitleGui#render` then does `Title getTitle = list.peek();` and, unconditionally,
`Component.translatable(getTitle.getKey())` → `NullPointerException`. Because the NPE is thrown
*before* the `if (showTicks == 0) list.dequeue();` line, the bad entry can never be removed —
the queue stays wedged for the rest of the session and every subsequent legitimately-earned
title is stuck behind it. Every other packet in `packet/client` (`SkillOverlayCP`,
`PlayerMessagesCP`, `SyncSkillCapabilityCP`) has an explicit null/absence guard; this one does
not.

**Why it matters**

It is reachable without any malice — a server with a datapack/KubeJS title the
client does not have, or a client/server pair that passed the protocol handshake but differ in
registered titles, produces exactly this. It is also a trivial client-crash vector for a
hostile server.

**Failure scenario**

Server sends `TitleOverlayCP("some_pack_title")`; client's
`RegistryTitles.getTitle` returns null; on the next HUD frame the `runicskills:title_overlay`
layer throws. Best case Forge logs an overlay error every frame (log flood + frame cost);
worst case the exception escapes to `GameRenderer` and drops the client to the crash screen.

**Fix**

`if (title == null) return;` in `TitleOverlayCP#handle` (log at DEBUG), and defensively
`if (getTitle == null) { list.dequeue(); return; }` in `OverlayTitleGui#render`.

---

Severity: Medium | Confidence: High | Impact: Medium | Effort: Trivial
File: network/packet/client/TitleOverlayCP.java — handle (~33)
Problem: `Title title = RegistryTitles.getTitle(this.title); OverlayTitleGui.list.enqueue(title);` — getTitle returns null for an unknown name, TitleQueue.enqueue accepts null, and OverlayTitleGui.render (~40) does `list.peek()` then dereferences `getTitle.getKey()` (~60) with no null guard. The title registry is built from each side's own titles.json5, which is NOT among the configs synced by CommonConfigSyncCP, so identical mod versions can have different title lists and the protocol handshake cannot catch it.
Why: the queue entry is never dequeued (dequeue at ~62 is downstream of the NPE), so the exception recurs every frame — crash or unplayable client, triggered by normal server-side customization.
Fix: null-guard in the handler with a WARN; defensively in TitleQueue.enqueue/OverlayTitleGui.render. Root cause: sync the server's title list, or have TitleOverlayCP carry the translation key and display name directly.

### [RS-023] `PowersScreen` is unreachable dead code — the keybind it documents is never registered

**Severity:** High | **Confidence:** High | **Impact:** A whole shipped feature (Powers equip UI, ~15 KB, 12 lang keys) has no entry point | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/PowersScreen.java` — class Javadoc (~line 29) and whole class
- `src/main/java/com/otectus/runicskills/RunicSkillsClient.java` — `ClientProxy#registerKeys` (~line 96–98)

**Problem**

The class Javadoc states "Triggered by the `key.runicskills.open_powers` keybind
(default `U`) and by a button on the existing `RunicSkillsScreen`". Neither exists.
`RunicSkillsClient` declares exactly one `KeyMapping` (`OPEN_RUNICSKILLS_SCREEN`, key 89 = `Y`)
and `registerKeys` registers only that one. A repo-wide grep for `PowersScreen` finds zero
references outside the file itself and the CHANGELOG. `en_us.json` nevertheless ships
`key.runicskills.open_powers = "Open Powers Panel"`, `screen.runicskills.powers.*`,
`tier.runicskills.*` and `school.runicskills.*` — 12+ keys for a screen no player can open.

**Why it matters**

`PowerEquipSP` is registered on the network channel and validated server-side,
and `RegistryPowers` is populated, but there is no way for a player to equip a Power. The
entire Powers gameplay loop is inert on the client. It also means the `PowersCommand` is the
only path, which is operator-only in practice.

**Failure scenario**

Player installs the mod, reads the CurseForge description mentioning Powers,
binds nothing (no keybind appears in Controls), and can never open the panel.

**Fix**

Register a second `KeyMapping("key.runicskills.open_powers", InputConstants.Type.KEYSYM,
GLFW.GLFW_KEY_U, "key.runicskills.title")` in `ClientProxy#registerKeys`, extend
`ClientForgeEvents#checkKeyboard` to consume it, and add a footer button on
`RunicSkillsScreen`'s overview page. If Powers are intentionally unfinished, delete the screen
and its lang keys instead of shipping them.

---

### [RS-024] All 16 non-English locales are ~72 % incomplete, and `tooltip.skill.level_up` is placeholder-broken in every one of them

**Severity:** High | **Confidence:** High | **Impact:** Non-English players see raw `%s` in a core tooltip and English text for 1486 keys | **Effort:** High (translation) / Trivial (the `%s` bug)

**Affected code:**
- `src/main/resources/assets/runicskills/lang/*.json` (all 16 non-`en_us` files) Call site: `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#drawLevelUpButton` (~line 476–478)

*Independently corroborated in the build/tests/docs pass.*

Problem (a): `en_us.json` has **2061** keys. Every other locale has **584** — 1486 missing and
9 stale keys that no longer exist in `en_us` (`perk.runicskills.blood_mastery`,
`crimson_bond`, `curse_ward`, `ritual_sage` + their `.description`s, and
`yacl3.config.runicskills:config.category.common.group.skills`). The missing set is dominated
by `perk.runicskills.*` (836), `yacl3.config.*` (408), `power.runicskills.*` (150) and
`title.runicskills.*` (58) — i.e. essentially every perk added since the translations were
made, the whole config UI, and the whole Powers system. `key.runicskills.*` is also missing in
all locales, so the keybind name in the Controls menu is English everywhere.

Problem (b): a hard bug. `en_us` has
`"tooltip.skill.level_up": "Spend %s xp to level up %s."` — **two** placeholders, and the call
site passes exactly two arguments:

```java
Component.translatable("tooltip.skill.level_up",
        Component.literal(String.valueOf(SkillLevelUpSP.requiredPoints(skillLevel))).withStyle(color),
        Component.translatable(detailState.skill().getKey()).withStyle(color))
```

Every one of the 16 locales has **three** placeholders (a stale "spend N *levels* (M xp)"
phrasing), e.g. `de_de`: `"Gib %s Stufen (%s XP) aus, um %s aufzuleveln."`. Minecraft's
`TranslatableContents` throws `TranslatableFormatException` on the missing third argument and
falls back to rendering the raw template, so the tooltip on the level-up button literally reads
`Gib %s Stufen (%s XP) aus, um %s aufzuleveln.` — with the `%s` visible and the actual cost
hidden.

**Why it matters**

(b) hides the single most important number in the whole UI (the XP price of a
level-up) from every non-English player. (a) means the mod presents as half-translated.

**Failure scenario**

German client, open Skills → any skill → hover the level-up button. Tooltip
shows literal percent-s placeholders instead of the cost.

**Fix**

(b) rewrite the key in all 16 locales to two placeholders matching `en_us` (or add a third
argument at the call site if the "levels (xp)" phrasing is wanted, which would then also need
`en_us` updating). Add a build-time check (there is already a `checkSidedImports` /
`checkLockProviders` precedent in `build.gradle`) that asserts placeholder-count parity across
locales and reports the missing-key count per locale.

---

Severity: Medium | Confidence: High | Impact: Low | Effort: Medium
File: `src/main/resources/assets/runicskills/lang/*.json`

**Problem**

`en_us.json` has 2,061 keys. All sixteen other locales (`ar_sa`, `de_de`, `es_ar`, `es_cl`,
`es_ec`, `es_es`, `es_mx`, `es_uy`, `es_ve`, `fr_fr`, `hi_in`, `ja_jp`, `ko_kr`, `pt_br`, `ru_ru`,
`zh_cn`) have exactly **584** keys each — 1,486 missing, identical counts across all sixteen,
indicating they were all translated at one point in history and never updated since. Each of them
also carries the same **9 keys that do not exist in `en_us`**:
`perk.runicskills.blood_mastery(.description)`, `crimson_bond(.description)`,
`curse_ward(.description)`, `ritual_sage(.description)` — the Blood Magic perks removed in 1.5.0 —
plus `yacl3.config.runicskills:config.category.common.group.skills`, which `FOLLOW_UPS.md:6` already
flags as unused.

Why it matters: a French player sees a UI that is ~72% raw English (or raw translation keys, for
keys with no `en_us` fallback path). Because the missing set is identical across all sixteen files,
this is a systemic process gap, not sixteen independent oversights: nothing in the build or CI
compares locale key sets.

Failure scenario: a Spanish-language pack ships Runic Skills; players see `perk.runicskills.berserker`
literal keys in the perk tree for the ~1,486 strings added since the last translation pass.

Fix: (a) add a JUnit test (no Minecraft needed) that parses every `lang/*.json` and asserts no locale
contains a key absent from `en_us` — this catches the 9 stale keys today and prevents future drift,
and is a hard failure that is safe to enforce immediately; (b) add a *reporting* (non-failing) task
that prints per-locale coverage so translators have a worklist; (c) delete the 9 stale keys from all
sixteen files now.

---

### [RS-025] `DrawTabs.render` allocates two full `Screen`s, a `RecipeBookComponent` and a GameProfile-tagged `ItemStack` on every frame

**Severity:** High | **Confidence:** High | **Impact:** Constant GC churn and NBT rebuild whenever the inventory or Skills screen is open | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/gui/DrawTabs.java` — `DrawTabs#render` (~line 26–33)
- `src/main/java/com/otectus/runicskills/client/core/Utils.java` — `Utils#playerHead` (~line 37–49)

**Problem**

The tab strip is rebuilt from scratch every frame:

```java
tabList = new ArrayList<>();
tabList.add(new Tabs("inventory", Utils.playerHead(), new InventoryScreen(client.player), ...));
tabList.add(new Tabs("leveling", RegistryItems.LEVELING_BOOK.get().getDefaultInstance(), new RunicSkillsScreen(), ...));
```

Per frame this allocates: a new `ArrayList`, two `Tabs`, a full `InventoryScreen` (whose ctor
builds a `RecipeBookComponent`, which itself allocates `StackedContents`, ghost-recipe and
widget lists), a full `RunicSkillsScreen`, two `ItemStack`s, plus `Utils.playerHead()` which
calls `head.getOrCreateTag()`, `SkullBlockEntity.updateGameprofile(...)` and
`NbtUtils.writeGameProfile(...)` — re-serialising the ~500-byte base64 skin texture property
into a fresh `CompoundTag` every frame. The two constructed screens are pure garbage except for
the one that is used on the frame a tab is clicked.

**Why it matters**

This runs on the render thread for the entire time the vanilla inventory or the
Skills screen is open (both `MixInventoryScreen#render` at `renderBg` TAIL and
`RunicSkillsScreen#drawScreen` call it). At 144–240 fps it is thousands of short-lived objects
per second plus repeated NBT serialisation, causing measurable stutter on low-end clients and
in heavily modded packs.

**Failure scenario**

Open the inventory on a 240 Hz display and watch allocation rate; the skull
NBT rebuild alone is ~120 KB/s of garbage.

**Fix**

Build `tabList` once (lazily, invalidated on `client.player` identity change or a
resource/lang reload) and store *screen suppliers* rather than screen instances —
`new Tabs(..., () -> new InventoryScreen(client.player), ...)` — so `setScreen` constructs the
screen only on click. Cache the player-head `ItemStack` in a static field keyed by the player's
UUID.

---

### [RS-026] Opening the config screen while connected to a server writes the server's values into the client's own config file, then reloads them over the sync

**Severity:** High | **Confidence:** High | **Impact:** Client's local/singleplayer config silently overwritten; server rules replaced by client rules mid-session | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/config/YaclConfigUiBuilder.java#buildScreen` (~line 54–60) and `src/main/java/com/otectus/runicskills/client/config/ReloadOnCloseScreen.java#onClose` (~line 86–97)

**Problem**

`buildScreen` calls `HandlerCommonConfig.HANDLER.save()` as its first statement. On a client that has already received `CommonConfigSyncCP`/`DynamicConfigSyncCP`, the in-memory POJO holds the *server's* values — so `save()` persists the server's `skillMaxLevel`, `disabledPerks`, integration toggles, XP multipliers etc. into the player's own `config/RunicSkills/runicskills.common.json5`. `ReloadOnCloseScreen.onClose()` then calls `holder.load()`, replacing the synced in-memory state with whatever is now on disk.

**Why it matters**

The two sync packets deliberately do **not** persist (`CommonConfigSyncCP.java:509`, `DynamicConfigSyncCP.java:270`: "Removed: `HandlerCommonConfig.HANDLER.save()` — server-synced values must not overwrite the user's local config file"). The config screen re-introduces exactly that, through a different door. The "Configure" button is reachable from the in-game Esc → Mods menu, so this is a normal in-session action.

**Failure scenario**

Player joins a hardcore server with `skillMaxLevel: 8`, `disabledPerks: [limit_breaker, …]`, `skillLevelUpCostMultiplier: 4.0`. They open Mods → Runic Skills → Config to change nothing, and close it. Their personal config file now permanently carries the server's balance; every singleplayer world they create afterwards runs the server's rules. Conversely, if their local file differed from the server's, on close the client's in-memory state silently reverts to local values for the remainder of the session — item-lock predictions and perk gating now disagree with the server until they relog.

**Fix**

Gate the whole config screen behind `Minecraft.getInstance().hasSingleplayerServer()` / `getCurrentServer() == null`, or open it read-only with a "these values are controlled by the server" banner while connected. If it must stay editable, do not `save()` synced state: build the YACL handler directly against the on-disk file (YACL already reads the file itself via `GsonConfigSerializerBuilder.setPath(holder.path())`, so the pre-save is unnecessary), and on close re-request a config sync from the server instead of `holder.load()`.

---

### [RS-027] `DynamicConfigSyncCP` packs 16 int arrays into a `-`-delimited string; a negative or empty array corrupts the wire format and kicks the client

**Severity:** High | **Confidence:** High | **Impact:** Client disconnect on join / passive levels silently mis-assigned | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/network/packet/client/DynamicConfigSyncCP.java#convertArraysToString` (~line 214–220) and the decoder (~line 114–181)

*Independently corroborated in the networking pass.*

**Problem**

The encoder joins each `int[]` with `,` and joins the 16 arrays with `-`. Negative numbers and empty arrays both break the delimiter. Verified empirically:

```
arrays = {5,8,11}, {-2,4}, {}
encoded : "5,8,11--2,4-"
split("-") -> ["5,8,11", "", "2,4"]        // 3 sections from 3 arrays, but shifted
parse ""  -> NumberFormatException: For input string: ""
```

The decoder's only guard is `if (allLevels.length < 16) throw new DecoderException(...)` (line ~120) — a negative value *increases* the section count, so the guard passes and the arrays are silently mis-assigned before a raw `NumberFormatException` escapes from `Integer::parseInt` (an unchecked exception, not the `DecoderException` Forge expects).

Additionally `buffer.writeUtf(result)` (line ~187) uses the 32767-char default. `skillMaxLevel` may legitimately be 1000, so a pack with 16 long passive-level arrays can exceed that and hit an `EncoderException` server-side on every player join.

**Why it matters**

Nothing validates these 38 `int[]` fields — they are the only fields in the whole config with *no* annotation of any kind, and `ConfigHolder.applyClamps` cannot touch them (`@Clamp` reads `((Number) field.get(...))`, which `ClassCastException`s on an array and is swallowed at line ~140).

**Failure scenario**

A pack author writes `"attackKnockbackPassiveLevels": [-1, 8, 14]` (or `[]`) in `runicskills.common.json5`. Every player joining that server is disconnected with `Internal Exception: io.netty.handler.codec.DecoderException` — or, worse, connects with `movementSpeedPassiveLevels` populated from the `armor` array.

**Fix**

Replace the string packing with `buffer.writeVarIntArray(...)` per array (or `writeCollection`), which is length-prefixed and sign-safe, and bound each array's length on read. Independently, validate the arrays on load: non-empty, strictly increasing, all entries in `[1, skillMaxLevel]`.

---

Severity: Medium | Confidence: High | Impact: High | Effort: Small
File: network/packet/client/DynamicConfigSyncCP.java — decoder ctor (~124)
Problem: 16 passive-level arrays are wire-encoded as one `-`-joined, `,`-separated string. Decoder validates only the SECTION COUNT, never contents. `"".split(",")` yields `[""]`; `Integer.parseInt("")` throws NumberFormatException, not DecoderException. A null array is worse: convertArraysToString NPEs on the SERVER inside toBytes during PlayerLoggedInEvent.
Why: this packet is sent unconditionally on every login (PlayerLifecycleHandler:63). A decode failure on a login packet is a hard disconnect for every player including the integrated-server host — a config typo bricks the server with no in-game diagnostic.
Scenario: pack maker sets `"magicResistPassiveLevels": []` to disable a passive; every joining client hits NumberFormatException in the netty decode thread and is disconnected with a generic internal-error message pointing at the netty pipeline, not the config key.
Fix: validated per-section helper (empty -> empty int[], element cap, NumberFormatException -> DecoderException naming the section); guard convertArraysToString against null. Better: replace the string encoding with writeVarInt(count) + writeVarInt elements, count-checked via PacketBounds like every other decoder in the package.

### [RS-028] `@Clamp` covers 9 of 1042 range-annotated fields — hand-edited values reach runtime unvalidated

**Severity:** High | **Confidence:** High | **Impact:** Every documented range in the config is advisory only outside the UI | **Effort:** M

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/storage/Clamp.java` (whole file) and `src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java#applyClamps` (~line 122–144)

**Problem**

`Clamp`'s own javadoc states the problem precisely — "The YACL `@IntField`/`@FloatField` ranges only constrain the client UI — a hand-edited file bypassed them entirely… Keep the ranges in sync with the YACL annotations on the same field." Only 9 fields ever got the annotation, all in the `general` group (`skillMaxLevel`, `playersMaxGlobalLevel`, `skillFirstCostLevel`, `maxActivePerks`, `perksPerGlobalLevel`, `maxPerkBudgetCap`, `perkSwapCooldownTicks`, `skillLevelUpCostMultiplier`, `skillLevelUpMinCost`). The remaining **1033** fields declare a `@IntField`/`@FloatField` range that is enforced nowhere on the server.

**Why it matters**

The server is the only place these values matter, and the server has no UI. A dedicated server's `runicskills.common.json5` is *always* hand-edited, so for a dedicated server the effective validation coverage is 0.9%.

**Failure scenario**

`"limitBreakerAmplifier": 999999999` (`@FloatField` range is UI-only) — one-shot everything. `"lionHeartPercent": -400` — negative potion-duration math. `"counterAttackPercent": 100000` — reflect damage overflow. None of these log anything.

**Fix**

This is mechanical and should be generated, not hand-written. Either (a) have `applyClamps` fall back to reading `@IntField`/`@FloatField` `min`/`max` reflectively when `@Clamp` is absent (they are `RUNTIME`-retained; read them by annotation *name* + reflective `min()`/`max()` so `ConfigHolder` keeps zero YACL types in its constant pool), or (b) add a Gradle `check` task that fails the build when a field carries `@IntField`/`@FloatField` without a matching `@Clamp`. Option (a) removes the duplication entirely and is the smaller diff.

---

### [RS-029] `nextInt(probability)` throws on a 0 or negative config value, and the convergence roll runs before the perk-enabled check

**Severity:** High | **Confidence:** High | **Impact:** Exception on every craft / every hit for every player | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java#onPlayerCraft` (~line 58–66)
- `CraftingEventHandler.java#onContainerOpen` (~line 112), `CraftingEventHandler.java` lucky-drop (~line 162), `src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java` limit-breaker (~line 153), `src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java` (~line 649, 721, 731)

*Independently corroborated in the events/XP pass.*

**Problem**

`ThreadLocalRandom.nextInt(bound)` throws `IllegalArgumentException` for `bound <= 0`. All six call sites pass a config-derived probability. The probability fields (`convergenceProbability`, `luckyDropProbability`, `limitBreakerProbability`, `locksmithProbability`, `treasureHunterProbability`, `spellEchoProbability`, `focusProbability`, `beastTamerProbability`, …) carry `@IntField(min = 1, …)` but **no `@Clamp`**, so `0` or `-1` from a hand-edited file flows straight through.

Worse, in `onPlayerCraft` the roll is evaluated *before* the enablement check:

```java
int randomizer = ThreadLocalRandom.current().nextInt((int) RegistryPerks.CONVERGENCE.get().getActiveValue(player)[0]);
if (RegistryPerks.CONVERGENCE.get().isEnabled(player) && (…|| randomizer == 1)) {
```

so the exception fires for every player crafting anything, whether or not they have the perk.

**Failure scenario**

An admin sets `"convergenceProbability": 0` intending "never". Every crafting operation on the server now throws `IllegalArgumentException: bound must be positive` out of a `PlayerEvent.ItemCraftedEvent` handler at `EventPriority.HIGHEST` — crafting is broken server-wide and the log fills.

Related semantics bug: with `bound == 1`, `nextInt(1)` is always `0`, and the sites test `randomizer == 1`, so "1-in-1" means *never* rather than *always*. `Perk.getParameter` also computes `1.0D / parameterValue * 100.0D`, yielding `Infinity%` in the tooltip for `0`.

**Fix**

Add `@Clamp(min = 1, …)` to every probability field (covered by the generic fix above), move the roll inside the `isEnabled` branch, and use `nextInt(Math.max(1, bound)) == 0` so the boundary case is well-defined.

---

Severity: Medium
Confidence: High
Impact: Medium   Effort: Trivial
File: src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java — `CraftingEventHandler#onPlayerCraft` (line 62)
Problem: `int randomizer = ThreadLocalRandom.current().nextInt((int) RegistryPerks.CONVERGENCE.get().getActiveValue(player)[0]);` is computed for **every craft by every player**, before the `isEnabled(player)` check on line 63. `ThreadLocalRandom.nextInt(bound)` throws `IllegalArgumentException` for `bound <= 0`. `convergenceProbability` is a plain configurable `int` with no validation. The same unguarded-bound pattern appears at CraftingEventHandler:112 (`LOCKSMITH`), CraftingEventHandler:162 (`LUCKY_DROP`) and CombatEventHandler:153 (`LIMIT_BREAKER`) — those three are at least behind an `isEnabled` check, but still crash if the perk is taken. 

**Why it matters**

A config value of `0` (a natural way for a pack author to express "never") turns every craft into an exception thrown from an event handler. Failure scenario: pack author sets `convergenceProbability = 0`; the next player to craft anything crashes the server with `IllegalArgumentException: bound must be positive` inside `ItemCraftedEvent`. Fix: `int bound = Math.max(1, (int) value[0]);` at every site, and move the roll after the `isEnabled` gate.

### [RS-030] A trailing comma in a JSON5 array silently injects a `null` element; a `null` lock entry NPEs the lock loader

**Severity:** High | **Confidence:** High | **Impact:** Item locking silently or loudly breaks after a benign edit | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java#load` (~line 80–115) → `src/main/java/com/otectus/runicskills/handler/HandlerSkill.java#getSkill` (~line 36–52) / `#buildSkillsList` (~line 78–98)

**Problem**

`load()` strips comments and hands the text to `JsonParser.parseString` (lenient). Verified empirically against Gson 2.10:

```
{"l":["x","y",]}   -> OK, l = [x, y, null]     // trailing comma in ARRAY -> null element
{"a":5,"b":6,}     -> FAIL MalformedJsonException  // trailing comma in OBJECT -> whole file reset
```

The file extension is `.json5`; JSON5 permits trailing commas; a pack author deleting the last entry of a list will leave one. `HandlerSkill.getSkill()` then iterates `lockItemList` with no null guard and calls `buildSkillsList(lockItem, true)`, which dereferences `lockItem.Skills` → `NullPointerException`.

**Why it matters**

The failure is asymmetric and unintuitive — the same edit is silently corrupting in an array and fatally resetting in an object. `disabledPerks` happens to survive (`DisabledContentMatcher.matches` skips `null` entries, line ~30) and `titleList` survives (`RegistryTitles.load` guards `title == null`, line ~54), so the inconsistency is invisible until it hits `lockItemList`.

**Failure scenario**

An admin removes the last entry from `lockItemList` in `runicskills.lockItems.json5`, leaving `…}, ]`. Next `/skillsreload` throws an NPE out of `HandlerSkill.getSkill()`, aborting `ForceRefresh()` — titles are left unbound, the lock cache is left stale, and the command reports success.

**Fix**

Null-guard the loop in `getSkill()`/`UpdateLockItems()` (and every other list consumer), and strip `null` elements centrally in `ConfigHolder.load()` after parsing — walk the parsed object's `List` fields and drop nulls, logging a WARN per removal. Optionally strip trailing commas in `stripJsonComments` so JSON5 semantics are actually honoured.

---

### [RS-031] Server config leaks into singleplayer and into the next server: nothing reloads the config on disconnect

**Severity:** High | **Confidence:** High | **Impact:** A player who visits one server carries its rules into every later world | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/Configuration.java#reloadAll` (~line 38–43) — only callers are `Init()` and `HandlerSkill.ForceRefresh()`
- `src/main/java/com/otectus/runicskills/handler/HandlerSkill.java#UpdateLockItems` (~line 20–34), `src/main/java/com/otectus/runicskills/network/packet/client/CommonConfigSyncCP.java#handle` (~line 417–509)

*Independently corroborated in the networking, persistence passes.*

**Problem**

Both sync packets mutate the shared static `HandlerCommonConfig.HANDLER.instance()` and `HandlerSkill.Skills` in place. `Configuration.reloadAll()` is called exactly twice — at mod construction and from `/skillsreload`. There is no `ClientPlayerNetworkEvent.LoggingOut` handler (grep for `LoggingOut`/`ClientPlayerNetworkEvent` returns nothing), so nothing restores the client's own values when it leaves a server.

**Why it matters**

`HandlerSkill.UpdateLockItems` even carries the comment "Replace the old skills map so client lock items doesn't affect while playing on server" — the one-way half of the problem was recognised; the return trip was not.

**Failure scenario**

Player joins Server A (`skillMaxLevel: 8`, 900 lock entries, `disabledPerks: [berserker, …]`), disconnects, and opens a singleplayer world in the same session. `HandlerCommonConfig` still holds Server A's `skillMaxLevel`, `disabledPerks`, integration toggles and XP multipliers; `HandlerSkill.Skills` still holds Server A's lock map. Their singleplayer world is played under Server A's rules until they restart Minecraft. In singleplayer the integrated server reads the same static POJO, so the *saved* progression is affected too, not just the display.

**Fix**

Subscribe `ClientPlayerNetworkEvent.LoggingOut` on the client and call `Configuration.reloadAll()` + `HandlerSkill.ForceRefresh()`. Better: stop mutating the config POJO from network handlers — hold synced server state in a separate `ServerConfigView` object that is cleared on logout, and have gameplay/UI code read `ServerConfigView.orLocal()`. That also makes it explicit which of the 1141 fields are actually server-authoritative.

---

Severity: Medium | Confidence: High | Impact: Medium | Effort: Small
File: network/packet/client/CommonConfigSyncCP.java — handle (~417)
Problem: assigns ~90 server values straight into HandlerCommonConfig.HANDLER.instance(). save() was deliberately removed (comment ~509) but no snapshot is taken and nothing restores on disconnect. YaclConfigUiBuilder:40 then calls holder.save().
Scenario: player joins hardcore server (skillMaxLevel=8, maxActivePerks=3), leaves, opens the config screen in main menu, clicks Save -> their singleplayer config is now the hardcore server's.
Fix: snapshot local instance on first sync, restore from ClientPlayerNetworkEvent.LoggingOut; cheapest: call HandlerCommonConfig.HANDLER.load() on logout.

Severity: Medium | Confidence: High | Impact: Medium | Effort: Small
File: network/packet/client/CommonConfigSyncCP.java — handle (~422); DynamicConfigSyncCP (~227-269)
Problem: both handlers write ~90 fields straight into the process-wide singleton. save() correctly removed (comment ~509) so disk is safe — but nothing reloads the file on disconnect. HandlerCommonConfig.HANDLER.load() runs exactly once, from Configuration (~41). There is no ClientPlayerNetworkEvent.LoggingOut subscriber anywhere in the tree.
Why: the integrated server reads the SAME singleton, so the next singleplayer session silently runs under the remote operator's rules until the game is restarted. Config file says one thing, world behaves another way.
Scenario: player joins hardcore server (skillMaxLevel=8, maxActivePerks=3, 20 disabled perks), quits to title, opens their own creative world — integrated server starts with skillMaxLevel=8 and 20 perks disabled, ENFORCED server-side by SkillLevelUpMath.canLevelUp and RegistryPerks.isDisabled, so not merely display.
Fix: snapshot overwritten fields before applying the first server packet and restore from a client LoggingOut handler (simplest: call HandlerCommonConfig.HANDLER.load() + HandlerConvergenceItemsConfig.HANDLER.load()). Cleaner: a separate ServerSyncedConfig holder consulted only while a connection exists.

### [RS-032] MixLivingEntity rebuilds `MobEffectInstance` for every player effect, discarding ambient/visible/showIcon/curative flags

**Severity:** High | **Confidence:** High | **Impact:** Cross-mod effect behaviour | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/MixLivingEntity.java — `MixLivingEntity#this$onAddEffect` (~line 127) and `#this$onDrinkPotion` (~line 165)

**Problem**

Both re-implementations construct `new MobEffectInstance(effect.getEffect(), duration, amplifier)`
**unconditionally** — even when no Runic Skills perk applies. That 3-arg constructor defaults
`ambient=false`, `visible=true`, `showIcon=true`, drops `hiddenEffect`, and drops Forge's
`curativeItems` list from the original instance. The mixin cancels vanilla `addEffect` at
`INVOKE canBeAffected` (line 88-105) for *every* `Player`, so this rewrite is on the path of every
effect application to every player from every mod in the pack.

**Why it matters**

`ambient` is what makes beacon effects non-intrusive; `showIcon`/`visible` are how
mods suppress HUD spam for internal/bookkeeping effects; `curativeItems` is Forge's per-instance
override of what cures an effect (milk vs. a modded antidote). All three are silently reset.

**Failure scenario**

Player stands in a beacon range → vanilla applies `MobEffectInstance(SPEED, 260,
0, true /*ambient*/, false /*visible*/)`. The mixin replaces it with a non-ambient, fully-visible,
icon-showing instance → constant particle emission and a permanent HUD icon that never appears in
vanilla. A mod that applies a hidden marker effect with `showIcon=false` now shows an icon on every
player. A modded effect with a custom curative item becomes curable only by milk.

**Fix**

Copy the source instance instead of rebuilding it — use the 6-arg constructor carrying
`effect.isAmbient()`, `effect.isVisible()`, `effect.showIcon()`, then
`newEffect.setCurativeItems(effect.getCurativeItems())`; and short-circuit to plain
`vanilla addEffect` (do not cancel) when no perk is active.

---

### [RS-033] MixLivingEntity substitutes the affected player for the real effect *source* entity

**Severity:** High | **Confidence:** High | **Impact:** Attribution / other mods' event handlers | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/MixLivingEntity.java — `MixLivingEntity#onAddEffect(MobEffectInstance, Entity, CallbackInfoReturnable)` (~line 97-105)

**Problem**

The injector receives `(MobEffectInstance effect, Entity source, ...)` but calls
`this$onAddEffect(effect, player)` — the `source` argument is discarded and the *target* player is
passed through in its place. Downstream, line 133 posts
`new MobEffectEvent.Added(this$class, mobeffectinstance, newEffect, player)` and line 136 calls
`onEffectAdded(newEffect, player)`. Vanilla passes the applying entity there.

**Why it matters**

`MobEffectEvent.Added#getEffectSource()` is the standard way other mods attribute
an effect (PvP kill credit, "who poisoned me", anti-grief, mob-vs-player rules). Every effect
applied to a player now reports the player as its own source.

**Failure scenario**

A witch throws a poison potion at a player. A combat-log or PvP mod listening to
`MobEffectEvent.Added` sees `getEffectSource() == the victim`, not the witch, and mis-attributes
(or suppresses) the effect. Same for any mod keying off `LivingEntity#onEffectAdded`'s entity arg.

**Fix**

Thread `source` through `this$onAddEffect`/`this$onDrinkPotion` and pass it to both
`MobEffectEvent.Added` and `onEffectAdded`/`onEffectUpdated`.

---

### [RS-034] `MixCraftingMenu` empties the crafting result after vanilla has already sent it to the client

**Severity:** High | **Confidence:** High | **Impact:** Client/server desync, ghost items | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/MixCraftingMenu.java — `MixCraftingMenu#slotChangedCraftingGrid` (~line 19-32)

**Problem**

The inject is at `@At("TAIL")`. By that point vanilla `CraftingMenu.slotChangedCraftingGrid`
has already executed `pResult.setItem(0, itemstack)`, `pMenu.setRemoteSlot(0, itemstack)` and
`serverplayer.connection.send(new ClientboundContainerSetSlotPacket(...))`. The mixin then does
`resultContainer.setItem(0, ItemStack.EMPTY)` without updating the remote slot or resending the
packet.

**Why it matters**

The server's authoritative result is empty; the client's is the crafted item. The
menu's remote-slot tracking also still holds the item, so the next `broadcastChanges()` sees no
delta and never corrects the client.

**Failure scenario**

A player at a crafting table arranges a recipe for a locked item. The output slot
visibly shows the item. Clicking it does nothing (server has `EMPTY`), and the ghost item persists
until the player closes the GUI or changes the grid. Reported by users as "the crafting table is
broken".

**Fix**

Move the inject to an `@ModifyVariable`/`@Redirect` before the packet send, or after zeroing
the result also call `pMenu.setRemoteSlot(0, ItemStack.EMPTY)` and resend
`ClientboundContainerSetSlotPacket` with `pMenu.incrementStateId()`.

---

### [RS-035] Haggler discount is written to persistent `MerchantOffer.specialPriceDiff` but the undo map lives only in RAM

**Severity:** High | **Confidence:** Medium-High | **Impact:** Compounding price exploit | **Effort:** Medium

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/MixVillager.java — `MixVillager#runicskills$applyHagglerDiscount` (~line 383-393) / `#runicskills$resetHagglerDiscount` (~line 370-381)

**Problem**

The applied delta is stored in a `@Unique IdentityHashMap` on the mixin instance
(line 368). `MerchantOffer#specialPriceDiff` is serialised to NBT (`"specialPrice"`) and survives
chunk unload / server restart; the `IdentityHashMap` does not. On reload the HEAD reset finds an
empty map and undoes nothing, but the TAIL handler applies a fresh discount on top of the persisted
one.

**Why it matters**

The discount compounds without bound across sessions, and it leaks to *other*
players who trade with the same villager before `updateSpecialPrices` runs for them.

**Failure scenario**

Player with Haggler opens a villager (−N emeralds persisted), logs out, logs in,
opens the same villager again → −2N. Repeat until every trade costs the minimum 1 emerald. Also
occurs across chunk unload/reload, which happens routinely on servers.

**Fix**

Store the applied delta in the villager's own persistent NBT alongside the offer, or recompute
"base cost" from `offer.getBaseCostA()` and set an absolute `specialPriceDiff` rather than an
additive delta. Also consider `MerchantOffer#setSpecialPriceDiff` semantics vs. Hero of the Village,
which also writes this field.

---

### [RS-036] Build is not reproducible: SNAPSHOT plugin plus two dynamic version ranges

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Low

**Affected code:**
- `build.gradle` — lines 8, 15, 16

**Problem**

Three of the build's four plugin coordinates are non-pinned.
`classpath 'org.spongepowered:mixingradle:0.7-SNAPSHOT'` (line 8) resolves a mutable SNAPSHOT
artifact; `id 'net.minecraftforge.gradle' version '[6.0.16,6.2)'` (line 15) is an open range; and
`id 'org.parchmentmc.librarian.forgegradle' version '1.+'` (line 16) is a dynamic version. There
is no `dependencyLocking`, no `--write-locks` lockfile, and no `settings.gradle` `versionCatalog`.

**Why it matters**

The same commit builds different bytecode on different days. Mixingradle
0.7-SNAPSHOT in particular controls refmap generation and the `createSrgToMcp` output that both
run configs reference (`build.gradle:84,91`) — a silent SNAPSHOT change can produce a jar whose
mixins fail to apply in production while `runClient` still works locally. CI's Gradle cache
(`.github/workflows/build.yml:21-28`) is keyed on `hashFiles('**/*.gradle*')`, which does not
change when the SNAPSHOT upstream changes, so CI can keep serving a stale-but-different resolution
indefinitely and then flip on the first cache miss.

**Failure scenario**

A user reports "perk X does nothing on 1.6.1". The maintainer rebuilds 1.6.1 from
the tag six weeks later, gets a different mixingradle snapshot, produces a jar with a correct
refmap, cannot reproduce, and closes the issue. Meanwhile the released jar's `MixCraftingMenu`
injection silently never applied.

**Fix**

Pin all three. `org.spongepowered:mixingradle:0.7.38` (or whatever release is current),
`net.minecraftforge.gradle` to an exact version, `org.parchmentmc.librarian.forgegradle` to `1.1.3`.
Then enable Gradle dependency locking for the `classpath` and `runtimeClasspath` configurations and
commit the lockfiles. Add `gradle/actions/wrapper-validation` to CI (see the CI finding below).

---

### [RS-037] MixinExtras is declared as an annotation processor and a jar-in-jar dependency but is never used, and `jarJar` is never enabled

**Severity:** High | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `build.gradle` — lines 189-192; `CLAUDE.md` — "Key Dependencies"/"Conventions"; `README.md:269`

**Problem**

`build.gradle:189-192` declares
`implementation(annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.0"))` and
`implementation(jarJar("io.github.llamalad7:mixinextras-forge:0.4.0"))` with a
`jarJar.ranged(it, "[0.4.0,)")` constraint. A grep across all 15 mixin classes and the entire
`src/main/java` tree finds **zero** references to `com.llamalad7.mixinextras` and zero MixinExtras
annotations (`@WrapOperation`, `@ModifyExpressionValue`, `@ModifyReturnValue`,
`@WrapWithCondition`, `Operation<>`). Separately, `jarJar.enable()` is never called anywhere in
`build.gradle`, which in ForgeGradle 6 means the `jarJar` task stays disabled and no `-all.jar` is
produced at all.

**Why it matters**

Three things are simultaneously wrong and they mask each other.
(a) A dead compile-time dependency and annotation processor slow every build and add a
supply-chain surface for no benefit. (b) `CLAUDE.md` tells every future contributor
"**MixinExtras** (0.4.0) — enhanced mixin features" and "Mixins: declared in
`runicskills.mixins.json`, uses MixinExtras" — a contributor will write `@WrapOperation` believing
it is wired, and it will compile (the AP is on the classpath) but the class will not be shipped or
declared as a dependency in `mods.toml`, producing a runtime `NoClassDefFoundError` in the field.
(c) `README.md:269` documents `runicskills-<version>-all.jar — jar-in-jar bundle (includes bundled
deps)` as a release artifact. That artifact is never built, so the documented release step is
fiction, and if someone later *does* start using MixinExtras and calls `jarJar.enable()`, CI's
`path: build/libs/*.jar` (`build.yml:41`) would upload *both* jars with no indication which one to
publish.

**Failure scenario**

Contributor adds `@ModifyExpressionValue` to `MixLivingEntity`, CI goes green,
the jar ships, and every client with no other MixinExtras-providing mod crashes on world load with
`java.lang.NoClassDefFoundError: com/llamalad7/mixinextras/injector/ModifyExpressionValue`.

**Fix**

Pick one. Either (1) drop lines 189-192 and the `mixinextras` mentions from `CLAUDE.md`, and
delete the `-all.jar` line from `README.md:269`; or (2) keep them, add `jarJar.enable()`, add a
`[[dependencies.runicskills]]` entry for `mixinextras` in `mods.toml`, and make CI upload only the
`-all.jar`. Option 1 matches reality today.

---

### [RS-038] `/updateskilllevel` and `/globallimit` write config values that bypass `@Clamp`, and extreme values overflow the XP curve into near-free skill levels

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/common/command/UpdateSkillLevelCommand.java:20,36-37`; `src/main/java/com/otectus/runicskills/common/command/GlobalLimitCommand.java:19,28-29`; `src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java:88,118-121`; `src/main/java/com/otectus/runicskills/common/util/ExperienceMath.java:36-41,100-102`

*Independently corroborated in the config pass.*

**Problem**

Both commands take `IntegerArgumentType.integer(1)` — a lower bound of 1 and no upper
bound (`Integer.MAX_VALUE`). They then assign straight into the live config POJO
(`HANDLER.instance().skillMaxLevel = levelLimit`, `HANDLER.instance().playersMaxGlobalLevel = …`)
and call `HANDLER.save()`. The corresponding fields carry `@Clamp(min = 2, max = 1000)` and
`@Clamp(min = 32, max = 99999)` (`HandlerCommonConfig.java:38,44`), but `ConfigHolder` applies
clamps **only on file load** — `applyClamps(loaded)` is called at `ConfigHolder.java:88` inside the
parse path, and the javadoc at line 118-121 states this explicitly ("Runs only on file loads").
`save()` (line ~157) performs no clamping.

Two consequences compound:

1. **Silent revert.** `/updateskilllevel 5000` sets `skillMaxLevel = 5000` in memory, writes 5000
   to `runicskills.common.json5`, and syncs it to all clients via `DynamicConfigSyncCP`. On the
   next server restart, `applyClamps` silently rewrites it to 1000 with a WARN buried in the log.
   The admin's setting evaporates and the only trace is one WARN line.
2. **Integer overflow → economy break.** `ExperienceMath.sum(n, a0, d)` computes
   `n * (2 * a0 + (n - 1) * d) / 2` in `int` arithmetic (line 100-102). For the >30 branch
   (`getExperienceForLevel`, line 40) with `d = 9`, the product `n * 9n` overflows `int` at
   roughly `n > 15,400`, i.e. a skill level above ~15,430. `requiredPoints`
   (line 94-98) then computes `Math.max(0, Math.round(base * mult))` on a negative `base`, yielding
   0, which is then floored at `skillLevelUpMinCost` (default 1). Every skill level-up costs
   **1 XP point**.

**Failure scenario**

An admin on a pack that wants a very long progression runs
`/updateskilllevel 20000`. Immediately every player can level every skill from 1 to 20000 for 1 XP
point per level, permanently destroying the server's progression economy — and after the next
restart the cap silently drops back to 1000 with the damage already saved into player NBT.

**Fix**

(a) give both commands the same bounds as the `@Clamp` annotation:
`IntegerArgumentType.integer(2, 1000)` and `IntegerArgumentType.integer(32, 99999)`; (b) better, add
a `ConfigHolder.setClamped(field, value)` / call `applyClamps(instance)` inside `save()` so *no*
mutation path can escape the declared range; (c) independently, widen `ExperienceMath.sum` to
`long` arithmetic with a saturating cast so the curve degrades gracefully rather than wrapping.

---

Severity: Medium | Confidence: High | Impact: Admin command can put the mod in a state the config loader considers invalid | Effort: S
File: `src/main/java/com/otectus/runicskills/common/command/UpdateSkillLevelCommand.java#execute` (~line 27–43) and `src/main/java/com/otectus/runicskills/common/command/GlobalLimitCommand.java#execute` (~line 25–35)

Problem: both commands declare `IntegerArgumentType.integer(1)` — minimum 1, **no maximum** — then assign directly to the POJO and call `HANDLER.save()`. `applyClamps` only runs in `load()`, so the out-of-range value is live for the whole session *and* written to disk; it is silently corrected only on the next restart.

`skillMaxLevel`'s declared range is `[2, 1000]`; `playersMaxGlobalLevel`'s is `[32, 99999]`. `/updateskilllevel 1` and `/globallimit 1` are both accepted.

**Why it matters**

`skillMaxLevel = 1` makes `Skill.getRank` compute `(1 / 8) * i == 0`, so every level satisfies every rank and the rank label pins to the top tier (`Skill.java` ~line 88). `SkillLevelUpMath.canLevelUp(currentLevel, 1)` rejects all level-ups for anyone already at level ≥ 1 — i.e. everyone, since skills seed at 1. `/updateskilllevel 2000000000` produces `skillLevel + firstCostLevel` integer overflow in `ExperienceMath.requiredPoints` (`skillLevel + firstCostLevel - 1`).

Failure scenario: admin runs `/globallimit 1` to "lock progression". Every player's `getGlobalLevel()` already exceeds 1; the skills screen shows "global max level reached" permanently, and the value is now baked into `runicskills.common.json5`.

Fix: bound the Brigadier arguments to the `@Clamp` ranges (`IntegerArgumentType.integer(2, 1000)` / `(32, 99999)`), or route both commands through a shared `ConfigHolder.setClamped(field, value)` that applies the annotation before assigning. Also expose the clamp ranges as constants so the command, the annotation and the UI cannot drift.

---

### [RS-039] README's command reference is materially wrong for three of eight documented commands

**Severity:** High | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `README.md:138-147` (Commands table)

**Problem**

Comparing the table against the nine registered commands:

| README claim | Reality |
|---|---|
| `/skills <player> <skill> <level>` — set a skill | Actual grammar is `/skills <player> <skill> set <level>`. The literal `set` is mandatory (`SkillLevelCommand.java:31`); the documented form does not parse. |
| `/skills <player> <skill> add <amount>` — "Add (or subtract, with negative)" | `add` takes `IntegerArgumentType.integer(1, max)` (line 37) — negatives are **rejected by the parser**. A separate `subtract` literal exists (line 40) and is undocumented. |
| `/registeritem <item-id>` | Takes no item id. Grammar is `/registeritem <skill> <level>` and it operates on the caller's **main-hand item** (`RegisterItem.java:30-32,39`). |
| `/respec <player>` — "Reset all passives and perks (skill levels preserved) and refund points" | Sets **every skill to level 1** (`RespecCommand.java:42`). Nothing is refunded — no XP is returned to the player. |
| — | `/updateskilllevel <level>` and the entire `/powers` command tree (`list`/`view`/`equip`/`unequip`) are not documented at all. |
| `/skills get` | Undocumented (`SkillLevelCommand.java:28`). |

`README.md:175` compounds this by telling users to run `/registeritem <skill> 0` — which is the
*correct* grammar, contradicting line 145 four lines earlier.

**Why it matters**

`/respec` is described as non-destructive but is in fact the most destructive
command in the mod. An admin who trusts the README will wipe a player's entire skill progression
while believing they are only refunding perk points, and there is no undo.

**Failure scenario**

Server owner reads "skill levels preserved", runs `/respec Steve` to fix a stuck
perk, and Steve loses 200 hours of skill levels permanently.

**Fix**

Regenerate the table from the source. Add `/updateskilllevel` and `/powers`. Correct the
`/respec` description to "Resets all skills to level 1 and clears all passives and perks. XP is not
refunded. Destructive — no undo." Consider adding a confirmation argument
(`/respec <player> confirm`) given the blast radius.

---

### [RS-040] The documented KubeJS subscription example names a class that does not exist

**Severity:** High | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `docs/API_EVENTS.md:51`; `README.md:299`

**Problem**

Both the public API reference and the README instruct pack authors to write
`ForgeEvents.onEvent('net.minecraftforge.event.entity.player.PlayerEvent$SkillLevelUpEvent', …)`.
The real class is `com.otectus.runicskills.event.SkillLevelUpEvent`
(`src/main/java/com/otectus/runicskills/event/SkillLevelUpEvent.java:20`). It *extends*
`PlayerEvent` but is a top-level class in the mod's own package, not a nested class of Forge's
`PlayerEvent`. The documented string can never resolve.

**Why it matters**

This is the *only* code sample for the flagship scripting integration, presented in
the section titled "Stability commitment", and it is copy-pasteable. KubeJS's `ForgeEvents.onEvent`
with an unresolvable class name typically fails silently or with a script-load error, so the
author's hook simply never fires and they have no signal about why.

**Failure scenario**

Pack author copies the snippet, sees no output, concludes the event API is broken,
and files an issue or drops the integration.

**Fix**

Replace the FQCN with `com.otectus.runicskills.event.SkillLevelUpEvent` in both files, and add
the other four (`PassiveLevelUpEvent`, `PerkToggleEvent$Pre`, `PerkToggleEvent$Post`,
`TitleEarnedEvent`). Ideally add a KubeJS smoke script under `docs/` that a maintainer can drop into
a test instance so this can never silently rot again.

---

### [RS-041] Apotheosis interactor attribution compares level game-time against server uptime ticks

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Trivial

**Affected code:**
- `integration/ApotheosisIntegration.java` — `recordInteraction` (66-69), `resolveInteractor` (76-91), `pruneInteractors` (94-98)

**Problem**

`recordInteraction` stores `p.level().getGameTime()` as the timestamp.
`resolveInteractor` and `pruneInteractors` both compute `now = RunicSkills.server.getTickCount()`
and test `now - stored > INTERACTION_WINDOW_TICKS`. These are two unrelated clocks:
`getTickCount()` is server-process uptime (0 at every boot), `getGameTime()` is the level's
persisted monotonic tick counter (millions on any real world, and settable by `/time`).

**Why it matters**

On any world whose `gameTime` exceeds the server's current uptime — i.e. every
world after the first restart — `now - stored` is a large *negative* number, so the `> 20` test is
never true. The 1-second window never closes. `resolveInteractor` therefore always returns the
player with the highest recorded `gameTime`, i.e. **the last player who right-clicked any block
anywhere, at any point since server boot** (`recordInteraction` is called from
`PlayerInteractEvent.RightClickBlock`, ~line 262, which fires on every block interaction by every
player). `pruneInteractors` never removes anything, so the map also grows monotonically (bounded
by unique players, but never released). The B4 comment claims this "tolerates concurrent
interactions across multiple players"; it does the opposite.

**Failure scenario**

Server has been up 10 minutes (`tickCount` ≈ 12 000) on a world with
`gameTime` = 4 000 000. Player A right-clicks a door. Ten minutes later player B sockets a mythic
gem. `resolveInteractor()` returns **A**. B's socketing is gated against A's Fortune level
(`onGemSocket`, ~285) and A's `GEM_ATTUNEMENT` perk decides whether B's gem is refunded
(~430-455). B is denied a legal socket, or A's perk duplicates B's gem into A's inventory.
`findItemOwner` (~405) has the same misattribution for socket-count bonuses.

**Fix**

Use one clock. `long now = player.level().getGameTime()` at all three sites (or
`server.overworld().getGameTime()`, matching what `CombatEventHandler.onServerTick` already does),
and only record interactions from the Apotheosis-relevant events, not every `RightClickBlock`.

---

### [RS-042] An empty passive-level array divides by zero and installs a NaN attribute modifier

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Trivial

**Affected code:**
- `registry/RegistryAttributes.java` — `modifierAttributes` (~85-92); arrays at `handler/HandlerCommonConfig.java:371-508, 3396-4532`

**Problem**

The modifier amount is
`passive.getValue() / passive.levelsRequired.length * passive.getLevel(serverPlayer)`.
`levelsRequired` is the user-editable `int[]` straight out of config, with no length validation
anywhere (`RegistryPassives.register` → `new Passive(...)` stores the array reference verbatim).
An empty array makes the divisor `0`: `x / 0` is `Infinity` for a double numerator, and
`Infinity * 0` (the player's level, which can never rise above 0 because
`addPassiveLevel` clamps to `levelsRequired.length`) is `NaN`. `RangedAttribute.sanitizeValue`
uses `Mth.clamp`, whose comparisons are all false for `NaN`, so the `NaN` survives into the
attribute value.

**Why it matters**

06 reports that an empty array corrupts the `DynamicConfigSyncCP` wire format and
kicks the client — but that packet carries **only 16 of the 38** passive-level arrays. The other
22 (`swimSpeedPassiveLevels`, `repairEfficiencyPassiveLevels`, `craftingLuckPassiveLevels`, and all
of the Iron's/Ars/Apothic arrays at 3396-4506) are not in any sync packet, so they reach
`modifierAttributes` with no wire-format tripwire in front of them. `modifierAttributes` runs on
every login, respawn, dimension change, passive level change, `/respec` and `/skillsreload`.

**Failure scenario**

Pack author writes `"swimSpeedPassiveLevels": []` intending "disable this
passive" (the config has no documented disable form for arrays — see 06). Every player who logs in
gets `forge:swim_speed = NaN`. Swim velocity becomes `NaN`, the player's position becomes `NaN`,
and the server disconnects them with "moved wrongly" / an `IllegalStateException` in entity
movement — for every player, permanently, with no error pointing at the config.

**Fix**

Guard the divisor (`int steps = Math.max(1, passive.levelsRequired.length)`), reject
zero-length arrays at config load with a WARN + fallback to the built-in default, and add a
`Double.isFinite` assertion before `addPermanentModifier`.

---

### [RS-043] Shrinking a passive-level array leaves players above the new maximum, and the attribute scales past its configured cap

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Small

**Affected code:**
- `common/capability/SkillCapability.java` — `deserializeNBT` (~520-524), `copyFrom` (~596), `addPassiveLevel` (~232); `registry/RegistryAttributes.java` — `modifierAttributes`

**Problem**

`levelsRequired.length` is simultaneously the passive's max level and the divisor that
turns the configured total value into a per-level increment. Nothing ever clamps a *stored*
`passiveLevel` against it. `deserializeNBT` writes `nbt.getInt(key)` unconditionally, `copyFrom`
copies verbatim, and `addPassiveLevel`'s `Math.min` only constrains new gains. `PassiveLevelUpSP`
rejects further level-ups at cap, so an over-cap value is stable, not self-correcting.

**Why it matters**

The value formula is `total / length * level`. If a player banked level 10 under a
10-entry array and the array is later shortened to 5 entries, they receive `total / 5 * 10` =
**twice the configured maximum** of that attribute, forever. This is the exact reconciliation gap
04 flags for perks ("Reload-invalidated state is never reconciled") but with a quantitative
over-grant rather than a soft lockout, and it also survives a full restart (which is the only way
the shortened array takes effect at all, since `Passive` snapshots the array at registration).

**Failure scenario**

Admin trims `maxHealthPassiveLevels` from 10 entries to 5 to nerf the passive and
restarts. Every player who was at passive level 6-10 now has 1.2×-2× the intended `MAX_HEALTH`
bonus, the Skills screen reads "10 / 5", and `/skillsreload` cannot fix it because the array is
frozen in the `Passive` object.

**Fix**

Clamp on load — `Math.min(nbt.getInt(key), passive.levelsRequired.length)` in `deserializeNBT`
and `copyFrom` — and refund/log the delta. Long-term, store the per-level increment explicitly
instead of deriving it from array length.

---

### [RS-044] A config-disabled perk still consumes the perk budget, and `hideDisabledPerks` removes the only way to release it

**Severity:** High | **Confidence:** High | **Impact:** High | **Effort:** Small

**Affected code:**
- `registry/RegistryPerks.java` — `countEnabledPerks` (4356-4362), `isOverPerkBudget` (4385-4388), `isHiddenFromUi` (4405-4407); `client/screen/RunicSkillsScreen.java:686-687`; `network/packet/common/TogglePerkSP.java:76-85`

**Problem**

Three independently-correct behaviours compose into a trap.
1. `countEnabledPerks` counts every entry with `perkRank >= 1`, with no `isDisabled` filter — a
   perk disabled after the player took it still occupies a budget slot.
2. `isOverPerkBudget` freezes **all** perk enabling/rank-ups when the count exceeds the cap;
   `TogglePerkSP` tells the player to respec.
3. 1.6.1's `hideDisabledPerks` makes `RunicSkillsScreen.buildDetailPageState` `removeIf` the perk
   from the list entirely — so the row the player would click to set rank 0 no longer exists.

**Why it matters**

`TogglePerkSP` explicitly keeps the disable path open ("so players can clear a
stuck rank"), but the UI that would send that packet has been deleted. `/respec` is `hasPermission(2)`
(01 already notes it is the only exit from the freeze); with `hideDisabledPerks` on it becomes the
*only possible* exit, requiring operator intervention per player.

**Failure scenario**

Server owner adds three overpowered perks to `disabledPerks` and sets
`hideDisabledPerks = true` (the natural pairing — hide what you disabled). Every player who had
those three perks is now at cap+3. Their perk tooltip reads "13 / 10 active" while only 10 perks
are visible anywhere in the UI, and every perk click is silently rejected with a "respec" banner
they cannot act on. The same shape exists for Powers: `PowersScreen.filterHidden` (77-82) removes
disabled powers, so an equipped-then-disabled Mark permanently occupies a slot even though
`PowerEquipSP` (63-68) would happily unequip it.

**Fix**

Exclude disabled entries from `countEnabledPerks`/the equipped-power slot count, or keep
disabled-but-held entries visible (greyed, "disabled — click to remove") regardless of the hide
flag. Additionally: auto-zero the rank of a perk that becomes disabled at `/skillsreload` time,
where `RegistryAttributes.modifierAttributes` is already re-run for the passive equivalent.

---

### [RS-045] Documented Power prerequisites and the Power-Point budget are not enforced anywhere

**Severity:** Medium-High | **Confidence:** High | **Impact:** Documented balance rules absent | **Effort:** M

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/network/packet/common/PowerEquipSP.java` — `PowerEquipSP#handle` (lines 246-271); `registry/powers/PowerTier.java` (lines 403-414)

**Problem**

`PowerTier`'s javadoc states "SEAL … requires one same-school Mark slotted", "CROWN …
requires one same-school (or category) Seal slotted", and "The PP budget is checked server-side in
PowersEquipSP". `PowerEquipSP` checks only: power exists, not disabled, required mod loaded, skill
level ≥ threshold, and (inside `SkillCapability#equipPower`) slot capacity + duplicate.
`PowerTier.pointCost` has **zero** references outside its own declaration (grep-verified); there is
no school/tier prerequisite check in the codebase.

**Why it matters**

This is the only tree/prerequisite structure the mod has (perks have none — they
gate purely on a flat skill level), and it is unimplemented. `PowerTier.maxEquipped` alone lets a
player fill 5 Marks + 3 Seals + 1 Crown across nine unrelated schools.

**Failure scenario**

A Magic-90 player equips `GLACIAL_SOVEREIGN` (Ice Crown) with no Ice Seal and no
Ice Mark slotted; the pack's intended "commit to a school" progression is bypassed entirely.

**Fix**

Implement `hasSameSchoolAtTier(cap, schoolId, tier)` in `PowerEquipSP` before `equipPower`, and
either implement or delete the PP budget (and the javadoc that promises it). Also cascade-unequip:
removing a Mark must invalidate a dependent Seal.

---

### [RS-046] Power internal cooldowns and proc windows are transient — relogging resets them

**Severity:** Medium-High | **Confidence:** High | **Impact:** Exploit; dead persisted state | **Effort:** M

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/common/powers/PowerRuntime.java` — `InternalCooldowns` (lines 163-182), `ProcWindows` (139-159); `common/capability/SkillCapability.java` (lines 74-77, 332-362)

**Problem**

`SkillCapability` declares `powerCooldowns` / `powerWindows`, serialises and deserialises
both (SkillCapability.java:493-500, 573-582) and exposes five accessors — none of which have any
caller (grep-verified). The dispatcher uses the in-memory `PowerRuntime` maps exclusively, and
`PowerRuntime.clearPlayer` wipes them on logout (`PowerEventDispatcher.java:988`). The
`ProcWindows` javadoc claims windows are "also mirrored on the capability for persistence"; they
are not.

**Why it matters**

Every ICD is a free reset away. `HERALD_OF_DAWN` carries a 600-tick (30 s) ICD,
`FORTIFYING_BOND` 100 ticks, `HEAT_HAZE` 160.

**Failure scenario**

Player triggers Herald of Dawn, disconnects and reconnects (or the server
restarts); `InternalCooldowns.STORE` no longer has the entry, so `checkAndStart` succeeds
immediately and the Crown power fires again with no cooldown. The saved `powerCooldowns` compound
in their NBT is read back and then ignored.

**Fix**

Either write through `PowerRuntime.InternalCooldowns`/`ProcWindows` to the capability fields
(and rehydrate them on login), or delete the dead capability fields and accessors and document that
ICDs are session-scoped.

---

### [RS-047] `IronsSpellbooksIntegration.isModLoaded()` is invoked from always-loaded classes, defeating the reflective-isolation design

**Severity:** Medium-High | **Confidence:** Medium-High | **Impact:** Latent hard crash without ISS | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/RunicSkills.java — `RunicSkills#<init>` (~line 71); also registry/RegistryPerks.java (~line 332 and ~40 further sites), registry/RegistryPassives.java (~line 77-79), integration/ArsNouveauIntegration.java (~line 159, 213)

**Problem**

`IronsSpellbooksIntegration` imports 10 `io.redspace.ironsspellbooks.*` types and has
`@SubscribeEvent` methods whose descriptors are ISS types. Calling a static method on it forces the
JVM to load, link and **verify** the class. Whether verification eagerly resolves the ISS types
depends on javac's receiver typing for each call site — it happens to be safe today because javac
emits `invokevirtual` with the ISS class as owner, but any refactor that widens an ISS value to a
supertype (e.g. `Event e = spellEvent;`, or a `List<SpellEvent>`) makes the verifier resolve the
missing class and throws `NoClassDefFoundError` from `RunicSkills.<init>`.

**Why it matters**

This is the exact hazard the codebase documents at RunicSkillsClient.java:81-90
("An inline lambda body … references sfiomn.* types … the JVM verifier then tries to check
assignability … and blows up with NoClassDefFoundError") and solves correctly for L2Tabs, Legendary
Tabs, YACL, FTB Quests (`RunicQuestBridge`) and Powers (`IronsSpellbooksPowerCompat`). ISS is the
one integration where the guard itself lives on the foreign-typed class.

**Failure scenario**

A future edit inside any `IronsSpellbooksIntegration` method that assigns an ISS
event to `net.minecraftforge.eventbus.api.Event` (or passes it to a method taking `Event`) makes
every pack **without** Iron's Spells fail to boot, with a stack trace pointing at mod construction.

**Fix**

Move `isModLoaded()` to a foreign-type-free holder (mirror the existing
`ApothicAttributesIntegration` — 12 lines, zero foreign imports) and have `RunicSkills`,
`RegistryPerks`, `RegistryPassives` and `ArsNouveauIntegration` call that instead. Same treatment
for `ArsNouveauIntegration.isModLoaded()` (RegistryPassives.java:82-84).

---

### [RS-048] Optional-mod mixins inherit `defaultRequire: 1` — a version bump in the target mod becomes a crash, not a degradation

**Severity:** Medium-High | **Confidence:** High | **Impact:** Startup crash on target-mod update | **Effort:** Low

**Affected code:**
- src/main/resources/runicskills.mixins.json — `injectors.defaultRequire` (~line 21); affects mixin/MixGunItem.java (~line 23), mixin/MixTargetFinder.java (~line 245), mixin/MixTrueInvisibilityEffect.java (~line 335)

**Problem**

`RunicSkillsMixinPlugin` correctly suppresses these mixins when the target mod is *absent*.
It does nothing when the mod is *present but a different version*. With `defaultRequire: 1`, a
renamed or re-signatured target method (`GunItem#tryFire`, `TargetFinder#findAttackTargetResult`,
`TrueInvisibilityEffect#onDealDamage`) makes Mixin throw `InjectionError` at class transform time,
which Forge surfaces as a hard crash.

**Why it matters**

PointBlank, BetterCombat and Iron's Spells are all actively-updated mods, and
`MixTrueInvisibilityEffect` is explicitly pinned in its own javadoc to "ISS 7402504 (3.15.x)".
The failure mode for an optional cosmetic/QoL hook should be "feature off", not "pack won't launch".

**Failure scenario**

Pack author updates Iron's Spells to a version where
`TrueInvisibilityEffect.onDealDamage` is renamed. Every client crashes on startup with a Mixin
injection error naming `runicskills`, and Runic Skills gets the bug report.

**Fix**

Add `require = 0, expect = 0` to the three optional-mod `@Inject`s (they already have
`@Pseudo`), and log a one-line warning from the plugin instead. Keep `defaultRequire: 1` for the
vanilla mixins.

---

### [RS-049] `GenericNamespaceLockProvider` auto-locks another mod's entire item registry by substring match

**Severity:** Medium-High | **Confidence:** High | **Impact:** Wrong items gated across ~30 mods | **Effort:** Medium

**Affected code:**
- src/main/java/com/otectus/runicskills/integration/lock/LockGen.java — `LockGen#classifyGear` (~line 99-120); registrations in integration/lock/LockProviderRegistry.java (~line 100-145)

**Problem**

30 `GenericNamespaceLockProvider` registrations scan `ForgeRegistries.ITEMS` for a
namespace and classify each item by `String.contains` against keyword tables (LockGen.java:28-51).
Substrings are unanchored: `"cap"` matches *cape*, *escape*, *capacitor*, *mushroom_cap*;
`"bow"` matches *bowl*, *elbow*, *rainbow*; `"axe"` matches *waxed_…*; `"rod"` matches every
`*_rod`; `"orb"` matches *absorbent*, *orbital*; `"club"` matches *clubroot*.

**Why it matters**

A false positive makes another mod's item unusable until the player reaches an
arbitrary skill level, with no per-item review and only a namespace-wide opt-out
(`disabledDiscoveredLockMods`).

**Failure scenario**

`register(new GenericNamespaceLockProvider("aquaculture", 10, "aquaculture"))`
(LockProviderRegistry.java:~106). Aquaculture's headline items are tiered fishing rods
(`aquaculture:iron_fishing_rod`, `neptunium_fishing_rod`, …). `"rod"` is in `MAGIC_KW`, so every
Aquaculture fishing rod is locked behind **Magic 10 + Intelligence 7**. The codebase already knows
this: the comment at LockProviderRegistry.java:~140 says a bespoke `StarcatcherLockProvider` was
needed because *"'rod' → magic (Starcatcher fishing rods)"* — but Aquaculture was left on the
generic provider.

**Fix**

Anchor the keywords (require `_`/start/end boundaries), classify by item *class* first
(`ArmorItem`, `TieredItem`, `ProjectileWeaponItem`, `ShieldItem`, `FoodProperties != null` → skip)
and use keywords only as a tiebreaker, and skip anything whose `Item` is a `BlockItem` or has food
properties. Best fix: drive locks from a datapack tag (see "Tag usage" below) so pack authors can
override per item.

---


---

## 5. Medium Priority Findings

### [RS-050] Full ~600-entry capability state re-serialized and re-sent on every mutation

**Severity:** Medium | **Confidence:** High | **Impact:** High | **Effort:** Moderate

**Affected code:**
- network/packet/client/SyncSkillCapabilityCP.java — send (~43)

**Problem**

`new SyncSkillCapabilityCP(cap.serializeNBT())` — 472 perk ints, 39 passive ints, 11 skill ints, ~79 title booleans, all cooldown maps (~600 named tags, order 15 KB). Only client sync path. Invoked from ~30 sites incl. TickEventHandler#onPlayerTick:49 and Title#setRequirement:69 (once per newly-unlocked title).
Scenario: first login unlocks every Default:true title in one pass -> one full-state packet per title; shipped 77-title default = ~1 MB burst per joining player. TogglePerkSP rate-limited to 1/2 ticks -> holding rank-up sustains ~150 KB/s.

**Fix**

Delta packet (kind,name,value); minimum viable: drop the per-title send in Title#setRequirement, send once at end of RegistryTitles#serverPlayerTitles if anything changed.

### [RS-051] Equipped Power lists deserialized with no cap, dedup, or existence check

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- common/capability/SkillCapability.java — deserializeNBT (~561-571)

**Problem**

`for (int i...) equippedMarks.add(marksTag.getString(i));` — no PowerTier.maxEquipped bound, no dedup, no RegistryPowers.getPower != null. equipPower enforces all three (~292-313) but the load path bypasses it and nothing re-clamps.
Scenario: pack lowers SEAL.maxEquipped 3->2; existing players keep 3 forever since PowerEquipSP only ever adds subject to cap.

**Fix**

Validating loop on load — skip null power, skip dupes, stop at tier.maxEquipped, WARN the dropped ids. Same for equippedCrown.

### [RS-052] /respec requires op but is the only exit from the over-budget perk freeze

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Small

**Affected code:**
- common/command/RespecCommand.java — register (~26)

**Problem**

`.requires(source -> source.hasPermission(2))`. TogglePerkSP freezes all perk enables/rank-ups when RegistryPerks.isOverPerkBudget(capability); the documented remedy is "respec". RespecCommand also does not clear equippedMarks/Seals/Crown or powerCooldowns, so a respec leaves Powers equipped at skill levels that no longer qualify.
Scenario: operator lowers maxPerkBudgetCap 20->12 and runs /skillsreload; every player with 13+ perks can only disable perks until an op runs /respec for each of them individually. On an unattended server that's an outage.

**Fix**

Self-service `/skills respec` at hasPermission(0) (config-gated, optional XP cost); and have respec clear equipped powers, powerCooldowns, powerWindows, perkCooldowns.

### [RS-053] Client capability sync dropped silently with no retry when local player absent

**Severity:** Medium | **Confidence:** Medium | **Impact:** Medium | **Effort:** Small

**Affected code:**
- network/packet/client/SyncSkillCapabilityCP.java — handle (~36)

**Problem**

`if (cap != null) cap.deserializeNBT(nbt);` — drop is correct but terminal. Client's fresh capability is all-defaults; the only unconditional resync is EntityJoinLevelEvent, which is the very packet dropped. Nothing re-requests state.
Why: GUI, item-lock prediction (ClientCapabilityAccess#canUseItemClient) and every getLocal() reader then run on level-1 defaults; server stays authoritative so gameplay is right but the UI lies.

**Fix**

Add a rate-limited RequestSyncSP sent from ClientPlayerNetworkEvent.LoggingIn and on RunicSkillsScreen init; log DEBUG on the drop. (Reachability of the window in 1.20.1 placeNewPlayer ordering is unverified.)

### [RS-054] Direct Integer/Boolean unboxing in three capability accessors

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- common/capability/SkillCapability.java — getPassiveLevel (~243), addPassiveLevel (~248), getLockTitle (~372)

**Problem**

`passiveLevel.get(name)` / `unlockTitle.get(name)` unbox without null check. Neighbouring getSkillLevel/safeLevel (~191-208) and getPerkRank (~255) were explicitly hardened with getOrDefault, with a comment explaining why; these three were missed.
Why: getLockTitle runs on every title scan (every 200 ticks per player); titles come from a config file, so a mismatch is config-shaped, not code-shaped.

**Fix**

`getOrDefault(passive.getName(), 0)` and `getOrDefault(title.getName(), title.Requirement)`; use getOrDefault inside add/subPassiveLevel.

### [RS-055] Title.getRequirement dereferences a nullable capability

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- registry/title/Title.java — getRequirement (~52,56); setRequirement (~60,62)

**Problem**

`SkillCapability.getLocal().getLockTitle(this)` and `SkillCapability.get(player).getLockTitle(this)` — both getters are @Nullable and every other caller null-checks them. setRequirement has a second unguarded dereference at ~62.
Why: setRequirement runs on the join path and on a 200-tick timer; get() returns null for FakePlayer and during the entity-construction window the file's own comments document.

**Fix**

Hoist a null-checked local: `SkillCapability cap = SkillCapability.get(player); return cap != null && cap.getLockTitle(this);`

### [RS-056] Title unlocks are monotonic — op-gated `administrator` title survives de-op (staff impersonation)

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Small

**Affected code:**
- registry/title/Title.java — setRequirement (~59)

**Problem**

SetRequirement has only an unlock branch, no re-lock. RegistryTitles.serverPlayerTitles calls `ADMIN.get().setRequirement(sp, sp.hasPermissions(2))` (~197), so `title.administrator=true` is persisted the first time a player holds perms 2. SetPlayerTitleSP#handle then authorizes purely off that stale persisted flag (`if (!capability.getLockTitle(title)) return;` ~52) rather than re-deriving hasPermissions(2).
Why: displayTitlesAsPrefix (default true) renders every chat line and tab entry as `[Administrator] Name`; titlesUseCustomName (default true) also sets the entity custom name. Same monotonicity means any config title whose requirement later becomes false also stays available.
Scenario: owner grants a helper op for one session; helper joins (syncTitles runs on EntityJoinLevelEvent), unlocking administrator. Owner de-ops. Helper sends SetPlayerTitleSP("administrator"); accepted. They now ask players for base coordinates "as an admin doing a claim audit".

**Fix**

Symmetric re-lock branch in setRequirement (guarded so it doesn't re-fire TitleEarnedEvent/TitleOverlayCP); and in SetPlayerTitleSP#handle call RegistryTitles.serverPlayerTitles(player) immediately before the getLockTitle check, special-casing ADMIN with a direct hasPermissions(2) test.

### [RS-057] `Perk.isEnabled()` allocates a String on every call, and it is called ~100× per melee hit

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/RegistryPerks.java — `RegistryPerks#isDisabled(Perk)` (~line 4400)

**Problem**

`isDisabled(Perk)` evaluates `perk.getMod() + ":" + perk.getName()` as an argument expression, so the concatenation runs *before* `DisabledContentMatcher.matches` gets a chance to short-circuit on an empty `disabledPerks` list. Every `Perk.isEnabled(player)` therefore allocates one `String` even in the overwhelmingly common case where nothing is disabled. 

**Why it matters**

`PerkEffectsHandler.onIncomingDamage` walks 13 `REDUCTIONS` entries plus ~12 inline `on(...)` checks; `onOutgoingDamage` ~22; `CombatEventHandler.onLivingHurtStrengthAttacker` ~20; `onLivingHurtConstitutionDefense` 13. One melee swing runs 4 of these handlers (plus the 15 other `LivingHurtEvent` listeners this mod registers), i.e. ~80–100 `isEnabled` calls → ~100 short-lived Strings per hit, per entity. 

**Failure scenario**

40 players in a mob-grinder arena, 5 hits/sec each landing on 3 entities = ~60 000 String allocations/sec purely from the disabled-list check, driving young-gen GC pressure that other mods get blamed for. 

**Fix**

Reorder to `List<String> l = HandlerCommonConfig...disabledPerks; if (l == null || l.isEmpty()) return false;` before building the full id, or precompute and cache the `modId:path` string on the `Perk` instance (it is immutable).

### [RS-058] `LORE_MASTERY` multiplies *any* XP orb picked up while a grindstone menu happens to be open

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java — `CraftingEventHandler#onPickupXp` (lines 124–137)

**Problem**

The only gate is `sp.containerMenu instanceof GrindstoneMenu`; the orb's *origin* is never checked. Any XP orb the player walks over while the grindstone GUI is open is multiplied by `(value[0] - 1)` and granted via `giveExperiencePoints`. 

**Why it matters**

Same currency-inflation concern as the finding above. 

**Failure scenario**

Place a grindstone inside a mob-grinder collection point, open it, and stand in the orb stream — every furnace/mob/ore orb is silently boosted for as long as the GUI stays open. 

**Fix**

Correlate the pickup with an actual grindstone result-take (e.g. record a "grindstone XP pending" amount in the `AnvilRepairEvent`/`GrindstoneMenu` take path and consume it), or at minimum require `event.getOrb().position()` to be within a block or two of the grindstone.

### [RS-059] The "80% damage-reduction clamp" is applied per handler, not globally — three independent stacked clamps

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Moderate

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java — `CombatEventHandler#onLivingHurtConstitutionDefense` (lines 723–727)

**Problem**

Three separate `LivingHurtEvent` listeners each compute their own reduction and each multiply `event.getAmount()` independently: `onFireDamage` (HIGH, CombatEventHandler:796, capped at `maxFireResist`), `onLivingHurtConstitutionDefense` (LOWEST, capped at 0.80), and `PerkEffectsHandler.onIncomingDamage` (LOWEST, capped at 0.80, PerkEffectsHandler:203–205). The code comment at line 648 states "clamp at 80% so no combination grants invulnerability", but that guarantee only holds within one handler. 

**Why it matters**

The multipliers compose. 

**Failure scenario**

A player with `SEARING_RESISTANCE` (Constitution handler, 80%) + `FIRE_RESISTANCE`/`FIRE_PROOF`/`DRAGONHIDE` (PerkEffects handler, 80%) + a high Endurance level (`onFireDamage`, say 50%) takes fire damage reduced by `1 - 0.2 × 0.2 × 0.5 = 98%` — 100 damage becomes 2. Add vanilla Fire Resistance and armour on top and fire is a no-op, which is exactly what the comment promises cannot happen. 

**Fix**

Accumulate all reductions into one place (a single `LOWEST`-priority handler, or a per-hit accumulator keyed on the event instance) and apply one clamped multiplier.

### [RS-060] `TickEventHandler.onPlayerTick` runs client-side and drops/zeroes item stacks there

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/TickEventHandler.java — `TickEventHandler#onPlayerTick` (lines 22–40)

**Problem**

`TickEvent.PlayerTickEvent` fires on both logical sides. The `dropLockedItems` block has no `event.side` / `level().isClientSide` guard, so on the client it calls `player.drop(hand.copy(), false)` and `hand.setCount(0)` against the client's own inventory. 

**Why it matters**

`Level.addFreshEntity` is a no-op on `ClientLevel`, so no item entity is created, but `hand.setCount(0)` mutates the client-side stack — the item visually vanishes from the hotbar/hand until the next full container sync. The `canUseItem` call also runs twice per player per tick on the client for nothing. Note the sibling `onPlayerTickLow` (line 76) and `PerkEffectsHandler.onAttributeTick` (line 325) both do check the side correctly. 

**Failure scenario**

With `dropLockedItems=true`, a player holding a locked item sees it flicker/disappear client-side while the server still has it, producing ghost-item desync reports. 

**Fix**

Add `if (event.side != LogicalSide.SERVER) return;` at the top of the method.

### [RS-061] `ItemCraftedEvent` handlers run on the client and roll RNG independently, producing ghost bonus items

**Severity:** Medium | **Confidence:** Medium | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java — `PerkEffectsHandler#onCraft` (lines 727–746); also CraftingEventHandler.java:58–94

**Problem**

`ResultSlot.checkTakeAchievements` fires `PlayerEvent.ItemCraftedEvent`, and `AbstractContainerMenu.doClick` runs on both sides (`MultiPlayerGameMode.handleInventoryMouseClick` → `menu.clicked`). Neither `PerkEffectsHandler.onCraft` nor `CraftingEventHandler.onPlayerCraft` checks `isClientSide`. `onCraft` calls `player.getInventory().placeItemBackInInventory(bonus)` and `onPlayerCraft` calls `player.drop(convergenceItem, false)` / mutates `crafted.setDamageValue(...)`, all with an independently-seeded `player.getRandom()` roll on each side. 

**Why it matters**

The client and server reach different RNG outcomes, so the client inserts a bonus item into its predicted inventory that the server does not have (or misses one the server granted). 

**Failure scenario**

A player with `ASSEMBLY_LINE` crafts a stack of planks; the client shows extra planks appearing that vanish on the next container sync, and the `MASTER_TINKERER` durability adjustment is applied twice conceptually (once per side) with a visible durability-bar flicker. 

**Fix**

`if (player.level().isClientSide) return;` at the top of both handlers, or gate on `player instanceof ServerPlayer` as `CRAFTING_LUCK` (CraftingEventHandler:83) already correctly does.

### [RS-062] Food and item-use handlers run client-side; `RAPID_FIRE`/`CROSSBOW_EXPERT` desync bow draw between sides

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java — `PerkEffectsHandler#onFinishEating` (line 669) and `#onItemUseTick` (line 790)

**Problem**

`LivingEntity.completeUsingItem()` runs on both sides (`if (!this.level().isClientSide || this.isUsingItem())`), and `LivingEntity.updatingUsingItem` ticks the use item on both sides, so `LivingEntityUseItemEvent.Finish` and `.Tick` both fire client-side. Neither handler guards the side. `onFinishEating` calls `player.getFoodData().eat(...)`, `player.heal(...)`, `player.addEffect(...)`, `player.removeEffect(...)` on the client's `LocalPlayer`. `onItemUseTick` calls `event.setDuration(event.getDuration() - 1)` behind an *independent* `player.getRandom()` roll on each side. 

**Why it matters**

Food/health/effect state is server-authoritative and the client values are overwritten on the next sync, producing visible flicker; the draw-time perks are worse because the client's predicted bow charge diverges from the server's every tick the rolls disagree. 

**Failure scenario**

`RAPID_FIRE` at 30% — the client believes the bow is fully drawn while the server does not, so the player releases and fires a weak arrow, repeatedly and unpredictably. 

**Fix**

Add `if (event.getEntity().level().isClientSide) return;` to both. For draw-speed specifically, drive it from a deterministic value (an attribute or a fixed reduction) rather than an unsynced RNG roll.

### [RS-063] Combat-memory windows are keyed on `Entity.tickCount`, which resets to 0 on respawn — perks silently lock out for minutes

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java — `SURVIVE_COOLDOWN` / `LAST_KILL_TICK` / `LAST_DODGE_TICK` / `BERSERK_UNTIL` / `LAST_HURT_TICK` (lines 71–98, used at 102, 145, 157, 353, 613, 618, 827, 835, 840, 850)

**Problem**

All six maps store absolute `player.tickCount` values, but `PlayerList.respawn()` constructs a brand-new `ServerPlayer` whose `tickCount` starts at 0. `PerkEffectsHandler.clearPlayer` is called only from `PlayerLifecycleHandler#onPlayerLoggedOut` (PlayerLifecycleHandler.java:73) — never on respawn or `PlayerEvent.Clone`. 

**Why it matters**

The stale absolute values are compared against a counter that just restarted. 

**Failure scenario**

A player triggers `UNDYING_WILL` at `tickCount = 48 000`, so `SURVIVE_COOLDOWN = 49 200`. They later die for real and respawn with `tickCount = 0`. `onDeath` now evaluates `0 < 49 200` → `return` on every subsequent death, so `UNDYING_WILL`/`MYTHICAL_BERSERKER` are dead for the next ~41 minutes of play. Symmetrically, `inKillWindow` (`tickCount - LAST_KILL_TICK < 100`) evaluates `0 - 48000 = -48000 < 100` → true, so `BLOODLUST`'s attack-speed bonus is permanently active after a respawn until `tickCount` catches up. 

**Fix**

Key these maps on `player.level().getGameTime()` (monotonic and dimension-independent, as `CombatEventHandler` already does for `VENGEANCE`/`LAST_STAND`), and call `PerkEffectsHandler.clearPlayer` from `PlayerEvent.Clone` as well.

### [RS-064] `onLivingHurtStrengthAttacker` applies melee-flavoured bonuses (and `CLEAVE`) to any damage the player is the source of

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java — `CombatEventHandler#onLivingHurtStrengthAttacker` (line 340)

**Problem**

The only entry condition is `event.getSource().getEntity() instanceof Player`. There is no `getDirectEntity() == player` melee check, unlike `PerkEffectsHandler.onOutgoingDamage` (line 594) which correctly computes `boolean melee`. So `WEAPON_MASTER` ("wielding a sword/axe/trident"), `SPARTANS_DISCIPLINE`, `RUNIC_MIGHT`, `TITANS_GRIP`, `GLADIATOR` and `CLEAVE` all fire for arrow hits, thrown tridents, TNT the player lit, `BULWARK` thorns reflection (`PerkEffectsHandler.onIncomingDamage` line 222 uses `damageSources().thorns(player)`), and any modded spell that attributes the player as source. 

**Why it matters**

Bonuses documented as melee-weapon-conditional apply to unrelated damage types, and `CLEAVE` in particular fans an AoE out of every one of those. 

**Failure scenario**

A player with `CLEAVE` and a sword in hand lights TNT; the explosion damages 15 mobs, and each of those 15 `LivingHurtEvent`s independently triggers a `CLEAVE` AABB scan and up to 15 more `hurt()` calls each — 225 nested damage dispatches from one TNT. 

**Fix**

Compute `boolean melee = event.getSource().getDirectEntity() == player` and gate the weapon-conditional bonuses and `CLEAVE` on it.

### [RS-065] Dodge cancels `LivingHurtEvent` at `LOWEST` priority, after every other mod has already committed side effects

**Severity:** Medium | **Confidence:** Medium | **Impact:** Medium | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java — `PerkEffectsHandler#onIncomingDamage` (lines 152–160)

**Problem**

`DODGE_ROLL`/`EVASION`/`SPELL_DODGE` roll and `event.setCanceled(true)` at `EventPriority.LOWEST` — the last opportunity before vanilla applies the damage. By then the mod's own `onFireDamage` (HIGH), `onLivingHurtStrengthAttacker` (NORMAL, including the whole `CLEAVE` fan-out), `onOutgoingDamage` (HIGH), and every third-party listener at `HIGHEST`..`NORMAL` have already run and may have applied absorption drain, ward consumption, durability damage, or spawned entities for a hit that is then erased. Cancelling `LivingHurtEvent` also does not undo the knockback, hurt sound, or the 20-tick invulnerability window already set by `LivingEntity.hurt`. 

**Why it matters**

A "dodge" that still grants i-frames and knockback but consumes other mods' per-hit resources is both a balance oddity and a compat hazard. 

**Failure scenario**

An Apotheosis ward absorbs part of a hit at `HIGHEST`, then the dodge cancels the hit at `LOWEST` — the player takes no damage *and* has lost ward, and gets a free 20 ticks of invulnerability. 

**Fix**

Perform the dodge roll at `HIGHEST` (before anyone else has spent resources), or cancel `LivingAttackEvent` instead so the hit never enters the damage pipeline at all.

### [RS-066] Unconditional per-tick work in the Powers dispatcher, plus game-time-modulo scheduling that synchronises all players onto the same tick

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Small

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PowerEventDispatcher.java — `PowerEventDispatcher#onPlayerTick` (lines 877–905, 934, 968)

**Problem**

For every server player, every tick, the handler unconditionally resolves the capability and calls `PowerRuntime.PositionBuffer.push(...)` (common/powers/PowerRuntime.java:241) — a `synchronized` static method on a shared `WeakHashMap` that allocates a `Snapshot` record and does an `ArrayDeque` insert/trim — regardless of whether the player has *any* Power equipped (the buffer only feeds `ROOTED` and `UNRAVELED`). Separately, the `THUNDER_LORD`/`GLACIAL_SOVEREIGN`/`ROOTED` blocks gate on `now % 40 == 0`, `now % 20 == 0`, `now % 60 == 0` where `now` is the *global* `getGameTime()`, so every eligible player performs their AABB scan on the same tick. 

**Why it matters**

The unconditional push is ~20 record allocations/sec/player plus monitor acquisition for a feature almost no one uses; the modulo scheduling converts a smooth cost into periodic spikes. `THUNDER_LORD` and `GLACIAL_SOVEREIGN` each do a `getEntitiesOfClass` over a 10–12 block inflated AABB. 

**Failure scenario**

60 players on a busy server, 15 with `GLACIAL_SOVEREIGN` — every 20th tick, 15 simultaneous 24³-block entity scans plus effect re-application land in the same tick, showing up as a recurring 1-per-second tick spike in Spark. 

**Fix**

Skip the `PositionBuffer.push` unless the player has a Power that consumes it; and phase the modulo checks per player (`(now + player.getId()) % 40 == 0`).

### [RS-067] Datapack `skill_visuals` overrides never reach clients on a dedicated server

**Severity:** Medium | **Confidence:** High | **Impact:** Feature silently no-ops in multiplayer | **Effort:** M

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/skill/SkillVisualsReloadListener.java` — `#apply` (lines 55-95); registration at `registry/events/PlayerLifecycleHandler.java:116`

**Problem**

The listener is registered through `AddReloadListenerEvent`, i.e. it is a **server datapack**
listener. `apply()` mutates the server-side `Skill` objects via `skill.setVisuals(...)`. The values
are consumed only by `RunicSkillsScreen` rendering (`Skill#getOverviewIcon/getDetailIcon/
getBackgroundTexture`, Skill.java:149-165). There is no `SkillVisualsSyncCP` (grep: no sync packet
of any kind for this data), and the client never reads server datapacks.

**Why it matters**

It works in single-player only because both logical sides share one JVM and one set
of `Skill` instances — which is exactly the environment a developer tests in.

**Failure scenario**

A pack ships `data/mypack/runicskills/skill_visuals/magic.json`. In single-player
the icon changes. On the pack's dedicated server, every client still renders the stock textures and
the server logs "Loaded 1 skill visual override(s)" — with no indication that nothing happened.

**Fix**

Add a sync packet mirroring `PerkGroupsSyncCP` (send at `PlayerLoggedInEvent` and after
`/skillsreload`), or move the listener to `RegisterClientReloadListenersEvent` and re-document it as
a resource-pack (assets) feature.

---

### [RS-068] `SkillVisualsReloadListener.previouslyOverridden` is per-instance but the instance is recreated each reload

**Severity:** Medium | **Confidence:** High | **Impact:** Removed overrides never revert | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/skill/SkillVisualsReloadListener.java` — field at line 48, cleanup loop at lines 84-89

**Problem**

The class javadoc promises "Removing the JSON via `/reload` restores the legacy hardcoded
visuals — the listener tracks the previous keyset and clears overrides on skills that drop out."
That tracking is an instance field, but `PlayerLifecycleHandler#onAddReloadListeners` constructs a
**new** `SkillVisualsReloadListener()` every time `AddReloadListenerEvent` fires — and Forge fires it
on every `/reload` (a fresh `ReloadableServerResources` is built each time). `previouslyOverridden`
is therefore always empty when `apply()` runs, so the "clear dropped overrides" loop is a no-op.

**Why it matters**

The override lives on the long-lived `Skill` registry object, which outlives the
listener. Only the listener's memory is reset.

**Failure scenario**

Pack author adds `skill_visuals/magic.json`, `/reload`s (icon changes), deletes the
file, `/reload`s again. The custom icon persists until the server restarts, and the author concludes
the file wasn't the source.

**Fix**

Make `previouslyOverridden` `static`, or drop the tracking entirely and clear visuals on every
skill at the top of `apply()` before re-applying the current map.

---

### [RS-069] `PowerOverridesReloadListener` claims the generic `powers/` datapack folder

**Severity:** Medium | **Confidence:** Medium-High | **Impact:** Cross-mod data collision, log spam | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/powers/PowerOverridesReloadListener.java` — `FOLDER = "powers"` (line 61)

**Problem**

`SimpleJsonResourceReloadListener` scans `data/<any-namespace>/powers/*.json` across every
loaded datapack, not just `runicskills`. `powers/` is the canonical folder of Apoli/Origins (a very
common Forge 1.20.1 pack mod) and of several other ability mods. `PerkGroupsReloadListener` has the
same shape with `perk_groups` (line 42); by contrast `SkillVisualsReloadListener` correctly namespaces
its folder as `runicskills/skill_visuals`.

**Why it matters**

Every foreign `powers/*.json` is parsed by `PowerOverrides#parse`, which accepts any
JSON object, so it is silently stored in `PowerOverridesManager` under a foreign id — inflating the
"Loaded N power override(s)" count and, for non-object files, emitting an error per file per reload.
It also means another mod's data can never be distinguished from a real override.

**Failure scenario**

Pack with Origins installed. On every `/reload` and world load the log reports
"Loaded 214 power override(s) from datapacks", and any Origins power file that isn't a JSON object
logs "Failed to load power override …".

**Fix**

Move both folders under the mod namespace (`runicskills/powers`, `runicskills/perk_groups`),
matching the pattern already used for skill visuals. Add a migration note; keep reading the legacy
folder for one release if back-compat matters.

---

### [RS-070] `/reload` does not resync perk groups or power overrides to clients; `/skillsreload` misses power overrides

**Severity:** Medium | **Confidence:** High | **Impact:** Client shows stale rules | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/perks/PerkGroupsReloadListener.java` — `#apply` (line 63); `/home/claude/rs/src/main/java/com/otectus/runicskills/common/command/SkillsReloadCommand.java` — `#execute` (lines 101-112)

**Problem**

Both reload listeners call `…Manager.replaceAll(next)` and stop. Neither broadcasts to
online clients. `PlayerLifecycleHandler:64-65` sends `PerkGroupsSyncCP` + `PowerOverridesSyncCP` only
at `PlayerLoggedInEvent`. `SkillsReloadCommand` re-sends `PerkGroupsSyncCP` but **not**
`PowerOverridesSyncCP` — despite `PowerOverridesSyncCP`'s own javadoc (line 21) stating it is
"sent at login and after `/skillsreload`".

**Why it matters**

The client uses these for pre-validation and tooltips; a stale client offers actions
the server will silently reject (the reject path is just "resync the client", per `TogglePerkSP`).

**Failure scenario**

Admin edits a `perk_groups` JSON and runs vanilla `/reload`. The server now
enforces "berserker XOR juggernaut", but every online client still shows both as selectable; clicking
one appears to do nothing.

**Fix**

Broadcast `PerkGroupsSyncCP`/`PowerOverridesSyncCP` to all players at the end of each listener's
`apply()` (guarding for the server-start case where no players exist), and add the missing
`PowerOverridesSyncCP.sendToPlayer(sp)` to `SkillsReloadCommand`.

---

### [RS-071] Reload-invalidated state is never reconciled on players who already hold it

**Severity:** Medium | **Confidence:** High | **Impact:** Rules apply only to new selections | **Effort:** M

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/perks/PerkGroupManager.java` — `#firstBlockingGroup` (lines 41-51); `network/packet/common/TogglePerkSP.java` (lines 126-133)

**Problem**

Perk-group caps, the school-attunement cap, the disabled lists and the power
`required_skill_level` override are all evaluated **only on the enable/equip transition**. Nothing
re-examines existing state after a datapack or config reload. The single exception is the perk
*budget*, which has an over-budget freeze (`RegistryPerks#isOverPerkBudget`, line ~4386) that blocks
further activation until `/respec` — a mitigation that exists for budgets but for nothing else.

**Why it matters**

A pack author's balance change is retroactive for nobody who has already progressed,
which is precisely the population it is meant to affect.

**Failure scenario**

A pack ships a new `perk_groups` file making `berserker` and `juggernaut` mutually
exclusive. Every existing player who already has both keeps both indefinitely; only fresh players
are constrained. Same for a `powers/*.json` that raises `required_skill_level` from 30 to 60 — no
already-equipped Power is dropped.

**Fix**

Add a single `reconcile(ServerPlayer)` entry point that (a) unequips powers failing their
current gates, (b) disables perks over a group cap (lowest-priority-first, with a chat notice), and
(c) is invoked from both reload listeners for all online players and from `EntityJoinLevelEvent`.

---

### [RS-072] `break_speed` and `mining_speed` are the same attribute under the default Apothic config

**Severity:** Medium | **Confidence:** High | **Impact:** Duplicate stat, double-dipping | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryPassives.java` — `BREAK_SPEED` (line 61) and `APOTHIC_MINING_SPEED` (line 92); `integration/ApothicPassiveHelper.java#createMiningSpeedPassive` (line 84)

**Problem**

`BREAK_SPEED` resolves its attribute through `RegistryAttributes.getEffectiveBreakSpeed()`,
which returns `ApothicPassiveHelper.getMiningSpeed()` (= `attributeslib:mining_speed`) whenever
Apothic is loaded and `apothicDelegateMiningSpeed` is true — the **default**
(`HandlerCommonConfig.java:4412`). `APOTHIC_MINING_SPEED` targets the same
`ALObjects.Attributes.MINING_SPEED` directly. Both are Building-skill passives, both apply
`Operation.ADDITION`, with distinct UUIDs (`…511` vs `…535`) so both stack.

**Why it matters**

The UI presents two separately-levellable passives that are the same stat, and their
bonuses add. Any balance tuning of one is silently halved in effect.

**Failure scenario**

Pack with Apotheosis/AttributesLib (default config). A Building player maxes both
"Break Speed" and "Mining Speed" and receives `breakSpeedValue + apothicMiningSpeedValue` on one
attribute, roughly double the intended cap.

**Fix**

When delegation is active, suppress `APOTHIC_MINING_SPEED` (or `BREAK_SPEED`) — either by not
registering it or by adding it to the effective `disabledPassives` set — and surface the choice in
the config comment.

---

### [RS-073] Toggling Apothic delegation leaves an orphaned permanent modifier on the abandoned attribute

**Severity:** Medium | **Confidence:** Medium-High | **Impact:** Stale stat bonus survives forever | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryAttributes.java` — `#modifierAttributes` (lines 75-82) and `#getEffectiveCritDamage` (lines 44-49)

**Problem**

`passive.attribute` is captured once, when the `DeferredRegister` supplier runs
(`RegistryPassives.java:72` calls `RegistryAttributes.getEffectiveCritDamage()` at registration).
`modifierAttributes` only ever reconciles `passive.attribute` for the *currently registered*
passives, using `addPermanentModifier` — which is persisted in player NBT. Nothing removes a
modifier from the attribute the passive used to point at.

**Why it matters**

The delegation flags are user-facing toggles; flipping one and restarting
permanently strands the old modifier.

**Failure scenario**

Server runs with `apothicDelegateCritDamage = true`; players accumulate a permanent
modifier (UUID `…515`) on `attributeslib:crit_damage`. The admin sets it to `false` and restarts. The
`critical_damage` passive now writes to `runicskills:critical_damage`, but the old
`attributeslib:crit_damage` modifier remains in every player's NBT and is re-loaded on every login —
Apothic keeps applying it. Players silently keep both bonuses.

**Fix**

On login, sweep every passive UUID off *all* candidate attributes (the runicskills one and the
Apothic one) before applying the current one; or store the previously-used attribute id in the
capability and clean it up on change.

---

### [RS-074] `titlesUseCustomName` is ignored by the periodic title sync; the chat prefix never refreshes

**Severity:** Medium | **Confidence:** High | **Impact:** Config toggle does nothing; stale display name | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryTitles.java` — `#syncTitles` (lines 169-177); compare `network/packet/common/SetPlayerTitleSP.java` (lines 60-64)

**Problem**

`SetPlayerTitleSP` correctly gates the custom-name write:

```java
if (HandlerCommonConfig...titlesUseCustomName) {
    player.setCustomName(...); player.refreshDisplayName(); player.refreshTabListName();
}
```

`syncTitles` does the same write with **no** flag check and **no** refresh:

```java
Title title = getTitle(SkillCapability.get(serverPlayer).getPlayerTitle());
if (title != null) serverPlayer.setCustomName(Component.translatable(title.getKey()));
```

and it runs on every world join, every `PlayerEvent.Clone`, every passive level-up, and every 200
ticks (`TickEventHandler.java:78-80`).

**Why it matters**

(a) `titlesUseCustomName = false` does not actually prevent the player's
`CustomName` from being set, which other mods and `@e[name=…]` selectors observe. (b) The separate
`displayTitlesAsPrefix` path (`PlayerLifecycleHandler#onPlayerNameFormat`) is cached by Forge in
`Player.displayname` and only recomputed on `refreshDisplayName()`, which `syncTitles` never calls.

**Failure scenario**

`displayTitlesAsPrefix = true`, `titlesUseCustomName = false`. A player changes
title via the GUI; `SetPlayerTitleSP` skips the refresh because the flag is off, so their chat/tab
prefix still shows the old title until they relog — while their `CustomName` was nevertheless
overwritten by the next 200-tick scan.

**Fix**

Gate the `setCustomName` in `syncTitles` on the same flag; call `refreshDisplayName()` +
`refreshTabListName()` whenever the selected title changes, regardless of which flag is on.

---

### [RS-075] User-authored ids become ResourceLocations with no validation

**Severity:** Medium | **Confidence:** High | **Impact:** Startup crash from a config typo | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/config/models/TitleModel.java` — `#registry` (line 154) and `#register` (line 159); also `registry/perks/Perk.java#add` (line 62) and `registry/passive/Passive.java#add` (line 200)

**Problem**

`TITLES.register(TitleId, () -> _title)` passes a raw config string as a registry path.
`DeferredRegister#register` immediately builds `new ResourceLocation(namespace, name)`, which throws
`ResourceLocationException` for anything containing uppercase letters, spaces, or `:`. The load path
(`RegistryTitles#load`, lines 51-66) validates only null/empty and duplicates. The KubeJS entry
points `Perk.add` / `Passive.add` have the same gap. Note `HandlerResources#parseTexture`
(line 620-633) has a matching hole: the bare-path branch `new ResourceLocation(MOD_ID, path)` is
*outside* the try/catch that guards the qualified branch.

**Why it matters**

Title ids are the documented extension point for pack authors, and the failure is a
hard crash during mod construction with a stack trace that points at Forge, not at the user's file.

**Failure scenario**

A pack author adds `{"TitleId": "Dragon Slayer", ...}` to
`runicskills.titles.json5`. The game crashes at startup with
`ResourceLocationException: Non [a-z0-9/._-] character in path of location: runicskills:Dragon Slayer`
and no mention of which title or which file.

**Fix**

Validate with `ResourceLocation.isValidPath` (or `tryParse`) in `RegistryTitles#load` /
`Perk.add` / `Passive.add`, log the offending id + file and skip the entry; move `parseTexture`'s
bare-path construction inside its try block.

---

### [RS-076] Passives are not re-gated by skill level at runtime, unlike perks

**Severity:** Medium | **Confidence:** High | **Impact:** Stat bonuses survive skill loss | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryAttributes.java` — `#modifierAttributes` (line 79); `common/command/SkillLevelCommand.java#subtractSkill` (lines 117-139)

**Problem**

The passive modifier value is `passive.getValue() / passive.levelsRequired.length *
passive.getLevel(player)` — purely a function of the stored *passive* level. The skill-level
requirement (`passive.levelsRequired[i]`) is checked only in `PassiveLevelUpSP` at purchase time.
`Perk#isEnabled` re-checks `cap.getSkillLevel(getSkill()) >= requiredLevel` on every call, so perks
do auto-disable — the two systems behave differently. `SkillLevelCommand#setSkill/addSkill/
subtractSkill` additionally never call `modifierAttributes`, so even the value is not recomputed.

**Failure scenario**

Admin runs `/skills <player> constitution subtract 40`. Every Constitution perk
immediately stops working (correct), but the player keeps all Max Health passive levels and the full
`+HP` modifier, which is not even recomputed until the next passive change or relog.

**Fix**

Clamp `getPassiveLevel` against the current skill level inside `modifierAttributes` (or refund
the levels), and call `RegistryAttributes.modifierAttributes(player)` from all four
`SkillLevelCommand` mutators.

---

### [RS-077] `Skill#getPerks` / `getPassives` rebuild the whole registry list twice per iteration

**Severity:** Medium | **Confidence:** High | **Impact:** ~450k redundant element copies per GUI rebuild | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/skill/Skill.java` — `Skill#getPerks` (lines 56-64) and `#getPassives` (lines 65-73)

**Problem**

```java
for (int i = 0; i < RegistryPerks.PERKS_REGISTRY.get().getValues().stream().toList().size(); i++) {
    Perk perk = RegistryPerks.PERKS_REGISTRY.get().getValues().stream().toList().get(i);
```

The registry is streamed and materialised into a fresh `List` in both the loop condition and the
loop body — 2 × 471 list constructions of 471 elements each per call. `RegistryPerks.getCachedValues()`
already exists (RegistryPerks.java:~4319) and does exactly the right thing.

**Why it matters**

The caller is `RunicSkillsScreen#buildDetailPageState` (line 680-681), invalidated on
every click, page change and capability sync — so this runs interactively on the client thread.

**Failure scenario**

Opening the skills GUI and clicking through pages copies ~444k object references
per click, on the render thread, for a result the mod already caches elsewhere.

**Fix**

`for (Perk perk : RegistryPerks.getCachedValues()) if (perk.getSkill() == skill) list.add(perk);`
— same for passives. Consider caching the per-skill split once (the `Skill.list` field and
`setList()` exist for this and are unused).

---

### [RS-078] `MixForgeGui` overwrites `ForgeGui.rightHeight` instead of incrementing it, and unconditionally cancels `renderAir`

**Severity:** Medium | **Confidence:** High | **Impact:** Right-side HUD stacking breaks for every other mod's overlay; other renderAir modifications are silently discarded | **Effort:** Trivial

**Affected code:**
- `src/main/java/com/otectus/runicskills/mixin/MixForgeGui.java` — `MixForgeGui#renderAirs` (~line 26–52, assignment at line 47)

*Independently corroborated in the integrations/mixins pass.*

**Problem**

Vanilla `ForgeGui#renderAir` ends with `rightHeight += 10;` — the right-side HUD column
is a running cursor that food, air and every third-party overlay contribute to. The mixin
replaces this with an absolute assignment:

```java
this.this$class.rightHeight = 10;
```

Any overlay that renders after `AIR_LEVEL` and reads `ForgeGui.rightHeight` to place itself
(the standard Forge idiom for stacking right-side bars — thirst, stamina, mana, temperature
mods) now computes its Y from `10` instead of `~49`, and draws on top of the hunger/air row.
Separately, the injection is `@At("HEAD")` + `info.cancel()` with no condition, so it
unconditionally suppresses the vanilla implementation — any other mod that also injects into
`renderAir`, or that relies on Forge's own behaviour, loses. The only functional delta the
rewrite actually adds is replacing vanilla's hard-coded `300.0D` with
`player.getMaxAirSupply()`.

**Why it matters**

This is a whole-HUD mixin that changes global layout state for a cosmetic
improvement to one bar. It is exactly the kind of change that produces "Runic Skills breaks my
thirst bar" reports.

**Failure scenario**

Install any mod that registers a right-side bar above `AIR_LEVEL` and go
underwater. Its bar jumps down onto the hunger row for as long as the air bar is visible.

**Fix**

Change to `this.this$class.rightHeight += 10;`. Better: drop the mixin entirely and use the
supported API — cancel `RenderGuiOverlayEvent.Pre` for `VanillaGuiOverlay.AIR_LEVEL` and
register a replacement layer via `RegisterGuiOverlaysEvent` (the mod already uses that API for
its own three overlays), so other mods can opt out of the replacement.

---

Severity: Medium | Confidence: High | Impact: HUD overlap with other mods | Effort: Low
File: src/main/java/com/otectus/runicskills/mixin/MixForgeGui.java — `MixForgeGui#renderAirs` (~line 26-52, assignment at line 47)

Problem: Two issues in one method. (1) `info.cancel()` runs at HEAD **unconditionally**, whether or
not the Athletics perk is active, so Runic Skills owns the air bar in every pack. (2) Vanilla
`ForgeGui.renderAir` does `right_height += 10`; the mixin does `this$class.rightHeight = 10`,
clobbering whatever the armor bar / other right-side overlays have accumulated this frame.

Why it matters: `rightHeight` is Forge's shared cursor for right-side HUD stacking — it is the
documented cooperation point for HUD mods. Overwriting it breaks every overlay drawn after air.

Failure scenario: Player is underwater wearing armor. Armor bar sets `rightHeight` to 39; Runic
Skills resets it to 10; the next overlay (a mod's stamina/thirst/mana bar registered after
`AIR_LEVEL`) draws on top of the hotbar/armor row. Also, mods that mixin `renderAir` (AppleSkin-style
HUD packs, Overloaded Armor Bar) silently lose their injection because the method is cancelled at
HEAD.

Fix: `this$class.rightHeight += 10;`, and only cancel when the Athletics perk is actually active on
the camera player (otherwise return without cancelling and let Forge draw).

---

### [RS-079] Server rejects perk toggles for eight distinct reasons; the client shows a reason for exactly one

**Severity:** Medium | **Confidence:** High | **Impact:** Clicks that "do nothing" with no explanation — the most common UX complaint class for skill mods | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/network/packet/common/TogglePerkSP.java` — `#handle` (~lines 78–142) Client side: `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#handleDetailClick` (~line 1012–1024), `#drawPerkIcon` (~line 550)

**Problem**

`TogglePerkSP#handle` has eight rejection paths — over-budget freeze, disabled-in-config,
rank out of bounds, insufficient skill level for the requested rank, school-attunement limit,
global active-perk cap, `PerkGroupManager` mutual exclusion, and perk-swap cooldown — plus a
cancellable `PerkToggleEvent.Pre`. Only the over-budget path sends anything back
(`NoticeOverlayCP`); the other seven do `SyncSkillCapabilityCP.send(player); return;` — a silent
state resync.

Meanwhile the client sends the packet after checking only `perk.getToggle()` (skill level ≥
`requiredLevel` for rank 1). It does not pre-check the active-perk cap, the school limit, perk
groups, or the swap cooldown, and it plays the confirmation click sound (`Utils.playSound()`)
*before* sending. So a rejected toggle looks exactly like an accepted one for one frame, then
snaps back with no message.

**Why it matters**

With 471 perks, a global cap, a scaled per-global-level cap, data-driven
exclusion groups and a swap cooldown, "nothing happened" is the default experience once a player
is near any limit. Neither the perk-swap cooldown nor the earned global level is surfaced
anywhere in the UI, so the player cannot even deduce the reason.

**Failure scenario**

Player at the active-perk cap clicks an unowned perk. Click sound plays, icon
does not change, no message. Repeat forever.

**Fix**

(1) reuse the existing `NoticeOverlayCP` mechanism for every rejection branch — it already
takes a key plus string args and renders over an open GUI; (2) mirror the cheap checks
client-side (cap, cooldown, group) and grey out / red-frame the icon plus a tooltip line stating
the blocker, so the click is prevented rather than rejected; (3) move `Utils.playSound()` to
after a successful local pre-check, and use a distinct "denied" sound otherwise.

---

### [RS-080] Locked titles render identically to unlocked ones and clicking one gives zero feedback

**Severity:** Medium | **Confidence:** High | **Impact:** Titles page reads as broken; colour is the only status channel and it does not encode lock state at all | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#drawTitles` (~line 355–375, colour at line 364), `#handleTitleClick` (~line 1060–1067), `#getFilteredTitles` (~line 722–755)

**Problem**

`getFilteredTitles()` deliberately includes locked titles (`!title.HideRequirements`)
in the list. `drawTitles` then picks the text colour purely from *selection*:

```java
int textColor = selectedTitle ? Utils.TITLE_SELECTED_COLOR : Utils.TITLE_UNSELECTED_COLOR;
```

so a locked title and an unlocked-but-not-current title are both drawn in the same orange
(`0xFFAA00`), with the same hover highlight. `handleTitleClick` guards with
`&& title.getRequirement()`, so clicking a locked row falls through the loop, returns `false`,
plays no sound and shows no message.

**Why it matters**

The only distinguishing information is inside the hover tooltip
(`title.runicskills.requirement_description`), which a player has to discover. Everything else
about the row invites a click that does nothing.

**Failure scenario**

80 titles are registered; a new player sees a long orange list, clicks a title
they have not unlocked, nothing happens, and concludes titles are broken.

**Fix**

Colour locked rows dark grey and prefix them with a lock glyph (not colour alone — see the
accessibility finding); on a click of a locked row, play a denial sound and show the requirement
via `OverlayNoticeGui.show(...)`.

---

### [RS-081] Rendering the skill icon mutates the authoritative capability client-side, and NPEs if it is not resolved

**Severity:** Medium | **Confidence:** High | **Impact:** Client writes to shared state during a render pass; unguarded NPE on a nullable accessor | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/registry/skill/Skill.java` — `#getLockedTexture(int)` (~line 103) and `#getLockedTexture()` (~line 120), reached from `RunicSkillsScreen#drawOverview` (~line 271) and `OverlaySkillGui#draw` (~line 94) Related: `src/main/java/com/otectus/runicskills/registry/title/Title.java` — `#getRequirement()` (~line 51–53), reached from `RunicSkillsScreen#getFilteredTitles` (~line 728) and `#handleTitleClick` (~line 1062)

*Independently corroborated in the progression pass.*

**Problem**

The texture selector clamps out-of-range levels by *writing* to the capability from a
render path:

```java
if (getLevel() > size) {
    SkillCapability.getLocal().setSkillLevel(this, size);   // no null check, and it is a write
}
```

Two defects. (1) `SkillCapability.getLocal()` is explicitly `@Nullable` (it returns
`LOCAL_SUPPLIER.get()`, which is `player.getCapability(...).orElse(null)`), and every other
call site in the codebase null-checks it — these two do not. (2) Even when non-null, the client
is silently rewriting its own copy of server-authoritative state during `render()`. The write is
never sent to the server, so the display drifts from the server's value until the next
`SyncSkillCapabilityCP` re-overwrites it, at which point the clamp fires again — a
write/resync ping-pong. `Title#getRequirement()` has the same unguarded
`SkillCapability.getLocal().getLockTitle(this)`.

**Why it matters**

Reachable whenever `skillMaxLevel` is lowered below a player's stored level
(the scenario the surrounding comment describes), and whenever `Minecraft.player` is null/being
replaced while a screen or the HUD overlay is up (dimension change, respawn, disconnect
mid-frame). The NPE lands inside `GuiGraphics` rendering.

**Failure scenario**

Op lowers `skillMaxLevel` from 100 to 50; a player with STR 80 opens Skills.
Every frame the icon getter forces the local level to 50, the display shows 50, the server still
says 80, and the next sync flips it back — visible flicker plus a wrong number.

**Fix**

Make the clamp read-only — `int lvl = Math.min(getLevel(), size);` — and null-guard both
`getLocal()` calls (`Skill#getLockedTexture`, `Title#getRequirement`) returning a safe default.
Enforce the clamp server-side in `SkillCapability` on config reload instead.

---

Severity: Low-Medium | Confidence: High | Impact: Client/server skill-level divergence | Effort: S
File: `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/skill/Skill.java` — `#getLockedTexture` (lines 95-113 and 115-133)

Problem: Both overloads contain

```java
if (getLevel() > size) SkillCapability.getLocal().setSkillLevel(this, size);
```

inside a texture getter called from the render path. This writes to the *client's* capability copy
with no packet to the server, so the two sides disagree until the next `SyncSkillCapabilityCP`. It
also dereferences `getLocal()` without the null check that `getLevel()` itself performs (Skill.java:76),
and the surrounding `index >= 4` clamp hard-codes the four-entry texture array
(`HandlerResources.STRENGTH_LOCKED_ICON`, line 22-27) rather than using `lockedTexture.length`.
Separately, `#getRank` (line 85-93) computes `(skillMaxLevel / 8) * i` with integer division, so any
`skillMaxLevel < 8` makes every rank threshold 0 and the player always displays rank 8.

Failure scenario: Admin lowers `skillMaxLevel` from 100 to 50. A level-80 player opens the GUI; the
client silently rewrites its own level to 50 while the server still stores 80, so perks the client
thinks are locked still fire server-side (and vice versa) until relog.

Fix: Make the getters pure — clamp the *index*, not the stored level — and do any level clamping
server-side once, on login, with a sync packet. Use `lockedTexture.length - 1` instead of `3`.

---

### [RS-082] `OverlaySkillGui` / `OverlayNoticeGui` draw twice per frame while a screen is open; the comment asserting mutual exclusion is wrong

**Severity:** Medium | **Confidence:** High | **Impact:** Double-composited alpha (visibly darker banner), double render cost, and a wrong invariant baked into two files | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/gui/OverlaySkillGui.java` — `#render` (~line 41–46) and `#onScreenRender` (~line 56–60) Same pattern: `src/main/java/com/otectus/runicskills/client/gui/OverlayNoticeGui.java` — `#render` (~line 39–43), `#onScreenRender` (~line 45–49)

**Problem**

Both classes register as a Forge HUD overlay layer *and* subscribe to
`ScreenEvent.Render.Post`, on the stated assumption that "Forge skips this whole pass while a
Screen is up". That is not true in 1.20.1: `GameRenderer#render` calls
`minecraft.gui.render(...)` whenever `minecraft.level != null`, *before* rendering the screen —
which is why the entire HUD (hotbar, hearts, crosshair) stays visible while the chat screen is
open. `ForgeGui#render` then dispatches every registered `IGuiOverlay`, including
`runicskills:skill_overlay` and `runicskills:notice_overlay`. So with any screen open, `draw()`
runs once under the screen and once over it.

The class's own comment contradicts the claim: it says the warning "used to vanish *behind* the
crafting menu" — behind, i.e. it was drawn and occluded, not skipped.

**Why it matters**

`OverlaySkillGui#draw` composites 16 alpha-stacked black `fill` quads to build
its vignette; doing that twice makes the backdrop measurably darker with a GUI open than without,
and `OverlayNoticeGui`'s `alpha * 0.6` backdrop likewise. It is also double work on the render
thread, and future maintainers will reason from the false invariant.

**Failure scenario**

Trigger a locked-item warning with no screen open (normal darkness), then with
the crafting table open (noticeably darker backdrop + slightly different fade curve).

**Fix**

Guard the HUD-layer path with `if (this.client.screen != null) return;` inside
`render(ForgeGui, ...)` (or, equivalently, guard the `ScreenEvent.Render.Post` path — but keep
exactly one), and correct the comments.

---

### [RS-083] `OverlayTitleGui` animation is frame-rate driven, divides by zero at the ends, and stalls whenever a GUI is open

**Severity:** Medium | **Confidence:** High | **Impact:** Pop-in animation runs 4× faster on a 240 Hz display; degenerate transform each time a title finishes; title queue does not advance while a screen is open | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/gui/OverlayTitleGui.java` — `#render` (~line 37–70)

**Problem**

Three coupled issues in one method.

1. `scaleTick` is incremented/decremented inside `render()` (per frame) while `showTicks` is
   decremented in `onClientTick` (per tick). The 20-step scale-in therefore takes 1 s at
   20 fps, 0.33 s at 60 fps and 0.08 s at 240 fps.
2. `scaleTick` is clamped to `[0, 20]`, and `scale2 = 0.05625F * scaleTick` /
   `scale1 = 0.1F * scaleTick`. When `scaleTick == 0` (which happens on the frame the banner
   finishes, every time), the code computes `guiScaledWidth / scale1 / 2.0F` → `Infinity`, casts
   to `int` → `Integer.MAX_VALUE`, and then does `x - font.width(...)/2` → signed overflow, all
   inside a `pose().scale(0, 0, 1)` degenerate matrix.
3. `list.dequeue()` only happens inside `render()`, but `render()` is the HUD-layer path — and
   unlike `OverlaySkillGui`/`OverlayNoticeGui`, this overlay has **no**
   `ScreenEvent.Render.Post` mirror and no `hideGui` handling. `showTicks` keeps ticking down
   in `onClientTick` regardless. Earn several titles while the inventory is open and the queue
   drains only on the frames the HUD actually draws.

**Why it matters**

(1) and (2) are visible glitches; (3) means titles earned during a GUI session
either stack up or flash past. It also means this overlay is inconsistent with the two sibling
overlays which explicitly added the screen-open path.

**Failure scenario**

Complete a title while the crafting GUI is open; `showTicks` counts to 0 with
no render, so on close the banner appears already-expired at `scaleTick == 0` (the divide-by-zero
frame) and is immediately dequeued.

**Fix**

Drive `scaleTick` from `onClientTick`, not `render`; guard `scaleTick == 0` with an early
return before computing the scale; and move the dequeue into `onClientTick`.

---

### [RS-084] The level-up button pulses at up to ~6 Hz on high-refresh displays, and pulse state is mutated from `render`

**Severity:** Medium | **Confidence:** High | **Impact:** Photosensitivity risk (WCAG 2.3.1 "three flashes" threshold), and frame-rate-dependent UI behaviour | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#drawLevelUpButton` (~line 458–500)

**Problem**

`this.tick++`, `this.pulseOn = !this.pulseOn`, `this.tick = this.maxTick - 5` and
`this.pulseOn = canLevelUpSkill` are all executed inside the render method, with
`maxTick = 40` counted in **frames**, not ticks. At 60 fps the icon alternates every 0.67 s
(~1.5 Hz); at 240 fps every 0.17 s (~6 Hz); with an uncapped frame rate on a modern GPU the
Skills screen (which is not a pause screen — `isPauseScreen()` returns `false`, so vsync/60-cap
is whatever the user set) can go higher still. The mutation on hover (`this.tick = this.maxTick
- 5`) also means hovering changes the animation phase.

**Why it matters**

WCAG 2.3.1 sets three flashes per second as the general-flash threshold; a
high-contrast full-icon swap at 6 Hz over a small area is squarely in the "avoid" zone, and
there is no setting to disable it. Separately, animation that advances per frame instead of per
tick is a correctness bug — the same UI behaves differently on different machines.

**Failure scenario**

Player with an uncapped 240 Hz client opens a skill detail page with enough XP
to level; the level-up arrow strobes.

**Fix**

Drive the pulse from `Screen#tick()` (20 Hz, deterministic) with `maxTick` in ticks, cap the
alternation at ≤ 2 Hz, and add a client config toggle (`reduceMotion` / `disableHudAnimations`)
that renders the "can level up" state as a static highlight instead.

---

### [RS-085] `TooltipWrap.wrap` flattens wrapped lines to plain literals, destroying inline colour and translation structure

**Severity:** Medium | **Confidence:** High | **Impact:** Any tooltip line wider than 200 px loses all inline styling — exactly the long, argument-rich perk descriptions | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/tooltip/TooltipWrap.java` — `#wrap` (~line 34–51), `#charSeqToString` (~line 53–60)

**Problem**

For any line over `maxWidthPx`, the method splits it and rebuilds each piece as
`Component.literal(charSeqToString(seq))` with only the **outer** line style re-applied. The
per-character styles carried by the `FormattedCharSequence` are discarded. Perk and passive
descriptions are built by `Perk#getMutableDescription`, which substitutes coloured parameters
(`"§cx" + parameter`, `"§9" + parameter + "s"`, `"§6+"`, `"§2…%"`, `"§d" + roman`) — those
`§` codes survive because they are literal characters in the string, but every style applied via
`.withStyle(ChatFormatting.…)` on nested components (the gold underlined perk name, the grey
description, the green level numbers) is collapsed to the outer style.

`charSeqToString` also silently drops the style argument of the visitor:
```java
seq.accept((index, style, codePoint) -> { sb.appendCodePoint(codePoint); return true; });
```

**Why it matters**

The exact tooltips this helper exists to protect (long translated descriptions at
GUI scale 4) are the ones that get de-styled. It also fuses the `§`-prefixed parameter colours
with whatever outer style is applied, producing inconsistent colouring between a wrapped and an
unwrapped line of the same tooltip.

**Failure scenario**

Hover `perk.runicskills.stealth_mastery` (three substituted parameters, long
sentence) at GUI scale 3 — the first rendered line keeps its structure, the wrapped remainder is
uniformly grey.

**Fix**

Rebuild each split piece as a styled component by accumulating runs of equal `Style` from
the `FormattedCharSequence` visitor (`seq.accept((idx, style, cp) -> …)` already hands you the
style — group by it and emit one `Component.literal(run).setStyle(style)` per run, appended into
a single `MutableComponent` per line). Alternatively use
`ComponentUtils`/`FormattedText`-preserving splitting.

---

### [RS-086] Hard-coded English strings in client-facing UI

**Severity:** Medium | **Confidence:** High | **Impact:** Untranslatable UI text; also unreachable-by-resource-pack | **Effort:** Low

Files:
- `src/main/java/com/otectus/runicskills/client/integration/L2TabsClientIntegration.java` — `#registerTab` (~line 38): `Component.literal("Skills")` is the L2Tabs tab title.
- `src/main/java/com/otectus/runicskills/client/screen/PowersScreen.java` — `#render` (~line 156–160) `tierSegment("Marks"…)`, `("Seals"…)`, `("Crown"…)`; (~line 187) `"Esc to close · Scroll to navigate · Hover for details"`; `#renderPowerTooltip` (~line 271) `"Requires " + p.getGoverningSkill().getName() + " " + p.requiredSkillLevel`; (~line 275) `"[disabled in config]"`.
- `src/main/java/com/otectus/runicskills/mixin/MixPlayerRenderer.java` — `#render` (~line 35): the `"<"` / `">"` title decoration is hard-coded (`overlay.title.format` already exists in lang as `"<%s>"` and is *not* used here).
- `src/main/java/com/otectus/runicskills/client/screen/PowersScreen.java` — `#renderColumn` (~line 239–241): `prefix + name.getString()` concatenates a translated name into a raw string, so it is drawn as a plain string with no styling and no bidi handling.

**Problem**

These strings never reach the language files, so no locale (including a resource pack)
can change them. `PowersScreen:271` additionally concatenates the *internal registry name*
(`Skill#getName()`, e.g. `strength`) rather than the translated `skill.getKey()`.

**Why it matters**

`L2TabsClientIntegration`'s `"Skills"` is the tab label a player sees on the
inventory when L2Tabs is installed — the most visible string in the integration. The
`PowersScreen` strings are the entire chrome of that screen.

**Failure scenario**

Japanese client with L2Tabs: the tab tooltip reads `Skills` while every
neighbouring tab is translated.

**Fix**

Replace each with `Component.translatable(...)` and add the keys (`screen.skill.title`
already exists for the tab; `screen.runicskills.powers.hint`,
`screen.runicskills.powers.requires` with a `%s %s` pair, `screen.runicskills.powers.disabled`,
and the three `tier.runicskills.*` keys already in `en_us`). Use `overlay.title.format` in
`MixPlayerRenderer`. Build the Powers row label as a component, not a concatenated string.

---

### [RS-087] The Skills screen is entirely mouse-only: no keyboard navigation, no narrator, and the search box is not a registered widget

**Severity:** Medium | **Confidence:** High | **Impact:** Screen is unusable without a mouse and invisible to the narrator; text field is half-wired | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#init` (~line 152–166), whole class

**Problem**

Every interactive element (12 skill cards, level-up button, three footer buttons, back
button, page arrows, ~40 perk/passive icons per page, 11 title rows, the scrollbar) is drawn
with raw `blit` calls and hit-tested with hand-rolled `Area#contains`. None is an
`AbstractWidget`, so:
- `Tab` / arrow-key focus traversal reaches nothing; the screen cannot be operated from the
  keyboard at all.
- `Screen#createNarrationMessage` / `updateNarratedWidget` have nothing to announce — the
  narrator says only the screen title.
- The one real widget, `searchTitle` (`EditBox`), is created in `init()` but **never added**
  (`addWidget`/`addRenderableWidget` are not called). Consequences: `Screen#tick()` is not
  overridden and `EditBox#tick()` is never called, so the caret never blinks;
  `mouseDragged` is not routed (no drag-select in the search field); it never participates in
  focus/`setInitialFocus`; and it is manually rendered and manually fed `mouseClicked` /
  `charTyped` / `keyPressed`.

**Why it matters**

This is the mod's primary UI. Keyboard-only and screen-reader users have no
access to any of it.

**Failure scenario**

Press `Y`, then `Tab` — nothing focuses; press `Enter` — nothing activates.

**Fix**

At minimum register `searchTitle` via `addRenderableWidget` and override `tick()` to tick
it. Longer term, convert the discrete controls (level-up, back, page prev/next, three footer
toggles, title rows) to `Button`/`AbstractWidget` subclasses with `setMessage`/`setTooltip`, so
focus, narration and `GuiEventListener` routing come for free; the icon grid can be a single
custom widget that implements `nextFocusPath`.

---

### [RS-088] `DrawTabs` uses a cross-frame click latch and switches screens from inside the render pass

**Severity:** Medium | **Confidence:** Medium | **Impact:** Phantom tab activation from a stale latch; the outgoing screen keeps rendering after `removed()` | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/gui/DrawTabs.java` — `#render` (~line 40–54), `#mouseClicked` (~line 90–92), `#setScreen` (~line 85–88), statics at lines 23–24
- `src/main/java/com/otectus/runicskills/mixin/MixInventoryScreen.java` — `#mouseClicked` (~line 86–91)

**Problem**

The click model is a pair of mutable statics. `render()` sets `isMouseCheck = true`
when the cursor is over a non-current tab; `mouseClicked` sets `checkMouse = true` if
`isMouseCheck` was true *on the previous frame*; the actual `setScreen` then happens on the
*next* `render()` call, still inside `renderBg`. Both flags are `public static` and shared
between `RunicSkillsScreen` and `InventoryScreen`.

Two problems. (1) `checkMouse` is only cleared in `setScreen(...)` and in `onClose()` — and
`onClose()` is only invoked when neither L2Tabs nor Legendary Tabs is active. If a click sets
the latch but the next render does not hover the tab (cursor moved during the frame gap, screen
replaced by another mod, `client.player` momentarily null so the `isMouseCheck = false` reset at
line 29 is skipped), the latch stays armed and the *next* hover — potentially in a different
screen — instantly navigates without a click. (2) `setScreen` is called from inside
`MixInventoryScreen#renderBg` (or `RunicSkillsScreen#drawScreen`), i.e. `Minecraft#setScreen`
runs mid-render: the old screen's `removed()` fires and `minecraft.screen` is swapped, but
`GameRenderer` continues executing the *old* screen's `render()` stack — including
`AbstractContainerScreen` slot rendering for a menu whose `closeContainer()` has already been
sent.

**Why it matters**

The phantom-activation path is a "the GUI teleported me" bug that is very hard
to reproduce and report. The mid-render `setScreen` is a latent source of one-frame artifacts
and of `NullPointerException`s in other mods' `renderBg` injections.

**Failure scenario**

Click the Skills tab in the inventory at the exact moment a
`SyncSkillCapabilityCP` triggers a re-render with `client.player` transiently null; latch stays
armed; later, merely hovering the tab jumps screens.

**Fix**

Do the hit test in `mouseClicked` itself (the tab rectangles are computable from
`guiScaledWidth/Height` without a render pass) and defer the actual `setScreen` to the end of
the tick via `Minecraft.getInstance().tell(...)`, as `PowersScreen#sendEquip` already does. Drop
both statics.

---

### [RS-089] Common-package classes depend on `client.core.Utils`, whose static initialiser calls `Minecraft.getInstance()`

**Severity:** Medium | **Confidence:** High | **Impact:** A latent dedicated-server `NoClassDefFoundError` that the sided-import lint cannot see | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/core/Utils.java` — line 35: `public static final Minecraft client = Minecraft.getInstance();` Importers outside `client/`: `src/main/java/com/otectus/runicskills/registry/title/Title.java:3` (used at `#tooltip`, line 77), `src/main/java/com/otectus/runicskills/registry/perks/Perk.java:4` (used at `#getParameter`, lines 181 and 187) Lint: `build.gradle` — `checkSidedImports` (~line 336–380)

**Problem**

`Utils` lives in the client package and its `<clinit>` resolves and calls
`net.minecraft.client.Minecraft`. `Title` and `Perk` are common registry classes that are loaded
and initialised on a dedicated server, and both call into `Utils` (`Utils.getModName`,
`Utils.periodValue`, `Utils.intToRoman`). Today those specific call paths
(`Title#tooltip`, `Perk#getMutableDescription`) are only reached from `client/tooltip/**` and
`client/screen/**`, so the class is never initialised server-side — but that is an accident of
current call graphs, not an enforced invariant. Any future server-side use of a perk
description (a command, a quest-integration hook, a `/skills info` output) will initialise
`Utils`, hit `Minecraft.getInstance()`, and throw `NoClassDefFoundError` at server start-up or
first use.

`checkSidedImports` only greps for literal `import net.minecraft.client.*` / `com.mojang.blaze3d.*`
lines, so it does not catch a *transitive* dependency on a client-package class.

Related, lower risk: `RunicSkillsClient.client` (line 31) and `DrawTabs.client` (line 21) are the
same pattern. `RunicSkillsClient` is safe in practice — Forge's `AutomaticEventSubscriber` loads
only the nested `ClientForgeEvents`/`ClientProxy` classes (initialising a nested class does not
initialise the enclosing class), and the enclosing `<clinit>` only runs on first static access,
which happens exclusively in client-side handlers where `Minecraft.getInstance()` is already
non-null. It is, however, a `public static` **non-final** field: any code (or another mod) can
null it, and every dereference in `ClientForgeEvents#checkKeyboard` would NPE.

**Why it matters**

This is the one class of client/server bug that manifests as "the dedicated
server will not boot", which is unrecoverable for a server operator.

**Failure scenario**

Someone adds `/runicskills perkinfo <perk>` that prints
`perk.getMutableDescription(...)` server-side. Server crashes on the first invocation with
`NoClassDefFoundError: net/minecraft/client/Minecraft`.

**Fix**

Split the three side-agnostic helpers (`getModName`, `periodValue`, `intToRoman`,
`numberFormat`) out of `Utils` into `common/util/TextUtils`, leaving only the genuinely
client-side methods in `client.core.Utils`. Make `RunicSkillsClient.client`/`DrawTabs.client`
`private static final` (or drop them in favour of `Minecraft.getInstance()` at the call site).
Extend `checkSidedImports` to also forbid `import com.otectus.runicskills.client.*` from
non-client source directories, with an explicit allowlist for the dependency-free enums
(`SortPassives`, `SortPerks`, `Value`, `ValueType`).

---

### [RS-090] Lowering `skillMaxLevel` is not reconciled server-side; the client "fixes" it by mutating capability state inside a render method

**Severity:** Medium | **Confidence:** High | **Impact:** Client and server disagree about a player's level; client-side write to authoritative state | **Effort:** M

**Affected code:**
- `src/main/java/com/otectus/runicskills/registry/skill/Skill.java#getLockedTexture` (~line 96–110 and ~line 116–133)
- `src/main/java/com/otectus/runicskills/common/capability/SkillCapability.java#addSkillLevel` (~line 240), `src/main/java/com/otectus/runicskills/common/command/SkillsReloadCommand.java#execute` (~line 27–52)

**Problem**

`SkillCapability.addSkillLevel` clamps *new* levels to `skillMaxLevel`, but nothing walks existing player data when the cap decreases. The only correction is in a client render path:

```java
public ResourceLocation getLockedTexture() {
    int size = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
    …
    if (getLevel() > size){
        SkillCapability.getLocal().setSkillLevel(this, size);   // client writes its own capability
    }
```

`SkillCapability.getLocal()` is the client's copy, so this silently rewrites client-side state that the server never agreed to, from inside a texture lookup, and only for skills currently being drawn.

**Why it matters**

`/skillsreload` re-syncs config and re-applies passive attribute modifiers (`RegistryAttributes.modifierAttributes(sp)`, line ~43) but performs **no** reconciliation of stored skill levels, perk ranks, passive levels, or unlocked titles against the new config. The same gap applies to lowering a passive's `levelsRequired` length, raising a `*RequiredLevel` above a player's level, or removing a perk id.

**Failure scenario**

Admin lowers `skillMaxLevel` from 32 to 16 and runs `/skillsreload`. A level-30 player keeps level 30 on the server (global level, perk gating and attribute bonuses all still computed from 30). Their client renders the skill card, silently sets its local copy to 16, and now shows perks as locked that the server considers unlocked; the next `SyncSkillCapabilityCP` snaps it back to 30. Nothing refunds the now-unspendable levels.

**Fix**

Add a server-side reconciliation pass to `HandlerSkill.ForceRefresh()` / player login that, for each online player, clamps skill levels to `skillMaxLevel`, clamps passive levels to `levelsRequired.length`, drops perk ranks above `maxRank`, and re-evaluates the active-perk budget; log a summary per player and resync. Delete the write from `getLockedTexture` and clamp the *index* only.

---

### [RS-091] Built-in lock, title and perk thresholds are hardcoded against `skillMaxLevel = 32`, with no cross-field validation

**Severity:** Medium | **Confidence:** High | **Impact:** Lowering the level cap makes content permanently unreachable, with no warning | **Effort:** M

**Affected code:**
- `src/main/java/com/otectus/runicskills/handler/HandlerTitlesConfig.java` (~line 21–122), `src/main/java/com/otectus/runicskills/handler/HandlerLockItemsConfig.java` (~line 29–539), `src/main/java/com/otectus/runicskills/handler/HandlerSkill.java#defaultLockItemList` (~line 139–305)

**Problem**

The shipped defaults bake in the default cap. Titles require `skill/Strength/greater_or_equal/32`, `GlobalLevel/global/greater_or_equal/256`; lock entries require `magic:30`, `endurance:24`; 471 `*RequiredLevel` fields default to values up to 32. Nothing checks any of them against `skillMaxLevel` / `playersMaxGlobalLevel` at load, and `*RequiredLevel` is annotated `@IntField(min = -1, max = 1000)` — a full order of magnitude above the level cap's own maximum.

**Why it matters**

`skillMaxLevel` and `playersMaxGlobalLevel` are the two knobs a pack author is most likely to change first, and they are the two that quietly invalidate the most content.

**Failure scenario**

Pack sets `skillMaxLevel: 16`. Half the built-in titles (`*_great`, `archmage`, `warlord`, `polymath`…) become permanently unobtainable, `minecraft:elytra` (`dexterity:30`), `minecraft:dragon_egg` (`magic:30`) and ~40 other items become permanently locked, and ~30 perks become unreachable. The log says nothing.

**Fix**

At load, validate every `*RequiredLevel`, every `LockItem.Skill.Level` and every `skill/…` title threshold against `skillMaxLevel` (and `GlobalLevel/…` against `playersMaxGlobalLevel`); log one `WARN` summary listing unreachable content. Optionally express thresholds as a fraction of the cap. Long term, this is a strong argument for datapack-driven locks/titles (see the data-driven finding) so a pack can ship a matched set instead of editing the mod's defaults in place.

---

### [RS-092] The JSON5 comment stripper mishandles single-quoted strings; combined with lenient Gson this turns a valid file into a full reset

**Severity:** Medium | **Confidence:** High | **Impact:** Valid JSON5 is rejected; a total config reset follows | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java#stripJsonComments` (~line 233–283)

**Problem**

The stripper tracks `"`-delimited strings only. Gson's lenient reader accepts single-quoted strings and unquoted keys (both verified: `{'a':5,'b':6}` and `{a:5,b:6}` parse fine), and JSON5 permits both. So the *parser* accepts single quotes but the *pre-processor* does not know about them:

```
{"l":['http://x'],"a":9}
  -> stripper eats from "//" to EOL
  -> FAIL MalformedJsonException: Unterminated string
  -> whole file reset to defaults
```

Anything after the `//` on that line — potentially thousands of config keys — is deleted before Gson ever sees it.

Also in this method: an unterminated block comment silently swallows the rest of the file (line ~272–276) and then parses as truncated JSON, producing the same full reset with a misleading error. There is no accounting for JSON5 features the mod's own file extension advertises (trailing commas, hex, `Infinity`, `+1`), all of which Gson rejects.

**Why it matters**

The class is named for JSON5 and the files are named `.json5`, but the accepted dialect is "JSON, plus comments, minus anything else". That mismatch is the operator's problem to discover.

**Fix**

Track `'`-delimited strings in the stripper as well as `"`. Log which line the strip/parse failed on. Consider swapping to a real JSON5 parser or to `JsonReader` with `setLenient(true)` over a comment-aware `Reader`, so the dialect the file name promises is the dialect that is accepted.

---

### [RS-093] Renamed or unknown keys are silently dropped, and the next save deletes them from disk

**Severity:** Medium | **Confidence:** High | **Impact:** Silent loss of a pack author's tuning across mod updates | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java#load` (~line 86) and `#save` (~line 156–188)

**Problem**

`new Gson().fromJson(element, type)` ignores JSON keys with no matching field (verified: `{"a":5,"zzz":9}` parses cleanly). `save()` then serialises only the POJO's fields, so the unknown key is gone from disk. There is no migration layer and no "unrecognised key" warning.

**Why it matters**

With 1141 fields across 24 groups and an active development cadence (`CHANGELOG.md` shows frequent additions), field renames are likely. When one happens, the pack author's carefully tuned value reverts to the default with no signal at all — this is exactly the class of bug the `@SerializedName(alternate = …)` shims in `LockItem` and `TitleModel` were added to fix retroactively, after it had already shipped.

**Failure scenario**

`lifeEaterAmplifier` → `lifeEaterModifier` (this rename is already visible: `HandlerConfigCommon.java:183` defines `lifeEaterAmplifier` writing the key `"lifeEaterModifier"`). A pack that set the old name loses the value on update; nothing is logged; the key vanishes on the next save.

**Fix**

Log a `WARN` listing keys present in the file but absent from the class (compare `JsonObject.keySet()` against `type.getFields()`). Adopt `@SerializedName(alternate = …)` as the standing policy for every rename, and cover it with a test like the existing `LegacySnakeCaseConfigTest`.

---

### [RS-094] The on-disk config has zero documentation, and 403 fields are invisible in the UI too

**Severity:** Medium | **Confidence:** High | **Impact:** A 1141-key file that a pack author must edit blind | **Effort:** M

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java#save` (~line 164) and `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java` (whole file)

**Problem**

Every field carries `@SerialEntry(comment = "…")`, and the comments are genuinely good (median 43 chars; the best ones — `maxActivePerks`, `perksPerGlobalLevel`, `skillLevelUpMinCost` — explain the interaction with other fields and whether the value is server-authoritative). None of it reaches the file. `save()` uses plain Gson; `ConfigHolder`'s own javadoc records this: "New writes don't include comments — the `@SerialEntry(comment=…)` text only renders in the YACL UI tooltips, not in the on-disk file."

The UI is not a substitute: 403 of 1141 fields have no `@AutoGen` and therefore never appear in the YACL screen at all — including every `int[]` passive-level array, `checkForUpdates`, `titlesUseCustomName`, `treasureHunterItemList`, and all 361 unannotated `int` fields. YACL is also client-only, so on a dedicated server the documentation coverage is 0%.

**Why it matters**

The file is the *only* interface a server admin has. Once a client opens the config screen once (or `GlobalLimitCommand` runs), YACL's comment-bearing file is rewritten by `ConfigHolder.save()` and whatever documentation was there is stripped.

**Fix**

Emit the `@SerialEntry` comments when writing. `JsonWriter` doesn't support comments, but a small post-pass over the pretty-printed output that inserts `// <comment>` above each top-level key (keyed off the field name) is ~40 lines and round-trips through `stripJsonComments` by construction. Add `@AutoGen` to the 403 orphans, or split them into a clearly-labelled "advanced, file-only" section in the docs.

---

### [RS-095] `int[]` passive-level arrays have no validation and an unenforced "don't do this" comment

**Severity:** Medium | **Confidence:** High | **Impact:** Broken passives, divide-by-zero tooltips, corrupt sync packets | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java` (~line 370–371 and 37 similar), consumed at `src/main/java/com/otectus/runicskills/registry/passive/Passive.java` (~line 29, 92–112) and `src/main/java/com/otectus/runicskills/client/tooltip/PassiveTooltip.java` (~line 20–22)

**Problem**

`@SerialEntry(comment = "Attack damage passive levels. Don't modify the length of the array!")` is the entire enforcement. These 38 fields are the only ones in the config with no annotation of any kind; `@Clamp` cannot apply to an array (`applyClamps` `ClassCastException`s and swallows at line ~140).

Consequences of an empty or mis-ordered array: `SkillCapability.addPassiveLevel` does `Math.min(level + 1, passive.levelsRequired.length)` → level pinned to 0 forever; `PassiveTooltip` computes `passive.getValue() / passive.levelsRequired.length` → division by zero; `Passive.getMaxLevel()` returns 0; and the array corrupts the `DynamicConfigSyncCP` wire format (see that finding).

**Why it matters**

The comment tells the author the length matters but not *why*, not what it must equal, and not that the entries must be ascending and within `skillMaxLevel`.

**Fix**

Validate on load — non-empty, strictly ascending, every entry in `[1, skillMaxLevel]` — and either reject-to-default with a `WARN` or repair by sorting/clamping. Extend the comment to state the invariant. A `@ClampArray(minEntry, maxEntry, ascending = true)` annotation handled alongside `@Clamp` keeps it declarative.

---

### [RS-096] `HandlerConfigCommon` is 196 lines of dead `ForgeConfigSpec` that duplicates 60 field names and their defaults

**Severity:** Medium | **Confidence:** High | **Impact:** Maintenance trap — two sources of truth for the same tunables | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/handler/HandlerConfigCommon.java` (whole file; the `static` block is ~line 126–193)

**Problem**

The class is `@Obsolete`, its `SPEC` is never registered (`Configuration.Init` registers only `HandlerConfigClient.SPEC`), and grep finds **zero** references to it anywhere else in `src/`. Yet it still declares `skillMaxLevel`, `attackDamageValue`, `berserkerPercent`, `limitBreakerAmplifier` and ~56 more, each with its own `default*` constant and its own `defineInRange` bounds — a complete shadow copy of the live config's most important values, already drifted (`defaultLimitBreakerAmplifier = 999.0D` vs the live default, and the `lifeEaterAmplifier` field writing key `"lifeEaterModifier"`).

**Why it matters**

The file's own first line is `CONFIG.comment("THIS CONFIGURATION ISN'T USED ANYMORE, ANY CHANGE HERE WILL HAVE NO IMPACT!")` — but that comment only appears in a file that is never written, so nobody reading the config directory ever sees it. Meanwhile a maintainer grepping for `skillMaxLevel` gets two hits and must work out which is live. The bounds here (`defineInRange`) are also the *only* place several ranges are written down as enforced values, which invites someone to treat them as authoritative.

**Fix**

Delete the class. `HandlerSkill.defaultLockItemList` (~line 139–305, 130 entries) is its only consumer and is itself dead — grep finds no reader. Both are pure historical residue; `git` retains them.

---

### [RS-097] Title ids come from user config and go straight into `ResourceLocation` — a typo crashes mod loading

**Severity:** Medium | **Confidence:** High | **Impact:** Unbootable game/server from a single character in a config file | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/models/TitleModel.java#registry` / `#register` (~line 154–162), called from `src/main/java/com/otectus/runicskills/registry/RegistryTitles.java#load` (~line 64)

**Problem**

`RegistryTitles.load` guards only `title == null || title.TitleId == null || title.TitleId.isEmpty()` before calling `title.registry(TITLES)`, which does `new ResourceLocation(RunicSkills.MOD_ID, name)`. `ResourceLocation` throws `ResourceLocationException` on uppercase letters, spaces, or any character outside `[a-z0-9_.-]`.

**Why it matters**

This is the one config file whose values become registry keys, and it is edited by hand. The mod already has the right helper — `ConfigParser.tryBuild` (~line 58–65) exists specifically because "`new ResourceLocation(parts[0], parts[1])` could crash config loading on a single typo (e.g. an uppercase letter or space in a modpack-author's entry)" — but the title path doesn't use it.

**Failure scenario**

A pack author adds `{"TitleId": "Dungeon Master", "Conditions": [...]}`. The game crashes during mod construction with a `ResourceLocationException` whose stack trace points at Forge's registry code, not at the config file or the offending id.

**Fix**

Route the id through `ConfigParser.parseResourceLocation` (or a `ResourceLocation.tryParse` + `WARN` + skip) in `RegistryTitles.load`, naming the file and the bad id. Do the same for any other config string that becomes a registry key.

---

### [RS-098] `rebindAfterReload` drops titles added since startup from the in-memory list, and a later save can delete them from disk

**Severity:** Medium | **Confidence:** Medium | **Impact:** A pack author's new title silently disappears from their config file | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/registry/RegistryTitles.java#rebindAfterReload` (~line 119–144) and `#mergeDefaultsIntoConfig` (~line 86–107)

**Problem**

`rebindAfterReload` filters out any `TitleModel` whose id is not in the (startup-frozen) registry — correct, since it cannot be bound — and then **replaces the live list**: `HandlerTitlesConfig.HANDLER.instance().titleList = List.copyOf(boundTitles)` (line ~143). The dropped entries are now absent from the in-memory instance. `mergeDefaultsIntoConfig` calls `HandlerTitlesConfig.HANDLER.save()` (line ~105) whenever it adds a built-in default, which serialises that truncated list back over the file.

**Why it matters**

`/skillsreload` is the documented way to apply title edits, and the warning it emits ("will only take effect after a restart") reads as benign — it does not say the entry is being removed from the runtime list, and gives no hint that a subsequent save could remove it from the file.

**Failure scenario**

Author adds `dungeon_lord` to `runicskills.titles.json5` and runs `/skillsreload`. Warning logged, entry dropped from memory. They then update the mod (which ships a new built-in title); on the next boot `mergeDefaultsIntoConfig` adds it and saves — but the in-memory list at that moment is fine at startup, so this specific ordering is safe. The unsafe ordering is `/skillsreload` (drop) followed by anything that triggers `mergeDefaultsIntoConfig` + `save()` in the same session. Confidence is Medium because the exact ordering matters; the structural hazard (a filtered list feeding a whole-file save) is unambiguous.

Also note `HandlerTitlesConfig.titleList` defaults to an **immutable** `List.of(...)` (line ~21) while `HandlerLockItemsConfig.lockItemList` was deliberately changed to a mutable `new ArrayList<>(List.of(...))` for exactly this class of problem — the inconsistency is a live trap for the next person who tries to mutate the title list.

**Fix**

Keep unbound-but-configured titles in a separate `pendingTitles` list rather than dropping them, and never let a filtered/derived list be the source for `save()`. Make the default list mutable for consistency with the lock list.

---

### [RS-099] `ESkill` is a fixed 10-value enum, so lock items cannot reference KubeJS-registered skills

**Severity:** Medium | **Confidence:** High | **Impact:** The extensibility story stops at the item-lock system | **Effort:** M

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/models/ESkill.java` (whole file), `src/main/java/com/otectus/runicskills/config/models/LockItem.Skill` (~line 71–98), wire format at `src/main/java/com/otectus/runicskills/network/packet/client/ConfigSyncCP.java` (~line 48–56)

**Problem**

`LockItem.Skill.Skill` is typed `ESkill`, a hardcoded enum of the 10 built-in skills. `RegistrySkills` is a real Forge registry with a documented KubeJS `add` path (`Skill.add`, `Passive.add`, `Perk.add`), so a pack can create an 11th skill — but no lock item can require it. `ConfigSyncCP` also transmits skills by enum **ordinal** (line ~48–56, `ESkill.values()[ordinal]`), so the wire format is positionally coupled to enum declaration order.

Secondary: Gson maps an unrecognised enum name to `null` silently. `HandlerSkill.buildSkillsList` handles the null with a warn (line ~81–86), but the code-path constructor `LockItem.Skill(String, int)` instead *falls back to `ESkill.Strength`* on a bad name (line ~80–85) — two different behaviours for the same class of error.

**Fix**

Type the field as a `String` skill id resolved through `RegistrySkills.getSkill` at load (the mod already does this lookup one layer up, in `buildSkillsList` line ~87), and transmit the id string rather than an ordinal. That also removes the enum-reorder hazard from the packet.

---

### [RS-100] Config lives per-instance, not per-world, so one file governs every singleplayer save

**Severity:** Medium | **Confidence:** High | **Impact:** No per-world balance; a client's file is simultaneously "their singleplayer rules" and "display defaults for servers" | **Effort:** L

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/Configuration.java` (~line 13, 19–24)

**Problem**

`_absoluteDirectory = FMLPaths.CONFIGDIR.get().resolve("RunicSkills")` — all four JSON5 files are per-instance. Forge's own `ModConfig.Type.SERVER` mechanism (per-world, in `<world>/serverconfig/`) is not used; `HandlerConfigClient` is the only registered `ModConfig`, as `Type.CLIENT`. So the four files behave as `COMMON` in Forge terms while their *contents* are treated as server-authoritative by the sync packets.

**Why it matters**

This is the root of two other findings on this list (server config leaking into singleplayer, and the config-screen overwrite). A single file cannot simultaneously be "the rules of the world I am hosting" and "defaults to show when I'm a guest elsewhere". It also means a player cannot have a hardcore world and a casual world in the same instance.

Effective scoping today:

| Value class | Declared home | True scope | Correct home |
|---|---|---|---|
| `skillMaxLevel`, `playersMaxGlobalLevel`, `skillFirstCostLevel`, `skillLevelUp*`, `maxActivePerks`, `perksPerGlobalLevel`, `maxPerkBudgetCap`, `perkSwapCooldownTicks` | common | SERVER | per-world SERVER |
| `disabledPerks/Passives/Powers`, `enable*Integration`, `enableItemLocks`, `dropLockedItems`, all `*RequiredLevel`, all `*Percent`/`*Amplifier`/`*Probability`, all `*Value`, all `*PassiveLevels` | common | SERVER | per-world SERVER |
| `lockItemList`, `titleList`, `convergenceItemList` | own files | SERVER | per-world SERVER, or datapack |
| `hideDisabledPerks/Passives/Powers` | common | CLIENT presentation (currently synced, so server-forced) | CLIENT |
| `showPotionsHud` | common | SERVER (feeds `MobEffectInstance` `showIcon`, which is synced) — correct as-is, but the name reads client-only | keep, rename |
| `checkForUpdates` | common (no `@AutoGen`) | per-instance preference, read on both sides (`RunicSkills.java:133`, `PlayerLifecycleHandler.java:156`) | CLIENT + a separate server flag |
| `titlesUseCustomName` | common, **not synced** | SERVER (`SetPlayerTitleSP.java:60`) — fine, but sits beside `displayTitlesAsPrefix` which *is* synced | keep, document the asymmetry |
| `legendaryTabsPriority`, `sortPerk`, `sortPassive`, `showPerkModName`, `showTitleModName`, `showCriticalRollPerkOverlay`, `showLuckyDropPerkOverlay` | `HandlerConfigClient` (Forge CLIENT spec) | CLIENT | correct |

The only genuinely mis-scoped values are `hideDisabledPerks/Passives/Powers` (pure UI presentation, yet server-forced) and `checkForUpdates`. Everything else is *nominally* in the right file; the problem is that the file itself has no scope.

**Fix**

Move the four JSON5 files to `ModConfig.Type.SERVER` semantics — write them under `<world>/serverconfig/RunicSkills/` and load them on `ServerAboutToStartEvent`, falling back to a per-instance template for first-run generation (this is exactly what Forge's SERVER configs do, and is the one place where adopting Forge's mechanism is worth the cost; see the architecture notes). Keep `hideDisabled*` in the client TOML.

---

### [RS-101] The item-lock lists and per-mod integration lists belong in datapacks/tags, not in a 54 KB Java literal

**Severity:** Medium | **Confidence:** High | **Impact:** Pack authors cannot add locks without editing a 435-entry generated file; mod-support additions require a mod release | **Effort:** L

**Affected code:**
- `src/main/java/com/otectus/runicskills/handler/HandlerLockItemsConfig.java` (~line 29–539, 435 entries across 13 namespaces), `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java` (24 per-mod groups, ~400 integration fields)

**Problem**

`HandlerLockItemsConfig` hardcodes 435 lock entries — 145 `minecraft`, 72 `cataclysm`, 63 `apotheosis`, 53 `ars_nouveau`, 29 `stalwart_dungeons`, 16 `nichirin_dynasty`, and 7 more namespaces — as a Java list literal, which `ConfigHolder` then materialises into a JSON5 file on every installation, including installations that have none of those mods. Adding support for a 14th mod means editing Java and shipping a release. Meanwhile 738 of the 1141 common-config fields are per-mod integration tuning (`irons_spells` 141, `apothic_attributes` 52, `ars_nouveau` 41, `apotheosis` 15, …) that is inert for anyone without that mod installed.

The mod already has the right machinery: three `SimpleJsonResourceReloadListener`s (`PerkGroupsReloadListener`, `PowerOverridesReloadListener`, `SkillVisualsReloadListener`) with per-file error isolation, plus two item tags under `data/runicskills/tags/blocks/`. The lock system is the last big holdout.

What should move, and the trade-off:

| Data | Move to | Gain | Cost |
|---|---|---|---|
| `lockItemList` (435 entries) | `data/<ns>/runicskills/item_locks/*.json`, one file per source mod | Pack authors add locks without touching mod config; entries for absent mods never load; per-file error isolation; multiple packs compose | Loses `/registeritem` write-back (datapacks are read-only) — keep the JSON5 file as a *player/admin override layer* that wins over datapack entries |
| Lock skill requirements per item **class** (all shulker boxes, all netherite gear) | item tags + a tag→requirement mapping | 27 shulker-box entries collapse to one tag rule; automatically covers modded additions to vanilla tags | Requires a tag-resolution pass; a tag rule is less discoverable than an explicit item id |
| Per-mod integration field blocks (`irons_spells`, `ars_nouveau`, `apotheosis`, `cataclysm`, …) | one JSON5 file per integration, `runicskills.integration.<modid>.json5` | Common config drops from 1141 to ~250 fields; a parse error in one integration no longer resets everything; files only generated when the mod is present | More files; needs a `ConfigHolder` per integration (cheap — the class already supports it) |
| `titleList` | `data/<ns>/runicskills/titles/*.json` | Composable; matches the existing reload-listener pattern; removes the registry-mismatch hazard | Titles are registry entries, so this needs the registry to be datapack-driven or the ids pre-registered |
| `convergenceItemList` | recipe-adjacent datapack JSON | Naturally expressed as `input → salvage output` | Small list; low payoff |

**Fix**

Start with the lock lists — highest entry count, most pack-author demand, and the reload-listener pattern is already proven in this codebase. Keep the JSON5 file as an override layer so `/registeritem` and existing configs keep working, and give `LockItem` a `Source` field value of `"datapack:<ns>"` (the field already exists, line ~31).

---

### [RS-102] `MixShulkerBullet` cancel-and-reimplement drops other mods' `onHitEntity` behaviour for a rule Forge already exposes

**Severity:** Medium | **Confidence:** High | **Impact:** Conflicts with combat/effect mods | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/MixShulkerBullet.java — `MixShulkerBullet#onHitEntity` (~line 185-207)

**Problem**

The mixin injects after `super.onHitEntity(...)` and calls `info.cancel()`, then copies
vanilla's damage + levitation logic verbatim with one added perk check. Hard-coded `4.0F` damage
and `LEVITATION, 200` are frozen copies of vanilla values.

**Why it matters**

Anything another mod injects into the remainder of `ShulkerBullet#onHitEntity`
(including `@ModifyConstant` on the damage value, or a `@Inject(at=TAIL)`) never executes. The
entire behaviour could be expressed as a two-line `MobEffectEvent.Applicable` handler.

**Failure scenario**

A pack installs a mod that scales shulker damage with difficulty via a mixin on
`ShulkerBullet`. With Runic Skills present the scaling silently does nothing and shulkers always hit
for exactly 4.0.

**Fix**

Delete the mixin; subscribe to `MobEffectEvent.Applicable`, and cancel `LEVITATION` when the
target is a player with `TURTLE_SHIELD` and the source is a `ShulkerBullet`.

---

### [RS-103] `MixEnchantmentMenu` targets `EnchantmentMenu#clickMenuButton`, which Apotheosis overrides

**Severity:** Medium | **Confidence:** Medium | **Impact:** Perk silently inert in the packs it targets | **Effort:** Medium

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/MixEnchantmentMenu.java — `MixEnchantmentMenu#runicskills$reduceEnchantLevelRequirement` (~line 19-26)

**Problem**

Apotheosis (a first-class declared integration — mods.toml `apotheosis`, `compileOnly` in
build.gradle:242) replaces the enchanting table with its own menu subclass that overrides
`clickMenuButton`. A `@Inject` into the superclass method does not run when the subclass overrides
it without calling `super`.

**Why it matters**

Enchanter's Insight is advertised as working, and Apotheosis is one of the mod's
headline integrations. The two are mutually exclusive in practice.

**Failure scenario**

Pack ships Runic Skills + Apotheosis. `ENCHANTERS_INSIGHT` costs the player perk
points and never reduces a single enchanting cost. No error, no log line.

**Fix**

Detect `apotheosis` and route through Apotheosis' own API/event, or add a `@Pseudo` mixin on
the Apotheosis menu class gated by the config plugin. At minimum, log a warning when both are
present.

Secondary note on the same file: `costs[]` is `@Shadow @Final` and is mutated in place. The array is
also the client-visible synced data; mutating it server-side without a re-broadcast leaves the
client's displayed cost stale until the next `slotsChanged`.

---

### [RS-104] `MixInventoryScreen` declares a bare `onClose()` — an implicit, unannotated overwrite on a heavily-mixed class

**Severity:** Medium | **Confidence:** Medium-High | **Impact:** Mixin conflict with other inventory-tab mods | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/MixInventoryScreen.java — `MixInventoryScreen#onClose` (~line 93-97)

*Independently corroborated in the client/UI pass.*

**Problem**

The method carries no `@Inject`, `@Overwrite` or `@Unique`. Mixin merges it into
`InventoryScreen` as a new method overriding `AbstractContainerScreen#onClose`. With
`mixin.checks.implicitoverwrites` off (the default) this succeeds silently.

**Why it matters**

`InventoryScreen` is one of the most-mixed classes in any modpack (backpack mods,
tab mods, curios, JEI-adjacent UIs). If any other mod merges an `onClose()` into the same target,
Mixin raises a hard method conflict at class-transform time and one of the two mods fails to load.

**Failure scenario**

Pack adds another inventory-tab mod that also merges `onClose()` into
`InventoryScreen`; startup dies with `Mixin apply failed … conflicting method onClose()V`.

**Fix**

Convert to `@Inject(method = "onClose", at = @At("HEAD"))` and rename the mixin method with a
`runicskills$` prefix.

---

Severity: Low | Confidence: High | Impact: Mixin conflict risk with any other mod overriding `InventoryScreen#onClose`; avoidable per-frame allocation | Effort: Trivial
File: `src/main/java/com/otectus/runicskills/mixin/MixInventoryScreen.java` — `#onClose` (~line 93–97), `#render` (~line 79)

Problem: `public void onClose()` carries no `@Inject`/`@Overwrite` annotation. Mixin merges it as
a new method on `InventoryScreen` (which only inherits `onClose` from `Screen`), which works
today but is an implicit soft-override: a second mod doing the same produces a hard mixin
conflict rather than two co-existing `@Inject`s. Separately, line 79 constructs
`new ResourceLocation(RunicSkills.MOD_ID, "textures/skill/ender_chest_button.png")` inside the
per-frame render path; every other texture in the mod is a `static final` in `HandlerResources`.

Fix: convert `onClose` to `@Inject(method = "onClose", at = @At("HEAD"))`, and hoist the
`ResourceLocation` into `HandlerResources`.

---

### [RS-105] `MixTargetFinder` is declared in the common (non-client) mixin list but targets a client-only BetterCombat class

**Severity:** Medium | **Confidence:** Medium-High | **Impact:** Dedicated-server log noise / transform failure | **Effort:** Low

**Affected code:**
- src/main/resources/runicskills.mixins.json — `mixins[]` (~line 15); mixin/MixTargetFinder.java — `@Mixin(TargetFinder.class)` (~line 243)

**Problem**

The target is `net.bettercombat.client.collision.TargetFinder`. `MixGunItem` (also an
optional-mod client class) is correctly in the `client` array; `MixTargetFinder` is in the common
`mixins` array. On a dedicated server with BetterCombat installed, `shouldApplyMixin` returns `true`
and Mixin asks the classloader for a client-side class.

**Why it matters**

Best case it is a startup WARN — exactly the noise `RunicSkillsMixinPlugin` was
written to eliminate. Worst case it is a transform failure on the dedicated server.

**Failure scenario**

Server operator installs BetterCombat server-side (common in packs, since BC has
server components) and sees Mixin/classloader errors attributed to `runicskills`.

**Fix**

Move `MixTargetFinder` into the `client` array of `runicskills.mixins.json`. `build.gradle`'s
`checkSidedImports` already derives its client allowlist from that array, so this also tightens the
lint.

Related brittleness in the same file: the hard-coded reach-modifier UUID
`96a891fe-5919-418d-8205-f50464391509` (line 260) is the mod's own `entity_reach` passive UUID
(RegistryPassives.java:55) written as a literal; and `ModList.get().isLoaded("quality_equipment")`
→ `attackRange += 3.0D` (line 270) hard-codes a third mod's constant.

---

### [RS-106] KubeJS level-up hook is registered as a client-only event and consulted only on the client

**Severity:** Medium | **Confidence:** High | **Impact:** Scripted gating is bypassable and absent on servers | **Effort:** Medium

**Affected code:**
- src/main/java/com/otectus/runicskills/kubejs/events/CustomEvents.java — `CustomEvents.SKILL_LEVELUP` (~line 9); call site client/screen/RunicSkillsScreen.java (~line 975-980)

**Problem**

`GROUP.client("skillLevelUp", …)` registers the handler in KubeJS' CLIENT script scope,
and the only caller is `RunicSkillsScreen` (a client screen) which posts the event and, if not
cancelled, sends `SkillLevelUpSP`. The server-side `SkillLevelUpSP` handler never consults KubeJS.

**Why it matters**

Pack authors writing `RunicSkillsEvents.skillLevelUp(e => e.cancelled = true)` get
a hook that (a) does not exist in `server_scripts/`, (b) has no effect on a dedicated server, and
(c) is trivially bypassed by a client that never posts it.

**Failure scenario**

A pack gates skill level-ups behind a quest via KubeJS. It works in single-player,
silently does nothing on the pack's server, and any modified/older client bypasses it entirely.

**Fix**

Move the event to `GROUP.server(...)` (or `common`) and post it from the server-side
`SkillLevelUpSP` handler before applying the level. Keep the client call only as a UX pre-check.

---

### [RS-107] `mods.toml` declares 7 optional dependencies for ~40 integrated mods, including one whose load order the code explicitly relies on

**Severity:** Medium | **Confidence:** High | **Impact:** Load-order-dependent integration failures | **Effort:** Low

**Affected code:**
- src/main/resources/META-INF/mods.toml — `[[dependencies."${mod_id}"]]` blocks (~line 47-95)

**Problem**

Declared: `forge`, `minecraft`, `yet_another_config_lib_v3`, `kubejs`, `rhino`,
`ars_nouveau`, `attributeslib`, `apotheosis`, `ftbquests`. Not declared, despite live integration
code: `irons_spellbooks`, `curios`, `tacz`, `cgm`, `scguns`, `pointblank`, `bettercombat`, `l2tabs`,
`legendarytabs`, `locks`, `overgeared`, `starcatcher`, `iceandfire`, `cataclysm`, `mowziesmobs`,
`spartanweaponry`/`spartanshields`/`spartancataclysm`/`spartanfire`, `samurai_dynasty`,
`nichirin_dynasty`, `saintsdragons`, `jewelcraft`, `quality_equipment`, and the ~25 namespaces in
`LockProviderRegistry`.

**Why it matters**

`ordering = "AFTER"` is how a mod guarantees the dependency has finished
construction/registration before its own hooks run. Without it, Forge's topological sort falls back
to an ordering that is *not* a documented contract.

**Failure scenario**

RunicSkillsClient.java:74-79 states outright: *"Forge dispatches mod events in
alphabetical mod-id order, and 'legendarytabs' precedes 'runicskills'"* — the Legendary Tabs tab
registration is correct **only** because of an undocumented tie-break. Any change to Forge's sorter,
or another mod introducing a dependency edge, silently breaks the Skills tab. The same applies to
`irons_spellbooks` (whose registry objects are read during `RunicSkills.<init>`).

**Fix**

Add `mandatory = false, ordering = "AFTER"` entries for at least `irons_spellbooks`, `curios`,
`legendarytabs`, `l2tabs`, `bettercombat`, `pointblank`, and the mods whose registry contents are
read at construct time.

---

### [RS-108] `PowerEventDispatcher` is gated on Iron's Spells, so every Power is dead in packs without it — contradicting its own javadoc

**Severity:** Medium | **Confidence:** High | **Impact:** Whole feature silently absent | **Effort:** Medium

**Affected code:**
- src/main/java/com/otectus/runicskills/RunicSkills.java — `RunicSkills#<init>` (~line 71-73); registry/events/PowerEventDispatcher.java (~line 46-48)

**Problem**

The dispatcher's class javadoc says "Registered from `RunicSkills` **regardless of whether
ISS is loaded** — ISS-bound Powers self-short-circuit when the Power's `RegistryObject` is null."
The code does the opposite: registration is inside `if (IronsSpellbooksIntegration.isModLoaded())`.
The inline comment at RunicSkills.java:66-70 acknowledges the intended split as future work.

**Why it matters**

Powers (Marks/Seals/Crowns) are a headline 1.5+ feature with their own screen,
commands, packets and datapack overrides. In any pack without Iron's Spells the entire subsystem
is inert, and `MixTrueInvisibilityEffect` (the only mixin that touches Powers) is also gated off.

**Failure scenario**

A pack ships Runic Skills without ISS. Players equip Powers in the UI, the
`/powers` command works, `PowerProcCP` never fires, and nothing ever procs. No log line explains it.

**Fix**

Split the dispatcher into an ISS half and a vanilla half as the comment plans; or, at minimum,
correct the javadoc and emit an INFO line when Powers are disabled for lack of ISS.

---

### [RS-109] `libs/l2tabs-0.3.3.jar` is a hand-written API stub, so the L2Tabs call site is never type-checked against the real mod

**Severity:** Medium | **Confidence:** High | **Impact:** Reproducibility; runtime `NoSuchMethodError` risk | **Effort:** Low

**Affected code:**
- libs/l2tabs-0.3.3.jar — `L2TABS_STUB_README.txt`; build.gradle (~line 202); client/integration/L2TabsClientIntegration.java (~line 38)

*Independently corroborated in the build/tests/docs pass.*

**Problem**

The jar contains 5 fabricated `.class` files (`BaseTab`, `TabManager`, `TabToken`,
`TabRegistry`, `TabRegistry$TabFactory`) with, per its own README, "only the four type signatures
the Runic Skills source references". `TabRegistry.registerTab(3500, TabRunicSkills::new,
RegistryItems.LEVELING_BOOK, Component.literal("Skills"))` therefore compiles against a signature
the project author invented, not the one L2Tabs actually publishes.

**Why it matters**

A green build proves nothing about L2Tabs compatibility. The only runtime safety net
is the `LinkageError` catch in `L2TabsIntegration.registerClientTab` (line 50), which turns a
mismatch into "tab silently missing".

**Failure scenario**

L2Tabs 0.3.3's real `registerTab` takes a different parameter order or an extra
argument. CI is green; every user with L2Tabs gets a warning in the log and no Skills tab, and
nobody notices because the fallback strip is disabled by `runicskills$externalTabsActive()`
(MixInventoryScreen.java:47-49) only when registration *succeeded* — correct, but the whole path is
untested.

**Fix**

Resolve L2Tabs from a real Maven coordinate (Modrinth/CurseMaven, both repos are already
declared in build.gradle) and delete the stub; or, if no maven exists, keep the stub but add a
runtime reflective signature assertion and a build comment marking it unverified.

---

Severity: Medium | Confidence: High | Impact: Medium | Effort: Medium
File: `libs/l2tabs-0.3.3.jar` (3,416 bytes, contains `L2TABS_STUB_README.txt` + 5 class files);
`build.gradle:180-182,203`; `CLAUDE.md` "Key Dependencies"

Problem: the jar resolved as `dev.xkmc.l2tabs:l2tabs:0.3.3` via `flatDir { dirs 'libs' }` contains
exactly five classes (`BaseTab`, `TabManager`, `TabRegistry`, `TabRegistry$TabFactory`, `TabToken`),
totalling 3.3 KB, plus a README announcing itself as a stub. `CLAUDE.md` confirms: "ships as a
minimal API stub so the build works when the real L2Tabs jar isn't locally available — replace it
with the real 0.3.3 jar to compile against the full L2Tabs surface."

Why it matters: `compileOnly` against a stub means the compiler validates the *stub's* signatures,
not L2Tabs'. If the stub's `TabRegistry.register` signature drifts from the real one — or was
transcribed slightly wrong to begin with — the build is green and the runtime throws
`NoSuchMethodError` on any client that actually has L2Tabs installed. This is precisely the class of
failure that `compileOnly` is supposed to catch. It is also non-reproducible in the "two developers
get different results" sense: whoever swaps in the real jar compiles against a different API than
CI does, and `.gitignore:123` guarantees CI always uses the stub.

Additionally, `README.md:270-275` says both jars "aren't redistributed with this repo. Drop them
into `libs/` before building" and "If either jar is absent, Gradle will fail". Both jars **are**
committed and deliberately un-ignored (`.gitignore:119-124`), so this instruction is stale and
actively misleading — and the referenced "`build.gradle` lines 196–202" points at the wrong lines
(the relevant block is 202-207).

Failure scenario: L2Tabs 0.3.3's `TabRegistry.register` actually takes a fourth parameter the stub
omits. CI is green, the jar ships, and every user running L2Tabs gets a
`NoSuchMethodError: dev.xkmc.l2tabs.tabs.core.TabRegistry.register` when opening their inventory.

Fix: prefer a real Maven coordinate — L2Tabs publishes to `https://maven.saps.dev/releases` and
CurseForge, both of which are already usable from the declared repositories. If a local jar is
genuinely required, at minimum (a) add a `checkStubFreshness`-style verification task or a runtime
reflective guard in `L2TabsClientIntegration` that logs a clear error rather than crashing, and
(b) fix `README.md:270-275` to say the jars are committed and that the L2Tabs one is a stub.

---

### [RS-110] `/skills` bakes `skillMaxLevel` into its argument bounds at command-registration time

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/common/command/SkillLevelCommand.java:32,37,41`

**Problem**

The `level` argument is built as
`IntegerArgumentType.integer(1, HandlerCommonConfig.HANDLER.instance().skillMaxLevel)`. This is
evaluated once, inside `register(...)`, which runs from `RegisterCommandsEvent`
(`PlayerLifecycleHandler.java:87-96`). The bound is therefore frozen at whatever `skillMaxLevel`
was when the server built its command tree.

**Why it matters**

`/updateskilllevel` and the YACL config screen both change `skillMaxLevel` at
runtime; `/skillsreload` reloads the config from disk. None of these rebuild the Brigadier tree.
The admin command that is supposed to be the authoritative way to set a skill level then rejects
legal values, with a raw Brigadier "Integer must not be more than N" error that names the *old*
cap — pointing the admin at a number that no longer exists in their config.

**Failure scenario**

Pack author sets `skillMaxLevel: 64` in `runicskills.common.json5`, runs
`/skillsreload`, then `/skills Steve strength set 50` → "Integer must not be more than 32". The
config screen shows 64. No log line explains it.

**Fix**

Use an unbounded `IntegerArgumentType.integer(1)` and validate against the *current*
`skillMaxLevel` inside the executor, returning a translated failure
(`commands.message.skill.out_of_range`) when out of range. This also makes the command correct
after a `/reload`, and removes the last reason for `SkillLevelCommand` to touch config at
registration time.

---

### [RS-111] Public-API event javadoc contradicts the actual firing order

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/event/SkillLevelUpEvent.java:9-14`; `src/main/java/com/otectus/runicskills/event/PassiveLevelUpEvent.java:9-14`

**Problem**

`SkillLevelUpEvent`'s javadoc says the event fires "after the cost/level-gate checks
succeed **and after the capability is mutated**… If a subscriber cancels the event, the level
increment is **rolled back** in the same tick." `PassiveLevelUpEvent`'s javadoc makes the same
claim. The actual call sites do the opposite: `SkillLevelUpSP.java:82-86` posts the event and
`return`s on cancellation *before* `capability.addSkillLevel(...)` on line 86; `PassiveLevelUpSP`
posts at line 62 and returns before `capability.addPassiveLevel(...)` at line 66. There is no
rollback anywhere — the mutation simply never happens. (`docs/API_EVENTS.md`'s table is correct;
only the javadoc is wrong, so the two authoritative sources disagree.)

**Why it matters**

This is a documented-stable public API. A subscriber that trusts the javadoc will
assume the capability already reflects `newLevel` when the event fires and will read
`SkillCapability.get(player).getSkillLevel(skill)` inside its handler — getting `oldLevel` instead.
Worse, "rolled back" implies the mutation-then-undo is observable, so a handler that snapshots
state on cancel will snapshot the wrong thing.

**Failure scenario**

An addon mod subscribes to `SkillLevelUpEvent` to award a reward at level 20. It
reads the level from the capability rather than `event.getNewLevel()`, sees 19, and never fires.

**Fix**

Correct both javadocs to "fires after validation and **before** the capability is mutated;
cancelling aborts the change (nothing is written, so there is nothing to roll back)". While there,
add `@since 1.2.0` tags and document that `getEntity()` is always a `ServerPlayer` on these paths.

---

### [RS-112] `icons/` at the repo root is a stale, content-diverged duplicate of the shipped perk textures

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `icons/` (399 PNGs, 1.7 MB, 10 skill folders)

**Problem**

`icons/<skill>/<perk>.png` mirrors the directory layout of
`src/main/resources/assets/runicskills/textures/skill/<skill>/<perk>.png`, but the two have
diverged in both count and content. Per-skill counts: `icons/magic` has 40 files vs 99 in assets;
`icons/endurance` 40 vs 57; every folder is short. Spot-checking identical filenames shows
*different bytes* — `icons/tinkering/alloy_master.png` is `260da297…` while the shipped
`textures/skill/tinkering/alloy_master.png` is `cc93426b…`.

**Why it matters**

There are now three candidate sources of truth for perk art — `icons/`,
`tools/icongen/` (which generates into `tools/icongen/out/` and whose README says to copy from
there into `src/main/resources/...`), and the shipped assets. `PerkTextureResolutionTest` only
validates the shipped assets, so `icons/` can rot indefinitely. A contributor who edits the wrong
tree does work that never ships and gets no build failure.

**Failure scenario**

An artist is asked to retouch the Magic icons, finds `icons/magic/`, updates the
40 files there, opens a PR. CI passes (the test only reads `src/main/resources`). Nothing changes
in game.

**Fix**

Delete `icons/` (git history preserves it), and add a line to `tools/icongen/README.md`
stating that `src/main/resources/assets/runicskills/textures/skill/` is the only source of truth
and `tools/icongen/out/` is a scratch directory. If `icons/` is meant to be the CurseForge/press
image set, move it to `docs/press/` and say so.

---

### [RS-113] A third-party mod's texture is committed at the repository root

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `tab_menu_buttons.png` (repo root, 10,732 bytes)

**Problem**

This file is byte-identical (`eb4ad1e5618c257bbd2ebfd4d9a3c4e1`) to
`assets/legendarytabs/textures/gui/tab_menu_buttons.png` extracted from
`libs/legendarytabs-1.20.1-1.1.3.1.jar`. It is tracked in git at the repo root, outside any
resource directory. The repository is Apache-2.0 (`gradle.properties:53`) and `README.md`'s credits
section explicitly states "Runic Skills does not bundle any third-party code" and "GUI textures,
panels, and card artwork by the Runic Skills authors."

**Why it matters**

It is an unlicensed third-party art asset redistributed under the project's own
Apache-2.0 grant, directly contradicting the README's licensing claim. It also serves no build
purpose — `LegendaryTabRunicSkills.java:47` correctly references it at runtime as
`new ResourceLocation("legendarytabs", "textures/gui/tab_menu_buttons.png")`, i.e. from the
Legendary Tabs mod's own jar.

**Failure scenario**

The Legendary Tabs author (or CurseForge) notices the asset in a public
Apache-2.0 repo and files a takedown/DMCA against the project.

**Fix**

`git rm tab_menu_buttons.png`. If it was kept as a UV/coordinate reference for the blit math in
`LegendaryTabRunicSkills`, replace it with a comment in that file recording the atlas dimensions and
the source jar, or a hand-drawn diagram.

Related: `libs/legendarytabs-1.20.1-1.1.3.1.jar` (78 KB, the real mod jar including its assets and
`mods.toml`) is *also* committed and un-ignored on purpose (`.gitignore:119-124`). Redistributing a
full third-party mod jar in a public repo carries the same risk, and unlike the L2Tabs stub it is
not a first-party artifact. See the next finding.

---

### [RS-114] There is no Forge data generation, despite a `runData` run configuration and a generated-resources source directory

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Medium

**Affected code:**
- `build.gradle:101-107,118`; `src/main/resources/data/runicskills/tags/blocks/`; `src/main/resources/assets/runicskills/models/item/`

**Problem**

`build.gradle` configures a `data` run (lines 101-107, output `src/generated/resources/`)
and adds that directory as a resources srcDir (line 118). A grep for `GatherDataEvent`,
`DataGenerator`, `DataProvider`, `TagsProvider`, and `ModelProvider` across `src/main/java` returns
**zero** hits. `src/generated/resources/` does not exist. `./gradlew runData` therefore boots a
full data-generation server and emits nothing.

What is being maintained by hand today: 2 block tags, 1 item model, `sounds.json`, and — the real
cost — **2,061 lang keys** in `en_us.json` describing 522 perks, 38 passives, ~50 titles, and the
Powers catalog, all of which are declared in Java (`RegistryPerks.register("name", …)`, 472 call
sites) and must be mirrored by hand into JSON.

Assessment (as requested): full datagen for models/recipes/loot is **not** warranted — there is one
item and no recipes or loot tables (`CLAUDE.md` claims `data/runicskills/` holds "recipes, loot
tables, tags"; only tags exist). But the lang file *is* worth generating. A
`LanguageProvider` that walks `RegistryPerks`/`RegistryPassives`/`RegistryPowers`/`RegistryTitles`
and emits `perk.runicskills.<id>` / `.description` stubs would make the "registered a perk, forgot
the lang key" failure mode structurally impossible, and would produce a diffable list of
untranslated strings.

**Failure scenario**

A perk is registered with no `perk.runicskills.<id>` key. Nothing fails at build
time — `PerkTextureResolutionTest` checks the *icon* but not the *name* — and the perk renders in
the GUI as the raw key `perk.runicskills.frostbrand`.

**Fix**

Either delete the `data` run config and the `src/generated/resources` srcDir (honest: "we don't
use datagen"), or add a single `LanguageProvider`-based `GatherDataEvent` subscriber for the
en_us keys and wire `runData` into the release checklist. Given the 2,061-key file, the second is
the better investment. Independently, correct `CLAUDE.md`'s claim about recipes and loot tables.

---

### [RS-115] CI does not run a dedicated-server smoke test, does not publish test results, and misdescribes what it runs

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Medium

**Affected code:**
- `.github/workflows/build.yml` — lines 33-42

**Problem**

The entire pipeline is one `./gradlew --no-daemon build` step labelled
"Build (compile + checkSidedImports + reobf + jar)". Observations:

1. **Tests do run** — `build` → `check` → `test`, and `check` also depends on `checkSidedImports`,
   `checkLockProviders`, and `checkVersionConsistency` (`build.gradle:434,482,521`). But the step
   name omits tests entirely, and there is no `actions/upload-artifact` for
   `build/reports/tests/test` or `build/test-results`, so when a test fails a maintainer gets a
   Gradle stack trace in the log and no HTML report.
2. **No dedicated-server smoke test.** This is the single highest-value missing check for *this*
   mod. The repo's own history documents a dedicated-server boot crash
   (`docs/SMOKE_TESTS.md:1.1` — "Pre-1.1.0 this crashed with `NoClassDefFoundError:
   dev/isxander/yacl3/…`"), and `checkSidedImports` exists specifically as a *source-level proxy*
   for that runtime property. A source lint cannot catch a `Class.forName` of a client class, a
   `DistExecutor` misuse, or a YACL type reached through a lambda — only booting a server can.
3. **No `runData` / no `runGameTestServer`.** `build.gradle:97-99` defines a `gameTestServer` run
   config with `forge.enabledGameTestNamespaces = runicskills`, but there are zero GameTests in the
   repo, so the config is decorative.
4. **No wrapper validation.** `gradle/wrapper/gradle-wrapper.jar` is committed (correctly) but CI
   never runs `gradle/actions/wrapper-validation`, so a tampered wrapper jar would execute
   unchallenged.
5. **`fetch-depth`.** `actions/checkout@v4` defaults to depth 1, which is fine here since nothing
   reads git history — but `checkVersionConsistency` reads `CHANGELOG.md`, so a shallow checkout is
   sufficient. No issue; noted for completeness.

**Failure scenario**

A refactor moves a YACL-annotated field read into a common-loaded code path in a
way the regex lint doesn't match. CI is green. Every dedicated server without YACL crashes on boot
— the exact 1.0.x regression, shipped again.

Fix, in priority order:
```yaml
- name: Unit tests + lints
  run: ./gradlew --no-daemon check
- name: Upload test report
  if: always()
  uses: actions/upload-artifact@v4
  with: { name: test-report, path: build/reports/tests/test }
- name: Dedicated-server boot smoke test
  run: |
    mkdir -p run && echo "eula=true" > run/eula.txt
    timeout 600 ./gradlew --no-daemon runServer > server.log 2>&1 || true
    grep -q 'Done (' server.log || (tail -200 server.log; exit 1)
    grep -qi 'NoClassDefFoundError\|Mixin apply failed' server.log && (tail -200 server.log; exit 1) || true
```
Server startup on a ForgeGradle userdev setup is entirely feasible in CI (the run config already
passes `--nogui`); it needs `eula.txt`, a `--stop`-equivalent (grep-and-kill on the "Done" line, as
above), and roughly 3-5 minutes on a warm Gradle cache. Add `gradle/actions/wrapper-validation@v4`
as a first step. Consider adding a matrix leg that boots the server with a bare `mods/` folder (no
YACL, no L2Tabs) to lock down `SMOKE_TESTS.md` rows 1.1 and 1.4 automatically.

---

### [RS-116] `gradle.properties` carries eight dead properties, two of which contradict `build.gradle`

**Severity:** Medium | **Confidence:** High | **Impact:** Low | **Effort:** Low

**Affected code:**
- `gradle.properties` — lines 31, 34, 42, 64, 65, 68, 69, 70

**Problem**

A reference count against `build.gradle` shows zero uses for `mapping_channel` (line 31),
`mapping_version` (34), `jei_version` (42), `tetra_version` (64), `mutil_version` (65),
`placebo_version` (68), `apothic_attributes_version` (69), and `apotheosis_version` (70).

The mapping pair is the dangerous one: `gradle.properties` declares
`mapping_channel=official` / `mapping_version=1.20.1` with 20 lines of explanatory comment, while
`build.gradle:33` hard-codes `mappings channel: 'parchment', version: '2023.09.03-1.20.1'`. Anyone
who edits the properties file to change mappings — the documented, MDK-standard place to do it —
will see no effect and no error.

The four Apotheosis/Placebo version properties duplicate the CurseForge file IDs that are instead
hard-coded inline at `build.gradle:241-243` (`6274231`, `5634071`, `6461960`). The values happen to
match today; nothing keeps them in sync.

**Why it matters**

The mapping mismatch will burn exactly one contributor for exactly one confusing
afternoon, and the duplicated Curse IDs are a latent "we bumped Apotheosis but only in one of two
places" bug.

**Failure scenario**

Contributor tries to test against official mappings by editing
`mapping_channel=official` (already its value), sees Parchment parameter names in the decompiled
source anyway, and concludes the toolchain is broken.

**Fix**

Delete the six genuinely unused properties. For mappings, either wire `build.gradle:33` to the
properties (`mappings channel: mapping_channel, version: mapping_version` with the properties set to
`parchment` / `2023.09.03-1.20.1`) or delete both properties and their comment block. Move the four
CurseForge file IDs into properties and reference them from `build.gradle:241-243`.

---

### [RS-117] `mods.toml` is unmodified MDK boilerplate: no issue tracker, homepage, logo, or update JSON

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `src/main/resources/META-INF/mods.toml` — lines 1-39

**Problem**

The file still carries the full MDK comment block ("This is an example mods.toml file…")
and every optional metadata field remains commented out: `issueTrackerURL` (line 14),
`updateJSONURL` (23), `displayURL` (26), `logoFile` (28), `credits` (30), `displayTest` (39).

Why it matters, specifically for this mod:
- **No `issueTrackerURL`** means Forge's crash reports and the in-game mod list give users no place
  to report bugs. The README says to use GitHub issues; nothing in the shipped jar does.
- **No `updateJSONURL`** while the mod ships its **own** hand-rolled update checker
  (`RunicSkills.java:133-177`) that fires an unconditional HTTPS GET to
  `raw.githubusercontent.com/otectus/runicskills/master/VERSION` on every mod construction. Forge's
  built-in `VersionChecker` does exactly this, correctly, off-thread, with user-facing UI, and
  respects the user's global "check for updates" preference. The bespoke implementation duplicates
  it with a `CompletableFuture.runAsync` on the common pool during mod construction.
- **No `logoFile`** despite the project having plenty of art.

**Failure scenario**

A user crashes with a Runic Skills mixin conflict. The crash report's mod table
shows Runic Skills with no issue URL. They post it to the *other* mod's tracker.

**Fix**

Strip the MDK comments, fill in `issueTrackerURL`, `displayURL`, `logoFile`, and `credits`.
Then either adopt Forge's `updateJSONURL` and delete `RunicSkills.getLatestVersion()` (~45 lines
including the `java.net` imports), or at minimum keep the custom checker and document why.

---

### [RS-118] The update checker's repository URL contradicts every documented URL

**Severity:** Medium | **Confidence:** Medium | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/RunicSkills.java:166`; `README.md:89,320`; `docs/API_EVENTS.md` (footer); `COMMENT_TRIAGE.md:7`

**Problem**

The runtime update check fetches
`https://raw.githubusercontent.com/otectus/**runicskills**/master/VERSION`. Every user-facing doc
points at `https://github.com/otectus/**runic-skills**` (README's release link line 89, README's
issue tracker line 320, `COMMENT_TRIAGE.md:7`), while `docs/API_EVENTS.md`'s footer uses
`otectus/runicskills`. At most one of these repositories exists.

**Why it matters**

If the checker's URL is the wrong one, `getLatestVersion()` throws
`FileNotFoundException` on every launch — which is explicitly caught and downgraded to a DEBUG line
(`RunicSkills.java:152-156`), so the update checker has been silently non-functional for an unknown
number of releases and nobody would know. `build.gradle:489-521` (`checkVersionConsistency`) exists
solely to keep the `VERSION` file honest *for this checker*, so if the URL is wrong the whole
mechanism is dead weight. Conversely if the checker's URL is right, every documented link 404s.

**Failure scenario**

Users never see the "new version available" banner for a security-relevant
release; maintainer never learns because the failure is DEBUG-level.

**Fix**

Determine the canonical repository name and make all five references agree. Add an assertion to
the release checklist (or a CI step on tags) that `curl -fsSL <the URL>` returns the current
`VERSION` contents. Consider logging the update-check failure at INFO on the *first* failure so it
is not invisible.

---

### [RS-119] `CHANGELOG.md` is out of chronological order, has four `[Unreleased]` sections, and omits shipped releases

**Severity:** Medium | **Confidence:** High | **Impact:** Low | **Effort:** Medium

**Affected code:**
- `CHANGELOG.md` — headings at lines 503, 525, 554, 590, 617, 629, 703, 712

**Problem**

`[1.3.7]` (line 503) appears **above** `[1.3.8]` (line 525), violating the file's own
reverse-chronological ordering. Between `[1.3.8]` and `[1.1.0]` sit four separate
`## [Unreleased]` sections (554, 590, 617, 629, 703) describing work that has since shipped.
Versions `1.2.0`, `1.3.0` through `1.3.6`, and the entire `1.4.x` line have no entries at all —
despite `README.md` and `docs/API_EVENTS.md` repeatedly citing "since 1.2.0" and "1.3.0 ships two
data-driven features". `FOLLOW_UPS.md:2` flagged this at 1.3.7 and it has not been addressed.

Note that `checkVersionConsistency` (`build.gradle:503-504`) uses `changelogMatcher.find()` — the
*first* `## [x.y.z]` match — so it validates only that the top entry is `1.6.1`. It cannot detect
ordering violations, `[Unreleased]` sections, or gaps.

**Why it matters**

The changelog is the only migration guide for pack authors; `FOLLOW_UPS.md` itself
directs readers to "CHANGELOG migration note" as the stability contract for the public event API
(`docs/API_EVENTS.md`, "Stability commitment"). A pack author upgrading 1.3.5 → 1.6.1 has no
document describing what changed.

**Failure scenario**

A pack author on 1.3.2 upgrades to 1.6.1, hits the 1.5.0 Botania/Blood Magic perk
removal (which silently deletes save data references), and finds no changelog entry between their
version and the removal.

**Fix**

Reorder 1.3.7/1.3.8; fold the four `[Unreleased]` sections into the releases that actually
shipped them; backfill stub entries for 1.2.0 and 1.3.0-1.3.6 even if only "see git log". Then
strengthen `checkVersionConsistency` to also assert (a) exactly one `[Unreleased]` section at most
and it must be first, and (b) all `## [x.y.z]` headings parse as versions in strictly descending
order. That is ~15 lines of Groovy in a task that already reads the file.

---

### [RS-120] Command feedback is unlocalised and uses the wrong Brigadier feedback channel in five of nine commands

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `GlobalLimitCommand.java:32`; `UpdateSkillLevelCommand.java:30,40`; `RegisterItem.java:41,46,59,66,84,97`; `SkillsReloadCommand.java:48`; `PowersCommand.java:113,119-124,135,142,149,155,163,167-168,172,177,180`

**Problem**

`SkillLevelCommand`, `ListSkillsCommand`, `RespecCommand`, and `TitleCommand` correctly use
`Component.translatable(...)` with keys that all exist in `en_us.json`
(`commands.message.skill.get/set`, `commands.message.listskills.header/global`,
`commands.message.respec.success`, `commands.message.title.set/unset`,
`commands.message.capability.not_found`). The other five commands hard-code English
`Component.literal` strings — 20+ distinct messages including `"Run as a player."`,
`"Unknown power: "`, `"Cannot equip … — slot full or already equipped."`,
`"No item detected in main hand!"`, `"Forcing refresh of skills..."`.

Compounding this, `RegisterItem` (lines 41, 46, 59, 66, 84, 97) and `SkillsReloadCommand` (line 48)
send feedback via `player.sendSystemMessage(...)` rather than
`source.sendSuccess(…, true)` / `source.sendFailure(…)`. That has three consequences:
- Console and command-block invocations get **no output at all** (the messages are addressed to a
  `Player` that only exists in the `instanceof` branch).
- No operator broadcast — `sendSuccess(..., true)` is what echoes admin actions to other ops and to
  the server log. `/registeritem` permanently mutates the server's lock list with zero audit trail.
- `RegisterItem.execute` returns `Command.SINGLE_SUCCESS` even when the source is not a player and
  nothing at all happened (the whole body is inside `if (… instanceof Player player)`, line 38, and
  line 100 returns success unconditionally).

**Why it matters**

`/registeritem` is the mod's live-config-editing command. Running it from a command
block or the server console reports success while doing nothing, and running it as a player leaves
no record that the lock list was changed.

**Failure scenario**

An admin adds `/registeritem magic 30` to a command block for a setup script. It
reports success, the block runs, and nothing is registered — discovered weeks later when players
walk past the intended gate.

**Fix**

Add the ~20 missing keys to `en_us.json` under the existing `commands.message.*` namespace and
convert the literals. Replace every `player.sendSystemMessage` in a command with
`ctx.getSource().sendSuccess(() -> …, true)` or `sendFailure(…)`. Return `0` from `RegisterItem`
when the source is not a player, with an explicit `sendFailure` explaining that the command requires
a held item.

---

### [RS-121] `SkillArgument` performs no validation and suggests a hardcoded list

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/common/command/arguments/SkillArgument.java:19-24,31-33`

**Problem**

Three related defects in one 50-line class.
1. `parse(StringReader)` (line 31-33) is `return reader.readString()` — it accepts **any** string.
2. `ERROR_UNKNOWN_TITLE` is declared and initialised (lines 20-24, with the translation key
   `commands.argument.skill.not_found`, which exists in `en_us.json`) and is **never thrown**. It is
   dead code, and the lang key is consequently unreachable.
3. `EXAMPLES` (line 19) is a hardcoded `List.of("Strength", "Dexterity", …)` used as the *only*
   suggestion source, rather than reading `RegistrySkills.getCachedValues()`. It also suggests
   capitalised names while `RegistrySkills.getSkill(key)` is looked up by registry path (lowercase,
   e.g. `"strength"`).

Contrast `TitleArgument`, which does this correctly: `parse` calls `getResource(...)` which throws
`ERROR_UNKNOWN_TITLE` for unregistered ids (lines 39-41, 49-54), and `listSuggestions` enumerates
the live registry (line 44-47).

Consequence at the call sites: `SkillLevelCommand.getSkill/setSkill/addSkill/subtractSkill` all do
`Skill skill = RegistrySkills.getSkill(skillKey); if (player != null && skill != null) { … } return 0;`
(lines 52-54, 71-73, 92-94, 118-120). A typo'd skill name falls through to a bare `return 0` — no
message, no error, nothing. Brigadier reports the command as failed with no explanation.

**Failure scenario**

Admin runs `/skills Steve Strength set 20` using the capitalised name the
autocomplete just suggested. `getSkill("Strength")` returns null (registry paths are lowercase), the
command silently does nothing, and the admin retries several times before giving up.

**Fix**

Make `SkillArgument.parse` resolve against `RegistrySkills` and throw `ERROR_UNKNOWN_TITLE`
(rename it `ERROR_UNKNOWN_SKILL`) on miss — Brigadier then renders the translated error
automatically and the four null checks in `SkillLevelCommand` become unreachable. Drive
`listSuggestions` from `RegistrySkills.getCachedValues()` so suggestions match the accepted casing
and stay correct when KubeJS registers a custom skill.

---

### [RS-122] All nine commands register unnamespaced top-level literals that collide with other mods

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/registry/events/PlayerLifecycleHandler.java:87-96`

**Problem**

The registered root literals are `skills`, `titles`, `skillsreload`, `registeritem`,
`globallimit`, `updateskilllevel`, `respec`, `listskills`, `powers`. Several of these are
high-collision names in the 1.20.1 RPG-mod ecosystem — `/skills` is used by Project MMO and
several levelling mods, `/titles` by multiple cosmetic-title mods, `/powers` by Origins-style mods.
Brigadier merges same-named roots from different mods into one tree; whichever registers a
conflicting child node loses, and the failure is a confusing "Unknown or incomplete command" rather
than a load error.

**Why it matters**

`/respec` and `/globallimit` in particular are generic enough that a collision is
plausible in any large pack, and the resulting behaviour (one mod's subcommand silently shadowing
another's) is very hard to diagnose from a bug report.

**Failure scenario**

A pack ships Runic Skills alongside Project MMO. `/skills <player>` resolves into
PMMO's tree, `/skills <player> <skill> set` errors out, and the pack author reports "Runic Skills
commands don't work".

**Fix**

Register a single `/runicskills` (or `/rs`) root with all nine as children, and keep the
current top-level literals as `redirect()` aliases for backwards compatibility. This is a ~30-line
change in `PlayerLifecycleHandler.onRegisterCommands` plus one `Commands.literal` wrapper per
command class, costs nothing at runtime, and makes the whole surface greppable and documentable.

---

### [RS-123] No player-accessible read commands; all nine are gated at permission level 2

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- all nine command classes — `.requires(source -> source.hasPermission(2))`

Assessment of the current split (as requested — which should be *less* protected, which *more*):

**Should be player-accessible (permission 0), read-only:**
- `/listskills <self>` (`ListSkillsCommand.java:22`) — dumps skill levels. The GUI already shows
  this to the player; gating the text form at op-2 only prevents players from copy-pasting their
  state into a bug report. A `/listskills` with no argument defaulting to the caller, at permission
  0, would be strictly useful and leak nothing.
- `/powers view` and `/powers list` (`PowersCommand.java:40,46,41`) — both operate only on the
  caller (`ctx.getSource().getEntity()`, lines 89, 112) and print catalog/equipped state the player
  can already see. Currently op-2, so the `EQUIPPED_SUGGESTIONS` provider (lines 70-84) is unusable
  by the very players it describes.

**Correctly op-2, keep:** `/skills set|add|subtract`, `/titles`, `/respec`, `/registeritem`,
`/globallimit`, `/skillsreload`, `/powers equip|unequip` (the equip path explicitly bypasses the
skill-level gate — see the class javadoc at `PowersCommand.java:32-34`).

**Arguably needs *more* than op-2:** `/updateskilllevel` and `/globallimit` permanently rewrite the
server's config file on disk and broadcast to every client. `/updateskilllevel` already recognises
this and restricts itself to console/command-block only (`UpdateSkillLevelCommand.java:28-32`) —
but `/globallimit` does the same class of thing at op-2 with no such restriction, so the two are
inconsistent. Either both should be console-only, or both op-2; there is no defensible reason for
them to differ. (Given `docs/SMOKE_TESTS.md:3.1` documents `/globallimit 256` being run "from
singleplayer with cheats" as a fix verification, op-2 is probably the intended answer, and
`/updateskilllevel`'s console-only restriction should be relaxed to op-2 for consistency and to
make the command usable at all in singleplayer.)

**Fix**

Split each read subcommand into its own `.requires(…)` node rather than gating the whole root
literal. Brigadier supports per-node permissions; `Commands.literal("powers").then(literal("view")
.requires(s -> true)…)` costs one line.

---

### [RS-124] No administrative or diagnostic commands exist for the most common support workflows

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/common/command/` (9 commands)

**Problem**

For a mod with 522 perks, 38 passives, ~50 titles, a Powers system with three tiers, a
1,103-field config, and 20+ optional integrations, the entire admin surface is: read skill levels,
set/add/subtract a skill level, set/unset a title, reset everything, reload config, add a lock item,
and set two config caps. Concretely missing, in the order a support workflow needs them:

1. **`/runicskills dump <player>`** — a single command emitting the full capability state (all skill
   levels, all non-zero perk ranks, all passive levels, equipped Marks/Seals/Crown, unlocked +
   selected titles, computed perk budget). Today reproducing a bug report requires `/listskills`
   (skills only) plus `/powers view` (self only, op-2) plus reading NBT. There is no way to see a
   *remote* player's perk ranks at all.
2. **`/runicskills perk <player> <perk> <rank>`** — `/respec` can zero every perk but nothing can
   set one. Testing a perk requires levelling a skill, spending points, and clicking the GUI. This
   is the single biggest gap for both QA and support.
3. **`/runicskills xp <player> <amount>`** — grant skill XP / spendable points. Currently the only
   way to fund a level-up is vanilla `/xp`, which requires knowing
   `ExperienceMath.requiredPoints(level, firstCostLevel, mult, minCost)` by hand.
4. **`/runicskills repair <player>`** — validate and fix invalid capability data: perk ranks above
   `Perk.maxRank`, equipped Powers the player no longer qualifies for (see the `/respec` finding
   above), perks in `disabledPerks` still at rank > 0, skill levels above the current
   `skillMaxLevel` after an admin lowers it, and equipped-perk counts above `maxActivePerks`. The
   codebase already has all these invariants scattered across `SkillCapability`, `PerkCapMath`, and
   `PerkBudgetMath`; a repair command would concentrate them.
5. **`/runicskills powers <player> …`** — `/powers` is caller-only. An admin cannot inspect or fix
   another player's Powers at all.
6. **`/runicskills config get|set <field>`** — with 1,103 config fields and only two of them
   command-reachable, everything else needs a file edit plus `/skillsreload`.

**Why it matters**

The repo's own `COMMENT_TRIAGE.md` is a ledger of user bug reports; several
("how do you disable certain perks?", the `/globallimit` bug) would have been diagnosable in one
command with a dump. `docs/SMOKE_TESTS.md` has 40+ manual rows, most of which need exactly the
commands listed above to execute efficiently.

**Failure scenario**

A user reports "my perk budget is stuck". Without a dump command the maintainer
must ask for the world save, load it, and read `SkillCapability` NBT by hand.

**Fix**

Build `/runicskills dump` first — it is ~60 lines, read-only, needs no new state, and pays for
itself immediately in support. Then `perk` and `xp` for QA, `repair` last (it needs the invariant
set to be agreed on first). Put all of them under the namespaced root proposed above.

---

### [RS-125] `/registeritem` takes an unbounded, unvalidated integer and mutates server config on the game thread

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/common/command/RegisterItem.java:31,49-50,56,69,80,93`

**Problem**

The `level` argument is `IntegerArgumentType.integer()` — the **full** int range including
negatives. The executor branches on `level < 1` (line 56) to mean "remove", which is undocumented
(README:175 says "`/registeritem <skill> 0`", so `0` is the intended sentinel but `-2147483648` is
equally accepted). There is no upper bound and no check against `skillMaxLevel`, so
`/registeritem magic 999999` writes an item lock that can never be satisfied. The `skill` argument
comes from `SkillArgument`, which as noted above validates nothing — so `/registeritem notaskill 5`
writes a `LockItem.Skill("notaskill", 5)` into the persisted config with no error.

Each invocation also calls `HandlerLockItemsConfig.HANDLER.save()` (lines 69, 80, 93) synchronously
on the server thread. `save()` serialises the entire lock list — 500+ default entries plus every
generated integration lock — through Gson to a temp file and does an atomic move
(`ConfigHolder.java:157+`). Then `HandlerSkill.ForceRefresh()` rebuilds the lock cache and
`ConfigSyncCP.sendToAllPlayers()` broadcasts it.

**Why it matters**

The junk-skill case is the worst — an unrecognised skill name in a persisted
`LockItem` is silently written to `runicskills.lockItems.json5` and reloaded on every boot, and the
lock check presumably never matches it, so the item appears unlocked while the config file says it
is gated. There is no validation pass on load that would surface it.

**Failure scenario**

Admin types `/registeritem Magic 30` (capitalised, as `SkillArgument` suggests).
A `LockItem.Skill("Magic", 30)` is written. Nothing is gated. The config file looks correct.

**Fix**

Bound the argument to `IntegerArgumentType.integer(0, HandlerCommonConfig…skillMaxLevel)`
resolved at execution time (same fix as `SkillLevelCommand`), validate the skill via a fixed
`SkillArgument`, and use an explicit `remove` subcommand instead of the `level < 1` sentinel.
Move the `save()` to an off-thread write or debounce it; at minimum add
`sendSuccess(…, true)` so the config mutation is logged.

---

### [RS-126] `HandlerCommonConfig`: 1,103 public mutable fields in a single 4,836-line class

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** High

**Affected code:**
- `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java` (236 KB, 4,836 lines, 1,141 `@SerialEntry` annotations)

Honest assessment (as requested): **this is a real maintainability problem, but not the one it looks
like.** The class itself is mechanically simple — it is a flat list of annotated public fields with
no logic. Reading or editing any single field is easy. The problem is entirely in what depends on
it:

1. **`CommonConfigSyncCP` is a 34 KB hand-written mirror.** It re-declares 86 of the fields as its
   own instance fields, writes them in `toBytes()` (90 lines), reads them back in a 91-line
   constructor, and assigns them one-by-one in a 97-line `handle()` — every line of the form
   `HandlerCommonConfig.HANDLER.instance().fieldName = this.fieldName`. Adding a
   client-visible config field requires four coordinated edits in strict positional order across two
   files. Getting the *order* wrong (not the names) silently corrupts every subsequent field on the
   wire.
2. **No enforcement that the mirror is complete.** I verified today that the set of config fields
   read by `client/**` is a subset of the synced set (0 violations), so the invariant currently
   holds — but nothing keeps it holding. The next client-side read of an unsynced field produces a
   client that renders the *single-player operator's local file* while the server enforces something
   else.
3. **`@Clamp` is duplicated against `@IntField`/`@FloatField` on every numeric field.** The `Clamp`
   javadoc says "Keep the ranges in sync with the YACL annotations on the same field" — an
   invariant maintained by hand across ~400 fields with no check.

**A safe, incremental remediation** (explicitly not a rewrite):

- **Step 1 (no behaviour change, high value):** add a test that reflects over `HandlerCommonConfig`
  and `CommonConfigSyncCP` and asserts (a) every field name declared in the packet exists on the
  config with the same type, and (b) the packet's declaration order matches its `toBytes` write
  order and its constructor read order. This is pure reflection + source scanning, needs no
  Minecraft, and would have caught any historical wire-order bug. ~80 lines.
- **Step 2:** add a test asserting every `@Clamp(min,max)` matches the `@IntField`/`@FloatField`
  bounds on the same field. ~30 lines, pure reflection.
- **Step 3:** add a test asserting every config field read anywhere under `client/**` appears in a
  sync packet (the scan I ran manually). ~40 lines.
- **Step 4 (only after 1-3 are green):** replace the hand-written `toBytes`/read/assign trios with a
  reflective codec driven by a `@Synced` marker annotation on the config fields. With steps 1-3 as
  the safety net this becomes a mechanical, verifiable change rather than a leap.
- **Step 5 (optional, cosmetic):** split the class into `HandlerCommonConfig` (general + item locks
  + titles) and per-domain partials via composition. Low value relative to 1-4; the flat file is
  actually easier to grep than five files would be. **Do not do this first.**

---

### [RS-127] Progression mutation sites update different dependent systems; four of nine are incomplete

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Small

Files: `network/packet/common/SkillLevelUpSP.java:86-92`, `PassiveLevelUpSP.java:65-70`, `PassiveLevelDownSP.java:56-59`, `TogglePerkSP.java:150-165`, `SetPlayerTitleSP.java:57-67`; `common/command/SkillLevelCommand.java:69-140`, `TitleCommand.java:33-56`, `RespecCommand.java:41-58`; `registry/events/PlayerLifecycleHandler.java:121-155`

**Problem**

There is no single "progression changed" funnel. Each mutation site hand-picks which of
the six dependents it notifies (capability, attributes, titles, quest bridge, client sync, display
name). The matrix:

| site | cap | attributes | titles | quest bridge | client sync | display name |
|---|---|---|---|---|---|---|
| `SkillLevelUpSP` | ✓ | — | ✗ (≤200t late) | ✓ | ✓ | — |
| `PassiveLevelUpSP` | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| `PassiveLevelDownSP` | ✓ | ✓ | **✗** | ✓ | ✓ | — |
| `TogglePerkSP` | ✓ | ✗ | ✗ | ✓ (via event) | ✓ | — |
| `SetPlayerTitleSP` | ✓ | — | — | ✓ | ✓ | ✓ (flag-gated) |
| `/skills set\|add\|subtract` | ✓ | — | ✗ | **✗** | ✓ | — |
| `/title … unset` | ✓ | — | — | **✗** | ✓ | **✗** |
| `/respec` | ✓ | ✓ | ✗ | ✓ | ✓ | ✗ |
| `Clone` / `EntityJoinLevel` | ✓ | ✓ | ✓ | ✓ | ✓ | — |

**Why it matters**

The two admin-command rows are the ones players notice. `/skills … add` never calls
`RunicQuestBridge.onSkillLevelChanged`, so an FTB Quests `runic_skill_level` or `runic_global_level`
task granted by an admin does not progress until the player relogs (`FTBQuestsIntegration.onPlayerLoggedIn`
→ `refreshAll` is the only backfill). `/title … unset` revokes `unlockTitle` but leaves
`capability.playerTitle` pointing at the revoked title, does not call
`RunicQuestBridge.onTitleUnlockedChanged(..., false)` (so the quest task stays complete), and does
not `refreshDisplayName()`/`refreshTabListName()` — the player keeps wearing and displaying a title
they no longer hold. `PassiveLevelDownSP` is the only passive path that skips
`RegistryTitles.syncTitles`, which its level-up twin calls with a `// P5` comment.

**Failure scenario**

Admin runs `/skills Steve strength set 30` to fix a rollback. Steve's quest chain
gated on Strength 20 stays locked, and his title conditions do not re-evaluate for up to 10 seconds
(`TickEventHandler.onPlayerTickLow`, `tickCount % 200`). Admin then runs `/title Steve champion unset`;
Steve's nameplate still reads `[Champion]` and his quest reward is already banked.

**Fix**

One `ProgressionSync.after(ServerPlayer, ChangeKind)` helper that does
attributes → titles → quest bridge → sync → display-name refresh, called from all nine sites.

---

### [RS-128] `/respec` residue: eight kinds of state survive a "full reset"

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Small

**Affected code:**
- `common/command/RespecCommand.java:41-58`

*Independently corroborated in the build/tests/docs pass.*

**Problem**

`/respec` zeroes `skillLevel` (to 1), `passiveLevel` and `perkRank`, then re-runs
`modifierAttributes` and syncs. Enumerating everything it leaves behind:

1. **Equipped Powers** — `equippedMarks`, `equippedSeals`, `equippedCrown` are untouched; a
   respecced player keeps every Mark/Seal/Crown active at skill level 1. (08 reports this alone.)
2. **`powerCooldowns` / `powerWindows`** — persisted NBT maps, never cleared or pruned.
3. **`perkCooldowns`** — including `COOLDOWN_PERK_SWAP`, so the player is on perk-swap cooldown
   immediately after the respec that was supposed to let them rebuild.
4. **`unlockTitle`** — every title stays unlocked (`setRequirement` has no re-lock branch, and 02
   flags the `administrator` case), so titles earned at level 30 are still selectable at level 1.
5. **`playerTitle`** — still the level-30 title; the periodic scan re-affirms rather than revokes it.
6. **Orphaned permanent attribute modifiers** — `modifierAttributes` only walks *passives*.
   `COUNTER_ATTACK_UUID` is added with `addPermanentModifier` in `CombatEventHandler:319` and only
   removed by a branch gated on `getCounterAttack()`, which 03 shows is never true; the ATTACK_DAMAGE
   bonus survives the respec. `BLADE_STORM_ATTACK_SPEED_UUID` (transient) is only cleared by the
   prune loop while the player is online.
7. **`PerkEffectsHandler` / `PowerRuntime` / `CombatEventHandler` per-player memory** — dodge windows,
   berserk windows, survive-lethal cooldowns, spell history, proc windows all persist.
8. **The player's XP** — not refunded, by design, but combined with (1)-(6) the net effect of a
   respec is "loses all progression, keeps all rewards".

Also: with `dropLockedItems = true` the next `PlayerTickEvent` after a respec force-drops the
player's held and off-hand gear on the ground, because every item is now above their level-1 locks —
in survival, wherever they happen to be standing.

**Failure scenario**

An admin respecs a player to reset a broken build. The player keeps their Crown,
their `administrator`-adjacent titles, a permanent +damage modifier, and watches their netherite
sword drop into the lava they were standing over.

**Fix**

`/respec` should clear equipped Powers, both power maps, `perkCooldowns`, reset `playerTitle`
to `titleless`, re-run `serverPlayerTitles` (with a re-lock branch), strip all mod-owned attribute
modifiers by UUID, and call the per-player `clearPlayer` hooks. It should also refuse to run, or
warn, while `dropLockedItems` is on.

---

Severity: Medium | Confidence: High | Impact: Medium | Effort: Low
File: `src/main/java/com/otectus/runicskills/common/command/RespecCommand.java:41-53`;
`src/main/java/com/otectus/runicskills/common/capability/SkillCapability.java:71-73,297-324`

Problem: `respec(...)` resets all skills to level 1, subtracts all passive levels, and sets every
perk rank to 0. It never touches `capability.equippedMarks`, `equippedSeals`, or `equippedCrown`.
Powers are gated by skill level at equip time (`PowerEquipSP`), so after a respec a player retains
Powers they can no longer qualify for.

**Why it matters**

`/respec` is the documented recovery tool ("If lowering this puts a player over
budget, perk activation is frozen until they respec" —
`HandlerCommonConfig.java`, `perksPerGlobalLevel` comment). An admin using it to fix an
over-budget player leaves a strictly-stronger-than-legal state behind: Marks/Seals/Crown equipped
at a skill level of 1.

Failure scenario: player equips a Crown-tier Power at Magic 40. Admin runs `/respec player` to
undo an exploit. Player is back at Magic 1 with the Crown still equipped and firing in
`PowerEventDispatcher`.

Fix: in `RespecCommand.respec`, after the perk loop, clear the three power collections (or add a
`SkillCapability.clearEquippedPowers()` that both the respec path and the disabled-power filter can
share), then `SyncSkillCapabilityCP.send(player)` as it already does.

---

### [RS-129] `treasureHunterProbability = 0` means "always", and a negative value means "never" — silently

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- `registry/perks/TreasureHunterPerk.java:25-41`; called from `registry/events/CraftingEventHandler.java:41-54`

**Problem**

`int randomizer = (int) Math.floor(Math.random() * getActiveValue(player)[0]);` then
`if (randomizer == i)` against the treasure-list index. Unlike the six `ThreadLocalRandom.nextInt(bound)`
sites that 03/06 report as *throwing* on a non-positive bound, `Math.random() * 0` is simply `0.0`,
so `randomizer` is `0` and index 0 always matches.

**Why it matters**

`0` is the value a pack author naturally writes for "never" — and 06 already
documents that the probability fields carry no `@Clamp`, so `0` reaches runtime. Here it inverts the
mechanic instead of erroring, and the perk is on `BlockEvent.BreakEvent` for the whole `DIRT` tag.
A negative value makes `randomizer` negative, which matches no index, silently disabling the perk
with no log line. There is a second coupling nobody documents: the roll is over `[0, probability)`
but is matched against list indices, so any treasure beyond index `probability - 1` in
`treasureHunterItemList` is unreachable.

**Failure scenario**

`treasureHunterProbability = 0`; a player with Treasure Hunter tills or mines dirt
and receives a guaranteed treasure item per block — an infinite item farm from a config value that
reads as a disable.

**Fix**

`int bound = Math.max(1, (int) value[0]);`, roll once over `[0, bound)`, and pick the treasure
with a second independent roll over `getItems().size()`.

---

### [RS-130] One unparseable item id in `treasureHunterItemList` leaves a null in the drop table and NPEs on block break

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- `registry/perks/TreasureHunterPerk.java:57-85` (build) and `25-41` (read)

**Problem**

In the `xxxList[a;b;c]` branch, `arrayOfItem` is sized `itemsSize` up front and each entry
is written at index `j`. On `parsedItem.isEmpty()` the code `continue`s — skipping both
`arrayOfItem[j] = …` **and** `getItems.add(…)`. The result is a `BlockDrops` whose backing
`Item[]` contains a `null` hole, while the `List<BlockDrops>` is *shorter* than the array. The read
path then does `dropsRandom = floor(random * drops.size())` and dereferences
`drops.get(j).getStack[dropsRandom]` — indexing the array with an index derived from the list size.

**Why it matters**

The two lengths are no longer equal, so `dropsRandom` can land exactly on the null
hole. `stack = itemStack.getDefaultInstance()` then NPEs inside a `BlockEvent.BreakEvent` handler at
`EventPriority.HIGHEST` — an unhandled exception on the server thread during ordinary block breaking.

**Failure scenario**

Pack removes a mod but leaves `"gemsList[oldmod:ruby;minecraft:emerald]"` in the
config. `ConfigParser.parseItem("oldmod:ruby")` returns empty, `arrayOfItem[0]` stays null,
`getItems` has one element. A player with Treasure Hunter breaks dirt, `dropsRandom` = 0, and the
server logs `NullPointerException` — repeatedly, for every dirt block, for every affected player.

**Fix**

Build the item list first, then size the array from the successfully-parsed entries; index the
array with `j`, not `dropsRandom`; null-check before `getDefaultInstance()`.

---

### [RS-131] Six of eight `PowerRuntime.clear()` methods mutate their map outside the lock every other accessor holds

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- `common/powers/PowerRuntime.java` — lines 87, 158, 181, 261, 283 (unsynchronized) vs 119, 209 (synchronized)

**Problem**

Every read/write on `SpellHistory.STORE`, `ProcWindows.STORE`, `InternalCooldowns.STORE`,
`PositionBuffer.STORE` and `SummonRegistry.STORE` is a `static synchronized` method — the author
clearly intended the nested class's monitor to guard a plain `HashMap`. But the `clear(UUID)`
methods on those five (plus `TargetTags` is fine) are declared `static void clear(UUID id) { STORE.remove(id); }`
with no `synchronized`. `DamageTypeMemory.clear` and `TargetTags.clear` *are* synchronized, showing
the omission is accidental.

**Why it matters**

`clear` is called from `PlayerLifecycleHandler`/`PowerEventDispatcher.onLogout`
(server thread) while `PositionBuffer.push` runs from the player-tick path and `ProcWindows.open`
from ISS spell events. On an integrated server the client thread also reaches `PowerRuntime`
through the shared statics. `HashMap.remove` racing `computeIfAbsent` can corrupt the bucket table
(infinite loop on resize, or a lost entry) — the classic unsynchronized-HashMap failure, and one
that will not reproduce in single-player testing.

**Failure scenario**

A player disconnects on the same tick another player's Unraveled snapshot is
pushed; `PositionBuffer.STORE` resizes concurrently with the `remove` and the server thread spins
inside `HashMap.get` at 100% CPU. Six-hour dedicated servers with 300 mods are exactly where this
surfaces once.

**Fix**

Add `synchronized` to the five `clear` methods (or switch every `STORE` to
`ConcurrentHashMap`, matching `CombatEventHandler` and `PacketRateLimiter`).

---

### [RS-132] Static tick baselines are not reset when the server stops, disabling pruning and rate limiting for the rest of the JVM

**Severity:** Medium | **Confidence:** Medium | **Impact:** Medium | **Effort:** Trivial

**Affected code:**
- `registry/events/CombatEventHandler.java:85` (`lastPruneTick`) + `onServerTick` (736-772); `network/PacketRateLimiter.java:14-33`

**Problem**

`lastPruneTick` is a `private static long` holding an absolute overworld `gameTime`, never
reset on `ServerStoppedEvent` (which `PlayerLifecycleHandler` does handle, but only to null
`RunicSkills.server`). The guard is `if (now - lastPruneTick < PRUNE_INTERVAL_TICKS) return;`.
`PacketRateLimiter.lastPacketTick` has the same shape against `server.getTickCount()`.

**Why it matters**

In a single JVM, leaving one world and loading another (single-player, LAN, or a
server that recreates its `MinecraftServer`) restarts the clock from a lower value.
`now - lastPruneTick` becomes a large negative number, which is `< 100`, so `onServerTick` returns
immediately **every tick, forever**. `RECENT_HITS`, `LAST_ATTACKER`, `LAST_STAND_ACTIVE_UNTIL` and
`BLADE_STORM_ACTIVE_UNTIL` then grow without bound and `BLADE_STORM`'s `ATTACK_SPEED` modifier is
never stripped. `PacketRateLimiter` fails the other way: a surviving entry with a high tick makes
`currentTick - lastTick` negative, so `allow()` returns `false` permanently for that player/packet
pair (mitigated in practice because `clearPlayer` runs on `PlayerLoggedOutEvent`, but not if the
process is killed mid-disconnect or the event is skipped).

**Failure scenario**

A player spends four hours in world A (gameTime ≈ 290 000), quits to the title
screen, and loads world B. For the entire remaining session in world B the combat-memory maps never
prune, and any Blade Storm attack-speed modifier applied is permanent.

**Fix**

Reset `lastPruneTick = 0` and clear `PacketRateLimiter.lastPacketTick` from
`ServerStoppedEvent` (or `ServerStartingEvent`); use `Math.abs(now - last) >= interval` as
defence in depth.

---

### [RS-133] Version coherence: three schemas, one of them enforced, none of them linked

**Severity:** Medium | **Confidence:** High | **Impact:** Medium | **Effort:** Moderate

**Affected code:**
- `network/ServerNetworking.java:17-36` (`PROTOCOL_VERSION`), `common/capability/SkillCapability.java:serializeNBT`, `config/storage/ConfigHolder.java`, `gradle.properties`/`VERSION`

**Problem**

The mod has three independently-evolving on-the-wire/on-disk formats and manages exactly
one of them.
- **Packet protocol**: a hand-maintained `PROTOCOL_VERSION` string, currently `"8"`. Its own
  comment block records that the 1.1.0 bump "was never landed in code", which caused an
  `IllegalArgumentException` on player join two releases later. It is enforced (`acceptsVersion`).
- **Save schema**: no version field anywhere in the capability NBT (01). `deserializeNBT` decides
  format per key (the `TAG_BYTE`/`TAG_INT` perk-rank branch is a de-facto v1→v2 migration with no
  version marker).
- **Config schema**: no version field; unknown keys are dropped and the next save deletes them (06).

**Why it matters**

The protocol check gates *connection*, not *data*. Because it is bumped on payload
changes rather than tied to the mod version, two releases can share a protocol version while
disagreeing about persisted data. 1.6.0 and 1.6.1 both report `"8"`, so a client/server pair split
across those versions connects cleanly; if 1.6.x had changed the capability NBT the handshake would
not have caught it. In the other direction, the *only* signal an operator gets that a downgrade has
silently reinterpreted saved player data is a behavioural anomaly.

**Failure scenario**

An operator rolls a server back one patch release after a bad update. The
handshake passes, players connect, and every capability field whose meaning changed is read under
the old rules with no warning and re-saved in the old format on the next tick.

**Fix**

Write a `schemaVersion` int into the capability NBT and the config file; derive
`PROTOCOL_VERSION` from a constant that a build check ties to the mod version; on read, refuse
(or migrate) a schema newer than the running code rather than silently reinterpreting it.

---

### [RS-134] Five registry caches are memoised for the JVM lifetime and never invalidated

**Severity:** Medium | **Confidence:** Medium | **Impact:** Medium | **Effort:** Small

**Affected code:**
- `registry/RegistryPerks.java:4316-4333`, `RegistryPassives.java:115-131`, `RegistrySkills.java:52-68`, `RegistryTitles.java:151-166`, `RegistryPowers.java:209-225`

**Problem**

Each registry exposes `getCachedValues()` / `getX(String)` backed by a `volatile` field
populated on first call and **never** cleared — there is no `invalidate()` and no call site that
nulls them (verified by grep across the tree). The keys are path-only, so two namespaces collide
(01 reports the collision; this is the lifetime half of the same design).

**Why it matters**

These caches sit under everything. `SkillCapability`'s constructor, `serializeNBT`,
`deserializeNBT` and `copyFrom` all iterate `getCachedValues()`; the whole GUI reads through them.
Forge's registry snapshot injection replaces custom-registry contents when a client connects to a
dedicated server, and `/reload`-adjacent flows can rebuild registry views — but the caches are not
notified. `RegistryTitles.rebindAfterReload` explicitly rebinds `TitleModel`s after
`/skillsreload` yet never touches `cachedValues`/`cachedByName`, so a title merged in by
`mergeDefaultsIntoConfig` during that reload can never appear in `getTitle(...)` even though it is
in `titleList`. The lazy-population shape is also a latent boot-order hazard: whichever code
touches a registry first freezes that snapshot, and `SkillCapability`'s field initialisers run at
entity-construction time.

**Failure scenario**

`/skillsreload` on a running server merges a newly shipped built-in title into
`titles.json5` and logs "Merged 1 new built-in title". `rebindAfterReload` correctly reports it
needs a restart for the *registry*, but `RegistryTitles.getTitle` would not see it even if the
registry accepted it, because `cachedByName` was built at boot.

**Fix**

Give each registry an `invalidateCaches()` and call it from `HandlerSkill.ForceRefresh`,
`rebindAfterReload`, and a `RegisterEvent`/registry-snapshot listener.

---

### [RS-135] `/title … unset` leaves the player wearing a title they no longer hold

**Severity:** Medium | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- `common/command/TitleCommand.java:41-49`

**Problem**

The unset branch does `capability.setUnlockTitle(title, false)` + `SyncSkillCapabilityCP.send`
and nothing else. It does not compare against `capability.getPlayerTitle()`, does not reset the
selection to `titleless`, does not call `refreshDisplayName()`/`refreshTabListName()`, and does not
notify `RunicQuestBridge.onTitleUnlockedChanged(player, title, false)`.

**Why it matters**

`SetPlayerTitleSP` validates `getLockTitle(title)` on *selection*, but nothing
re-validates an existing selection. The player keeps the revoked title in
`PlayerEvent.NameFormat`, in `setCustomName` (re-applied by `syncTitles` every 200 ticks), and in
the Skills screen. Any FTB Quests `runic_title_unlocked` task stays complete. The 200-tick scan will
also silently re-grant the title if its conditions still pass, because `setRequirement` is
unlock-only — so the command is only ever cosmetic-and-broken or temporary.

**Failure scenario**

A moderator revokes a title from a player for abuse. The nameplate does not change,
the quest reward stays banked, and ten seconds later `serverPlayerTitles` re-unlocks it.

**Fix**

In the unset branch, reset `playerTitle` to `titleless` when it matches, refresh the display
name, call the quest bridge with `false`, and gate the periodic re-unlock (e.g. an "administratively
revoked" set) if revocation is meant to stick.

---

### [RS-136] `libs/legendarytabs-1.20.1-1.1.3.1.jar` redistributes a third-party mod jar in-repo without its licence text

**Severity:** Low-Medium | **Confidence:** High | **Impact:** Licence hygiene, repo weight, staleness | **Effort:** Low

**Affected code:**
- libs/legendarytabs-1.20.1-1.1.3.1.jar; build.gradle (~line 206); .gitignore (~line 120-124)

**Problem**

This is the *complete* Legendary Tabs mod (compiled classes, assets, lang, textures,
`mods.toml`), force-added to git via `.gitignore` negation. Its `mods.toml` declares
`license="MIT License"`, but no `LICENSE`/copyright notice file accompanies it in the repo. MIT
permits redistribution but requires the copyright and permission notice to travel with copies.
Only the `api/tabs_menu/` classes are actually needed to compile.

**Why it matters**

A vendored binary pinned at 1.1.3.1 also silently ages — the build keeps compiling
against a 2025-05-28 snapshot no matter what users run.

**Failure scenario**

Legendary Tabs ships a 1.2.x API change; the project still compiles clean against
the vendored 1.1.3.1 and `LegendaryTabsClientIntegration.registerTab` fails at runtime.

**Fix**

Resolve from CurseMaven (already configured), or reduce the vendored jar to the `api/` package
and add `libs/LICENSE-legendarytabs.txt` with the upstream MIT notice.

---

### [RS-137] Compile scopes: BetterCombat, KubeJS/Rhino/Architectury, Curios and YACL are on `implementation`, not `compileOnly`

**Severity:** Low-Medium | **Confidence:** High | **Impact:** Dev/runtime confusion, hidden hard deps | **Effort:** Low

**Affected code:**
- build.gradle — dependency block (~line 196-222)

**Problem**

- `implementation fg.deobf('maven.modrinth:better-combat:1.8.5+1.20.1-forge')` (~line 214) — a mod
  that is optional at runtime and only referenced from an optional-gated `@Pseudo` mixin.
- `implementation fg.deobf("maven.modrinth:kubejs…")`, `rhino`, `architectury-forge` (~line 194-196).
- `runtimeOnly(implementation fg.deobf("…curios-forge:5.9.1+1.20.1"))` followed by
  `compileOnly(fg.deobf("…:api"))` (~line 217-218) — the first line puts the **full** Curios jar on
  the compile classpath, defeating the `:api`-only intent of the second.
- `runtimeOnly(implementation fg.deobf("dev.isxander:yet-another-config-lib…"))` +
  `compileOnly(...)` (~line 220-221) — same doubled pattern.

**Why it matters**

`implementation` on a soft-dependency mod means nothing enforces that the code
compiles when the mod is absent, and it makes the "which of these is optional?" question
unanswerable from the build file. The Curios case in particular means a non-API Curios class could
be referenced without any build failure, and would then `NoClassDefFoundError` at runtime.

**Failure scenario**

Someone adds a call to a Curios internal (non-API) class in `HandlerCurios`.
Build passes. Packs with Curios installed work. Nothing breaks in dev. The class is simply not part
of the published API contract and disappears in a Curios update.

**Fix**

`compileOnly` for BetterCombat, and drop the redundant `implementation` from the Curios/YACL
lines so only `:api` / `compileOnly` reaches javac; keep `runtimeOnly` for dev launches.

---

### [RS-138] `MixItemStack` globally rewrites every enchantment tooltip line for every item in the pack

**Severity:** Low-Medium | **Confidence:** High | **Impact:** Tooltip mods, all modded enchantments | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/MixItemStack.java — `#appendEnchantmentNames` (~line 36-48)

**Problem**

When `enableScholarEnchantmentHiding` is on, the injector cancels at HEAD and emits one
copy of `tooltip.perk.scholar.lock_item` per enchantment tag, for every item, on every tooltip.
The mixin's own javadoc (line 30-34) identifies the correct Forge alternative
(`ItemTooltipEvent`, which is client-side and has `Minecraft.getInstance().player`).

**Why it matters**

Cancelling a vanilla tooltip builder is a shared-state takeover — tooltip mods
(JEI/EMI overlays, "show enchantment IDs", curse-highlighting mods) that inject into
`appendEnchantmentNames` are cut out. It is also a **common**-list mixin on a class used
server-side.

**Failure scenario**

Pack enables the option; every enchanted item in the pack shows N identical
"hidden" lines instead of N enchantment names, and a tooltip mod's colour-coding vanishes.

**Fix**

Re-implement as an `ItemTooltipEvent` handler in `client/`, which also enables the per-player
behaviour the javadoc says was wanted.

---

### [RS-139] Fragile vanilla/registry assumptions in perk effects

**Severity:** Low-Medium | **Confidence:** High | **Impact:** Wrong behaviour with modded content | **Effort:** Medium

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java and CombatEventHandler.java (multiple)

The mod is *mostly* good here — `DamageTypeTags`, `Tags.Blocks.ORES`, `BlockTags.LOGS`,
`ItemTags.FISHES` are used correctly (see "Done well"). Remaining string/class heuristics:

- `PerkEffectsHandler#onOutgoingDamage` (~line 605-611): `path(held,"halberd"|"glaive"|"spear"|
  "lance"|"pike")` for Polearm Mastery, `ns(held,"cataclysm")`, `ns(held,"spartanweaponry")`,
  `path(held,"dragon"|"bone")` for Dragon Bone Mastery. `ns()` uses `getNamespace().contains(n)`,
  not `equals` — so `"cataclysm"` also matches `spartancataclysm`, and a mod id containing
  `spartanweaponry` as a substring matches too.
- `CombatEventHandler#isHeavySpartanWeapon` (~line 576-580) and `RUNIC_MIGHT` (~line 525-531):
  `path.contains("runic")` matches this mod's own future items and any `*_runic_*` decoration block.
- `BRUTAL_SWING` → `instanceof AxeItem` (PerkEffectsHandler.java:606) and `TERRAFORMER` →
  `instanceof ShovelItem || HoeItem` (~line 774): modded tools that extend `DiggerItem` directly
  or use a custom class (very common — Tetra, Mekanism paxels, Silent Gear) are invisible. The
  correct check is `ItemTags`/`Tags.Items.TOOLS_AXES` (Forge) or `stack.canPerformAction(ToolActions.AXE_DIG)`.
- `GLADIATOR` → `instanceof ShieldItem` (CombatEventHandler.java:~518): Spartan Shields' own shields
  do extend `ShieldItem`, but many modded shields implement `IShieldItem`/use `ToolActions.SHIELD_BLOCK`
  instead. Use `stack.canPerformAction(ToolActions.SHIELD_BLOCK)`.
- `MEDIEVAL_ARCHITECTURE` → `instanceof BlockItem` (~line 741) is fine.
- `isTrophyTarget` (CombatEventHandler.java:~584-600) is the *good* pattern — namespace list from
  config **plus** a behavioural fallback (`MobCategory.MONSTER && maxHealth >= threshold`).
- `PerkEffectsHandler#onBlockBreak` `DOUBLE_DOWN` passes `condition = true`
  (~line 533), so it duplicates the drops of **any** block, including modded machines and tile
  entities with NBT — `Block.getDrops(...)` on a machine yields the machine with its inventory NBT,
  duplicating stored contents.
- `SILK_TOUCH_MASTERY` (~line 536-539) pops `new ItemStack(state.getBlock())` rather than the
  silk-touch drop, so modded blocks whose item form differs from `Block#asItem` (double slabs,
  multiblock parts, waterlogged variants) drop the wrong item.
- Dimension assumptions: `DEEP_CORE_MINING` uses `player.getY() < 0` and `QUARRY_MASTER`
  `player.getY() < 16` (~line 530-531) — hard-coded to Overworld build limits; in the Nether,
  a modded dimension with `min_y = -256`, or a skyblock, these fire (or never fire) incorrectly.

**Fix**

Replace `instanceof <ToolItem>` with `ToolAction`/tag checks, replace `ns().contains` with
`equals`, gate `DOUBLE_DOWN` on `isOre` or a tag, use `Block.getDrops` with a silk-touch tool for
`SILK_TOUCH_MASTERY`, and derive the Y thresholds from `level.getMinBuildHeight()`.

---

### [RS-140] `LivingEquipmentChangeEvent` handlers force-drop armour, and two independent handlers do it

**Severity:** Low-Medium | **Confidence:** Medium-High | **Impact:** Item loss / fights with equipment mods | **Effort:** Medium

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/InteractionEventHandler.java — `#onChangeEquipment` (~line 78-93); integration/ApotheosisIntegration.java — `#onEquipAffixItem` (~line 175+)

**Problem**

`LivingEquipmentChangeEvent` is a *notification* — it fires after the slot has changed and
is not cancellable. Both handlers respond by mutating the stack: `player.drop(item.copy(), false)`
then `item.setCount(0)`. Two handlers acting on the same event, one at default priority and one at
`HIGHEST`, both capable of zeroing the same stack.

**Why it matters**

Force-dropping is destructive and unrecoverable if it races another mod that also
reacts to the same event (curios/backpack auto-equip, "keep armour on death" mods, armour-swap
hotkeys). `ApotheosisIntegration`'s own comment (line ~168-172) documents a past bug where ordering
between the mutation and the gate computation produced a bogus denial — evidence the pattern is
fragile.

**Failure scenario**

An auto-equip mod re-equips the piece on the next tick; Runic Skills drops it
again; the loop drops a stack of armour on the floor where it despawns.

**Fix**

Instead of dropping, move the stack back into the player's inventory
(`player.getInventory().placeItemBackInInventory`) and only drop as a last resort; and consolidate
the two handlers so the decision is made once.

---


---

## 6. Low Priority Findings

### [RS-141] Static ThreadLocal memo retains a strong Player reference; can return post-invalidation capability

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- common/capability/SkillCapability.java — get (~170-189)

**Problem**

`GET_MEMO` static ThreadLocal<Object[]>; memo[0]=player (hard ref), memo[2]=its capability; key is (identity,tickCount); never cleared on logout or invalidateCaps().
Why: only place a Player lives in a static field; retained ServerPlayer pins inventory + ServerLevel. Also a same-tick get() AFTER invalidateCaps() (PlayerLifecycleHandler:140) returns the detached capability instead of null.

**Fix**

WeakReference for memo[0]; add clearMemo() called from onPlayerLoggedOut and from onPlayerClone after invalidateCaps().

### [RS-142] Type-mismatched NBT resolves to 0, not the documented default

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- common/capability/SkillCapability.java — deserializeNBT (~512,516,536)

**Problem**

One-arg `nbt.contains(key)` matches ANY tag type; getInt then swallows the ClassCastException and returns 0. So a skill stored as string/double yields level 0, not the level 1 the ternary promises (and the comments claim).
Why: level 0 is below the baseline everything else assumes (mapSkills seeds 1, subtractSkill floors at 1, baselineGlobalLevel counts 1/skill) -> getEarnedGlobalLevelForPerkBudget under-reports and the perk budget silently shrinks.

**Fix**

Typed overload `nbt.contains(key, Tag.TAG_INT)` / `Tag.TAG_BYTE`. The Powers block (~562,573,578) already does this correctly.

### [RS-143] Expired power cooldowns/windows never pruned; round-trip through NBT forever

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- common/capability/SkillCapability.java — setPowerCooldown/setPowerWindow (~337,350), serializeNBT (~493-502)

**Problem**

Entries are only removed when availableAt <= 0, never when the absolute game-time has passed. Contrast perkCooldowns, which tickCooldowns() (~137) actively evicts.

**Fix**

Skip entries with value <= currentGameTime in serializeNBT, or prune both maps alongside tickCooldowns().

### [RS-144] Registry names are path-only — cross-namespace entries collide in NBT keys and name caches

**Severity:** Low | **Confidence:** High | **Impact:** Medium | **Effort:** Moderate

**Affected code:**
- registry/perks/Perk.java — getName (~88); registry/RegistryPerks.java — getPerk (~4327)

**Problem**

GetName() returns `key.getPath()` (mirrored in Skill/Passive/Title/Power; the Powers comment at SkillCapability:68 states "path only, mod-id implicit" as a design choice). NBT keys are `"perk." + getName()`, and the lookup cache is `Collectors.toUnmodifiableMap(Perk::getName, Perk::get)`, which THROWS IllegalStateException on duplicate key.
Why: the mod ships Perk#getMod(), Power.requiredModId and `isDisabled(mod + ":" + name)`, so a non-runicskills namespace is anticipated. An addon registering `someaddon:berserker` alongside `runicskills:berserker` hard-crashes on the first perk lookup (player join), and the two would otherwise share one NBT slot.

**Fix**

Long term key NBT and caches on `key.toString()` with a migration rewriting bare paths. Immediately make the collectors collision-tolerant so a clash degrades instead of crashing.

### [RS-145] PlayerEvent.Clone infers death from health instead of isWasDeath(), and force-sets health

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- registry/events/PlayerLifecycleHandler.java — onPlayerClone (~135-139)

**Problem**

`if (!old.isDeadOrDying()) new.setHealth(old.getHealth()); else new.setHealth(old.getMaxHealth());` — event.isWasDeath() is the authoritative signal and is ignored; the non-death branch duplicates vanilla restoreFrom; the death branch reads the OLD player's max health (pre-modifierAttributes value).
Why: overwriting health from a Clone handler is invasive toward keepInventory-style and death-penalty mods.

**Fix**

Use isWasDeath(); ideally drop the health handling and leave it to PerkEffectsHandler#onRespawn which already owns PHOENIX_RISING.

### [RS-146] Player level values never clamped on load; addSkillLevel can overflow

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- common/capability/SkillCapability.java — deserializeNBT (~512), addSkillLevel (~240)

**Problem**

Accepts any int from NBT; `Math.min(cur + addLvl, skillMaxLevel)` overflows negative before min applies when the stored value is near Integer.MAX_VALUE. getGlobalLevel() sums into an int. Config side is protected (@Clamp(min=2,max=1000) on skillMaxLevel) — the save side is not.
Why: Skill#getLockedTexture documents an AIOOBE from exactly this and papers over it with a client-side write-back.

**Fix**

Clamp on load `Math.max(1, Math.min(nbt.getInt(key), cfg.skillMaxLevel))`; saturating add.

### [RS-147] Title written into the vanilla CustomName player NBT field

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Small

**Affected code:**
- registry/RegistryTitles.java — syncTitles (~174)

**Problem**

`serverPlayer.setCustomName(Component.translatable(title.getKey()))` — CustomName is shared, unnamespaced, vanilla-persisted state seen by scoreboard/team plugins, other nametag mods and `/data get entity`. It outlives the mod: uninstalling leaves every player permanently custom-named with an untranslatable key.

**Fix**

Keep the title in the capability (already there as playerTitle) and drive MixPlayerRenderer from a client-side title cache fed by the existing capability packet.

### [RS-148] Transient per-entity state in PowerRuntime.TargetTags / SummonRegistry leaks for non-player entities

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Small

**Affected code:**
- common/powers/PowerRuntime.java — TargetTags (~186-213), SummonRegistry (~266-284)

**Problem**

TargetTags.STORE is tagKey -> (entityUUID -> expiresAt); expiry is evicted only lazily inside has() for the exact entity queried, and clear(entityId) is only called from PowerEventDispatcher#onLogout for the departing PLAYER. Mob UUIDs tagged by a Power and then killed are never removed. Same shape for SummonRegistry.
Note: PowerRuntime's javadoc claims "All services are transient — rebuilt on login, cleared on logout", true only for player-keyed maps; and the javadoc at ~44 says clearPlayer is called from PlayerLifecycleHandler — it is actually called from PowerEventDispatcher#onLogout, which is only registered when Iron's Spellbooks is present.

**Fix**

Sweep both maps on a ServerTickEvent interval as CombatEventHandler#onServerTick already does; fix the two stale javadoc claims.

### [RS-149] Iron's Spellbooks per-player state maps never cleaned up

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- integration/IronsSpellbooksIntegration.java (~422-424)

**Problem**

SpellweaverCount / spellweaverLastCast / arcaneReprieveLastUse are plain HashMap<UUID,…> with no clearPlayer and no logout hook, unlike PerkEffectsHandler and PowerEventDispatcher. Also non-concurrent where the equivalents elsewhere use ConcurrentHashMap.

**Fix**

Add clearPlayer(UUID) called from onPlayerLoggedOut; switch to ConcurrentHashMap.

### [RS-150] Rate limiting runs inside enqueueWork — a flood still allocates and queues main-thread tasks unbounded

**Severity:** Low | **Confidence:** Medium | **Impact:** Medium | **Effort:** Small

**Affected code:**
- network/PacketRateLimiter.java — allow (~22) + every C2S handler

**Problem**

Handlers do `context.enqueueWork(() -> { ... if (!allow(...)) return; ... })`, so the limiter gates the WORK, not the ADMISSION. Each hostile packet is still fully decoded on the netty thread, allocates a message object, and schedules a Runnable onto MinecraftServer's task queue before being discarded. Vanilla applies no count throttle to ServerboundCustomPayloadPacket.
Scenario: patched client loops sendToServer(new TogglePerkSP(...)); tens of thousands/sec decode, allocate and enqueue; main thread drains every no-op task; queue grows on heap. Degradation rather than clean crash, but attacker-controlled and one line to fix.

**Fix**

Move the check ahead of enqueueWork (`ServerPlayer sender = context.getSender(); if (sender == null || !allow(...)) { context.setPacketHandled(true); return; }` — getSender() is safe off the netty thread). Add a per-player violation counter that disconnects after a threshold, reset in the existing clearPlayer path.

### [RS-151] OpenEnderChestSP opens a container without alive/spectator/sleeping checks

**Severity:** Low | **Confidence:** Medium | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- network/packet/common/OpenEnderChestSP.java — handle (~33)

**Problem**

After rate-limit and perk-enabled checks, `player.openMenu(enderChestContainer)` with no state validation. Vanilla reaches ender chests through EnderChestBlock#use, gated by the normal interaction path (spectators never get a use call, dead players cannot interact); this packet bypasses that entirely.
Scenario: spectator sends OpenEnderChestSP (or clicks the inventory button, which the mixin does not gate on game mode) and rearranges their ender chest while noclipping.

**Fix**

`if (!player.isAlive() || player.isSpectator() || player.isSleeping()) return;` before openMenu; mirror the guard in MixInventoryScreen's button visibility.

### [RS-152] S2C packet classes reference net.minecraft.client.* in handler bodies — safe today, but by accident

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Small

**Affected code:**
- network/packet/client/PlayerMessagesCP.java — handle (~39); also ConfigSyncCP, DynamicConfigSyncCP, CommonConfigSyncCP, SkillOverlayCP, TitleOverlayCP, NoticeOverlayCP

**Problem**

NOT a crash today, and the reasoning matters: ServerNetworking.init loads each class via registerMessage, and HotSpot verifies methods at link time — but the split verifier only loads a referenced class when it must compute a subtype relation. Here every assignment is type-identical (Minecraft.player is declared LocalPlayer, stored into a LocalPlayer local; the GUI calls are invokestatic with String args), so no subtype check arises, no client class resolves, and RuntimeDistCleaner's trap is never sprung. The method reference ConfigSyncCP::handle bootstraps only the descriptor.
Why: the safety is a property of the current bytecode shape, not of the design. Adding a branch that merges two client types, a field/cast of a client type, or a LocalPlayer-typed parameter makes the verifier resolve net.minecraft.client.* on the dedicated server -> "Attempted to load class ... for invalid dist DEDICATED_SERVER" at mod init, a total server-boot failure whose stack trace points at ServerNetworking.init, far from the edit that caused it.

**Fix**

Adopt the pattern PowerProcCP already uses — move each client body into a `@OnlyIn(Dist.CLIENT) private static class ClientHandler` and have handle call only ClientHandler.method(primitiveArgs). Extend the existing `:checkSidedImports` lint to flag net.minecraft.client imports under network/packet/ unless in an @OnlyIn nested handler. Also drop the unused LocalPlayer import in SyncSkillCapabilityCP.java:8.

### [RS-153] DynamicConfigSyncCP encodes 16 arrays into one writeUtf bounded at 32767 chars

**Severity:** Low | **Confidence:** Medium | **Impact:** Low | **Effort:** Small

**Affected code:**
- network/packet/client/DynamicConfigSyncCP.java — toBytes (~187)

**Problem**

Default 32767-char limit on the joined blob; FriendlyByteBuf.writeUtf throws EncoderException past it. Real configs are ~100 chars, but the limit is silent and the failure is a login-time exception rather than config validation.
Scenario: long-progression pack raises every passive to ~500 tiers; toBytes throws inside Forge's encoder on every join and DynamicConfigSyncCP never reaches any client — clients silently run on stale config.

**Fix**

Folded into the structured-encoding fix above.

### [RS-154] `CONVERGENCE` and `LIMIT_BREAKER` compare the roll to `1` instead of `0`, so probability `1` never procs

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java — `CraftingEventHandler#onPlayerCraft` (line 63); CombatEventHandler.java:158

**Problem**

`nextInt(n)` returns `0..n-1`, but these two sites test `randomizer == 1` / `random == 1` while the sibling perks (`LOCKSMITH` CraftingEventHandler:113, `LUCKY_DROP` CraftingEventHandler:163) correctly test `== 0`. 

**Why it matters**

The `ValueType.PROBABILITY` tooltip renders the value as "1/N (X%)", so the displayed odds are wrong for `N = 1`. 

**Failure scenario**

A pack sets `convergenceProbability = 1` to make the perk guaranteed; the tooltip shows "1/1 (100%)" but `nextInt(1)` always returns `0`, which never equals `1`, so the perk never fires. (`CONVERGENCE` happens to have a `>= 100` escape hatch on line 63; `LIMIT_BREAKER` does not.) 

**Fix**

Use `== 0` at both sites for consistency with the rest of the codebase.

### [RS-155] `TRACKING` re-applies a `GLOWING` effect on every single hit with no chance gate

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java — `PerkEffectsHandler#onOutgoingDamage` (lines 643–644)

**Problem**

`if (on(RegistryPerks.TRACKING, player)) target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));` — unconditional, allocating a `MobEffectInstance` and (per the `MobEffectInstance.update` semantics described above) triggering `onEffectUpdated` → a `ClientboundUpdateMobEffectPacket` broadcast to every tracking client on each hit. 

**Why it matters**

Combined with `CLEAVE` this fires once per splashed entity as well. 

**Failure scenario**

A `TRACKING` + `CLEAVE` player swinging into a 20-mob crowd emits ~20 effect-update packets per swing to every nearby client. 

**Fix**

Only apply when the effect is absent or nearly expired, as with the tick-handler fix above.

### [RS-156] `SkillLevelUpEvent` cancellation returns without resyncing the client

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/network/packet/common/SkillLevelUpSP.java — `SkillLevelUpSP#handle` (lines 82–84)

**Problem**

Every other rejection path in this handler (`canLevelUp` failure at line 59, `canAfford` failure at line 76) calls `SyncSkillCapabilityCP.send(player)` so the client cannot stay in a misleading state; the `MinecraftForge.EVENT_BUS.post(...)` cancellation path returns bare. 

**Why it matters**

The client optimistically renders the level-up. 

**Failure scenario**

An FTB Quests gate or another mod cancels `SkillLevelUpEvent`; the player's screen shows the skill at the new level and their XP spent until they relog or trigger an unrelated sync. 

**Fix**

Add `SyncSkillCapabilityCP.send(player);` before the `return`.

### [RS-157] `ExperienceMath.sum` overflows `int` at extreme configured skill/player levels

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- src/main/java/com/otectus/runicskills/common/util/ExperienceMath.java — `ExperienceMath#sum` (line 100)

**Problem**

`n * (2 * a0 + (n - 1) * d) / 2` is evaluated entirely in `int`. For the `level > 30` branch (`a0 = 112, d = 9`) the product exceeds `Integer.MAX_VALUE` at roughly `n ≈ 21 850`, i.e. level ≈ 21 880, wrapping to a negative total. `getLevelForExperience` and `spendableXp` then produce nonsense. 

**Why it matters**

Unreachable at the shipped `skillMaxLevel = 32`, but `skillMaxLevel` is a freely-editable config `int` and a player's *vanilla* experience level (fed into `spendableXp`) is unbounded and reachable on long-lived XP-farm servers. 

**Failure scenario**

A player at vanilla level 22 000 opens the skill screen; `spendableXp` returns a negative balance, `canAfford` rejects every level-up, and if any path did charge them `addPlayerXP` would clamp their XP to 0. 

**Fix**

Compute `sum` in `long` and saturate to `Integer.MAX_VALUE`, and clamp `skillMaxLevel` at config load.

### [RS-158] The `entity_reach` passive UUID is duplicated as a string literal in a mixin

**Severity:** Low | **Confidence:** High | **Impact:** Silent feature breakage on refactor | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/mixin/MixTargetFinder.java` — `#findAttackTargetResult` (line 55)

**Problem**

`playerReach.getModifier(UUID.fromString("96a891fe-5919-418d-8205-f50464391509"))` re-derives
the Entity Reach passive's modifier id from a magic string, duplicating
`RegistryPassives.java:56`. It also parses the UUID on every Better Combat attack instead of using a
constant. `RegistryAttributes` already holds three named UUID constants (lines 23-25) — the passive
UUIDs are the only ones left as scattered literals (21 in `RegistryPassives`, 3 in
`ArsNouveauPassiveHelper`, 12 in `ApothicPassiveHelper`, 3 in `IronsSpellsPassiveHelper`).

**Failure scenario**

Someone renumbers the `entity_reach` UUID (e.g. to resolve a collision — note
`ArsNouveauPassiveHelper` and `ApothicPassiveHelper` already both use `…530/531/532`, harmless today
only because they target different attributes). Better Combat reach extension silently stops
applying, with no error.

**Fix**

Hoist the passive UUIDs into a single `PassiveUuids` holder of `static final UUID` constants and
reference `RegistryPassives.ENTITY_REACH.get().attributeUuid` from the mixin.

---

### [RS-159] Dead Power runtime subsystems with unbounded-growth shapes

**Severity:** Low | **Confidence:** High | **Impact:** Dead code; latent memory leak if wired up | **Effort:** S

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/common/powers/PowerRuntime.java` — `DamageTypeMemory` (lines 92-135), `TargetTags` (186-213), `SummonRegistry` (266-284)

**Problem**

Three of the eight documented services have zero call sites (grep-verified;
`DamageTypeMemory` is referenced only by a "hook is in place; behavior is deferred" comment at
`PowerEventDispatcher.java:680`). Both `DamageTypeMemory` and `TargetTags` are keyed by *arbitrary
entity* UUIDs, but the only eviction path is `PowerRuntime.clearPlayer(id)` on
`PlayerLoggedOutEvent` — which matches player UUIDs. `TargetTags.has()` expires lazily only for the
exact key/entity being queried.

**Failure scenario**

If a Phase-2 Power starts tagging mobs, every tagged mob that dies (or unloads)
leaves a permanent `Map` entry; a long-running server accumulates them for its whole uptime.

**Fix**

Delete the unused services until they are needed, or add a periodic sweep keyed off
`level.getGameTime()` and evict on `LivingDeathEvent` / `EntityLeaveLevelEvent`.

---

### [RS-160] 471 hand-written perk declarations over 1143 config fields, with unused rank machinery

**Severity:** Low | **Confidence:** High | **Impact:** Maintenance cost; 149 inert perks | **Effort:** L

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryPerks.java` — whole file (4419 lines); `handler/HandlerCommonConfig.java` (4836 lines, 472 `*RequiredLevel` fields)

**Problem**

Every perk is an 8-line copy of the same shape (declaration, `< 0 ? null :` guard, path
string repeated twice, skill, level, texture constant, `new Value(...)`). The perk name is repeated
as a Java constant, a registry path, a `HandlerResources.X_PERK` constant, and 2–4 config field
names — five places to keep in sync, none of them checked by the compiler. The multi-rank
constructor (`Perk.java:45-54`), `rankLevelRequirements`, `getValue(int rank)`, `cachedRankedValues`
and `canRankUp` exist for a feature with **zero** registered users (grep: no `new int[]` in
`RegistryPerks`, so every perk has `maxRank == 1`). `src/test/resources/perk_no_effect_allowlist.txt`
records 149 perks with no gameplay hook at all — a third of the catalogue.

**Why it matters**

The effect logic is also split three ways: table-driven in `PerkEffectsHandler`
(`REDUCTIONS`, `ATTRS`), hand-written in `CombatEventHandler`/`CraftingEventHandler`/mixins, and
per-integration in `integration/*`. Adding a perk touches 5+ files.

**Failure scenario**

Not a runtime failure — but a rename of any perk registry path silently orphans
every player's saved rank, because ranks are keyed by bare path in NBT
(`SkillCapability#serializeNBT`, `"perk." + perk.getName()`, line ~475). There is no migration map,
so ids are effectively frozen forever.

**Fix**

Move perk definitions to a data-driven table (JSON or a builder DSL keyed by a single id, with
`requiredLevel`/`values` resolved from config by convention) and generate the config fields; keep the
existing registry paths as the stable id. Either implement multi-rank perks or delete the machinery.

---

### [RS-161] 470 of 471 perk `RegistryObject`s are nullable statics guarded only by convention

**Severity:** Low | **Confidence:** High | **Impact:** One missed guard = NPE in a hot path | **Effort:** M

**Affected code:**
- `/home/claude/rs/src/main/java/com/otectus/runicskills/registry/RegistryPerks.java` — the `< 0 ? null :` idiom (e.g. lines 34-42)

**Problem**

Disabling a perk yields a `null` `RegistryObject` rather than an absent-but-present handle,
so all 186 `RegistryPerks.X.get()` call sites must null-check. They currently all do (verified by
script across `src/main`), but the invariant is enforced by nothing — no test, no lint, no
`Optional`. `PerkEffectsHandler` had to introduce `on(RegistryObject, Player)` and
`val(RegistryObject, ...)` helpers (lines 106-115) purely to centralise the check for its own table.

**Failure scenario**

A contributor adds a new effect site for a perk and writes
`RegistryPerks.NEW_PERK.get().isEnabled(player)` in a `LivingHurtEvent` handler. Nothing fails until
a pack sets that perk's required level to `-1`, at which point every hit in the world throws NPE.

**Fix**

Register every perk unconditionally and route "disabled" through `RegistryPerks.isDisabled`
(already checked inside `Perk#isEnabled`, line 219/232). If the null idiom is kept, extend
`PerkEffectCoverageTest` with a rule that every `RegistryPerks.X.get()` outside the `on()/val()`
helpers is preceded by a null guard.

---

### [RS-162] Tooltips are rendered inside the item loop on the detail and titles pages, so later draws paint over them

**Severity:** Low | **Confidence:** Medium | **Impact:** Tooltip clipped/overdrawn by icons and chrome drawn after the hovered element | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#drawPassiveIcon` (~line 535), `#drawPerkIcon` (~line 570), `#drawTitles` (~line 372 vs 377–382)

**Problem**

`#drawOverview` was explicitly fixed for this (see its comment at ~line 246: "Deferred
the hovered-skill tooltip until after all cells are drawn … rendering it inline causes
subsequent cells to overpaint the tooltip") but the same inline pattern remains on the other two
pages: `drawPassiveIcon`/`drawPerkIcon` call `Utils.drawToolTipList` from inside the row loop,
and `drawTitles` draws the row tooltip before `updateTitleScrollbar`/`drawTitleScrollbar`,
`drawTitleModToggle` and `drawBackButton`.

**Why it matters**

If the overpaint the author observed on the overview page is real, it is equally
real on the pages with the *most* elements (up to 20 icons per detail page, drawn after the
hovered one).

**Failure scenario**

Hover a passive in the top-left of the detail grid; the icons drawn later in
the same loop (and the footer chrome) sit on top of the tooltip's lower-right corner.

**Fix**

Apply the overview's fix consistently — return the hovered `Passive`/`Perk`/`Title` from
the loop and render exactly one tooltip after all page chrome is drawn.

---

### [RS-163] `RenderSystem.enableBlend()` is called without a matching `disableBlend()` in five places

**Severity:** Low | **Confidence:** High | **Impact:** GL blend state leaks to whatever renders next | **Effort:** Trivial

Files:
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#drawScreen` (~lines 202, 207, 213), `#drawPassiveIcon` (~line 536), `#drawPerkIcon` (~line 557)
- `src/main/java/com/otectus/runicskills/client/gui/OverlaySkillGui.java` — `#draw` (~lines 71, 92)
- `src/main/java/com/otectus/runicskills/client/gui/OverlayNoticeGui.java` — `#draw` (~line 59)
- `src/main/java/com/otectus/runicskills/client/gui/DrawTabs.java` — `#renderTabVisual` (~line 68)
- `src/main/java/com/otectus/runicskills/mixin/MixInventoryScreen.java` — `#render` (~line 78)

**Problem**

`MixForgeGui#renderAirs` correctly pairs `enableBlend()`/`disableBlend()`; none of the
others do. Blend is left enabled when the method returns.

**Why it matters**

Vanilla and most mods set the state they need, so this is usually benign — but it
makes this code order-dependent on whatever runs next, and the two HUD overlays leak into the
remaining `ForgeGui` overlay layers (which is exactly where an ordering-sensitive third-party
overlay would be affected).

**Failure scenario**

An overlay registered after `runicskills:notice_overlay` that assumes blend is
disabled renders with unexpected transparency while a notice is on screen.

**Fix**

Pair every `enableBlend()` with `disableBlend()` (or wrap in try/finally, as
`DrawTabs#renderTabVisual` already does for the pose stack).

---

### [RS-164] The Runic Skills tab strip is clipped off the top of the screen at the minimum GUI height

**Severity:** Low | **Confidence:** High | **Impact:** Top ~5 px of the tab row is cut off for players at small window sizes / high GUI scale | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/gui/DrawTabs.java` — `#tabY` (~line 61–63)
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `PANEL_HEIGHT = 194` (~line 57)

**Problem**

`tabY = (guiScaledHeight - textureHeight) / 2 - 28`. Minecraft's auto GUI scale
guarantees only `guiScaledHeight >= 240`. With the Runic Skills panel (`194` tall) that gives
`(240 - 194)/2 - 28 = -5`, so the 32-px-tall tabs start 5 px above the viewport. (The vanilla
inventory, at 166 tall, gives `+9` and is fine.) `LegendaryTabRunicSkills#initTabOnScreens`
passes the same 194 to Legendary Tabs, so third-party strips inherit the same clipping.

**Why it matters**

Reproducible for anyone who forces GUI Scale 4 or plays in a small window —
the tab icons are visibly cut.

**Failure scenario**

1280×720 window, GUI Scale 4 → scaled 320×240 → Skills screen tabs clipped.

**Fix**

Clamp — `return Math.max(0, (guiScaledHeight - textureHeight) / 2 - 28);` — or reduce
`PANEL_HEIGHT` / shift the panel down when `guiScaledHeight < PANEL_HEIGHT + 60`.

---

### [RS-165] Three HUD overlays have hard-coded positions and no on/off or reposition config

**Severity:** Low | **Confidence:** High | **Impact:** Guaranteed collision with other centred HUD elements; no accessibility escape hatch | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/gui/OverlaySkillGui.java` — `#draw` (~line 65–66: `width/2`, `height/4`); `OverlayNoticeGui#draw` (~line 54–55: `width/2`, `height/3`); `OverlayTitleGui#render` (~line 50–51, 58–59: `width/2`, `height/4`) Config: `src/main/java/com/otectus/runicskills/handler/HandlerConfigClient.java` — only `showCriticalRollPerkOverlay`, `showLuckyDropPerkOverlay`, two mod-name toggles, two sort enums, one tab priority

**Problem**

All three overlays are hard-centred at fixed fractions of the screen. The client config
has toggles for two *chat/actionbar* messages but none for the three overlays, and no X/Y or
anchor setting. Registering them above `VanillaGuiOverlay.HOTBAR` (a good choice, and the
current Forge API) controls z-order only, not position.

**Why it matters**

The upper-centre band is the busiest part of a modded HUD — Iron's Spellbooks'
cast bar, Apotheosis boss bars, boss health, `displayClientMessage` action-bar text and vanilla
titles all live there. `OverlaySkillGui#draw` in particular paints a 16-layer black vignette
roughly 60 px tall across the centre, which will obscure them. There is also no way for a player
who finds the animated banners distracting to turn them off.

**Failure scenario**

A locked-item warning fires while a boss bar and an action-bar message are on
screen; all three overlap.

**Fix**

Add client config entries per overlay — `enabled`, `anchor` (TOP/CENTER/BOTTOM ×
LEFT/CENTER/RIGHT), `offsetX`, `offsetY`, and a `scale`. The existing `HandlerConfigClient`
`ForgeConfigSpec` already has the right shape for this.

---

### [RS-166] `PowersScreen` scroll offset is unbounded upward, creating a scroll dead-zone

**Severity:** Low | **Confidence:** High | **Impact:** After over-scrolling, the list does not move until the same number of notches are scrolled back | **Effort:** Trivial

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/PowersScreen.java` — `#mouseScrolled` (~line 296–306)

**Problem**

`scroll[colIdx] = Math.max(0, scroll[colIdx] + dir);` has a lower bound but no upper
bound. The display clamps at use time (`Math.min(scrollOffset, pool.size() - rowsVisible)` in
`#addColumnButtons` and `#renderColumn`) but the stored value keeps growing. Scroll past the end
20 times and you must scroll back 20 notches before the list starts moving.

**Fix**

Clamp on write — `scroll[i] = Mth.clamp(scroll[i] + dir, 0, Math.max(0, pool.size() - rowsVisible))`
using the pool for that column.

---

### [RS-167] `mouseScrolled` on the Titles page swallows scroll events even when the list fits

**Severity:** Low | **Confidence:** High | **Impact:** Scroll input is consumed with nothing to scroll | **Effort:** Trivial

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#mouseScrolled` (~line 843–847)

**Problem**

The `PAGE_TITLES` branch returns `true` unconditionally, even when `maxOffset == 0`
(fewer than 12 titles match the search). Vanilla convention is to return `false` when the event
was not used so other listeners can act on it.

**Fix**

`return maxOffset > 0;`.

---

### [RS-168] Number formatting follows the JVM default locale, not the Minecraft language setting

**Severity:** Low | **Confidence:** High | **Impact:** Decimal separators (and, on some JVM locales, digits) disagree with the selected in-game language | **Effort:** Low

Files: `src/main/java/com/otectus/runicskills/client/tooltip/PassiveTooltip.java` — `#tooltip` (~line 19: `new DecimalFormat("0.##")`); `src/main/java/com/otectus/runicskills/registry/perks/Perk.java` — `#getParameter` (~line 180); `src/main/java/com/otectus/runicskills/client/core/Utils.java` — `#periodValue` (~lines 92, 103–104) and `#numberFormat` (~line 68: `String.format("%02d", …)`)

**Problem**

`new DecimalFormat(pattern)` and the one-argument `String.format` both use
`Locale.getDefault()`. Minecraft's language is chosen independently of the OS/JVM locale.

**Why it matters**

A German OS running the client in English shows perk values as `1,5` inside an
English sentence (and vice versa). `String.format("%02d", …)` will emit non-ASCII digits on a JVM
whose default locale specifies an alternative numbering system.

**Failure scenario**

`de_DE` JVM, `en_us` game language → `"Now you have permanent Resistance 1,5"`.

**Fix**

Pass an explicit locale derived from `Minecraft.getInstance().getLanguageManager()
.getSelected()` (or, simplest and most predictable, `Locale.ROOT`) to `DecimalFormat`
(`new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale))`) and to `String.format`.

---

### [RS-169] `Utils.intToRoman` throws `ArrayIndexOutOfBoundsException` outside 0..3999

**Severity:** Low | **Confidence:** High | **Impact:** Crash in a tooltip/render path if a perk ever exposes a rank or boost ≥ 4000 or < 0 | **Effort:** Trivial

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/core/Utils.java` — `#intToRoman` (~line 83–89)

**Problem**

`thousands[number / 1000]` indexes a 4-element array; any `number >= 4000` (or negative)
throws. Callers include `PerkTooltip#tooltip` (~lines 24, 73–74),
`RunicSkillsScreen#drawPerkIcon` (~line 564) and `Perk#getParameter` (~line 187), the last of
which converts a *config-supplied* `BOOST` value:
`Utils.intToRoman(Integer.parseInt(df.format(parameterValue)))`.

**Failure scenario**

A pack author sets a `BOOST`-typed perk value to `5000` in the config; hovering
that perk crashes the tooltip render.

**Fix**

`if (number < 1 || number > 3999) return String.valueOf(number);` at the top.

---

### [RS-170] `Utils.checkMouse` uses inclusive bounds on both edges, so adjacent tabs overlap by one pixel

**Severity:** Low | **Confidence:** High | **Impact:** A 1-px column belongs to two tabs; the earlier one in the loop wins | **Effort:** Trivial

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/core/Utils.java` — `#checkMouse` (~line 63–65)

**Problem**

`x <= mouseX && x + width >= mouseX` makes the hitbox `width + 1` px wide. Tabs are
spaced 27 px apart with a 26-px width, so tab *n*'s box ends at `x+26` and tab *n+1*'s starts at
`x+27` — but the ender-chest button check in `MixInventoryScreen#render` (~line 69) uses the
same helper with a 20×18 box and is likewise 1 px oversized. The `Area#contains` record inside
`RunicSkillsScreen` (~line 1171) correctly uses `>= x && < x + width`; the two hit tests
disagree.

**Fix**

Change to `mouseX < x + width && mouseY < y + height` and reuse a single hit-test helper.

---

### [RS-171] `en_us.json` contains eight duplicate keys, and every locale ships nine stale keys

**Severity:** Low | **Confidence:** High | **Impact:** Silent last-wins parsing; dead weight in every language file | **Effort:** Trivial

**Affected code:**
- `src/main/resources/assets/runicskills/lang/en_us.json` — lines 659–666 duplicated at 2023–2030

**Problem**

`school.runicskills.{fire,ice,lightning,holy,nature,blood,ender,evocation}` appear
twice (values happen to be identical, so the last-wins Gson parse is currently harmless).
Separately, all 16 locales carry nine keys that no longer exist in `en_us`
(`perk.runicskills.{blood_mastery,crimson_bond,curse_ward,ritual_sage}` + `.description`,
`yacl3.config.runicskills:config.category.common.group.skills`). The seven Spanish files
(`es_ar`, `es_cl`, `es_ec`, `es_es`, `es_mx`, `es_uy`, `es_ve`) are byte-identical
(md5 `456b82ab…`), i.e. six copies of the same file are maintained by hand.

**Fix**

Delete the duplicate block at lines 2023–2030 (keeping `school.runicskills.eldritch`,
which is only present there); drop the stale keys; and either generate the `es_*` variants from
`es_es` at build time or ship only `es_es` (Minecraft falls back per-key, and vanilla's own
`es_*` variants exist, so a single `es_es` covers the regional codes far less cleanly — prefer
build-time generation).

---

### [RS-172] The GUI never shows earned global level or the perk-swap cooldown, both of which gate actions

**Severity:** Low | **Confidence:** High | **Impact:** Two of the server's gating rules are invisible until they block the player | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#drawOverview` (~line 234–243), `#drawLevelUpButton` (~line 467–470) Server rules: `network/packet/common/TogglePerkSP.java` (~line 136–142, swap cooldown), `SkillLevelUpSP`/`RegistryPerks#effectivePerkCap`

**Problem**

The overview header renders only `screen.skill.level` (`Lvl: %s / Exp: %s`, vanilla XP
level and spendable points) and per-skill `%s/%s`. The *global* level — which caps skill
level-ups via `playersMaxGlobalLevel` and scales the perk budget via `perksPerGlobalLevel` — is
read at `#drawLevelUpButton` line 467 but only ever *displayed* in the tooltip shown once the cap
is already hit (`tooltip.skill.global_max_level`). `PerkTooltip` does surface
`tooltip.perk.next_slot` ("Next perk slot at earned global level %s"), which references a number
the player cannot see anywhere. The `perkSwapCooldownTicks` gate is not surfaced at all.

**Fix**

Add a global-level line to the overview header (`Global %s / %s`) and a cooldown indicator
(remaining seconds) next to the perk grid when `COOLDOWN_PERK_SWAP > 0`.

---

### [RS-173] `MixPlayerRenderer` allocates and re-translates the title component for every player, every frame

**Severity:** Low | **Confidence:** High | **Impact:** Per-entity per-frame allocation in the entity render path; fragile equality check | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/mixin/MixPlayerRenderer.java` — `#render` (~line 33–40)

**Problem**

For every player whose name tag renders, every frame:
```java
MutableComponent name = Component.literal("<").append(entity.getCustomName().copy()
        .withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD)).append(Component.literal(">"));
if (!entity.getCustomName().equals(Component.translatable(RegistryTitles.TITLELESS.get().getKey()))) {
```
The component is built *before* the "is this the titleless placeholder" check, and the check
itself constructs a fresh `TranslatableContents` component every frame purely to compare it. On
a busy server this is one `Component.literal` + two `copy()` + one `Component.translatable` per
visible player per frame.

Also: the `<`/`>` decoration is hard-coded (see the hard-coded-strings finding) even though
`overlay.title.format = "<%s>"` exists in lang.

**Fix**

Hoist the titleless comparison to a cached static component (or compare
`ComponentUtils.getTranslationKey`-style on the contents), do the check first, and cache the
decorated component per player, invalidated on `getCustomName()` identity change.

---

### [RS-174] `-1` as "disable this perk" is undocumented and contradicted by the field's own UI range

**Severity:** Low | **Confidence:** High | **Impact:** Pack authors can't discover the feature; the UI won't let them use it | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/registry/RegistryPerks.java` (471 `…RequiredLevel < 0 ? null : …` sites) vs `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java` (~line 2268–2273 and 470 similar)

**Problem**

471 perks are skipped entirely when their `*RequiredLevel` is negative, but only 111 of the `*RequiredLevel` fields declare `@IntField(min = -1, …)`; the rest declare `@IntField(min = 1)`, so the UI clamps the sentinel away. Every one of the 471 shares the same four-word comment: `"Required level to unlock perk"` — the sentinel is documented nowhere in the config, the README, or `docs/`.

**Why it matters**

There are now three overlapping ways to turn a perk off (`*RequiredLevel = -1`, `disabledPerks`, `hideDisabledPerks`) with different semantics — `-1` removes it from the registry (see the registry-sync finding), `disabledPerks` leaves it registered but suppressed. Only the second is documented.

**Fix**

Pick one mechanism. `disabledPerks` is the safer one (no registry impact, already synced, already has `DisabledContentMatcher` + tests). Deprecate the `-1` sentinel, register unconditionally, and update the shared comment to point at `disabledPerks`.

---

### [RS-175] Dead config code: `ItemListGroup`, `HandlerSkill.defaultLockItemList`

**Severity:** Low | **Confidence:** High | **Impact:** Noise; `checkSidedImports` maintains an allowlist entry for an unused class | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/ItemListGroup.java` (whole file), `src/main/java/com/otectus/runicskills/handler/HandlerSkill.java` (~line 139–305)

**Problem**

`ItemListGroup` implements YACL's `ListGroup.ValueFactory<Item>`/`ControllerFactory<Item>` but no `@ListGroup` annotation references it (only `StringListGroup` and `LockItemListGroup` are used). It is nonetheless explicitly allowlisted in `build.gradle`'s `checkSidedImports` YACL exemption list (~line 396), so the dead file carries ongoing build-config weight. `HandlerSkill.defaultLockItemList` is a 130-entry `List<String>` in the legacy `item#skill:level` format, referenced only by the dead `HandlerConfigCommon`.

**Fix**

Delete both, and drop the corresponding `checkSidedImports` allowlist entry. Note that `ItemListGroup.provideNewValue()` returning `null` would have been a latent NPE in YACL had anything used it.

---

### [RS-176] `ConfigHolder.save()` does not fsync; `.tmp`/`.invalid` siblings are not namespaced

**Severity:** Low | **Confidence:** High | **Impact:** Rare data loss on power failure; stray files in the config directory | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java#save` (~line 156–188), `#backupInvalid` (~line 147–154)

**Problem**

The write-temp-then-`ATOMIC_MOVE` pattern is correct and covered by `ConfigHolderAtomicSaveTest`, with a sensible `AtomicMoveNotSupportedException` fallback. It does not `FileChannel.force(true)` before the move, so on a hard power loss the rename can land ahead of the data on some filesystems. Minor. Separately, `runicskills.common.json5.tmp` and `runicskills.common.json5.invalid` sit next to the live files with no cleanup and no README mention, so an operator seeing them has no idea whether they matter.

**Fix**

`force(true)` the channel before closing the writer if durability matters here (arguably it doesn't for config). Document the `.invalid` convention in the README's Configuration section, and delete stale `.tmp` files on load.

---

### [RS-177] `Title.tooltip()` reads a `ModConfig.Type.CLIENT` value from a common-package class

**Severity:** Low | **Confidence:** Medium | **Impact:** Potential `IllegalStateException` if ever reached server-side | **Effort:** S

**Affected code:**
- `src/main/java/com/otectus/runicskills/registry/title/Title.java` (~line 76)

**Problem**

`Title.tooltip()` calls `HandlerConfigClient.showTitleModName.get()`. `HandlerConfigClient.SPEC` is registered as `ModConfig.Type.CLIENT` (`Configuration.java:21`), which Forge does not load on a dedicated server; `ForgeConfigSpec.ConfigValue.get()` on an unloaded spec throws `IllegalStateException`. `Title` lives in `registry/title/`, i.e. common code, outside every `checkSidedImports` allowlist — the lint only catches `net.minecraft.client.*` and YACL imports, not sided *config* reads.

**Why it matters**

Today every caller of `tooltip()` appears to be client-side, so this is latent rather than live. But nothing enforces that, and a future KubeJS or integration caller would trip it. Confidence is Medium on the impact (reachability), High on the sidedness violation itself.

**Fix**

Move `tooltip()` to a client-side renderer (the mod already has `client/tooltip/PerkTooltip` and `PassiveTooltip` doing exactly this for perks and passives — `Title` is the odd one out). Extend `checkSidedImports` to flag `HandlerConfigClient` references outside the client allowlist.

---

### [RS-178] `RunicSkillsMixinPlugin` gates only 3 of 14 mixins and has no version/config awareness

**Severity:** Low | **Confidence:** High | **Impact:** Missed opportunity, not a live defect | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/mixin/RunicSkillsMixinPlugin.java — `#shouldApplyMixin` (~line 42-52)

**Problem**

The plugin is correct for what it does — `LoadingModList.get().getModFileById(modId)` is
the right pre-construct API, matching on the mixin's simple name is safe, and returning `true` by
default is the right fallback. Gaps: (1) it does not gate `MixEnchantmentMenu` off when Apotheosis
is present (see that finding), (2) it does not offer a pack-level kill switch for the intrusive
vanilla mixins (`MixLivingEntity`, `MixShulkerBullet`, `MixForgeGui`, `MixItemStack`), which is the
standard escape hatch when a mixin conflicts in a large pack, (3) `getRefMapperConfig()` returns
`null` — fine, but combined with `MixTrueInvisibilityEffect`'s `targets = "…"` string it means a
typo in that FQCN fails silently rather than at build time.

**Failure scenario**

A pack hits a `MixLivingEntity` conflict with another effect-system mod and has no
way to disable just that mixin short of removing Runic Skills.

**Fix**

Read a small `runicskills-mixins.json` from the config dir in `onLoad` and honour per-mixin
disable flags; add the Apotheosis gate.

---

### [RS-179] The mod defines two block tags it never consumes, and consumes none of its own item tags

**Severity:** Low | **Confidence:** High | **Impact:** Missing the primary pack-author extension point | **Effort:** Medium

**Affected code:**
- src/main/resources/data/runicskills/tags/blocks/{obsidian,dirt}.json; registry/RegistryTags.java (~line 11-26)

**Problem**

`RegistryTags.Blocks.OBSIDIAN` and `.DIRT` are declared and two datapack tag files exist,
but neither `TagKey` is referenced anywhere in the Java source (grep for `RegistryTags` returns only
the declaration). `RegistryTags.Items.tag(...)` is a factory with zero call sites and there is no
`data/runicskills/tags/items/` directory at all.

**Why it matters**

Tags are the standard, datapack-overridable way for a progression mod to say
"these are the weapons / these are the ores / these are the magic implements". Runic Skills instead
uses ~120 hard-coded substring keywords across `LockGen`, `IronsSpellbooksLockProvider`,
`OvergearedLockRules`, `StarcatcherLockRules`, `IceAndFireIntegration`, `SpartanIntegration` and
`CombatEventHandler`, none of which a pack author can override without editing Java.

What *should* be a tag:
- `runicskills:locks/magic_implement`, `/melee_weapon`, `/ranged_weapon`, `/armor`, `/tool` —
  replacing `LockGen.classifyGear`'s keyword tables, with the keyword scan kept only as a
  bootstrap that generates a suggested tag file.
- `runicskills:polearms`, `runicskills:heavy_weapons`, `runicskills:runic_weapons` — replacing
  `CombatEventHandler#isHeavySpartanWeapon` and the `path(held, …)` chains.
- `runicskills:culinary_food` — replacing `CulinaryIntegration`'s namespace list
  (`CulinaryNamespaces`).
- `runicskills:boss_entities` (entity-type tag) — replacing
  `cfg.trophyHunterBossNamespaces` + max-health heuristic.
- `runicskills:vein_mineable` — replacing `Tags.Blocks.ORES` for the vein-miner cascade.

**Fix**

Introduce the tag keys, populate them from the existing keyword output as the shipped default,
and check tags first / keywords second.

---

### [RS-180] FTB Quests: full quest-tree walk on every progression mutation

**Severity:** Low | **Confidence:** High | **Impact:** Server tick cost on large packs | **Effort:** Low

**Affected code:**
- src/main/java/com/otectus/runicskills/integration/quests/FTBQuestsIntegration.java — `#forEachTask` (~line 200-208), callers at ~line 102-157

**Problem**

Every skill level, passive level, perk rank change and title event walks
`ServerQuestFile.INSTANCE.forAllQuests` → every task in the pack, doing an `instanceof` +
`equalsIgnoreCase` per task. `onSkillLevelChanged` additionally evaluates *every*
`RunicGlobalLevelTask` unconditionally. The code's own comment acknowledges this
("Walking the full tree on each mutation is simple and correct").

**Failure scenario**

A 5,000-task FTB Quests pack with 20 players actively gaining XP — each level-up
is a 5,000-element scan plus a `TeamData.get(player)` lookup, on the server thread.

**Fix**

Build an identity-keyed `Map<TaskType, List<AbstractRunicTask>>` once on
`ServerQuestFile` load / `QuestFile` reload and invalidate on reload.

The rest of the FTB Quests integration is sound: reflective load, `RunicQuestBridge` facade with a
NOOP listener, correct `mods.toml` entry with a bounded `versionRange = "[2001.4,2002.0)"`,
compileOnly artifacts, and a `sticky` flag with correct NBT/net/config plumbing.

---

### [RS-181] `RegistryPerks`/`RegistryPassives` conditional `null` RegistryObjects require a null check at ~1,000 call sites

**Severity:** Low | **Confidence:** High | **Impact:** Latent NPE surface | **Effort:** High

**Affected code:**
- src/main/java/com/otectus/runicskills/registry/RegistryPerks.java (~line 34 onward)

**Problem**

The "absent" representation is a `null` `RegistryObject<Perk>` field, so every consumer
must write `RegistryPerks.X != null && RegistryPerks.X.get().isEnabled(player)`. The codebase does
this consistently (checked across `PerkEffectsHandler`, `CombatEventHandler`, all mixins) — but it
is one forgotten check away from an NPE in a hot event handler, and it is why the mixins are
littered with `RegistryPerks.FOO != null` guards.

**Fix**

Register a disabled sentinel `Perk` whose `isEnabled` always returns `false`, so the field is
never null. Mechanical but eliminates a whole class of future bug.

---

### [RS-182] Unused and over-broad Maven repositories

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Low

**Affected code:**
- `build.gradle` — lines 120-183

**Problem**

- **`maven.blamejared.com` (lines 174-177)** is declared with the comment "Botania (Vazkii's
  Blame-Jared maven — ships the `:api` classifier we compile against)". There is no Botania
  dependency in `build.gradle` and no Botania integration class — 1.5.0 removed them
  (`README.md`: "all Botania, Blood Magic, and Enigmatic Legacy perks … have been removed"). The
  repo is dead weight and its comment is a lie.
- **`https://api.modrinth.com/maven` (line 131-133)** is declared with no `content { includeGroup }`
  filter, unlike `cursemaven` (152-156), `architectury` (159-165) and `saps.dev` (166-172) which
  are all correctly filtered. Every unresolved dependency in the build now makes a network round
  trip to Modrinth before falling through, slowing resolution and widening the dependency-confusion
  surface.
- **`maven.shedaniel.me` (121-124)**, **`maven.kosmx.dev` (125-129)**, and the BetterCombat /
  Cloth Config / PlayerAnimator dependencies (210-216) exist solely to satisfy BetterCombat, which
  is declared as `implementation` — meaning it is a *runtime* dependency of the published mod, not a
  compat layer. There is no `BetterCombatIntegration` class. Three repositories and three
  `implementation` dependencies appear to be vestigial.
- **`flatDir { dirs 'libs' }` (179-182)** is labelled "L2 Tabs" but serves both local jars. Gradle
  documents `flatDir` as discouraged (no metadata, no transitive resolution, no reproducible
  coordinates).

**Why it matters**

Unfiltered and unused repositories are the standard vector for dependency-confusion
attacks and are the first thing to check when a build starts resolving something unexpected.

**Failure scenario**

A typo'd coordinate resolves against an attacker-published artifact on the
unfiltered Modrinth maven instead of failing fast.

**Fix**

Delete the blamejared repo; add `content { includeGroup "maven.modrinth" }` to the Modrinth
repo; audit whether Cloth Config / PlayerAnimator / BetterCombat are still needed and drop the two
repos with them if not; keep `flatDir` only for as long as the stub-jar situation persists.

---

### [RS-183] Twenty-one unused imports, plus two fixed-size `Arrays.asList()` config defaults

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Low

**Affected code:**
- 21 files across `src/main/java` (full list below); `HandlerCommonConfig.java:71,76,81`

**Problem**

A scan for imported simple names with no occurrence in the class body finds 21 unused
imports, notably `RegistryPerks` in `SkillLevelCommand.java` and `SkillLevelUpSP.java`,
`java.util.Objects` in five files, `net.minecraft.client.player.LocalPlayer` in
`SyncSkillCapabilityCP.java` (a *client* type imported into a `network/packet/client` class — that
directory is on the `checkSidedImports` allowlist, so the lint does not catch it, but the import is
dead), and `net.minecraftforge.fml.ModList` + `java.util.List` + `java.util.Objects` in both
`CombatEventHandler.java` and `InteractionEventHandler.java`.

Separately, `disabledPerks`, `disabledPassives`, and `disabledPowers` default to `Arrays.asList()`
(lines 71, 76, 81). `Arrays.asList()` returns a **fixed-size** list — `add()` throws
`UnsupportedOperationException`. This is the identical bug class that `HandlerLockItemsConfig.java`
already fixed with a documented comment ("the default must be MUTABLE. `/registeritem` calls
`.add()`… an immutable `List.of(...)` threw `UnsupportedOperationException`"). Today nothing calls
`.add()` on these three — YACL's list controller replaces the whole list — but the trap is armed for
the first person who adds a `/runicskills disable <perk>` command.

**Why it matters**

Individually trivial; collectively they are the noise that makes a real
sided-import or unused-dependency problem invisible.

**Failure scenario**

A future `/runicskills disable <perk>` command calls
`cfg.disabledPerks.add(name)` and throws `UnsupportedOperationException` on a live server.

**Fix**

Run the IDE's optimise-imports across the tree (one mechanical commit). Change the three
defaults to `new ArrayList<>()`. Consider adding `options.compilerArgs << '-Xlint:all'` to the
`JavaCompile` configuration at `build.gradle:328-330` — it will not catch unused imports (javac
doesn't warn on those) but will surface the deprecation and unchecked warnings the build currently
prints and ignores.

Full list:
`client/capability/ClientCapabilityAccess.java` (Objects) ·
`client/event/RegistryClientEvents.java` (Objects) ·
`common/command/RegisterItem.java` (Objects) ·
`common/command/SkillLevelCommand.java` (RegistryPerks) ·
`integration/ApotheosisIntegration.java` (Attribute, RegistryObject) ·
`integration/IceAndFireIntegration.java` (SkillCapability, RegistrySkills) ·
`integration/IronsSpellbooksIntegration.java` (RunicSkills) ·
`mixin/MixTargetFinder.java` (Objects) ·
`network/packet/client/SyncSkillCapabilityCP.java` (LocalPlayer) ·
`network/packet/common/SkillLevelUpSP.java` (RegistryPerks) ·
`registry/RegistryPowers.java` (Set) ·
`registry/events/CombatEventHandler.java` (ModList, List, Objects) ·
`registry/events/CraftingEventHandler.java` (SkillCapability) ·
`registry/events/InteractionEventHandler.java` (ModList, List, Objects) ·
`registry/events/TickEventHandler.java` (SkillCapability)

---

### [RS-184] `org.gradle.daemon=false` makes every local build pay full JVM + ForgeGradle startup

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Low

**Affected code:**
- `gradle.properties:2`

**Problem**

The daemon is globally disabled. For a ForgeGradle project this is unusually expensive —
every `./gradlew compileJava` re-launches the JVM, re-reads the Forge userdev metadata, and
re-evaluates a 522-line build script with three custom source-scanning tasks (`checkSidedImports`
walks the entire `src/main/java` tree on every `check`).

**Why it matters**

`CLAUDE.md` advertises `./gradlew compileJava` as the "faster iteration" loop; with
the daemon off it is not fast. This is friction that compounds across every contributor, every day.

**Fix**

Delete the line (Gradle's default is daemon-on) and pass `--no-daemon` only in CI, which
`build.yml:34` already does explicitly. If the daemon was disabled to work around a memory problem,
raise `org.gradle.jvmargs` instead (currently `-Xmx3G`, which is on the low side for ForgeGradle
decompilation).

---

### [RS-185] Repository hygiene: duplicate documents, an internal AI prompt, and 430 KB of unorganised root markdown

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Low

**Affected code:**
- repo root

**Problem**

`runic-skills-1.3.0.md` and `runic-skills-update.md` are **byte-identical**
(both `16393a1331284d2467d205242a2a104d`, 40,499 bytes) — the same document committed twice under
two names. `runic-skills-config-audit-prompt.md` (13,666 bytes) is an internal audit *prompt*, not
documentation. Thirteen markdown files totalling ~430 KB sit at the repo root
(`MAGIC-RUNIC-SKILLS.md` 52 KB, `RUNIC_SKILLS_POWERS.md` 68 KB, `CURSEFORGE_DESCRIPTION.md` 29 KB,
`AUDIT.md`, `COMMENT_TRIAGE.md`, `FOLLOW_UPS.md`, `VERIFICATION.md`, …) alongside a `docs/`
directory that holds only five.

`docs/PERK_ICON_AUDIT.md` is 102 KB — a generated per-icon table checked into git as prose.

**Why it matters**

A new contributor opening the repo sees thirteen top-level documents with no
obvious reading order, two of which are the same file, one of which is a prompt. It is not obvious
that `README.md` is the entry point or that `AUDIT.md`/`VERIFICATION.md`/`FOLLOW_UPS.md` are
historical artefacts of a specific past audit rather than current state.

**Fix**

Delete `runic-skills-update.md` (or `runic-skills-1.3.0.md`) and
`runic-skills-config-audit-prompt.md`. Move `AUDIT.md`, `VERIFICATION.md`, `FOLLOW_UPS.md`,
`COMMENT_TRIAGE.md`, `MAGIC-RUNIC-SKILLS.md`, `RUNIC_SKILLS_POWERS.md`, `runic-skills-1.3.0.md`
under `docs/` (with an `docs/history/` subfolder for the point-in-time ones) and add a short index
to `docs/README.md`. Regenerate `docs/PERK_ICON_AUDIT.md` from `tools/icongen/specs.tsv` rather than
maintaining it, or drop it.

---

### [RS-186] `AUDIT.md`, `VERIFICATION.md`, `FOLLOW_UPS.md`, and `CLAUDE.md` are stale against 1.6.1

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Low

**Affected code:**
- `FOLLOW_UPS.md:24,45`; `VERIFICATION.md:1`; `CLAUDE.md` "Project Structure", "Key Dependencies", "Mixin Targets"

**Problem**

None of these documents is dated or version-scoped in a way that survives contact with
later releases, and several statements are now false:

- `FOLLOW_UPS.md:24` — "`ServerNetworking.PROTOCOL_VERSION` ("5") is unchanged". It is now `"8"`
  (`ServerNetworking.java:33`).
- `FOLLOW_UPS.md:45` — "Botania 'Band of Aura: Passive Channel' is unimplemented
  (`BotaniaIntegration` TODO)". `BotaniaIntegration` no longer exists (removed in 1.5.0).
- `FOLLOW_UPS.md:14-19` — item 1 ("`/registeritem` can throw on the default immutable lock list")
  has been fixed; `HandlerLockItemsConfig.java:29-31` now carries the mutable default and a comment
  explaining it. The follow-up is not marked resolved.
- `VERIFICATION.md:1` — titled "Runic Skills 1.3.7 — Verification" and describes
  `ConfigHolderTest` as "10 tests"; there are now 20 test classes.
- `CLAUDE.md` "Project Structure" — claims `data/runicskills/` contains "recipes, loot tables,
  tags". It contains only two block tags; there are no recipes or loot tables anywhere.
- `CLAUDE.md` "Key Dependencies" / "Conventions" — claims MixinExtras is used (it is not; see that
  finding).
- `CLAUDE.md` "Mixin Targets" — lists 9 common mixins; `runicskills.mixins.json` declares 10
  (`MixTrueInvisibilityEffect` is omitted from the doc).

**Why it matters**

`CLAUDE.md` is the file an AI assistant or new contributor reads first and treats as
authoritative. Every false statement in it becomes a false premise in downstream work.

**Fix**

Add a `> Status as of <version>` banner to the three point-in-time documents and move them
under `docs/history/`. Correct the four `CLAUDE.md` statements. Mark `FOLLOW_UPS.md` items 1 and 5
as resolved and update the protocol version.

---

### [RS-187] `README.md`'s installation and protocol sections cite three wrong versions

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Low

**Affected code:**
- `README.md:89,97,312`

**Problem**

Line 89 tells users to download `runicskills-1.5.4.jar`; the current version is 1.6.1
(`gradle.properties:55`, `VERSION`). Line 97 states `PROTOCOL_VERSION=7` and line 312 states
`PROTOCOL_VERSION=5` — 215 lines apart, in the same document, contradicting each other; the actual
value is `"8"` (`ServerNetworking.java:33`).

**Why it matters**

`checkVersionConsistency` (`build.gradle:489-521`) already enforces agreement
between `VERSION`, `mod_version`, and the CHANGELOG heading — precisely because this drift "has now
happened twice". README was left out of that guard.

**Fix**

Replace the hardcoded jar name with a version-agnostic link, and delete both
`PROTOCOL_VERSION=N` mentions (users cannot act on the number; "clients and servers must run the
same Runic Skills version" conveys the operational fact). If the number must stay, extend
`checkVersionConsistency` to also grep README for `PROTOCOL_VERSION=` and compare against
`ServerNetworking.java` — a ~8-line addition to a task that already exists.

---

### [RS-188] `RegistryPerks`: 4,419 lines and 472 registrations in one class

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** High

**Affected code:**
- `src/main/java/com/otectus/runicskills/registry/RegistryPerks.java` (283 KB)

Honest assessment: **less of a problem than its size suggests, and the existing guardrails are
good.** It is a `DeferredRegister` manifest — 472 near-identical `PERKS.register("id", () -> new
Perk(...))` declarations grouped by skill. There is no control flow, so the file is append-only in
practice and merge conflicts are localised to the skill block being edited. Crucially, three tests
already enforce the invariants that actually matter:
`PerkEffectCoverageTest` (every perk has an effect site or an explicit allowlist entry, and the
allowlist can only shrink), `PerkTextureResolutionTest` (every registered id has a matching icon and
no foreign-namespace texture), and the `checkLockProviders` Gradle task for the integration side.
That is a genuinely well-engineered guard against the failure mode a file this size invites.

The remaining costs are real but modest: IDE responsiveness, a 283 KB file in every diff view, and
the fact that `PerkEffectCoverageTest` and `PerkTextureResolutionTest` both parse it with regexes
(`PERKS\.register\("(\w+)"`, `public static final RegistryObject<Perk>\s+([A-Z0-9_]+)`) — so a
reformat that puts the annotation and the name on different lines silently reduces the parsed set.
Both tests defend against this with `assertTrue(ids.size() > 400, "…parse looks wrong…")`, which is
the right instinct.

Safe incremental remediation: **do not split it.** If size becomes intolerable, extract per-skill
classes (`StrengthPerks`, `MagicPerks`, …) that each hold their own registrations and are referenced
from `RegistryPerks` — mechanical, one skill per commit, with the three tests as the safety net.
Before that, harden the two regex-based tests to fail loudly if the per-skill counts shift
unexpectedly, so a reformat cannot silently shrink their coverage.

---

### [RS-189] Five methods exceed 100 lines, three exceed 170

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Medium

**Affected code:**
- `registry/events/CombatEventHandler.java:340` (232 lines, `onLivingHurtStrengthAttacker`); `registry/events/PowerEventDispatcher.java:267` (225, `onSpellDamage`), `:82` (170, `onSpellOnCast`), `:877` (104, `onPlayerTick`); `network/packet/common/TogglePerkSP.java:46` (120, `handle`); `RunicSkills.java:49` (113, constructor); `client/screen/RunicSkillsScreen.java:922` (108, `handleDetailClick`)

**Problem**

These are single `@SubscribeEvent` methods that dispatch across dozens of perks with long
`if (RegistryPerks.X.get().isEnabled(player)) { … }` chains. `onLivingHurtStrengthAttacker` at 232
lines is the hottest path in the mod — it runs on every melee hit.

**Why it matters**

Three concrete costs rather than aesthetics. (a) **Untestable** — the entire perk
damage pipeline is reachable only with a live `LivingHurtEvent`, which is why the test suite covers
`ForgingQualityMath` and `ApothGateMath` but nothing in combat. (b) **Hot-path cost** — a 232-line
method with dozens of branches is above HotSpot's default inlining threshold and re-evaluates every
perk check on every hit regardless of which perks the player has. (c) **Merge conflicts** — every
new Strength perk edits the same method.

Fix (incremental, per-perk): the pattern that would help most is extracting each perk's damage
contribution into a small pure function — `static float berserkerBonus(float base, int rank, float
pct)` — living beside the existing `common/util/*Math` classes, called from the handler. That is
exactly the pattern `ExperienceMath`, `PerkCapMath`, `ForgingQualityMath`, and `ApothGateMath`
already establish, and it is what makes them testable. Doing this for the five or six highest-value
combat perks would meaningfully raise coverage without touching the dispatch structure.
`RunicSkills.java:49` (the constructor) is a separate, easy win: extract the integration-loading
block (lines 85-128) into `private void loadIntegrations(HandlerCommonConfig cfg)` and the update
check (133-161) into `private void scheduleUpdateCheck()`.

---

### [RS-190] `Component.translatable(...)` on the server for values sent into client-facing text

**Severity:** Low | **Confidence:** Medium | **Impact:** Low | **Effort:** Low

**Affected code:**
- `src/main/java/com/otectus/runicskills/common/command/PowersCommand.java:141`

**Problem**

`printRow` does
`String disp = p == null ? n : Component.translatable(p.getKey()).getString();` — calling
`.getString()` on a `TranslatableComponent` **server-side**. On a dedicated server the client
language table is not loaded, so `getString()` returns the raw key (`power.runicskills.fire_mark`)
rather than a translated name. The surrounding message is then `Component.literal(...)`, so the
already-flattened string is shipped as literal text and the client cannot translate it either.

**Why it matters**

`/powers view` on a dedicated server prints raw translation keys; the same command
in singleplayer prints proper names. It looks like a per-environment bug.

**Fix**

Build the component compositionally —
`Component.literal("  " + label + ": ").append(Component.translatable(p.getKey())).append(Component.literal(" (" + n + ")"))`
— so the client resolves the key. Same pattern applies to `listAll` (line 102), which already does
this correctly, so the two paths are inconsistent within one file.

---

### [RS-191] KubeJS perk/passive registration is dead API

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Small

**Affected code:**
- `registry/perks/Perk.java:58-77` (`Perk.add`), `registry/passive/Passive.java:41-49` (`Passive.add`), `kubejs/Plugin.java:11-17`

**Problem**

`Perk.add(...)` and `Passive.add(...)` are documented "KubeJS support" factories, but they
only *construct* an object — neither touches `RegistryPerks.PERKS` / `RegistryPassives.PASSIVES`,
and no call site exists anywhere in the tree. `Plugin.registerBindings` exposes only `ValueType` and
`Skill` to scripts, so a pack script cannot even reach the factories, and there is no registry-event
window in which a script could register into a `DeferredRegister` anyway.

**Why it matters**

`CLAUDE.md` and the README list KubeJS as a scripting integration; the only working
KubeJS surface is the deprecated level-up event (07 notes it is registered client-only). A pack
author following the class comments produces objects that are never registered and therefore never
appear in `getCachedValues()`, the capability maps, or NBT — silently. This compounds 06's
"`ESkill` is a fixed 10-value enum" finding: even a hypothetically-registered KubeJS skill could not
be referenced by lock items.

**Failure scenario**

Pack author writes a startup script adding a custom perk; nothing errors, nothing
appears, and there is no log line to debug against.

**Fix**

Either delete the two factories and the KubeJS claim from the docs, or add a real
`RunicSkillsRegistryEvent` fired inside the `RegisterEvent` window that scripts can populate.

---

### [RS-192] `betterCombatEntityRange` is dead state that is persisted and re-sent on every mutation

**Severity:** Low | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- `common/capability/SkillCapability.java:64, 505, 584, 612`

**Problem**

The field is declared, serialized, deserialized and copied in `copyFrom`, but is never read
or written by any other class (grep across the tree finds only those four sites). `MixTargetFinder`,
the mixin it is named for, reads the `entity_reach` attribute modifier directly by UUID instead.

**Why it matters**

It is a `putDouble` in every `serializeNBT`, which 01 notes is called on every
capability mutation and shipped whole in `SyncSkillCapabilityCP`. Harmless per-call, but it is a
permanent NBT key with no owner — exactly the kind of entry the "unknown keys silently discarded"
problem (01) makes impossible to retire later.

**Fix**

Delete the field; leave a one-line migration note so the stale NBT key is ignored on read.

---

### [RS-193] Capability eagerly constructed for every Player, defeating the lazy wrapper

**Severity:** Enhancement | **Confidence:** High | **Impact:** Low | **Effort:** Trivial

**Affected code:**
- registry/events/PlayerLifecycleHandler.java — onAttachCapabilitiesPlayer (~102)

**Problem**

Constructor immediately builds four HashMaps totalling ~601 entries. LazySkillCapability.createPlayerAbility() already handles a null provider, so the laziness is designed for but never used. Runs for EVERY Player construction including every RemotePlayer on a client — and remote players' capabilities are never populated (no StartTracking sync) and never read (MixPlayerRenderer uses CustomName).

**Fix**

`event.addCapability(id, new LazySkillCapability(null));`

### [RS-194] No search or filter on the perk/passive detail page (471 perks, 38 passives across 12 skills)

**Severity:** Enhancement | **Confidence:** High | **Impact:** Finding a specific perk means paging through a 5×4 icon grid with only three sort orders | **Effort:** Medium

**Affected code:**
- `src/main/java/com/otectus/runicskills/client/screen/RunicSkillsScreen.java` — `#buildDetailPageState` (~line 670–714), `DETAIL_ITEMS_PER_ROW = 5`, `DETAIL_ROWS_PER_PAGE = 4` (~lines 90–91)

**Problem**

The Titles page has an `EditBox` search (`#getFilteredTitles`, ~line 742–754) but the
detail page has none — only `sortPerk` (name / reverse name / level) and `sortPassive` (name /
reverse name), plus 20-item pages. `en_us.json` defines 471 perk names and 38 passive names;
averaged over ~12 skills that is ~40 items (2–3 pages) per skill, all identified by a 20×20
icon with no visible label. Filtering by "unlocked", "active", "affordable" or "mod" is also
absent, even though `HandlerConfigClient.showPerkModName` already tracks mod attribution.

**Fix**

Reuse the existing `EditBox` pattern on the detail page (matching against
`Component.translatable(perk.getKey()).getString()`, as `getFilteredTitles` does), plus toggle
chips for "unlocked only" / "active only". Both filters must be applied inside
`buildDetailPageState` *before* chunking, so the render walk and the click walk stay index-aligned
(the existing `removeIf(RegistryPerks::isHiddenFromUi)` at line 687 already establishes that
contract).

---

### [RS-195] The Skills keybind opens but cannot close the screen

**Severity:** Enhancement | **Confidence:** High | **Impact:** Minor friction; inconsistent with vanilla `E` | **Effort:** Trivial

**Affected code:**
- `src/main/java/com/otectus/runicskills/RunicSkillsClient.java` — `ClientForgeEvents#checkKeyboard` (~line 36–41)

**Problem**

`KeyMapping#consumeClick()` only returns true when no `Screen` is open (vanilla
`KeyboardHandler#keyPress` calls `KeyMapping.click` exclusively in the no-screen branch), so
pressing `Y` while the Skills screen is up does nothing. Vanilla's `E` closes the inventory.

**Fix**

In `RunicSkillsScreen#keyPressed`, check
`RunicSkillsClient.OPEN_RUNICSKILLS_SCREEN.matches(keyCode, scanCode)` (after the search-box
branch and only when the search box is not focused) and call `onClose()`. Note the ordering
matters: the check must come after `searchTitle.keyPressed(...)` so typing the bound letter into
the title search does not close the screen.

---

---

## 7. Multiplayer & Networking

### Packet inventory

One `SimpleChannel`, `PROTOCOL_VERSION = "8"`, 18 messages. Every registration passes an explicit `NetworkDirection`; every handler uses `enqueueWork` and `setPacketHandled(true)`.

| Packet | Dir | Payload | Decode validation | Handler validation | Verdict |
|---|---|---|---|---|---|
| `ConfigSyncCP` | S→C | item id → (skill ordinal, level) list | count ≤ 8192, skills ≤ 32, ordinal range-checked | player non-null | ✔ |
| `DynamicConfigSyncCP` | S→C | 2 ints + 16 `int[]` as one string + 24 ints | **section count only** | player non-null | ✘ RS-027 |
| `CommonConfigSyncCP` | S→C | ~90 scalars + 5 string lists | all lists via `PacketBounds` | player non-null | ⚠ RS-026, RS-031 |
| `SyncSkillCapabilityCP` | S→C | full `CompoundTag` | vanilla `readNbt` limits | `getLocal() != null` | ⚠ RS-050 |
| `PlayerMessagesCP` | S→C | key + amount | `readUtf()` | key whitelisted vs 3 literals | ✔ |
| `SkillOverlayCP` | S→C | skill id | `readUtf()` | none | ✔ |
| `NoticeOverlayCP` | S→C | key + `String[]` | args ≤ 16 → `DecoderException` | none | ✔ |
| `TitleOverlayCP` | S→C | title name | `readUtf()` | **none — enqueues null** | ✘ RS-022 |
| `PerkGroupsSyncCP` | S→C | groups → perk sets | ≤ 4096 / ≤ 8192 | none | ✔ |
| `PowerOverridesSyncCP` | S→C | overrides → value maps | ≤ 8192 / ≤ 1024 | none | ✔ |
| `PowerProcCP` | S→C | power id + gameTime | `readUtf(Short.MAX_VALUE)` | player non-null | ✔ (`@OnlyIn` inner class — the model to copy) |
| `SkillLevelUpSP` | **C→S** | skill name | `readUtf()` | rate 2t, skill resolved, per-skill cap, XP re-derived — **no global cap** | ✘ RS-008 |
| `PassiveLevelUpSP` | **C→S** | passive name | `readUtf()` | rate 2t, resolved, disabled, at-max, skill gate | ⚠ RS-021 |
| `PassiveLevelDownSP` | **C→S** | passive name | `readUtf()` | rate 2t, resolved, level > 0 | ⚠ RS-021 |
| `TogglePerkSP` | **C→S** | perk name + varint rank | `readUtf()`, `readVarInt` | **nine independent checks** | ✔ exemplary |
| `SetPlayerTitleSP` | **C→S** | title name | `readUtf()` | rate 5t, resolved, **stale persisted flag** | ✘ RS-056 |
| `OpenEnderChestSP` | **C→S** | — | — | rate 5t, perk enabled — no alive/spectator check | ⚠ RS-151 |
| `PowerEquipSP` | **C→S** | power name + boolean | `readUtf()` | rate 2t, resolved, mod loaded, skill gate, tier capacity | ✔ |

### What is structurally safe

Three properties make whole classes of multiplayer bug impossible rather than merely unlikely:

1. **No C→S packet carries a player UUID, entity ID, or target selector.** Every handler operates exclusively on `context.getSender()`. Cross-player action and entity-ID spoofing cannot be expressed in this protocol.
2. **Explicit `NetworkDirection` on every registration.** Forge's `IndexedMessageCodec` terminates the connection on a wrong-side packet, so no client can invoke a client-side handler on the server. This is also what makes the client-import situation in the S2C packet classes benign rather than exploitable.
3. **`SyncSkillCapabilityCP` uses `PacketDistributor.PLAYER`, never a broadcast** — no player's progression data reaches another player.

### What is not

- **`playersMaxGlobalLevel` is enforced only in the GUI** (RS-008). The only server-side check is per-skill.
- **The `administrator` title is monotonic** (RS-056) and `SetPlayerTitleSP` authorises off the persisted flag rather than re-deriving `hasPermissions(2)`.
- **Rate limiting runs *inside* `enqueueWork`** (RS-150), so a flood still decodes, allocates and schedules a main-thread task per packet before being discarded. It gates the work, not the admission.
- **Bulk level-up sends N packets in one tick and the mod's own limiter drops N−1 of them** (RS-021) — a documented feature that silently does not work, and the rejection path returns before the resync so the client is never told.
- **A malformed passive-level config array produces a `NumberFormatException` in the netty decode thread on a login packet** (RS-027) — every player, including the integrated-server host, is disconnected with a generic internal-error message.

### Concurrency

Verified clean: **no game state is mutated off the main thread anywhere in the mod.** No `new Thread`, no executor submitting world mutations, no async I/O touching entities. `CombatEventHandler` uses `ConcurrentHashMap` throughout with a periodic `onServerTick` prune. The one concurrency defect is internal to `PowerRuntime`, where six of eight `clear()` methods mutate a `HashMap` outside the monitor every other accessor on that map holds (RS-131).

---

## 8. Persistence & Save Compatibility

### How data is stored

All persistent player state is in one capability serialized into the vanilla `ForgeCaps` compound of `playerdata/<uuid>.dat`. There is no `SavedData`, no world-level storage, no custom file I/O for player data, and no database. This is the right choice and it means save/load is vanilla-driven and identical across singleplayer and dedicated servers.

### The three save-compatibility hazards

**1. No schema version (RS-005).** There is no `dataVersion` int. The only migrations that exist are tag-type sniffing (a `TAG_BYTE` perk value means a legacy boolean rank) and three hardcoded legacy key names. There is nowhere to hang a future rename, unit change, or semantic change. A version bump that reinterprets `perkRank` would silently corrupt every existing save with no way to detect it.

**2. Registry-driven serialization discards orphans (RS-006).** Both `serializeNBT` and `deserializeNBT` iterate the *registry*, never the NBT. Any key whose registry entry is currently absent is never read and never re-written — the first autosave after the entry disappears erases it permanently. This matters far more than it would in a normal mod because **titles are registered from an editable config file**: an operator removing a title from `runicskills.titles.json5` to test something, restarting, letting players cycle once, and re-adding it has destroyed every player's record of having earned it.

**3. Registry names are path-only (RS-144).** `getName()` returns `ResourceLocation#getPath()`, and NBT keys are `"perk." + getName()`. The perk lookup cache is a `Collectors.toUnmodifiableMap`, which throws `IllegalStateException` on a duplicate key. The mod ships `Perk#getMod()` and `Power.requiredModId`, so a foreign namespace is clearly anticipated — but the moment an addon registers `someaddon:berserker` alongside `runicskills:berserker`, the first perk lookup (on player join) hard-crashes, and absent the crash the two perks would share one NBT slot.

### Data outside the capability

Two kinds of player state are written outside the mod's own storage and therefore survive `/respec`, survive uninstalling the mod, and are invisible to every capability-side cleanup path:

- **Permanent attribute modifiers.** `RegistryAttributes.RegisterAttribute#amplifyAttribute` uses `addPermanentModifier`, which vanilla serializes into the player's `Attributes` NBT. Reconciliation only iterates the *currently registered* passives, so a removed passive's modifier is never revisited (RS-018). `COUNTER_ATTACK` compounds this: `setCounterAttack(true)` is a no-op, so the flag guarding removal is permanently false and the perk grants a never-expiring `ATTACK_DAMAGE` bonus (RS-011).
- **`CustomName`.** The selected title is written into the vanilla `CustomName` entity field (RS-147) — shared, unnamespaced state visible to scoreboard plugins, other nametag mods and `/data get entity`, and left behind as an untranslatable raw key if the mod is removed.

### Lifecycle correctness

| Transition | Handled | Correct? |
|---|---|---|
| Death → respawn | `PlayerEvent.Clone` | ✔ textbook (`reviveCaps` → `copyFrom` → `invalidateCaps`) |
| End portal return | `PlayerEvent.Clone` | ✔ `wasDeath` correctly does **not** gate the copy |
| Dimension change | `EntityJoinLevelEvent` | ✔ one hook covers all three via `ServerLevel#addEntity` |
| Login | `EntityJoinLevelEvent` + `PlayerLoggedInEvent` | ✔ |
| Logout | `PlayerLoggedOutEvent` | ⚠ clears 3 of 5 transient-state owners |
| Server restart | vanilla | ✔ for progression; ✘ for clock-baselined state (RS-041, RS-132) |
| LazyOptional invalidation | — | ✘ never invalidated (RS-007) |
| Config change under live data | — | ✘ not reconciled (RS-042, RS-043, RS-044) |

### Recommended migration mechanism

```java
private static final int CURRENT_DATA_VERSION = 1;

// serializeNBT
nbt.putInt("dataVersion", CURRENT_DATA_VERSION);
nbt.merge(unknownTags);                      // orphan pass-through, written first
// ... registry-driven keys written after, so they win

// deserializeNBT
int v = nbt.contains("dataVersion", Tag.TAG_INT) ? nbt.getInt("dataVersion") : 0;
for (Migration m : MIGRATIONS) if (v < m.version()) m.apply(nbt);
this.unknownTags = collectUnclaimedKeys(nbt);   // bounded, with a stored timestamp
```

Migration 0→1 is exactly the existing byte/int perk sniffing and the three legacy cooldown keys, moved out of the read path. Everything after that becomes cheap.

---

## 9. Performance

### Tick-time risks

| Site | Cost | Multiplier | Finding |
|---|---|---|---|
| `TickEventHandler#onPlayerTick` — `addEffect` re-application | `MobEffectInstance` alloc + `ClientboundUpdateMobEffectPacket` | ×20/s ×players ×2 perks | RS-009 |
| `TickEventHandler#onPlayerTick` — `amplifyAttribute` | remove+add modifier, `setDirty()` → attribute packet, **permanent** (NBT) modifier | ×20/s ×players ×2 perks | RS-010 |
| `PowerEventDispatcher#onPlayerTick` — `PositionBuffer.push` | `synchronized` static map, `Snapshot` record alloc, deque trim — **unconditional**, even with no Power equipped | ×20/s ×players | RS-066 |
| `PowerEventDispatcher` — `gameTime % 40/20/60` scheduling | 24³ `getEntitiesOfClass` AABB scans, **all players on the same tick** | periodic spike | RS-066 |
| `RegistryPerks#isDisabled(Perk)` | `perk.getMod() + ":" + perk.getName()` built *before* the empty-list short-circuit | ~100 Strings per melee hit per entity | RS-057 |
| `PerkEffectsHandler#onArrowSpawn` | subscribed to `EntityJoinLevelEvent` — fires for **every entity spawn world-wide** | ×all spawns | inventory §Event |
| `Skill#getPerks` / `getPassives` | rebuilds the whole registry list twice per iteration | per GUI frame | RS-077 |
| `FTBQuestsIntegration` | full quest-tree walk on **every** progression mutation | ×players ×levelups | RS-180 |

Concretely: two common perks produce roughly **40 clientbound packets per second per player** for state that never changes. At 40 players that is ~1,600 packets/second of pure noise before any gameplay happens.

### Render-time risks

`DrawTabs.render` allocates two full `Screen` objects, a `RecipeBookComponent` and a GameProfile-tagged `ItemStack` **every frame** (RS-025) — at 144 fps that is ~576 non-trivial allocations per second while the inventory is open. `MixPlayerRenderer` allocates and re-translates a title `Component` per visible player per frame (RS-193). `MixInventoryScreen` allocates a `ResourceLocation` per frame (RS-192). The level-up button's pulse animation is frame-rate driven and mutates state from inside `render` (RS-096); `OverlayTitleGui`'s animation divides by zero at both ends (RS-095).

### Networking overhead

`SyncSkillCapabilityCP` sends the **entire** ~600-tag capability (order 15 KB) on every mutation, from ~30 call sites (RS-050). The worst case is first login: `Title#setRequirement` sends one full-state packet *per newly-unlocked title*, and the shipped default unlocks ~77 titles in a single pass — approximately **1 MB per joining player**. A player holding a rank-up button sustains ~150 KB/s.

### Scaling behaviour in a 300-mod pack

The costs that scale badly are the ones multiplied by *entity count* and *registry size* rather than player count: `PerkEffectsHandler#onArrowSpawn` on every entity spawn, `MixPlayer`'s capability lookup inside `Entity#getMaxAirSupply`, `MixItemStack` rewriting every enchantment tooltip for every item in the pack, and 19 `LivingHurtEvent` listeners from this mod alone competing with every other combat mod's listeners on the same hot path. `CLEAVE` is the pathological case: it re-enters `hurt()` from inside a `LivingHurtEvent` handler, so one swing into a 20-mob room dispatches 20 nested `hurt()` calls, each running all 19 of this mod's listeners *plus* every listener the other 299 mods registered — and each splash hit receives the full outgoing perk stack including a `CHAIN_LIGHTNING_STRIKE` roll that can spawn a `LightningBolt` (RS-014).

### Memory lifecycle

Per-player maps are cleaned on logout in `PacketRateLimiter`, `PerkEffectsHandler` and `PowerRuntime` (via `PowerEventDispatcher#onLogout`). The leaks are entity-keyed and clock-baselined state: `PowerRuntime.TargetTags`/`SummonRegistry` accumulate mob UUIDs for the process lifetime (RS-148); `IronsSpellbooksIntegration`'s three per-player maps have no logout hook at all (RS-149); `ApotheosisIntegration.recentInteractors` never prunes because its window comparison is broken (RS-041); and `SkillCapability.GET_MEMO` retains a strong `Player` reference in a static `ThreadLocal` (RS-141).

---

## 10. Forge Architecture

### What is correct 1.20.1 Forge

- `DeferredRegister` + `RegistryObject` for items, effects, sounds, attributes, command arguments, and five custom registries via `makeRegistry`/`RegistryBuilder`.
- `RegisterCapabilitiesEvent` + `CapabilityManager.get(new CapabilityToken<>(){})` + `AttachCapabilitiesEvent<Entity>` + `ICapabilitySerializable<CompoundTag>` — the current, correct capability pattern (Forge 1.20.1 has no data attachments; that is 1.20.4+/NeoForge).
- `AddReloadListenerEvent` for the three datapack reload listeners.
- `RegisterCommandsEvent` for commands; `RegisterKeyMappingsEvent` and `RegisterGuiOverlaysEvent` on the mod bus for client registration.
- `EntityAttributeModificationEvent` on the mod bus for attaching modded attributes to players.
- Correct mod-bus vs Forge-bus separation throughout.
- `context.enqueueWork` in every packet handler, with `setPacketHandled(true)`.

### Older idioms that still work

- `@Mod.EventBusSubscriber` alongside manual `EVENT_BUS.register(instance)` — deliberate, documented, and correct here (the two register disjoint handler sets), but it is a pattern that reads as a bug to reviewers and deserves the comment it has.
- `NetworkRegistry.newSimpleChannel` is the standard 1.20.1 API; nothing newer applies on this version.

### Genuinely incorrect patterns

- **`disableSaving()` without `disableSync()` on five registries whose contents are config- and mod-conditional** (RS-015). This is the one place where a Forge API is being used in a way that its contract does not support.
- **`addPermanentModifier` where `addTransientModifier` is meant** (RS-010, RS-018). Permanent modifiers are serialized into player NBT; a modifier reconciled every tick from live state should never be permanent. `PerkEffectsHandler#onAttributeTick` gets this exactly right and is the in-repo model.
- **Missing logical-side guards** in `TickEventHandler#onPlayerTick`, `PerkEffectsHandler#onCraft`, `#onFinishEating`, `#onItemUseTick` and `CraftingEventHandler#onPlayerCraft` (RS-060, RS-061, RS-062). `TickEvent.PlayerTickEvent`, `ItemCraftedEvent` and `LivingEntityUseItemEvent` all fire on both logical sides; the sibling handlers in the same files check correctly, so these are oversights rather than misunderstanding.
- **Cancelling `LivingHurtEvent` at `LOWEST` for dodge** (RS-065) — by then every other mod has committed side effects (ward consumption, absorption drain, durability), and cancelling `LivingHurtEvent` does not undo the knockback, hurt sound, or the 20-tick invulnerability already set by `LivingEntity.hurt`. `LivingAttackEvent` is the correct event for a dodge.
- **`Level.destroyBlock` for vein mining** (RS-013) — it posts no `BlockEvent.BreakEvent` (bypassing every protection mod) and calls `Block.dropResources` with an **empty tool**, so Fortune and Silk Touch are silently lost on every cascaded block.

### Event inventory summary

145 `@SubscribeEvent` methods across 36 classes. In a fully-loaded pack: **19 `LivingHurtEvent` listeners** and **8 `PlayerTickEvent` listeners** from this mod alone. Two handlers are subscribed to `EntityJoinLevelEvent`, which fires for every entity spawn world-wide. Priorities are used deliberately (`HIGHEST` for lock gates, `LOWEST` for final damage clamps) but the three damage-reduction handlers each apply their own independent 80% clamp, so the documented "no combination grants invulnerability" guarantee holds only *within* one handler — three stacked clamps reach 98% reduction (RS-059).

---

## 11. Mod Compatibility

### Overall posture

Cooperative in design, with a small number of proprietary behaviours concentrated in the mixins. The integration architecture itself is good: `tryLoadIntegration(modid, FQCN)` behind `ModList.isLoaded`, a NOOP facade for FTB Quests, a foreign-type-free holder class for Apothic Attributes, and a `LinkageError` quarantine around L2Tabs are all deliberate, correct isolation patterns.

### Where it will fight other mods

| Behaviour | Conflicts with | Finding |
|---|---|---|
| `MixLivingEntity` rebuilds every player `MobEffectInstance` via the 3-arg constructor | any mod relying on `ambient`/`visible`/`showIcon`/`hiddenEffect`/`curativeItems`; blocks every other `addEffect` mixin | RS-032 |
| `MixLivingEntity` substitutes the affected player for the real effect source | anything doing effect attribution (kill credit, PvP tracking) | RS-033 |
| `MixForgeGui` cancels `renderAir` unconditionally and **assigns** `rightHeight = 10` | every other mod's right-side HUD overlays; other `renderAir` mixins | RS-078 |
| `MixCraftingMenu` empties the result **after** vanilla sent the slot packet | client desync; modded crafting stations bypass the gate entirely | RS-034 |
| `MixItemStack` rewrites every enchantment tooltip for every item | every enchantment-description mod | RS-138 |
| `MixVillager` writes persistent `specialPriceDiff` with a RAM-only undo map | Hero of the Village, villager-trading mods | RS-035 |
| `GenericNamespaceLockProvider` classifies foreign registries by substring | any mod whose item paths contain the mod's keywords — Aquaculture rods → Magic aptitude | RS-049 |
| `CLEAVE` splashes `playerAttack` damage to nearby entities | PvP/claim protection, combat overhauls, ward/absorption mods | RS-014 |
| `veinMine` uses `Level.destroyBlock` | **all** land-claim and protection mods | RS-013 |
| `LivingEquipmentChangeEvent` handlers force-drop armour (two independent handlers) | any mod managing equipment slots | RS-140 |

### Optional-dependency safety

The gating pattern is correct in almost every case. The one latent defect is that `IronsSpellbooksIntegration.isModLoaded()` — the guard itself — is a static method on a class whose other members reference Iron's Spellbooks types, and it is called from `RunicSkills.<init>`, `RegistryPerks`, `RegistryPassives` and `ArsNouveauIntegration` (RS-047). Calling a guard defined on the guarded class defeats the isolation; today it survives because the JVM resolves only what it needs, but it is the same accidental-safety shape as the packet client imports.

`mods.toml` declares **7 optional dependencies for ~40 integrated mods** (RS-107). Because everything is `ModList.isLoaded`-gated this is not a crash risk, but the missing `ordering = "AFTER"` entries mean Forge does not guarantee those mods construct first — and code comments in `LegendaryTabsIntegration` explicitly depend on load ordering that currently works by alphabetical accident.

Optional-mod mixins inherit `"defaultRequire": 1` from `runicskills.mixins.json` (RS-048), so a target mod's version bump that moves the injection point becomes a **startup crash** rather than a degradation. `MixGunItem`, `MixTargetFinder` and `MixTrueInvisibilityEffect` all target third-party classes and all need `require = 0`.

### Fragile vanilla assumptions

`instanceof AxeItem`/`ShovelItem`/`ShieldItem`, namespace `.contains()` checks, and hardcoded damage-type string sets appear throughout the integrations (RS-139). `onLivingHurtStrengthAttacker` gates only on `event.getSource().getEntity() instanceof Player`, with no melee check — so perks documented as "wielding a sword/axe/trident" fire for arrows, thrown tridents, player-lit TNT, thorns reflection and modded spells (RS-064). The mod defines two block tags and consumes neither its own item tags nor any of the ~120 keyword-based classifications that should be tags (RS-179).

---

## 12. Skill System Architecture

### XP and levels

Vanilla experience points are the **single authoritative currency**. `ExperienceMath` converts between vanilla levels and total points, `spendableXp` clamps `experienceProgress` to `[0,1]`, `getLevelForExperience` clamps to 0, and `addPlayerXP` clamps the total to non-negative and recomputes level and progress consistently. `SkillLevelUpMath.canLevelUp` is the shared client/server predicate. This layer is Forge-free, unit-tested, and is the strongest part of the design.

Two mathematical defects survive: `ExperienceMath.sum` evaluates `n * (2*a0 + (n-1)*d) / 2` entirely in `int` and overflows at roughly level 21,880 (RS-157) — unreachable at the shipped `skillMaxLevel = 32`, but `skillMaxLevel` is a freely-editable config `int` and vanilla player level is unbounded on long-lived XP-farm servers. And levels loaded from NBT are never clamped, so `addSkillLevel`'s `Math.min(cur + add, max)` overflows negative before the `min` applies (RS-146).

### Bonuses

Three separate application mechanisms with different lifetimes:

1. **Passives** → `addPermanentModifier` with `ADDITION`, value `configValue / levels * playerLevel`, applied by `RegistryAttributes.modifierAttributes` on join, clone, level change, `/respec` and `/skillsreload`.
2. **Perk attributes** → two declarative tables in `PerkEffectsHandler` (`REDUCTIONS`, `ATTRS`), reconciled once per second, idempotent, using `addTransientModifier`. **This is the correct pattern** and the rest of the mod should converge on it.
3. **Everything else** → ad-hoc effect code scattered across seven event handlers, 14 mixins and ~20 integration classes.

### Persistence and exploit resistance

Covered in §8 and §15. The progression-specific exploits are the four Critical faucets plus `LORE_MASTERY` (multiplies *any* XP orb picked up while a grindstone GUI happens to be open — put a grindstone at a mob-grinder collection point, RS-058) and `DOUBLE_DOWN` (duplicates the drops of *every* block with no player-placed check, making place-and-break net-positive, RS-012).

### Extensibility

The skill system is currently **closed**. Skills are a fixed 10-value set duplicated as an `ESkill` enum, so lock items cannot reference a KubeJS-registered skill (RS-099). Perk magnitudes and level gates are frozen at registration and cannot be changed by `/skillsreload` (RS-019). The KubeJS perk/passive registration API exists but is dead (RS-191). Power tier prerequisites and the Power-Point budget are documented but unimplemented (RS-045). The only genuinely data-driven surfaces are the three datapack reload listeners (`perk_groups`, `power_overrides`, `skill_visuals`) — and two of those do not reach clients on a dedicated server (RS-067, RS-070).

---

## 13. Data-Driven & Modpack Integration Opportunities

### What exists today

| Surface | Mechanism | Reaches client? |
|---|---|---|
| Perk groups | datapack `SimpleJsonResourceReloadListener` + `PerkGroupsSyncCP` | ✔ on login, ✘ on `/reload` |
| Power overrides | datapack listener + `PowerOverridesSyncCP` | ✔ on login, ✘ on `/reload` and `/skillsreload` |
| Skill visuals | datapack listener | ✘ never |
| Titles | JSON5 config file | ✘ (and see RS-015) |
| Item locks | JSON5 config + 435 hardcoded Java defaults | ✔ `ConfigSyncCP` |
| Block classification | two block tags (`dirt`, `obsidian`) | ✔ vanilla |

### The highest-value additions

**1. Tags for the ~120 keyword classifications.** The lock providers and the ore/crop/weapon checks are doing by substring match what `TagKey` does correctly. Concretely useful additions, matching the mod's actual design:

```
runicskills:xp/mining_blocks      runicskills:gear/magic_weapons
runicskills:xp/farming_crops      runicskills:gear/ranged_weapons
runicskills:xp/excluded_blocks    runicskills:excluded_entities
runicskills:gear/melee_weapons    runicskills:no_bonus_drops   (player-placed / anti-farm)
```

This is the single change that most improves modpack integration, because it lets a pack author add a modded ore to Building XP with a two-line datapack file instead of a Java patch or a config-list edit.

**2. Move item locks out of Java and into datapacks.** `HandlerLockItemsConfig` is a 54 KB Java literal holding 435 entries across 13 namespaces. A `data/<namespace>/runicskills/locks/*.json` folder with a reload listener would let each integration ship its own file, would let pack authors override individual entries without editing a monolith, and would remove the `ESkill` enum coupling.

**3. Datapack-defined titles.** Titles are already fully data-shaped (`type/variable/comparator/expected` condition strings) and are the direct cause of the highest-severity registry finding. Moving them from a config file to `data/runicskills/titles/*.json` fixes RS-014 for titles outright, because Minecraft already syncs datapack registries to clients.

**4. Sync the config object rather than the fields.** `CommonConfigSyncCP` (172 hand-written field copies) and `DynamicConfigSyncCP` (44) cover 128 of 1,103 fields and are a four-place edit each time. Serialising the server's `HandlerCommonConfig` to a single JSON string and having the client hold it as a `serverConfig` reference that gameplay code prefers is ~30 lines, is automatically complete, and makes "clear on disconnect" trivial (`serverConfig = null`).

### What should stay in Java

Do **not** make skills themselves datapack-defined. Skills carry no behaviour, there are ten of them, they are referenced by a fixed enum in the lock system and by index in the GUI layout, and the serialization/sync/validation cost of making them dynamic buys nothing a pack author actually wants. The same argument applies to the perk *set*: perks are metadata plus scattered Java behaviour, and a datapack cannot supply the behaviour. Perk *tuning* (magnitude, level gate, groups) is already the right data surface — it just needs to actually apply at reload time.

---

## 14. UI / UX

### What a player can and cannot see

| Information | Visible? |
|---|---|
| Current skill level | ✔ |
| Current XP / XP required | ✔ |
| Max level | ✔ |
| Unlocked perk effects | ✔ (tooltips) |
| Next unlock | partial |
| Active bonuses | partial |
| **Earned global level** (gates the perk budget) | ✘ RS-172 |
| **Perk-swap cooldown** (blocks the action) | ✘ RS-172 |
| **Why a perk toggle was rejected** | ✘ 1 of 8 reasons shown (RS-079) |
| **Whether a title is locked** | ✘ renders identically to unlocked (RS-080) |

Two of the three values that most often *block* a player's action are invisible in the UI, and the server rejects perk toggles for eight distinct reasons while the client surfaces a reason for exactly one. This is the single largest UX gap.

### Structural problems

- **`PowersScreen` is unreachable dead code** — the keybind it documents is never registered (RS-023). An entire 15 KB screen for a 75-power subsystem cannot be opened.
- **The Skills screen is mouse-only**: no keyboard navigation, no narrator support, and the search `EditBox` is never registered as a widget (RS-087).
- **No search or filter across 471 perks and 39 passives** (RS-194).
- **The Skills keybind opens the screen but cannot close it** (RS-195).
- **Three HUD overlays have hard-coded positions with no on/off or reposition config** (RS-165).

### Rendering defects

`DrawTabs` uses a cross-frame click latch and calls `setScreen` from inside the render pass (RS-088). `Skill#getLockedTexture` **mutates the authoritative capability from inside a render method** to paper over an out-of-range level, and NPEs if the capability is unresolved (RS-081). `RenderSystem.enableBlend()` is called without a matching `disableBlend()` in five files (RS-163). The tab strip is clipped off-screen at minimum GUI height (RS-164). Tooltips drawn inside the item loop get overpainted by later draws (RS-162).

### Localization

`en_us.json` has 2,061 keys (2,069 lines — 8 duplicates). **All sixteen other locales have exactly 584 keys — 28.3% coverage**, identical across every locale, each also carrying 9 keys that no longer exist in `en_us`. The seven `es_*` files are byte-identical to each other.

| Namespace | Missing per locale | Present |
|---|---:|---:|
| `perk.runicskills.*` | 836 | 106 |
| `yacl3.config.*` | 408 | 184 |
| `power.runicskills.*` | **150** | **0** |
| `title.runicskills.*` | 58 | 101 |
| `screen.runicskills.*` (Powers) | 4 | 0 |

The entire Powers subsystem and the Powers screen are untranslated in every language. `tooltip.skill.level_up` has a placeholder mismatch in all sixteen (RS-024). No positional placeholders (`%1$s`) are used anywhere, but 52 keys contain two or more sequential `%s`/`%d`, forcing English word order — converting these is a prerequisite for serious translation work. Sentence assembly by `Component#append` in `PassiveTooltip`, `PerkTooltip` and `Title#tooltip` cannot be reordered by a translator at all, and the literal `" "` indent prefixes render on the wrong side under `ar_sa` (RTL). Five of nine commands emit unlocalised `Component.literal` feedback.

### Accessibility

Locked and unlocked titles are distinguished by colour alone with no icon, label or tooltip. Number formatting follows the JVM default locale rather than the Minecraft language setting (RS-168). The level-up button pulses at up to ~6 Hz on high-refresh displays (RS-084) — above the 3 Hz threshold generally recommended for photosensitivity. There is no narrator support and no keyboard path through the primary screen.

---

## 15. Security & Server Hardening

Assume every connected client is hostile and fully modified.

### Confirmed exploitable

| Vector | Effect | Finding |
|---|---|---|
| `LOCKSMITH` container spam | unlimited vanilla XP → unlimited skill levels | **RS-001** |
| `SILK_TOUCH_MASTERY` | duplicate any block in the game | **RS-002** |
| `LUCKY_DROP` timing | multiply any dropped item stack | **RS-003** |
| `DOUBLE_DOWN` place-and-break | net-positive material loop, AFK-automatable | RS-012 |
| `LORE_MASTERY` + grindstone at a mob farm | multiplies every XP orb collected | RS-058 |
| `SkillLevelUpSP` past the global cap | +96 global levels, ~48 extra perk slots | **RS-008** |
| `administrator` title after de-op | staff impersonation in chat and tab list | **RS-056** |
| `veinMine` across a claim boundary | mine 47 protected blocks the claim mod never sees | RS-013 |
| `CLEAVE` splash | damage entities/players that PvP and claim protection would block | RS-014 |
| `OpenEnderChestSP` while spectating/dead | container access outside the vanilla interaction path | RS-151 |
| C→S packet flood | main-thread task queue growth; limiter gates work, not admission | RS-150 |

### Crash and denial-of-service vectors

- A malformed `int[]` in the passive-level config throws `NumberFormatException` in the netty decode thread of a **login** packet — every player disconnected, no diagnostic (RS-027).
- A non-numeric title-condition value throws out of `TitleModel#CheckRequirements` and propagates out of a `PlayerTickEvent` handler — "Ticking player" crash, repeating on every restart (RS-017).
- `nextInt(probability)` with a config value of `0` throws `IllegalArgumentException` from inside `ItemCraftedEvent` (RS-029).
- A `TitleOverlayCP` for a title the client does not have enqueues `null` and NPEs the HUD **every frame** thereafter (RS-022).
- One unparseable item id in `treasureHunterItemList` leaves a `null` in the drop table and NPEs on block break (RS-130).

### Data-destruction vectors

- One malformed character in `runicskills.common.json5` resets all 1,141 settings **and overwrites the operator's file** (**RS-004**).
- A trailing comma in a JSON5 array injects a `null` element that NPEs the lock loader; the same edit in an *object* triggers the full reset above (RS-030).
- Removing a title from config permanently erases every player's record of having earned it (RS-006).

### The hardening priorities, in order

1. Fix the four Critical faucets and add the global-cap server check.
2. Re-derive `administrator` authorisation at request time instead of trusting the persisted flag.
3. Make config parsing per-field, and **never overwrite a file that failed to parse**.
4. Move `PacketRateLimiter.allow` ahead of `enqueueWork`, and add a violation counter that disconnects after a threshold.
5. Replace the `-`-delimited string packing in `DynamicConfigSyncCP` with count-checked varints.
6. Validate every user-supplied config value that reaches `nextInt`, a divisor, or a `ResourceLocation` constructor.

---

## 16. Build & Developer Experience

### Reproducibility

**Not reproducible.** Three of four plugin coordinates are unpinned: `org.spongepowered:mixingradle:0.7-SNAPSHOT`, `net.minecraftforge.gradle` at `[6.0.16,6.2)`, and `org.parchmentmc.librarian.forgegradle` at `1.+`. There is no dependency locking and no version catalogue. The same commit builds different bytecode on different days — and mixingradle in particular controls refmap generation, so a silent SNAPSHOT change can produce a jar whose mixins fail to apply in production while `runClient` still works locally (RS-036).

`gradle.properties` declares `mapping_channel=official` / `mapping_version=1.20.1`, which **directly contradicts** the hardcoded Parchment mappings in `build.gradle` (RS-116), alongside seven other dead properties.

MixinExtras is declared as an annotation processor *and* a jar-in-jar dependency but is never used, and `jarJar.enable()` is never called — so the `-all.jar` the README tells users to download does not exist (RS-037).

### Can a fresh clone…

| Task | Works? | Blocker |
|---|---|---|
| Clone and build | ⚠ probably | unpinned plugins; `libs/*.jar` must be present |
| Run a client | ⚠ | requires the two vendored jars |
| Run a dedicated server | untested | **no CI coverage at all** |
| Run data generation | ✘ | a `runData` config and a `src/generated/resources` srcDir exist, but **there are no data generators** (RS-114) |
| Run tests | ✔ | 20 JUnit 5 classes, all deliberately Forge-free |

### CI

`.github/workflows/build.yml` runs a build. It does **not** boot a dedicated server, does not publish test results, does not validate the Gradle wrapper, and mislabels its one step (RS-115). Given that `SMOKE_TESTS.md` records server-side classloading as the project's worst historical regression class, and given the `Title`/`Perk` → `client.core.Utils` → `Minecraft.getInstance()` static-initialiser chain (RS-089), a dedicated-server boot job is the single highest-value CI addition.

### Repository hygiene

`icons/` (399 PNGs) is a stale, byte-diverged duplicate of the shipped perk textures — a third source of truth (RS-112). `tab_menu_buttons.png` at the repository root is a byte-identical Legendary Tabs asset, and `libs/legendarytabs-*.jar` redistributes a third-party mod jar without its MIT notice, in a repository whose licence claims to bundle no third-party code (RS-113, RS-136). `libs/l2tabs-0.3.3.jar` is a 3 KB **hand-written stub**, so the L2Tabs call site is compiled against a fabricated API and has never been type-checked against the real mod (RS-109). There is 430 KB of unorganised root markdown, two byte-identical 40 KB documents, and an internal AI audit prompt committed alongside the source.

### Documentation

The volume is high and the habit is good, but four documents are stale against 1.6.1: `AUDIT.md`, `VERIFICATION.md`, `FOLLOW_UPS.md` and `CLAUDE.md` cite the wrong protocol version, reference a removed `BotaniaIntegration`, and claim recipes and loot tables that do not exist. The README's command reference is materially wrong for three of eight commands and **destructively wrong for `/respec`** (RS-039), and the documented KubeJS subscription example names a class that does not exist, in both the README and `docs/API_EVENTS.md` (RS-040). `docs/API_EVENTS.md` also states that events fire *after* mutation and roll back on cancel; the code fires them *before* mutation with no rollback (RS-111).

---

## 17. Testing Recommendations

### Current coverage

20 JUnit 5 classes, ~1,700 lines, all deliberately Forge-free — and genuinely well chosen for what they cover: pure math (`ExperienceMath`, `SkillLevelUpMath`, `PerkCapMath`, `PerkBudgetMath`, `ForgingQualityMath`, `ApothGateMath`, `PacketBounds`), config lifecycle (`ConfigHolderTest`, `ConfigHolderAtomicSaveTest`, `ConfigClampTest`, `LegacySnakeCaseConfigTest`, `LockItemSourceTest`), lock rules, and **two source-scanning invariant guards** (`PerkEffectCoverageTest`, `PerkTextureResolutionTest`) which are why the texture audit came back perfectly clean.

Untested: **every command (0 of 9)**, **every packet's wire format (0 of 18)**, `SkillCapability` NBT round-trip, the entire combat/perk-effect dispatch, all client GUI code, and all lang integrity.

### Proposed test matrix

P0 = ships a user-visible bug today or guards a documented past regression. P1 = high value, low cost.

| Test | Type | Priority | Purpose |
|---|---|---|---|
| `ConfigSyncMirrorTest` — assert `CommonConfigSyncCP` fields match `HandlerCommonConfig` by name+type, and that declaration / `toBytes` / constructor-read orders agree | Unit (reflection + source scan) | **P0** | The single highest-value structural test in this codebase; a wire-order slip silently corrupts every following field |
| `DedicatedServerBootGameTest` — CI job booting `runServer` with a bare mods folder; assert `Done (` appears and no `NoClassDefFoundError` / `Mixin apply failed` | Integration (CI) | **P0** | The project's worst historical regression class; `checkSidedImports` is only a source-level proxy |
| `SkillCapabilityNbtRoundTripTest` — populate every skill/perk/passive/power/title, serialize→deserialize, assert equality including the `equippedCrown` empty-string sentinel | GameTest | **P0** | Player progression data; a silent NBT regression loses everyone's save |
| `ConfigMalformedIsolationTest` — write a file with one bad field, assert the other 1,140 survive and the source file is **not** overwritten | Unit | **P0** | Locks down RS-004 |
| `ConfigMutationClampTest` — set an out-of-range value via the `/globallimit` path, `save()`, `load()`, assert the value was rejected rather than silently reverted | Unit | **P0** | Locks down the clamp-bypass finding |
| `ExperienceMathOverflowTest` — assert `getExperienceForLevel(50_000)` is monotonic and non-negative; `requiredPoints` ≥ `minCost` for all levels in `[1, 1_000_000]` | Unit | **P0** | Directly tests the int-overflow → free-level-up path |
| `GlobalCapEnforcementGameTest` — send `SkillLevelUpSP` directly at `globalLevel == playersMaxGlobalLevel`, assert rejection and resync | GameTest | **P0** | RS-008 |
| `XpFaucetBoundednessGameTest` — open the same container 100×, break-and-replace the same block 100×; assert XP and item deltas are bounded | GameTest | **P0** | RS-001, RS-002, RS-012 |
| `LangIntegrityTest` — every `lang/*.json` parses; no locale has a key absent from `en_us`; every literal `Component.translatable` key in Java exists in `en_us` (with explicit knowledge of the two runtime-concatenated prefixes) | Unit (source + resource scan) | **P0** | Catches the 9 stale keys today; prevents "perk ships with no name" permanently |
| `CommandBoundsTest` — assert no `IntegerArgumentType` bound is read from config at registration time | Unit (source scan) | **P0** | Catches the stale-bound bug and prevents recurrence |
| `PacketRoundTripTest` — for each of 18 packets, `toBytes` → decode → assert field equality and that the buffer is fully consumed | GameTest | **P1** | Catches read/write asymmetry |
| `RespecInvariantTest` — after `/respec`, assert skills == 1, perk ranks == 0, passive levels == 0, **and** all three power collections empty, and no mod-owned attribute modifiers remain | GameTest | **P1** | RS-128 |
| `PlayerCloneGameTest` — death, End return, and dimension change each preserve the full capability | GameTest | **P1** | Guards the one thing this mod already does right |
| `TwoPlayerSimultaneousXpGameTest` — two players gain XP and level up in the same tick; assert no cross-contamination | GameTest | **P1** | Shared-state regression guard |
| `ItemLockEnforcementGameTest` — locked item unusable below level, usable above, `/skillsreload` applies without relog | GameTest | **P1** | The mod's most user-visible feature, currently hand-verified only |
| `PerkBudgetEnforcementGameTest` — with `maxActivePerks=3` and `perksPerGlobalLevel=0.5`, assert the effective cap, a rejected 4th activation, and a client resync | GameTest | **P1** | Arithmetic is tested; enforcement is not |
| `DisabledPerkBudgetTest` — disable a perk the player holds; assert it stops consuming budget and remains releasable | Unit/GameTest | **P1** | RS-044 |
| `PublicEventContractGameTest` — assert firing order relative to mutation and that cancelling leaves state untouched and suppresses the sync | GameTest | **P1** | Turns the documented stability commitment into an executable contract |
| `ClampAnnotationConsistencyTest` — every `@Clamp` matches the `@IntField`/`@FloatField` on the same field | Unit (reflection) | **P1** | Enforces by machine what `Clamp`'s javadoc asks for by hand |
| `ResourceIntegrityTest` — extend `PerkTextureResolutionTest` to sounds, model parents and orphan detection | Unit (resource scan) | **P1** | |
| `IntegrationAbsenceBootTest` — CI matrix leg booting the server with each optional integration absent | Integration (CI) | **P2** | |
| `MalformedPacketTest` — decode hostile buffers (negative counts, oversized strings, unknown ids) against every C→S packet | Unit | **P2** | |

---

## 18. Enhancement Opportunities

Every item below derives from something found in the code, not from a general feature wishlist.

**Gameplay**
- Implement the Power tier prerequisites and Power-Point budget that `RUNIC_SKILLS_POWERS.md` documents and the code does not enforce (RS-045).
- Make Power internal cooldowns actually persist — the NBT round-trip exists but nothing writes it, so relogging resets every cooldown (RS-046).
- Give `COUNTER_ATTACK` a real retaliation window; it currently grants a permanent bonus (RS-011).

**Configuration**
- Emit `@SerialEntry` comments on save, so the on-disk file is self-documenting (all 1,141 fields already carry a comment; none reaches disk).
- Have `applyClamps` fall back to the existing `@IntField`/`@FloatField` ranges, closing the 1,033-field enforcement gap in one change.
- Load config holders from `<world>/serverconfig/` on `ServerAboutToStartEvent`, making config per-world and eliminating the singleplayer leak.

**Modpack integration**
- The tag set in §13.
- Item locks as datapack files rather than a 54 KB Java literal.
- Titles as a datapack registry (also fixes RS-015 for titles).
- Declare all ~40 integrated mods as optional dependencies with `ordering = "AFTER"`.

**API / extensibility**
- Fire `SkillLevelUpEvent`/`PerkToggleEvent` where the javadoc says they fire, or fix the javadoc (RS-111).
- Add the XP-award and pre-level-up hooks the four existing events imply but do not cover — an addon can observe a level-up but cannot influence the XP that caused it.
- Make the dead KubeJS perk/passive registration API real, or remove it (RS-191).
- Expose `SkillCapability` through a narrow read-only interface so addons stop needing the concrete class.

**UX**
- Surface the earned global level and the perk-swap cooldown (RS-172).
- Show the actual rejection reason for all eight `TogglePerkSP` rejection paths (RS-079).
- Register the `PowersScreen` keybind (RS-023).
- Add search/filter across 471 perks (RS-194).
- Make HUD overlay positions configurable (RS-165).

**Performance**
- The three per-tick fixes (RS-009, RS-010, RS-066) are individually small and collectively remove ~40 packets/second/player.
- A delta sync packet replacing the full-state broadcast (RS-050).
- Cache `modid:path` on `Perk` at construction (RS-057).

**Reliability**
- Schema version + orphan retention (RS-005, RS-006).
- Per-field config parsing that never destroys the operator's file (RS-004).
- Self-service `/respec` so a config change cannot strand an entire playerbase (RS-052).

**Developer experience**
- Pin the build; add a dedicated-server boot job; add the P0 tests.
- A `LanguageProvider` data generator for 2,061 hand-maintained keys.

---

## 19. Dead Code / Cleanup

High-confidence deletion candidates:

| Item | Evidence | Finding |
|---|---|---|
| `HandlerConfigCommon` | 196 lines of `ForgeConfigSpec` shadowing 60 live fields; never read | RS-096 |
| `ItemListGroup`, `HandlerSkill.defaultLockItemList` | unreferenced | RS-175 |
| MixinExtras AP + jarJar declaration | declared, never used, `jarJar.enable()` never called | RS-037 |
| `betterCombatEntityRange` | persisted and re-sent on every mutation; nothing reads it | RS-192 |
| KubeJS perk/passive registration | dead API | RS-191 |
| Dead `PowerRuntime` subsystems | registered, never consumed, unbounded growth shape | RS-159 |
| 8 dead `gradle.properties` entries | two of them contradict `build.gradle` | RS-116 |
| 21 unused imports | | RS-183 |
| 8 duplicate keys in `en_us.json`; 9 stale keys × 16 locales | | RS-171 |
| `icons/` (399 PNGs) | stale byte-diverged duplicate of shipped textures | RS-112 |
| `tab_menu_buttons.png` at repo root | third-party asset, unused, licensing issue | RS-113 |
| Dead Maven repos (blamejared, unfiltered Modrinth) | Botania removed in 1.5.0 | RS-182 |
| Duplicate root markdown (2 × byte-identical 40 KB), committed AI prompt | | RS-185 |
| `commands.argument.skill.not_found` | key exists; the error is never thrown | RS-121 |

**Explicitly do not delete:** `RegistryPerks` is 4,419 lines but mechanically uniform and guarded by three source-scanning tests. Splitting it adds merge risk and refactor risk without adding safety. Leave it.

---

## 20. Technical Debt

Five structural debts, in order of how much they cost per change:

**1. The hand-mirrored config wire format.** 1,103 fields in one class, 172 of them copied by hand into a positional packet. Every new config field is a four-place edit with no machine check that the four agree. This is the highest per-change tax in the codebase and the cheapest to fix (one reflection test, then a JSON-blob sync).

**2. The two-system validation split (RS-028).** `@IntField` describes the constraint; `@Clamp` enforces it; 1,033 fields have the first and not the second. The annotations and the enforcement drifted apart during the YACL migration and never reconverged.

**3. Config snapshotting at registration.** `Perk`/`Passive` read config into `final` fields inside their `DeferredRegister` suppliers and `Perk.getValue()` memoises forever. This is why `/skillsreload` cannot change a perk, why the config sync is largely inert, and why five registry caches are memoised for the JVM lifetime. It is a single pattern with three symptoms.

**4. Perk behaviour scattered across 40+ files.** The two declarative tables in `PerkEffectsHandler` show the intended direction. Most perks predate them and are inline `if (on(PERK, player))` branches in 232-line methods on the melee hot path, which is also why combat logic is untestable.

**5. Clock-baseline confusion.** `Entity.tickCount`, `level.getGameTime()` and `server.getTickCount()` are used interchangeably for windowing. They are not interchangeable: `tickCount` resets on respawn (RS-063), `getTickCount()` resets on restart while `getGameTime()` does not (RS-041, RS-132). Every one of these is a silent, restart-conditional misbehaviour.

---

## 21. Recommended Refactors

### R1 — Config sync as one object instead of 172 fields

**Current state.** `CommonConfigSyncCP` (519 lines) and `DynamicConfigSyncCP` (285 lines) hand-copy 128 of 1,103 fields in a positional wire format, writing into a singleton POJO that `Perk`/`Passive` stopped reading at boot.

**Problem.** Four-place edit per field; no machine check of agreement; the `-`-delimited array packing is a live crash vector; nothing restores the client's own values on disconnect; the sync is largely inert anyway because of R2.

**Proposed state.** One packet carrying `Gson.toJson(serverConfig)` as a length-checked string. The client holds it as `HandlerCommonConfig serverConfig`, and a single accessor (`Cfg.get()`) returns `serverConfig != null ? serverConfig : localConfig`. Disconnect sets `serverConfig = null`.

**Migration.** Add the packet and accessor; migrate read sites incrementally (the compiler finds them); delete the two hand-written packets last. Bump `PROTOCOL_VERSION`.

**Risk.** Low. The new path is additive until the old packets are removed, and a round-trip test is trivial to write first.

### R2 — Read config at use time, not at registration time

**Current state.** `Perk.requiredLevel`, `rankLevelRequirements`, `configValues`, `Passive.attributeValue` and `levelsRequired` are `final`, populated inside `DeferredRegister` suppliers; `Perk.getValue()` memoises into `cachedValues`.

**Problem.** `/skillsreload` cannot change a perk. Config sync cannot affect client prediction. Five caches never invalidate. It is the root cause of six separate findings.

**Proposed state.** Replace the `final` fields with `IntSupplier`/`DoubleSupplier` bound to the config holder, or add an explicit `invalidateCaches()` called from every reload path.

**Migration.** The supplier form is mechanical and can be done per-field-group. Start with `requiredLevel` (the one that most visibly fails today), verify `/skillsreload` changes a gate, then continue.

**Risk.** Medium — `getValue()` is on the melee hot path, so the supplier must not allocate. Bind once at registration; call per use.

### R3 — Move perk effect dispatch into declarative tables

**Current state.** `PerkEffectsHandler` has two good tables (`REDUCTIONS`, `ATTRS`) and ~40 inline branches; the rest of the effects live in six other handlers and 14 mixins. `onLivingHurtStrengthAttacker` is 232 lines on the melee hot path.

**Problem.** Untestable, unprofileable, and the reason `Perk.isEnabled` is called ~100× per hit.

**Proposed state.** Extend the table pattern to every effect expressible as (condition, attribute/damage-type, magnitude). Leave genuinely bespoke effects as code, but behind a registered handler interface rather than an inline branch.

**Migration.** Incremental and individually verifiable — `PerkEffectCoverageTest` already exists to prove no perk loses its effect.

**Risk.** Low per perk, and the existing coverage test is the safety net.

### R4 — One clock

**Current state.** Three time bases used interchangeably for windows and cooldowns.

**Proposed state.** `level.getGameTime()` everywhere for gameplay windows (monotonic, persisted, dimension-independent). Reset every static baseline on `ServerStoppedEvent`.

**Migration.** Mechanical grep-and-replace plus a `ServerStoppedEvent` handler. Persisted values keyed on the old base need a one-time migration — which is another argument for R5.

**Risk.** Low.

### R5 — Schema version and orphan retention

Described in §8. Small, and it is the precondition for every future data change.

---

## 22. Quick Wins

Valuable, low-risk, small effort — roughly in value order:

| # | Change | Finding |
|---|---|---|
| 1 | Add the `playersMaxGlobalLevel` check to `SkillLevelUpSP#handle` (2 lines) | RS-008 |
| 2 | Null-guard `TitleOverlayCP#handle` and `OverlayTitleGui#render` (3 lines) | RS-022 |
| 3 | Stop `ConfigHolder.load()` overwriting a file that failed to parse (1 line) | RS-004 |
| 3b | Have `applyClamps` fall back to the `@IntField`/`@FloatField` ranges (closes 1,033 fields at once) | RS-028 |
| 4 | `Math.max(1, …)` on every `nextInt(bound)` and move the roll after `isEnabled` (5 sites) | RS-029 |
| 5 | Move `MeetCondition` inside the existing try/catch in `TitleModel#CheckRequirements` (1 line) | RS-017 |
| 6 | `if (event.side != LogicalSide.SERVER) return;` in `TickEventHandler#onPlayerTick` and the four other unguarded handlers | RS-060–062 |
| 7 | Short-circuit `isDisabled(Perk)` on an empty list before building the id string | RS-057 |
| 8 | Only re-apply mob effects when absent or nearly expired | RS-009 |
| 9 | Add the `existing.getAmount() == amount → return` guard to `amplifyAttribute` and switch to `addTransientModifier` | RS-010 |
| 10 | `getOrDefault` in the three unhardened capability accessors | RS-054 |
| 11 | Null-check the capability in `Title#getRequirement`/`#setRequirement` | RS-055 |
| 12 | Move `PacketRateLimiter.allow` ahead of `enqueueWork` | RS-150 |
| 13 | `if (!player.isAlive() \|\| player.isSpectator()) return;` in `OpenEnderChestSP` | RS-151 |
| 14 | `require = 0` on the three optional-mod mixins | RS-048 |
| 15 | `disableSync()` on the five custom registries | RS-015 |
| 16 | Register the `PowersScreen` keybind | RS-023 |
| 17 | Resync the client on the `SkillLevelUpEvent` cancellation path | RS-156 |
| 18 | Pin the three unpinned Gradle plugin versions | RS-036 |
| 19 | Fix the README `/respec` entry and the KubeJS example FQCN | RS-039, RS-040 |
| 20 | Delete the 9 stale lang keys and the 8 duplicate `en_us` keys | RS-171 |

Items 1–5 are, in this auditor's view, the five changes that most improve the mod per line of diff.

---

## 23. Long-Term Improvements

- **R1–R5 from §21**, in that order.
- **Per-world server config** (`<world>/serverconfig/`), which eliminates the singleplayer leak, the per-instance limitation, and the "which config is this world running?" support burden in one change.
- **Datapack-defined titles and item locks**, which makes the mod genuinely modpack-authorable and removes the highest-severity registry hazard.
- **The tag vocabulary in §13**, which is what lets other mods integrate without a Java patch.
- **A GameTest suite**, which is the only way the combat and progression paths become regression-safe. The two existing source-scanning tests prove the team can build this kind of infrastructure.
- **A `LanguageProvider` data generator**, replacing 2,061 hand-maintained keys and making locale completeness machine-checkable.
- **Split `HandlerCommonConfig` by domain** (core / combat / integrations) once R1 removes the hand-mirrored packet — not before, because the packet is what makes the split risky today.

---

## 24. Prioritized Remediation Roadmap

### Phase 1 — Correctness & data safety

The four Critical faucets (RS-001, RS-002, RS-003, RS-004) plus the crash-and-destroy set: per-field config parsing that never overwrites a failed file, the title-condition parse crash (RS-017), `nextInt(0)` (RS-029), the `TitleOverlayCP` null NPE loop (RS-022), the treasure-list null (RS-130), the `DynamicConfigSyncCP` login kick (RS-027). Then schema version + orphan retention (RS-005, RS-006) — do this *before* any further data-shape change ships, because it is the precondition for every migration after it. Finish with the remaining duplication exploits (RS-012, RS-058) and `COUNTER_ATTACK`'s permanent modifier (RS-011).

### Phase 2 — Multiplayer, authority & security

Server-side global cap (RS-008); `administrator` re-derivation and title re-lock (RS-056); rate-limit admission ordering plus a violation counter (RS-150); `LazyOptional` invalidation (RS-007); the `veinMine` and `CLEAVE` protection bypasses (RS-013, RS-014); `OpenEnderChestSP` state checks (RS-151); `disableSync()` on the five registries (RS-015). Add the dedicated-server boot CI job here — it belongs with the security phase because it is the guard against the client-classloading fragility that Phase 5's refactors will otherwise expose.

### Phase 3 — Performance

The three per-tick fixes (RS-009, RS-010, RS-066); `isEnabled` string caching (RS-057); the delta sync packet (RS-050); `DrawTabs` per-frame allocation (RS-025); `Skill#getPerks` double rebuild (RS-077); the FTB Quests full-tree walk (RS-180); the clock-baseline unification (R4), which also fixes the Apotheosis attribution bug (RS-041) and the disabled pruning (RS-132).

### Phase 4 — Compatibility & extensibility

The three cancel-and-reimplement mixins, in severity order: `MixLivingEntity` → `MobEffectEvent.Applicable`/`Added` (RS-032, RS-033); `MixForgeGui` → `RegisterGuiOverlaysEvent` (RS-078); `MixCraftingMenu` ordering (RS-034). `require = 0` on optional-mod mixins (RS-048). Declare all integrated mods in `mods.toml` with `AFTER` ordering (RS-107). Then the data-driven work: the tag vocabulary, datapack titles, datapack item locks, and R1/R2 — R2 in particular is what makes `/skillsreload` mean what pack authors expect it to mean.

### Phase 5 — Maintainability

The P0 test matrix (`ConfigSyncMirrorTest` first — it is the highest-value single test in the codebase). Pin the build. R3 (declarative perk effects). Config validation fallback to `@IntField` ranges. The dead-code list in §19. Documentation corrections.

### Phase 6 — UX & enhancements

Rejection reasons (RS-079), earned global level and swap cooldown in the UI (RS-172), locked-title affordance (RS-080), the `PowersScreen` keybind (RS-023), perk search (RS-194), configurable HUD positions (RS-165), keyboard/narrator access (RS-087), self-service `/respec` (RS-052), the missing admin/diagnostic commands (RS-124), localisation completion and indexed placeholders.

**Note on ordering.** Phase 1's schema-version work is deliberately early rather than grouped with the persistence refactors, because every later phase that changes a data shape needs it to exist first. Conversely, splitting `HandlerCommonConfig` is deliberately *last* — R1 must land first or the split is a merge hazard against a hand-mirrored packet.

---

## 25. What Runic Skills Already Does Well

These are supported by specific code, not offered as balance.

**Capability lifecycle.** `PlayerEvent.Clone` does `reviveCaps()` → `copyFrom` → `invalidateCaps()`, and `wasDeath` correctly does *not* gate the copy, so data survives both death and the End return. Sync across login, respawn and dimension change is achieved with **one** `EntityJoinLevelEvent` hook, because `ServerLevel#addEntity` is the common funnel for `addNewPlayer`, `addRespawnedPlayer` and `addDuringTeleport`. Most mods get one of these wrong.

**`FakePlayer` exclusion at attach time.** `onAttachCapabilitiesPlayer` refuses to attach to a `FakePlayer` at all, so `Perk.isEnabled` returns false for every automation fake player regardless of whether an individual handler remembers to check. Create deployers, AE2 pattern providers, Botania and Industrial Foregoing cannot trigger a single perk or XP grant. This is the most common source of "my modpack duplicates items" reports in skill mods, and it is closed here by construction.

**Packet trust model.** No C→S packet carries a UUID, entity ID, or target selector — cross-player action is not expressible. Every registration passes an explicit `NetworkDirection`. `SyncSkillCapabilityCP` uses `PacketDistributor.PLAYER`, never a broadcast. `TogglePerkSP` runs nine independent server-side checks and resyncs on every rejection path, with a deliberate "disable is always allowed" escape hatch so a config change can never permanently strand a player.

**Protocol version discipline.** `PROTOCOL_VERSION` has been bumped for payload-only changes, not just new packets (4→5, 5→6, 6→7, 7→8), each with an inline comment stating the rule and the reason. `acceptsVersion` logs the exact required-vs-reported values while suppressing Forge's `ABSENT`/`ALLOWVANILLA` probe noise. Version 8 is correct for the current tree.

**The extracted math layer.** `ExperienceMath`, `SkillLevelUpMath`, `PerkCapMath`, `PacketBounds`, `ApothGateMath`, `ForgingQualityMath` are Forge-free, headless-testable, and shared by client and server so the two predicates cannot drift. The 1.5.5 fix collapsing the OR-across-currencies affordability gate into a single points comparison is exactly the right correction.

**Source-scanning invariant tests.** `PerkEffectCoverageTest` and `PerkTextureResolutionTest` assert properties of the *source tree*, not of a function. They are why 561 textures produced 0 missing and 0 orphaned. This technique is underused industry-wide and should be extended to lang keys and the config wire format.

**`ConfigHolder`'s write path.** Temp file + atomic move, `.invalid` backup on unparseable input, empty-file detection, `@Clamp` enforcement with per-field warnings, and a hand-written JSON5 comment stripper that respects string literals. The write path is better than most mods'; it is the read path's all-or-nothing failure mode that needs work.

**`PerkEffectsHandler#onAttributeTick`.** Throttled to once per second, idempotent (`existing.getAmount() == amount → continue`), uses `addTransientModifier`, and caches `nameUUIDFromBytes` results. The comments show the per-tick churn problem was found and fixed *here* — `TickEventHandler` simply never received the same treatment.

**Deliberate, documented sided design.** A custom `:checkSidedImports` Gradle task, a mixin plugin gating optional-mod mixins, the `@OnlyIn(Dist.CLIENT)` nested-handler pattern in `PowerProcCP`, the `ClientProxy` method-reference indirection for YACL, and an honest `ConfigHolder` javadoc explaining why YACL's static initialiser had to be quarantined. The hard part of Forge sidedness has been engaged with directly.

**Optional-dependency isolation patterns.** `tryLoadIntegration(modid, FQCN)`, the `RunicQuestBridge` NOOP facade, `ApothicAttributesIntegration` as a foreign-type-free holder, and the `LinkageError` quarantine around L2Tabs are all correct and are the right models for the cases that currently fall short of them.

**Explanatory comments where they matter.** `RegistryCommonEvents` explains the static-vs-instance registration split. `SkillCapability.get`'s `ThreadLocal` memo documents why it deliberately does not memoise nulls. `EntityKilledCondition.WARNED_MISSING` explains the log-spam it replaces. Several null-safe registry lookups name the mid-gameplay NPE they were added to fix. These are comments explaining *why*, which is the kind worth having.

**No off-thread game-state mutation anywhere.** Verified across the whole tree. In a mod this size, with this many integrations, that is a genuinely clean result.

---

## 26. Machine-Readable Findings Index

| ID | Finding | Severity | Confidence | Impact | Effort | File | Audit pass |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RS-001 | LOCKSMITH awards vanilla XP on PlayerContainerEvent.Open — an unlimited, zero-cost XP farm, … | Critical | High | Critical | Small | `CraftingEventHandler.java` | events/XP |
| RS-002 | SILK_TOUCH_MASTERY drops the block *in addition to* its normal drops — unbounded duplication… | Critical | High | High | Small | `PerkEffectsHandler.java` | events/XP |
| RS-003 | LUCKY_DROP multiplies the stack size of *any* freshly-spawned item entity near the corpse — … | Critical | Medium | High | Moderate | `CraftingEventHandler.java` | events/XP |
| RS-004 | One bad character resets all 1141 settings to defaults and overwrites the file | Critical | High | A pack's entire balance silently reverts on a typo | M | `ConfigHolder.java` | config |
| RS-005 | No save-schema version field anywhere in the capability NBT | High | High | High | Small | `SkillCapability.java` | persistence |
| RS-006 | Unknown NBT keys silently discarded on next save (no orphan pass-through) | High | High | High | Moderate | `SkillCapability.java` | persistence |
| RS-007 | Attached LazyOptional never invalidated — stale capability survives death/dimension change | High | Medium | High | Trivial | `LazySkillCapability.java` | persistence |
| RS-008 | Server-side skill level-up never enforces playersMaxGlobalLevel — global cap is client-only | High | High | High | Trivial | `SkillLevelUpSP.java` | networking |
| RS-009 | Per-tick addEffect re-application sends a mob-effect packet every tick, per player | High | High | High | Small | `TickEventHandler.java` | events/XP |
| RS-010 | Per-tick attribute modifier churn marks ARMOR/ATTACK_DAMAGE dirty every tick and writes *per… | High | High | High | Small | `TickEventHandler.java` | events/XP |
| RS-011 | COUNTER_ATTACK grants a permanent, never-expiring ATTACK_DAMAGE bonus — setCounterAttack(tru… | High | High | High | Trivial | `SkillCapability.java` | events/XP |
| RS-012 | DOUBLE_DOWN duplicates the drops of *every* block, with no player-placed-block check | High | High | High | Moderate | `PerkEffectsHandler.java` | events/XP |
| RS-013 | VEIN_MINER uses Level.destroyBlock, which fires no BlockEvent.BreakEvent — bypasses land-cla… | High | High | High | Small | `PerkEffectsHandler.java` | events/XP |
| RS-014 | CLEAVE re-enters hurt() inside a LivingHurtEvent handler, and its splash hits receive the fu… | High | High | High | Moderate | `CombatEventHandler.java` | events/XP |
| RS-015 | Config-derived content lives in *synced* Forge registries — client/server registry mismatch … | High | Medium-High | Players cannot join | M | `RegistryPerks.java` | progression |
| RS-016 | Flipping apothicDelegate* at runtime NPEs on every critical hit | High | High | Server crash / repeated exception | S | `RunicSkills.java` | progression |
| RS-017 | A non-numeric title-condition value throws out of the title scan and kills the join/tick | High | High | Player cannot join; server tick exception | S | `TitleModel.java` | progression |
| RS-018 | Perk attribute modifiers are addPermanentModifier and are orphaned when the perk is disabled | High | High | Permanent unremovable stat bonus in save data | M | `RegistryAttributes.java` | progression |
| RS-019 | Perk magnitudes and required levels are frozen at registration; /skillsreload silently doesn… | High | High | Config edits appear to work but don't; two sources of truth | M | `RegistryPerks.java` | progression |
| RS-020 | Equipped Powers are never re-validated: they survive respec, skill loss and level rollback | High | High | Progression bypass | S | `RespecCommand.java` | progression |
| RS-021 | Bulk passive level-up/down (Shift ×5 / Ctrl ×10 / Alt = max) is silently defeated by the ser… | High | High | Advertised feature applies exactly 1 level instead of 5/10/N, silently | Low | `RunicSkillsScreen.java` | client/UI |
| RS-022 | An unknown title id from the server enqueues null and NPEs the HUD overlay every frame | High | High | Repeating exception in the GUI render loop; title queue permanently wedged | Trivial | `TitleOverlayCP.java` | client/UI |
| RS-023 | PowersScreen is unreachable dead code — the keybind it documents is never registered | High | High | A whole shipped feature (Powers equip UI, ~15 KB, 12 lang keys) has no entry point | Low | `PowersScreen.java` | client/UI |
| RS-024 | All 16 non-English locales are ~72 % incomplete, and tooltip.skill.level_up is placeholder-b… | High | High | Non-English players see raw `%s` in a core tooltip and English text for 1486 keys | High (translation) / Trivial (the `%s` bug) | `RunicSkillsScreen.java` | client/UI |
| RS-025 | DrawTabs.render allocates two full Screens, a RecipeBookComponent and a GameProfile-tagged I… | High | High | Constant GC churn and NBT rebuild whenever the inventory or Skills screen is open | Low | `DrawTabs.java` | client/UI |
| RS-026 | Opening the config screen while connected to a server writes the server's values into the cl… | High | High | Client's local/singleplayer config silently overwritten; server rules replaced by client rules mid-session | S | `YaclConfigUiBuilder.java` | config |
| RS-027 | DynamicConfigSyncCP packs 16 int arrays into a --delimited string; a negative or empty array… | High | High | Client disconnect on join / passive levels silently mis-assigned | S | `DynamicConfigSyncCP.java` | config |
| RS-028 | @Clamp covers 9 of 1042 range-annotated fields — hand-edited values reach runtime unvalidated | High | High | Every documented range in the config is advisory only outside the UI | M | `Clamp.java` | config |
| RS-029 | nextInt(probability) throws on a 0 or negative config value, and the convergence roll runs b… | High | High | Exception on every craft / every hit for every player | S | `CraftingEventHandler.java` | config |
| RS-030 | A trailing comma in a JSON5 array silently injects a null element; a null lock entry NPEs th… | High | High | Item locking silently or loudly breaks after a benign edit | S | `ConfigHolder.java` | config |
| RS-031 | Server config leaks into singleplayer and into the next server: nothing reloads the config o… | High | High | A player who visits one server carries its rules into every later world | S | `Configuration.java` | config |
| RS-032 | MixLivingEntity rebuilds MobEffectInstance for every player effect, discarding ambient/visib… | High | High | Cross-mod effect behaviour | Low | `MixLivingEntity.java` | integrations/mixins |
| RS-033 | MixLivingEntity substitutes the affected player for the real effect *source* entity | High | High | Attribution / other mods' event handlers | Low | `MixLivingEntity.java` | integrations/mixins |
| RS-034 | MixCraftingMenu empties the crafting result after vanilla has already sent it to the client | High | High | Client/server desync, ghost items | Low | `MixCraftingMenu.java` | integrations/mixins |
| RS-035 | Haggler discount is written to persistent MerchantOffer.specialPriceDiff but the undo map li… | High | Medium-High | Compounding price exploit | Medium | `MixVillager.java` | integrations/mixins |
| RS-036 | Build is not reproducible: SNAPSHOT plugin plus two dynamic version ranges | High | High | High | Low | `build.gradle` | build/tests/docs |
| RS-037 | MixinExtras is declared as an annotation processor and a jar-in-jar dependency but is never … | High | High | Medium | Low | `build.gradle` | build/tests/docs |
| RS-038 | /updateskilllevel and /globallimit write config values that bypass @Clamp, and extreme value… | High | High | High | Low | `UpdateSkillLevelCommand.java` | build/tests/docs |
| RS-039 | README's command reference is materially wrong for three of eight documented commands | High | High | Medium | Low | `README.md` | build/tests/docs |
| RS-040 | The documented KubeJS subscription example names a class that does not exist | High | High | Medium | Low | `API_EVENTS.md` | build/tests/docs |
| RS-041 | Apotheosis interactor attribution compares level game-time against server uptime ticks | High | High | High | Trivial | `ApotheosisIntegration.java` | cross-system |
| RS-042 | An empty passive-level array divides by zero and installs a NaN attribute modifier | High | High | High | Trivial | `RegistryAttributes.java` | cross-system |
| RS-043 | Shrinking a passive-level array leaves players above the new maximum, and the attribute scal… | High | High | High | Small | `SkillCapability.java` | cross-system |
| RS-044 | A config-disabled perk still consumes the perk budget, and hideDisabledPerks removes the onl… | High | High | High | Small | `RegistryPerks.java` | cross-system |
| RS-045 | Documented Power prerequisites and the Power-Point budget are not enforced anywhere | Medium-High | High | Documented balance rules absent | M | `PowerEquipSP.java` | progression |
| RS-046 | Power internal cooldowns and proc windows are transient — relogging resets them | Medium-High | High | Exploit; dead persisted state | M | `PowerRuntime.java` | progression |
| RS-047 | IronsSpellbooksIntegration.isModLoaded() is invoked from always-loaded classes, defeating th… | Medium-High | Medium-High | Latent hard crash without ISS | Low | `RunicSkills.java` | integrations/mixins |
| RS-048 | Optional-mod mixins inherit defaultRequire: 1 — a version bump in the target mod becomes a c… | Medium-High | High | Startup crash on target-mod update | Low | `mixins.json` | integrations/mixins |
| RS-049 | GenericNamespaceLockProvider auto-locks another mod's entire item registry by substring match | Medium-High | High | Wrong items gated across ~30 mods | Medium | `LockGen.java` | integrations/mixins |
| RS-050 | Full ~600-entry capability state re-serialized and re-sent on every mutation | Medium | High | High | Moderate | `SyncSkillCapabilityCP.java` | persistence |
| RS-051 | Equipped Power lists deserialized with no cap, dedup, or existence check | Medium | High | Medium | Trivial | `SkillCapability.java` | persistence |
| RS-052 | /respec requires op but is the only exit from the over-budget perk freeze | Medium | High | Medium | Small | `RespecCommand.java` | persistence |
| RS-053 | Client capability sync dropped silently with no retry when local player absent | Medium | Medium | Medium | Small | `SyncSkillCapabilityCP.java` | persistence |
| RS-054 | Direct Integer/Boolean unboxing in three capability accessors | Medium | High | Medium | Trivial | `SkillCapability.java` | persistence |
| RS-055 | Title.getRequirement dereferences a nullable capability | Medium | High | Medium | Trivial | `Title.java` | persistence |
| RS-056 | Title unlocks are monotonic — op-gated administrator title survives de-op (staff impersonation) | Medium | High | Medium | Small | `Title.java` | networking |
| RS-057 | Perk.isEnabled() allocates a String on every call, and it is called ~100× per melee hit | Medium | High | Medium | Trivial | `RegistryPerks.java` | events/XP |
| RS-058 | LORE_MASTERY multiplies *any* XP orb picked up while a grindstone menu happens to be open | Medium | High | Medium | Trivial | `CraftingEventHandler.java` | events/XP |
| RS-059 | The "80% damage-reduction clamp" is applied per handler, not globally — three independent st… | Medium | High | Medium | Moderate | `CombatEventHandler.java` | events/XP |
| RS-060 | TickEventHandler.onPlayerTick runs client-side and drops/zeroes item stacks there | Medium | High | Medium | Trivial | `TickEventHandler.java` | events/XP |
| RS-061 | ItemCraftedEvent handlers run on the client and roll RNG independently, producing ghost bonu… | Medium | Medium | Medium | Trivial | `PerkEffectsHandler.java` | events/XP |
| RS-062 | Food and item-use handlers run client-side; RAPID_FIRE/CROSSBOW_EXPERT desync bow draw betwe… | Medium | High | Medium | Trivial | `PerkEffectsHandler.java` | events/XP |
| RS-063 | Combat-memory windows are keyed on Entity.tickCount, which resets to 0 on respawn — perks si… | Medium | High | Medium | Small | `PerkEffectsHandler.java` | events/XP |
| RS-064 | onLivingHurtStrengthAttacker applies melee-flavoured bonuses (and CLEAVE) to any damage the … | Medium | High | Medium | Small | `CombatEventHandler.java` | events/XP |
| RS-065 | Dodge cancels LivingHurtEvent at LOWEST priority, after every other mod has already committe… | Medium | Medium | Medium | Small | `PerkEffectsHandler.java` | events/XP |
| RS-066 | Unconditional per-tick work in the Powers dispatcher, plus game-time-modulo scheduling that … | Medium | High | Medium | Small | `PowerEventDispatcher.java` | events/XP |
| RS-067 | Datapack skill_visuals overrides never reach clients on a dedicated server | Medium | High | Feature silently no-ops in multiplayer | M | `SkillVisualsReloadListener.java` | progression |
| RS-068 | SkillVisualsReloadListener.previouslyOverridden is per-instance but the instance is recreate… | Medium | High | Removed overrides never revert | S | `SkillVisualsReloadListener.java` | progression |
| RS-069 | PowerOverridesReloadListener claims the generic powers/ datapack folder | Medium | Medium-High | Cross-mod data collision, log spam | S | `PowerOverridesReloadListener.java` | progression |
| RS-070 | /reload does not resync perk groups or power overrides to clients; /skillsreload misses powe… | Medium | High | Client shows stale rules | S | `PerkGroupsReloadListener.java` | progression |
| RS-071 | Reload-invalidated state is never reconciled on players who already hold it | Medium | High | Rules apply only to new selections | M | `PerkGroupManager.java` | progression |
| RS-072 | break_speed and mining_speed are the same attribute under the default Apothic config | Medium | High | Duplicate stat, double-dipping | S | `RegistryPassives.java` | progression |
| RS-073 | Toggling Apothic delegation leaves an orphaned permanent modifier on the abandoned attribute | Medium | Medium-High | Stale stat bonus survives forever | S | `RegistryAttributes.java` | progression |
| RS-074 | titlesUseCustomName is ignored by the periodic title sync; the chat prefix never refreshes | Medium | High | Config toggle does nothing; stale display name | S | `RegistryTitles.java` | progression |
| RS-075 | User-authored ids become ResourceLocations with no validation | Medium | High | Startup crash from a config typo | S | `TitleModel.java` | progression |
| RS-076 | Passives are not re-gated by skill level at runtime, unlike perks | Medium | High | Stat bonuses survive skill loss | S | `RegistryAttributes.java` | progression |
| RS-077 | Skill#getPerks / getPassives rebuild the whole registry list twice per iteration | Medium | High | ~450k redundant element copies per GUI rebuild | S | `Skill.java` | progression |
| RS-078 | MixForgeGui overwrites ForgeGui.rightHeight instead of incrementing it, and unconditionally … | Medium | High | Right-side HUD stacking breaks for every other mod's overlay; other renderAir modifications are silently discarded | Trivial | `MixForgeGui.java` | client/UI |
| RS-079 | Server rejects perk toggles for eight distinct reasons; the client shows a reason for exactl… | Medium | High | Clicks that "do nothing" with no explanation — the most common UX complaint class for skill mods | Medium | `TogglePerkSP.java` | client/UI |
| RS-080 | Locked titles render identically to unlocked ones and clicking one gives zero feedback | Medium | High | Titles page reads as broken; colour is the only status channel and it does not encode lock state at all | Low | `RunicSkillsScreen.java` | client/UI |
| RS-081 | Rendering the skill icon mutates the authoritative capability client-side, and NPEs if it is… | Medium | High | Client writes to shared state during a render pass; unguarded NPE on a nullable accessor | Low | `Skill.java` | client/UI |
| RS-082 | OverlaySkillGui / OverlayNoticeGui draw twice per frame while a screen is open; the comment … | Medium | High | Double-composited alpha (visibly darker banner), double render cost, and a wrong invariant baked into two files | Low | `OverlaySkillGui.java` | client/UI |
| RS-083 | OverlayTitleGui animation is frame-rate driven, divides by zero at the ends, and stalls when… | Medium | High | Pop-in animation runs 4× faster on a 240 Hz display; degenerate transform each time a title finishes; title queue does not advance while a screen is open | Medium | `OverlayTitleGui.java` | client/UI |
| RS-084 | The level-up button pulses at up to ~6 Hz on high-refresh displays, and pulse state is mutat… | Medium | High | Photosensitivity risk (WCAG 2.3.1 "three flashes" threshold), and frame-rate-dependent UI behaviour | Low | `RunicSkillsScreen.java` | client/UI |
| RS-085 | TooltipWrap.wrap flattens wrapped lines to plain literals, destroying inline colour and tran… | Medium | High | Any tooltip line wider than 200 px loses all inline styling — exactly the long, argument-rich perk descriptions | Medium | `TooltipWrap.java` | client/UI |
| RS-086 | Hard-coded English strings in client-facing UI | Medium | High | Untranslatable UI text; also unreachable-by-resource-pack | Low | `—` | client/UI |
| RS-087 | The Skills screen is entirely mouse-only: no keyboard navigation, no narrator, and the searc… | Medium | High | Screen is unusable without a mouse and invisible to the narrator; text field is half-wired | Medium | `RunicSkillsScreen.java` | client/UI |
| RS-088 | DrawTabs uses a cross-frame click latch and switches screens from inside the render pass | Medium | Medium | Phantom tab activation from a stale latch; the outgoing screen keeps rendering after `removed()` | Medium | `DrawTabs.java` | client/UI |
| RS-089 | Common-package classes depend on client.core.Utils, whose static initialiser calls Minecraft… | Medium | High | A latent dedicated-server `NoClassDefFoundError` that the sided-import lint cannot see | Low | `Utils.java` | client/UI |
| RS-090 | Lowering skillMaxLevel is not reconciled server-side; the client "fixes" it by mutating capa… | Medium | High | Client and server disagree about a player's level; client-side write to authoritative state | M | `Skill.java` | config |
| RS-091 | Built-in lock, title and perk thresholds are hardcoded against skillMaxLevel = 32, with no c… | Medium | High | Lowering the level cap makes content permanently unreachable, with no warning | M | `HandlerTitlesConfig.java` | config |
| RS-092 | The JSON5 comment stripper mishandles single-quoted strings; combined with lenient Gson this… | Medium | High | Valid JSON5 is rejected; a total config reset follows | S | `ConfigHolder.java` | config |
| RS-093 | Renamed or unknown keys are silently dropped, and the next save deletes them from disk | Medium | High | Silent loss of a pack author's tuning across mod updates | S | `ConfigHolder.java` | config |
| RS-094 | The on-disk config has zero documentation, and 403 fields are invisible in the UI too | Medium | High | A 1141-key file that a pack author must edit blind | M | `ConfigHolder.java` | config |
| RS-095 | int[] passive-level arrays have no validation and an unenforced "don't do this" comment | Medium | High | Broken passives, divide-by-zero tooltips, corrupt sync packets | S | `HandlerCommonConfig.java` | config |
| RS-096 | HandlerConfigCommon is 196 lines of dead ForgeConfigSpec that duplicates 60 field names and … | Medium | High | Maintenance trap — two sources of truth for the same tunables | S | `HandlerConfigCommon.java` | config |
| RS-097 | Title ids come from user config and go straight into ResourceLocation — a typo crashes mod l… | Medium | High | Unbootable game/server from a single character in a config file | S | `TitleModel.java` | config |
| RS-098 | rebindAfterReload drops titles added since startup from the in-memory list, and a later save… | Medium | Medium | A pack author's new title silently disappears from their config file | S | `RegistryTitles.java` | config |
| RS-099 | ESkill is a fixed 10-value enum, so lock items cannot reference KubeJS-registered skills | Medium | High | The extensibility story stops at the item-lock system | M | `ESkill.java` | config |
| RS-100 | Config lives per-instance, not per-world, so one file governs every singleplayer save | Medium | High | No per-world balance; a client's file is simultaneously "their singleplayer rules" and "display defaults for servers" | L | `Configuration.java` | config |
| RS-101 | The item-lock lists and per-mod integration lists belong in datapacks/tags, not in a 54 KB J… | Medium | High | Pack authors cannot add locks without editing a 435-entry generated file; mod-support additions require a mod release | L | `HandlerLockItemsConfig.java` | config |
| RS-102 | MixShulkerBullet cancel-and-reimplement drops other mods' onHitEntity behaviour for a rule F… | Medium | High | Conflicts with combat/effect mods | Low | `MixShulkerBullet.java` | integrations/mixins |
| RS-103 | MixEnchantmentMenu targets EnchantmentMenu#clickMenuButton, which Apotheosis overrides | Medium | Medium | Perk silently inert in the packs it targets | Medium | `MixEnchantmentMenu.java` | integrations/mixins |
| RS-104 | MixInventoryScreen declares a bare onClose() — an implicit, unannotated overwrite on a heavi… | Medium | Medium-High | Mixin conflict with other inventory-tab mods | Low | `MixInventoryScreen.java` | integrations/mixins |
| RS-105 | MixTargetFinder is declared in the common (non-client) mixin list but targets a client-only … | Medium | Medium-High | Dedicated-server log noise / transform failure | Low | `mixins.json` | integrations/mixins |
| RS-106 | KubeJS level-up hook is registered as a client-only event and consulted only on the client | Medium | High | Scripted gating is bypassable and absent on servers | Medium | `CustomEvents.java` | integrations/mixins |
| RS-107 | mods.toml declares 7 optional dependencies for ~40 integrated mods, including one whose load… | Medium | High | Load-order-dependent integration failures | Low | `mods.toml` | integrations/mixins |
| RS-108 | PowerEventDispatcher is gated on Iron's Spells, so every Power is dead in packs without it —… | Medium | High | Whole feature silently absent | Medium | `RunicSkills.java` | integrations/mixins |
| RS-109 | libs/l2tabs-0.3.3.jar is a hand-written API stub, so the L2Tabs call site is never type-chec… | Medium | High | Reproducibility; runtime `NoSuchMethodError` risk | Low | `build.gradle` | integrations/mixins |
| RS-110 | /skills bakes skillMaxLevel into its argument bounds at command-registration time | Medium | High | Medium | Low | `SkillLevelCommand.java` | build/tests/docs |
| RS-111 | Public-API event javadoc contradicts the actual firing order | Medium | High | Medium | Low | `SkillLevelUpEvent.java` | build/tests/docs |
| RS-112 | icons/ at the repo root is a stale, content-diverged duplicate of the shipped perk textures | Medium | High | Medium | Low | `—` | build/tests/docs |
| RS-113 | A third-party mod's texture is committed at the repository root | Medium | High | Medium | Low | `—` | build/tests/docs |
| RS-114 | There is no Forge data generation, despite a runData run configuration and a generated-resou… | Medium | High | Medium | Medium | `build.gradle` | build/tests/docs |
| RS-115 | CI does not run a dedicated-server smoke test, does not publish test results, and misdescrib… | Medium | High | Medium | Medium | `—` | build/tests/docs |
| RS-116 | gradle.properties carries eight dead properties, two of which contradict build.gradle | Medium | High | Low | Low | `gradle.properties` | build/tests/docs |
| RS-117 | mods.toml is unmodified MDK boilerplate: no issue tracker, homepage, logo, or update JSON | Medium | High | Medium | Low | `mods.toml` | build/tests/docs |
| RS-118 | The update checker's repository URL contradicts every documented URL | Medium | Medium | Medium | Low | `RunicSkills.java` | build/tests/docs |
| RS-119 | CHANGELOG.md is out of chronological order, has four [Unreleased] sections, and omits shippe… | Medium | High | Low | Medium | `CHANGELOG.md` | build/tests/docs |
| RS-120 | Command feedback is unlocalised and uses the wrong Brigadier feedback channel in five of nin… | Medium | High | Medium | Low | `GlobalLimitCommand.java` | build/tests/docs |
| RS-121 | SkillArgument performs no validation and suggests a hardcoded list | Medium | High | Medium | Low | `SkillArgument.java` | build/tests/docs |
| RS-122 | All nine commands register unnamespaced top-level literals that collide with other mods | Medium | High | Medium | Medium | `PlayerLifecycleHandler.java` | build/tests/docs |
| RS-123 | No player-accessible read commands; all nine are gated at permission level 2 | Medium | High | Medium | Low | `—` | build/tests/docs |
| RS-124 | No administrative or diagnostic commands exist for the most common support workflows | Medium | High | Medium | Medium | `—` | build/tests/docs |
| RS-125 | /registeritem takes an unbounded, unvalidated integer and mutates server config on the game … | Medium | High | Medium | Low | `RegisterItem.java` | build/tests/docs |
| RS-126 | HandlerCommonConfig: 1,103 public mutable fields in a single 4,836-line class | Medium | High | Medium | High | `HandlerCommonConfig.java` | build/tests/docs |
| RS-127 | Progression mutation sites update different dependent systems; four of nine are incomplete | Medium | High | Medium | Small | `—` | cross-system |
| RS-128 | /respec residue: eight kinds of state survive a "full reset" | Medium | High | Medium | Small | `RespecCommand.java` | cross-system |
| RS-129 | treasureHunterProbability = 0 means "always", and a negative value means "never" — silently | Medium | High | Medium | Trivial | `TreasureHunterPerk.java` | cross-system |
| RS-130 | One unparseable item id in treasureHunterItemList leaves a null in the drop table and NPEs o… | Medium | High | Medium | Trivial | `TreasureHunterPerk.java` | cross-system |
| RS-131 | Six of eight PowerRuntime.clear() methods mutate their map outside the lock every other acce… | Medium | High | Medium | Trivial | `PowerRuntime.java` | cross-system |
| RS-132 | Static tick baselines are not reset when the server stops, disabling pruning and rate limiti… | Medium | Medium | Medium | Trivial | `CombatEventHandler.java` | cross-system |
| RS-133 | Version coherence: three schemas, one of them enforced, none of them linked | Medium | High | Medium | Moderate | `ServerNetworking.java` | cross-system |
| RS-134 | Five registry caches are memoised for the JVM lifetime and never invalidated | Medium | Medium | Medium | Small | `RegistryPerks.java` | cross-system |
| RS-135 | /title … unset leaves the player wearing a title they no longer hold | Medium | High | Low | Trivial | `TitleCommand.java` | cross-system |
| RS-136 | libs/legendarytabs-1.20.1-1.1.3.1.jar redistributes a third-party mod jar in-repo without it… | Low-Medium | High | Licence hygiene, repo weight, staleness | Low | `build.gradle` | integrations/mixins |
| RS-137 | Compile scopes: BetterCombat, KubeJS/Rhino/Architectury, Curios and YACL are on implementati… | Low-Medium | High | Dev/runtime confusion, hidden hard deps | Low | `build.gradle` | integrations/mixins |
| RS-138 | MixItemStack globally rewrites every enchantment tooltip line for every item in the pack | Low-Medium | High | Tooltip mods, all modded enchantments | Low | `MixItemStack.java` | integrations/mixins |
| RS-139 | Fragile vanilla/registry assumptions in perk effects | Low-Medium | High | Wrong behaviour with modded content | Medium | `PerkEffectsHandler.java` | integrations/mixins |
| RS-140 | LivingEquipmentChangeEvent handlers force-drop armour, and two independent handlers do it | Low-Medium | Medium-High | Item loss / fights with equipment mods | Medium | `InteractionEventHandler.java` | integrations/mixins |
| RS-141 | Static ThreadLocal memo retains a strong Player reference; can return post-invalidation capa… | Low | High | Low | Trivial | `SkillCapability.java` | persistence |
| RS-142 | Type-mismatched NBT resolves to 0, not the documented default | Low | High | Low | Trivial | `SkillCapability.java` | persistence |
| RS-143 | Expired power cooldowns/windows never pruned; round-trip through NBT forever | Low | High | Low | Trivial | `SkillCapability.java` | persistence |
| RS-144 | Registry names are path-only — cross-namespace entries collide in NBT keys and name caches | Low | High | Medium | Moderate | `Perk.java` | persistence |
| RS-145 | PlayerEvent.Clone infers death from health instead of isWasDeath(), and force-sets health | Low | High | Low | Trivial | `PlayerLifecycleHandler.java` | persistence |
| RS-146 | Player level values never clamped on load; addSkillLevel can overflow | Low | High | Low | Trivial | `SkillCapability.java` | persistence |
| RS-147 | Title written into the vanilla CustomName player NBT field | Low | High | Low | Small | `RegistryTitles.java` | persistence |
| RS-148 | Transient per-entity state in PowerRuntime.TargetTags / SummonRegistry leaks for non-player … | Low | High | Low | Small | `PowerRuntime.java` | persistence |
| RS-149 | Iron's Spellbooks per-player state maps never cleaned up | Low | High | Low | Trivial | `IronsSpellbooksIntegration.java` | persistence |
| RS-150 | Rate limiting runs inside enqueueWork — a flood still allocates and queues main-thread tasks… | Low | Medium | Medium | Small | `PacketRateLimiter.java` | networking |
| RS-151 | OpenEnderChestSP opens a container without alive/spectator/sleeping checks | Low | Medium | Low | Trivial | `OpenEnderChestSP.java` | networking |
| RS-152 | S2C packet classes reference net.minecraft.client.* in handler bodies — safe today, but by a… | Low | High | Low | Small | `PlayerMessagesCP.java` | networking |
| RS-153 | DynamicConfigSyncCP encodes 16 arrays into one writeUtf bounded at 32767 chars | Low | Medium | Low | Small | `DynamicConfigSyncCP.java` | networking |
| RS-154 | CONVERGENCE and LIMIT_BREAKER compare the roll to 1 instead of 0, so probability 1 never procs | Low | High | Low | Trivial | `CraftingEventHandler.java` | events/XP |
| RS-155 | TRACKING re-applies a GLOWING effect on every single hit with no chance gate | Low | High | Low | Trivial | `PerkEffectsHandler.java` | events/XP |
| RS-156 | SkillLevelUpEvent cancellation returns without resyncing the client | Low | High | Low | Trivial | `SkillLevelUpSP.java` | events/XP |
| RS-157 | ExperienceMath.sum overflows int at extreme configured skill/player levels | Low | High | Low | Trivial | `ExperienceMath.java` | events/XP |
| RS-158 | The entity_reach passive UUID is duplicated as a string literal in a mixin | Low | High | Silent feature breakage on refactor | S | `MixTargetFinder.java` | progression |
| RS-159 | Dead Power runtime subsystems with unbounded-growth shapes | Low | High | Dead code; latent memory leak if wired up | S | `PowerRuntime.java` | progression |
| RS-160 | 471 hand-written perk declarations over 1143 config fields, with unused rank machinery | Low | High | Maintenance cost; 149 inert perks | L | `RegistryPerks.java` | progression |
| RS-161 | 470 of 471 perk RegistryObjects are nullable statics guarded only by convention | Low | High | One missed guard = NPE in a hot path | M | `RegistryPerks.java` | progression |
| RS-162 | Tooltips are rendered inside the item loop on the detail and titles pages, so later draws pa… | Low | Medium | Tooltip clipped/overdrawn by icons and chrome drawn after the hovered element | Low | `RunicSkillsScreen.java` | client/UI |
| RS-163 | RenderSystem.enableBlend() is called without a matching disableBlend() in five places | Low | High | GL blend state leaks to whatever renders next | Trivial | `—` | client/UI |
| RS-164 | The Runic Skills tab strip is clipped off the top of the screen at the minimum GUI height | Low | High | Top ~5 px of the tab row is cut off for players at small window sizes / high GUI scale | Low | `DrawTabs.java` | client/UI |
| RS-165 | Three HUD overlays have hard-coded positions and no on/off or reposition config | Low | High | Guaranteed collision with other centred HUD elements; no accessibility escape hatch | Medium | `OverlaySkillGui.java` | client/UI |
| RS-166 | PowersScreen scroll offset is unbounded upward, creating a scroll dead-zone | Low | High | After over-scrolling, the list does not move until the same number of notches are scrolled back | Trivial | `PowersScreen.java` | client/UI |
| RS-167 | mouseScrolled on the Titles page swallows scroll events even when the list fits | Low | High | Scroll input is consumed with nothing to scroll | Trivial | `RunicSkillsScreen.java` | client/UI |
| RS-168 | Number formatting follows the JVM default locale, not the Minecraft language setting | Low | High | Decimal separators (and, on some JVM locales, digits) disagree with the selected in-game language | Low | `—` | client/UI |
| RS-169 | Utils.intToRoman throws ArrayIndexOutOfBoundsException outside 0..3999 | Low | High | Crash in a tooltip/render path if a perk ever exposes a rank or boost ≥ 4000 or < 0 | Trivial | `Utils.java` | client/UI |
| RS-170 | Utils.checkMouse uses inclusive bounds on both edges, so adjacent tabs overlap by one pixel | Low | High | A 1-px column belongs to two tabs; the earlier one in the loop wins | Trivial | `Utils.java` | client/UI |
| RS-171 | en_us.json contains eight duplicate keys, and every locale ships nine stale keys | Low | High | Silent last-wins parsing; dead weight in every language file | Trivial | `en_us.json` | client/UI |
| RS-172 | The GUI never shows earned global level or the perk-swap cooldown, both of which gate actions | Low | High | Two of the server's gating rules are invisible until they block the player | Medium | `RunicSkillsScreen.java` | client/UI |
| RS-173 | MixPlayerRenderer allocates and re-translates the title component for every player, every frame | Low | High | Per-entity per-frame allocation in the entity render path; fragile equality check | Low | `MixPlayerRenderer.java` | client/UI |
| RS-174 | -1 as "disable this perk" is undocumented and contradicted by the field's own UI range | Low | High | Pack authors can't discover the feature; the UI won't let them use it | S | `RegistryPerks.java` | config |
| RS-175 | Dead config code: ItemListGroup, HandlerSkill.defaultLockItemList | Low | High | Noise; `checkSidedImports` maintains an allowlist entry for an unused class | S | `ItemListGroup.java` | config |
| RS-176 | ConfigHolder.save() does not fsync; .tmp/.invalid siblings are not namespaced | Low | High | Rare data loss on power failure; stray files in the config directory | S | `ConfigHolder.java` | config |
| RS-177 | Title.tooltip() reads a ModConfig.Type.CLIENT value from a common-package class | Low | Medium | Potential `IllegalStateException` if ever reached server-side | S | `Title.java` | config |
| RS-178 | RunicSkillsMixinPlugin gates only 3 of 14 mixins and has no version/config awareness | Low | High | Missed opportunity, not a live defect | Low | `RunicSkillsMixinPlugin.java` | integrations/mixins |
| RS-179 | The mod defines two block tags it never consumes, and consumes none of its own item tags | Low | High | Missing the primary pack-author extension point | Medium | `RegistryTags.java` | integrations/mixins |
| RS-180 | FTB Quests: full quest-tree walk on every progression mutation | Low | High | Server tick cost on large packs | Low | `FTBQuestsIntegration.java` | integrations/mixins |
| RS-181 | RegistryPerks/RegistryPassives conditional null RegistryObjects require a null check at ~1,0… | Low | High | Latent NPE surface | High | `RegistryPerks.java` | integrations/mixins |
| RS-182 | Unused and over-broad Maven repositories | Low | High | Low | Low | `build.gradle` | build/tests/docs |
| RS-183 | Twenty-one unused imports, plus two fixed-size Arrays.asList() config defaults | Low | High | Low | Low | `HandlerCommonConfig.java` | build/tests/docs |
| RS-184 | org.gradle.daemon=false makes every local build pay full JVM + ForgeGradle startup | Low | High | Low | Low | `gradle.properties` | build/tests/docs |
| RS-185 | Repository hygiene: duplicate documents, an internal AI prompt, and 430 KB of unorganised ro… | Low | High | Low | Low | `—` | build/tests/docs |
| RS-186 | AUDIT.md, VERIFICATION.md, FOLLOW_UPS.md, and CLAUDE.md are stale against 1.6.1 | Low | High | Low | Low | `FOLLOW_UPS.md` | build/tests/docs |
| RS-187 | README.md's installation and protocol sections cite three wrong versions | Low | High | Low | Low | `README.md` | build/tests/docs |
| RS-188 | RegistryPerks: 4,419 lines and 472 registrations in one class | Low | High | Low | High | `RegistryPerks.java` | build/tests/docs |
| RS-189 | Five methods exceed 100 lines, three exceed 170 | Low | High | Low | Medium | `CombatEventHandler.java` | build/tests/docs |
| RS-190 | Component.translatable(...) on the server for values sent into client-facing text | Low | Medium | Low | Low | `PowersCommand.java` | build/tests/docs |
| RS-191 | KubeJS perk/passive registration is dead API | Low | High | Low | Small | `Perk.java` | cross-system |
| RS-192 | betterCombatEntityRange is dead state that is persisted and re-sent on every mutation | Low | High | Low | Trivial | `SkillCapability.java` | cross-system |
| RS-193 | Capability eagerly constructed for every Player, defeating the lazy wrapper | Enhancement | High | Low | Trivial | `PlayerLifecycleHandler.java` | persistence |
| RS-194 | No search or filter on the perk/passive detail page (471 perks, 38 passives across 12 skills) | Enhancement | High | Finding a specific perk means paging through a 5×4 icon grid with only three sort orders | Medium | `RunicSkillsScreen.java` | client/UI |
| RS-195 | The Skills keybind opens but cannot close the screen | Enhancement | High | Minor friction; inconsistent with vanilla `E` | Trivial | `RunicSkillsClient.java` | client/UI |

---

*Audit produced by static analysis of Runic Skills 1.6.1 at commit `0b8be65` plus uncommitted working-tree changes. 195 findings across nine independent audit passes and one cross-system reconciliation pass. No repository files were modified.*
