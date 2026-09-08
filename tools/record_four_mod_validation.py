#!/usr/bin/env python3
"""Record local four-mod validation, requiring production logs tied to the current release jar."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding='utf-8', errors='replace')


def digest(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def write(path, data):
    (ROOT / path).write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')


def main():
    build = read('build/four-mod-build.log')
    assert 'BUILD SUCCESSFUL' in build and '> Task :productionValidationJar' not in build
    gametest = read('build/four-mod-gametest.log')
    required = int(re.search(r'All (\d+) required tests passed', gametest)[1])
    assert 'BUILD SUCCESSFUL' in gametest
    suites = [ET.parse(p).getroot() for p in (ROOT / 'build/test-results/test').glob('TEST-*.xml')]
    totals = {key: sum(int(s.get(key, 0)) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert totals['tests'] > 0 and totals['failures'] == totals['errors'] == 0
    artifacts = []
    for name in ('runicskills-2.1.1.jar', 'runicskills-2.1.1-slim.jar'):
        path = 'build/libs/' + name
        with zipfile.ZipFile(ROOT / path) as jar:
            names = jar.namelist()
            assert not any('/gametest/' in n or '/validation/' in n or '/tomcompat/' in n for n in names)
            assert not any(n.startswith(('com/li64/tide/', 'net/sweenus/simplyswords/', 'net/rosemarythyme/simplymore/', 'com/gametechbc/traveloptics/')) for n in names)
            for power in ('tide_stillwater_oath', 'tide_unbroken_thread', 'tide_keeper_of_the_banks'):
                assert f'assets/runicskills/textures/power/{power}.png' in names
        artifacts.append(dict(path=path, sha256=digest(path), bytes=(ROOT / path).stat().st_size))
    release_hash = artifacts[0]['sha256']
    expected = sum(read(f'src/gametest/java/com/otectus/runicskills/gametest/{name}.java').count('@GameTest(')
                   for name in ('FourModIntegrationGameTest', 'TideCatchGameTest', 'GuardGameTest', 'SwordsInspectionGameTest', 'SwordsWearGameTest', 'TomAquaGameTest', 'WeaponCombatGameTest', 'TomCastGameTest'))
    companion_path = 'build/compat-libs/runicskills-tom-compat-2.1.1.jar'
    with zipfile.ZipFile(ROOT / companion_path) as jar:
        names = jar.namelist()
        assert 'com/otectus/runicskills/tomcompat/TomCompatMod.class' in names
        assert not any(n.endswith('refmap.json') or n.endswith('mixins.json') for n in names)
        assert 'MixinConfigs' not in jar.read('META-INF/MANIFEST.MF').decode('utf-8')
        assert not any(n.endswith('.class') and not n.startswith('com/otectus/runicskills/tomcompat/') for n in names)
        metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode('utf-8'))
        assert metadata['mods'][0]['version'] == '2.1.1'
        assert {d['modId'] for d in metadata['dependencies']['runicskills_tom_compat'] if d['mandatory']} == {'runicskills', 'traveloptics', 'irons_spellbooks'}
    companion = dict(path=companion_path, sha256=digest(companion_path), bytes=(ROOT / companion_path).stat().st_size)
    coverage = json.loads(read('docs/integrations/content-native-tests.json'))['entries']
    assert len(coverage) == 56
    production = {}
    for profile in ('tide', 'absent', 'swords_more_tide', 'all_four_no_companion', 'all_four'):
        path = f'build/reports/four-mod-production-{profile}.log'
        log = read(path)
        assert f'FOUR_MOD_PRODUCTION artifact {release_hash}' in log, f'{profile}: stale artifact evidence'
        passed = re.findall(r'FOUR_MOD_PRODUCTION PASS (\w+)', log)
        skipped = re.findall(r'FOUR_MOD_TEST SKIP (\w+)', log)
        assert len(passed) == expected and 'FOUR_MOD_PRODUCTION FAIL' not in log
        assert {'guardnativedamageordering', 'guardcancellationnesteddamageandcallbackgrants',
                'guardshieldlifecycleandwirebounds'}.issubset(passed)
        missing_prefixes = {'tom_'} if profile != 'all_four' else set()
        if profile in ('tide', 'absent'): missing_prefixes.update({'ss_', 'sm_'})
        if profile == 'absent': missing_prefixes.add('tide_')
        expected_skips = {test for entry, tests in coverage.items()
                          if any(entry.split(':')[1].startswith(prefix) for prefix in missing_prefixes)
                          for test in tests}
        assert set(skipped) == expected_skips, (profile, set(skipped) ^ expected_skips)
        if profile != 'absent':
            assert 'native preparation validated for 14 rods' in log
            assert 'native catch bridge: 17 scenarios passed' in log
            assert 'FOUR_MOD_TEST Baitkeeper: 11 native scenarios passed' in log
            assert 'FOUR_MOD_TEST Many Waters:' in log
            assert 'FOUR_MOD_TEST Keeper of the Banks:' in log
            assert 'FOUR_MOD_TEST Stillwater Oath:' in log and 'FOUR_MOD_TEST Unbroken Thread:' in log
        if profile in ('swords_more_tide', 'all_four', 'all_four_no_companion'):
            assert 'Resonant Reading: 133 native weapons preserve pristine and decorated NBT' in log
            assert 'FOUR_MOD_TEST Patient Temper: native melee, two-point mining' in log
        if profile == 'all_four':
            assert f"FOUR_MOD_TOM_COMPANION artifact {companion['sha256']}" in log
            assert 'FOUR_MOD_TEST Aqua Attunement:' in log
        else:
            assert 'FOUR_MOD_TOM_COMPANION artifact' not in log
        if profile in ('all_four', 'all_four_no_companion'):
            for module in json.loads(read('docs/integrations/compat-manifest.json'))['profiles']:
                assert f"FOUR_MOD_DEPENDENCY {module['mod_id']} {module['mods'][0]['version']} {module['sha256']}" in log
        production[profile] = dict(passed=passed, explicitly_skipped_native_tests=skipped, log_sha256=digest(path))
    static = json.loads(read('build/reports/four-mod-artifact-verification.json'))
    assert len(static['results']) == 4 and all(row['status'] == 'PASS' for row in static['results'])
    reloads = json.loads(read('build/reports/four-mod-rule-reload.json'))
    assert set(reloads) == {'valid', 'malformed', 'missing_item', 'missing_tag', 'duplicate', 'restored'}
    original = reloads['valid']['rule_revision']
    for state in ('malformed', 'missing_item', 'missing_tag', 'duplicate'):
        assert reloads[state]['rule_revision'] == original and reloads[state]['active_rule_count'] == 1
        assert reloads[state]['last_rule_reload_failure']
    assert reloads['restored']['rule_revision'] > original and not reloads['restored']['last_rule_reload_failure']
    for tests in coverage.values():
        assert all(test.lower() in production['all_four']['passed'] for test in tests)
    ledger = json.loads(read('docs/integrations/content-ledger.json'))
    perks = read('src/main/java/com/otectus/runicskills/registry/RegistryPerks.java')
    powers = read('src/main/java/com/otectus/runicskills/registry/RegistryPowers.java')
    icons = json.loads(read('tools/icongen/catalogue.json'))
    english = json.loads(read('src/main/resources/assets/runicskills/lang/en_us.json'))
    assert len(ledger['entries']) == 56 and set(coverage) == {e['id'] for e in ledger['entries']}
    perk_count = power_count = 0
    for entry in ledger['entries']:
        name = entry['id'].split(':')[1]
        is_power = '/' in entry['progression']
        source = powers if is_power else perks
        registration = 'crossPower' if is_power else 'registerPerk'
        kind = 'power' if is_power else 'perk'
        assert f'{registration}("{name}"' in source, f'{name}: registration missing'
        assert any(icon['id'] == name and icon['kind'] == kind for icon in icons), f'{name}: icon missing'
        assert english.get(f'{kind}.runicskills.{name}') and english.get(f'{kind}.runicskills.{name}.description'), f'{name}: localization missing'
        perk_count += not is_power
        power_count += is_power
        entry.update(implementation='implemented_native_server_validated', player_registration=True,
                     native_tests=coverage[entry['id']],
                     validation='Representative native positive and rejection/lifecycle checks in the pinned all-four server profile; see content-native-tests.json and validation-2.1.1.json. Client and exhaustive per-item/provider matrix not run.')
    assert (perk_count, power_count) == (32, 24)
    report = dict(date='2026-09-08', version='2.1.1', protocol=14,
                  completion='All 32 perks and 24 Powers implemented and registered; representative native server behavior verified for every entry. Full specification release certification remains separate.',
                  implemented=dict(perks=perk_count, powers=power_count, total=56),
                  build='PASS; normal release build excludes validation mod', unit_tests=totals,
                  userdev_gametests=dict(required_passed=required, profile='four target integrations absent',
                      native_tests='Native tests explicitly skip missing integrations; skipped success is not native behavior evidence.'),
                  production_profiles=production,
                  entry_coverage='content-native-tests.json',
                  production_environment=dict(java='17.0.20', forge='47.4.23', minecraft='1.20.1', dependencies='validation-dependencies.json'),
                  production_rule_reload=dict(result='Prior foundation validation retained', cases=list(reloads), log_sha256=digest('build/reports/four-mod-rule-reload.json')),
                  static_artifacts='4 pinned hashes and all listed native member descriptors passed; runtime transformed hook verification supplements the static inventory',
                  icons=f'{len(icons)} unique native icons passed generator verification',
                  native_swords='PASS: charged primary damage, Draw, ordinary wear, 133-weapon read-only inspection, native gem effect/manual/summon success and rejection, paid/unlocked ability completion, owner return, paid manual anvil extraction and cooldown debt.',
                  native_more='PASS: mounted forward travel, primary damage/counter, paid repair, aimed legal reach, real native shield disable, allied recipient limit and Mimicry form identity through native combat transitions.',
                  native_tide='PASS: preparation, catch/wear/bait conservation, habitat/species sequences, journal knowledge/milestones/favor, native minigame window, lava/void charges and shared caps/lifecycle debt.',
                  native_tom='PASS with companion: Aqua attribute, actual paid casts, school rotations, owned summons, functional talent slot, relic followup, Aqua projectile budget, canceled/successful counterspell and authenticated paid Mechanized armor output.',
                  supported_boundaries=[
                      'Legal reach uses Forge entity reach and aimed hitbox intersection; unavailable with Better Combat until its server reach adapter is verified.',
                      'Relic Care supports native paid manual anvil extraction; other workshop routes stay unchanged.',
                      'SS ability completion uses the pinned successful activation API; payment requires actual native mana debit. Unverified release-only completion paths do not grant rewards.',
                      'Mimicry continuity requires a native replacement after damage observed in its admitted manual ability timeline; arbitrary stack copies and unverified delayed transitions do not advance it.',
                      'Pressure Reader verifies real interruption of a casting T.O. IMagicEntity; an attempted counterspell, idle target, or unsupported dispel path does not count.',
                      'Artificer receipts support authenticated Mechanized Exoskeleton activations with actual Plasma Fuel debit and committed thrust/projectile output. Other equipment or passive drains do not earn receipts.',
                      'Confluence offensive Aqua classification explicitly covers Hydroshot, Aqua Missiles and Tsunami; utility casts preserve its charge.',
                      'Tide external minigame providers and converted fish delivery require separate verified adapters.'
                  ],
                  not_run=['client rendering/prediction and remote multiplayer',
                           'exhaustive 56-entry acceptance matrix across every native item/spell/provider and balance profiling',
                           'automatic equipment/native ability gate enforcement and client rule synchronization'],
                  warnings=['Optional Tide and pre-existing Botania owners are absent from some compile-classpath remapping checks; pinned signatures and production hooks are checked separately'],
                  companion_artifact=companion, artifacts=artifacts)
    write('docs/integrations/validation-2.1.1.json', report)
    write('docs/integrations/content-ledger.json', ledger)
    manifest = json.loads(read('docs/integrations/compat-manifest.json'))
    for profile in manifest['profiles']:
        prefix = {'simplyswords':'ss_', 'simplymore':'sm_', 'traveloptics':'tom_', 'tide':'tide_'}[profile['mod_id']]
        profile['native_tests'] = sorted({test for entry, tests in coverage.items() if entry.split(':')[1].startswith(prefix) for test in tests})
        profile['production_server'] = f'PASS: {expected} targeted checks in pinned all-four profile; all 14 entries have representative native evidence. See report for supported paths and untested release matrix.'
    manifest['validation_report'] = 'validation-2.1.1.json'
    write('docs/integrations/compat-manifest.json', manifest)
    print(json.dumps(dict(unit_tests=totals, gametests=required, production_tests=expected, artifacts=artifacts, companion=companion), indent=2))


if __name__ == '__main__':
    main()
