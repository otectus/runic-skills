# Upgrading to Runic Skills 2.2.0

Install matching **2.2.0 / protocol 16** builds on the server and every client. The main
artifact is `runicskills-2.2.0.jar`. Keep the optional T.O. companion at the matching version
when using Aqua Attunement. This update targets Minecraft 1.20.1, Forge 47.4.23 and Java 17.

Back up the world and `config/RunicSkills` before upgrading. Skill, perk, passive, Power,
title and paid Tinkers' upgrade IDs stay unchanged. Lowering a cap retains earned levels
and ranks; ordinary progression cannot increase a level beyond the current limits.

## Progression settings

Open **Mods → Runic Skills → Config → Progression**. Save commits the draft; Done closes
the screen. Cancel discards pending changes. A failed save keeps the screen and draft
available for retry. If the file changes externally, reconcile that edit before retrying;
the editor does not overwrite the changed revision. Opening or resizing does not save.

| Setting | Meaning | Default / bounds |
| --- | --- | --- |
| `skillMaxLevel` | Maximum level in one skill | 32; 2–1000 |
| `globalLevelCapMode` | `custom` or `sum_of_skill_caps` | `custom` |
| `playersMaxGlobalLevel` | Stored custom total budget | 256; 32–99999 |
| `scaleGeneratedLockRequirements` | Scale legacy automatic equipment levels from reference 32 | false |

There are ten built-in skills, each starting at level 1. A new character therefore has
a global level of 10. Full mastery at cap 32 requires 320, while custom 256 intentionally
requires specialization. Eight skills at 32 plus two at 1 total **258**.

Choose **Allow all skills to reach maximum** for an automatically matching budget. Presets
offer 32/custom/256, 32/automatic/320, 64/automatic/640, 100/automatic/1000 and
1000/automatic/10000. The count comes from the registered skill set. Automatic mode retains
the last custom number. A custom 1024 budget with cap 32 stays 1024; the editor explains
the reachable 320 and offers raising the cap to 103 (1030 available total levels).

Local-world and LAN-host saves apply on the integrated server thread and synchronize its
players. On a remote server, gameplay settings are shown read-only; local defaults remain
intact. Dedicated-server operators edit the server's files and use `/skillsreload`.
`/globallimit` selects custom mode and uses the same validation bounds.
`/updateskilllevel` accepts 2–1000, commits through the same transaction and rebuilds live
state; its existing console/command-block restriction remains. Operator skill
commands retain their explicit global-budget override; normal purchases and grants do not.
Restart-only integration/bootstrap settings are reported separately by `/skillsreload`.

## Equipment rules and Tinkers'

New or missing `enableTConstructLockItems` settings default to **true**. An existing
explicit false stays false. Enable it in the Integrations category to opt an older config
into native material progression. Other integration and experimental settings are preserved.

Automatic Tinkers' requirements use the actual stack and action. Attack, use, equip and
mining can have different requirements. Automatic rules do not trap crafted or stored
tools: CRAFT and TAKE are exempt. Explicit applicable pack rules remain authoritative;
MINE falls back to an older authored USE rule when no MINE rule applies. Known high tiers
remain gated and an unknown part does not erase another part's known requirement.

Legacy generated ID requirements remain literal unless reference scaling is enabled.
Tinkers' and the newer equipment profiles already scale against the cap and are not
scaled twice. Manual levels stay literal, including intentionally unreachable ones.

To exempt an exact item in `runicskills.lockItems.json5`, use an explicit rule:

```json5
{ Item: "spartanweaponry:wooden_rapier", Allow: true, Skills: [] }
```

Retain the surrounding file's `lockItemList` structure. Old empty rules without `Allow`
remain invalid/ignored. Duplicate requirements for a skill resolve to their maximum;
the last manual item entry replaces earlier entries. Manual rules replace automatic ID
rules; Tinkers' applicable explicit stack rules precede manual ID overrides.

Use `/skills locks inspect` with the item in hand (optionally `attack`, `use`, `equip`,
`craft`, `take`, or `mine`). `/skills locks audit` requires operator permission and writes
`debug/runicskills-locks.json`. Tinkers' detailed commands remain `/skills tinkers inspect`
and `/skills tinkers compat`. Full audit exports contain registries, recipes, dependency
hashes and rule provenance, not player inventories.

## Pack Mule

Pack Mule is one Strength perk with three ranks. Ordinary natural-64 stacks can hold
128, 192 or 256 in the owner's main inventory, hotbar, offhand and carried cursor. Default
reference unlock levels are 8/16/24, scaled to the configured skill cap. The three
reference levels, `enablePackMule` and `packMuleExclusions` are configurable. Exclusions
accept exact item IDs or `#namespace:tag` entries.

Natural-1/16 items, damageable gear, capability-bearing storage and unsafe stateful items
keep their limits. Chests, machines and crafting slots keep their own limits. Pack Mule
does not change global Item or ItemStack maximums. Ordinary modded natural-64 stackables
can qualify; that does not certify their sorting, storage or custom networking systems.

After rank loss, disablement or respec, excess is split into available player slots. A full
inventory retains remaining owned excess in place, including the cursor, with an over-limit
tooltip. It can shrink and move within destination limits; it cannot grow. Reconciliation
runs once per second and after config apply. Save decoding remains enabled when the perk
is disabled. Native drops are split to their natural maximums.

Canceled drops return through a persistent recovery record, with a player notice. Ordinary
recovery is limited to 128 records; exceptional overflow is exported, with its owner UUID,
to `debug/runicskills-stack-recovery/canceled-*.nbt` for operator recovery. If disk export
fails, emergency ownership stays in player NBT and the error is logged; normal replay work
remains bounded. These exports
are not extra accessible inventory slots. Do not delete them before restoring ownership.
An unsupported saved integer count is preserved as complete compressed NBT in that same
directory before vanilla rejects it. The supported wire/save count range is 1–1,048,576;
that representation limit does not permit new Pack Mule stacks above 256. Corrupt legacy
negative/zero byte counts cannot be inferred back into 128/256.

## Before removal or downgrade

Older builds cannot safely read extended counts. Disabling Pack Mule alone is **not** a
completed downgrade migration.

1. Keep 2.2.0 installed and set `enablePackMule: false`; run `/skillsreload`.
2. Have every player, including previously offline players, log in. Close crafting/storage
   menus, make inventory space, and run `/skills packmule normalize`. An operator can run
   `/skills packmule normalize <player>` for an online player.
3. Repeat until `/skills packmule status` reports no oversized or pending stacks. Store the
   resulting native-sized stacks in ordinary containers if needed.
4. Resolve every recovery export. Check backups, graves/corpses and mod-owned inventories
   that may have copied old player data. The normalization command covers the current
   player's inventory/cursor/recovery record; it does not rewrite offline saves or arbitrary
   third-party storage.
5. Save and stop the server, back up the normalized state, then change both client and server
   builds. Do not downgrade while unresolved extended stacks or recovery records remain.

See [implementation and verification](IMPLEMENTATION_2.2.0.md) for tested boundaries and
the [provider audit](PROGRESSION_AUDIT_2.2.0.md) for known inference limits.
