# Runic Skills

**Turn your XP bar into a character build.** Runic Skills adds a full RPG progression layer to Minecraft: spend the experience you already earn on ten skills, then cash those skills in for abilities, permanent stat upgrades and titles that other players can see.

> **Minecraft 1.20.1 · Forge 47.3.0+**
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
| **Magic** | Spellcasting, mana, and all nine magic schools | 89 |
| **Endurance** | Defence, wards, damage soaking | 49 |
| **Tinkering** | Smithing, repair, lockpicking, tool mastery | 44 |
| **Fortune** | Luck, critical hits, fishing, gems and sockets | 44 |
| **Dexterity** | Stealth, accuracy, dodging, ranged precision | 43 |
| **Strength** | Melee power, boss hunting, life-stealing | 42 |
| **Intelligence** | Enchanting, alchemy, trading, spell efficiency | 41 |
| **Building** | Mining, ore yield, salvaging, block work | 41 |
| **Constitution** | Max health, cooking, food and healing | 39 |
| **Wisdom** | Lore, focus, farming, arcane insight | 39 |

---

## What you spend it on

**Perks — 471 abilities you pick.**
Perks unlock at skill breakpoints and you toggle them on and off freely from the Skills screen. *Vein Miner* cascades an ore vein in one break. *Wormhole Storage* opens your ender chest from anywhere. *Haggler* talks villagers down. *Cleave* splashes damage into the crowd around your target. You have a limited number of active slots, so a build is about what you leave off as much as what you take.

**Passives — 39 permanent stat upgrades.**
Pour levels into +Max Health, +Attack Damage, +Movement Speed, +Armour Toughness, +Projectile Damage and more. These use Minecraft's real attribute system, so they stack correctly with your gear and with other mods.

**Powers — 75 deep magic specialisations.** *(needs Iron's Spells 'n Spellbooks)*
A second, separate loadout for spellcasters: **5 Marks, 3 Seals and 1 Crown**, escalating in strength, covering all nine magic schools plus cross-cutting effects. Equip them from the Powers panel.

**Titles — 77 badges for real milestones.**
Kill the Ender Dragon, beat the Warden, max out a skill. Earn one and you can wear it above your name, where everyone on the server sees it.

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
- **Powers panel** — unbound by default so it can't clash with your pack; assign a key under Options → Controls.
- **Skills tab** — sits next to your inventory tab. If you use L2Tabs or Legendary Tabs, it slots into their strip instead of drawing over it.
- **Hover a perk and hold Shift** for its full description and level requirement.

**Want the in-game settings menu?** Add [YACL](https://www.curseforge.com/minecraft/mc-mods/yacl) — it's optional, client-side only, and just gives you a graphical config screen. Without it everything still works; you'd edit the config file by hand instead.

---

## Multiplayer

Works in single-player, LAN and on dedicated servers, using the same jar everywhere.

- **Install it on the server and on every client** — clients and servers must be on the same version.
- The **server decides the rules.** Skill caps, disabled perks and item locks are enforced server-side and pushed to clients on join, so one player's config can't give them an advantage.
- Your progress is stored per-world in your player data and survives death and dimension changes.

---

## Works with your modpack

Runic Skills integrates with around 40 mods. **Every one is optional** — it detects what you actually have installed and quietly skips the rest. Nothing crashes because a mod is missing.

**Magic** — Ars Nouveau (spell damage, mana regen, glyph mastery) · Iron's Spells 'n Spellbooks (all nine schools, spell gating, and the entire Powers system)

**Gear & stats** — Apotheosis and Apothic Attributes (affix, gem and socket awareness, plus a much wider stat pool for passives)

**Quests & scripting** — FTB Quests (six task types, so quests can require skill levels, perks or titles) · KubeJS (script your own skills, perks, passives and titles)

**UI** — L2Tabs · Legendary Tabs

**Combat & content** — Better Combat, Ice and Fire, Cataclysm, Mowzie's Mobs, Bosses of Mass Destruction, Stalwart Dungeons, Siege Machines, Saints Dragons, Samurai Dynasty, Nichirin Dynasty, Farmer's Delight and the Let's Do series, Nature's Aura, Jewelcraft, and more — mostly automatic level-gating for their gear.

**Guns** — TacZ, Scorched Guns 2, PointBlank (Vic's), Crayfish Gun Mod

---

## Configuring it

Every number is adjustable: skill caps, XP costs, how many perks you can run at once, which perks exist at all, and every item lock. Edit them in-game via the config screen, or directly in `config/RunicSkills/`.

Pack authors also get datapack support for titles, perk groups and custom skill artwork, plus KubeJS hooks for adding your own content. Full details are in the [README](https://github.com/otectus/runic-skills#readme).

To turn a perk off entirely, add its name to `disabledPerks`. To switch off item locking, set `enableItemLocks` to `false` — note that *deleting* the lock file regenerates the defaults rather than removing them. Apply changes live with `/skillsreload`.

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

Available to everyone:

| Command | Does |
|---|---|
| `/respec` | Reset **your own** progression |

---

## What's new in 1.7.0

A large correctness and security pass driven by a full technical audit of the mod.

- **Four duplication exploits closed.** Silk Touch Mastery and Lucky Drop could duplicate items, Double Down turned cobblestone into free material, and Locksmith was an unlimited source of XP. All four now behave as intended.
- **Your config can no longer be wiped by a typo.** A single bad value used to reset all 1,100+ settings *and* overwrite your file. Now only that one setting falls back to its default, and an unreadable file is left untouched.
- **Server rules are properly enforced.** The global level cap was previously only checked by the client, and the Administrator title was never removed when someone was de-opped.
- **Smoother performance**, especially on busy servers — a lot of redundant per-tick network traffic has been removed.
- **The Powers panel is finally reachable** — its keybind was never registered, so the whole screen was unusable.

⚠️ **Update clients and servers together.** The network version changed, so 1.7.0 will not connect to older versions. Your existing worlds and configs carry over automatically.

Full details for this and every previous release are in the [changelog](https://github.com/otectus/runic-skills/blob/master/CHANGELOG.md).

---

## Bugs and suggestions

[GitHub issues](https://github.com/otectus/runic-skills/issues) is the fastest way to reach us.

For a crash, please attach `logs/latest.log` and your mod list. For anything config-related, add `config/RunicSkills/runicskills.common.json5` too.

Translated into 17 languages. Pull requests welcome — including translation fixes.

---

## Credits

**Otectus** — current maintainer. **JustLevelingFork** — the original fork base.

Every mod we integrate with belongs to its own author; Runic Skills bundles none of their code and only adds thin optional compatibility layers.
