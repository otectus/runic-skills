#!/usr/bin/env python3
"""Launch a copied test world with installed Minecraft libraries and a synthetic offline identity.

No launcher account files or user world saves are read. The opt-in validation mod quits
after 400 client world ticks; the timeout terminates only the child created here.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import zipfile


def allowed(rules):
    result = not rules
    for rule in rules or []:
        os_rule = rule.get('os', {})
        if os_rule.get('name', 'windows') != 'windows':
            continue
        if os_rule.get('arch', 'amd64') not in ('amd64', 'x86_64'):
            continue
        if rule.get('features'):
            continue
        result = rule['action'] == 'allow'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--install', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--world', default='validation-world')
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--tests', type=int, default=0)
    parser.add_argument('--latest-pack', action='store_true')
    parser.add_argument('--tabs', action='store_true', help='Run the opt-in inventory-tab screenshot/click checks.')
    parser.add_argument('--tab-profile', default='tabs', help='Simple directory name for tab evidence.')
    parser.add_argument('--width', type=int, help='Client window width; tab checks default to 1920.')
    parser.add_argument('--height', type=int, help='Client window height; tab checks default to 1080.')
    parser.add_argument('--heap-mib', type=int, default=8192, help='Maximum Java heap size in MiB.')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.tab_profile):
        parser.error('--tab-profile must contain only letters, digits, underscores, or hyphens.')
    if args.heap_mib < 1024:
        parser.error('--heap-mib must be at least 1024.')
    root, install = args.directory.resolve(), args.install.resolve()
    if not (root/'saves'/args.world/'level.dat').is_file():
        raise SystemExit('A copied validation world is required.')
    base = json.loads((install/'versions/1.20.1/1.20.1.json').read_text())
    forge = json.loads((install/'versions/forge-47.4.23/forge-47.4.23.json').read_text())
    libraries = {}
    for library in base['libraries'] + forge['libraries']:
        if allowed(library.get('rules')):
            # Keep native classifiers while letting Forge override a base library version.
            parts = library['name'].split(':')
            libraries[tuple(parts[:2] + parts[3:])] = library
    classpath = []
    natives = root/'natives'
    natives.mkdir(exist_ok=True)
    for library in libraries.values():
        artifact = library.get('downloads', {}).get('artifact')
        if not artifact:
            continue
        path = install/'libraries'/artifact['path']
        if not path.is_file():
            raise SystemExit(f'Missing installed library: {path}')
        classpath.append(path.as_posix())
        if 'natives-windows' in path.name:
            with zipfile.ZipFile(path) as jar:
                for member in jar.namelist():
                    if member.endswith('.dll'):
                        (natives/Path(member).name).write_bytes(jar.read(member))
    classpath.append((install/'versions/1.20.1/1.20.1.jar').as_posix())
    variables = dict(natives_directory=natives.as_posix(), launcher_name='RunicLocalValidation',
        launcher_version='1', classpath=';'.join(classpath), version_name='forge-47.4.23',
        library_directory=(install/'libraries').as_posix(), classpath_separator=';',
        auth_player_name='RunicValidation', game_directory=root.as_posix(),
        assets_root=(install/'assets').as_posix(), assets_index_name=base['assetIndex']['id'],
        auth_uuid=uuid.uuid3(uuid.NAMESPACE_DNS, 'RunicValidation').hex, auth_access_token='0',
        clientid='0', auth_xuid='0', user_type='legacy', version_type='release')

    def expand(arguments):
        output = []
        for argument in arguments:
            if isinstance(argument, dict):
                if not allowed(argument.get('rules')):
                    continue
                argument = argument['value']
            for value in argument if isinstance(argument, list) else [argument]:
                output.append(re.sub(r'\$\{([^}]+)\}', lambda m: variables[m[1]], value))
        return output

    command = ['-Xms1G', f'-Xmx{args.heap_mib}M', '-Drunicskills.clientValidation=true']
    if args.tabs:
        command += ['-Drunicskills.tabValidation=true',
                    '-Drunicskills.tabValidationProfile='+args.tab_profile]
    if args.tests:
        command.append('-Drunicskills.productionValidation=true')
    if args.latest_pack:
        command.append('-Drunicskills.latestPackValidation=true')
    command += expand(base['arguments']['jvm']) + expand(forge['arguments']['jvm'])
    # The stock launcher aliases the vanilla jar to the Forge version name. This
    # standalone runner keeps its original name, which must also be ignored by FML.
    command = [a+',1.20.1.jar' if a.startswith('-DignoreList=') else a for a in command]
    command += [forge['mainClass']]
    command += expand(base['arguments']['game']) + expand(forge['arguments']['game'])
    command += ['--quickPlaySingleplayer', args.world,
                '--width', str(args.width or (1920 if args.tabs else 960)),
                '--height', str(args.height or (1080 if args.tabs else 540))]
    argument_file = root/'client-validation.args'
    argument_file.write_text('\n'.join('"' + a.replace('\\', '\\\\').replace('"', '\\"') + '"' for a in command), encoding='utf-8')
    log_path = root/'client-validation.log'
    started = time.monotonic()
    with log_path.open('w', encoding='utf-8') as log:
        process = subprocess.Popen([str(args.java), '@'+str(argument_file)], cwd=root,
            stdout=log, stderr=subprocess.STDOUT, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        print(f'Client validation child PID {process.pid}; log {log_path}', flush=True)
        last_report = started
        try:
            while process.poll() is None:
                time.sleep(1)
                now = time.monotonic()
                if now-started > args.timeout:
                    process.terminate()
                    break
                if now-last_report >= 30:
                    print(f'Client validation elapsed {int(now-started)}s', flush=True)
                    last_report = now
            process.wait(timeout=30)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
    log_text = log_path.read_text(encoding='utf-8', errors='replace')
    result = dict(passed='RUNIC_CLIENT_VALIDATION PASS ' in log_text,
        failed='RUNIC_CLIENT_VALIDATION FAIL ' in log_text, exit_code=process.returncode,
        clean_save='All dimensions are saved' in log_text,
        native_passed=len(re.findall(r'FOUR_MOD_PRODUCTION PASS ', log_text)),
        native_failed=len(re.findall(r'FOUR_MOD_PRODUCTION FAIL ', log_text)),
        expected_native_tests=args.tests,
        elapsed_seconds=round(time.monotonic()-started), log=str(log_path))
    if args.tabs:
        tab_path = root/'tab-validation'/args.tab_profile/'result.json'
        tab_result = json.loads(tab_path.read_text()) if tab_path.is_file() else {}
        result.update(tab_passed=tab_result.get('passed', False),
                      tab_captures=len(tab_result.get('captures', [])),
                      tab_failures=tab_result.get('failures', ['No tab evidence was produced.']),
                      tab_evidence=str(tab_path))
    (root/'client-validation.result.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result), flush=True)
    return int(not result['passed'] or result['failed'] or not result['clean_save'] or process.returncode != 0
               or result['native_failed'] or result['native_passed'] != args.tests
               or (args.tabs and not result['tab_passed']))


if __name__ == '__main__':
    raise SystemExit(main())
