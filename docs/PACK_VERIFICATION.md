# Verifying a build against a real pack

Check a build's Tinkers' member references against production-mapped dependency jars, then
check transformed hooks and gameplay in a running pack. These checks establish different facts.

## Static check: `tools/verify_against_pack.py`

Dev and GameTests run Mojang-mapped; a production pack runs SRG-mapped. `checkMixinRemapping` and
`verifyShippedRefmap` both prove the naming is internally consistent — that a `remap = true`
selector resolves to an SRG name, and that the SRG name made it into the shipped refmap — but
neither has ever read a real pack's jars. `tools/verify_against_pack.py` does: for every mixin
under `mixin/tconstruct/`, it works out the exact name Mixin will look for at runtime (the
refmap's SRG name where the injector is remapped, the literal selector where it is not), then reads
the pack's actual, obfuscated Tinkers'/Mantle jars out of its `mods/` directory and checks that the
member exists there under that exact name and descriptor.

```
python tools/verify_against_pack.py --jar build/libs/runicskills-2.1.0.jar --mods "<pack>/mods"
```

Arguments:
- `--jar` — the shipped `runicskills` jar to read the refmap from. Defaults to the newest
  `runicskills-*.jar` in `build/libs`, explicitly excluding `-all.jar` (the pre-2.0.7 bundle name)
  and `-slim.jar` (the un-bundled jar), so an older or wrong artifact left in `build/libs` is never
  picked over the current `jarJar` output.
- `--mods` — the pack's `mods/` directory to read dependency jars from. When omitted,
  `RUNIC_PACK_MODS` must supply the path; there is no hard-coded machine-specific default.

The script parses every `@Inject`/`@Redirect`/`@ModifyArgs`/`@ModifyArg`/`@ModifyVariable`/
`@ModifyConstant`/`@WrapMethod`/`@WrapOperation`/`@ModifyExpressionValue`/`@ModifyReturnValue`/
`@WrapWithCondition` annotation in `mixin/tconstruct/**`, resolves each `method =` and `@At(target
=)` selector through the jar's refmap when the reference is remapped, and looks the resulting
`(owner, name, descriptor)` up in the pack's real class files by parsing the constant pool and
member tables directly (no bytecode library dependency). A target class absent from the pack (an
optional add-on) is reported as `SKIP`, not a failure. It exits `0` if every checked reference
resolves and `1` otherwise, printing one `FAIL` line per unresolved reference. Record skipped
classes with the result: a successful run with missing add-ons does not validate their hooks.

### 2.1.0 stabilization dependency sample

A static preflight on 2026-09-06 checked **35 references with zero failures and zero skips**
across all 15 Tinkers' mixin target classes. The sample contained original production artifacts
from the Gradle dependency cache, not a user's complete modpack or remapped development jars:

| Dependency | Version |
| --- | --- |
| Tinkers' Construct / Mantle | 3.11.2.166 / 1.11.97 for 1.20.1 |
| TCIntegrations / Tinkers' Levelling Addon | 1.20.1-2.0.25.19 / 1.4.3 |
| Tinkers' Delight / Farmer's Delight | 2.0.3 / 1.20.1-1.3.4 |
| Tinkers' Advanced Core / EtSTLib | 3.0.0-beta.5 / 3.0.0-beta.20 |
| Tinkers' Innovation / Thinking | 1.20.1-3.0.0 / 0.1.6.6.3 |
| Botania / Ars Nouveau | 1.20.1-455-forge / 4.12.7 |
| Curios / Patchouli / GeckoLib | 5.14.1+1.20.1 / 1.20.1-85-forge / Modrinth `aC5KMoNg` (Forge 1.20.1) |

Each staged jar's SHA-1 matched its Gradle cache directory, and its Forge `mods.toml` was read
before use. Staging records original paths and SHA-256 hashes in
`build/reports/production-validation-provenance.json`. Re-run after packaging the final jar:

```text
python tools/verify_against_pack.py --jar build/libs/runicskills-2.1.0.jar --mods build/production-validation-mods
```

That local staging directory is an ignored verification artifact, not a redistributable pack.
Tinkers' Jewelry 1.2.0 was not available in the cache and was not validated by this sample.
The script validates declared selectors and descriptors; it does not establish injection
cardinality, mod loading, recipe effects, third-party mixin conflicts, or multiplayer behavior.

## Live check: the `TCONSTRUCT_COMPAT` log line

The static check proves the bytecode is shaped correctly; it does not prove the mod actually
booted against that pack. For that, deploy the built jar to the pack's `mods/` folder, launch the
server or client once, and grep `logs/latest.log` for the line `TConstructBootstrap` writes at the
end of its constructor:

```
TCONSTRUCT_COMPAT version=<detected> profile=<profile> hooks=<applied>/<total> CLASSIFICATION=<status> REPAIR=<status> ...
```

This line is deliberately a fixed-token, one-line, machine-readable form of the human-readable
`TConstructCompatibilityStatus.describe()` block also logged just above it — written for `grep`
rather than a person, with every `Capability` name appearing on the line whatever its status, so an
absent capability name means the line format changed rather than that the capability is fine.

**`hooks=N/N` is the load-bearing field.** It comes from `TConstructHookLedger.appliedCount()`,
which counts how many core Tinkers' mixins have verified injector call sites in the transformed
target, inspected from Mixin's `postApply` callback, out of how many exist (ten core hooks: see
`TConstructHookLedger.TARGETS`). `N/N` (every hook applied) is a healthy install. A number lower
than the total means at least one mixin was offered — Tinkers' is present, the profile matched, and
the config flag is on — but did not match its target at class-load time: an upstream shape change
the injectors don't expect. That is exactly what `HOOK_UNAVAILABLE` in the per-capability status
(and in `/skills tinkers compat`) reports, and `TConstructHookLedger.hookProblem(Capability)` names
which mixin failed and why. A hook count that is `0/N` for a reason other than Tinkers' being
absent, an unsupported profile, or the integration being disabled by config is the signal this
runbook exists to catch before it reaches a live pack.
