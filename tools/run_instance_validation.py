#!/usr/bin/env python3
"""Boot a staged Forge server, capture evidence, and shut down only the child we started."""
import argparse
import json
from pathlib import Path
import re
import os
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--tests', type=int, default=0)
    parser.add_argument('--latest-pack', action='store_true')
    parser.add_argument('--baseline-locks', action='store_true')
    parser.add_argument('--progression', action='store_true', help='Run the 2.2.0 progression and inventory harness.')
    parser.add_argument('--historical-providers', type=Path, help='Compare old provider bytecode on this runtime; not an old-release boot.')
    parser.add_argument('--timeout', type=int, default=600)
    parser.add_argument('--log', default='validation-console.log')
    args = parser.parse_args()
    root = args.directory.resolve()
    command = [str(args.java), '-Xms1G', '-Xmx8G']
    if args.tests:
        command.append('-Drunicskills.productionValidation=true')
    if args.latest_pack:
        command.append('-Drunicskills.latestPackValidation=true')
    if args.baseline_locks:
        command.append('-Drunicskills.baselineLocks=true')
    if args.progression:
        command.append('-Drunicskills.progressionValidation=true')
    if args.historical_providers:
        command.append('-Drunicskills.historicalProviders='+str(args.historical_providers.resolve()))
    platform_args = 'win_args.txt' if os.name == 'nt' else 'unix_args.txt'
    command += ['@libraries/net/minecraftforge/forge/1.20.1-47.4.23/'+platform_args, '--nogui']
    log_path = root/args.log
    started, ready, stopped = time.monotonic(), None, False
    with log_path.open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                   text=True, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        print(f'Validation child PID {process.pid}; log {log_path}', flush=True)
        last_report = started
        try:
            while process.poll() is None:
                time.sleep(1)
                text = log_path.read_text(encoding='utf-8', errors='replace')
                now = time.monotonic()
                if ready is None and re.search(r'Done \([\d.,]+s\)!', text):
                    ready = now
                    print('World reached Done; waiting for runtime checks.', flush=True)
                passed = len(re.findall(r'FOUR_MOD_PRODUCTION PASS ', text))
                failed = len(re.findall(r'FOUR_MOD_PRODUCTION FAIL ', text))
                fatal = any(message in text for message in ('Failed to start the minecraft server',
                        'Encountered an unexpected exception', 'Failed to load registries due to above errors', 'Failed to load datapacks, can\'t proceed'))
                complete = ready is not None and (passed+failed >= args.tests if args.tests else now-ready >= 30)
                if not stopped and (complete or fatal or now-started > args.timeout):
                    try:
                        process.stdin.write('stop\n')
                        process.stdin.flush()
                    except (BrokenPipeError, OSError):
                        # A startup failure may close stdin between poll() and flush().
                        # Still preserve the failure log and result receipt below.
                        pass
                    stopped = True
                    stop_time = now
                if stopped and now-stop_time > 30:
                    process.terminate()
                if now-last_report > 30:
                    print(f'Elapsed {int(now-started)}s; world_ready={ready is not None}; tests={passed} pass/{failed} fail', flush=True)
                    last_report = now
            process.wait(timeout=10)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
    text = log_path.read_text(encoding='utf-8', errors='replace')
    passed = len(re.findall(r'FOUR_MOD_PRODUCTION PASS ', text))
    failed = len(re.findall(r'FOUR_MOD_PRODUCTION FAIL ', text))
    result = dict(world_ready=ready is not None, passed=passed, failed=failed, expected_tests=args.tests,
                  clean_save='All dimensions are saved' in text, exit_code=process.returncode,
                  elapsed_seconds=round(time.monotonic()-started), log=str(log_path))
    (root/(args.log+'.result.json')).write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(result), flush=True)
    return int(not result['world_ready'] or failed > 0 or passed != args.tests or not result['clean_save'])


if __name__ == '__main__':
    raise SystemExit(main())
