# Verifying a build against a real pack

How to prove a build's Tinkers' mixins will apply in a real, obfuscated modpack — without
launching Minecraft, and then, as a live check, by launching it once and reading one log line.

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
python tools/verify_against_pack.py
python tools/verify_against_pack.py --jar build/libs/runicskills-2.0.7.jar --mods "<pack>/mods"
```

Arguments:
- `--jar` — the shipped `runicskills` jar to read the refmap from. Defaults to the newest
  `runicskills-*.jar` in `build/libs`, explicitly excluding `-all.jar` (the pre-2.0.7 bundle name)
  and `-slim.jar` (the un-bundled jar), so an older or wrong artifact left in `build/libs` is never
  picked over the current `jarJar` output.
- `--mods` — the pack's `mods/` directory to read Tinkers' and Mantle from. Defaults to the
  instance this repository's tooling was developed against.

The script parses every `@Inject`/`@Redirect`/`@ModifyArgs`/`@ModifyArg`/`@ModifyVariable`/
`@ModifyConstant`/`@WrapMethod`/`@WrapOperation`/`@ModifyExpressionValue`/`@ModifyReturnValue`/
`@WrapWithCondition` annotation in `mixin/tconstruct/**`, resolves each `method =` and `@At(target
=)` selector through the jar's refmap when the reference is remapped, and looks the resulting
`(owner, name, descriptor)` up in the pack's real class files by parsing the constant pool and
member tables directly (no bytecode library dependency). A target class absent from the pack (an
optional add-on) is reported as `SKIP`, not a failure. It exits `0` if every reference resolves and
`1` otherwise, printing one `FAIL` line per unresolved reference.

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
which counts how many of the core Tinkers'-targeting mixins Mixin's own `postApply` callback
reported as genuinely applied, out of how many exist (ten core hooks, as of this release: see
`TConstructHookLedger.TARGETS`). `N/N` (every hook applied) is a healthy install. A number lower
than the total means at least one mixin was offered — Tinkers' is present, the profile matched, and
the config flag is on — but did not match its target at class-load time: an upstream shape change
the injectors don't expect. That is exactly what `HOOK_UNAVAILABLE` in the per-capability status
(and in `/skills tinkers compat`) reports, and `TConstructHookLedger.hookProblem(Capability)` names
which mixin failed and why. A hook count that is `0/N` for a reason other than Tinkers' being
absent, an unsupported profile, or the integration being disabled by config is the signal this
runbook exists to catch before it reaches a live pack.
