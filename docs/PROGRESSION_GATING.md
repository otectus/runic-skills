# Default progression gates (2.2.0)

See [2.2.0 implementation](IMPLEMENTATION_2.2.0.md), [cap controls and migration](MIGRATING_TO_2.2.0.md),
and [registry/provider audit](PROGRESSION_AUDIT_2.2.0.md) for the current behavior and executed checks.

New configurations enable progression for Tinkers' Construct and its native equipment addons,
Simply Swords, Simply More, T.O. Magic (`traveloptics`) and Tide. Existing integrations such as
Iron's Spellbooks, Starcatcher and Overgeared retain their enabled defaults.

Settings live in `config/RunicSkills/runicskills.common.json5`:

| Setting | Default | Generated requirements controlled |
| --- | --- | --- |
| `enableItemLocks` | `true` | Master switch for all item locks, including explicit locks |
| `enableTConstructLockItems` | `true` | Material-based Tinkers and addon equipment, including Jewelry |
| `simplySwordsAutomaticEquipmentGates` | `true` | Simply Swords weapons |
| `simplyMoreAutomaticEquipmentGates` | `true` | Simply More weapons |
| `tomAutomaticEquipmentGates` | `true` | T.O. weapons, armor and spellcasting equipment |
| `tideAutomaticEquipmentGates` | `true` | Tide fishing rods |

Set an individual switch to `false` to disable generated requirements while keeping perks and
Powers available. Four-mod integration modes `off` and `observe` also disable their generated
locks. The four providers honor `disabledDiscoveredLockMods` (module ID or namespace) and
`disabledDiscoveredLockItems` (exact item ID). Manual item locks retain precedence.

Tinkers reads each finished tool's actual materials regardless of its namespace. At skill cap 32,
tiers 0/1/2/3/4/5/6+ require Tinkering 0/1/8/16/24/28/32; functional requirements are
0/1/4/8/16/20/24, chosen for the action being performed. Higher known addon tiers no longer
bypass the table. Unknown material data remains unrestricted and appears in diagnostics.
Parts, casts, patterns, storage and crafting are outside automatic equipment-use gates.

For the four newest mods, wood is unrestricted; stone/copper, gold, iron, diamond and
netherite/runic equipment use primary reference levels 4, 6, 8, 16 and 24. Other named weapons,
armor and spellbooks use 24; other fishing rods use 12. Secondary requirements use half the
primary level. Weapons use Strength/Dexterity, armor Endurance/Constitution, rods
Fortune/Dexterity, and spellbooks Magic/Intelligence. T.O. gear also requires Magic.
All these reference levels scale with the server skill cap. Fish, bait, gems, crafting materials
and decorative blocks do not become equipment through name matching.

Existing saved `false` values are preserved. Updating does not overwrite an operator's opt-out;
enable the desired switches explicitly in an older config. Missing keys get the new defaults.

The config editor now saves through the atomic config writer and updates its local values
immediately, so reopening no longer restores stale values. Saving in a local world schedules a
server refresh and synchronizes connected players. On a remote server, gameplay values are read-only and local defaults stay intact;
edit the server's file and run `/skillsreload` there. Restart-required
integration switches still need a restart.

The reserved four-mod `NativeAbilityGates` settings and action-specific integration datapack
rules remain separate from these equipment locks. They do not yet enforce native packet-driven
abilities. Tinkers' explicit `tconstruct_rules` use requirements remain supported.

## Historical 2.1.2 validation

The earlier 2.1.2 working-tree validation record reports that its build passed all build guards and 364 JUnit tests. The combined stable Tinkers,
Iron's Spellbooks, Levelling, Delight, Thinking, Innovation, Advanced, TCIntegrations,
Botania, Ars and Jewelry profile passed all 338 required GameTests. The isolated production
server passed 41 checks against the release jar and real Simply Swords, Simply More, T.O. and
Tide artifacts, including low-level refusal, satisfied requirements, manual overrides and
master/per-module opt-outs. Test players used for exact-damage assertions disable Apothic's
unrelated random critical-hit roll; gameplay defaults are unaffected.

Config persistence tests cover reopening/restarting, preserved unknown keys, remote snapshot
separation, explicit saved opt-outs and recovery after a malformed file is repaired. The YACL
bridge compiles against the actual library; an interactive client Save/Cancel session was not
manually exercised.

Local evidence: `build/progression-release-check.log`, `build/test-results/test/` and
`build/audit-production-server/progression-release-production.log.result.json`.
