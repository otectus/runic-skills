# Upgrading to 2.1.2

This is an unpublished development build. Install the built `runicskills-2.1.2.jar` on
both the server and every client. The channel moves from **14 to 15** to synchronize
skill artwork; older peers are deliberately refused during negotiation. If using the
T.O. companion, build/install the matching `runicskills-tom-compat-2.1.2.jar` separately.

No registry IDs or progression NBT fields are removed. Existing skills, perk ranks,
passive allocations, Power choices/cooldown debt, earned titles, and paid Tinkers
Keystones remain stored. No world conversion command is required. Keep a normal
backup before upgrading a modpack.

## Behavior changes

- Passive ranks whose skill requirements are no longer met become dormant. They stay
  allocated and resume when requirements are met again; the tooltip explains the
  difference between allocated and active ranks. Existing respec controls remain available.
- The Long Note, Warmage's Covenant and The Still Mind require Iron's Spellbooks.
  Without it their saved selections remain, but cannot proc or spend Power Points.
- Arcanist's Barrage uses one target's shared hit sequence and one damage impact. Its
  default Iron's Spellbooks combat gap is now 200 ticks (formerly 600). Existing
  `echo_count_threshold`, `combat_timeout_ticks` and `echo_damage_multiplier` overrides
  remain accepted; canonical keys take precedence when both forms are configured.
- Reversible crafting conversions, repairs and special/copy recipes do not earn
  Efficient Crafting refunds. Ordinary one-way manufacture still does. Stonecutting
  previews show the native result; any bonus is delivered after material is consumed.
- Unified Arcana uses mana actually removed by each native Ars payment. Free payments
  and repeated resolve/target events grant nothing extra; a third-party refund after
  the payment scope cannot be detected. Schoolbridge uses school power above the
  neutral 1.0 baseline.
- Invalid or oversized perk-group/Tinkers-rule/artwork reloads retain the previous
  valid snapshot. This includes malformed, unreadable, empty or null JSON documents.
  Fix the logged file and reload again. An intentionally empty directory clears its
  snapshot. Individual documents are limited to 256 KiB for groups/rules and 64 KiB
  for artwork; directory limits are 4,096 group files and 2,048 Tinkers rule files.
  Tinkers retains its existing empty-index exception for schema failures: individually
  valid documents can install when no prior rules exist. See the [pack rules](TCONSTRUCT_PACK_RULES.md).
- Title IDs must be valid lowercase paths in the Runic namespace. `administrator`
  and `titleless` are reserved. Bad entries are skipped in memory; valid entries and
  the source config file are preserved. Null/malformed conditions cannot earn a title.
- Advancement conditions may now use `Advancement/minecraft:story/mine_stone/equals/true`.
  Existing `minecraft:story-mine_stone` aliases still work when no exact ID exists.

Skill artwork JSON keeps its existing format and folder. The server sends texture
identifiers, not image bytes: distribute the referenced textures in a client resource
pack. Missing assets use Runic's normal artwork. See the [current audit](AUDIT_RESULTS_2026_09.md)
for executed validation and outstanding client/modpack checks.

Skill artwork limits are 128 files, 64 skill overrides and 256 characters per resource ID.
Invalid texture strings now reject the candidate reload instead of becoming a placeholder.
Unknown skills still warn and skip. Files with the same resource ID follow normal pack
priority; different files addressing the same skill resolve in resource-ID order (last wins,
with a warning). Removing the JSON restores defaults on the next successful reload.

## Equipment progression and config saves

New configurations enable Tinkers' material locks and automatic equipment gates for Simply
Swords, Simply More, T.O. Magic and Tide. Native Tinkers addons share the material resolver,
including Jewelry and material tiers above 4. Existing saved `false` settings remain opt-outs;
enable the desired switches explicitly when upgrading an older configuration. See
[progression settings](PROGRESSION_GATING.md) for the full table and disable options.

Config-screen saves now persist without being overwritten on reopening, and saving in a local
world refreshes server gameplay settings. A successfully repaired config can be saved again
without restarting. Remote servers still own their gameplay configuration: edit the server file
and run `/skillsreload` there.
