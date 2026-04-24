# Runic Skills

**Level ten skills through the actions you already take.** Runic Skills adds a quiet, vanilla-respecting RPG progression layer to Minecraft 1.20.1 — swing a sword to raise Strength, cast a spell to raise Magic, plant a farm to raise Wisdom. Each skill funds perks and passives you pick yourself, and titles you earn for the rare milestones a normal world never sees.

> **Minecraft 1.20.1 · Forge 47.3.0+ · Java 17**
> Forked from JustLevelingFork and substantially reworked. Free, open source, Apache-2.0 licensed.

---

## What makes Runic Skills different

Most "RPG skills" mods make you grind against a separate track. Runic Skills piggy-backs on the things you already do in a survival world:

- **Ten skills, ten play-styles.** Strength, Constitution, Dexterity, Endurance, Intelligence, Building, Wisdom, Magic, Fortune, Tinkering. Melee players, archers, mages, farmers, miners, and engineers each have a natural primary track.
- **Perks are abilities, not permanent stats.** You unlock them at skill breakpoints, you can toggle them on and off from the Skills screen, and many have multiple ranks (e.g. **Haggler I / II / III** deepens villager discounts; **Wormhole Storage** opens your ender chest from the inventory screen). Choose the ones that fit your playstyle.
- **Passives are your stat spend.** Spend skill points into passive tiers: +Attack Damage, +Max Health, +Mana Regen, +Armor Toughness, +Movement Speed, +Projectile Damage — all via the vanilla/Apothic attribute system, so they stack correctly with gear and other mods.
- **Titles are rare, visible, and earned.** ~50 titles for milestones like *slayer of the Ender Dragon*, *conqueror of the Warden*, *master enchanter*. Pick one to display above your name once you've earned it. Other players see it too.
- **Item lock-gating** — optionally lock powerful armour, weapons, and tools behind skill thresholds. Throw someone a Netherite sword before their Strength is high enough and they literally can't swing it. Configurable per-item via the in-game config GUI.

---

## Screenshots

> _Placeholder — drop screenshots or gifs here before publishing:_
>
> - Skills overview page showing the ten aptitudes and total level.
> - Detail page showing passives on the left, perks on the right, with a multi-rank perk expanded.
> - Title selection screen with scroll list.
> - Overlay warning for a skill-locked item.
> - Legendary Tabs strip with the Skills tab installed.

---

## Mod compatibility

Runic Skills is built to feel like part of your modpack rather than an island. Optional integrations light up automatically when their mods are installed:

- **Ars Nouveau** — Magic scales spell damage, mana-regen passives, glyph mastery perk.
- **Irons Spellbooks** — Spell echo, arcane shield, attunement perks, school bonuses, spell gating by Magic level.
- **Apotheosis & Apothic Attributes** — Affix, gem, and socket awareness. Broader attribute pool for passives.
- **KubeJS** — Script your own skills, perks, passives, titles, and conditions. A `SKILL_LEVELUP` event is exposed.
- **L2Tabs / Legendary Tabs** — Skills shows up as a native tab in those mods' strips instead of being drawn twice.
- **Farmers Delight, Blood Magic, Ice and Fire, Cataclysm, Mowzie's Mobs, Enigmatic Legacy, Stalwart Dungeons, Bosses of Mass Destruction, Siege Machines, Saints Dragons, Samurai Dynasty, Nichirin Dynasty, Natures Aura, More Vanilla, Fantasy Armor, Jewelcraft, Locks** — Auto-generated level-gate lock lists for each.
- **Crayfish Gun Mod (unofficial), Scorched Guns 2, TacZ, PointBlank (Vic's)** — Gun-fire events honour Runic Skills perks and locks.

**Every integration is optional.** Runic Skills detects missing mods at runtime — no silent class-not-found crashes, no hard dependencies beyond Forge and YACL.

---

## Requirements

- **Minecraft Forge 47.3.0+** on **Minecraft 1.20.1**.
- **YACL (Yet Another Config Lib v3)** 3.4.2+ — client-side only, used for the in-game config GUI. Grab it from the [YACL CurseForge page](https://www.curseforge.com/minecraft/mc-mods/yacl) or [Modrinth](https://modrinth.com/mod/yacl).

That's it. Everything else is optional.

---

## Installation

1. Install **Minecraft Forge** for **1.20.1**.
2. Download the Runic Skills jar.
3. Drop it in your `.minecraft/mods/` folder. Add YACL to the same folder if you don't have it yet.
4. Launch. Press **`Y`** in-game to open the Skills screen (or click the Skills tab next to your inventory tab).

Works on single-player, LAN, and dedicated servers. Same jar on client and server.

---

## Controls and UI

- **`Y`** (default, rebindable in Controls) — open Skills screen.
- **Skills tab** — appears next to the inventory tab. With L2Tabs or Legendary Tabs installed, uses those mods' tab systems natively so the UI stays consistent with your modpack.
- **Mouse-hover a perk, hold Shift** — see description, level requirement, rank, and when your next rank unlocks.
- **Overlay warnings** — a skill requirement overlay appears briefly when you try to use a gated item you can't yet wield.

---

## For modpack makers

- **Every number is in a config.** Skill max level, XP curves, lock-item lists, title requirements, per-integration toggles. You can tune the whole pack from `config/runicskills-common.json5` (editable via the in-game config GUI as well).
- **Titles are data-driven.** Datapack JSON under `data/runicskills/titles/` — ship your own title pack by dropping files in your pack. Reload live with `/skills reload`.
- **KubeJS scripting.** Register custom skills, perks, passives, titles, and condition types from server scripts. See the [`KubeJS scripting hook`](https://github.com/otectus/runic-skills#kubejs-scripting-hook) section of the README for examples.
- **Sided-imports lint** runs at build time — no `net.minecraft.client.*` code leaks into common-side logic, so the mod is safe on dedicated servers. Repeatedly audited for packet-deserialization security (see the 0.9.2 CHANGELOG entry).

---

## Commands (ops only, permission level 2)

- `/skills <player> <skill> <level>` — set a player's skill.
- `/skills <player> <skill> add <amount>` — nudge up or down.
- `/skills <player> list` — print all ten skill levels.
- `/skills respec <player>` — refund passives and perks without touching skill levels.
- `/skills register <item-id>` — runtime-lock an item.
- `/skills reload` — re-read the titles and lock-items datapack.
- `/globallimit <cap>` — cap the total-skill sum.
- `/titles <player> <title> set true|false` — grant or revoke a title.

---

## What's new in 1.0.0

- **First stable release.** Consolidates the full 0.9.x line into a single 1.0 milestone; no further pre-1.0 versions will be cut.
- **Tooltip matches enforcement.** Disabling the item-lock master toggle (`enableItemLocks = false`) now also hides the "Requirements:" block from item tooltips. Previously the text remained, so players who unlocked items via config still saw stale "Requires Level X" warnings and thought the lock was still active.
- **Server owners and pack makers get five new knobs** for balancing the perk/passive system. Defaults are behavior-preserving, so updating from 0.9.7 changes nothing until you opt in.
- **Global active-perk cap** (`maxActivePerks`). Answers the top-requested "how many perks can I have active at once" question. `0` = unlimited. Iron's Spells school attunements count against this cap in addition to their own `ironsMaxSchoolSelections` limit.
- **Per-perk and per-passive kill switches** (`disabledPerks`, `disabledPassives`). Lists of registry names that can't be enabled or leveled up; effects are fully suppressed but save data is preserved, so re-enabling restores state. No more "delete the whole integration" to nerf one perk.
- **Perk-swap cooldown** (`perkSwapCooldownTicks`). Stops rapid-fire swapping between perks between fights. Cooldown persists through logout.
- **Skill level-up cost multiplier** (`skillLevelUpCostMultiplier`). Tune vanilla XP cost up or down. `1.0` = vanilla, `2.0` = twice as expensive, `0.5` = half price.
- **Data-driven perk groups via datapack.** Drop a JSON into `data/<pack>/perk_groups/` with `{ "max_active": N, "perks": [...] }` to enforce mutual-exclusion or custom caps on any subset of perks. Plays nicely alongside the hardcoded Iron's Spells school limit — both systems run independently.
- **Server-authoritative everywhere.** Every new check rejects with a `SyncSkillCapabilityCP` resync so clients can't desync from server rules. `/skillsreload` propagates config *and* datapack changes without requiring anyone to relog.
- **Protocol version bumped to `4`.** Clients and servers must update together — old clients connected to new servers (and vice versa) will get a clean connection refusal rather than a silent wire-format bug.
- **Fixed: mod now loads cleanly without Legendary Tabs.** An inline lambda in the client-setup path caused the JVM verifier to eager-resolve `sfiomn.*` classes at mod construction, so the mod crashed with `NoClassDefFoundError: TabBase` whenever Legendary Tabs wasn't installed — ignoring the runtime `isModLoaded()` guard. Isolated the optional-mod references into a dedicated wrapper method so classes only load when Legendary Tabs is actually present. Runic Skills is now a clean optional dependency again.
- **Fixed: skill-selection hover highlight is now aligned with the button.** The 74×26 green halo was being squashed into the 66-pixel button rect due to an incorrect `textureWidth` argument; it's now blitted at its true dimensions, centred around the button with a 4px outer glow.
- **Fixed: skill-selection tooltip is no longer overpainted by adjacent cells.** Deferred the tooltip render until after the grid loop so no later cell can draw on top of it.

## What's new in 0.9.6

- **Skills tab blends seamlessly into the Legendary Tabs strip.** It now blits its sword icon directly from Legendary Tabs' own atlas, so the frame shape, shading, hover state, and "currently selected" treatment are byte-for-byte identical to every neighbouring tab.
- **Skills always appears right after the Inventory tab.** Default priority lowered to `15`, slotting Skills between Inventory (priority 10) and every other known integration (20+).
- **Tab strip stays stable as you move between inventory screens.** Opening Skills no longer collapses the strip to a lone icon — every tab on the vanilla inventory now appears on the Skills page in the same order and X position. Conversely, opening a companion screen (Medkit, Curios, Backpack, etc.) no longer hides the Skills tab.

## What's new in 0.9.5

- **Native Legendary Tabs integration.** Skills now participates in Legendary Tabs' own strip instead of being drawn on top of it. Priority is configurable.
- **Major audit and remediation pass.** Two crash bugs (Better Combat attack-range modifiers; PointBlank skill-gated gun fire), multiple NPE paths in pre-sync tooltip/overlay rendering, matrix-stack leak in the tab renderer, and several config and capability guardrails fixed. See the full [CHANGELOG](https://github.com/otectus/runic-skills/blob/master/CHANGELOG.md).
- **Translations across 17 languages** — new lang keys for perk ranks and the title-edit button, translated for every language the mod already supports (Arabic, German, English, Spanish ×7, French, Hindi, Japanese, Korean, Portuguese-Brazilian, Russian, Simplified Chinese).
- **Internal performance.** KubeJS level-up hook now caches its reflection lookups. `RecipeBook.isVisible()` is cached per render frame. Villager haggler-delta map self-cleans.

---

## Reporting bugs and requesting features

- [GitHub issues](https://github.com/otectus/runic-skills/issues) is the best place.
- If reporting a crash, attach `logs/latest.log`, your mod list, and if config-related your `config/runicskills-common.json5`.
- Pull requests welcome — the project is Apache-2.0.

---

## Credits

- **Otectus** — current maintainer, rebrand, GUI rework, audit, Legendary Tabs integration.
- **JustLevelingFork** — the original fork base.
- Each third-party mod we integrate with is owned by and credited to its respective author. Runic Skills does not bundle their code; integrations are thin optional compat layers.

Thanks for installing — drop feedback on the [issues tracker](https://github.com/otectus/runic-skills/issues). Happy levelling.
