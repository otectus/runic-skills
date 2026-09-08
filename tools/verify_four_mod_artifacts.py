#!/usr/bin/env python3
"""Check pinned four-mod hashes and production member descriptors; this does not certify gameplay."""
import argparse
import hashlib
import json
from pathlib import Path
import tomllib
import zipfile

from verify_against_pack import parse_class

ROOT = Path(__file__).resolve().parents[1]

def inspect(directory, manifest):
    installed = {}
    for path in sorted(directory.glob('*.jar')):
        with zipfile.ZipFile(path) as jar:
            if 'META-INF/mods.toml' not in jar.namelist():
                continue
            metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode('utf-8'))
            for mod in metadata.get('mods', []):
                key = mod['modId']
                if key in installed:
                    raise ValueError(f'Duplicate installed mod ID: {key}')
                installed[key] = path
    results = []
    for profile in manifest['profiles']:
        path = installed.get(profile['mod_id'])
        if path is None:
            results.append(dict(profile=profile['profile'], status='ABSENT', hooks=[]))
            continue
        checksum = hashlib.sha256(path.read_bytes()).hexdigest()
        result = dict(profile=profile['profile'], sha256=checksum, status='PASS' if checksum == profile['sha256'] else 'HASH_MISMATCH', hooks=[])
        with zipfile.ZipFile(path) as jar:
            for hook in profile.get('hooks', []) + profile.get('read_only_members', []):
                owner = hook['class'].replace('.', '/')
                try:
                    members = parse_class(jar.read(owner+'.class'))[3]
                    valid = (hook['member'], hook['descriptor']) in members
                except KeyError:
                    valid = False
                result['hooks'].append(dict(member=hook['member'], status='PASS' if valid else 'MISSING'))
                if not valid:
                    result['status'] = 'HOOK_MISSING'
        results.append(result)
    return results

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mods', type=Path, required=True)
    parser.add_argument('--require-all', action='store_true')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    manifest = json.loads((ROOT/'docs/integrations/compat-manifest.json').read_text(encoding='utf-8'))
    results = inspect(args.mods, manifest)
    for result in results:
        print(f"{result['status']} {result['profile']}: {len(result['hooks'])} native descriptor(s)")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(dict(scope='Static hashes and member signatures only; no runtime certification.', results=results), indent=2)+'\n', encoding='utf-8')
    return int(any(r['status'] != 'PASS' and (args.require_all or r['status'] != 'ABSENT') for r in results))

if __name__ == '__main__':
    raise SystemExit(main())
