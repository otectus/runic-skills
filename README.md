# Runic Skills

A RPG-style progression mod for Minecraft 1.20.1 Forge. Level ten skills through the actions you already take, unlock perks and passives, gate equipment behind skill thresholds, and earn titles for milestones your world rarely sees.

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-62B47A)
![Forge 47.3.0+](https://img.shields.io/badge/Forge-47.3.0%2B-1E2D3C)
![Java 17](https://img.shields.io/badge/Java-17-F89820)
![License Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue)

Forked from JustLevelingFork in v0.9.0, rebranded and reworked as Runic Skills in v0.9.1. First stable 1.0.0 release shipped the consolidated 0.9.x feature set (item-lock master toggle, perk/passive kill switches, perk-group datapacks) plus the tooltip-matches-enforcement fix. The magic tree grew across the 1.0.x and 1.2.x line — 78+ perks across Iron's Spells 'n Spellbooks (46), Apotheosis + Apothic Attributes (12), Ars Nouveau (11), and cross-mod synergies that activate only when multiple source mods are installed together. **1.1.0 ships the dedicated-server safety refactor** (YACL is now genuinely optional on servers — pre-1.1.0 the README claimed this but the code crashed at boot), the L2Tabs class-load fix, and a triage of CurseForge user reports including the `/globallimit` command bug and the Scholar/enchantment-hiding side effect. **1.2.0** introduces a public Forge event API (`SkillLevelUpEvent`, `PassiveLevelUpEvent`, `PerkToggleEvent.Pre`/`Post`, `TitleEarnedEvent`) so external Java mods and KubeJS scripts can hook level changes without reflection, plus four deferred-backlog perks (Apothic Apprentice, Gem-Threaded Armor, Spellsocket, Resonant Affixes), per-integration master-toggle booleans, tooltip word-wrap at GUI scale 4, named-layer HUD overlays so resource packs can relocate them, and bulk-level passives via Shift/Ctrl/Alt-click. **1.3.0** ships two data-driven features authors have been asking for: a custom-skill-visuals datapack layer (override per-skill overview/detail/background art via `data/<ns>/runicskills/skill_visuals/*.json` with full `namespace:path` ResourceLocation support) and an FTB Quests integration that registers six native task types (`skill_level`, `global_level`, `perk_rank`, `passive_level`, `title_unlocked`, `title_selected`) wired through an isolated quest bridge so the mod boots cleanly without FTB Quests installed; KubeJS perk/passive helpers also accept arbitrary `namespace:path` texture ids now. **1.5.0** trims the perk roster to match the target Runecraft pack — **all Botania, Blood Magic, and Enigmatic Legacy perks (and their integrations / lock providers) have been removed** — and fully wires Apotheosis: the APOTHEOSIS_WISDOM enchantment-cap perk is live, and socketing a gem now requires a Fortune level scaled by the gem's rarity (config toggle `apothEnableGemRarityGating`, default on). See [`CHANGELOG.md`](CHANGELOG.md) for the full breakdown.

---

## Table of contents

- [Overview](#overview)
- [Skills](#skills)
- [Perks, passives, and titles](#perks-passives-and-titles)
- [Controls](#controls)
- [Installation](#installation)
- [Supported mod integrations](#supported-mod-integrations)
- [Commands](#commands)
- [Configuration](#configuration)
- [Custom skill visuals](#custom-skill-visuals)
- [FTB Quests integration](#ftb-quests-integration)
- [Version matrix](#version-matrix)
- [Building from source](#building-from-source)
- [KubeJS scripting hook](#kubejs-scripting-hook)
- [Server / multiplayer notes](#server--multiplayer-notes)
- [Reporting bugs](#reporting-bugs)
- [Credits and licence](#credits-and-licence)

---

## Overview

Runic Skills adds an unobtrusive, vanilla-respecting progression layer on top of Minecraft. Every swing, craft, mine, and spell contributes to one of ten core skills. Each skill levels up independently and funds two kinds of rewards:

- **Perks** — discrete, toggleable abilities you unlock at specific skill levels (some with multi-rank tiers — e.g. *Haggler I/II/III* for progressively deeper villager discounts).
- **Passives** — permanent stat modifiers you spend skill points into, e.g. +Attack Damage, +Max Health, +Mana Regen, +Armor Toughness.

On top of the skill system sits a **titles** subsystem: ~50 earnable prefixes with configurable requirements (kill the Ender Dragon 10 times, mine a Netherite Block, craft 100 Totems) that players can pick to display above their name.

The mod is designed to feel flexible in custom modpacks — almost every number lives in a YACL-backed config, condition types can be registered by other mods, and the skill/perk/passive data can be extended via KubeJS scripting.

---

## Skills

Ten core skills; the player UI calls these "Skills" (internally "aptitudes"):

| Skill | Focuses | Typical level-up actions |
|---|---|---|
| **Strength** | Melee damage, heavy armour, sprinting | Melee kills, wearing heavier gear |
| **Constitution** | Max health, kb resistance, armour absorption | Taking survivable damage |
| **Dexterity** | Speed, crit chance, bow & projectile damage | Ranged kills, bow shots landed |
| **Endurance** | Stamina, air supply, environmental resistance | Long swims, sprinting, cold exposure |
| **Intelligence** | Enchanting, XP gain, scholar perk | Enchanting, reading books |
| **Building** | Block-placement speed, haggle discounts, storage perks | Placing blocks, villager trading |
| **Wisdom** | Brewing, farming, and mob-interaction bonuses | Brewing, villager interactions |
| **Magic** | Mana pool, spell damage, mana regen (spellbook mods) | Casting spells, studying glyphs |
| **Fortune** | Lucky drops, rare-loot multipliers | Mining ore, breaking grass, fishing |
| **Tinkering** | Smithing, crafting refunds, tool durability | Crafting, smithing table operations |

Total level is the sum of all ten; a global cap (`playersMaxGlobalLevel`) can be set via `/globallimit` or the config.

---

## Perks, passives, and titles

- **Perks** range from vanilla-friendly (Haggler, Scholar, Wormhole Storage) to heavily mod-integrated (Spell Echo for Irons Spellbooks, Glyph Mastery for Ars Nouveau, Ricochet for firearm mods). Most are toggleable from the skills screen so you can disable any perk you don't want active.
- **Passives** appear in the skill detail page — spend skill levels on them up to tier caps you unlock with skill level. Attribute additions come from the vanilla or Apothic Attributes registries, so they stack correctly with other gear and mods.
- **Titles** are earned, not chosen. Hide or reveal their unlock requirements via the `titlesHideRequirements` config. Each title is a translation key so modpack makers can reskin them freely without touching code.

---

## Controls

- **Y** — default keybind to open the Skills screen (rebindable in Minecraft Controls).
- **In the inventory** — a Skills tab appears next to the inventory tab; integrates with **L2Tabs** and **Legendary Tabs** when either is installed (see [Supported mod integrations](#supported-mod-integrations)).
- **Hover a perk with `Shift` held** — reveals the description, level requirement, rank, and next-rank unlock level.

---

## Installation

### Players
1. Install **Minecraft Forge 47.3.0+** for Minecraft **1.20.1**.
2. Drop the `runicskills-1.5.0.jar` from the [latest release](https://github.com/otectus/runic-skills/releases/latest) into your `mods/` folder.
3. Install **[YACL (Yet Another Config Lib v3)](https://modrinth.com/mod/yacl)** version 3.4.2+ — required client-side for the configuration UI.
4. Optionally install any of the supported integration mods (see below) — Runic Skills auto-detects them and enables relevant perks/passives/lock-items.

No client-side-only nor server-side-only variants; one jar on both sides.

### Server operators
- Drop the same jar on the dedicated server. YACL is **not** required server-side (1.1.0+; pre-1.1.0 the mod required YACL on the server even though the docs said otherwise).
- Syncs skill, perk, passive, and title state to clients via a versioned custom Forge network channel (`PROTOCOL_VERSION=5`). Old clients fail fast instead of desyncing.
- Optional ops-only commands in `/skills`, `/titles`, `/globallimit` (see [Commands](#commands)).

---

## Supported mod integrations

Runic Skills detects installed mods at runtime and enables matching content without needing a config tweak. All integrations are reflectively loaded, so **missing dependencies never crash the mod**.

| Integration | Effect when present |
|---|---|
| **KubeJS** / Rhino | `SKILL_LEVELUP` event, plus ability to register custom skills, perks, passives, titles, and conditions from scripts |
| **Ars Nouveau** | 11 form/school perks (Form Focus: Projectile/Touch/Self, Wild Manipulation, per-school Hedgewitch/Emberforged/Stormcaller/Geomancer/Conjurer/Abjurer/Arcane Weaver) on top of the existing spell-damage scaling, mana regen passives, glyph mastery, and familiar gating |
| **Irons Spellbooks** | 46 magic-tree perks: generic mana/casting (Wellspring, Quickening, Reservoir, Tempo, Spellweaver, Mana Bulwark, Arcane Reprieve, Mana Surge…), per-school triplets (X-mancer / X-Warded / X-Catalyst for all nine schools including Eldritch — including the blood-school perks Blood Attunement / Blood-mancer / Blood-Warded / Blood Catalyst and Blood Fury), summon perks (Lord of the Dead, Life Leech Bound), plus the existing Spell Echo, Arcane Shield, and school-attunement gating |
| **Apotheosis** | Affix gating, gem attunement, socket bonus interactions, Socket Virtuoso (+N sockets), Affix Affinity (scales with Rare+ affix-item count), Apothic Apprentice (higher-tier +N sockets, stacks with Socket Virtuoso), Gem-Threaded Armor (Endurance: flat ARMOR per equipped socket), Spellsocket (Magic: +effective spell level per N equipped sockets), Resonant Affixes (Magic: ISS spell-damage per Rare+ affix item), Apotheosis Wisdom (enchantment-cap boost via Placebo's GetEnchantmentLevelEvent), plus gem rarity gating — socketing a gem requires a Fortune level scaled by the gem's rarity (uncommon→4, rare→10, epic→18, mythic→26, ancient→32), toggled by `apothEnableGemRarityGating` (default on) |
| **Apothic Attributes** | Extended attribute pool for passives plus 10 combat perks (Apothic Critical Mastery, Vampiric Fangs, Reaper's Edge, Evasive, Arrow Mastery, Earthbreaker, Apothic Scholar, Spectral Ward, Ghostbound, Heart of the Healer) |
| **Cross-mod synergy** | 6 Schoolbridges (Iron's school spell-power bleeds into matching Ars school damage), Unified Arcana (Ars casts refund ISS mana), Triple Threat (+% mana/regen/spell-power when Iron's + Ars + Apotheosis all loaded), Affix Focus (+ISS spell levels when 4+ Rare Apoth items equipped) |
| **Farmers Delight** | Lock items on knives and cooking gear |
| **Ice and Fire** | Dragon-slayer perk + dragon-item lock list |
| **Cataclysm** | Fire-dragon weapon / gear lock items |
| **Mowzie's Mobs** | Mowzie-weapon lock list |
| **More Vanilla** | Additional lock-item coverage |
| **Fantasy Armor** | Armour-piece lock list |
| **Jewelcraft** | Jewelry lock list |
| **Locks** | Lock-item tier generation |
| **Natures Aura**, **Bosses of Mass Destruction**, **Jet and Elias**, **Nichirin Dynasty**, **Saints Dragons**, **Samurai Dynasty**, **Siege Machines**, **Spartan suite**, **Stalwart Dungeons** | Lock-item tier generation |
| **Crayfish Gun Mod (unofficial)**, **Scorched Guns 2**, **TacZ**, **PointBlank** (Vic's) | Gun-fire events honour Runic Skills perks/locks |
| **L2Tabs** | Registers the Skills tab in L2Tabs' strip (priority 3500) |
| **Legendary Tabs** (Sfiomn) | Registers a native `TabBase` for the Legendary Tabs sidebar (priority configurable) |
| **FTB Quests** (since 1.3.0) | Six native task types (`skill_level`, `global_level`, `perk_rank`, `passive_level`, `title_unlocked`, `title_selected`) — see the [FTB Quests integration](#ftb-quests-integration) section |

If you're a mod author and want Runic Skills to integrate with your mod, open an issue or a PR — each integration is a single Java class with an `isModLoaded()` gate, see [`src/main/java/com/otectus/runicskills/integration/`](src/main/java/com/otectus/runicskills/integration/).

---

## Commands

All `/skills*` operator commands require OP level 2.

| Command | Effect |
|---|---|
| `/skills <player> <skill> <level>` | Set a player's skill to a specific level. |
| `/skills <player> <skill> add <amount>` | Add (or subtract, with negative) to a player's skill. |
| `/listskills <player>` | Dump all ten skill levels for a player. |
| `/skillsreload` | Re-read the datapack-side config (lock items, titles, conditions) without a server restart. |
| `/respec <player>` | Reset all passives and perks (skill levels preserved) and refund points. |
| `/registeritem <item-id>` | Register an item as level-gated at runtime (persisted to config on reload). |
| `/globallimit <cap>` | Set the global-level cap. |
| `/titles <player> <title> set true\|false` | Grant or revoke a title. |

---

## Configuration

Configuration surfaces (all under `config/RunicSkills/`):

1. **Common config** (`config/RunicSkills/runicskills.common.json5`) — YACL-managed, editable via **Mods → Runic Skills → Config**. Covers skill max-level, XP costs, UI overlays, per-integration toggles, the disabled perk/passive/power lists, and the `enableItemLocks` master toggle.
2. **Lock-item list** (`config/RunicSkills/runicskills.lockItems.json5`) — the per-item skill requirements. Edited directly in the file or with `/registeritem`; **not** shown in the YACL screen.
3. **Client config** (`config/runicskills-client.toml`) — Forge config spec. Covers rendering toggles (critical-roll overlay, lucky-drop overlay, perk mod-name display), sort orders, and the Legendary Tabs priority.

Title definitions and their conditions live as datapack JSON under `data/runicskills/titles/*.json`. After editing any config file in-world, apply it with `/skillsreload` (no restart needed) — this re-reads every config file and re-syncs to clients.

### Disabling item locking

There are **four distinct actions** — pick the one that matches what you want:

1. **Turn the whole feature off (master toggle).** Set `enableItemLocks: false` in `config/RunicSkills/runicskills.common.json5`, or untick **Enable item locks** under **Mods → Runic Skills → Config → General**. Apply by saving in the UI, running `/skillsreload`, or restarting. This disables *every* lock — config entries and integration-generated ones alike.
2. **Disable specific locked items.** Edit `config/RunicSkills/runicskills.lockItems.json5` and remove the entries you don't want (or run `/registeritem <skill> 0` while holding the item to drop a requirement). Apply with `/skillsreload`.
3. **Disable an integration's auto-generated locks.** Each integration has a per-lock toggle (e.g. `spartanEnableLockItems`) and a master toggle (e.g. `enableSpartanIntegration`) in the common config. Setting either off stops that integration's locks; the master toggle also disables the integration's other hooks.
4. **Deleting `runicskills.lockItems.json5` does NOT disable locking.** On the next launch the mod **regenerates the default lock list** (an `INFO` line names the file in the log). To turn locking off, use the master toggle in action 1 — not file deletion.

---

## Custom skill visuals

Since 1.3.0, the overview-grid icon, detail-page icon, and detail-page background of every skill can be overridden via datapack. Files live at `data/<namespace>/runicskills/skill_visuals/<id>.json`:

```json
{
  "skill": "magic",
  "overview_icon": "my_pack:textures/gui/runicskills/magic_overview.png",
  "detail_icon": "my_pack:textures/gui/runicskills/magic_detail.png",
  "background": "my_pack:textures/gui/runicskills/magic_bg.png"
}
```

- `skill` is required and identifies the target by its lowercase name (`strength`, `constitution`, `dexterity`, `endurance`, `intelligence`, `building`, `wisdom`, `magic`, `fortune`, `tinkering`).
- `overview_icon`, `detail_icon`, and `background` are all optional. Any field omitted falls back to the legacy hardcoded asset, so you can override a single slot without re-supplying the rest.
- Texture ids accept either a fully-qualified `namespace:path` or a bare path. Bare paths resolve to the `runicskills` namespace for parity with the legacy KubeJS helper.
- Reloads pick up overrides via the standard datapack reload path (`/reload` or world load). Removing the JSON restores the default on the next reload.

**Client-asset caveat.** Texture ids must point at assets the **client** actually has. Datapack overrides on a dedicated server don't conjure client textures out of thin air — ship the PNGs in a resource pack (or as part of the pack's overrides folder) alongside the JSON.

KubeJS perk/passive scripts also accept namespaced texture ids since 1.3.0:

```js
// Before 1.3.0: had to ship the texture inside the runicskills namespace.
// Since 1.3.0: any mod's texture works.
Perk.add('test_perk', 'magic', 1, 'irons_spellbooks:textures/item/blank_rune.png', [Value.of(...)])
```

The progressive 4-tier "locked icon" array on each skill (the icon that fills in as you level up) is **not** part of the override — it's a single static slot per skill. Pack authors who want progressive art can replace the underlying `runicskills:textures/skill/<name>/locked_*.png` files via a resource pack instead.

---

## FTB Quests integration

Since 1.3.0, with FTB Quests Forge installed, Runic Skills registers six task types you can add directly in the FTB Quests editor:

| Task id | Fields | Completes when |
|---|---|---|
| `runicskills:skill_level` | `skill` (string), `required_level` (int) | named skill ≥ required level |
| `runicskills:global_level` | `required_total` (int) | sum of all skill levels ≥ required total |
| `runicskills:perk_rank` | `perk` (string), `required_rank` (int, default 1) | named perk's rank ≥ required rank |
| `runicskills:passive_level` | `passive` (string), `required_level` (int) | named passive ≥ required level |
| `runicskills:title_unlocked` | `title` (string) | named title unlocked on the player |
| `runicskills:title_selected` | `title` (string) | player is actively wearing the named title |

Example task SNBT / JSON:

```json
{ "type": "runicskills:skill_level",     "skill": "magic",        "required_level": 20 }
{ "type": "runicskills:global_level",    "required_total": 100 }
{ "type": "runicskills:perk_rank",       "perk": "berserker",      "required_rank": 1 }
{ "type": "runicskills:passive_level",   "passive": "magic_resist","required_level": 5 }
{ "type": "runicskills:title_unlocked",  "title": "administrator" }
{ "type": "runicskills:title_selected",  "title": "administrator" }
```

**Sticky completion is the default.** Once a task completes, it stays complete even if the player respecs or levels a passive back down — matches the "checked off, stays checked" UX players expect from FTB Quests. To opt into live-threshold semantics (task progress reflects current state, including regressions), add `"sticky": false` to the task block:

```json
{ "type": "runicskills:passive_level", "passive": "magic_resist", "required_level": 5, "sticky": false }
```

**Backfill on login.** When a player logs in (or respawns / is cloned after a death), every Runic Skills task is re-evaluated. Authors can ship FTB Quests data after players have already leveled past the threshold without leaving them with phantom-incomplete quests.

**Configuration.** Disable the integration entirely via `enableFTBQuestsIntegration = false` in `runicskills.common.json5` (or **Mods → Runic Skills → Config → Integrations**). Task types are not registered when the toggle is off; quests using these types will appear as "unknown" in the editor.

---

## Version matrix

| Runic Skills | Minecraft | Forge | Java | FTB Quests Forge (optional) |
|---|---|---|---|---|
| 1.5.x | 1.20.1 | 47.3.0+ | 17 | `[2001.4,)` (tested against 2001.4.22) |

---

## Building from source

**Requirements:**
- JDK **17** (Parchment mappings pin to 17)
- Gradle 8.10 (the `gradlew` wrapper pulls this automatically)

**Build:**
```
./gradlew build
```
Artifacts land in `build/libs/`:
- `runicskills-<version>.jar` — mod jar for distribution.
- `runicskills-<version>-all.jar` — jar-in-jar bundle (includes bundled deps).

**Local-jar dependencies.** Two integrations compile against jars that aren't redistributed with this repo. Drop them into `libs/` before building:
- `libs/legendarytabs-1.20.1-1.1.3.1.jar` — Sfiomn's Legendary Tabs.
- `libs/l2tabs-0.3.3.jar` — Minecraft-LightLand's L2Tabs.

If either jar is absent, Gradle will fail at dependency resolution with a `Could not find <coord>` error. See [`build.gradle`](build.gradle) lines 196–202 for the comments.

**Other tasks:**
- `./gradlew compileJava` — compile only (faster iteration).
- `./gradlew runClient` — launch a dev Minecraft client with the mod loaded.
- `./gradlew checkSidedImports` — sided-import lint (enforces that no `net.minecraft.client.*` or `com.mojang.blaze3d.*` imports leak into common-side code). Runs automatically as part of `check`.

---

## Scripting hooks and the public Forge event API

Since **1.2.0**, Runic Skills fires four public `PlayerEvent`-subclass events on `MinecraftForge.EVENT_BUS` that external Java mods and KubeJS scripts can subscribe to. The full reference is in [`docs/API_EVENTS.md`](docs/API_EVENTS.md); a summary:

| Event | Cancelable | Fires from |
|---|---|---|
| `SkillLevelUpEvent` | ✅ | `SkillLevelUpSP.handle`, after validation, before XP consumption |
| `PassiveLevelUpEvent` | ✅ | `PassiveLevelUpSP.handle` and `PassiveLevelDownSP.handle` |
| `PerkToggleEvent.Pre` | ✅ | `TogglePerkSP.handle`, after built-in validation, before state mutation |
| `PerkToggleEvent.Post` | ❌ | `TogglePerkSP.handle`, after state mutation |
| `TitleEarnedEvent` | ❌ | `Title.setRequirement` when a title unlocks for a player |

**KubeJS script (new style — recommended):**
```js
// kubejs/server_scripts/runicskills_hooks.js
ForgeEvents.onEvent('net.minecraftforge.event.entity.player.PlayerEvent$SkillLevelUpEvent', event => {
    if (event.skill.name === 'magic' && event.newLevel > 30) {
        event.entity.tell(`Magic ${event.newLevel} reached!`);
    }
});
```

**Legacy `SKILL_LEVELUP` event** (pre-1.2.0) still fires for backward compatibility via a deprecated shim in `KubeJSIntegration` — but new scripts should subscribe to the Forge event directly. The legacy reflection bridge is marked `@Deprecated(forRemoval = true)` and scheduled for removal in a future major.

---

## Server / multiplayer notes

- **Protocol version** — the custom Forge network channel uses `PROTOCOL_VERSION=5`; clients on an older Runic Skills version will be rejected at join. Running a mixed-version modpack server is not supported.
- **Config sync** — the server is authoritative for the common config. On join, the server pushes its values to each client; the local `runicskills.common.json5` on the client is read for display defaults only.
- **Title name custom display** — titles apply a display-name prefix via `setCustomName`. Set `titlesUseCustomName=false` in the common config to disable if you run a chat/nickname mod that collides.

---

## Reporting bugs

- **Bug reports and feature requests:** use the [GitHub issues](https://github.com/otectus/runic-skills/issues) tracker.
- When reporting a crash, attach your `logs/latest.log` (or the crash report under `crash-reports/`) and your mod list (`mods.txt` output or a listing of your `mods/` directory). Include your `config/RunicSkills/runicskills.common.json5` if the bug is config-related.
- Security issues: open a private advisory instead of a public issue.

---

## Credits and licence

- Originally forked from **JustLevelingFork**, with significant reworks to the UI, perk/passive system, title system, and integration architecture.
- Title assets and the "Runic Skills" rebrand by [@otectus](https://github.com/otectus).
- GUI textures, panels, and card artwork by the Runic Skills authors.
- Released under the **Apache License 2.0**. See [`LICENSE`](LICENSE) (if present) or the [SPDX entry](https://choosealicense.com/licenses/apache-2.0/) for terms.

### Third-party mods tested against

Listed under [Supported mod integrations](#supported-mod-integrations). Runic Skills does not bundle any third-party code; each integration is a thin optional compat layer that respects the upstream mod's own licence.
