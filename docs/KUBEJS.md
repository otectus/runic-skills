# KubeJS integration

Runic Skills posts progression events to KubeJS server and client scripts, so you can observe or gate skill level-ups — including advancement-based progression rules.

## Supported versions

KubeJS 2001.6.5-build.14 and later. Compiled against build.14; tested with build.14 through build.26.

## server_scripts vs client_scripts

**Progression rules belong in server_scripts.** The server post to `RunicSkillsEvents.skillLevelUp` is authoritative: a cancellation prevents the skill from rising, the player is charged no XP, and a denial message is sent once to chat. Client-side cancellation only stops the GUI from sending its packet and is not authority — another client, a command, or a hand-sent packet bypasses it. Observing progression for logging or UI purposes can live in client_scripts, but enforcement goes on the server.

> **⚠️ Security reminder:** gates you write in scripts can be worked around by hand-sent packets unless they run on the server. Only server_scripts are authoritative.

## Skill level-up event

Listen for a player levelling a skill:

```js
RunicSkillsEvents.skillLevelUp(event => {
    if (event.serverSide) {
        console.info(`${event.player.name.string} leveled ${event.skill.name} to ${event.newLevel}`)
    }
})
```

## Event fields

The event carries these fields and methods:

| Field | Type | Notes |
| --- | --- | --- |
| `event.player` | Player | the player who is leveling |
| `event.skill` | Skill | the skill being leveled |
| `event.skill.name` | string | registry name of the skill (`strength`, `dexterity`, etc.) |
| `event.oldLevel` | int | the player's current level before this attempt |
| `event.newLevel` | int | the level they would reach if allowed |
| `event.cause` | string | why it is changing: `purchase` (player clicked), `command`, or `respec` (reserved for future use) |
| `event.serverSide` | boolean | true on the server post, false on the client convenience post |
| `event.clientSide` | boolean | true on the client convenience post, false on the server post |
| `event.setCanceled(true)` | void | cancel the level-up (American spelling) |
| `event.setCancelled(true)` | void | cancel the level-up (British spelling) |
| `event.cancel()` | void | cancel the level-up (KubeJS spelling) |
| `event.cancel('reason')` | void | cancel and set the denial message shown to the player |
| `event.deny('reason')` | void | cancel and set the denial message (preferred spelling) |
| `event.hasAdvancement(id)` | boolean | whether the player has completed the advancement (server-side only) |
| `event.getAdvancementProgress(id)` | double | completion fraction 0.0–1.0 (server-side only) |
| `event.getCompletedAdvancementCriteria(id)` | int | how many criteria the player has met (server-side only) |
| `event.getTotalAdvancementCriteria(id)` | int | how many criteria the advancement has (server-side only) |
| `event.completedAdvancementCount` | int | how many advancements with a display the player has completed, recipes excluded (server-side only) |

**Zero-argument getters are exposed as properties:** scripts read `event.cause`, `event.serverSide`, `event.completedAdvancementCount` without parentheses.

## Cancellation

Three different ways to cancel all do the same thing:

```js
RunicSkillsEvents.skillLevelUp(event => {
    // All three are equivalent:
    event.setCanceled(true);           // American spelling
    event.setCancelled(true);          // British spelling
    event.cancel();                    // KubeJS native
    event.cancel('reason here');       // with a message
    event.deny('reason here');         // preferred spelling with a message
})
```

When cancelled:
- The skill level does not rise.
- The player is charged **zero XP**.
- If a message was supplied (via `deny()` or `event.cancel('...')`), it is sent once to the player's chat on the server post only.

## Advancement helpers

The `hasAdvancement`, `getAdvancementProgress`, and criteria helpers query the server's loaded advancement registry and return their safe defaults on the client post (false / 0 / 0.0), since the client's advancement registry is a partial copy. A malformed or unknown advancement id returns the safe value and logs once at DEBUG without crashing — type a typo in a script and the level-up proceeds while the typo goes to `logs/kubejs/server.log`.

Example:

```js
RunicSkillsEvents.skillLevelUp(event => {
    if (!event.serverSide || event.cause !== 'purchase') return;
    
    if (!event.hasAdvancement('minecraft:adventure/kill_a_mob')) {
        event.deny('You must kill a mob first');
    }
})
```

## Example: specific advancement gate

```js
// kubejs/server_scripts/runicskills_gates.js
RunicSkillsEvents.skillLevelUp(event => {
    // Only gate purchases, not admin commands
    if (event.cause !== 'purchase') return;
    
    const skill = event.skill.name;
    const level = event.newLevel;
    
    if (skill === 'strength') {
        if (level >= 5 && !event.hasAdvancement('minecraft:adventure/kill_a_mob')) {
            event.deny('Strength 5 requires: Kill a Mob');
        }
        if (level >= 10 && !event.hasAdvancement('minecraft:adventure/kill_all_mobs')) {
            event.deny('Strength 10 requires: Monsters Hunted');
        }
    }
    
    if (skill === 'fortune') {
        if (level >= 5 && !event.hasAdvancement('minecraft:story/iron_tools')) {
            event.deny('Fortune 5 requires: Getting an Upgrade');
        }
        if (level >= 10 && !event.hasAdvancement('minecraft:story/mine_diamond')) {
            event.deny('Fortune 10 requires: Mine Diamond');
        }
    }
})
```

## Example: advancement-count gate

```js
// Require a certain number of advancements to level up
RunicSkillsEvents.skillLevelUp(event => {
    if (event.cause !== 'purchase') return;
    
    const required = (event.newLevel - 1) * 2;  // level 5 needs 8 advancements
    const completed = event.completedAdvancementCount;
    
    if (completed < required) {
        event.deny(`You need ${required} advancements, you have ${completed}`);
    }
})
```

## Example: partial advancement progress

```js
// Gate on partial progress through an advancement
RunicSkillsEvents.skillLevelUp(event => {
    if (event.cause !== 'purchase') return;
    
    if (event.skill.name === 'endurance' && event.newLevel >= 10) {
        const progress = event.getAdvancementProgress('minecraft:adventure/adventuring_time');
        if (progress < 0.5) {
            event.deny(`Endurance 10 requires 50% of Adventuring Time (you have ${Math.round(progress * 100)}%)`);
        }
    }
})
```

## Admin-command behavior

A filter on `event.cause === 'purchase'` lets operators use `/skills` without triggering progression gates:

```js
RunicSkillsEvents.skillLevelUp(event => {
    if (event.cause !== 'purchase') return;  // Ops bypass via /skills
    
    // Your gate here
    event.deny('gate reason');
})
```

Omitting the filter makes admin commands obey the gate too:

```js
RunicSkillsEvents.skillLevelUp(event => {
    // This gate applies to /skills commands as well
    event.deny('gate reason');
})
```

Be careful not to lock yourself out — make sure you can still run `/skills <yourself> <skill> set 1` to reset.

The `respec` cause is reserved in the enum (nothing constructs it yet), so scripts written to switch on the cause will not break when a respec hook appears.

## Reloading scripts

Apply script changes with `/kubejs reload server_scripts` in-game. Expect the output to report 0 errors; if there are errors, check `logs/kubejs/server.log` for the syntax error or runtime failure.

## ProbeJS

Run `/probejs dump` to generate type stubs for your IDE. The `RunicSkillsEvents.skillLevelUp` event appears in both SERVER and CLIENT context. ProbeJS is not a dependency; it is an optional dev tool.

## Troubleshooting

**"ForgeEvents is not defined"**
The old example used `ForgeEvents.onEvent` through `ForgeEvents` (a KubeJS class for subscribing to Forge events). The Runic Skills progression event is custom, not a Forge event — subscribe with `RunicSkillsEvents.skillLevelUp` in `kubejs/server_scripts/` instead. (The old example referenced a nonexistent inner class of Forge's PlayerEvent and relied on ForgeEvents from server_scripts, which is not the supported API.)

**"ServerEvents.forge is not the Runic API"**
`ServerEvents.forge` is the Forge event bridge (for vanilla Forge events). Runic Skills events go through `RunicSkillsEvents` instead.

**"Works in client_scripts but not server_scripts"**
Your KubeJS version is older than 2001.6.5-build.14, the KubeJS plugin failed to load (check startup log for plugin errors or README.md startup output), or there is a syntax error in your script. Verify the version in `mods list`, check `logs/kubejs/server.log` for messages, and run `/probejs dump` to confirm the event is visible.
