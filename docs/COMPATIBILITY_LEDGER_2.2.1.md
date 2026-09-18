# Compatibility ledger — 2.2.1

The artifact identities, inspection results and reproduction status behind the 2.2.1 compatibility
work. Reference document §4.1 requires a ledger *before* a fix is selected, so that a later reader
can tell which build was actually read and which claims are still unverified.

Nothing in this file is a promise about an artifact that is not listed here by exact version.

## Environment under test

| Item | Value |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 |
| Mappings | Parchment 2023.09.03-1.20.1 |
| Runic Skills | 2.2.1 (`gradle.properties` `mod_version=2.2.1`, `VERSION` 2.2.1) |
| Runic Skills sync protocol | 18 (17 introduced the new lock actions and Titan's Grip sync; 18 adds scoped enforcement rules and independent legacy fallbacks to the atomic configuration snapshot) |
| Iron's Spellbooks compile pin | CurseForge file `8680180` = `1.20.1-3.16.3` (Phase 3; was `7402504` = `1.20.1-3.15.0`). The runtime test profile adds `maven.modrinth:irons-lib:1.20.1-2.1.0` and raises Curios to `5.14.1+1.20.1`, both of which 3.16.3 declares mandatory. |

## Artifacts

All three jars live in `libs/` and are **untracked**: `.gitignore:111` ignores `*.jar` with only two exceptions
(`gradle/wrapper/gradle-wrapper.jar` and `libs/l2tabs-0.3.3.jar`, `.gitignore:129-130`). A fresh clone does not have them, so nothing in the build may depend on
them at compile time. They are reproduction inputs only.

| File | Mod id | Declared version | SHA-256 |
| --- | --- | --- | --- |
| `libs/biggerstacks-1.20.1-2026.06.17-all.jar` | `biggerstacks` | `1.20.1-2026.06.17` (from `Implementation-Version`; `mods.toml` uses `${file.jarVersion}`) | `70840af81e6f734a6ea9c5eecf1f540eb470e42ce8339a5f4cb381a3cfc36204` |
| `libs/apprentice_codex-0.9.7.1+mc1.20.1.jar` | `apprenticecodex` | `0.9.7.1` | `4685141bdd7fe0319270167ebffd658b6c2d91c6eaef5a22adb72c427b9cf4da` |
| `libs/irons_spellbooks-1.20.1-3.16.3.jar` | `irons_spellbooks` | `1.20.1-3.16.3` | `54b5aaa52887c38f570fbd820288171234d8541c17a5e953f9271189fdd480fb` |

Bigger Stacks jar-in-jar payloads (their versions decide the count *rules*, not the count
*representation*): `BiggerStacksTransformerLib-2026.02.24`, `BiggerStacksConfigLib-2026.02.25`,
`SimpleLoggerWrapper-1.0`, `xstream-1.4.20`, `xstream-1.4.21`.

Spartan Weaponry is the exception to the paragraph above: it has a real Modrinth coordinate, so it
is a compile-only Gradle dependency rather than an untracked file, and a fresh clone resolves it.

| Coordinate | Mod id | Declared version | Resolved file | SHA-256 |
| --- | --- | --- | --- | --- |
| `maven.modrinth:spartan-weaponry:4U6gwadt` | `spartanweaponry` | `3.2.1` (`mods.toml`) | `SpartanWeaponry-1.20.1-forge-3.2.1-all.jar` | `2736ac2dd52a7b3a188df454e61a4154cd36e273fa770f3ab89dcf6e732e88ad` |

The immutable version ID, not the version string `1.20.1-3.2.1`, because the string also names this
mod's other loader builds. The jar carries a jar-in-jar `mixinextras-forge-0.3.5`; under
`-PspartanProfile=true` FML selects the newest implementation present (this mod's 0.4.0), which the
`runGameTestServer` log for that profile records at `UniqueModListBuilder` ("Selected file
mixinextras-forge-0.4.0.jar for modid mixinextras with version 0.4.0").

## Produced artifacts — 2.2.1

Built with the Gradle `build` task on the working tree this document describes; `check` passed
first — every lint gate, the unit tests, and `verifyShippedRefmap`, which reported 53 refmap
classes and 5 Tinkers' SRG entries.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `build/libs/runicskills-2.2.1.jar` (jarJar — **the distributable**) | 3162906 | `ed4a8d5eb3b4a4ea6215cbaea5d83d6942743125487f364fe6e75f437b3d4f71` |
| `build/libs/runicskills-2.2.1-slim.jar` (no bundled dependencies) | 2979790 | `2552d1b2b4ba21c349a9c404de7de22b607db34181109e3df64cbe7dbcc2b65b` |

`unzip -l` on the distributable: 1821 entries, including `runicskills.mixins.json`,
`runicskills.refmap.json`, `data/runicskills/gate_calibration/v1.json`,
`data/runicskills/irons/book_profiles.json`, and a `META-INF/jarjar/` holding exactly
`metadata.json` plus `mixinextras-forge-0.4.0.jar`. Zero entries under `jp/aquafactory`,
`io/redspace`, `slimeknights`, `biggerstacks`, `com/oblivioussp` or `net/bettercombat`: none of
these integration surfaces is bundled, which is what lets each of them be absent at runtime.
`com/oblivioussp` (Spartan Weaponry) is a compile-only dependency; `net/bettercombat` is an
`implementation` dependency, present at runtime in the development environment, but it is still
never packaged into the shipped jar. Also zero `weapon_attributes` entries — the Better Combat two-handed fixture the
Titan's Grip gametests need lives in `src/gametest/resources`, which no shipping task packages.

The automatic-gate calibration corpus ships at `data/runicskills/gate_calibration/v1.json`, **not**
under `gates/`. The gate rule loader scans the datapack folder `runicskills/gates`, which resolves
to `data/<namespace>/runicskills/gates`; a runtime probe of the server resource manager confirmed
that `listResources("runicskills/gates", …)` returned zero entries while the corpus was, before the move,
visible as `runicskills:gates/calibration/v1.json` under `gates`, so the two folders never collided. The corpus
was moved anyway because the two paths were one level of nesting apart and easy to read as the same
folder, and a corpus inside the loader's scope would be parsed as a malformed rule and refuse the
whole reload. `GateRuleReloadGameTest` drives a real reload against the shipped resources and holds
the separation.

## Gate review corrections — 2026-09-17

The artifact hashes above describe the rebuild after fixing manual precedence, client/server action
agreement, scoped-rule fallbacks, crafting-result filtering, and LIVE catalog invalidation on config
reload. Protocol 18 carries scoped enforcement rules and independent legacy defaults in the same
atomic revision as the configuration and display table. FROZEN catalogs still honor current manual
overrides; Tinkers' native material requirements remain active outside an authored rule's actions.

Validation of the corrected tree:

- `./gradlew check build --offline`: passed; 460 unit tests across 85 suites, no failures or errors;
  shipped artifact verification found 53 refmap classes and 5 Tinkers' SRG entries.
- `./gradlew runGameTestServer -PcodexProfile=true --offline`: all 295 required tests passed,
  with Iron's Spellbooks and Apprentice's Codex present.
- `./gradlew runGameTestServer -PtinkersProfile=stable --offline`: all 371 required tests passed,
  including the scoped crafting allow/native mining requirement regression.
- Packet round-trips preserve action/domain decisions and retain the previous client snapshot until
  a complete revision arrives. Crafting tests drive the real recipe result and pickup checks.

The default remapping check still reports unresolved targets for optional mods absent from its
compile classpath; the above runtime profiles establish only their listed combinations.

## Bigger Stacks `1.20.1-2026.06.17` — static inspection

Method: unzip, read `META-INF/mods.toml`, `biggerstacks.mixins.json`, `biggerstacks.refmap.json`,
`META-INF/accesstransformer.cfg` and `transformers/vanilla.xml`; `javap -v -p` on the `ItemStack`,
`FriendlyByteBuf` and helper classes. This is a **bytecode read, not a runtime observation**.

### Count representation

**Persistence** — `mixin.vanilla.stacksize.ItemStackMixin`:

- `@Redirect(method = "save", at = @At(value = "INVOKE", target = "CompoundTag.putByte(Ljava/lang/String;B)V"))`
  writes `Count` as a **byte clamped to `min(count, 127)`**, and additionally writes
  `BigCount` as an **`int`** when `count > 127`.
- `@Redirect(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "FIELD",
  target = "ItemStack.count:I", opcode = PUTFIELD))` reads, in order:
  1. `BigCount` as `int` when present;
  2. otherwise `Count` **as `int` when its tag type is `TAG_INT` (3)**;
  3. otherwise `Count` as a byte.

Consequence: Bigger Stacks' read path already understands Runic Skills' integer `Count`, so a save
written by Runic Skills alone is read back losslessly after Bigger Stacks is installed. The reverse
is not true — Runic Skills has never read `BigCount`.

**Network** — `mixin.vanilla.FriendlyByteBufMixin`:

- `@Redirect(method = "writeItemStack", at = "FriendlyByteBuf.writeByte(I)")` → `writeInt(count)`:
  the count occupies a **fixed 4-byte big-endian int**, not a byte and not a VarInt.
- `@Redirect(method = "readItem", at = "FriendlyByteBuf.readByte()")` → returns `0`, and
  `@ModifyVariable(method = "readItem", at = @At("STORE"), ordinal = 0)` → `readInt()`.

**Representable range**: `BigCount` is an NBT int and the wire field is a 4-byte int, so the
representation is lossless to `Integer.MAX_VALUE`. The *configured* maximum is a separate concept:
`portb.biggerstacks.config.StackSizeRules.getMaxStackSize()` is
`max(ruleSet.getMaxStacksize(), maxRegisteredItemStackSize)` and the shipped `RuleSet` default is
`64`; the project description advertises a ceiling of `MAXINT/2` (about 1.07 billion).

### Overlaps with Runic Skills

Direct, same-instruction collisions — both mods would inject into the same target:

| Target | Bigger Stacks | Runic Skills (2.2.0) |
| --- | --- | --- |
| `ItemStack.<init>(CompoundTag)`, PUTFIELD `count` | `@Redirect` | `@Redirect` (`MixItemStack:36-42`) |
| `ItemStack.save` | `@Redirect` on `CompoundTag.putByte` | `@Inject` at `RETURN` writing int `Count` (`MixItemStack:44-49`) |
| `FriendlyByteBuf.writeItemStack`, `writeByte(I)` | `@Redirect` | `@WrapOperation` (`MixFriendlyByteBuf:16-24`) |
| `FriendlyByteBuf.readItem`, `STORE` ordinal 0 | `@Redirect` on `readByte()` + `@ModifyVariable` | `@ModifyVariable` (`MixFriendlyByteBuf:25-31`) |

Two `@Redirect`s on one instruction is a hard Mixin application failure; two `@ModifyVariable`s on
one store apply in an undefined order and produce a silently wrong count. Either way the two
serializers cannot coexist.

Further overlaps that do **not** collide at the bytecode level but do compete for the same
behaviour, and which are why Pack Mule is deferred rather than layered (§4.5 of the reference
document forbids a multiplier on top of Bigger Stacks):

- `ItemStack.getMaxStackSize` and `Item.getMaxStackSize` — Bigger Stacks `@Inject`s a cancellable
  rule-set result.
- `transformers/vanilla.xml` rewrites, via Bigger Stacks' own ASM transformer library and with
  `transformChildren="true"`: `net.minecraft.world.Container.getMaxStackSize`,
  `net.minecraft.world.inventory.Slot.getMaxStackSize` and
  `net.minecraftforge.items.IItemHandler.getSlotLimit` — the same three destination-capacity seams
  Runic Skills' `MixSlot`, `MixInventory` and `MixInvWrapper` adjust for Pack Mule.
- Other Bigger Stacks mixins recorded for completeness: `AnvilMenuMixin`, `ContainersMixin`,
  `ItemPropertiesMixin`, `BundleItemMixin`, `ItemEntityMixin`, `ServerGamePacketListenerImplMixin`,
  `ItemStackHandlerMixin` (present in the jar, absent from `biggerstacks.mixins.json` — added by its
  `TransformerEngine` plugin), plus AE2, Better Bundles, Modular Routers, Refined Storage and
  Sophisticated Core compat mixins and twelve `transformers/*.xml` rule files.

### The collision static inspection missed, and how it was found

A `runGameTestServer -PbiggerStacksProfile=true` run crashed during `Items.<clinit>`, before any
GameTest, on a fifth collision that the table above did not list because it is not a count hook:

```
[mixin] @ModifyConstant conflict. Skipping biggerstacks.mixins.json:vanilla.stacksize.
  ServerGamePacketListenerImplMixin->@ModifyConstant::increaseStackLimit(I)I with priority 1000,
  already redirected by runicskills.mixins.json:MixPackMulePacketListener->@ModifyConstant::
  runicskills$creativeLimit(...)I with priority 1000
…
InjectionError: Critical injection failure: Constant modifier method increaseStackLimit(I)I in
  biggerstacks.mixins.json:vanilla.stacksize.ServerGamePacketListenerImplMixin failed injection
  check, (0/1) succeeded.
```

Both mods modify the same `64` in `ServerGamePacketListenerImpl.handleSetCreativeModeSlot`. Mixin
skips the second modifier with a warning, Bigger Stacks' injector — which does not set
`require = 0` — then fails its own injection check, and the whole class transformation aborts.
Selecting the representation correctly was not enough: a *capacity* hook, not a *count* hook, killed
the server. Log: `/tmp/gradle-runic-skills-runGameTestServer-20260916-105456.log`, lines 695 (correct
`EXTERNAL_DELEGATED` selection), 1048 and 1089.

Resolution: every mixin whose only purpose is Pack Mule capacity is now gated on
`StackRepresentationProvider.grantsPackMuleCapacity()`, and every mixin that carries other duties
keeps applying but neutralises its capacity branch. The per-mixin verdict:

| Mixin | Duties | Verdict |
| --- | --- | --- |
| `MixPackMulePacketListener` | creative-slot limit only | **Gated.** The `@ModifyConstant` collision above. |
| `MixPackMuleMenu` | four `@WrapOperation`s on `Slot`/`ItemStack.getMaxStackSize` call sites | **Gated.** Capacity only. |
| `MixInvWrapper` | re-implements `insertItem`, raises `getSlotLimit` | **Gated.** Capacity only, and its `insertItem` would step over the `IItemHandler.getSlotLimit` rewrite Bigger Stacks' transformer library installs. |
| `MixSlot` | capacity + `safeInsert` overflow guard + **item-lock refusal on result slots** | **Kept.** `PlayerStackPolicy.capacity(Slot, ItemStack, int)` returns the slot's own limit when the perk is deferred. The `safeInsert` guard only ever refuses, never grants, so it stays live. |
| `MixInventory` | three capacity hooks + **death-drop ownership recovery** | **Kept.** `hasRemainingSpaceForItem`, `addResource` and `placeItemBackInInventory` return without cancelling when deferred; `dropAll` keeps splitting by the item's own native maximum and keeps recovering refused drops. |
| `MixPackMuleDrops` | splits an oversized toss + **recovers a cancelled toss at any count** | **Kept, unchanged.** Splitting is driven by the item's current maximum, so a foreign large stack drops whole; gating it would turn a refused drop into a deleted one. |
| `MixAbstractContainerMenu` | publishes the interacting player for item locks | **Kept.** Not a capacity hook. |
| `MixHopperBlockEntity` | hopper cooldown perk | **Kept.** Not a capacity hook. |

Sweep for the same class of problem: `@ModifyConstant` appears exactly once in the whole mod, in
`MixPackMulePacketListener`, so that was the only constant that could be claimed twice. Comparing
every remaining Bigger Stacks injection point against Runic Skills' mixin set leaves no other
same-instruction pair — `Containers.dropItemStack` (constants 21/10), `AnvilMenu.onTake`
(`@Redirect` on `Container.setItem`), `ItemEntity.merge` (constant 64), `ItemStackHandler.getSlotLimit`,
`Item$Properties.stacksTo`, `Item.getMaxStackSize`, `ItemStack.getMaxStackSize`, `BundleItem` and the
client `ItemRenderer` hook are all in methods this mod does not inject into. `MixAnvilMenu` shares
the `AnvilMenu` class but injects into `createResult`, not `onTake`. The three
`transformers/vanilla.xml` rewrites are ASM, not Mixin, and target `Slot.getMaxStackSize()I` and
`Container.getMaxStackSize()I` — the no-argument overloads, not the `(ItemStack)` one `MixSlot` uses
— so they cannot collide mechanically; they are handled by the capacity gating above.

### Verdict used by the code

The inspected representation is complete, integer-wide on both sides, and its read path already
accepts Runic Skills' legacy integer `Count`. `StackRepresentationProvider` therefore selects
`EXTERNAL_DELEGATED` for **exactly** the declared version `1.20.1-2026.06.17`, and
`UNSUPPORTED_OVERLAP` for any other `biggerstacks` build, because the transformation logic of the
historical `1.0.3` line is explicitly not assumed to be identical (reference document §4.1). Both
non-Runic modes switch Runic Skills' own count hooks off; they differ in the diagnostic and in
whether a large stored count is treated as valid data or as unclassifiable data that is preserved
untouched.

The pin is on the declared version rather than the SHA-256 because ForgeGradle re-writes third-party
jars for the Mojang-mapped development runtime, which changes the file hash while leaving the
manifest version intact. The observed hash is logged in the startup diagnostic and in
`/skills locks representation` so an operator can compare it against the row above.

## Reproduction status

Static inspection was done first, and the runtime profiles were then run: the table below records
the reproductions that have been executed and their GameTest counts, and the Phase 5 runtime section
records the Iron's and Codex profile runs. The rows still marked _pending_ have not been run. The
profiles are:

```
gradlew-quiet.sh /home/otectus/Projects/runic-skills runGameTestServer -PbiggerStacksProfile=true
gradlew-quiet.sh /home/otectus/Projects/runic-skills runClient         -PbiggerStacksProfile=true
```

The profile adds `libs/biggerstacks-1.20.1-2026.06.17-all.jar` as a **runtime-only** dependency. It
resolves through a `local.mods`-scoped Ivy repository over `libs/` rather than the existing
`flatDir` entry, because a `flatDir` artifact has no module identity and ForgeGradle's deobfuscator
leaves it unremapped — which would drop a production, SRG-named jar into a Mojang-mapped development
run and crash on Bigger Stacks' first native call. With the coordinate, `fg.deobf` produces the
mapped copy under `~/.gradle/caches/forge_gradle/deobf_dependencies/local/mods/biggerstacks/…`,
confirmed by resolving `runtimeClasspath` with the profile on. Nothing is added to any compile
classpath, and when the untracked file is absent the profile logs that it was skipped and
configuration continues, so a fresh clone is unaffected.

GameTest totals grow between runs as tests were added during the work, so each row below and in
the Phase 5 table names the point it was recorded at.

| Reproduction (reference document §4.1) | Result |
| --- | --- |
| Runic Skills alone, Pack Mule off and on at each rank | **Pass (Phase 1).** `runGameTestServer`, 235/235, selection line reports `RUNIC`, both count hooks active, capacity available. Log `/tmp/gradle-runic-skills-runGameTestServer-20260916-110438.log:614,2052`. |
| Both mods start and run a dedicated (GameTest) server | **Pass (Phase 1).** `runGameTestServer -PbiggerStacksProfile=true`, 235/235, `EXTERNAL_DELEGATED`. Log `…-110359.log:695,2244`. First attempt crashed in `Items.<clinit>`; see the collision section above. |
| Both mods, Pack Mule enabled at each rank | **Pass, as deferral.** Capacity is not granted under a foreign provider, so the rank tests assert the reported deferral instead; a cancelled toss and 500 randomised chest clicks are asserted to conserve every item. |
| Mixin application log inspected for the overlaps listed above | **Pass.** In the Bigger Stacks run, zero occurrences of `MixItemStackCount`, `MixFriendlyByteBuf`, `MixPackMulePacketListener`, `MixPackMuleMenu` and `MixInvWrapper` being mixed; `MixSlot`, `MixInventory`, `MixPackMuleDrops` and `MixAbstractContainerMenu` still applied; Bigger Stacks' own `ItemStackMixin` and `ServerGamePacketListenerImplMixin` applied; zero `InjectionError`, `@ModifyConstant conflict` or `Redirect conflict` lines. |
| Bigger Stacks alone, default rules | _pending_ |
| Bigger Stacks alone, known large-count rules | _pending_ |
| Both mods with a large-count Bigger Stacks ruleset (the runs above used its shipped default of 64) | _pending_ |
| Two real clients, one joining with large stacks present | _pending_ |
| Save made by Runic Skills alone, then opened with both installed | _pending_ |
| Save made by Bigger Stacks alone, then opened with both installed | _pending_ |
| Interactive client session (`runClient -PbiggerStacksProfile=true`) | _pending_ |

## "Before" fixture — Iron's Spellbooks keyword book table

Recorded from `integration/lock/IronsSpellbooksLockProvider` before Phase 3 replaces it, so the
change in generated requirements can be compared rather than asserted. `classify` routes any path
containing `spell_book`, `spellbook`, `grimoire`, `tome` or `codex` into `bookTier`, then emits
`magic = bookTier` and `intelligence = round(bookTier * 0.6)`, each scaled by the pack multiplier
and dropped when the scaled level is below 2.

| Path keyword | `bookTier` |
| --- | --- |
| `wimpy`, `blank` | 4 |
| `novice`, `basic`, `wooden` | 6 |
| `apprentice`, `stone`, `copper` | 8 |
| `iron`, `adept` | 10 |
| `gold`, `expert` | 12 |
| `diamond`, `master` | 14 |
| `netherite`, `archmage`, `legendary` | 18 |
| anything else (the fallback) | 8 |

The fallback of 8 is the row Phase 3 replaces: every book whose registry path matches none of the
keywords above currently receives the same requirement as an `apprentice` book.

**Phase 3 result.** `bookTier` is gone. Books resolve through the reviewed table
(`data/runicskills/irons/book_profiles.json`), then a chassis metadata profile, then a conservative
anchor equal to the reviewed copper value, then abstention. Ten reviewed rows ship, each checked
against the 3.16.3 recipes and item registrations; five books the earlier proposal covered were
left out because the artifact contradicted their proposed values, and use the metadata profile
instead. The generated requirements and the evidence for each are in
[`INTEGRATION_MATRIX.md`](INTEGRATION_MATRIX.md); the audit export carries the per-item reason as
`profile_reason`.

## Apprentice's Codex `0.9.7.1` — compile surface and runtime coexistence

### Compile surface

Codex **is** published to Modrinth, so unlike Legendary Tabs or CustomNPCs it needs no hand-written
API mirror source set and a fresh clone builds without the untracked jar:

```
compileOnly fg.deobf("maven.modrinth:apprentices-codex:lcCKcjxL")
```

The immutable version id is used rather than the version string `0.9.7.1`, which also names the
1.21.1 NeoForge build. `lcCKcjxL` resolves to `apprentice_codex-0.9.7.1+mc1.20.1.jar` and is
**byte-identical** to the artifact in `libs/` — both SHA-256
`4685141bdd7fe0319270167ebffd658b6c2d91c6eaef5a22adb72c427b9cf4da`, verified by downloading the
Maven artifact and comparing. Its POM declares no dependencies, so nothing is pulled transitively.

The runtime profile is `-PcodexProfile=true`, which resolves the same coordinate as `runtimeOnly`
and switches on the Iron's profile rather than declaring a second, parallel set of the four mods
Codex requires. No missing transitive dependency was found: the run loads `apprenticecodex 0.9.7.1`,
`irons_spellbooks 1.20.1-3.16.3`, `irons_lib 1.20.1-2.1.0`, `curios 5.14.1+1.20.1` and
`geckolib 4.8.4` and reaches `GAME TESTS COMPLETE`.

### Mixin coexistence (reference document §6.1, §11)

`runGameTestServer -PcodexProfile=true`, Codex's 71-mixin config and Runic Skills' 95 applied into
the same process: **197 mixin applications, zero `InjectionError`, zero `InvalidInjectionException`,
zero `@ModifyConstant conflict`, zero `Redirect conflict`, and all 278 gametests passed.** Every
shared target listed in the plan applied from both mods:

| Shared target | Codex | Runic Skills | Others in the same run |
| --- | --- | --- | --- |
| `io.redspace.ironsspellbooks.api.spells.AbstractSpell` | `AbstractSpellMixin`, `AbstractSpellManaBypassMixin` | none in this profile — `MixPaidSpellActions` is gated on the verified T.O. companion, which this profile does not install | — |
| `net.minecraft.world.entity.LivingEntity` | `LivingEntityAccessor`, `LivingEntityBulwarkGreatshieldMixin`, `LivingEntityMistFormMixin`, `LivingEntityPhalanxGuardMixin`, `LivingEntitySpectralWingMixin`, `LivingEntitySpellgunPowerMixin` | `MixGuardDamage`, `MixLivingEntity`, `MixLivingEntityAccess` | irons_lib, Iron's, BetterCombat, KubeJS, Curios, Caelus |
| `net.minecraft.world.entity.player.Player` | `PlayerBulwarkGreatshieldMixin`, `PlayerRemoteOwnerCastMixin` | `MixGuardDamage`, `MixPackMuleDrops`, `MixPlayerAction` | irons_lib, Iron's, BetterCombat, KubeJS |
| `net.minecraft.world.entity.Entity` | `EntityBulwarkGreatshieldPushMixin`, `EntityMistFormMovementRestrictionMixin`, `EntityRemoteOwnerCastMixin`, `EntitySharedFlagAccessor` | `MixPlayer` | Iron's, KubeJS, Curios |
| `net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity` | `AbstractFurnaceBlockEntityMixin` | `MixAbstractFurnaceBlockEntity` | — |
| `net.minecraft.world.inventory.EnchantmentMenu` | `EnchantmentMenuMixin` | `MixEnchantmentMenu` | — |
| `net.minecraft.server.network.ServerGamePacketListenerImpl` | `ServerGamePacketListenerImplBoundSwordMixin` | `MixPackMulePacketListener` | BetterCombat |

The highest-risk pair the scout identified — Runic Skills' `MixPaidSpellActions` (`@WrapMethod` on
`attemptInitiateCast`/`castSpell`) against Codex's `AbstractSpellMixin` (`@Redirect` on `onCast`) —
**was not exercised**, because `MixPaidSpellActions` applies only when the verified T.O. companion,
Travel Optics and Iron's are all present and this profile installs none of them. That combination
remains untested and is recorded here rather than claimed.

`apprenticecodex.MixSpellDispenserCastHelper` applied into
`jp.aquafactory.apprenticecodex.block.spelldispenser.SpellDispenserCastHelper` in the same run, and
`checkMixinRemapping` resolves both of its selectors against the real Codex jar (it is on the
verification task's target-artifact list), so a renamed or reshaped `tryCast` fails the build rather
than silently ceasing to apply.

### Runtime profiles run for Phase 5

| Profile | Command | Result |
| --- | --- | --- |
| Absence baseline (Phase 5) | `runGameTestServer` | **254/254 passed**, zero `NoClassDefFoundError` / `ClassNotFoundException` |
| Iron's only (Phase 5) | `runGameTestServer -PironsProfile=true` | **268/268 passed**, no `apprenticecodex` in the mod list |
| Iron's + Codex (Phase 5) | `runGameTestServer -PcodexProfile=true` | **278/278 passed** (the ten Codex cases register only here) |

The baseline run needed one fix to reach "zero missing-class errors": `/skills locks inspect spell`
named `IronsSpellGate` unconditionally, and although that class catches the `LinkageError` and
degrades correctly, resolving it on a server without Iron's still printed a `NoClassDefFoundError`
stack trace — and an absence log containing a missing-class error is indistinguishable from one that
is actually broken. The command now presence-checks before naming the class.

## Phase 1 result summary

| Question | Answer |
| --- | --- |
| Does Runic Skills still own counts when Bigger Stacks is absent? | Yes — `RUNIC`, unchanged format and bounds (`MAX_SERIALIZED_COUNT = 1048576`). |
| Does it install a second codec when Bigger Stacks is present? | No — `MixItemStackCount` and `MixFriendlyByteBuf` are not applied. |
| Is `MixItemStack` disabled? | No. Durability, enchantment-display and recovery hooks are in `MixItemStack` and always apply; only the two count hooks moved out. |
| Is Pack Mule capacity granted under a foreign provider? | No. Reference document §4.5 forbids a multiplier on top of Bigger Stacks, and its transformer library rewrites the three destination seams the perk adjusts. The perk is deferred and the reason is reported by `/skills locks representation`; §4.5 leaves resolving that path to release acceptance. |
| Is a supported external count treated as corrupt? | No — quarantine and validation apply only while Runic Skills owns the representation. |
