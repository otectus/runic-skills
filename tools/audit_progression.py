#!/usr/bin/env python3
"""Compare actual lock exports, known material chains, and effective recipe prerequisites.

Never infers material order from combat stats. Gold and special materials are side branches.
Warnings describe use gates, not proof that a recipe or external acquisition is impossible.
"""
import argparse
from collections import Counter
import gzip
import json
from pathlib import Path


def read(path):
    return json.loads(gzip.decompress(path.read_bytes()) if path.suffix == '.gz' else path.read_text())


def harder(lower, higher):
    return {skill: [level, higher.get(skill, 1)] for skill, level in lower.items()
            if level > higher.get(skill, 1)}


def audit(current, baseline):
    items = {r['item_id']: r for r in current['items']}
    old = baseline.get('items', {})
    changes, pairs, recipe_warnings = [], [], []
    for item_id, item in items.items():
        before = old.get(item_id, {}).get('requirements', {})
        after = item['effective_requirements']
        if before != after:
            changes.append(dict(item_id=item_id, before=before, after=after,
                                before_source=old.get(item_id, {}).get('source', 'unhandled'),
                                after_source=item['winner']))
        namespace, path = item_id.split(':', 1)
        # Compare exactly the same ID suffix/family. Native vanilla materials or a curated
        # Spartan row establish identity; an arbitrary material-looking name is insufficient.
        for lower, higher in [('wooden', 'stone'), ('stone', 'iron'), ('iron', 'diamond'), ('diamond', 'netherite')]:
            if not path.startswith(lower+'_'):
                continue
            higher_id = namespace+':'+higher+path[len(lower):]
            other = items.get(higher_id)
            if not other or item['family'] != other['family']:
                continue
            native = all('net.minecraft.world.item.Tiers:' in r['native_material'] or
                         'net.minecraft.world.item.ArmorMaterials:' in r['native_material']
                         for r in (item, other))
            curated = namespace.startswith('spartan') and all(r['winner'] == 'spartan' for r in (item, other))
            if not (native or curated):
                continue
            old_lower = old.get(item_id, {}).get('requirements', {})
            old_higher = old.get(higher_id, {}).get('requirements', {})
            pairs.append(dict(lower=item_id, higher=higher_id, evidence='native_material' if native else 'curated_spartan_material',
                              before_inversion=harder(old_lower, old_higher),
                              after_inversion=harder(after, other['effective_requirements']),
                              before_family_drift=bool(old_lower and old_higher and set(old_lower) != set(old_higher)),
                              after_family_drift=bool(after and other['effective_requirements'] and set(after) != set(other['effective_requirements']))))
        for recipe in item['recipes']:
            if recipe['every_alternative_in_an_ingredient_has_a_higher_use_gate']:
                recipe_warnings.append(dict(item_id=item_id, recipe=recipe['id'],
                                            ingredient_alternatives=recipe['ingredient_alternatives']))
    return dict(schema=1, baseline_mode=baseline.get('mode', 'full release runtime'),
                baseline_sha256=baseline.get('artifact_sha256'), baseline_comparison_passed=baseline.get('passed', True),
                registered_item_count=current['registered_item_count'], examined_items=len(items),
                provider_coverage=current['providers'], warning_counts=dict(Counter(w for i in items.values() for w in i['warnings'])),
                changed_items=changes, verified_material_pairs=pairs,
                material_pair_summary=dict(compared=len(pairs), before_inversions=sum(bool(p['before_inversion']) for p in pairs),
                                           after_inversions=sum(bool(p['after_inversion']) for p in pairs),
                                           before_family_drift=sum(p['before_family_drift'] for p in pairs),
                                           after_family_drift=sum(p['after_family_drift'] for p in pairs)),
                recipe_use_gate_warnings=recipe_warnings,
                dormant_or_missing_configured_ids=current['dormant_or_missing_configured_ids'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('current', type=Path)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = audit(read(args.current), read(args.baseline))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(dict(changed_items=len(result['changed_items']), **result['material_pair_summary'],
                          recipe_warnings=len(result['recipe_use_gate_warnings']), warnings=result['warning_counts'])))


if __name__ == '__main__':
    main()
