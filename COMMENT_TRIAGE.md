# Comment triage

Per-comment ledger of CurseForge user reports against Runic Skills, with fix references
back to the codebase. Public usernames omitted (use generic "CurseForge user N"); language
kept professional and concise. Updated each release.

If you reported one of these issues and want to be cited by name, open an issue at
[github.com/otectus/runic-skills/issues](https://github.com/otectus/runic-skills/issues)
and we'll edit the entry.

---

## 1.1.0 (2026-05-08)

### Comment 1a — "how do you disable certain perks?"
- **Status:** Fixed in 1.1.0.
- **Affected:** Config UX (in-game YACL screen).
- **Root cause:** `disabledPerks` and `disabledPassives` had `@SerialEntry` and
  `@ListGroup` annotations but no `@AutoGen` annotation, so the fields persisted to
  disk but never appeared in the in-game config screen. Users had to hand-edit
  `config/RunicSkills/runicskills.common.json5` to discover them.
- **Fix:** Added `@AutoGen(category = "common", group = "general")` to both fields.
  See `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java:57,62`.
  English lang already had labels (`Disabled perks`, `Disabled passives`) and tooltips.
- **Verify:** Open the in-game config screen → `general` group → `Disabled perks`
  and `Disabled passives` lists are visible and editable. Add a perk's registry path
  (e.g. `"berserker"`); reload; the perk's effects should be suppressed.

### Comment 1b — "I keep seeing the message ice and fire disabled repeating forever"
- **Status:** Best-effort fix in 1.1.0; needs more info if it persists.
- **Affected:** Log noise.
- **Root cause:** No exact "ice and fire disabled" string was found in source. The
  closest match was the INFO log at `IceAndFireIntegration.java:75`:
  `"Ice and Fire Integration: Generated {} lock items"`. It fires once per
  `HandlerSkill.ForceRefresh()` call — i.e. on initial cache load and on every
  `/skillsreload` / `/skills register`. Some other mod or workflow may have been
  triggering reloads in a loop that produced the appearance of "forever".
- **Fix:** Demoted all six per-integration "Generated N lock items" lines from `INFO`
  to `DEBUG`. They no longer appear at default log level. Files: `BloodMagicIntegration`,
  `IceAndFireIntegration`, `JewelcraftIntegration`, `LocksIntegration`,
  `MoreVanillaIntegration`, `SamuraiDynastyIntegration`, `SpartanIntegration`.
- **If still spamming on 1.1.0:** Please attach a `latest.log` excerpt to a GitHub
  issue. We'll need to see the exact message and surrounding context to identify the
  source.

### Comment 2 — "is there a way to disable hiding the enchantments?"
- **Status:** Fixed in 1.1.0.
- **Affected:** Tooltip rendering on enchanted items.
- **Root cause:** `MixItemStack.appendEnchantmentNames` mixin keyed off
  `RegistryPerks.SCHOLAR.isEnabled()` to decide whether to globally hide every
  enchantment name (replacing them with a placeholder line). When a user added
  `"scholar"` to `disabledPerks` (the natural way to disable a perk), `isEnabled()`
  returned `false`, so the mixin took the hide branch — globally erasing enchantment
  text from every tooltip. There was no separate config to disable the hiding feature.
- **Fix:** Decoupled the two. The Scholar perk's `isEnabled()` check now only governs
  Scholar's actual XP/enchanting bonus. A new `enableScholarEnchantmentHiding` config
  (default `false`) is the only thing that controls the global hiding. Default `false`
  matches what most users expect — set to `true` if your modpack wants the historical
  hide-until-perk behaviour. Mirrored through `CommonConfigSyncCP`; protocol version
  bumped 4 → 5.
- **Files:** `src/main/java/com/otectus/runicskills/mixin/MixItemStack.java`;
  `src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java` (new field
  near line 88); `src/main/java/com/otectus/runicskills/network/packet/client/CommonConfigSyncCP.java`.
- **Verify:** Add `"scholar"` to `disabledPerks`. Hover an enchanted item — enchantment
  names should still render normally. Then flip `enableScholarEnchantmentHiding=true`,
  reload — names should now be replaced with the placeholder line.

### Comment 3 — "/globallimit … (says this command can't be called client side)"
- **Status:** Fixed in 1.1.0.
- **Affected:** `/globallimit` command — used to set the global skill-level cap.
- **Root cause:** `GlobalLimitCommand.execute` had a backwards guard:
  ```java
  if (command.getSource().getEntity() != null
          && command.getSource().getEntity() instanceof Player) {
      // refuse with "This command can't be called client side!"
  }
  ```
  In singleplayer the integrated server's command source IS the local `ServerPlayer`,
  and on dedicated servers any op invoking the command also runs as a `ServerPlayer`,
  so the guard rejected every legitimate path. Only the server console / rcon path
  worked. Brigadier commands registered via `RegisterCommandsEvent` already only run
  on the logical server, and `requires(s -> s.hasPermission(2))` already gates op-only
  access. The guard was wrong and unnecessary.
- **Fix:** Removed the four-line guard.
  See `src/main/java/com/otectus/runicskills/common/command/GlobalLimitCommand.java:26`.
- **Verify:** In singleplayer with cheats enabled, run `/globallimit 256`. Expected
  result: `Updating playersMaxGlobalLevel, new level: 256`. Same for op-level players
  on multiplayer.

---

## Reporting new issues

The most reliable place to report a Runic Skills issue is the GitHub issue tracker:

- Bug reports / crashes: [github.com/otectus/runic-skills/issues](https://github.com/otectus/runic-skills/issues)
- Always include: Forge version, mod loader version, list of installed mods (or a modpack
  name), and a `latest.log` excerpt with the relevant lines.

CurseForge comments are also read but harder to triage, since users can't reliably attach
crash reports there.
