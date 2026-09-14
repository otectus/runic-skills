# Runic Skills Tests: loading failure and current-mod compatibility

Investigated 2026-09-08, Minecraft **1.20.1 / Forge 47.4.23 / Java 17**. This is a local,
unpublished Runic Skills **2.1.2** update on top of the existing development changes.

The full pack now creates a world and saves all dimensions in an isolated dedicated-server
test. This establishes a working loading profile. It does **not** certify every client screen,
recipe, mod mechanic, or Runic perk: specific remaining gaps are recorded below.

## What caused the freeze and subsequent update failures

| Failure | Evidence | Applied solution |
|---|---|---|
| Original world preparation exhausted its Java heap | `crash-2026-09-08_15.41.42-client.txt`: `OutOfMemoryError`, 0 MiB free of a 4096 MiB maximum; latest.log shows allocation failures during spawn preparation | Validation passed with 8192 MiB. CurseForge reverted the attempted profile override, but the active client launched at 16:42 now uses an inherited **11648 MiB** heap. Retain that effective setting. The host has 32 GB RAM. |
| Duplicate GeckoLib mod ID after updating the pack | Both `geckolib-aC5KMoNg.jar` and `geckolib-forge-1.20.1-4.8.4.jar` declare 4.8.4 | Keep the independently hash-verified official Modrinth `aC5KMoNg` artifact; quarantine the duplicate. The cached CurseForge attribution for the retained jar was misleading. |
| Runic T.O. companion rejected current Iron's Spellbooks | Companion required exactly 3.15.0; instance has 3.16.3 | Support the two exact version/SHA-256 pairs. Both the mixin gate and companion use the same profile definition. |
| T.O. 6.3.0 cannot link current Cataclysm/ISS | Missing `items/Dungeon_Eye/DungeonEyeItem`, moved particle classes and ISS Dead King goal | Use published **BielGG's Spells 1.4-hotfix**, which supplies the upstream compatibility transformations. Tested alongside Runic's companion. |
| Starcatcher 3 rejects old cross-mod fish JSON | `saintsdragons:moop` uses the old schema; Starcatcher's built-in `tide:shooting_starfish` uses the wrong bait representation | Generate two conditional in-memory resource overrides from the installed original data, preserving catch weights, restrictions and rewards. |
| Current Galosphere removed its silver ingot | Create/Overgeared cooking recipes request `galosphere:silver_ingot`; serializer throws and aborts world data loading | Guard three obsolete recipes with `forge:item_exists`, only when that actual output is still present in the original recipe and the item is absent. |

The original memory crash happened **before** the later mod update. These are separate issues;
changing memory alone would not repair the updated pack's class-linkage and registry failures.
No original world save is used or modified by the validation servers.

The published bridge is an additional content mod, not a patch-only package. Its extra recipes
can also refer to optional mods absent from this pack. Its jar is installed as a separate
upstream artifact; none of its code/assets is copied into Runic. See the
[publisher's exact 1.4-hotfix file](https://www.curseforge.com/minecraft/mc-mods/bielggs-spells-addon/files/8742968).
T.O.'s current [6.3.0 release](https://www.curseforge.com/minecraft/mc-mods/to-tweaks-irons-spells/files/7424522)
was built for the older Iron's profile. A future maintained T.O. release or a separately maintained
patch-only bridge can replace this dependency after the same tests pass.

## Runic changes

- Correct L2 Tabs compilation and reflection to use `TabToken.TabFactory` and the actual
  `TabToken` return descriptor. The previous local API stub described a nonexistent API.
  Compile-only source mirrors replace it; no L2 classes are bundled in Runic.
- Update Angler's Luck for Starcatcher 3.1.4.1. The old treasure loot table no longer exists.
  Its replacement uses the native fish and modifiers from the player's live fishing bob,
  delegates to the native per-fish treasure API, and grants nothing for a missing/removed bob.
  Legacy Starcatcher 2.3 retains its loot-table path.
- Extend the optional T.O. companion's exact validated Iron's profiles to 3.16.3 while retaining
  3.15.0. Unknown versions/hashes remain conservative rather than bypassing the native binding check.
- Supply the fish and silver recipe adaptations as generated data. Third-party jars and data
  assets are not rewritten. The fish migration runs only for the exact Starcatcher profile.
- Extend static verification to class-valued mod mixin targets and report unresolved inherited
  references separately from confirmed missing members.

Older Gradle compile versions are often **minimum API baselines**, not maximum runtime versions.
Blindly upgrading every compile dependency would drop older working profiles and can change
ForgeGradle's shared dependency resolution. Keep immutable compiler inputs, select exact runtime
artifacts, and raise a compile baseline only when the adapter actually needs a new API.

## Version review for every mod

The [127-row mod matrix](compatibility/2026-09-08-mod-matrix.md) records every original top-level
jar, its version/source, the applicable Runic integration, and the recommended action.
The [machine-readable manifest](compatibility/2026-09-08-pack-manifest.json) includes SHA-256 pins.

Live Modrinth lookup found **86 current stable release hashes and 8 current prerelease hashes**.
The other 33 files include CurseForge-only distributions, duplicate GeckoLib and three local
artifacts. Their publisher/local provenance is identified in the matrix. Minecraft 1.21,
NeoForge-only releases and unrelated forks are not drop-in updates for this instance.

“Latest” here means the current **Forge 1.20.1 stable release**, retaining existing beta lines
where already selected by the pack. Notable newer optional betas/snapshots include BOMD 1.1.2,
CERBON API 1.1.0, Mantle 1.11.117, and newer MineColonies-family snapshots. The working Mantle
1.11.113 profile is intentionally retained; downgrading it to the older stable release would
undo the existing Tinkers' Jewelry initialization fix. The latest Tinkers 3.12 beta requires
separate profile validation and is not silently substituted for stable 3.11.2.166.

## Validation and its limits

Final build and runtime evidence is recorded in
[the validation receipt](compatibility/2026-09-08-validation.json).

- Gradle `build tomCompatJar productionValidationJar --offline`: all required build checks and
  **358 JUnit tests** passed, including version/refmap checks and new migration/profile tests.
- Static installed-jar scan: **465 referenced native members**, **0 confirmed missing members**;
  **35 inherited references remain unresolved** without the Minecraft parent classes in that
  scanner. Nine absent-owner references concern optional integrations. Static existence is
  not a behavior test.
- Extended installed-pack mixin check: **104 references, 0 missing**, including five accepted
  obfuscated/development selector aliases.
- Complete-pack world generation reached `Done (47.611s)` in the first successful run; total
  process duration including mod loading, observation and clean shutdown was 161 seconds.
- Complete-pack native checks verified both migrated fish records, absence of the three invalid
  silver recipes, and the live Starcatcher treasure API. Native treasure was returned for
  **234 of 243 loaded fish profiles**; missing/removed bob scenarios returned no bonus.
- A separate production suite passed **40/40 tests** against the latest Tide, Simply Swords/More
  and T.O./Iron's versions. The exact final complete-pack jars passed **4/4 native checks**,
  world loading and a clean save in 172 seconds. The 40-test suite is a focused cohort:
  its results do not promote conservative fishing features
  when Fishing Real, Hybrid Aquatic or Starcatcher changes their ownership in the full pack.

The client launched after installation was independently observed using `-Xmx11648m`.
Its log confirms spawn preparation completed in **5289 ms** and the player joined at
**16:49:38**. This confirms the previous 0% world-loading barrier was passed in the actual
instance. JEI subsequently spent additional time indexing this large pack's recipes.
Inventory screens, L2 tab rendering, gun animations and multiplayer packet paths still need
feature-specific client playtests; joining a world does not validate those interactions.

## Remaining work toward total compatibility

1. **Finish fishing delivery bridges.** Tide 2.1.1 shares catch handling with Fishing Real and
   Hybrid Aquatic; Runic intentionally suppresses bonuses without a verified delivery receipt.
   Starcatcher also owns its minigame window. Add native receipt adapters with canceled-catch,
   rejected-spawn, full-inventory, duplicate-event, bait/wear, and save/reload regression tests
   before enabling these bonuses. Starcatcher perks still recognize its own item namespace;
   foreign fish and native minigame/bait changes are not fully integrated.
2. **Repair upstream recipe schemas with explicit data patches.** The successful load logged
   296 nonfatal recipe errors. The [exact IDs and errors](compatibility/2026-09-08-upstream-data-errors.json)
   are retained. These errors are mostly deliberate disabled recipes or missing optional
   integrations, but some are genuinely unavailable content. Do not invent substitute costs
   or globally suppress all recipe errors.
   - Dragonsteel 0.81 supplies **182** `minecraft:false` conditions across SpartanFire,
     Ice and Fire and other namespaces. Change them to `forge:false`; preserve intentional
     removal. Three recipes also use `forge:false` as a serializer instead of a condition.
   - Nichirin Dynasty and Epic Knights Addon retain old smithing serializers. Migrate to
     `smithing_transform` with an explicit template policy; map old `epicsamurai` item IDs
     only to verified current counterparts. Samurai Dynasty/Antique Legacy have removed items.
   - Scorched Guns needs its Create condition repaired, one unused pattern key removed,
     and its optional experience fluid guarded. Overgeared, Malum, Tinkers add-ons and the
     published bridge need mod/item-exists guards for optional or removed content.
   - Lili's Lucky Lures has six recipes whose fish-trap serializer is absent in this profile;
     inspect its registration/configuration before promising that feature works.
3. **Correct resource ownership upstream.** Saint's Dragons reads MCA dialogue JSON as its
   own dialogue schema. Namespace filtering avoids these rejected foreign documents.
   Validate both NPC interaction systems after the fix.
4. **Test client interactions and pack rules.** Check L2 tabs, skill UI/reconnect, gun fire and
   reload, spell casts, fishing providers, Tinkers crafts/repairs, food cooking and village/quest
   rewards. Confirm that recipe aliases do not introduce crafting XP/refund cycles.
5. **Maintain a versioned test matrix.** Freeze exact runtime hashes, rerun static linkage and
   native tests for each update, then boot the complete pack and test a copied existing world.
   Keep optional integrations isolated and unknown versions conservative. Do not widen all
   dependency ranges and call that compatibility.

## Files, installation and rollback

Core: `build/libs/runicskills-2.1.2.jar`; optional T.O. companion:
`build/compat-libs/runicskills-tom-compat-2.1.2.jar`.
The separate `build/validation-libs` jar is a test harness and must **not** be installed in the
user's instance. Validation worlds live entirely beneath this repository's `build` directory.

The installer backs up the original two Runic jars, duplicate GeckoLib and instance metadata
under `Runic Skills Tests/runicskills-backups/<timestamp>/`, verifies hashes, installs the core,
companion and published bridge, quarantines duplicate GeckoLib, and requests an 8 GB memory override.
The running CurseForge app restored the inherited setting; the active client's actual 11.4 GB
allocation was verified and retained instead. File edits alone do not override a running launcher's cache.
The backup's `installation-receipt.json` records exact hashes and paths.

Installed backup: `runicskills-backups/2026-09-08_16-40-36/` within the named instance.

For rollback with Minecraft closed, restore the two Runic jars from that backup and move the added
BielGG jar out of `mods`. The profile remains on its inherited memory setting; the backed-up
metadata records 4096 MiB and override off, while the current effective global launch allocation is 11648 MiB.
Keep only one GeckoLib jar. Restore the entire backed-up instance JSON only if no subsequent
CurseForge profile changes need preserving. Original saves were not changed.
