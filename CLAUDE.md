# Runic Skills — Forge 1.20.1 Mod

## Quick Reference
- **Mod ID**: `runicskills`
- **Package**: `com.otectus.runicskills`
- **Version**: 1.3.7
- **MC**: 1.20.1 | **Forge**: 47.3.0 | **Java**: 17
- **Mappings**: Parchment 2023.09.03-1.20.1

## Build
- `./gradlew build` — full build (output: build/libs/)
- `./gradlew compileJava` — compile-only (faster iteration)
- `./gradlew runClient` — launch dev client

## Project Structure
```
src/main/java/com/otectus/runicskills/
  ├── mixin/           — Mixins (13 total: 4 client, 9 common)
  └── ...              — Skills, perks, config, commands, GUI, events
src/main/resources/
  ├── META-INF/mods.toml
  ├── runicskills.mixins.json
  ├── assets/runicskills/   — lang, models, textures
  └── data/runicskills/     — recipes, loot tables, tags
```

## Key Dependencies
- **YACL** (3.5.0) — config UI (optional, client-only). Server-safe code paths use plain Gson (`com.otectus.runicskills.config.storage.ConfigHolder`); the YACL UI lives in `client/config/`. The `:checkSidedImports` lint forbids YACL executable-class imports outside `client/config/`.
- **KubeJS** (2001.6.5) — scripting integration (optional)
- **Ars Nouveau** — magic system integration (optional)
- **Apothic Attributes / Apotheosis** — attribute system integration (optional)
- **Irons Spellbooks** — spell system integration (optional)
- **L2Tabs / Legendary Tabs** — inventory tab integrations (optional, compile-time only; drop jars into `libs/`). `libs/l2tabs-0.3.3.jar` ships as a minimal API stub so the build works when the real L2Tabs jar isn't locally available — replace it with the real 0.3.3 jar to compile against the full L2Tabs surface.
- **MixinExtras** (0.4.0) — enhanced mixin features

## Terminology
- **Aptitudes** = the skill categories (what players see as "Skills")
- **Skills** = individual abilities within aptitudes (what players see as "Perks")
This naming is internal — the player-facing UI uses "Skills" and "Perks"

## Conventions
- Registration: DeferredRegister on MOD bus
- Config: server-safe `ConfigHolder<T>` POJO + JSON5 storage; YACL UI is a client-only adapter (`client/config/YaclConfigUiBuilder`) reached via reflection from `ConfigHolder.generateGui()`
- Mixins: declared in `runicskills.mixins.json`, uses MixinExtras
- Events: FORGE bus for gameplay, MOD bus for registration
- Access Transformers: none (disabled in build.gradle)
- Optional-mod integrations: load via `RunicSkills.tryLoadIntegration("modid", "FQCN")` so the integration class never enters the main constant pool. Client-side optional integrations (Legendary Tabs, L2Tabs, YACL UI) use a method-reference indirection from `ClientProxy` to avoid JVM-verifier eager-resolution.

## Mixin Targets
Client: ForgeGui, PlayerRenderer, InventoryScreen, GunItem
Common: ItemStack, LivingEntity, Player, PowderSnowBlock, ShulkerBullet, TargetFinder, Villager, CraftingMenu, EnchantmentMenu

## Release History
See `CHANGELOG.md` for per-release notes (security fixes, bug fixes, new integrations).
