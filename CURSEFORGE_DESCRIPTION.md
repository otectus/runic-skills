# Runic Skills

**Turn your XP bar into a character build.** Runic Skills adds a full RPG progression layer to Minecraft: spend the experience you already earn on ten skills, then cash those skills in for abilities, permanent stat upgrades and titles that other players can see.

> **Minecraft 1.20.1 · Forge 47.3.0+ · Java 17**
> Free and open source (Apache-2.0). Forked from JustLevelingFork and heavily reworked.

---

## How it works

You collect XP the normal way — mining, fighting, smelting, whatever you were doing anyway. Then you press **`Y`**, open the Skills screen, and **spend that XP on whichever skill you choose.**

That's the whole loop, and the choice is the point: XP spent on Strength is XP not spent on Magic. Each skill costs more as it rises, so nobody maxes everything, and two players who did exactly the same things end up with completely different characters.

There's no second XP bar to grind and nothing to babysit. Your experience finally has a long-term use beyond enchanting.

## Ten skills to invest in

Each skill unlocks its own branch of abilities and stat upgrades:

| Skill | Governs | Perks |
|---|---|---|
| **Magic** | Spellcasting, mana, and all nine magic schools | 79 |
| **Endurance** | Defence, wards, damage soaking | 48 |
| **Fortune** | Luck, critical hits, fishing, gems and sockets | 44 |
| **Dexterity** | Stealth, accuracy, dodging, ranged precision | 43 |
| **Strength** | Melee power, boss hunting, life-stealing | 42 |
| **Tinkering** | Smithing, repair, lockpicking, tool mastery | 40 |
| **Intelligence** | Enchanting, alchemy, trading, spell efficiency | 39 |
| **Constitution** | Max health, cooking, food and healing | 38 |
| **Wisdom** | Lore, focus, farming, arcane insight | 37 |
| **Building** | Mining, ore yield, salvaging, block work | 35 |

Your total level is the sum of all ten, and a server can cap it.

---

## What you spend it on

**Perks — 445 abilities you pick.**
Perks unlock at skill breakpoints and you toggle them on and off freely from the Skills screen. *Vein Miner* cascades an ore vein in one break. *Wormhole Storage* opens your ender chest from anywhere. *Haggler* talks villagers down. *Cleave* splashes damage into the crowd around your target. You have a limited number of active slots, so a build is about what you leave off as much as what you take.

**Passives — 38 permanent stat upgrades.**
Pour levels into +Max Health, +Attack Damage, +Movement Speed, +Armour Toughness, +Projectile Damage and more. These use Minecraft's real attribute system, so they stack correctly with your gear and with other mods. Shift, Ctrl and Alt buy 5, 10, or as many as you can afford at once.

**Powers — a second loadout, 75 of them live.**
A separate set of slots from perks, on their own point budget: **5 Marks, 3 Seals and 1 Crown**, escalating in strength, with each tier requiring one of the tier below already slotted in the same school. Thirty are cross-cutting — projectile, channel, summon, mobility, weapon-caster and utility — and work in **any** pack. The other forty-five belong to the nine magic schools and each needs Iron's Spells 'n Spellbooks. All seventy-five execute their intended effects. Equip them from the Powers panel, reachable from a button on the Skills screen.

**Titles — 77 badges for real milestones.**
Kill the Ender Dragon, beat the Warden, max out a skill. Earn one and you can wear it above your name, where everyone on the server sees it. Titles are drawn as a name prefix and never overwrite your actual name, so nickname, chat and tab-list mods keep working.

**Item locking — gear gated by skill.** *(on by default)*
Powerful weapons, tools and armour require a matching skill level. Hand a new player a netherite sword too early and they simply can't swing it — which makes progression mean something instead of being handed over by one lucky chest. Locked items say so in their tooltip, so nobody is left guessing. Not for your world? A single toggle (`enableItemLocks`) switches the whole system off.

---

## Screenshots

> _Placeholder — add before publishing:_
> Skills overview · perk detail page · Powers panel · title selection · a locked-item warning

---

## Getting started

1. Install **Minecraft Forge 47.3.0+** for **1.20.1**.
2. Drop the Runic Skills jar into your `mods` folder.
3. Launch and press **`Y`**.

That's the whole setup. No other mods are required.

**Controls**

- **`Y`** — open and close the Skills screen (rebindable).
- **Powers panel** — **unbound by default** so it can't clash with your pack; assign a key under Options → Controls, or use the button on the Skills screen.
- **Skills tab** — sits next to your inventory tab. If you use L2Tabs or Legendary Tabs (2.0 or newer), it slots into their strip instead of drawing over it.
- **Hover a perk and hold Shift** for its full description, rank and level requirement.

**Want the in-game settings menu?** Add [YACL](https://www.curseforge.com/minecraft/mc-mods/yacl) 3.5.0+ — it's optional, client-side only, and just gives you a graphical config screen. Without it everything still works and the Configure button tells you what's missing; you would edit the config files by hand instead.

---

## Multiplayer

Works in single-player, LAN and on dedicated servers, using the same jar everywhere.

- **Install it on the server and on every client.** The network protocol is versioned, and a mismatch is refused at connect with a named error rather than corrupting a save.
- The **server decides the rules.** Skill caps, disabled perks and item locks are enforced server-side and pushed to clients on join, so one player's config can't give them an advantage.
- Your progress is stored per-world in your player data and survives death and dimension changes.

---

## Works with your modpack

**Every integration is optional.** Runic Skills detects what you actually have installed and quietly skips the rest — nothing crashes because a mod is missing. There are two different kinds.

**Deep integrations** — perks, events or whole systems that hook another mod directly:

- **Iron's Spells 'n Spellbooks** — all nine magic schools, spell gating, summon hooks, and the entire Powers system
- **Ars Nouveau** — spell damage, mana regeneration, glyph mastery
- **Apotheosis / Apothic Attributes** — affix, gem and socket perks, an enchantment-cap boost, gem-rarity gating on socketing, and a much wider stat pool for passives
- **FTB Quests** — six native task types, so quests can require skill levels, total level, perk ranks, passive levels or titles
- **KubeJS** — server-side progression events can observe or veto skill level-ups, including advancement-based progression rules
- **L2Tabs / Legendary Tabs (2.0+)** — the Skills tab joins their strip instead of drawing over it
- **Farmer's Delight and the Let's Do series, Cataclysm, Mowzie's Mobs, Starcatcher, Overgeared**, and the gun mods (**TacZ**, **Scorched Guns 2**, **PointBlank (Vic's)**, **Crayfish Gun Mod**) — food, combat and firearm perks

**Automatic gear gating** — 33 mod families get their weapons, tools and armour level-gated with no configuration at all, through a mix of hand-tuned rules and namespace scanning: Ice and Fire, Spartan Weaponry, Samurai Dynasty, Jewelcraft, Locks Reforged, Epic Knights, Aquaculture, Dragonsteel, Cataclysm, Mowzie's Mobs, Starcatcher, Overgeared, and many more. If a classification gets one item wrong, you can exempt that single item instead of switching a whole mod off.

> **Stated honestly:** the perks that depend on Apotheosis, Ars Nouveau, Iron's Spells, Ice and Fire, Samurai Dynasty, Locks Reforged and Siege Machines compile against those mods, but have not yet been run against them end to end. They should work; they are not yet verified.

---

## Configuring it

Every number is adjustable: skill caps, XP costs, how many perks you can run at once, which perks exist at all, and every item lock. Edit them in-game via the YACL screen, or directly in `config/RunicSkills/` — gameplay settings live in `runicskills.common.json5`, client-only display settings in `runicskills-client.toml`.

Some keys worth knowing:

| Key | Does |
|---|---|
| `enableItemLocks` | Master switch for the whole item-locking system |
| `disabledPerks` | Turn individual perks off entirely |
| `disabledDiscoveredLockMods` | Stop auto-gating a whole mod's gear |
| `disabledDiscoveredLockItems` | Exempt a single item from auto-gating |
| `discoveredLockLevelMultiplier` | Scale every auto-generated lock level |
| `playersMaxGlobalLevel` | Cap a player's total levels across all skills |

Note that *deleting* the lock file regenerates the defaults rather than removing them — use `enableItemLocks` to switch locking off. Apply changes live with `/skillsreload`, which reports which fields took effect immediately and which need a restart.

**Accessibility is configuration too, and the safe values are the defaults.** `powerScreenShake` and `powerFlashes` are **off by default**, because both are common migraine and photosensitivity triggers and neither carries information the rune silhouette and the HUD card do not. `powerVfxQuality` (`OFF`/`REDUCED`/`FULL`), `powerHudFeedback`, `powerProcSounds`, `highContrastRunes` and a particle multiplier are all yours to set.

Pack authors also get datapack support for titles, perk groups, Power overrides and custom skill artwork, plus KubeJS and Forge event hooks for reacting to progression. Full details are in the [README](https://github.com/otectus/runic-skills#readme).

---

## Commands

Operator commands (permission level 2):

| Command | Does |
|---|---|
| `/skills <player> <skill> get\|set\|add\|subtract <level>` | Read or change a skill level |
| `/listskills <player>` | Print all ten skill levels |
| `/respec <player>` | Reset that player's skills, passives, perks and Powers |
| `/titles <player> <title> set true\|false` | Grant or revoke a title |
| `/registeritem <item>` | Add an item to the lock list at runtime |
| `/globallimit <cap>` | Cap a player's total levels across all skills |
| `/skillsreload` | Re-read the config and datapacks without a restart |
| `/powers list\|view\|equip\|unequip` | Inspect and manage Powers |
| `/updateskilllevel <level>` | Change the per-skill level cap. **Console or command block only**, because it rewrites the server's config file |

All of these are also available under a `/runicskills` root, so `/runicskills respec <player>` works the same way as `/respec <player>`.

Available to everyone:

| Command | Does |
|---|---|
| `/respec` | Reset **your own** progression |

---

## What's new in 2.0.5

**Stability and perk semantics.** Lucky Break now prevents durability loss on tool-like items instead of passively repairing. Mending Boost only amplifies real Mending repairs. Crafting rewards are server-authoritative, and Efficient Crafting actually preserves your consumed materials.

**Six Tinkering and Building perks now do what their tooltips promise:** Precision Tools gives you a bigger durability pool. Tinker's Touch stamps crafted items with a durability bonus. Tool Smith and Weapon Smith stamp repaired items for speed and damage. Runic Engineering improves enchantments on repaired runic gear. Heritage Builder works with MineColonies to protect colony buildings from structural damage.

Master Researcher no longer scans thousands of recipes per craft — it now indexes them with a candidate budget, with improved compatibility for modded recipe and ingredient implementations, including configurations using ModernFix. Combat secondary damage carries re-entry protection and safeguards for large multi-skill builds.

**Inventory tabs now stay out of the way.** They avoid the recipe book, potion effects, and other mods' custom tab regions. You can move the tab strip with Shift-drag, and the position is saved.

**KubeJS server-side progression gates now work.** Write `RunicSkillsEvents.skillLevelUp` listeners in `kubejs/server_scripts/` to observe or deny skill level-ups, including advancement-based gates. The server post is authoritative and charges no XP on denial. See docs/KUBEJS.md for advancement helpers and examples.

**2.0.4 and 2.0.5 pair freely** — no protocol or save-data change; new client-only tab settings use safe defaults.

## What's new in 2.0.4

**Sixteen audit fixes:** passives that were stuck scaling, Powers that weren't firing, Scholar enchantment hiding now per-player, gem credit fixed, perk timers reliable across respawns, and duration values consistent.

Wisdom XP Bonus and Enchanting Power passives now scale correctly. Arcane Reprieve and Continuous Flow, two mana-management Powers, are now reachable. Scholar's enchantment-name hiding works per-player. Break Speed passive respects other mods' speed modifiers. Perk timers use world time instead of server ticks.

**2.0.3 and 2.0.4 pair freely** — no protocol, config or save-data change.

## What's new in 2.0.3

**Every Iron's Spells school Power now does something.** Nineteen Powers that shipped with tooltips but no effect — Marrow Sense, Kinetic Affinity, Piercing Insight, Arcane Echo, Black Hole Resonance, Creeper Cascade Mastery, Fang Follow-Through, Shield Wall, Ember Trail, Heat Haze, Scorched Earth, Wings of Judgment, Frost Echo, Reforge the Shadow, Shatter, Conduit Mark, Static Cling, Blight Spread and Venomous Harvest — each now executes its intended effect. All magnitude, duration and chance values are datapack tunable.

**2.0.2 and 2.0.3 pair freely** — no protocol, config or save-data change.

## What's new in 2.0.2

**Inventory tab strip compatibility with other mods.** The tab strip's close reset now listens to Forge's screen-closing event instead of competing with other mods for the mixin, fixing a startup conflict with Highlighter and similar tab mods.

**2.0.1 and 2.0.2 pair freely** — no protocol, config or save-data change.

### Coming from 2.0.0 or earlier

⚠️ **The network protocol changed in 2.0.1 (10 → 11). A 2.0.0 client cannot join a 2.0.1+ server, or the reverse.** Worlds and configs carry over untouched.

## What's new in 2.0.1

**Powers you can finally see.** Every Power has its own icon, not a placeholder square. A proc now shows where it landed — damage amplifiers brand the mob, chains draw at each corpse, and every client in range sees it. Tier is visible at a glance — Marks, Seals and Crowns are distinguishable in grayscale by their rune shape. A card names what fired above the hotbar. Item locks got more precise — keyword matching moved from substring to segment-based so bowls, waxed blocks and fishing rods are no longer accidentally gated.

**2.0.0 was the big one.** 129 perks and 44 Powers that did nothing were implemented or removed. The server became authoritative over configuration. Player data stopped being destroyed by config edits. **1.9.0** fixed a bug where dying wiped your character. All of it is included.

Full details for this and every previous release are in the [changelog](https://github.com/otectus/runic-skills/blob/master/CHANGELOG.md).

---

## Bugs and suggestions

[GitHub issues](https://github.com/otectus/runic-skills/issues) is the fastest way to reach us.

For a crash, please attach `logs/latest.log` and your mod list. For anything config-related, add `config/RunicSkills/runicskills.common.json5` too.

**Translations.** The jar ships 17 language files, but only English is complete: the other sixteen carry 545 of 2,276 strings — about a quarter — and Minecraft falls back to English for the rest. That gap is tracked in the repository rather than papered over, and translation pull requests are especially welcome.

---

## Credits

**Otectus** — current maintainer. **JustLevelingFork** — the original fork base.

Every mod we integrate with belongs to its own author; Runic Skills bundles none of their code and only adds thin optional compatibility layers.
