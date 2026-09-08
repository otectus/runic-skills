"""Regenerate the source-level 2.1.0 content trace without loading optional mods.

This inventories executable references, not runtime test results. See the generated
document's scope statement before treating a reference as proof of a working mechanic.
"""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/otectus/runicskills'
LANG = json.loads((ROOT / 'src/main/resources/assets/runicskills/lang/en_us.json').read_text(encoding='utf-8'))
TOKEN = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')


def uncomment(text, strings=True):
    return TOKEN.sub(lambda m: m[0] if strings and m[0][0] in '\"\'' else ' ' * len(m[0]), text)


SOURCES = {p: uncomment(p.read_text(encoding='utf-8')) for p in sorted(JAVA.rglob('*.java'))}
RUNTIME = {p: code for p, code in SOURCES.items()
           if p.stem == 'OreDetectorRenderer' or not p.relative_to(JAVA).as_posix().startswith(
               ('client/', 'config/', 'network/packet/client/', 'registry/content/'))}
STRIPPED_RUNTIME = {p: uncomment(code, strings=False) for p, code in RUNTIME.items()}
CONFIG = SOURCES[JAVA / 'handler/HandlerCommonConfig.java']
DEFAULTS = dict(re.findall(r'public\s+\w+(?:\[\])?\s+(\w+)\s*=\s*([^;]+);', CONFIG))


def cell(text):
    return re.sub(r'\s+', ' ', str(text)).replace('|', '\\|').strip()


def link(path, label=None):
    return f'[{label or path.stem}](../{path.relative_to(ROOT).as_posix()})'


def declarations(kind, registry):
    return re.findall(r'public static final RegistryObject<' + kind + r'>\s+(\w+)\s*=\s*([^;]+);',
                      SOURCES[JAVA / f'registry/{registry}.java'])


def configs(body):
    names = list(dict.fromkeys(re.findall(r'HandlerCommonConfig\.HANDLER\.instance\(\)\.(\w+)', body)))
    return ', '.join(f'`{name}={cell(DEFAULTS.get(name, "see config"))}`' for name in names) or 'shared tier/runtime rules'


def references(registry, constant, path_id=None):
    needle = re.compile(r'\b' + registry + r'\.' + constant + r'\b')
    result = []
    for path, body in RUNTIME.items():
        if path.stem in (registry, 'TConstructPowers', 'RunicAttributeModifiers', 'RegistryPassives', 'RunicSkills'):
            continue
        code = STRIPPED_RUNTIME[path]
        if needle.search(code) or (path_id and f'"{path_id}"' in body):
            result.append(link(path))
    return result


def sites(registry, constant, path_id=None):
    return ', '.join(references(registry, constant, path_id)) or 'generic attribute/dispatch route; see shared trace'


def identifier(body, constant):
    found = re.search(r'(?:registerPerk|registerPassive|issPower|crossPower)\("([a-z0-9_]+)"', body)
    return found[1] if found else constant.lower()


def governing(body):
    found = re.search(r'RegistrySkills\.([A-Z_]+)', body)
    return found[1].lower() if found else 'see factory'


def gate(body):
    if '? null' not in body and '?\n' not in body:
        return 'registered; runtime config gate'
    before = body.split('?')[0].strip()
    return f'absent when `{cell(before)}`'


def build():
    perks = declarations('Perk', 'RegistryPerks')
    passives = declarations('Passive', 'RegistryPassives')
    powers = declarations('Power', 'RegistryPowers')
    out = [
        '# 2.1.0 content trace', '',
        'Generated from the current Java registrations, configuration defaults, English descriptions, '
        'and gameplay source references by `tools/content_trace.py`. Regenerate after changing content.', '',
        f'The full optional-mod catalogue has **10 Skills, {len(perks)} Perks, {len(passives)} Passives, '
        f'and {len(powers)} Powers**. Runtime membership depends on installed mods; disabled settings '
        'retain identifiers and saved progression.', '',
        '## Scope and interpretation', '',
        'Every declaration is included below. Runtime-reference links exclude comments, presentation UI, '
        'client snapshot packets, configuration and content-status declarations. Server progression '
        'packet handlers and the client-rendered Ore Detector mechanic are included. A reference '
        'establishes a reachable consumer; '
        'it does not prove that an upstream event fires or that every optional-mod combination behaves '
        'correctly. Formula inspection and regression tests complement this inventory. Optional integrations '
        'without a recorded test profile remain source-reviewed, not certified through live play. '
        'Consult `TCONSTRUCT_TEST_MATRIX.md` and the release verification summary for executed profiles.', '',
        '## Shared registration, requirements, synchronization and saves', '',
        '- **Skills:** `RegistrySkills` registers ten stable IDs. `SkillLevelUpSP` and '
        '`ProgressionService` authorize server XP purchases and fire `SkillLevelUpEvent`; '
        '`SkillCapability.skillLevel` persists them. Item/armor/use/mining/spell gates consume these levels. '
        '`Skill.getRank` divides the configured cap proportionally, including caps below eight.',
        '- **Perks:** `RegistryPerks` retains config factories; `refreshFromConfig` refreshes tunables '
        'without replacing registry IDs. `TogglePerkSP`/`ProgressionService`, `ScaledRequirement`, '
        '`PerkGroupManager` and `SkillCapability.isPerkActive` authorize rank, skill, cost, group and '
        'dormancy requirements. `perkRank` and `perkCooldowns` persist through NBT and clones. '
        '`Perk.isEnabled` has matching local/server disabled semantics. Event handlers and mixins '
        'below own action-specific requirements, effect formulas and timing.',
        '- **Passives:** `RegistryPassives` creates the per-skill requirement array and stable attribute '
        'UUID. `AdjustPassiveSP`/`ProgressionService` authorize changes. `passiveLevel` persists; '
        '`RegistryAttributes.modifierAttributes` reconciles bounded transient attributes and removes '
        'disabled bonuses. Vanilla, Forge or the optional attribute owner consumes the installed attribute; '
        'custom Runic attributes have consumer links below.',
        '- **Apothic reloads:** owned attributes remain registered at neutral defaults. Passive '
        'factories refresh their selected attribute provider, and reconciliation moves each '
        'passive UUID off the inactive provider; `ApothicDelegationGameTest` checks both directions '
        'against the real optional mod.',
        '- **Powers:** `RegistryPowers` derives shared tier gates through `PowerEligibility`. '
        '`PowerEquipSP` validates slots; `PowerDispatch`, per-school dispatchers and `PowerRuntime` '
        'authorize effects. `PowerOverridesManager` applies bounded datapack tunables; '
        '`powerCooldowns`, `powerWindows`, equipped slots and `PowerCooldownDebt` carry runtime/persistent '
        'state. Artifice adds capability/profile gates through `TConstructCompatibilityStatus`.',
        '- **Synchronization:** gameplay/config snapshots (`GameplayConfigCP`, `ConfigSyncCP`), '
        '`SyncSkillCapabilityCP`, Power/perk-group snapshots and reload reconciliation keep client '
        'descriptions, server gates and saved state aligned. Registry handshake synchronization is '
        'disabled because optional membership and configuration must not remap stored IDs.',
        '- **Presentation:** `RunicSkillsScreen`, `PowersScreen`, `PerkTooltip` and `PassiveTooltip` '
        'consume the same descriptions and values. Texture inventories and native-resolution image '
        'validation are independent of this behavior trace.', '',
        '## Behavioral regressions corrected in this pass', '',
        '`GameplayStabilizationGameTest` covers Counter Attack surviving the pre-damage swing event '
        'and consuming only a landed melee hit; forced critical damage; mining bonuses on obsidian and '
        'hoes; post-mitigation Last Stand with its advertised protection window; saved survival/Chaos Roll '
        'cooldowns; Phoenix Rising recovery; piercing spectral-arrow scaling; and gem compression '
        'refund abuse with a positive manufacture case; Blood Fury healing only committed critical '
        'damage; and rescued victims granting no Bloodlust/Chaos Roll kill rewards while completed '
        'kills still do. The optional `SpellPowerGameTest` registers the bounded Mana Shield '
        'absorption regression when its Iron’s Spells perk exists. `ChannelAndSummonGameTest` '
        'covers saved projectile provenance, one-impact channel bonuses, final-damage Siphon '
        'healing capped at the victim’s remaining health, and the saved capped '
        'Lingering Binding damage bank. The optional `SchoolPowerStabilizationGameTest` exercises '
        'Shatter with absorbed/secondary/repeated hits and real fire-field duration across NBT reload. '
        '`SkillLevelUpMathTest` covers short, uneven and extreme configured caps. Test names identify '
        'coverage; the release verification report records which profiles passed.', '',
        'Phoenix Rising retains its ID, requirement and percentage field, but now rescues a fatal hit '
        'at that percentage of maximum health rather than reducing vanilla full respawn health. '
        'It shares the saved 60-second rescue lockout with Undying Will and Mythical Berserker. '
        'Last Stand is a separate saved 60-second cooldown and a two-second protection/damage window. '
        'Chaos Roll uses its existing 20-second interval in the saved perk cooldown map. '
        'No progression identifiers or saved config fields were removed.', '',
        '## Skills', '', '| ID | Description | Consumers |', '| --- | --- | --- |',
    ]
    for constant, body in declarations('Skill', 'RegistrySkills'):
        path_id = constant.lower()
        key = f'skill.runicskills.{path_id}'
        label = LANG.get(key, path_id)
        abbreviation = LANG.get(key + '.abbreviation', '')
        out.append(f'| `{path_id}` | {cell(label)} ({abbreviation}); overview/detail UI shows level, '
                   f'cap and proportional rank; opens the {label} passive/perk rows listed below. '
                   f'| {sites("RegistrySkills", constant)} |')
    out += ['', '## Perks', '', '| ID / skill | Registration / defaults | English mechanic | Runtime references |',
            '| --- | --- | --- | --- |']
    for constant, body in perks:
        path_id = identifier(body, constant)
        out.append(f'| `{path_id}` / {governing(body)} | {gate(body)}; {configs(body)} '
                   f'| {cell(LANG.get(f"perk.runicskills.{path_id}.description", "see registered translation"))} '
                   f'| {sites("RegistryPerks", constant)} |')
    out += ['', '## Passives', '', '| ID / skill | Registration / defaults | English mechanic / attribute consumer |',
            '| --- | --- | --- |']
    for constant, body in passives:
        path_id = identifier(body, constant)
        factory = re.search(r'(\w+PassiveHelper)::(\w+)', body)
        original = body
        if factory:
            helper = SOURCES[JAVA / f'integration/{factory[1]}.java']
            found = re.search(r'public static Passive ' + factory[2] + r'\(\)\s*\{([\s\S]+?)\n    }', helper)
            if found:
                body += found[1]
        attributes = list(dict.fromkeys(re.findall(r'(?:ALObjects\.Attributes|Attributes|ForgeMod|RegistryAttributes|AttributeRegistry)\.(\w+)', body)))
        consumers = references('RegistryPassives', constant)
        custom = list(dict.fromkeys(re.findall(r'RegistryAttributes\.(\w+)', body)))
        effective = {'getEffectiveProjectileDamage': 'PROJECTILE_DAMAGE',
                     'getEffectiveBreakSpeed': 'BREAK_SPEED', 'getEffectiveCritDamage': 'CRITICAL_DAMAGE'}
        for attribute in custom:
            consumers += references('RegistryAttributes', effective.get(attribute, attribute))
        if factory:
            consumers.append(link(JAVA / f'integration/{factory[1]}.java', f'{factory[1]} factory'))
            consumers.append('the installed optional mod consumes its native attribute')
        elif not custom:
            consumers.append('vanilla/Forge consumes its native attribute through the shared attribute route')
        elif any(attribute in effective for attribute in custom):
            consumers.append('Apothic Attributes consumes the substituted native attribute when installed')
        out.append(f'| `{path_id}` / {governing(body)} | {gate(original)}; {configs(body)} '
                   f'| {cell(LANG.get(f"passive.runicskills.{path_id}.description", "see registered translation"))}; '
                   f'`{", ".join(attributes)}`; {", ".join(dict.fromkeys(consumers))} |')
    out += ['', '## Powers', '', '| ID / skill / tier | Registration / base internal cooldown | English mechanic | Runtime references |',
            '| --- | --- | --- | --- |']
    for constant, body in powers:
        path_id = identifier(body, constant)
        tier = re.search(r'PowerTier\.(\w+)', body)
        icd = re.search(r',\s*(\d+)\s*\)', body)
        cooldown = icd[1] + ' ticks' if icd else '`TConstructPowers.Definition`'
        availability = 'Iron\'s Spellbooks required' if body.startswith('issPower') else 'runtime eligibility/profile gates'
        out.append(f'| `{path_id}` / {governing(body)} / {tier[1] if tier else "see definition"} '
                   f'| {availability}; {cooldown} '
                   f'| {cell(LANG.get(f"power.runicskills.{path_id}.description", "see registered translation"))} '
                   f'| {sites("RegistryPowers", constant, path_id)} |')
    destination = ROOT / 'docs/CONTENT_TRACE_2.1.0.md'
    destination.write_text('\n'.join(out) + '\n', encoding='utf-8')
    print(f'{destination.relative_to(ROOT)}: 10 skills, {len(perks)} perks, {len(passives)} passives, {len(powers)} powers')


if __name__ == '__main__':
    build()
