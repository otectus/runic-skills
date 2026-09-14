#!/usr/bin/env python3
"""Read live Modrinth release metadata for hashes in an instance audit (no jar downloads)."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request


def request(path, payload=None):
    request = urllib.request.Request('https://api.modrinth.com/v2/'+path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={'User-Agent':'RunicSkills/compatibility-audit (local developer tooling)', 'Content-Type':'application/json'})
    with urllib.request.urlopen(request, timeout=35) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    audit = json.loads(args.audit.read_text(encoding='utf-8'))
    rows = [row for row in audit['jars'] if not row['nested']]
    hashes = {hashlib.sha1(Path(row['file']).read_bytes()).hexdigest(): row for row in rows}
    matches = request('version_files', {'hashes':list(hashes), 'algorithm':'sha1'})
    projects = sorted({version['project_id'] for version in matches.values()})
    def check(project):
        query = urllib.parse.urlencode({'loaders':json.dumps(['forge']), 'game_versions':json.dumps(['1.20.1'])})
        try:
            versions = request(f'project/{project}/version?{query}')
            releases = [v for v in versions if v['version_type'] == 'release']
            latest = max(releases, key=lambda v:v['date_published']) if releases else None
            newest = max(versions, key=lambda v:v['date_published']) if versions else None
            return project, dict(latest=latest, newest=newest, error=None)
        except Exception as exc:
            return project, dict(latest=None, newest=None, error=str(exc))
    with ThreadPoolExecutor(max_workers=4) as executor:
        checked = dict(executor.map(check, projects))
    results = []
    for checksum, row in hashes.items():
        current = matches.get(checksum)
        latest = checked[current['project_id']]['latest'] if current else None
        result = dict(file=Path(row['file']).name, mods=row['mods'], sha1=checksum,
            status='UNMATCHED', source=None, installed_modrinth_id=current['id'] if current else None)
        if current:
            result['installed_release_type'] = current['version_type']
            result['source'] = 'https://modrinth.com/mod/'+current['project_id']+'/versions?g=1.20.1&l=forge'
            result['error'] = checked[current['project_id']]['error']
            result['status'] = 'NO_RELEASE_METADATA' if latest is None else ('CURRENT_RELEASE' if current['id'] == latest['id'] else 'REVIEW_RELEASE')
            newest = checked[current['project_id']]['newest']
            if newest:
                result['latest_available'] = dict(id=newest['id'], version=newest['version_number'], type=newest['version_type'], date=newest['date_published'])
                if latest is None:
                    result['status'] = 'CURRENT_PRERELEASE' if current['id'] == newest['id'] else 'REVIEW_PRERELEASE'
        if latest:
            result['latest'] = dict(id=latest['id'], version=latest['version_number'], date=latest['date_published'], files=[dict(filename=f['filename'], sha1=f['hashes']['sha1']) for f in latest['files']])
        results.append(result)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(dict(checked_at=datetime.now(timezone.utc).isoformat(), scope='Live Modrinth metadata for Forge 1.20.1 releases; unmatched CurseForge-only artifacts require publisher-page review.', results=results), indent=2)+'\n', encoding='utf-8')
    for status in sorted({r['status'] for r in results}):
        print(status, sum(r['status']==status for r in results))
    for row in results:
        if row['status']=='REVIEW_RELEASE': print(row['file'], '->', row['latest']['version'])


if __name__ == '__main__':
    main()
