# Botania for Runic Skills: A Forge 1.20.1 integration playbook

**Bottom line up front.** Botania's design DNA — a universal Mana currency, a visible aim-and-timing power system, "everything is a flower," diegetic documentation via the Lexica Botania, and a rune progression of four Elements → four Seasons → seven Deadly Sins + Mana — maps almost one-to-one onto a perk tree. For Joshua's "Runic Skills," the Wisdom tree should borrow Botania's documentation-as-gameplay, sensory/knowledge tooling (Manaseer Monocle, The Spectator, Lexica unlocks, ore divination) and its "no numbers" ethos; the Magic tree should borrow Mana capacity/regen, elemental/seasonal effects, and Gaia-tier capstones. Hard integration is comfortable on Forge 1.20.1: Botania exposes Forge `Capability<T>` objects (`BotaniaForgeCapabilities.MANA_RECEIVER`, `MANA_ITEM`, `SPARK_ATTACHABLE`, `MANA_TRIGGER`, `WANDABLE`, `RELIC`, etc.), a `ManaItemHandler.instance()` helper for player-inventory mana, a `ManaNetworkEvent` on the Forge bus, and data-pack-friendly recipe types (`botania:runic_altar`, `botania:mana_infusion`, `botania:petal_apothecary`, `botania:terra_plate`, `botania:elven_trade`, `botania:pure_daisy`, `botania:brew`, `botania:orechid`, `botania:marimorphosis`). The API is published at `https://maven.blamejared.com` as the `:api` classifier. No 1.20.1 skill-tree mod currently ships Botania hooks, so this is genuine green field.

---

## 1. What makes Botania Botania

Vazkii states the philosophy plainly on botaniamod.net: **no pipes/wires, no GUIs, no numbers, renewable-first, pleasing visuals from only two particle effects** (sparkle and mana-burst wisp). Mana is the single universal currency; everything — tools, weapons, functional flora, enchanting, teleports, crafting rituals — pays in mana. The aesthetic pillars are **Livingwood/Livingrock** (Pure-Daisy-purified wood and stone that anchor every magical device), sixteen colors of Mystical Petals, pastel Elven upgrades (Dreamwood, Elementium, Dragonstone, Pixie Dust, Alfglass) reached through the Portal to Alfheim, and the green-tinted Terrasteel endgame forged in the Terrestrial Agglomeration ritual from roughly half a Mana Pool per ingot. The endgame boss, the **Gaia Guardian**, is the player's own darkened silhouette; its hard-mode fight caps incoming damage at 25 HP per hit — a useful balance precedent if Joshua's perks enable very high burst damage.

The "soul" of the mod is five interlocking ideas: **everything is a flower** (generators, farms, mob manipulators, item transport — all planted in dirt); **everything has shape** (mana bursts are literal visible projectiles that can be bounced, refracted, detected, magnetized); **the Lexica Botania is the tutorial, the wiki, and the advancement tracker in one** (craftable from book + sapling, contextual sneak-right-click on any Botania block opens that block's entry, Elven Knowledge unlocks half the book); **numbers are hidden on purpose** (Mana Pools show bars, not counts, and a Lexica entry titled "Numerical Mana" contains only the word "no"); and **automation is the reward for ingenuity**, not the grind. A perk tree that echoes those five ideas — diegetic UI, visible ability effects, sparing numerical min-max, flower-shaped passives, and in-world unlocks — will feel native rather than bolted-on.

**The canonical progression arc** for reference: Mystical Flowers → Petal Apothecary → Pure Daisy → Livingwood/rock → Endoflame + Mana Spreader + Mana Pool → Mana Infusion (Manasteel/Mana Diamond/Mana Pearl) → **Runic Altar and the 16 runes** → Manasteel gear → Portal to Alfheim and Elven Trade → Terrestrial Agglomeration → Terrasteel gear → Ritual of Gaia → Gaia Spirits → Relics of the Aesir (Ring of Odin/Loki/Thor, Key of King's Law, Eye of the Flügel, Fruit of Grisaia, and the Dice of Fate that awards them).

## 2. The mana economy at a glance

Mana storage is tiered: **Diluted Pool 10,000**, standard **Mana Pool 1,000,000**, **Mana Tablet 500,000**, **Band of Mana 500,000**, **Greater Band of Mana 2,000,000**. Portal to Alfheim activation costs 200,000 mana split across its two pools; each Elven Trade costs 500; Terrestrial Agglomeration burns approximately 500,000 per Terrasteel Ingot. The **Endoflame**, the canonical first generator, outputs 30 mana/second off any furnace fuel (≈1,200 mana per charcoal, ≈16,000 per coal block). **Dandelifeon** (Game-of-Life-driven, endgame) is orders of magnitude above that when you solve the pattern. **Gourmaryllis** follows mana = hunger² × 70 × variety multiplier. **Thermalily** pulses high output from lava with long randomized cooldowns. **Rosa Arcana** converts XP orbs and nearby player XP. **Munchdew** eats leaves; **Entropinnyum** absorbs TNT explosions; **Kekimurus** eats cakes; **Spectrolus** eats color-cycling wool; **Narslimmus** feeds on slime-chunk slimes; **Rafflowsia** eats whole crafted flowers with variety bonuses. Dayblooms and Nightshades no longer exist in modern Botania; Hydroangeas remains but is explicitly positioned as a stepping-stone.

Mana moves via **Mana Spreaders** that fire discrete Mana Bursts, optionally wearing one of ~22 **Lenses** — Velocity, Potency, Resistance, Efficiency, Bounce, Gravity, Bore, Damaging, Phantom, Magnetizing, Entropic, Influence, Weight, Kindle (fire), Force (piston), Flash (decorative flame), Paintslinger, Warp, Redirective, Celebratory (fireworks), Flare (beam), Tripwire, Messenger — each of which is effectively a ready-made perk concept. **Sparks** on pools trade mana through the air and gain personalities from four **Augments** (Dispersive, Dominant, Recessive, Isolated). The **Mana Fluxfield** is the one sanctioned mana→Forge-Energy bridge and is how Botania talks to Mekanism, Industrial Foregoing, AE2, etc.

## 3. Items and mechanics worth stealing for perks

The **Rods** catalogue is a perk designer's shopping list: **Rod of the Lands/Highlands/Depths** (place dirt/cobble, Highlands works in midair), **Rod of the Seas** (place/refill water), **Rod of the Molten Core** (in-world smelting, Sand→Glass, Cobble→Stone→Smooth Stone), **Rod of the Plentiful Mantle** (divining rod that outlines ores through walls by color), **Rod of the Shaded Mesa** (gravity — levitate or fling entities/items), **Rod of the Unstable Reservoir** (homing arcane missiles), **Rod of the Bifrost** (temporary rainbow bridge ~100 blocks), **Rod of the Hells** (radial fire), **Rod of the Skies** (tornado jump with brief fall-damage grace and Elytra boost), **Rod of the Terra Firma** (terraform to player altitude), **Rod of the Shifting Crust** (7×7×7 block exchange). Each is a one-line perk rationale.

The **tool ladder** runs Manasteel → Elementium → Terrasteel with signature flourishes: **Terra Shatterer** (rank-up pickaxe D→SS with ever-larger AoE), **Terra Truncator** (one-swing tree felling), **Terra Blade** (on-swing beam at full HP), **Elementium Pickaxe** (voids junk, reveals ores), **Elementium Shovel** (chains gravity blocks), **Elementium Axe** (Looting-enabled beheading), **Elementium Sword** (pixie spawn buff), **Starcaller** (falling-star proc), **Thundercaller** (chain lightning), **Thorn/Flare Chakram** (piercing boomerangs), **Mana Blaster** (hand-held spreader with a six-lens magazine). The **Curios** inventory is equally rich — Odin (+10 hearts, regen, elemental resist), **Loki** (multi-target rod effects), **Thor** (3×3 mining), **Aesir** (all three combined), **Ring of the Mantle** (Haste while swinging), **Flügel Tiara** (mana-fueled creative flight with sprint-dash and sneak-glide), the **Sojourner's → Globetrotter's Sash** speed ladder, **Great Fairy Ring** and **Charm of the Diva** (pixies and aggro redirection), **Cloak of Sin / Virtue / Balance** (reflect / negate / split-and-never-lethal), **Cirrus → Nimbus Amulet** (double → triple jump with no fall damage), **Pyroclast → Crimson Pendant** (fire tiers), **Snowflake Pendant** (frost-walker), **Ring of Far Reach**, **Ring of Chordata** (underwater package), **Ring of Magnetization + Solegnolia** (suppressor), **Ring of Dexterous Motion** (dodge roll), **Resolute Ivy** (keep an item on death), **Manaseer Monocle** (see mana bursts through walls and flower ranges), **The Spectator** (highlights inventories/entities holding a queried item), **Third Eye** (mob-glow through walls), **Tectonic Girdle** (knockback negation), **Ring of Correction** (auto-swap to the right tool).

**Functional flora** provide effect blueprints that translate directly to area-buff perks: Agricarnation (crop growth aura), Fallen Kanade (Regen aura), Hopperhock (item pickup), Rannuncarpus (auto-placement), Bergamute (sound silence), Hyacidus (poison), Tigerseye (Creeper petrification), Heisei Dream (mob infighting), Orechid / Orechid Ignem / Marimorphosis (stone→ore/biome-stone), Loonium (loot summon), Vinculotus (Enderman redirect), Spectranthemum (item teleport), Solegnolia (magnet suppressor), Bubbell (underwater air dome), Jiyuulia/Tangleberrie (keep-out/keep-in wards), Daffomill (wind), Clayconia (sand→clay), Jaded Amaranthus (grows mystical flowers), Pollidisiac (auto-breeding), Medumone (stop-motion), Labellia (name tags). Remember Vazkii's rule: a flower either generates OR functions, never both — a nice constraint if you want perks to feel tradeoff-ish.

## 4. The rune system — why it's the keystone for "Runic Skills"

Botania's Runic Altar is the single best structural reference for Joshua's mod, because it is already a three-tier progression graph that Minecraft players know. The altar is built of Livingrock with a Mana Pearl and Mana Diamond; ingredients orbit visibly, mana charges it from an aimed Spreader, and the craft finalizes when the player tosses Livingrock and right-clicks with the Wand of the Forest. **Crucially, all runes used as catalysts in higher-tier recipes are returned** — they gate, they don't deplete. This is the right precedent for "perk unlock items" if Joshua wants physical-object gating.

The sixteen runes cluster into four Elemental (Water, Fire, Earth, Air — ~5,200 mana each, 3 Manasteel + theme ingredients, output x2), four Seasonal (Spring, Summer, Autumn, Winter — ~8,000 mana each, two Elemental catalysts + themed ingredients), seven Deadly Sins (Lust, Gluttony, Greed, Sloth, Envy, Wrath, Pride — ~20,000 mana each, 1 Elemental + 1 Seasonal catalyst + 2 Mana Diamonds), and the standalone **Rune of Mana** (5 Manasteel + 1 Mana Pearl, ~5,200 mana, output x2). The sin-rune catalyst pairings are thematically rich and reusable: **Lust = Summer+Air, Gluttony = Winter+Fire, Greed = Spring+Water, Sloth = Autumn+Air, Wrath = Winter+Earth, Envy = Winter+Water, Pride = Summer+Fire**. Each rune's downstream recipes reveal its "domain": Water→Hydroangeas/Chordata/Snowflake/Rod of Seas/Bubbell; Fire→Thermalily/Pyroclast/Crimson/Rod of Hells/Flare Chakram; Earth→Orechid/Exoflame/Marimorphosis/Tectonic Girdle/Clayconia; Air→Tornado Rod/Cirrus/Nimbus/Flügel Tiara/Daffomill; Spring→Agricarnation/Fallen Kanade/Hopperhock; Summer→Gourmaryllis/Narslimmus; Autumn→Loonium/Rafflowsia/Kekimurus; Winter→Bergamute/Tigerseye/Snowflake; Lust→Pollidisiac/Vinculotus/Charm of Diva; Gluttony→Munchdew/Kekimurus/Band of Mana; Greed→Rannuncarpus/Spectranthemum/Magnetization; Sloth→Shifting Crust/Dexterous Motion; Wrath→Thundercaller/Starcaller; Envy→Heisei Dream/Cloak of Sin/Tainted Blood; Pride→Charm of the Diva/Ring of Far Reach/Flügel Tiara. That domain mapping is the exact raw material for naming and flavoring perk nodes.

## 5. Hard integration — the Forge 1.20.1 API surface

The Botania API in 1.20.1 lives under `vazkii.botania.api.*` inside the `Xplat` and `Forge` source sets (the `1.20.x` branch). The Forge capability registry class is `vazkii.botania.api.BotaniaForgeCapabilities`, which exposes — as confirmed in the `ForgeCommonInitializer` source — the following `Capability<T>` objects: **`MANA_RECEIVER`**, **`MANA_ITEM`**, **`SPARK_ATTACHABLE`**, **`MANA_TRIGGER`**, **`WANDABLE`**, **`RELIC`**, **`AVATAR_WIELDABLE`**, **`BLOCK_PROVIDER`**, **`COORD_BOUND_ITEM`**, **`EXOFLAME_HEATABLE`**, **`HORN_HARVEST`**, **`HOURGLASS_TRIGGER`**, **`MANA_GHOST`**. The key interfaces live in `vazkii.botania.api.mana.*` and include `ManaReceiver` (with `receiveMana(int)`, `getCurrentMana()`, `getMaxMana()`, `isFull()`, `canReceiveManaFromBursts()`), `ManaPool`, `ManaCollector`, `ManaItem` (for charge-able item capabilities), `ManaSpreader`, `ManaBurst`, `ManaTrigger`, `BurstProperties`, `ICompositableLens`, `ILensEffect`, and utilities `ManaItemHandler` (with `requestMana`, `requestManaExact`, `dispatchMana`, `dispatchManaExact` as both ItemStack- and inventory-scoped variants).

The cross-loader facade `vazkii.botania.xplat.XplatAbstractions` provides loader-agnostic lookups — `findManaReceiver(level, pos, state, be, direction)`, `findSparkAttachable(...)`, `findManaTrigger(...)`, `findWandable(...)` — and also exposes `fireManaNetworkEvent(ManaReceiver, ManaBlockType, ManaNetworkAction)` which on Forge posts a `ManaNetworkEvent` to `MinecraftForge.EVENT_BUS`. Events worth subscribing to include **`ManaNetworkEvent`** (pool/spreader added or removed from the network), **`ManaProficiencyEvent`** (for modifying armor-set mana discounts), **`ManaDiscountEvent`** (ad-hoc discount), **`ManaItemsEvent`** (listing mana items on a player), and **`ElvenPortalUpdateEvent`** (portal state). `BotaniaAPI.instance()` is explicitly **marked "Do not Override"** — the mod throws `IllegalAccessError` at server start if any other class implements it, so interop must go through capabilities and events, never replacement.

Recipe types are fully data-pack-ready: their classes are `RecipeRuneAltar`, `RecipeManaInfusion`, `RecipePetals`, `RecipePureDaisy`, `RecipeElvenTrade`, `RecipeBrew`, and the Terrestrial Agglomeration lives in `botania:terra_plate`. Each ships with a standard `RecipeSerializer` and JSON schema (input list, output, mana cost, optional catalyst state for mana infusion). A datapack in your mod jar at `data/runic_skills/recipes/runic_altar/*.json` using `"type": "botania:runic_altar"` will load automatically when Botania is present, and the vanilla recipe manager silently drops it otherwise — the cleanest form of soft-failing hard integration.

**Gradle setup (Forge 1.20.1):**
```gradle
repositories { maven { url 'https://maven.blamejared.com' } }
dependencies {
  compileOnly fg.deobf("vazkii.botania:Botania:1.20.1-446:api")
  runtimeOnly fg.deobf("vazkii.botania:Botania:1.20.1-446") // optional, for dev testing
}
```

**mods.toml optional dep:**
```toml
[[dependencies.runic_skills]]
    modId="botania"
    mandatory=false
    versionRange="[1.20.1-446,)"
    ordering="AFTER"
    side="BOTH"
```

**Read/write pattern, with class-load isolation:**
```java
// In main mod class, guard all Botania touches:
if (ModList.get().isLoaded("botania")) {
    BotaniaCompat.init();   // entire class resolved lazily here
}

// BotaniaCompat.java — safe to import vazkii.botania.api.* freely:
public static int drainNearbyPool(Level level, BlockPos center, int want) {
    for (BlockPos p : BlockPos.betweenClosed(center.offset(-6,-3,-6), center.offset(6,3,6))) {
        BlockEntity be = level.getBlockEntity(p);
        if (be == null) continue;
        var cap = be.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER).orElse(null);
        if (cap instanceof ManaPool pool && pool.getCurrentMana() > 0) {
            int take = Math.min(want, pool.getCurrentMana());
            pool.receiveMana(-take);
            return take;
        }
    }
    return 0;
}

public static boolean chargePlayer(Player p, int amount) {
    // Drains from any ManaItem in inventory (tablet, ring, etc.)
    return ManaItemHandler.instance().requestManaExact(
        p.getMainHandItem(), p, amount, true);
}
```

The hard rule for class-load isolation on Forge is that **any class that directly imports a `vazkii.botania.*` type must not be referenced from a code path that can execute when Botania is absent**. Put `BotaniaCompat` behind a `Supplier<Object>` or a static init guarded by `ModList.get().isLoaded`. Reflection is unnecessary — the `:api` classifier is small enough to ship as `compileOnly` without bloating the jar.

**Prior art to study.** Applied Botanics Addon (ramidzkh, 1.20.1) pipes mana through AE2 P2P tunnels using `ManaReceiver` caps; MythicBotany (noeppi_noeppi, 1.20.1) registers new flowers via `XplatAbstractions.createSpecialFlowerBlock` and new recipe types via `IForgeRegistries.RECIPE_SERIALIZERS`; Botanical Machinery (LMor) wraps mana pools in adjacent block entities with their own caps; Botanic Additions (Zeith) adds Mana Tesseracts by implementing `ManaReceiver` on a custom BlockEntity and delegating across dimensions. All of them depend on the `:api` jar via `maven.blamejared.com` and guard initialization behind `FMLCommonSetupEvent.enqueueWork`.

## 6. Soft integration — aesthetic and naming patterns that land

Botania's naming vocabulary has three safe layers to borrow: **material/prefix** (petal-, floral-, living-, mystic-, elven-, mana- — all generic fantasy terms, freely usable except the trademarked compounds Livingwood/Livingrock/Dreamwood/Manasteel/Terrasteel/Gaia Spirit/Lexica Botania/Wand of the Forest), **motif** (druidic/Norse/Greek/pseudo-Latin botanical binomials — Anthos, Viridis, Sylvana, Yggdra-, Bifrost-, Chloris-), and **structural** (the "X of Y" pattern — Ring of Odin, Rod of the Skies — and the sin/season/element tripartite). Safe new coinages for Runic Skills perks: Petalbound, Rootwake, Sapflow, Verdantheart, Grovecall, Thornward, Moonpetal, Sunbloom, Bifrost-borne, Skaldsong, Runemarked, Channeler, Wellspring, Greenmantle, Florarum. Avoid the trademark-adjacent coinages (anything ending in "-umus/-yllis/-ifeon" that directly matches a named Botania flower, "Gaia Guardian," "Pure Daisy," and the proper-noun relics).

For particle/visual feel, Botania leans on two particle primitives: a **16-colored sparkle** and a **mana-burst wisp trail**. A Runic Skills perk that applies an aura-style colored sparkle while active and a brief wisp trail on activation will read as Botania-native without borrowing any asset. For UI, match the **Patchouli** aesthetic — parchment, sentence-case sans-serif, pastel-on-cream — rather than a PoE-style portrait menu. Patchouli is itself a Vazkii mod and is already a hard-coded dependency of Botania, so using it for your perk book is free.

## 7. Ecosystem signals

Among 1.20.1 Forge addons: MythicBotany (Alfheim+, Alfsteel, Aesir flavor), Botanic Additions (Mana Tesseract, Gaia Plate, Dreaming Pool), Botanical Machinery (GUI-machine versions that deliberately break Vazkii's no-GUI rule), Applied Botanics Addon (AE2 P2P mana), Botanic Pledge (Yggdrasil boss/armor tier), Garden of Glass (official skyblock), Botania Editor (data-driven tweaks), BotaniaCombat (Fabric-only in 1.20.1). ExtraBotany's modern fork exists but is philosophically disliked by Vazkii. In modpacks, **ATM9 and ATM9 To The Sky** treat Botania as a first-class progression axis; Enigmatica 6/9's wiki is the best publicly-available Botania-automation tutorial base. For skill-tree prior art: **Project MMO** ships Botania Magic-XP defaults (soft/data), **Passive Skill Tree** (Daripher) and **Pufferfish's Skills** are the leading 1.20.1 skill-tree frameworks but have **zero Botania-specific content**. This is open territory.

One naming warning: "Runic Skills" collides with the existing Runic (Sweetygamer), Runics (waggerra), RUNIC: Enchants (ReviloDev), Runic Ages (LaDestitute), and of course Botania's Runic Altar itself. A tagline ("Runic Skills: Petals of the Living Branch" or similar) will help discoverability.

## 8. Design philosophy translated to perk-tree rules

Before the perk list, four design rules fall out of the research:

1. **Two trees, two flavors.** Wisdom = Botania's knowledge layer (Lexica, Manaseer, Spectator, divining, Corporea awareness, auto-identify, ore sense, Elven Knowledge). Magic = Botania's power layer (mana capacity/regen, elemental/seasonal procs, sin-risk/reward capstones, Gaia-tier ultimates).
2. **Hard integration stays optional.** Every perk should have a **soft form** that works standalone and a **hard form** that activates when `botania` is loaded and adds a Botania-specific clause.
3. **Use the rune triad as tier structure.** Low-tier perks are Elemental/Mana-flavored, mid-tier are Seasonal, high-tier are Sin or Gaia-flavored. This mirrors the Runic Altar progression players already know.
4. **Prefer diegetic effects over numbers.** Where a perk could be "+10% mining speed" or "your pickaxe breathes faster when near mana," pick the second framing and let the number be invisible.

---

## 9. Wisdom tree — perk brainstorm

The Wisdom tree translates Botania's *knowledge layer*: the Lexica, the Manaseer Monocle, The Spectator, divining, Corporea queries, Elven Knowledge. Every entry below lists **Soft (S)** — works standalone, Botania-flavored — or **Hard (H)** — activates extra behavior when Botania is loaded.

**Low tier (Elemental / Rune-of-Mana equivalent — entry perks).**
- **Petal-Reader** *(S)* — identifying any plant/flower block reveals an extra tooltip line with its effect; rationale: the core Lexica fantasy of "look at a flower, know what it does."
- **Rune of Mana: Resonance** *(H)* — +10% max mana of any external mana resource your mod defines; when Botania loaded, inventory Mana Tablets/Bands gain +10% effective capacity via a `ManaProficiencyEvent` listener.
- **Sparkle-Sense** *(S)* — glow effect briefly on any magical block or entity you look at within 12 blocks; echoes Botania's sparkle particle as a perception cue.
- **Dowser's Twig** *(H)* — right-click the ground to reveal the nearest ore of a type (vanilla only); when Botania loaded, piggybacks on `Rod of the Plentiful Mantle`'s ore-outline effect for a 3-second window at no mana cost.
- **Green Thumb** *(S)* — 1-in-8 chance of doubled drops when harvesting wild flowers or leaves; flavor matches Munchdew/Jaded Amaranthus.
- **Livingbark Student** *(S)* — chopping any log gives a small chance to drop an extra sapling; Pure-Daisy-adjacent flavor without naming it.

**Mid tier (Seasonal — specialization).**
- **Spring: Agricultor's Eye** *(S)* — crops within 8 blocks have a visible growth-stage indicator overhead; *(H)* recognizes Botania's functional flowers and displays their internal tick progress.
- **Summer: Forager's Palate** *(S)* — eating any food gives a brief +20% XP gain for 30 seconds; echoes Gourmaryllis variety mechanics without copying them.
- **Autumn: Loot-Hunter's Intuition** *(S)* — structure chests within 64 blocks are outlined through walls for 3 seconds on activation; *(H)* if near a Loonium, it shares the reveal.
- **Winter: Still Listener** *(S)* — hostile mob detection ring (compass-like) while sneaking; *(H)* while inside a Bergamute radius, detection doubles as silenced.
- **Manaseer's Lens** *(H)* — makes any Botania Mana Burst visible through walls within 24 blocks even without the Monocle worn; *(S)* version: your mod's own spell projectiles gain a through-wall glow.
- **Corporea Query** *(H)* — chat command `/know <item>` returns a tally across all nearby inventories that share a Corporea Spark color; *(S)* version: same command works on vanilla chests within 16 blocks.

**High tier (Sin / Gaia / Elven Knowledge equivalent — capstones).**
- **Greed: Cartographer-Prospector** *(S)* — periodic client-side ore heatmap overlay (toggle); *(H)* works through Rod of the Plentiful Mantle without consuming mana.
- **Pride: Far Reach** *(S)* — +2 blocks interaction reach; rationale: the Ring of Far Reach's own flavor quote: "tap into Pride, nothing will be out of your reach."
- **Sloth: Lazy Swap** *(S)* — auto-swaps hotbar to the correct tool when you start breaking a block; *(H)* stacks with Ring of Correction without consuming its mana.
- **Envy: Mirror's Read** *(S)* — seeing another player within 16 blocks reveals their held item + armor tier in tooltip; flavor match for Cloak of Sin without copying its damage effect.
- **Elven Knowledge** *(H)* — tossing your Runic Skills perk-book into an active Alfheim Portal upgrades it, unlocking an "Alfheim chapter" with 3 bonus perks (soft fallback: after reaching level 50, the chapter unlocks automatically); rationale: the single most beautiful diegetic unlock in Botania, and you can literally replicate the Lexica trick by listening for `ElvenPortalUpdateEvent`.
- **Gaia's Witness** *(H)* — after the player first defeats a Gaia Guardian (tracked via the `botania:main/gaia_guardian` advancement), the Wisdom tree permanently gains one extra perk slot; *(S)* fallback: awarded on first Wither kill.
- **Oracle of the Nine Runes** *(S/H capstone)* — hovering the perk book over any block names its material tier and magical affinity; *(H)* names the specific functional flower and its mana throughput category (Low/Medium/Ludicrous) via the Lexica's own tier ontology.

---

## 10. Magic tree — perk brainstorm

The Magic tree translates the *power layer*: mana capacity, regen, elemental procs, seasonal timing, sin-flavored risk/reward, Gaia-tier ultimates.

**Low tier (Elemental / Rune-of-Mana — foundation).**
- **Inner Wellspring** *(S)* — +15% to your mod's max mana pool; *(H)* the per-player "virtual Band of Aura" trickle-charges any Mana Tablet/Band in inventory at +50% base rate by posting a `ManaProficiencyEvent`-style discount.
- **Rune of Water: Tidewoven** *(S)* — spells/abilities cost 10% less mana while standing in rain or water; *(H)* Hydroangeas and Bubbell in your vicinity reach you even outside their nominal radii for passive effects.
- **Rune of Fire: Emberheart** *(S)* — +1 fire damage on attacks; *(H)* aligns with Kindle Lens and Pyroclast Pendant — taking lava damage pays back as 100 mana per half-heart to any inventory mana item.
- **Rune of Earth: Stone-Rooted** *(S)* — +1 armor while standing on stone-family blocks; *(H)* if Marimorphosis products (Metamorphic Stones) are under you, +2 armor.
- **Rune of Air: Featherstep** *(S)* — halved fall damage; *(H)* extends Cirrus/Nimbus Amulet jumps by one extra mid-air jump every 10 seconds.
- **Band of Aura: Passive Channel** *(H)* — replicates the Band of Aura mana-trickle for your mod's mana resource; stacks with a worn Band of Aura to compound.

**Mid tier (Seasonal — spell-flavor branches).**
- **Spring: Verdant Pulse** *(S)* — once per 30 seconds, bone-meal a 5×5 area around you; *(H)* reads as if an Agricarnation is present, drawing from the nearest Mana Pool for mana instead of self.
- **Summer: Solar Conduit** *(S)* — during daytime, +10% ability damage; *(H)* Thermalilies near you vent mana 20% faster to spreaders bound to them.
- **Autumn: Harvest Tithe** *(S)* — kills grant a 3% chance to drop a random vanilla gem/nugget; *(H)* a Loonium within 16 blocks borrows your current Luck attribute when rolling loot tables.
- **Winter: Frostbound** *(S)* — melee attackers take 1 cold damage and are Slowed 1 for 2 seconds; *(H)* stacks with Snowflake Pendant into a 2-block frost-walker trail.
- **Lens Mastery: Velocity** *(S)* — projectile/spell travel speed +25%; *(H)* applies to Mana Blaster bursts you fire and Spreader bursts bound to you.
- **Lens Mastery: Potency** *(S)* — next ability every 15 seconds deals 2× damage; *(H)* mirrors Potency Lens behavior on any burst you cause.

**High tier (Sin / Gaia / relic capstones — pick-one branches).**
- **Lust: Pixie Affinity** *(S)* — 5% chance on taking damage to summon an allied spirit (a 4-HP wisp that attacks the aggressor for 5 seconds); *(H)* if wearing any Elementium piece or Great Fairy Ring, pixie spawn chance gets a flat +10% added via a `LivingHurtEvent` listener.
- **Gluttony: Cake Combustion** *(S)* — eating food over-caps your hunger and converts the excess to a 30-second regeneration; *(H)* a Kekimurus you own within 24 blocks gets a 30% mana-output boost for the same duration.
- **Greed: Magnetite** *(S)* — item magnet radius 6 blocks, sneaking disables; *(H)* bypasses Solegnolia suppression once per 60 seconds.
- **Sloth: Unbound Step** *(S)* — no movement penalty in soul sand/cobweb/slime; *(H)* Stone of Temperance does not affect you.
- **Envy: Mirrored Wrath** *(S)* — reflect 20% of incoming damage; *(H)* interacts with Cloak of Sin's 10-second cooldown by halving it.
- **Pride: Crown of Reach** *(S)* — attack and interaction range +3 blocks; *(H)* Mana Blaster gains 3 extra range and no spread.
- **Wrath: Thundercall** *(S)* — critical hits roll a 5% chance to chain a small lightning strike to another nearby hostile; *(H)* stacks with Thundercaller's chain without exhausting its mana budget.
- **Gaia's Gift: Relic Attunement** *(H, ultimate-capstone)* — one relic (player's choice from Odin/Loki/Thor/Key of King's Law/Eye of the Flügel/Fruit of Grisaia) equipped becomes soulbound to a perk slot rather than the curio slot, freeing the curio slot for another item; *(S)* fallback: one dedicated "relic slot" is added to the player that accepts any curios-tagged amulet.
- **Terrasteel Ascension** *(S ultimate)* — passive: +4 max HP, +1 armor toughness, food saturation decays 30% slower; rationale: echoes Terrasteel armor set bonus; reserve this for a 40+ level gate.
- **Flügel's Grace** *(S)* — triple jump with safe landing and a 2-second sprint-dash cooldown; *(H)* consumes mana from a Flügel Tiara if worn, otherwise from your mod's pool.
- **Manastorm** *(S ultimate)* — once per 5 minutes, detonate a personal mana overload around you for area damage scaled to your current mana; *(H)* if a Mana Pool within 16 blocks has mana, it's drained first for double damage — direct echo of the Manastorm Charge.

---

## 11. Final implementation notes for Joshua

Ship the Botania `:api` jar via `maven.blamejared.com` as `compileOnly`, and isolate every Botania import inside a `BotaniaCompat` class that is **only touched** through `if (ModList.get().isLoaded("botania")) { ... }`. Register your hard-integration listeners inside `FMLCommonSetupEvent.enqueueWork`; register recipe JSONs under `data/runic_skills/recipes/<botania_type>/*.json` and they will self-activate only when Botania loads them. Prefer `ManaItemHandler.instance().requestMana(stack, player, amount, remove)` for player-inventory drains — it walks every `ManaItem`-tagged stack including Tablets, Bands, Mana Mirrors, and the Terra Shatterer. For block-level reads, use `XplatAbstractions.INSTANCE.findManaReceiver(level, pos, state, be, dir)` rather than the raw capability — it handles block-lookaside registrations (Mana Void, Drum, Mana Detector) that pure capability lookup misses. Listen to `ManaNetworkEvent` on `MinecraftForge.EVENT_BUS` to map mana infrastructure in a chunk for perks like Corporea Query. Never override `BotaniaAPI.instance()` — Vazkii's code throws `IllegalAccessError` at server start if you do, and the error message is the rudest in mod-loading history.

For UI, depend on **Patchouli** (already hard-required by Botania) and author your perk codex as a Patchouli book — this gives you the Lexica aesthetic for free and respects the "documentation-as-gameplay" rule. For particles, reuse `ParticleTypes.INSTANT_EFFECT` and `ParticleTypes.ENCHANT` with tinted color parameters rather than shipping new textures; Botania's own visual language is color + sparkle, not texture novelty. Performance: nearby-pool reads should be gated behind a 10-tick (half-second) throttle and limited to a 12-block radius; do not scan every player tick, and never poll `getCurrentMana()` across dimension changes.

Finally, two strategic choices. First, give every perk both a soft and a hard effect in the same node — players with Botania feel rewarded without players without it feeling shortchanged. Second, mirror Botania's three-tier rune progression as your tier structure explicitly (Elemental/Mana → Seasonal → Sin/Gaia), and the thematic labeling will carry 80% of the design work. The rune domain map in Section 4 tells you exactly which Botania items and flavors belong to which node.

## Conclusion

Botania's power as a design reference comes from the tight coupling of its economy (one currency, visible flow), its aesthetic (two particles, sixteen colors, green-tinted materials), its rune progression (4/4/7+1), and its documentation-as-gameplay ethos — each of which has a Forge 1.20.1 API surface exposed enough to integrate against without violating Vazkii's design wishes. The smartest move for Runic Skills is to use Botania's **rune taxonomy as your tier scaffold** (Elemental low-tier, Seasonal mid-tier, Sin high-tier, Gaia capstone), to write **dual soft/hard** effects into every node so the mod feels complete standalone and enriched when Botania is loaded, and to route all hard integration through `BotaniaForgeCapabilities` + `ManaItemHandler` + data-driven recipe types — never through reflection or API overrides. The API is stable, the ecosystem is permissive, and no existing 1.20.1 skill-tree mod occupies this niche: Runic Skills can become the canonical Botania-flavored perk tree if it ships with the Patchouli codex, the rune-tiered structure, and the graceful degrade pattern baked in from day one.