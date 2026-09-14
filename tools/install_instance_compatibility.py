#!/usr/bin/env python3
"""Install verified artifacts with original-file hashes and reversible backups.

Close Minecraft first. Exit CurseForge before requesting a persistent file-based memory
override, or set memory in its profile UI: a running launcher can restore its cached setting.
"""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance', type=Path, required=True)
    parser.add_argument('--original-audit', type=Path, required=True)
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--companion', type=Path, required=True)
    parser.add_argument('--bridge', type=Path, required=True)
    parser.add_argument('--validation', type=Path, required=True)
    args = parser.parse_args()
    result = json.loads(args.validation.read_text())
    if not (result['world_ready'] and result['passed'] == 4 and result['failed'] == 0 and result['clean_save'] and result['exit_code'] == 0):
        raise SystemExit('The final complete-pack validation must pass before installation.')
    instance = args.instance.resolve(strict=True)
    mods = (instance/'mods').resolve(strict=True)
    def inside(path):
        resolved = path.resolve()
        if not resolved.is_relative_to(instance):
            raise ValueError(f'Installation path escapes the named instance: {resolved}')
        return resolved
    inside(mods)
    old_rows = {Path(row['file']).name:row for row in json.loads(args.original_audit.read_text())['jars'] if not row['nested']}
    sources = [(args.core.resolve(strict=True), args.core.name),
               (args.companion.resolve(strict=True), args.companion.name),
               (args.bridge.resolve(strict=True), 'bielgg_spells-1.4-hotfix.jar')]
    duplicate = inside(mods/'geckolib-forge-1.20.1-4.8.4.jar')
    originals = [inside(mods/name) for _,name in sources[:2]] + [duplicate]
    for path in originals:
        expected = old_rows[path.name]['sha256']
        if digest(path) != expected:
            raise SystemExit(f'{path.name} changed since the audit; refusing to overwrite it.')
    if (mods/sources[-1][1]).exists():
        raise SystemExit('Bridge already exists; inspect it instead of overwriting.')
    # Verify the exact artifacts used in the successful validation run.
    validated_mods = Path(result['log']).parent/'mods'
    for source,name in sources:
        if digest(source) != digest(validated_mods/name):
            raise SystemExit(f'{name} does not match the validated artifact.')
    metadata_path = inside(instance/'minecraftinstance.json')
    metadata_bytes = metadata_path.read_bytes()
    metadata = json.loads(metadata_bytes.decode('utf-8-sig'))
    stamp = datetime.now().strftime('%Y-%m-%d_%H-%M-%S')
    backup = inside(instance/'runicskills-backups'/stamp)
    backup.mkdir(parents=True, exist_ok=False)
    (backup/'minecraftinstance.json').write_bytes(metadata_bytes)
    for path in originals:
        shutil.copy2(path, inside(backup/path.name))
    receipt = dict(backup=str(backup), before_memory_mib=metadata.get('allocatedMemory'),
                   before_memory_override=metadata.get('isMemoryOverride'), requested_memory_mib=8192,
                   memory_override_requires_launcher_verification=True,
                   installed=[dict(file=name,sha256=digest(source)) for source,name in sources],
                   quarantined=dict(file=duplicate.name,sha256=digest(duplicate)),
                   original_worlds_modified=False)
    # Every destination is resolved and checked above/below; no recursive removal or move.
    for source,name in sources:
        shutil.copy2(source, inside(mods/name))
    quarantine = inside(backup/'quarantined')
    quarantine.mkdir()
    duplicate.rename(inside(quarantine/duplicate.name))
    metadata['allocatedMemory'] = 8192
    metadata['isMemoryOverride'] = True
    staged_metadata = inside(instance/'minecraftinstance.runicskills-staged.json')
    staged_metadata.write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    staged_metadata.replace(metadata_path)
    for source,name in sources:
        if digest(mods/name) != digest(source):
            raise RuntimeError(f'Installed artifact verification failed: {name}; backup: {backup}')
    (backup/'installation-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(receipt,indent=2))


if __name__ == '__main__':
    main()
