#!/usr/bin/env python3
"""Read a Forge instance without modifying it; inventory jars and audit Runic JVM links.

Static linkage is evidence of API shape, not certification of gameplay or mixin call sites.
CurseForge's cached latestFile is recorded as cached evidence, never a live update claim.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import tomllib
import zipfile

from verify_against_pack import parse_class


def constant_pool(data):
    pos, index = 10, 1
    count = struct.unpack_from('>H', data, 8)[0]
    pool = {}
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, pos)[0]
            pool[index] = (tag, data[pos+2:pos+2+size].decode('utf-8', 'replace'))
            pos += size + 2
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, struct.unpack_from('>H', data, pos)[0])
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, *struct.unpack_from('>HH', data, pos))
            pos += 4
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            index += 1
        elif tag == 15:
            pos += 3
        else:
            raise ValueError(f'Invalid constant pool tag {tag}')
        index += 1
    refs = []
    for entry in pool.values():
        if entry[0] in (9, 10, 11):
            owner = pool[pool[entry[1]][1]][1]
            name_type = pool[entry[2]]
            refs.append((owner, pool[name_type[1]][1], pool[name_type[2]][1]))
    return refs


class Inventory:
    def __init__(self):
        self.jars = []
        self.classes = {}
        self.parsed = {}

    def add(self, path, data=None, nested=False):
        raw = path.read_bytes() if data is None else data
        jar = zipfile.ZipFile(io.BytesIO(raw))
        names = set(jar.namelist())
        metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode('utf-8-sig')) if 'META-INF/mods.toml' in names else {}
        manifest = jar.read('META-INF/MANIFEST.MF').decode('utf-8', 'replace').replace('\r\n ', '') if 'META-INF/MANIFEST.MF' in names else ''
        attrs = dict(line.split(': ', 1) for line in manifest.splitlines() if ': ' in line)
        mods = []
        for mod in metadata.get('mods', []):
            version = str(mod.get('version', 'unknown')).replace('${file.jarVersion}', attrs.get('Implementation-Version', 'unknown'))
            mods.append(dict(id=mod['modId'], version=version, name=mod.get('displayName', mod['modId'])))
        row = dict(file=str(path), sha256=hashlib.sha256(raw).hexdigest(), nested=nested, mods=mods, dependencies=metadata.get('dependencies', {}))
        self.jars.append(row)
        for name in names:
            if name.endswith('.class'):
                self.classes.setdefault(name[:-6], (jar, str(path)))
        if 'META-INF/jarjar/metadata.json' in names:
            nested_meta = json.loads(jar.read('META-INF/jarjar/metadata.json'))
            for entry in nested_meta.get('jars', []):
                child = entry['path']
                self.add(Path(str(path) + '!/' + child), jar.read(child), True)
        return row

    def load(self, name):
        if name not in self.parsed:
            source = self.classes.get(name)
            self.parsed[name] = parse_class(source[0].read(name+'.class')) if source else None
        return self.parsed[name]

    def resolves(self, owner, name, descriptor, seen=None):
        seen = set() if seen is None else seen
        if owner in seen:
            return False
        seen.add(owner)
        parsed = self.load(owner)
        if parsed is None:
            return None  # Unknown parent; do not report an unproven missing inherited member.
        if (name, descriptor) in parsed[3]:
            return True
        if name == '<init>':
            return False
        unknown = False
        for parent in [parsed[1], *parsed[2]]:
            if not parent:
                continue
            result = self.resolves(parent, name, descriptor, seen)
            if result is True:
                return True
            unknown |= result is None and parent != 'java/lang/Object'
        return None if unknown else False


def audit(instance, runic_jars):
    inventory = Inventory()
    metadata_path = instance/'minecraftinstance.json'
    metadata = json.loads(metadata_path.read_text(encoding='utf-8-sig')) if metadata_path.exists() else {}
    addons = {a.get('fileNameOnDisk', '').lower(): a for a in metadata.get('installedAddons', [])}
    for addon in metadata.get('installedAddons', []):
        addons.setdefault(addon.get('installedFile', {}).get('fileName', '').lower(), addon)
    for path in sorted((instance/'mods').glob('*.jar')):
        row = inventory.add(path)
        addon = addons.get(path.name.lower())
        if addon:
            row['curseforge'] = dict(project_id=addon.get('addonID'), url=addon.get('webSiteURL'), installed_file_id=addon.get('installedFile', {}).get('id'), cached_latest_file=addon.get('latestFile', {}).get('fileName'), cached_latest_file_id=addon.get('latestFile', {}).get('id'))
    mod_files = {}
    for row in inventory.jars:
        if not row['nested']:
            for mod in row['mods']:
                mod_files.setdefault(mod['id'], []).append(row['file'])
    duplicates = {key: value for key, value in mod_files.items() if len(value) > 1}
    known_mods = {m['id'] for row in inventory.jars for m in row['mods']} | {'minecraft', 'forge'}
    missing_dependencies = []
    for row in inventory.jars:
        for owner, deps in row['dependencies'].items():
            for dep in deps:
                if dep.get('mandatory') and dep['modId'] not in known_mods:
                    missing_dependencies.append(dict(owner=owner, file=row['file'], dependency=dep))
    checked, missing, unresolved, unknown_inheritance = set(), {}, {}, {}
    for path in runic_jars:
        with zipfile.ZipFile(path) as jar:
            for name in jar.namelist():
                if not name.endswith('.class') or not name.startswith('com/otectus/'):
                    continue
                for owner, member, descriptor in constant_pool(jar.read(name)):
                    if owner.startswith(('com/otectus/', 'java/', 'javax/', 'net/minecraft/', 'net/minecraftforge/', 'org/spongepowered/', 'org/objectweb/', 'org/slf4j/', 'org/apache/', 'org/jetbrains/', 'org/lwjgl/', 'com/google/', 'com/mojang/', 'io/netty/', 'it/unimi/', 'com/llamalad7/', 'cpw/', '[')):
                        continue
                    key = (owner, member, descriptor)
                    if owner not in inventory.classes:
                        unresolved.setdefault(key, set()).add(name)
                        continue
                    checked.add(key)
                    result = inventory.resolves(*key)
                    if result is False:
                        missing.setdefault(key, set()).add(name)
                    elif result is None:
                        unknown_inheritance.setdefault(key, set()).add(name)
    def references(items):
        return [dict(owner=k[0], member=k[1], descriptor=k[2], callers=sorted(v)) for k, v in sorted(items.items())]
    return dict(scope='Forge 1.20.1 installed artifacts; static linkage only. Nested jars are inventoried, not Forge-version-selected. Dependency ranges require Forge runtime validation. Cached latest files are not live verification.', game_version=metadata.get('gameVersion'), memory_mib=metadata.get('allocatedMemory'), memory_override=metadata.get('isMemoryOverride'), jars=inventory.jars, duplicates=duplicates, missing_dependencies=missing_dependencies, checked_references=len(checked), missing_members=references(missing), absent_owner_references=references(unresolved), unknown_inherited_references=references(unknown_inheritance))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance', type=Path, required=True)
    parser.add_argument('--jar', type=Path, action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.instance, args.jar)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('jars', 'absent_owner_references')}, indent=2))
    print(f"Inventoried {len(result['jars'])} jars including nested libraries; {len(result['absent_owner_references'])} references have absent owners (includes optional mods).")
    return int(bool(result['duplicates'] or result['missing_dependencies'] or result['missing_members']))


if __name__ == '__main__':
    raise SystemExit(main())
