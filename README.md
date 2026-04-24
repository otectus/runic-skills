# Runic Skills

A RPG-style progression mod for Minecraft 1.20.1 Forge. Level ten skills through the actions you already take, unlock perks and passives, gate equipment behind skill thresholds, and earn titles for milestones your world rarely sees.

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-62B47A)
![Forge 47.3.0+](https://img.shields.io/badge/Forge-47.3.0%2B-1E2D3C)
![Java 17](https://img.shields.io/badge/Java-17-F89820)
![License Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue)

Forked from JustLevelingFork in v0.9.0, rebranded and reworked as Runic Skills in v0.9.1. First stable 1.0.0 release ships the consolidated 0.9.x feature set (item-lock master toggle, perk/passive kill switches, perk-group datapacks) plus the tooltip-matches-enforcement fix.

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
2. Drop the `runicskills-1.0.0.jar` from the [latest release](https://github.com/otectus/runic-skills/releases/latest) into your `mods/` folder.
3. Install **[YACL (Yet Another Config Lib v3)](https://modrinth.com/mod/yacl)** version 3.4.2+ — required client-side for the configuration UI.
4. Optionally install any of the supported integration mods (see below) — Runic Skills auto-detects them and enables relevant perks/passives/lock-items.

No client-side-only nor server-side-only variants; one jar on both sides.

### Server operators
- Drop the same jar on the dedicated server. YACL is **not** required server-side.
- Syncs skill, perk, passive, and title state to clients via a versioned custom Forge network channel (`PROTOCOL_VERSION=4`). Old clients fail fast instead of desyncing.
- Optional ops-only commands in `/skills`, `/titles`, `/globallimit` (see [Commands](#commands)).

---

## Supported mod integrations

Runic Skills detects installed mods at runtime and enables matching content without needing a config tweak. All integrations are reflectively loaded, so **missing dependencies never crash the mod**.

| Integration | Effect when present |
|---|---|
| **KubeJS** / Rhino | `SKILL_LEVELUP` event, plus ability to register custom skills, perks, passives, titles, and conditions from scripts |
| **Ars Nouveau** | Spell-damage scaling by Magic, mana regen passives, glyph mastery perk, familiar gating |
| **Irons Spellbooks** | Spell echo, arcane shield, attunement perks, school bonuses, spell gating |
| **Apotheosis** | Affix gating, gem attunement, socket bonus interactions |
| **Apothic Attributes** | Extended attribute pool for passives (armor pierce, cold damage, etc.) |
| **Blood Magic** | Lock items for sigils, sentient gear, orbs |
| **Farmers Delight** | Lock items on knives and cooking gear |
| **Ice and Fire** | Dragon-slayer perk + dragon-item lock list |
| **Cataclysm** | Fire-dragon weapon / gear lock items |
| **Mowzie's Mobs** | Mowzie-weapon lock list |
| **Enigmatic Legacy** | Cursed-item lock list |
| **More Vanilla** | Additional lock-item coverage |
| **Fantasy Armor** | Armour-piece lock list |
| **Jewelcraft** | Jewelry lock list |
| **Locks** | Lock-item tier generation |
| **Natures Aura**, **Bosses of Mass Destruction**, **Jet and Elias**, **Nichirin Dynasty**, **Saints Dragons**, **Samurai Dynasty**, **Siege Machines**, **Spartan suite**, **Stalwart Dungeons** | Lock-item tier generation |
| **Crayfish Gun Mod (unofficial)**, **Scorched Guns 2**, **TacZ**, **PointBlank** (Vic's) | Gun-fire events honour Runic Skills perks/locks |
| **L2Tabs** | Registers the Skills tab in L2Tabs' strip (priority 3500) |
| **Legendary Tabs** (Sfiomn) | Registers a native `TabBase` for the Legendary Tabs sidebar (priority configurable) |

If you're a mod author and want Runic Skills to integrate with your mod, open an issue or a PR — each integration is a single Java class with an `isModLoaded()` gate, see [`src/main/java/com/otectus/runicskills/integration/`](src/main/java/com/otectus/runicskills/integration/).

---

## Commands

All `/skills*` operator commands require OP level 2.

| Command | Effect |
|---|---|
| `/skills <player> <skill> <level>` | Set a player's skill to a specific level. |
| `/skills <player> <skill> add <amount>` | Add (or subtract, with negative) to a player's skill. |
| `/skills <player> list` | Dump all ten skill levels for a player. |
| `/skills reload` | Re-read the datapack-side config (lock items, titles, conditions) without a server restart. |
| `/skills respec <player>` | Reset all passives and perks (skill levels preserved) and refund points. |
| `/skills register <item-id>` | Register an item as level-gated at runtime (persisted to config on reload). |
| `/globallimit <cap>` | Set the global-level cap. |
| `/titles <player> <title> set true\|false` | Grant or revoke a title. |

---

## Configuration

Two configuration surfaces:

1. **Common config** (`config/runicskills-common.json5`) — YACL-managed, client-editable via **Mods → Runic Skills → Config**. Covers skill max-level, XP costs, UI overlays, per-integration toggles, and the lock-item list.
2. **Client config** (`config/runicskills-client.toml`) — Forge config spec. Covers rendering toggles (critical-roll overlay, lucky-drop overlay, perk mod-name display), sort orders, and the Legendary Tabs priority.

Title definitions and their conditions live as datapack JSON under `data/runicskills/titles/*.json`. Reload with `/skills reload`.

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

## KubeJS scripting hook

When KubeJS is installed, Runic Skills exposes a custom `SKILL_LEVELUP` event:

```js
// kubejs/server_scripts/runicskills_hooks.js
onEvent('runicskills.skill_levelup', event => {
    if (event.skill.name === 'magic' && event.player.level > 30) {
        event.player.tell(`Magic ${event.player.level} reached!`);
    }
});
```

The event object exposes `player` and `skill` and respects cancellation — cancelling blocks the level-up from being saved. Reflection handles are cached on first successful resolve, so event hook cost scales with handler complexity, not with Runic Skills overhead.

---

## Server / multiplayer notes

- **Protocol version** — the custom Forge network channel uses `PROTOCOL_VERSION=4`; clients on an older Runic Skills version will be rejected at join. Running a mixed-version modpack server is not supported.
- **Config sync** — the server is authoritative for the common config. On join, the server pushes its values to each client; the local `runicskills-common.json5` on the client is read for display defaults only.
- **Title name custom display** — titles apply a display-name prefix via `setCustomName`. Set `titlesUseCustomName=false` in the common config to disable if you run a chat/nickname mod that collides.

---

## Reporting bugs

- **Bug reports and feature requests:** use the [GitHub issues](https://github.com/otectus/runic-skills/issues) tracker.
- When reporting a crash, attach your `logs/latest.log` (or the crash report under `crash-reports/`) and your mod list (`mods.txt` output or a listing of your `mods/` directory). Include your `config/runicskills-common.json5` if the bug is config-related.
- Security issues: open a private advisory instead of a public issue.

---

## Credits and licence

- Originally forked from **JustLevelingFork**, with significant reworks to the UI, perk/passive system, title system, and integration architecture.
- Title assets and the "Runic Skills" rebrand by [@otectus](https://github.com/otectus).
- GUI textures, panels, and card artwork by the Runic Skills authors.
- Released under the **Apache License 2.0**. See [`LICENSE`](LICENSE) (if present) or the [SPDX entry](https://choosealicense.com/licenses/apache-2.0/) for terms.

### Third-party mods tested against

Listed under [Supported mod integrations](#supported-mod-integrations). Runic Skills does not bundle any third-party code; each integration is a thin optional compat layer that respects the upstream mod's own licence.
