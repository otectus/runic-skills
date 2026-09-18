# Runic Skills: Compatibility, Spellbook Progression, Apprentice’s Codex, and Automatic Gates

**Implementation specification for a coding agent**  
Prepared: 16 September 2026  
Target: Minecraft 1.20.1, Forge 47.x, Java 17

## 1. Mission and implementation baseline

Implement four connected improvements:

1. Diagnose and resolve the reported incompatibility with **Bigger Stacks**, including coexistence with Pack Mule and preservation of existing large stacks.
2. Replace the weak tier classification of **Iron’s Spells ’n Spellbooks** books with meaningful, explainable progression.
3. Add optional, substantive support for **Apprentice’s Codex**, covering its spells, equipment, spellbooks, utility blocks, and unusual casting paths.
4. Add an **automatic gate system, enabled by default and configurable**, that discovers relevant installed content and estimates suitable requirements from native metadata, established progression, comparable content, and bounded inference.

Treat automatic gating as a conservative extension of the existing lock system. Use the same authoritative decisions for gameplay, tooltips, commands, and previews. Preserve pack overrides, explicit exemptions, skill progression, item ownership, and existing integrations.

### 1.1 Use the newer branch

The repository’s default branch is misleading for this task:

| Branch | Inspected commit | Declared version | Significance |
| --- | --- | --- | --- |
| `main` | `6b52ec2d530af9019d845bde873913bec315d76d` | **2.2.0** | Baseline for this specification; includes progression, Pack Mule, and compatibility changes. |
| `master` | `ee17728ea6eacb56d2651d825c9c656fb6d729cb` | 2.1.1 | Older parent of the newer implementation; still the GitHub default. |

The 2.2.0 source pins Forge **47.4.23**, uses a default skill cap of **32**, and declares Runic Skills network protocol **16**. Start from the latest applicable descendant of `main`. Recheck branches, version, release artifacts, and local `AGENTS.md` instructions before editing. Do not accidentally implement this against the older default checkout. Source: [2.2.0 commit](https://github.com/otectus/runic-skills/commit/6b52ec2d530af9019d845bde873913bec315d76d), [build properties](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/gradle.properties).

Choose the next release number after that check. This is substantial enough for a feature release; do not overwrite or reuse a published version.

### 1.2 Evidence boundaries

This plan is grounded in source inspection. It does **not** claim that the reporter’s exact crash has been reproduced or that the proposed integration has passed a game launch. The feedback supplied neither a Bigger Stacks version nor logs, and did not name the troublesome books.

Proceed with the concrete source findings below. Obtain the reporter’s exact versions/logs when available, but do not make all implementation work depend on them. Record separately:

- **Observed:** behavior directly present in the inspected source.
- **Likely:** a diagnosis supported by overlapping implementations, requiring reproduction.
- **Proposed:** new behavior and balance decisions specified here.
- **Verified:** behavior demonstrated by tests against identified artifacts.

Upstream branches are source references, not proof that a released JAR contains identical code. Record checksums and verify artifact/source correspondence for every integration profile used for acceptance.

## 2. Findings that determine the design

### 2.1 Runic Skills already contains much of the necessary infrastructure

All Runic Skills Java paths below are relative to `src/main/java/com/otectus/runicskills/`.

| Existing component | Observed behavior | Required use in this work |
| --- | --- | --- |
| `handler/HandlerSkill.java` | Builds an immutable, revisioned snapshot with rules, sources, audit entries, and matching gameplay configuration. Configured entries precede generated providers. | Extend the snapshot and publication path; avoid a second unsynchronized gate database. |
| `integration/lock/LockProviderRegistry.java` | Registers generated item providers and separate live stack providers; stack providers are consulted before the ID fallback. | Integrate discovery and metadata adapters while making explicit-rule precedence consistent across both paths. |
| `integration/lock/LockItemProvider.java`, `StackLockProvider.java` | Separate bulk ID generation from decisions requiring a concrete stack. | Retain this useful distinction. Spell level, native materials, and player-linked books cannot always be reduced to an item ID. |
| `integration/lock/LockAction.java` | Existing actions are `USE`, `ATTACK`, `EQUIP`, `CRAFT`, `TAKE`, and `MINE`. | Add typed block/cast contexts without silently changing existing action meanings. |
| `config/models/LockItem.java` | Supports explicit `Allow`, source metadata, and skill vectors. | Preserve explicit unrestricted rules; never reinterpret a legacy empty/invalid vector as an exemption. |
| `integration/lock/LockAudit.java` | Existing inspection/audit infrastructure. | Extend it with candidate evidence, rejected alternatives, confidence, and typed targets. |
| `common/capability/SkillCapability.java` | Central item/block/ID checks plus stack-aware dispatch. | Keep the public entry points and delegate to one shared resolver. |
| `integration/tconstruct/TConstructRequirementResolver.java` | Explicit native rules, configured ID precedence, material-aware generation, and uncertainty handling. In 2.2.0 the automatic material profile is enabled by default; saved opt-outs remain meaningful. | Preserve this behavior. The generic engine must not override native Tinkers’ material decisions. |
| `network/packet/client/ConfigSyncCP.java` | Revisioned chunks, bounded assembly, and installation of matching config and item rules. | Extend the established protocol for typed/action-specific gates, with byte bounds and complete publication. |
| `StackRequirementsCP`, `InspectStackSP`, `client/tooltip/StackRequirementTooltip.java` | Server-computed stack inspection tied to menu/slot/request/state and a stack fingerprint. | Reuse the request pattern, adding rules revision and relevant native state where missing. |
| `handler/HandlerCurios.java` | Curios equip refusal already calls the skill capability. | Reuse and strengthen this seam; test existing equipment after reload/respec as well as new equips. |

Sources: [HandlerSkill](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/handler/HandlerSkill.java), [provider registry](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/integration/lock/LockProviderRegistry.java), [Tinkers’ resolver](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/integration/tconstruct/TConstructRequirementResolver.java).

### 2.2 Bigger Stacks: overlapping representation changes

Observed in Runic Skills 2.2.0:

- `MixFriendlyByteBuf` writes ordinary counts as a byte, but uses a `-128` sentinel followed by a VarInt for counts above 127. Its reader enforces that representation.
- `MixItemStack` redirects loading of the `Count` field and writes an integer `Count` tag above 127. The same class also contains unrelated durability and enchantment-display behavior.
- `StackCapacityMath.MAX_SERIALIZED_COUNT` is **1,048,576**. `checkedCount` rejects values outside `1..1,048,576`.
- `StackDataRecovery` treats larger integer counts as unsupported. This is an infrastructure limit, independent of whether the player owns Pack Mule.
- `PlayerStackPolicy.eligible` requires `stack.getMaxStackSize() == 64`; Pack Mule’s three capacities are 128, 192, and 256 for eligible player-carried stacks. External changes to the reported maximum affect eligibility.
- These representation hooks are registered independently of the runtime `enablePackMule` setting. Turning off the perk does not remove already-applied mixins.

Sources: [buffer mixin](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/mixin/MixFriendlyByteBuf.java), [item-stack mixin](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/mixin/MixItemStack.java), [capacity math](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/common/inventory/StackCapacityMath.java), [player stack policy](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/common/inventory/PlayerStackPolicy.java).

Bigger Stacks supports configurable limits far above vanilla. Its maintainer has documented another mod conflict caused by competing `FriendlyByteBuf` count changes and recommended disabling the overlapping mixin when Bigger Stacks is installed. That is strong evidence for the failure family, **not proof of this reporter’s exact failure**. The official source is on Codeberg; direct source retrieval was unavailable during this review. Inspect the actual supported JAR/source before choosing its serialization adapter. Sources: [Bigger Stacks project](https://www.curseforge.com/minecraft/mc-mods/bigger-stacks), [maintainer’s compatibility report](https://github.com/MOAKIEE/ae2overclocked/issues/4).

### 2.3 Iron’s books: the current fallback collapses distinct equipment

`IronsSpellbooksLockProvider` scans only `irons_spellbooks` and classifies paths using keywords. `bookTier` returns **8** when no tier keyword matches. At multiplier 1, this becomes **Magic 8 / Intelligence 5**.

The following book IDs from the inspected Iron’s 3.16.3 source all reach that fallback in Runic Skills: `rotten_spell_book`, `blaze_spell_book`, `dragonskin_spell_book`, `druidic_spell_book`, `villager_spell_book`, `ice_spell_book`, and `evoker_spell_book`. This reproduces the classification defect without needing the reporter’s list. It does not identify the books in that particular report.

The inspected Iron’s item registrations give many materially different books the **same `Rarity.UNCOMMON`**, despite different slot counts and attributes. Replacing keyword tiering with item rarity alone would preserve the defect in another form. Sources: [Runic Skills classifier](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/integration/lock/IronsSpellbooksLockProvider.java), [Iron’s 3.16.3 item registrations](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/src/main/java/io/redspace/ironsspellbooks/registries/ItemRegistry.java).

### 2.4 Spell gating is already present, but separate from book gating

`IronsSpellbooksIntegration.onSpellPreCast` currently applies:

```text
required Magic = integer truncation of (base + (spellLevel - 1) * scale)
defaults: base = 4, scale = 2
```

It then checks an explicit spell ID through `canUseSpecificID`. The automatic formula runs first, so an explicit lower spell rule or unrestricted ID does not inherently replace that formula. `ironsEnableSchoolGating` controls the formula independently of `enableItemLocks`; preserve the existing configuration meanings during migration and resolve their composition deliberately.

The event is not restricted to the base mod’s namespace. Addon spells reaching the ordinary Iron’s pre-cast event can already encounter this formula. Do not advertise namespace discovery alone as newly implemented casting support. Source: [Iron’s integration](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java).

### 2.5 Apprentice’s Codex: verified namespace, dependency change, and special paths

Inspected upstream: branch `1.20.1-main`, commit **`10aa377cd732a4650f73d2878f8ce0cffbe3d673`**, declaring **0.9.7.1**. Its actual mod/registry namespace is **`apprenticecodex`**, not the repository or JAR spelling `apprentice_codex`.

That source targets Iron’s **1.20.1-3.16.3**, Iron’s Lib **1.20.1-2.1.0**, and Curios **5.14.1**. Runic Skills currently compiles against the Iron’s **3.15.0** artifact, CurseForge file **7402504**. Build a compatible runtime profile explicitly; never put two Iron’s JARs on one classpath. Sources: [Codex properties](https://github.com/hexqua/apprentice_codex/blob/10aa377cd732a4650f73d2878f8ce0cffbe3d673/gradle.properties), [Codex dependency declarations](https://github.com/hexqua/apprentice_codex/blob/10aa377cd732a4650f73d2878f8ce0cffbe3d673/src/main/resources/META-INF/mods.toml), [Runic Skills build](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/build.gradle).

The Iron’s source examined for that newer API is commit `cae63a6999e24ed3deaa012ecb8c262e0e377816`, on the unusually named `not-1.20.1` branch, whose properties declare 1.20.1-3.16.3. Select by verified version/commit rather than assuming the branch named `1.20.1-dragon` is current. Source: [Iron’s inspected properties](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/gradle.properties).

Observed special cases:

- `isekai_travel_guidebook` and `explorers_codex` extend Iron’s `UniqueSpellBook`; they are functional equipment, not ordinary documentation books.
- `ender_grimoire` and `archivists_grimoire` have specialized storage/selection behavior. A generic `SpellBook` cast or default-stack slot count does not fully describe them.
- `AbstractSpellGunItem` reaches `spell.attemptInitiateCast`, including a temporary borrowed-mana path and failure cleanup. Verify cancellation through that native path rather than adding a second mana accounting system.
- `SpellDispenserCastHelper` creates casting proxies and directly orchestrates pre-cast conditions, native callbacks, fuel/mana handling, and `onCast`. Its inspected path does not simply call the ordinary `attemptInitiateCast` event seam.

Sources: [Codex item registrations](https://github.com/hexqua/apprentice_codex/blob/10aa377cd732a4650f73d2878f8ce0cffbe3d673/src/main/java/jp/aquafactory/apprenticecodex/registry/ItemRegistry.java), [spellgun implementation](https://github.com/hexqua/apprentice_codex/blob/10aa377cd732a4650f73d2878f8ce0cffbe3d673/src/main/java/jp/aquafactory/apprenticecodex/item/spellgun/AbstractSpellGunItem.java), [dispenser casting](https://github.com/hexqua/apprentice_codex/blob/10aa377cd732a4650f73d2878f8ce0cffbe3d673/src/main/java/jp/aquafactory/apprenticecodex/block/spelldispenser/SpellDispenserCastHelper.java).

## 3. Required behavior and non-negotiable invariants

1. The server decides requirements and eligibility. Clients render authoritative information and may predict presentation only.
2. A manual rule or explicit exemption is never silently overwritten, strengthened, or filled in by inference.
3. Automatic rules are reproducible for the same installed content, effective config, datapacks, calibration data, and algorithm version. No RNG, network service, language model, or player telemetry participates.
4. Do not label a heuristic confidence score as a statistically calibrated probability.
5. Known native systems retain ownership: Iron’s owns spells/mana/cooldowns; Codex owns its inventories and casting behavior; Bigger Stacks can own count representation; Tinkers’ owns material semantics.
6. Item count, stack limit, display language, custom item name, temporary player buffs, and current player skill do not alter an inferred equipment tier.
7. Owning, transporting, retrieving, discarding, or safely unequipping an item must remain possible when a **new inferred use gate** is unmet. Preserve legacy explicit crafting restrictions unless deliberately migrated.
8. Gate a selected spell individually. One unavailable spell must not lock every spell in the same book or erase its contents.
9. Refusals happen before irreversible consumption or world effects. A canceled attempt must not consume scrolls, ammo, mana, fuel, items, durability, or cooldowns solely because of the Runic gate.
10. No stack is truncated, deleted, duplicated, silently clamped, or discarded by reconciliation, respec, reload, provider selection, or migration.
11. Optional-mod absence must work on a dedicated server. No eager resolution of optional classes from common registries, config initialization, or early mixin detection.
12. One action uses one immutable rules revision. A failed rebuild leaves the last working snapshot intact.
13. Low-confidence inference must not punish the player with a maximum-level gate. Preserve explicit rules, use a validated conservative role fallback where possible, otherwise report `UNDETERMINED` and add no new restriction.

## 4. Workstream A: Bigger Stacks compatibility

### 4.1 Reproduce before selecting the fix

Create a compatibility ledger containing Minecraft, Forge, Runic Skills, Bigger Stacks, relevant inventory mods, file IDs, filenames, checksums, configuration, and reproduction steps. Start with:

- Runic Skills alone, Pack Mule disabled and enabled at each rank.
- Bigger Stacks alone with its normal configuration and a known large-count configuration.
- Both mods, including Pack Mule disabled. This distinguishes perk arithmetic from always-active serialization hooks.
- A dedicated server with two real clients. Have one player already present with large stacks when the second joins.
- Existing saves produced by each mod separately before installing both.

The official Bigger Stacks project listed a Forge 1.20.1 build named `biggerstacks-1.20.1-2026.06.17-all.jar` during review. Verify that artifact and the reporter’s version if different. Do not assume the historical `1.0.3` build uses identical transformation logic. Source: [official files and description](https://www.curseforge.com/minecraft/mc-mods/bigger-stacks).

Inspect exported transformed classes and mixin application logs. Record all overlaps around `FriendlyByteBuf`, `ItemStack`, inventory insertion, menus, item entities, NBT, and rendering. A successful title-screen launch is insufficient.

### 4.2 Separate storage representation from the Pack Mule perk

Introduce a narrow startup-selected representation policy, for example `StackRepresentationProvider`:

| Environment | Representation owner | Runic Skills responsibility |
| --- | --- | --- |
| Supported Runic Skills-only installation | Runic Skills extended representation | Preserve the existing format and its validated bounds. |
| Verified Bigger Stacks profile | Bigger Stacks | Disable overlapping Runic network encoding; delegate native count serialization, with a tested legacy read bridge only where needed. |
| Unrecognized overlap | No claim of compatibility | Preserve recoverable data and give a precise startup diagnostic; do not guess a wire format or silently install two codecs. |

Select the provider consistently on both physical sides **before affected classes transform**. Extend `RunicSkillsMixinPlugin` using the early mod-discovery mechanism already valid at that lifecycle stage. Do not call a not-yet-initialized `ModList.get()` or read player/server gameplay config in early mixin selection.

Split count persistence/recovery hooks out of `MixItemStack` into dedicated mixins. Never skip the whole existing class merely to avoid a serialization collision: doing so would also remove unrelated durability perks and enchantment-display behavior. Likewise, split only conflicting portions of mixed-purpose slot/inventory mixins if necessary.

Do not solve collisions by increasing mixin priority, overwriting the foreign method, or setting `require = 0` everywhere. Verify exactly which hooks apply in each profile and test their behavior.

### 4.3 Network compatibility is a startup property

The representation affects ordinary Minecraft item packets, not just the Runic Skills channel. A later gameplay config packet cannot negotiate a different interpretation after item bytes have begun flowing.

- Use the loader’s dependency/handshake facilities and a compatible startup capability check before gameplay item synchronization, as supported by the tested Forge profile.
- Keep strict Runic protocol/version validation. Bump protocol 16 when this work changes its wire structures.
- Validate that the two peers select compatible representation providers and supported versions. Explain a mismatch before ordinary inventory interaction where the handshake permits it.
- A runtime Pack Mule toggle changes capacity eligibility only. It must never change the wire format.
- A future unsupported client combination must be rejected cleanly; do not heuristically decode several formats from the same byte stream.

### 4.4 Remove the accidental external count ceiling

`MAX_SERIALIZED_COUNT = 1,048,576` is a Runic representation limit, not a universal valid-item limit. Audit every `checkedCount`, invalid-count recovery call, and capacity calculation.

In Bigger Stacks mode, supported external counts must not pass through a validator that rejects them merely for exceeding Runic’s limit. Derive representation bounds from the verified provider. Keep **three different concepts** explicit:

1. What the persistence/network representation can losslessly represent.
2. What a particular destination accepts now.
3. What Pack Mule grants an eligible player.

Use `long` for intermediate arithmetic and inventory conservation assertions. Validate before narrowing to `int`. Iteration must be bounded by slots/records/work budget, never by the number of individual items in a large stack. Limit packet bytes and container sizes independently of the item count.

A large count that is valid for the selected provider is not corrupt data. A truly unsupported saved record remains recoverable with an explicit diagnostic; do not log an export and then erase the only in-world ownership record as if the operation succeeded.

### 4.5 Default coexistence policy for Pack Mule

Use a conservative **external-policy-first** default:

- If Bigger Stacks already expands an item beyond 64, preserve that native limit. Pack Mule does not multiply it again.
- Respect explicit foreign lower limits, exclusions, specialized slots, and nonstackable items. Do not interpret an externally stackable sword, spellbook, or container as an ordinary Pack Mule candidate.
- If an otherwise eligible ordinary item remains at 64, apply the existing 128/192/256 player-only perk only when the tested provider can support that destination adjustment without a conflicting transform. Otherwise defer the perk for that provider and report the reason in diagnostics; release acceptance should resolve this path where feasible.
- Chests, hoppers, machines, armor slots, Curios, and foreign containers retain their own destination rules. Do not globally mutate the `Item` singleton.
- The serializer must remain able to carry an already-owned stack larger than today’s destination capacity after a perk/config change.

Do not introduce a multiplier-on-top-of-Bigger-Stacks mode in the initial implementation. It would make progression, overflow, and provider limits harder to reason about without satisfying an additional user requirement.

The eligibility adapter must distinguish native item suitability from the externally adjusted current maximum. When metadata is unavailable, use an explicit validated allow/exclusion policy and decline uncertain Pack Mule expansion. Never infer container safety solely from `getMaxStackSize()`.

### 4.6 Persistence and migration

Document and test a format matrix for: vanilla byte `Count`, Runic integer `Count`, and the actual tested Bigger Stacks format. Do not invent the foreign NBT key or assume integer `Count` compatibility.

Where a read bridge is necessary:

1. Recognize the format using unambiguous metadata/type checks.
2. Read the complete original item and capability payload.
3. Convert exactly once into the selected provider’s representation without changing ownership or item contents.
4. Make retries/reloads idempotent.
5. Keep migration recovery records tied to a stable operation/owner identity so replay cannot duplicate a previously restored stack.

Reconciliation should fill legal destination space and retain remaining excess in the owned source when inventory space is exhausted. Existing canceled-drop recovery must also use provider-compatible serialization. Review cursor stacks, offhand, death drops, clone handling, and canceled item-toss events.

Clarify that `/skills ... normalize` using the **current native maximum** does not automatically make a save safe for removing Bigger Stacks. A removal-preparation command, if supplied, must target the post-removal representation and all relevant storage, and report unresolved locations rather than claiming universal readiness after scanning only online inventories.

### 4.7 Required acceptance for this workstream

- Both mods start, join, save, restart, and reconnect with Pack Mule on and off.
- Test counts **1, 16, 63, 64, 65, 127, 128, 192, 255, 256, 257, 1,048,576**, and a supported external count above **1,048,576**. Add the actual provider limit and its invalid boundary where practical using synthetic codec tests.
- Ordinary click, shift-click, drag split, double-click collection, number-key swap, offhand swap, crafting output, drop/pickup, hopper transfer, death/respawn, creative transitions, and a full inventory conserve quantities.
- Main inventory, cursor, storage, NBT, and packet round trips agree on counts and item/capability data.
- An external reduced limit is respected; an external expanded limit is not capped at 256 or 1,048,576.
- Runic Skills-only Pack Mule still grants 128/192/256 correctly.
- Changing a gate or denying an action while the relevant stack is oversized preserves the whole stack.

## 5. Workstream B: meaningful Iron’s spellbook progression

### 5.1 Separate the book chassis from its contents

Resolve two different questions:

- **Equipment requirement:** may this player use/equip this book and receive its native equipment benefits?
- **Spell requirement:** may this player cast the selected spell at the selected native level through the current source?

An ordinary book’s equipment gate depends on its durable capabilities: native capacity, built-in attributes, intrinsic functions, and curated progression. Adding or removing an ordinary inscribed spell must not raise/lower that equipment gate. Fixed preset content may inform the curated tier of a unique book, but its individual spells still have individual cast gates.

Locked contents stay stored, visible, selectable for inspection, removable, and transferable where the upstream mod permits. Never strip spells, reroll them, downgrade them, or prevent retrieval of another owned item.

### 5.2 Native metadata adapter

Create an optional Iron’s metadata adapter that can identify:

- The spellbook role through supported interfaces/classes, including addon subclasses outside `irons_spellbooks`.
- Native base capacity through the supported book API, and specialized handling for books whose effective capacity is held elsewhere.
- Intrinsic max mana, cooldown/cast-time modifiers, general/school spell power, defensive modifiers, and fixed tradeoffs.
- Fixed preset versus freely inscribable spells.
- Supported upgrade recipes and curated family links.

In the inspected Iron’s 3.16.3 source, `SpellBook` exposes `getMaxSpellSlots()`, and `ISpellbook` identifies the role. These are starting points, not permission to assume every addon implements the concrete class. Query static metadata or a detached inspection stack; never initialize/mutate the player’s real spell container to classify it. Exclude player buffs, enchantment-driven rarity inflation, current damage, and unrelated affix rolls from baseline tiering. Source: [SpellBook implementation](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/src/main/java/io/redspace/ironsspellbooks/item/SpellBook.java).

### 5.3 Initial curated balance table

The following is a **proposed release starting point at skill cap 32 and multiplier 1**, not a claim about existing requirements. Verify each row against the supported artifact and recipes. Keep the final table data-driven and include the reason for every change in the audit export.

Namespace for these rows: `irons_spellbooks`.

| Book path | Current generated Magic / Intelligence | Proposed Magic / Intelligence | Balance rationale |
| --- | --- | --- | --- |
| `wimpy_spell_book` | 4 / 2 | 4 / 2 | Preserve the entry-level item’s modest restriction; inspect its actual intended role and empty capacity. |
| `copper_spell_book` | 8 / 5 | 8 / 5 | Low-tier capacity anchor. |
| `iron_spell_book` | 10 / 6 | 10 / 6 | Preserve a clear step above copper. |
| `gold_spell_book` | 12 / 7 | 12 / 7 | Intermediate capacity and casting benefits. |
| `diamond_spell_book` | 14 / 8 | 16 / 10 | Higher capacity and mana benefit. |
| `legendary_spell_book` | 18 / 11 | 18 / 11 | High-capacity generic book; its name alone does not outrank specialized books. |
| `netherite_spell_book` | 18 / 11 | 22 / 14 | High capacity plus substantial mana and cooldown benefits. |
| `rotten_spell_book` | 8 / 5 | 14 / 8 | Intermediate capacity/mana with a defensive drawback; price the tradeoff rather than treating it as pure upside. |
| `blaze_spell_book` | 8 / 5 | 20 / 12 | Advanced fire-oriented casting package. |
| `druidic_spell_book` | 8 / 5 | 20 / 12 | Comparable nature-oriented specialization. |
| `villager_spell_book` | 8 / 5 | 22 / 13 | Strong combined casting attributes. |
| `ice_spell_book` | 8 / 5 | 24 / 14 | High capacity and specialized casting benefits. |
| `dragonskin_spell_book` | 8 / 5 | 26 / 16 | High-capacity ender specialization with advanced acquisition to verify. |
| `evoker_spell_book` | 8 / 5 | 22 / 14 | Unique preset casting package; inspect total usable capacity rather than one constructor argument. |

The seven fallback rows were checked against the existing classifier. The current numbers assume generated rules win: a pack’s manual overrides may already change them. Metadata/attribute rationale comes from the inspected [Iron’s item registrations](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/src/main/java/io/redspace/ironsspellbooks/registries/ItemRegistry.java). Treat recipe/acquisition ranking as a balance hypothesis until the supported artifact’s recipes are verified.

Allow equal gates for legitimate sidegrades. Require monotonic progression only along **verified upgrades** or genuine dominance relationships. Capacity alone is not dominance: a larger book may lose mana or another useful attribute. For a direct upgrade that retains the same role and adds a meaningful benefit without a compensating loss, its primary requirement should normally increase by at least one attainable level.

### 5.4 Migration and scaling

- Existing explicit IDs and `Allow` entries win unchanged.
- Replace the Iron’s provider’s generated defaults; do not append a second competing default provider.
- Do not overwrite saved manual entries merely because they equal an old shipped default. Existing value-equality provenance is ambiguous; preserve ambiguous values and report them.
- Honor `ironsLevelMultiplier` exactly once.
- Honor the existing generated-lock scaling setting exactly once; do not multiply in the adapter and again in `HandlerSkill`.
- Keep reference requirements and final effective requirements distinct in the audit output.
- Where a reduced skill/global cap makes a generated vector unattainable, lower or remove only generated requirements under the policy in §9. Never silently rewrite an explicit pack challenge.
- Keep a documented legacy book-profile option for packs that need the previous generated balance while adopting the compatibility fixes. Default to the improved profile.

### 5.5 Presentation and enforcement

Show book equipment requirements separately from the selected spell’s cast requirements. Reuse Curios’ equip-denial integration and the central action checks. For an already-equipped book that becomes unavailable after reload/respec, suppress unauthorized use and native equipment benefits through supported seams, or safely return the item to player inventory with lossless overflow handling. Leaving its mana/cooldown bonuses active while only hiding casting is incomplete enforcement.

Audit hand casting, Curios keybind casting, scrolls, spell wheels, inscription menus, right-click equip, quick-equip, and slot swaps. Prefer a cancelable upstream event. Where the pre-cast event lacks the actual casting stack/slot, capture the server’s authoritative context at the native initiation boundary; do not guess from the main hand or trust a client-reported item.

Acceptance: the proposed profiles display consistently, stronger direct upgrades no longer flatten, a low-level spell remains usable in an otherwise permitted book containing an unavailable spell, and all refused routes preserve resources and book contents.

## 6. Workstream C: Apprentice’s Codex support

### 6.1 Dependency and registration contract

Add a default-enabled `enableApprenticeCodexIntegration` setting. It becomes active only when Codex and its required native dependencies are present and compatible. Its provider identifier must be stable; use the actual namespace `apprenticecodex` for content targets.

Build two Iron’s profiles where API compatibility permits:

1. The existing supported Iron’s baseline without Codex, to catch regressions in current users’ installations.
2. Codex 0.9.7.1 with the verified compatible Iron’s 3.16.3, Iron’s Lib, Curios, and animation dependencies.

Compile common Iron’s integration against the lowest API that truly supports the declared range. Isolate newer/Codex APIs in an optional adapter or distinct compatibility source set where necessary. If maintaining the old minimum is infeasible, raise the minimum explicitly and document it; do not quietly retain a false compatibility range.

Reuse the project’s existing optional integration bootstrapping, manifest, diagnostics, and runtime-profile approach. Do not add dependencies to the default runtime solely to make absence tests pass accidentally.

### 6.2 Build a complete content and action ledger

Enumerate the supported artifact’s item, block, and Iron’s spell registries after registration. For every Codex entry, record its exact ID, concrete/native role, relevant API, supported actions, rule source, resulting gate or explicit reason for exemption, and test coverage. Count aliases once and distinguish block IDs from corresponding item IDs.

The ledger must cover these groups:

| Group | Required behavior | Initial governing skills |
| --- | --- | --- |
| Regular and unique spellbooks | Native book profile, Curios/hand use, individual spell inspection and casting. | Magic; Intelligence when justified. |
| Ender/archivist storage books | Respect native player-linked storage and selected-book identity; allow safe content retrieval even below the equip gate. | Curated Magic/Intelligence profile based on the actual function. |
| Spellguns and swingcast weapons | Separate physical use from the selected spell cast; preserve native ammo, borrowed mana, timing, recoil, and failure cleanup. | Dexterity or Strength for the physical role; Magic for casting. |
| Staves, scepters, bows, shields, amplifiers | Use class/adapter metadata; support hybrid actions and offhand sources. | Role-appropriate primary skill and at most one secondary by default. |
| Curios and robes | Gate equip and active native benefits; preserve removal and inventory contents. | Magic with a modest role-specific secondary. |
| Crafting/support blocks | Gate deliberate player operation; register result slots only when crafting gates are explicitly enabled. | Tinkering for magical mechanisms, Building for construction, Magic where the operation requires it. |
| Utility and support spells | Differentiate basic convenience from advanced mobility, remote effects, bulk processing, and combat support. | Magic by default; add a secondary only through a reviewed profile. |
| Ingredients, ordinary components, decorative items | Classify and normally exempt; do not lock by namespace alone. | None. |
| Temporary spell-created blocks/entities | No extra gate on an already-authorized effect; apply the spell’s gate at initiation. | Inherit the originating spell action. |
| Spell dispensers and proxies | Explicit actor/automation policy; preserve native machine operation and resource accounting. | See §6.5. |

Current source examples include `spellcaster_workbench`, `apprentice_desk`, `spell_calibration_bench`, `spell_dispenser`, `atelier_station`, and temporary spell blocks. Do not treat every Codex block as a player-built workstation. Source: [Codex block registry](https://github.com/hexqua/apprentice_codex/blob/10aa377cd732a4650f73d2878f8ce0cffbe3d673/src/main/java/jp/aquafactory/apprenticecodex/registry/BlockRegistry.java).

### 6.3 Initial Codex balance policy

Use the following as reviewed starting profiles, then finalize against the content ledger:

- **Entry utility:** small Magic requirements, normally 4–8 at cap 32. A light or modest support spell should not require an endgame build merely because it belongs to an addon.
- **Useful exploration/support:** roughly 8–14 where native rarity, reach, persistence, or capability justify it.
- **Advanced remote, mobility, storage, or processing abilities:** roughly 14–22, based on verified behavior and existing comparable spells.
- **Exceptional effects:** roughly 22–28 only with clear native evidence or a curated rule. Avoid assigning the cap to all unfamiliar spells.
- **Specialized gear:** use the same equipment anchors as base Iron’s, with physical action requirements for hybrid weapons.

These ranges are design guidance, not automatically inferred classifications of every spell in the category. Utility is not synonymous with weak: long-range teleportation, large-scale excavation, or powerful automated production can warrant higher requirements than a simple attack.

Give `isekai_travel_guidebook` a reviewed low-entry equipment profile; it contains fixed level-one Healing Bloom and Companion Trunk spells in the inspected source. Classify it as a functional unique spellbook. `explorers_codex` also contains a native fixed spell package and a mana attribute. Inspect that package before assigning its final profile. Sources: [Isekai Travel Guidebook](https://github.com/hexqua/apprentice_codex/blob/10aa377cd732a4650f73d2878f8ce0cffbe3d673/src/main/java/jp/aquafactory/apprenticecodex/item/curios/isekaitravelguidebook/IsekaiTravelGuidebook.java), [Explorer’s Codex](https://github.com/hexqua/apprentice_codex/blob/10aa377cd732a4650f73d2878f8ce0cffbe3d673/src/main/java/jp/aquafactory/apprenticecodex/item/curios/explorerscodex/ExplorersCodex.java).

Keep reviewed Codex profiles available even when universal inference is disabled. The dedicated integration setting controls these profiles. An integration opt-out must also prevent the universal engine from recreating the same Codex-specific restrictions behind the user’s back; generic spell handling that already existed must be described separately in diagnostics.

### 6.4 Casting-path coverage

Trace each casting family to its first authoritative, cancelable point before costs and effects. Record whether it reaches `SpellPreCastEvent`, a native initiation method, or a direct callback.

For a normal player cast:

1. Resolve the real actor, native spell ID, selected native level, cast source, and authoritative equipment source.
2. Check applicable equipment-action and spell-action requirements through the shared resolver.
3. Cancel before native commitment if either is denied.
4. Let native code perform the successful cast and all costs exactly once.
5. Preserve any native failure cleanup, including borrowed mana rollback.

Use one read-only evaluation context through nested calls. Do not generate synthetic `SpellOnCastEvent` events to gain generic perk support: listeners could apply damage, resources, or rewards twice. Existing Iron’s perks should work where their native hooks fire; for alternate routes, explicitly record which effects are supported, unsupported, or require an adapter.

Test normal books, scrolls, spellguns, swingcast weapons, offhand sources, autocast/triggered curios, LONG casts, CONTINUOUS casts, instant casts, canceled casts, and cast completion after a config reload. Native spell level bonuses must not make a perk invalidate the same cast that qualified before its bonus; see §10.2.

### 6.5 Automation and proxy casting

The default policy should distinguish **a player initiating a spell through an alternate source** from **an autonomous machine executing its configured behavior**.

- Player-triggered proxy casts resolve the actual initiating player. Do not treat an empty FakePlayer skill capability as that person’s skill record.
- Default autonomous dispenser policy: **gate player placement/configuration/use of the device where applicable; let the device’s native autonomous casting rules operate without inventing a human skill level**. Mark these executions `AUTOMATION_EXEMPT` in audit information. Do not grant player skill rewards or combat credit simply by copying an owner UUID.
- Provide an optional strict owner policy for packs that want spell gates on autonomous casting. Require a verified owner association. Initially support online-owner evaluation; pause with a clear machine diagnostic when the owner cannot be resolved or is offline. Do not silently bypass the rule or synchronously load arbitrary offline player data every tick.
- If implementing persistent owner authorization later, define trusted ownership changes, rule/skill revisions, expiry, revocation, and offline behavior before using it. A cached UUID or stale allow bit is not authorization.
- Under strict policy, check instant and continuous dispenser paths before native fuel/mana consumption and before `onServerPreCast`/world effects. Check again at a defined continuous-cast authorization boundary, using one decision per interval rather than repeatedly charging or replaying effects.
- Preserve the default semantics of unrelated fake players and other machines. Narrow the adapter to supported Codex contexts.

The `SpellDispenserCastHelper` source demonstrates why the ordinary player pre-cast subscription alone is not complete coverage. Use a supported native hook if one exists; otherwise add a narrowly scoped, version-verified mixin with a real failure return. Never cancel a `void` callback after resources have already been consumed and call that a successful gate.

### 6.6 Codex acceptance

- Every supported registered item/block/spell has a ledger outcome: curated, native/generated, explicit exemption, or documented undetermined status.
- An absent Codex installation has no missing-class errors, dead UI rows, or registration failures.
- A compatible installation launches on a dedicated server and the selected artifact versions are recorded.
- Gear gates and cast gates agree across ordinary, alternate, offhand, and Curios routes.
- A failed spellgun cast restores temporary borrowed mana and consumes no unauthorized shot resource.
- Storage books retain contents and support safe retrieval while use/equip is gated.
- Dispenser default and strict-owner policies behave as documented, including offline owner, no owner, owner change, continuous cast, and reload cases.
- Existing Iron’s bonuses apply once where supported; unsupported native routes are listed rather than advertised as universal parity.

## 7. Workstream D: universal automatic gates

### 7.1 Product contract

Add `enableAutoGates = true`. For new installations and existing configs without that field, the system is on by default. Explicit saved `false` values remain false.

When enabled, it discovers relevant **items, blocks, and supported spell registries**, selects the best available evidence, and adds requirements only where higher-priority rules do not decide the same target/action. When disabled, its inferred rules disappear after a successful reload; manual rules and independently configured curated/native integrations remain active.

Display the setting as **Automatic gates for unconfigured content** with that scope in its description. Existing Iron’s, Tinkers’, and other integration toggles continue to mean what their users configured. Offer an overview showing which sources remain enabled so disabling universal inference is not confused with disabling all existing locks.

The system is a deterministic content-analysis engine. “Projected likelihood” means estimating role and progression from comparable evidence, with an explainable confidence score and explicit uncertainty. Do not introduce online AI calls, runtime training, nondeterministic predictions, or secret balance changes.

### 7.2 Discovery pipeline

Build after registries and the relevant server tags/recipes/native registries are ready:

1. Capture the effective configuration and input revisions.
2. Enumerate item and block registries in stable ID order.
3. Ask registered spell adapters to enumerate their native spell registries, independent of item registries.
4. Resolve curated rules, manual rules, exemptions, and integration ownership before inferring gaps.
5. Build immutable descriptors containing cheap native facts.
6. Extract bounded recipe/upgrade evidence where supported.
7. Classify role and action applicability.
8. Estimate a reference requirement vector and confidence.
9. Apply progression, reachability, exclusions, and conflict checks.
10. Build an immutable candidate snapshot and an audit diff.
11. Validate it; atomically publish the entire revision and synchronize clients.

Never enumerate all items, inspect recipes, instantiate block entities, or resolve optional classes during a tooltip render, inventory insertion, attack, or cast. Runtime decisions should be indexed lookups plus bounded reads of state that actually varies by stack/player.

### 7.3 Candidate identification

| Candidate | Strong signals | Weak signals that cannot decide alone | Default inferred actions |
| --- | --- | --- | --- |
| Armor | Native armor role, equipment slot, material/stat attributes. | Name suffix, creative tab, rarity color. | `EQUIP`. |
| Melee weapon | Native weapon/tool action metadata and actual attack characteristics. | `sword`, `blade`, `hammer` in a path. | `ATTACK`; `USE` only for a known active function. |
| Mining/harvesting tool | Native tool actions, tier, effective-block tags. | Material words or apparent durability alone. | `MINE`, plus verified tool-use actions. |
| Ranged weapon | Bow/crossbow or registered native firearm/weapon adapter. | `gun`, `bow`, or `rifle` substring. | `USE`/`ATTACK` as appropriate. |
| Spellbook/focus/curio | Native interface, native capacity, supported capability/attribute adapter. | `book`, `codex`, `ring`, `orb` substring. | `EQUIP`/`USE`; individual spells use `CAST`. |
| Workstation/machine | Verified block role, existing equivalent workstation tags, or registered adapter. | Merely being a `BlockEntity` or having high hardness. | `INTERACT_BLOCK`; optional `PLACE_BLOCK`. |
| Ore or harvest target | A supported harvest-role adapter and calibrated existing block requirements. | Hardness, blast resistance, dimension, or `ore` in its name alone. | `MINE_BLOCK` only when the profile has sufficient evidence. |
| Spell | A real spell-registry entry, supported metadata, selected native level, native category/rarity. | Registry path words or translated spell description. | `CAST`. |
| Ordinary materials/decorations | Known ingredient/decorative roles or exclusion tags. | High item rarity by itself. | None by default. |

Use the actual ten Runic Skills aptitude IDs. Do not invent “Mining”, “Agility”, “Defense”, or other nonexistent skills to simplify classification. Derive primary/secondary mappings from existing profiles for Strength, Constitution, Dexterity, Endurance, Fortune, Intelligence, Building, Magic, Wisdom, and Tinkering.

An ingredient named `diamond_sword_blade`, a decorative `magic_tome`, an ordinary `fishing_rod`, and a temporary spell-created light are required false-positive fixtures. Structural/native evidence must outrank broad substring matching.

### 7.4 Exclusions and bootstrap protection

Normally exclude air, creative/debug-only entries, temporary spell artifacts, ordinary ingredients, raw resources, food, common decorative blocks, basic storage retrieval, and ordinary informational books from new automatic restrictions. Existing explicit/legacy rules on these targets remain meaningful.

Provide item/block tags and exact-ID exclusions for packs. Exclusions from **inference** prevent a generated fallback; an **explicit allow rule** decides eligibility against lower-priority gate sources. These are different operations and must not be conflated.

Do not infer item pickup, storage withdrawal, dropping, safe unequip, or ordinary transportation gates. Default automatic crafting restrictions to **off**. Equipment can therefore be crafted, traded, and stored before it can be used, unless an existing explicit rule intentionally prohibits crafting.

Keep foundational ways to earn each aptitude available. Run a reachability audit for starting tools, workstations, and training routes; report cycles or isolated progression requirements. Do not silently remove intentional manual restrictions to repair such a cycle. A pack needs a specific report identifying the blocked route and the rules responsible.

## 8. Inference model: concrete and explainable

### 8.1 Descriptor model

Use a typed descriptor rather than unstructured text:

```text
ContentDescriptor
  target: kind + registry ID
  namespace, native role, subrole, supported actions
  material/tier evidence
  role-specific intrinsic stats and native progression values
  item/block tags, spell category/school metadata where meaningful
  verified upgrade relations and bounded recipe evidence
  fixed equipment functions and documented tradeoffs
  evidence provenance, missing fields, adapter version
```

Separate static descriptors from dynamic descriptors. Static equipment descriptors can be built once per rules revision. Dynamic descriptors include a Tinkers’ material set, selected spell level, or the actual selected book in an owner-linked Codex inventory. A cache key must represent every fact used by its adapter, including native capability state where NBT alone is insufficient.

Do not key dynamic inference solely by item ID. Do not cache references to mutable `ItemStack`, player, level, or registry objects across incompatible reloads.

### 8.2 Trusted calibration data

Create a versioned, reviewed calibration corpus with:

- Existing curated Runic progression anchors and verified vanilla material/role examples.
- Corrected Iron’s book profiles from §5.
- Native Tinkers’/other integration metadata where the adapter owns the inference.
- Reviewed Codex profiles and representative spells spanning capability and native rarity.
- Explicit ungated starter/ingredient examples to support exclusion and low-tier decisions.

Do **not** learn from the engine’s own previous predictions. Do not treat every old keyword-generated rule as ground truth; that would reproduce the book defect. Exclude inactive providers, stale IDs, placeholder rows, invalid vectors, and ambiguous imported defaults from automatic calibration.

Manual pack rules remain authoritative for their exact target. Learning from them is a separate option, **off by default**, because an administrator may intentionally set one exceptional item to level 100. When enabled, accept only valid opted-in reference rows and cap any one namespace/family’s influence.

Store canonical reference levels separately from already-scaled values. Never apply a multiplier or cap conversion twice through a training sample.

### 8.3 Evidence precedence within inference

Use this sequence after higher-priority explicit/curated rules are resolved:

1. **Verified native tier/progression adapter:** deterministic metadata-to-profile mapping.
2. **Trusted comparable content:** role-matched nearest neighbors with calibrated requirements.
3. **Validated role fallback:** conservative low-tier profile for a confidently identified role when tier evidence is incomplete.
4. **Undetermined:** record the candidate and add no new gate.

Path keywords may corroborate a role/tier but never supply an endgame gate on their own. Minecraft rarity is a minor feature for generic equipment; native spell rarity can be a stronger feature for spells because it describes a different system. Keep those feature types distinct.

### 8.4 Initial nearest-neighbor estimator

Implement a simple, testable estimator before considering more sophisticated models:

1. Restrict neighbors to the same target kind, compatible role, and relevant action. Compare armor within slot, books with books, and spells within a supported spell-domain profile.
2. Normalize relevant numeric features using **fixed, versioned calibration ranges** for that role. Do not renormalize all balance every time a pack installs one unusually strong item.
3. Compute weighted similarity. Starting weights for equipment: structural role/material **0.45**, intrinsic power/utility stats **0.35**, verified recipe/upgrade evidence **0.15**, bounded ID-token corroboration **0.05**. Adapters may supply reviewed domain-specific weights.
4. Missing evidence contributes no similarity and is counted in feature coverage. It must not make two poorly described items look like a perfect match.
5. Select at most **5** nearest trusted neighbors; stable ID order breaks equal-distance ties. Require at least **3 distinct trusted anchors** for ordinary neighbor-based enforcement.
6. For each relevant skill, estimate the **weighted median** of the neighbors’ reference requirements. This resists one extreme sample. A secondary skill requires support from at least 60% of the selected neighbor weight; do not union every neighbor’s skill list.
7. Retain the neighboring range, chosen anchors, similarity, feature coverage, and disagreement in the evidence record.

Initially limit ordinary inferred vectors to **one primary and one secondary skill**. Richer requirements require a curated profile or explicit rule. Hybrid items have separate action vectors, not one large combination that makes every use require every skill.

### 8.5 Confidence and abstention

Confidence describes evidence quality, not a measured chance that the gate is objectively correct. Use a deterministic starting calculation:

```text
candidateConfidence = min(
  roleConfidence,
  0.40 * featureCoverage
  + 0.35 * weightedNeighborSimilarity
  + 0.25 * neighborAgreement
)
```

All terms lie in `[0, 1]`. Define them in code and the calibration documentation:

- `roleConfidence`: 1.0 for a verified native role adapter; reviewed tag combinations may be high but lower; a name-only classifier stays below enforcement eligibility.
- `featureCoverage`: fraction of the role’s weighted expected features actually observed, including absent recipe evidence.
- `weightedNeighborSimilarity`: weighted mean similarity of the selected valid neighbors.
- `neighborAgreement`: `1 - min(1, weightedMeanAbsoluteDeviation / 8)` for reference-cap-32 primary requirements; normalize equivalents before this calculation. Store the deviation as well.

Proposed enforcement threshold: **0.75**, with role confidence at least **0.85**. These are tunable engineering defaults to validate on the corpus, not statistical claims.

| Result | Runtime behavior |
| --- | --- |
| Verified native/curated result | Use its reviewed rule with its provenance; do not manufacture a probability. |
| Neighbor estimate meets evidence/sample/threshold requirements | Apply the inferred vector, after safety/reachability checks. |
| Confident role, insufficient tier evidence | Use a separately reviewed conservative role fallback if one exists, typically a low attainable requirement; label `ROLE_FALLBACK`. |
| Unclear role, contradictory evidence, invalid metadata, or unsupported system | `UNDETERMINED`; keep existing rules and add no restriction. |

Every discoverable entry gets an outcome, but not every registry entry deserves a restriction. An exhaustive audit with explicit abstentions satisfies coverage more honestly than arbitrary gates on unrelated content.

### 8.6 Recipe and upgrade evidence

Use recipes only as bounded supporting evidence. Never evaluate arbitrary recipes by crafting them or invoke world-mutating item/block methods to discover capabilities.

- Prefer explicit smithing/upgrading relationships and reviewed native progression.
- A conversion between equivalent forms is not an upgrade.
- An expensive optional recipe does not make an item expensive if an easier legitimate recipe exists; use the lowest supported acquisition estimate and record alternatives.
- Do not infer a mandatory crafting route from an incomplete recipe list: loot, trading, world generation, and scripts may provide alternatives.
- Normalize recipe output quantity for material effort, while ignoring stack **capacity** as a power signal.
- Bound traversal depth, visited nodes, ingredient-tag expansions, and total edges. Detect strongly connected components/cycles and avoid recursive escalation.
- Recipe evidence can nudge a neighbor estimate within a small reviewed bound, such as **±2 reference levels**. It cannot convert name-only classification into high confidence.
- Honor actual datapack changes on reload. When custom recipe semantics are unsupported, omit that feature and lower coverage.

### 8.7 Calibration and evaluation

Before enabling broad enforcement in release builds, evaluate the proposed estimator against a held-out set of curated examples from namespaces not used as their own nearest neighbors. Report:

- Role false positives, especially ingredients/decorations identified as usable equipment.
- Primary requirement error and direction relative to reviewed targets.
- Coverage: curated, native, neighbor-inferred, role fallback, excluded, and undetermined.
- Verified upgrade inversions and unintended cap/global-budget violations.
- Cases where missing metadata received higher confidence than complete metadata.

Use these results to tune fixed weights and thresholds. Do not call a score of 0.9 “90% accurate.” More complex statistics are optional future work and must not block the initial transparent implementation.

## 9. Scaling, reachability, and progression consistency

Represent a result as a reference vector plus a declared scaling policy:

- `ABSOLUTE`: preserve exact configured levels.
- `REFERENCE_32`: use the existing generated-level scaling policy when enabled.
- `NATIVE_CAP_RELATIVE`: already expressed relative to the active cap; never scale again.

Keep the existing default of `scaleGeneratedLockRequirements = false` unless the current repository changes it deliberately. Enabling universal discovery is not permission to change every existing requirement’s scale.

For generated rules, apply in a documented order:

1. Produce the reference vector.
2. Apply its one integration/category multiplier.
3. Apply its one cap conversion if enabled.
4. Round using the declared policy; preserve existing provider rounding where compatibility requires it.
5. Remove no-op level-one requirements where the existing skill system starts at one.
6. Check per-skill attainability and the global level budget using the actual `ProgressionService` rules, including minimum starting levels and any relevant unlock costs.
7. If a generated vector is impossible, lower/drop its generated secondary first and then lower its primary within the available budget; record the adjustment. If no meaningful attainable requirement remains, add no gate and report why.

Do not simply clamp a manually configured level to the current cap. Report intentional or accidental impossible manual requirements for the pack author to resolve.

Run monotonicity checks on verified upgrade families after scaling. If cap compression leaves too few attainable levels to distinguish every upgrade, allow a documented tie and report it. Never exceed the cap or reorder unrelated sidegrades to manufacture uniqueness.

Changing a player’s skill level reevaluates eligibility, not content difficulty. A new player must see the same item requirement as an experienced player on the same server revision.

## 10. Spell-specific inference and gating

### 10.1 Discover native spell registries

The universal system cannot discover spells by scanning item names. Add a `SpellGateAdapter` SPI that enumerates and describes real spell definitions for each supported magic system. Iron’s is required for this release and should discover registered addon spells regardless of namespace, subject to integration opt-outs.

Preserve the existing Ars Nouveau integration. An Ars spell is often a composed sequence rather than a directly comparable Iron’s spell definition; do not assign Iron’s rarity/level math to glyph chains. Route any extension through an Ars-specific adapter and keep its current complexity gate as a compatibility fallback. Unsupported magic systems appear in diagnostics rather than being falsely declared supported.

### 10.2 Use native level and capability, not raw level alone

Iron’s provides native maximum level, minimum rarity, and rarity-at-level metadata in the inspected `AbstractSpell`. A level-one spell can still be inherently advanced, and maximum levels differ between spells. Use the native rank within that spell’s progression plus native rarity and a reviewed capability profile. Source: [Iron’s AbstractSpell](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/src/main/java/io/redspace/ironsspellbooks/api/spells/AbstractSpell.java).

Suggested cap-32 initial rarity anchors for **Magic** are Common 4, Uncommon 8, Rare 14, Epic 20, Legendary 26. Treat them as proposed calibration values. A native adapter must explicitly map the supported rarity enum; never depend blindly on an ordinal or fall through a new enum to an extreme value.

Within one rarity band, add a small bounded progression increment using the spell’s native level interval for that band. Apply a reviewed capability adjustment where native rarity poorly represents exceptional utility. Do not let large mana costs alone determine difficulty, and do not demand Wisdom/Intelligence on every spell merely because they sound thematic.

Differentiate:

- **Selected/native level:** the level learned, inscribed, or legitimately chosen for the action.
- **Bonus/effective level:** increases granted by gear/perks during casting.

Gate on the selected/native level by default. Otherwise a perk that adds spell levels can make previously valid spells unusable as soon as it activates. Snapshot the qualifying level before downstream bonuses. Test Runic’s spell-level perks, native equipment bonuses, and Codex overrides to prove this ordering.

Require monotonically nondecreasing gates across a spell’s native levels. If an upstream disabled spell is unavailable, inference must not enable it. Invalid/out-of-range levels must follow native validation rather than being clamped into an exploitable valid cast.

### 10.3 Preserve the legacy formula as a compatibility mode

Add an Iron’s gate model setting, for example `METADATA` and `LEGACY_LEVEL`:

- `METADATA` is the new default when universal spell inference is enabled and supported metadata is available.
- `LEGACY_LEVEL` uses the existing `base + (level - 1) * scale` rule with its existing settings and rounding.
- If metadata cannot be read, use the enabled legacy fallback or an explicit abstention according to configuration; report which path won.
- If `enableAutoGates = false`, return to the separately enabled legacy/curated integration behavior. Do not leave stale metadata predictions installed.

The metadata estimate and legacy formula are **alternative generators** for the same spell gate. Do not require the maximum of both or add their values. A spell-specific explicit rule or `Allow` wins before either generator.

Fix the current composition defect by resolving explicit spell rules before the automatic formula. A permission for a spell affects the spell action; it does not automatically waive a separately applicable equipment gate. A book exemption likewise does not waive all contained spell requirements.

### 10.4 Cast cancellation and continuing effects

Choose the earliest supported server boundary where the authoritative actor, selected level, and source are known. `SpellPreCastEvent` is a useful normal path, but it does not carry an item stack in the inspected API. Capture context at `attemptInitiateCast` where required, and provide narrow adapters for paths that bypass it. Sources: [pre-cast event](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/src/main/java/io/redspace/ironsspellbooks/api/events/SpellPreCastEvent.java), [native initiation](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/src/main/java/io/redspace/ironsspellbooks/api/spells/AbstractSpell.java).

For LONG/CONTINUOUS casts, keep an authorization context with actor/source/native-level/rules revision. If rules or skills change before a later committed stage, reevaluate at a supported boundary and stop future effects cleanly when denied. Do not retroactively delete projectiles, summoned entities, or resources already legitimately committed, and do not refund prior successful ticks twice.

Do not run inference inside each channel tick. Use the current immutable rule/profile and bounded live context checks. A player notification is rate-limited by actor, target, action, and revision.

## 11. Shared resolution architecture

### 11.1 Typed targets and action contexts

The old table is keyed by a string used for items, blocks, entities, and spell IDs. New rules must distinguish those domains. The same resource location can legitimately exist in several registries.

Introduce small immutable models, with names adapted to the project’s conventions:

```text
GateTarget       = target kind + resource location
GateContext      = server actor + action + target + optional native/stack state
RequirementSet   = canonical aptitude -> minimum level
ResolvedGate     = outcome + requirements + source + rule ID + evidence + revision
GateDecision     = eligibility against one actor's current skills + failure details
GateSnapshot     = typed static rules + dynamic profiles + provenance + input fingerprint
```

Separate **resolved requirements** from **a player’s eligibility**. A cached allow result for one skilled player is never a reusable content rule for everyone.

Add `CAST`, `INTERACT_BLOCK`, `PLACE_BLOCK`, and `MINE_BLOCK` semantics through a new context type or carefully extended enum. Preserve existing action values and update the protocol if the serialized enum changes. Distinguish the tool’s `MINE` gate from the target block’s `MINE_BLOCK` gate.

Retain a compatibility adapter for legacy untyped IDs. Existing entries continue to affect the domains/actions they historically affected; do not silently narrow or expand them based on whichever registry is scanned first. New typed rules do not leak across item/block/spell domains. Export ambiguous legacy IDs with a migration suggestion.

### 11.2 Precedence contract

First apply the feature toggle for the domain/source being evaluated. Then resolve the applicable rule layers below. The existing explicit native-pack priority, notably Tinkers’, must remain stable unless separately migrated with an explicit conflict report.

| Order | Rule source | Semantics |
| --- | --- | --- |
| 1 | Explicit stack/native-context pack rule | Preserve existing most-specific native overrides, including explicit allow. |
| 2 | Explicit typed exact-ID rule or legacy configured ID rule | Replaces generated requirements for its target/actions. An intentionally authored typed exact rule takes precedence over the legacy fallback for the same action. |
| 3 | New explicit pack tag/family/namespace rule | Applies only where no more-specific explicit rule won. |
| 4 | Curated built-in/profile requirement | The reviewed default for identified content, such as corrected Iron’s/Codex profiles. |
| 5 | Owning native automatic adapter | Dynamic material/book/spell semantics for content it owns. |
| 6 | Existing compatibility generator | Preserve existing supported providers’ behavior during initial rollout, except providers deliberately upgraded by this work. |
| 7 | Universal inference | Fills remaining supported gaps. |
| 8 | No rule | Allow, retaining an explanatory excluded/undetermined outcome where appropriate. |

Within an explicit layer, define deterministic matching: exact native context before exact ID; then tags/families; then namespace. Within equal specificity use declared priority, followed by stable rule ID. Reject truly ambiguous duplicate declarations with differing results instead of relying on JSON iteration order. Resource-pack replacement of the same resource follows Minecraft’s pack priority; document how separate rule resources combine.

`Allow` is terminal for the target/action it covers. `NOT_APPLICABLE` means continue searching. An owning native adapter’s `UNDETERMINED` can be terminal for **further automatic inference** while still preserving applicable explicit rules. These must not all be represented by an empty list.

Do not combine all matching vectors using per-skill maximum. That would defeat exemptions, prevent intentional lower overrides, and turn two alternate estimators into stacked requirements. An explicit rule may opt into a documented merge mode later; default to replacement.

### 11.3 Integration ownership and opt-outs

Add an ownership/claim mechanism so the generic generator cannot preempt native content merely by adding an ID rule before a live stack check.

This is especially important for `TConstructRequirementResolver`: its existing implementation yields to entries in `HandlerSkill`. Inserting universal ID defaults for native Tinkers’ tools would otherwise disable material-specific behavior. Native ownership must be resolved before generating those ID defaults, including modifiable addon tools in other namespaces.

Respect explicit per-integration opt-outs as suppression of equivalent universal generation. Maintain a documented suppression mapping for existing settings such as `disabledDiscoveredLockMods`, `disabledDiscoveredLockItems`, and the Iron’s/Tinkers’ gate toggles. Do not quietly reopen a disabled provider through a new fallback.

Where broad native behavior already applies independently, explain it precisely. For example, disabling Codex’s curated gear adapter need not uninstall the existing Iron’s event listener for all addon spells, but the settings screen/audit must identify that remaining source and provide the appropriate spell-gating toggle.

### 11.4 Minimal implementation structure

Prefer extending existing classes and adding narrow helpers:

| Area | Suggested changes |
| --- | --- |
| `integration/lock/` | Add typed descriptors, source/outcome models, ownership registry, `AutoGateGenerator`, inference/calibration helpers, and action-aware resolution. Keep old provider interfaces operational through adapters. |
| `integration/irons/` or existing Iron’s integration package | Add book metadata and spell descriptors, casting-context bridge, curated profile loading, and selected-spell inspection. |
| `integration/apprenticecodex/` | Add optional bootstrap, specialized book/gear/block adapters, cast-path integration, and explicit automation policy. |
| `common/inventory/` | Add startup representation/provider policy and lossless migrations; amend eligibility/reconciliation to respect external limits. |
| `mixin/` | Split count-only hooks; conditionally select them; add only demonstrated missing action seams. |
| `handler/HandlerSkill.java` | Build and publish typed/static/dynamic rule metadata as one revision, preserving legacy accessors. |
| `config/storage/` and `config/snapshot/` | Validate settings, source opt-outs, scaling policies, and server synchronization. |
| `common/rules/` | Reuse loader/index conventions; avoid forcing generic rules through Tinkers’-specific semantics. |
| `network/` and `client/tooltip/` | Extend existing bounded snapshot and inspection paths. |
| `common/command/` and `integration/lock/LockAudit.java` | Add explain, preview, coverage, diff, and export operations to existing command roots. |

Do not build a new progression or capability system. Reuse the existing aptitude registry, `SkillCapability`, and progression/cap calculations.

## 12. Enforcement without item loss or unnecessary restrictions

### 12.1 Required seams

| Action | Required authoritative check | Important edge cases |
| --- | --- | --- |
| Attack/use item | Existing capability/interact/combat backstops using the actual acting stack. | Offhand, ranged release, hybrid tools, native weapon packets, canceled attacks. |
| Equip armor/Curios | Pre-equip native hook where available; safe postcondition handling as a fallback. | Quick equip, dispenser equip, slot swap, command insertion, reload/respec. |
| Interact with block | Actual target block/state and player action. | Empty hand, sneaking, offhand, alternate menu-open packets, retained open menus. |
| Place block | Placing item plus intended block target when a placement gate is enabled. | Placement cancellation, multi-block placement, handoff to another mod, successful-only effects. |
| Mine block | Separate held-tool and target-block decisions. | Instant breaking, AOE/native mining, fake players, spell-authorized terrain effects. |
| Craft | Existing result commit checks only where that source/action enables a crafting gate. | Quick-move loops, changing result, native result slots, consumed ingredients. |
| Cast | Supported native initiation/transaction boundary. | Book/scroll/Curios sources, bonuses, alternate gear, continuous casting, machine policy. |
| Retrieve/remove/store | No new inferred restriction. | Taking a book or ingredient out of storage while underleveled; safe unequip and overflow. |

Do not turn an `INTERACT_BLOCK` requirement into a prohibition on breaking that workstation. A player who cannot operate it must still be able to move or remove it unless a separate explicit mining rule says otherwise.

Audit `InteractionEventHandler.shouldCancelInteraction`, its null/empty-hand behavior, and its current block checks before generalizing it. The action-aware system must not rely on an unrelated item registry lookup succeeding before it evaluates the target block.

### 12.2 Existing equipment and reloads

The current armor/Curios handlers include drop-and-clear paths. For new automatic gates, do not use “drop then set count to zero” without verifying ownership transfer and cancellation. Reuse provider-compatible reconciliation and recovery.

Prefer rejecting a new equip before mutation. When stricter rules invalidate already-equipped gear, use supported native suppression or transactional unequip into legal storage, retaining recoverable overflow if full. Removal must remain permitted. Verify that attributes and native ticking benefits cannot remain active after a denied equip state.

Keep `dropLockedItems = false` as the existing default. Inferred use locks must not introduce automatic item dropping merely because the player holds an unfamiliar item. Preserve explicitly configured legacy behavior through its documented source/action policy.

### 12.3 Action atomicity

At the point of action commitment, re-read the authoritative stack/target and use one rules snapshot. A client tooltip or previously allowed preview is not permission to consume or execute.

For a crafting/menu operation, a denial leaves inputs, result, cursor, and destination unchanged except for legitimate UI synchronization. For a spell, a denial leaves native resources unchanged or lets the upstream documented failed-initiation cleanup restore temporary reservations. Test inventory changes between preview and commit.

Avoid implementing universal post-hoc refunds. Prevent the commitment through a cancelable seam; refunds after native side effects are difficult to make complete or idempotent.

## 13. Configuration and pack-author customization

### 13.1 Proposed settings

Use the project’s existing JSON5/YACL conventions. These are proposed field names; keep a coherent group and document any final renaming.

| Setting | Default | Meaning |
| --- | --- | --- |
| `enableAutoGates` | `true` | Enable universal gap-filling inference. |
| `autoGateItems` | `true` | Discover relevant unconfigured equipment/items. |
| `autoGateBlocks` | `true` | Discover recognized workstations and utility blocks. |
| `autoGateSpells` | `true` | Infer through supported native spell adapters. |
| `enableSpellLocks` | `true` | New master for all spell-action requirements; default true preserves previously enabled spell behavior. |
| `autoGateCrafting` | `false` | Add new inferred crafting requirements. Legacy explicit crafting gates remain unchanged. |
| `autoGatePlacement` | `false` | Add inferred placement requirements; operation gates remain available. |
| `autoGateHarvestBlocks` | `false` | Add inferred target-block harvest gates beyond existing rules. Tool gating remains available. |
| `autoGateMinimumConfidence` | `0.75` | Neighbor-estimate enforcement threshold; validate range. |
| `autoGateUseRoleFallbacks` | `true` | Use reviewed low-tier fallbacks when the role is well established. |
| `autoGateRecipeEvidence` | `true` | Use bounded supported recipe/upgrade evidence. |
| `autoGateLearnFromManualRules` | `false` | Allow opted-in manual rules to become calibration examples. |
| `autoGateExcludedNamespaces` | `[]` | Suppress universal inference for listed namespaces. |
| `autoGateExcludedItems/Blocks/Spells` | `[]` | Typed exact-ID and supported tag exclusions. |
| `ironsBookGateProfile` | `METADATA` | Corrected/native book profiles; optional `LEGACY_KEYWORDS`. |
| `ironsSpellGateModel` | `METADATA` | Metadata model when enabled; optional `LEGACY_LEVEL`. |
| `enableApprenticeCodexIntegration` | `true` | Activate reviewed Codex support when compatible dependencies are present. |
| `codexAutomationGatePolicy` | `DEVICE` | Gate player device operations; optional `ONLINE_OWNER` spell enforcement. |
| `autoGateMode` | `LIVE` | Optional `FROZEN` mode loads a previously accepted generated snapshot. |

Keep advanced model constants in versioned calibration data rather than filling the settings screen with dozens of opaque sliders. Surface presets only if they map to fully documented settings.

### 13.2 Existing toggles must remain coherent

- `enableItemLocks = false` still disables the existing item/block/legacy-ID lock domain. The new universal engine may produce an audit preview but does not enforce those item/block gates.
- `enableSpellLocks = false` disables all Runic spell-action requirements, including explicit and generated ones. It does not waive a separate equipment gate. Missing fields default to true, so adding this setting alone does not change existing spell behavior.
- `ironsEnableSchoolGating = false` disables generated Iron’s spell requirements, including Codex metadata spell estimates. Do not bypass it through the new spell adapter. Explicit legacy spell IDs retain their historical dependency on `enableItemLocks` as well as the new spell master. New typed explicit spell rules depend on the spell master, independently of the item-lock master; their separate domain is part of opting into the new schema.
- `enableAutoGates = false` disables the new inferred layer only. It does not undo the spellbook bug fix, manual rules, or deliberately enabled legacy/native integrations.
- Disabling an optional integration suppresses its new adapter/profile generation without unsafe class loading. Existing broader integration behavior is shown separately as described in §11.3.
- Scaling and integration multipliers apply once and remain server-authoritative.
- Representation provider selection for Bigger Stacks is **restart-required**, while skill capacity/gate settings can use safe runtime reload paths. Do not imply that a config toggle hot-unloads mixins.

Add a compact “active gate sources” view or command so users can answer which setting controls a particular restriction.

Required toggle truth cases: with both masters true and the Iron’s generation toggle false, explicit spell rules still apply; with `enableSpellLocks` false, all spell rules are off; with only `enableItemLocks` false, legacy explicit spell-ID rules are off but separately enabled native generation/new typed spell rules retain their declared scope. With `enableAutoGates` false, universal item/block inference and metadata spell estimation are off, while the enabled legacy formula and reviewed integration profiles remain. Test each case to prevent unexpected fallback enforcement.

### 13.3 Rule schema

Add a versioned generic datapack schema such as `data/<namespace>/runicskills/gates/*.json`. Keep authored files distinct from generated exports. Example **proposed schema**:

```json
{
  "schema_version": 1,
  "rules": [
    {
      "id": "example:dragonskin_override",
      "target": {"kind": "item", "id": "irons_spellbooks:dragonskin_spell_book"},
      "actions": ["equip", "use"],
      "result": {
        "type": "requirements",
        "skills": {"magic": 24, "intelligence": 14},
        "scaling": "absolute"
      }
    },
    {
      "id": "example:guidebook_exemption",
      "target": {"kind": "item", "id": "apprenticecodex:isekai_travel_guidebook"},
      "actions": ["equip", "use"],
      "result": {"type": "allow"}
    },
    {
      "id": "example:workbench_operation",
      "target": {"kind": "block", "id": "apprenticecodex:spellcaster_workbench"},
      "actions": ["interact_block"],
      "result": {
        "type": "requirements",
        "skills": {"tinkering": 10, "magic": 8},
        "scaling": "absolute"
      }
    }
  ]
}
```

Also support typed tags, spell native-level ranges, and explicit inference exclusions. Validate contradictory selectors, unknown skills/actions, empty malformed requirement vectors, duplicate rule IDs, invalid resource locations, nonfinite multipliers, and unsupported schema versions. Missing optional-mod IDs can remain dormant with an audit entry; they must not crash unrelated packs.

Require explicit `type: allow` for an exemption. An empty or invalid requirement map is not a permission. Keep imported legacy `Allow` behavior intact.

### 13.4 Generated data and frozen mode

Store generated snapshots outside manually authored config, with:

- Schema/algorithm/calibration version.
- Relevant mod versions and content fingerprints.
- Tags, recipes, explicit-rule, profile, and config fingerprints.
- Reference/effective vectors, source, evidence, and exclusions.
- A deterministic content digest and generation diagnostics.

`LIVE` rebuilds from current inputs on the normal safe lifecycle. `FROZEN` keeps a previously accepted generated catalog until an administrator previews and applies an update; explicit rules still retain precedence. Unseen content in frozen mode remains undetermined rather than receiving an unreviewed prediction. Missing mandatory frozen data must produce a clear status and preserve the last valid snapshot, not silently switch modes.

Provide a read-only export first. Promoting selected generated entries into explicit pack rules must be an intentional operator action producing reviewable files; never rewrite user config during normal startup.

## 14. Reload lifecycle, synchronization, and performance

### 14.1 Atomic generation and installation

Reuse `HandlerSkill.Snapshot` and the existing config-sync publication pattern. A candidate must include its matching config, typed rules, dynamic adapter profile versions, and diagnostics before it becomes visible.

Rebuild when relevant server tags/recipes/datapacks or gate config change, and after native registries such as Tinkers’ materials become ready. Coalesce duplicate lifecycle notifications. Use a monotonically increasing generation token so an older asynchronous build cannot replace a newer one.

Read Minecraft registry/recipe/native objects only on a safe thread/stage. If computation is moved off-thread, first capture plain immutable descriptors and perform only pure math off-thread. Publication and player-state reconciliation run on the server thread.

Malformed rules or a failed global build retain the last known good revision. Isolate an optional adapter failure with a single actionable diagnostic where possible, but do not discard explicit restrictions or publish a partially inconsistent catalog. On first startup with no valid rules snapshot, follow a documented failure policy; do not pretend successful enforcement while everything is empty.

### 14.2 Client synchronization

The existing `ConfigSyncCP` limits include 128 items per chunk, 512 chunks, and a bounded config payload. Expanding to several target kinds and spell-level profiles requires a byte-budget review, not merely increasing those counts.

- Send only gameplay/display data clients need; retain detailed recipe/neighbor evidence server-side for operator inspection.
- Bound per-packet bytes, total assembled bytes, entry counts, strings, per-spell level data, and concurrent pending revisions.
- Prefer compact monotonic spell profiles/segments to a huge row for every possible effective level.
- Assemble and validate the whole revision before activation; discard incomplete timed-out or older assemblies.
- On disconnect/world switch, clear authoritative client caches. A revision number from another server is not comparable to the current server’s epoch.
- Dynamic stack/spell responses include server epoch, rules revision, menu/slot/state/request identity, and a suitable fingerprint/revision of native state. Drop stale responses.
- A 32-bit stack hash is a UI cache hint, not a security proof. The server revalidates actual state for every action.
- Clients never supply requirement levels or trusted descriptor values. Bound and rate-limit inspection requests and restrict them to accessible real menu/slot contexts.

Source for the existing architecture: [ConfigSyncCP](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/network/packet/client/ConfigSyncCP.java), [StackRequirementsCP](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/network/packet/client/StackRequirementsCP.java), [network protocol](https://github.com/otectus/runic-skills/blob/6b52ec2d530af9019d845bde873913bec315d76d/src/main/java/com/otectus/runicskills/network/ServerNetworking.java).

### 14.3 Performance acceptance targets

Treat these as engineering targets to measure on a recorded reference machine:

- Static decisions are indexed lookups; no registry/recipe scans on ordinary action paths.
- Dynamic decisions read only the required native state and use bounded caches.
- No per-tick all-player/all-inventory inference pass. Existing bounded reconciliation can remain separate.
- A representative large-pack rebuild should complete within a few seconds; record registry/recipe sizes, duration, memory, and stage timings. A slow stage gets a concrete optimization or configurable work limit.
- One native adapter exception produces a summarized diagnostic and stable fallback behavior, not one warning per item per tick.
- High-stack tests must finish in roughly slot-bounded time even when a count increases from 256 to a million.
- Reject oversized generated/synchronized data cleanly while retaining the previous snapshot; never truncate the tail of the rule catalog silently.

Do not claim a numerical timing target was met without a measurement. Correct ownership and atomicity take priority over premature caching.

## 15. Player experience and operator diagnostics

### 15.1 Player-facing text

Normal tooltips should be short and useful:

```text
Requires Magic 20 • Intelligence 12
```

For a selected spell, show its own requirement under the spell entry. An advanced tooltip may add “Automatic estimate” or the integration/profile name. Do not put neighbor weights, class names, protocol details, or recipe graph diagnostics in normal player flows.

For denial, identify the action and missing skill, for example “Casting this spell requires Magic 14. You have 10.” Show it through the existing visible notice/overlay system, with rate limiting. A pending inspection should show “Requirements loading” rather than an invented local guess.

If native state is unavailable, preserve any known gate information and label the unresolved part. Do not show “No requirements” when the client has not received the authoritative result.

### 15.2 Commands and exports

Extend the existing `/skills locks` command family. Proposed commands:

| Command | Behavior |
| --- | --- |
| `/skills locks inspect hand` | Show current stack/action requirements and provenance. |
| `/skills locks inspect block` | Inspect the targeted block separately from the held item. |
| `/skills locks inspect spell <id> <level>` | Show the spell requirement and selected model. |
| `/skills locks explain <kind> <id>` | Show winning rule, suppressed candidates, native evidence, scaling, and uncertainty. |
| `/skills locks preview` | Build a candidate/diff without publishing it. |
| `/skills locks coverage` | Summarize outcomes by namespace, role, domain, and adapter. |
| `/skills locks export` | Export the current catalog and audit evidence using the existing audit route. |
| `/skills locks apply-preview <token>` | Publish an unchanged validated candidate; reject stale input fingerprints. |
| Existing reload command | Rebuild/publish through the same transaction used by preview/apply. |

Adapt syntax to the actual command tree rather than registering duplicate roots. Operator-only commands should export counts/IDs/profiles without player inventories, personal identifiers, or arbitrary NBT by default.

Include a Bigger Stacks diagnostic showing detected provider/version, active count hooks, representation bounds, native capacity versus Pack Mule capacity, and migration/recovery status. This should make the next compatibility report actionable.

## 16. Verification plan

Do not add tests that merely restate the implementation. Focus on externally meaningful regressions: data preservation, correct precedence, action authority, native transaction behavior, determinism, and balance invariants.

### 16.1 Pure tests

1. Stable input ordering, locale, and hash-map iteration produce identical generated catalogs and digests.
2. Changing stack count/max size leaves item/block/spell requirements unchanged.
3. Ingredient/decoration false-positive fixtures abstain or remain excluded.
4. Explicit allow/lower requirements beat every generated source for the same action.
5. Same ID in item, block, and spell registries stays distinct for typed rules.
6. Missing features lower coverage/confidence; name-only evidence never creates a high-tier gate.
7. Previous predictions cannot enter the calibration corpus automatically.
8. Selected/native spell level is monotonic; unrelated bonus levels do not retroactively invalidate qualification.
9. Multipliers and cap scaling apply once; native-cap-relative and absolute rules remain distinct.
10. Global/per-skill reachability checks handle starting levels and small caps correctly.
11. Recipe cycles, alternative recipes, unsupported serializers, and huge ingredient tags are bounded.
12. Provider-owned native content cannot be preempted by a generated generic ID rule.
13. Malformed/duplicate rules fail predictably; explicit dormant optional IDs are retained.
14. Serialization and transfer arithmetic preserve supported counts, including external counts above Runic’s historical ceiling.

Extend existing meaningful tests such as `LockProviderRegistryTest`, `StackCapacityMathTest`, and native requirement tests where suitable.

### 16.2 Real-artifact game tests

Use or extend `PackMuleGameTest`, `RulesReloadGameTest`, `ConfigAuthorityGameTest`, `CraftingAuthorityGameTest`, Tinkers’ `StackRequirementGameTest`, and Iron’s native test profiles. Add focused Codex fixtures rather than relying on a generic fake item that never calls the native cast/menu path.

| Profile | Required demonstrated outcomes |
| --- | --- |
| Runic only | Automatic item/block discovery, explicit overrides, reload, legacy locks, Pack Mule 128/192/256. |
| Runic + Bigger Stacks | Count preservation, overlapping hooks selected correctly, slot limits respected, Pack Mule on/off, external counts above 1,048,576. |
| Runic + old supported Iron’s | Existing advertised integrations and legacy profiles remain functional if that version stays supported. |
| Runic + selected newer Iron’s | Book metadata/table, native spell profiles, Curios and scroll casting. |
| Runic + Iron’s + Codex | Specialized books, spellguns, alternate casting, workstation and automation policies. |
| All requested mods together | No codec/gate interaction failures; denied actions preserve oversized stacks and native inventories. |
| Tinkers’ and representative existing integrations | Native material rules, explicit pack priority, opt-outs, and existing bonuses remain intact. |
| Optional mods absent | Dedicated-server launch and registration without optional classes. |

Test exact threshold minus one, threshold, and threshold plus one for representative gates. Use real successful/failed native effects, resource deltas, and resulting inventory state as assertions.

### 16.3 Multiplayer and lifecycle scenarios

- Two players with different skills handle the same item and see the same requirement but different eligibility.
- Client-local configuration differs from the server; the server’s rule remains authoritative.
- A client submits a stale menu action or forged inspection request; the server reevaluates real state.
- A rules reload raises, lowers, adds, and removes gates while menus/books are open.
- A player respecs while holding/equipping a book and while a long/continuous spell is active.
- Reload failure, duplicate/reordered packets, disconnect/reconnect, dimension travel, death/clone, and switching servers cannot install stale state.
- Existing manual exemptions and saved integration opt-outs survive upgrade and reload.
- Full inventory, canceled drops, and native storage containers preserve ownership during reconciliation.
- The same discovery run with a different client language produces identical rules.

### 16.4 Build gates and evidence

Run the repository’s required `check` and `build` tasks. The inspected build wires in sided-import, provider, version-consistency, language-parity, mixin inventory/remapping, YACL-generation, and shipped-refmap checks. Update their inventories intentionally when adding or splitting hooks.

Use the existing production validation/runtime profiles, adding named Bigger Stacks and Codex profiles as needed. Record the actual command lines and artifact hashes. A stub compile, unit test, or successful dev launch does not replace a remapped production-JAR test for mixin collisions.

Do not present unrun tests as passing. If a dependency cannot be fetched or a real client scenario is unavailable, state that exact validation gap in the implementation report and keep its release acceptance item open.

## 17. Implementation sequence and review boundaries

### Stage 0: establish the baseline

- Checkout the current 2.2.0 descendant and read local instructions.
- Record dependency/source/artifact manifests and available tests.
- Reproduce the existing book fallback table and establish large-stack fixtures.
- Capture current manual/generated resolution, skill caps, and optional integration settings for migration tests.

**Exit:** a source-grounded compatibility ledger and reproducible current-behavior fixtures; no invented crash diagnosis.

### Stage 1: repair stack coexistence

- Separate count serialization from unrelated mixed-purpose hooks.
- Add early representation-provider selection and strict peer compatibility.
- Preserve Runic-only behavior and implement tested Bigger Stacks delegation/migration.
- Repair capacity/reconciliation/count-bound assumptions.

**Exit:** Stage A acceptance passes against identified real artifacts, including a dedicated multiplayer session and existing-save round trips. This is independently reviewable from balance work.

### Stage 2: introduce shared typed resolution

- Add typed target/action/outcome models and a backward-compatible legacy adapter.
- Make explicit allow, native ownership, provider opt-outs, and source precedence unambiguous.
- Extend immutable snapshots, validation, inspection, and bounded synchronization.

**Exit:** current integrations retain their behavior; typed collision and manual-priority regressions pass.

### Stage 3: fix Iron’s books and unify spell decisions

- Add native book descriptors and the reviewed balance table.
- Separate equipment versus selected-spell requirements.
- Resolve explicit spell rules before automatic generators.
- Add metadata spell profiles with a legacy model and native-level qualification.

**Exit:** direct book upgrades no longer flatten, independent spell access works, and all normal native cast routes enforce without resource loss.

### Stage 4: implement universal inference

- Add discovery, immutable descriptors, calibration corpus, bounded neighbor inference, and conservative fallbacks.
- Add recipe support, exclusions, cap/global-budget checks, and deterministic output.
- Add default-enabled settings, coverage/explain/diff/export, and optional frozen mode.

**Exit:** held-out balance evaluation is recorded, false-positive fixtures pass, and enabled/disabled behavior is demonstrated on a representative mixed pack.

### Stage 5: complete Codex support

- Finalize the compatible dependency profile.
- Implement specialized equipment/storage/utility block profiles.
- Cover ordinary and alternate player casting, and explicit dispenser policies.
- Complete the content/action ledger and native behavior tests.

**Exit:** every supported Codex entry has a documented resolution outcome; requested player routes and automation policies are demonstrated against the real mod.

### Stage 6: release integration

- Run combined-mod production and multiplayer tests.
- Exercise upgrades from existing 2.2.0 configs/saves and preserve explicit opt-outs.
- Finalize reference balance data, protocol/version changes, docs, migration guidance, and changelog.
- Produce the compiled artifact and concise implementation/validation report when performing the actual implementation task.

**Exit:** the acceptance checklist below is complete or explicitly records a real blocker. Do not call source inspection alone a finished compatibility fix.

## 18. Deliverables expected from the implementing agent

1. Reviewed source changes in logical commits with appropriate tests and migrations.
2. A dependency/compatibility manifest with real artifact identities and tested ranges.
3. A machine-readable inventory of items, blocks, spells, gate outcomes, and coverage gaps.
4. Versioned corrected Iron’s/Codex profiles and the universal calibration corpus.
5. Documentation explaining source precedence, toggles, explicit allow/exclusions, scaling, automation, and generated/frozen catalogs.
6. Before/after spellbook and automatic-gate audit exports, including suppressed manual-conflict candidates.
7. Large-stack format/migration notes and reproduction results for Bigger Stacks.
8. A production build and a validation report distinguishing passed, failed, and unrun checks.
9. A player-facing changelog describing compatibility, improved progression, Codex support, and automatic-gate controls without implementation jargon.

## 19. Final acceptance checklist

- [ ] Work targets the current successor of `main` / 2.2.0, not stale `master` / 2.1.1.
- [ ] Bigger Stacks compatibility is reproduced and verified; only one compatible network count representation is active.
- [ ] Supported existing counts, item data, and capabilities survive saves, transfers, death, reload, and migration.
- [ ] Disabling Pack Mule does not break representation support for already-owned large stacks.
- [ ] Corrected Iron’s book progression reflects native capabilities and verified upgrades, with legitimate sidegrades preserved.
- [ ] Book equipment gates and individual spell cast gates are distinct and consistently displayed/enforced.
- [ ] Apprentice’s Codex uses its correct namespace, compatible dependencies, and documented native casting/storage support.
- [ ] Dispenser/proxy behavior follows an explicit tested policy rather than accidental FakePlayer skill values.
- [ ] Universal inference is enabled by default, can be disabled, and discovers items, blocks, and supported real spell registries.
- [ ] Strong native evidence and reviewed comparisons drive inference; uncertain entries remain visible without punitive guesses.
- [ ] Manual lower rules, explicit exemptions, legacy settings, and native integration ownership retain precedence.
- [ ] Counts, stack limits, player buffs, language, and current player level cannot distort content difficulty.
- [ ] Generated rules are deterministic, attainable, bounded, inspectable, and free of self-training feedback.
- [ ] New inferred gates do not trap stored items or introduce unrequested crafting/pickup restrictions.
- [ ] Server/client state agrees through atomic reloads, bounded synchronization, and stale-response rejection.
- [ ] Production mixin/remapping checks and real-artifact tests pass, with any remaining unrun scenarios disclosed.

## 20. Instructions to the coding agent

Implement the four requested workstreams to the acceptance criteria above. Reinspect the actual checkout and dependency artifacts before selecting hooks. Preserve existing saves/configuration and optional integration behavior. Prefer the repository’s existing providers, snapshots, capabilities, native event seams, and validation profiles.

When evidence contradicts a proposed balance value or API assumption, adjust the implementation and document the exact reason. Treat the explicit invariants, default-enabled automatic system, configurable opt-outs, source precedence, and item preservation as requirements. Treat illustrative class names and unverified balance/acquisition assumptions as design guidance requiring confirmation from source and tests.

Do not stop after adding namespace keywords, tooltips, or empty compatibility classes. Deliver working enforcement, native transaction behavior, migration, diagnostics, and demonstrated tests for the actual user-visible paths.
