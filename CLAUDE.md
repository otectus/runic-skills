# Runic Skills — Forge 1.20.1 Mod

## Quick Reference
- **Mod ID**: `runicskills`
- **Package**: `com.otectus.runicskills`
- **Version**: 0.9.2
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
- **YACL** (3.5.0) — config UI (client-side mandatory)
- **KubeJS** (2001.6.5) — scripting integration (optional)
- **Ars Nouveau** — magic system integration (optional)
- **Apothic Attributes / Apotheosis** — attribute system integration (optional)
- **MixinExtras** (0.4.0) — enhanced mixin features
- **Tetra** — tool system integration (optional)

## Terminology
- **Aptitudes** = the skill categories (what players see as "Skills")
- **Skills** = individual abilities within aptitudes (what players see as "Perks")
This naming is internal — the player-facing UI uses "Skills" and "Perks"

## Conventions
- Registration: DeferredRegister on MOD bus
- Config: YACL (yet_another_config_lib_v3), client-side
- Mixins: declared in `runicskills.mixins.json`, uses MixinExtras
- Events: FORGE bus for gameplay, MOD bus for registration
- Access Transformers: none (disabled in build.gradle)

## Mixin Targets
Client: ForgeGui, PlayerRenderer, InventoryScreen, GunItem
Common: ItemStack, LivingEntity, Player, PowderSnowBlock, ShulkerBullet, TargetFinder, Villager, CraftingMenu, EnchantmentMenu

## Review Documents
- `RUNIC_SKILLS_FULL_REVIEW.md` — comprehensive code review
- `RUNIC_SKILLS_REVIEW.md` — summary review
