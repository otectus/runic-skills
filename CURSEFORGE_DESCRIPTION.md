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

**Passives — 39 permanent stat upgrades.**
Pour levels into +Max Health, +Attack Damage, +Movement Speed, +Armour Toughness, +Projectile Damage and more. These use Minecraft's real attribute system, so they stack correctly with your gear and with other mods. Shift, Ctrl and Alt buy 5, 10, or as many as you can afford at once.

**Powers — a second loadout, 56 of them live.**
A separate set of slots from perks, on their own point budget: **5 Marks, 3 Seals and 1 Crown**, escalating in strength, with each tier requiring one of the tier below already slotted in the same school.

Seventy-five Powers are registered. Thirty are cross-cutting — projectile, channel, summon, mobility, weapon-caster and utility — and work in **any** pack. The other forty-five belong to the nine magic schools and need Iron's Spells 'n Spellbooks. Of those, **nineteen are written against Iron's Spells events this build has never been able to execute, and they are marked inert**: not equippable, not shown in the panel, and costing you nothing. That leaves 56 you can actually use. Equip them from the Powers panel, reachable from a button on the Skills screen.

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
- **KubeJS** — subscribe to progression events from scripts
- **L2Tabs / Legendary Tabs (2.0+)** — the Skills tab joins their strip instead of drawing over it
- **Farmer's Delight and the Let's Do series, Cataclysm, Mowzie's Mobs, Starcatcher, Overgeared**, and the gun mods (**TacZ**, **Scorched Guns 2**, **PointBlank (Vic's)**, **Crayfish Gun Mod**) — food, combat and firearm perks

**Automatic gear gating** — **33 mods** get their weapons, tools and armour level-gated with no configuration at all, through a mix of hand-tuned tables and namespace scanning: Ice and Fire, Spartan Weaponry, Samurai Dynasty, Jewelcraft, Locks Reforged, Epic Knights, Aquaculture, Dragonsteel and many more. If a classification gets one item wrong, you can exempt that single item instead of switching a whole mod off.

> **Stated honestly:** the perks and Powers that depend on Apotheosis, Ars Nouveau, Iron's Spells, Ice and Fire, Samurai Dynasty, Locks Reforged and Siege Machines compile against those mods, but have not yet been run against them end to end. They should work; they are not yet verified. The nineteen inert Powers above are the part of that backlog we can already prove, which is why they are switched off rather than advertised.

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

Available to everyone:

| Command | Does |
|---|---|
| `/respec` | Reset **your own** progression |

---

## What's new in 2.0.3

**Every Iron's Spells school Power now does something.**

Nineteen Powers — Marrow Sense, Kinetic Affinity, Piercing Insight, Arcane Echo, Black Hole
Resonance, Creeper Cascade Mastery, Fang Follow-Through, Shield Wall, Ember Trail, Heat Haze,
Scorched Earth, Wings of Judgment, Frost Echo, Reforge the Shadow, Shatter, Conduit Mark, Static
Cling, Blight Spread and Venomous Harvest — shipped with tooltips but no effect. Each now does what
its tooltip says. One substitution: Iron's Spells has no "Ice Shadow" summon, so Reforge the Shadow
buffs the Summon Polar Bear instead, and its tooltip says so. Every number is a datapack tunable.

**2.0.2 and 2.0.3 pair freely** — no protocol, config or save-data change between them.

## What's new in 2.0.2

**A startup conflict with Highlighter that could misfire your inventory tabs.**

The Skills tab strip cleared its click latch by overwriting the inventory screen's close method, and
Mixin keeps exactly one such overwrite when a second mod declares the same one. Highlighter's won,
ours was dropped, and every launch logged a `Method overwrite conflict` warning. The visible symptom
was a click that closed the inventory arming a tab switch that then fired in the next screen to draw
the strip. The reset now listens to Forge's own screen-closing event, so it composes with other mods
instead of competing with them.

**2.0.1 and 2.0.2 pair freely** — no protocol, config or save-data change between them.

### Coming from 2.0.0 or earlier

⚠️ **The network protocol changed in 2.0.1 (10 → 11). A 2.0.0 client cannot join a 2.0.1+ server, or the reverse.** Worlds and configs carry over untouched.

**2.0.1 — Powers you can finally see.**

- **Every Power has its own icon.** All seventy-five used to share one placeholder square, so the panel could not tell you what anything was until you read its name.
- **A proc now happens somewhere.** It used to spawn a few generic sparkles around *you*, whatever the Power was and wherever it had actually landed — and nobody else could see it at all. A damage amplifier now brands **the mob**, a chain detonation draws at **each corpse**, and other players nearby see it too.
- **You can tell Marks, Seals and Crowns apart at a glance — even in greyscale.** Tier is carried by the shape of the rune (broken ring, closed ring, triple ring), school by colour, motion and sound. Nothing depends on colour alone.
- **A card above the hotbar names what fired**, at most three at a time, with repeats counting up instead of stacking. It still works with particles and sound switched off.
- **Items were being locked by accident.** Keyword matching was too loose, so bowls, waxed copper blocks and fishing rods could end up gated behind a weapon skill in mods scanned automatically. Fixed — and you can now exempt a single item instead of disabling a whole mod's locks.
- **Notice and skill-lock pop-ups vanished in half their configured time.**
- **A core tooltip was broken in every non-English language**, showing a literal `%s` instead of the cost to level a skill.

**2.0.0 was the big one** — 129 perks and 44 Powers that did nothing at all were implemented or removed, the server became authoritative over configuration, and player data stopped being destroyed by config edits. **1.9.0** fixed a bug where **dying wiped your character**. All of it is included.

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
