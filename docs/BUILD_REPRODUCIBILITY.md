# Build inputs, reproducibility, and the Gradle 9 baseline

Reference for the release-engineering half of the 2.0.0 work (audit findings RS10-001 and
RS10-023). If you are just trying to build the mod, [`README.md`](../README.md#building) is
enough — a fresh clone builds with no manual setup.

## A fresh clone must build

1.9.0 could not be built by anyone but its author. `build.gradle` compiled against
`sfiomn.legendarytabs:legendarytabs:1.20.1-2.0`, resolved from `flatDir('libs')`, but that jar is
excluded by `.gitignore` and is not tracked — while `.gitignore` still carried an exception for the
*previous* `1.1.3.1` jar, which the 2.0 API can no longer compile against. CI was green throughout,
because it restored a warm Gradle cache and never had to resolve the missing artifact from a clean
state.

Two things prevent a repeat:

- **No third-party jar is needed to compile.** Legendary Tabs is compiled against
  [`src/legendarytabsApi/java`](../src/legendarytabsApi/java) — a hand-written signature mirror of
  the 2.0 API, built by the `legendarytabsApi` source set and placed on the compile classpath only.
  L2Tabs still uses the tracked stub jar `libs/l2tabs-0.3.3.jar`.
- **`fresh-clone-build` is the release gate.** It builds with no cache action, a `GRADLE_USER_HOME`
  inside the workspace, and an explicit check that the checkout contains no untracked file and no
  untracked jar in `libs/`. The cached `build` job stays for speed, but it is not evidence.

If you ever need to add a jar to `libs/` again, add the `.gitignore` exception **and** confirm
`fresh-clone-build` passes. That job failing is the only reliable signal.

### Keeping the stub honest

The stub mirrors a specific upstream version and is deliberately minimal: it declares only the
members Runic Skills calls, so calling anything else is a compile error rather than a runtime
`NoSuchMethodError` against a real install. When Legendary Tabs changes its API, update the stub
and the `legendarytabs` `versionRange` in `mods.toml` in the same change. Signatures were taken
from `javap` on the 2.0 release.

## Reproducible jars

Two clean builds of one commit must produce identical bytes.

- `Implementation-Timestamp` comes from `SOURCE_DATE_EPOCH` (release CI sets it from the commit
  date), falling back to the Unix epoch. It was `new Date()`, which guaranteed every build differed.
- `jar` sets `preserveFileTimestamps = false` and `reproducibleFileOrder = true`.
- The Gradle wrapper distribution is pinned by `distributionSha256Sum`.
- Every GitHub Action is pinned to a full commit SHA, with Dependabot proposing bumps
  ([`.github/dependabot.yml`](../.github/dependabot.yml)). A moved tag can otherwise change what CI
  runs with no commit in this repository.

The `reproducible-build` job builds the same commit in two independent containers and fails if the
distributable hashes differ. It runs on `v*` tags and on manual dispatch, not on every push — it is
two full cold builds.

## Mappings

`gradle.properties` declares `mapping_channel` / `mapping_version` and `build.gradle` reads them.
Until 2.0.0 those properties said `official` / `1.20.1` while `build.gradle` hardcoded Parchment
`2023.09.03-1.20.1`, so the declared settings were dead and actively misleading. They now say what
the build actually uses.

## Gradle 9 deprecation baseline

Recorded from `./gradlew --no-daemon clean build --warning-mode all` at the 2.0.0 branch point,
Gradle 8.10:

| Deprecation | Source | Owned by us? |
|---|---|---|
| `org.gradle.util.VersionNumber` (×2) | ForgeGradle, via `build.gradle:25` plugin application | No |
| `ResolvedConfiguration.getFirstLevelModuleDependencies(Spec)` | ForgeGradle, during `compileJava` | No |

**Zero deprecations originate in this project's own build scripts.** All three come from
ForgeGradle 6.0.24 / MixinGradle 0.7.38. The Gradle 9 upgrade is therefore gated on those plugins,
not on work here; re-record this table before attempting the bump, and treat any new row attributed
to `build.gradle` as something to fix first.
