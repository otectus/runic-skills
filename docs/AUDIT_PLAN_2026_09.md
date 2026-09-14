# September 2026 gameplay audit and implementation plan

Source baseline: `ee17728` (2.1.1). The working tree was clean when this review began.
`CLAUDE.md`, the current Java/resources, and build checks govern this work; previous
audit documents are leads, not evidence that a bug remains or a mechanic works.

## Priorities

1. **P0/P1 — correctness, authority, and persistence.** Trace registration through
   eligibility, configuration, action commits, cooldowns, synchronization and save
   restoration. Fix crashes, reward duplication, stale state and bypasses; protect
   fixes with behavioral tests. Preserve registry IDs and serialized fields.
2. **P1 — crafting and optional integrations.** Check Tinkers' native transaction,
   repair, wear and workshop paths, plus other integrations' load/config gates.
   Keep absent/unsupported mods dormant and preserve paid tool upgrades.
3. **P2 — customization and player feedback.** Review datapack parsing/reload,
   malformed configuration, titles, perk groups, interfaces, icons and tooltips.
   Improve existing mechanics where evidence supports a clear player benefit;
   avoid new content whose behavior cannot be validated.
4. **P2 — coverage and maintenance.** Regenerate the full source trace, record
   findings and limits, update current documentation/changelog, build the shipped
   artifact, run unit/static checks and available real-server GameTest profiles.

## Work streams

- Progression/combat: skills, passives/stats, powers, capability lifecycle and packets.
- Crafting/Tinkers: core crafting, durability, stations, native tools and add-ons.
- Integrations/UI: other optional mods, their perks, presentation and assets.
- Configuration/validation: configuration, datapacks, titles, complete inventory,
  build/test execution, synthesis and release notes.

Every content declaration must appear in the generated trace. A source reference
is evidence of a consumer, not proof of upstream event delivery or live visual
quality. Domain reports distinguish inspected paths, implemented fixes, executable
regressions and checks that still require a real client or modpack.

## Execution record

- Completed source inventory and three parallel domain reviews; regenerated the
  trace for all registered skills, perks, passives, Powers and owned attributes.
- Implemented the prioritized fixes and documented compatibility changes for 2.1.2.
- Initial cache access was resolved by using the existing user Gradle cache through
  an authorized invocation. Baseline and final builds completed successfully.
- Base, combined native-mod and conservative Tinkers beta gameplay profiles passed,
  followed by all 40 isolated production-server checks on the packaged jars.
  Final commands, counts, artifact hashes and remaining acceptance checks are recorded
  in [the results](AUDIT_RESULTS_2026_09.md) and the linked domain reports.
