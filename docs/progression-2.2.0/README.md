# 2.2.0 evidence

- `registry-audit.json.gz`: complete runtime registry, recipes, native evidence, rules and warnings.
- `historical-providers.json.gz`: isolated original 2.1.2 provider bytecode results on the same registry/config, not an old-release gameplay pass.
- `comparison.json`: component-wise comparisons and all recipe warnings.
- `dependencies*.json`: exact production mod IDs, versions and artifact hashes.
- `verification.json`: executed checks, artifact hashes, baseline identity and limitations.
- `session-changes.json`: files changed relative to the pre-existing dirty working tree.
- `client-*.json` and PNGs: actual client test receipts and native screenshots.
- `junit-results.xml.gz`: complete JUnit reports.
- `logs/*.gz`: retained build/client/production test logs.

Reproduce the comparison with `python3 tools/audit_progression.py registry-audit.json.gz historical-providers.json.gz --output comparison.json`. These exports contain no real player inventory data.
