# Inventory tab integration repair — 2026-09-08

The two reported screenshots show independent tab providers drawing over one another.
The tested instance contains CustomNPCs GBPort `1.20.1.20260711` and L2 Tabs `0.3.3`,
the latter embedded in L2 Library `2.5.3`. Adding Legendary Tabs `1.20.1-2.0` adds
a third strip. Removing Legendary Tabs therefore does not solve the original collision.

## Cause and correction

Runic's ownership check previously suppressed only its own fallback strip and its
CustomNPCs insertion. It did not suppress CustomNPCs' original widgets or reconcile
L2 Tabs with Legendary Tabs. The two crafting tables and overlapping books in the
second screenshot belong to different native strips with different anchors and pitches.

The client now shares destinations between the optional adapters and chooses one
visible provider per screen, in this order: Legendary Tabs, L2 Tabs, CustomNPCs,
Runic's built-in strip. CustomNPCs' original faction/quest actions and item icons
are represented by native buttons in the selected provider. Legendary also receives
L2's filtered non-inventory destinations. Each provider retains its own button skin,
tooltips, pagination and navigation behavior.

Suppression checks usable screen widgets, not just mod installation or successful
startup registration. Lower-priority widgets and their paging controls are hidden
and made inactive together, then their previous state is restored before the next
provider update. Inventory recipe-book displacement and the taller Skills panel use
actual panel geometry. Optional APIs remain isolated in client adapters; their
signature mirrors are compile-only and never enter the distributable jar.

Faction and quest action availability follows CustomNPCs' native checks independently
of Runic's preference for inserting Skills into the original CustomNPCs strip. L2's
Curios-only preference applies only when Curios is installed, matching the upstream
setting's documented behavior.

Visual inspection also found a separate field-notes button collision with JEI's pager.
The tutorial toast in the original screenshots temporarily pushed JEI down and masked
that collision. An optional JEI plugin now reserves the actual visible field-notes
button rectangle, so JEI can arrange its controls around it after the toast disappears.
The button's availability and navigation are unchanged.

Exact runtime jars were inspected with `javap`. Upstream references:
[Legendary Tabs source](https://github.com/Alex-Hashtag/LegendaryTabs) and
[L2 Tabs source](https://github.com/Minecraft-LightLand/L2Tabs).

## Reproducing the client checks

The runner requires a staged disposable pack, installed launcher libraries, Java 17,
and an existing synthetic validation world in the staged copy. It does not copy mods
or create the world itself. A schematic invocation is:

```text
python tools/run_client_pack_validation.py --directory <staged-copy> --install <launcher-install> --java <java17-executable> --tabs --tab-profile <evidence-label>
```

This launches an offline synthetic player. `--tab-profile` names the evidence folder;
the presence or absence of Legendary is determined by the staged `mods` directory.
The separate `productionValidationJar` contains `InventoryTabsClientChecks`;
it is never installed in the user's instance. The test captures Minecraft's actual
framebuffer, widget identities, bounds and selected states, and exercises navigation,
recipe-book toggles, GUI-scale changes and screen reinitialization.

The staging manifest under `build/tab-client-validation/runtime-stage-manifest.json`
records the original instance mod hashes. Each staging directory receives its own
`tab-validation/` evidence: `build/tab-client-validation` for the current instance and
`build/tab-client-legendary-validation` for Legendary. The final Legendary evidence is
also copied into the first directory for the installation check. No original world
save is used by these checks.

Each of these two staged profiles, containing L2 Tabs, Curios and CustomNPCs, captures
20 settled screens: inventory, Attributes, L2's Curios list,
Skills, inventory return, CustomNPCs factions and quests, return navigation, recipe
book open/closed, GUI scales 3 and 4, and screen reinitialization. Clicks use the
screen's ordinary mouse dispatch.
The configured L2 inventory action opens Curios through a server packet, and current
Curios uses `CuriosScreenV2`; assertions therefore run after the transition settles.
CustomNPCs' quest screen discards the superclass click return value even when navigation
succeeds. The check verifies the resulting screen rather than that return value.
Legendary's FTB Quests and CustomNPCs Quests are distinct actions and are selected by
their exact destination IDs.

The copied test configuration disables Citadel's synchronous experimental-world
confirmation bypass. The harness clicks the ordinary confirmation after initialization
so the previous world-open attempt can release its storage lock. This is confined to
the disposable copies; no corresponding user configuration change is needed.

## Validation and installation

The complete offline Gradle build passed all 358 tests and the shipped-refmap check.
Compile-only optional API mirrors were checked against the installed mod jars and are
absent from the release archive. The final full-pack client runs both passed:

| Staged configuration | Captured screens | Failures | Exit/save | Duration |
| --- | ---: | ---: | --- | ---: |
| Current instance, without Legendary | 20 | 0 | Exit 0; clean save | 438 seconds |
| Same pack with Legendary Tabs 2.0 | 20 | 0 | Exit 0; clean save | 422 seconds |

Final screenshots show one aligned row throughout inventory, Skills, factions,
quests, Attributes, and Curios navigation. Recipe-book displacement, both tested GUI
scales, and screen reinitialization passed. Legendary's native pager makes additional
destinations reachable on narrow panels; its normal page reset behavior is retained.
The final Legendary inventory screenshot also shows the field-notes button clearly
separated from JEI's pager after the tutorial toast has disappeared. JEI's debug log
confirms registration of `runicskills:inventory_controls`.

Evidence: `build/tab-client-validation/tab-validation/no-legendary/result.json` and
`build/tab-client-validation/tab-validation/legendary/result.json`, with adjacent PNGs
and client logs. Earlier passing 16-screen runs are preserved separately under
`*-pre-jei-exclusion`; the final installation uses the 20-screen results above.

Final release artifact: `build/libs/runicskills-2.1.2.jar`.

```text
SHA-256 18811CECD86F1C7EE061CE2EA4638E403BA9F002D5C927A0167C8ABC9A28E30A
```

The installation script requires both complete 20-screen results, matches the final
artifact against both tested jars, verifies the original backup, and verifies the
installed replacement. The receipt is saved as `build/tab-fix-installation-receipt.json`
and beside the instance backup. Only the core Runic Skills jar is replaced; the user's
existing Legendary installation choice, other mods, configuration, and saves are
preserved.

Installed successfully on 2026-09-08 at 19:52 America/New_York into
`C:\Users\crims\curseforge\minecraft\Instances\Runic Skills Tests\mods\runicskills-2.1.2.jar`.
The installed SHA-256 matches the final artifact above. The verified original is at
`C:\Users\crims\curseforge\minecraft\Instances\Runic Skills Tests\runicskills-backups\2026-09-08_19-52-43-inventory-tabs\runicskills-2.1.2.jar`.
Its SHA-256 is `024B8559267D54E45318B536C2F5DEC8557436A148EA550EFD6382D8BB9620AC`.
Restart Minecraft to load the replacement. To roll back, close Minecraft and restore
that backup to the installed mod path.
