"""Read-only deployed MySQL checks. Credentials stay on the deployment host."""
import argparse
import json
import os
from pathlib import Path
import shlex
import subprocess
import time
from urllib.parse import urlparse


def connection():
    values = {}
    for line in Path('/opt/antv/shared/env').read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        key, value = line.removeprefix('export ').split('=', 1)
        parts = shlex.split(value, comments=True)
        values[key] = parts[0] if parts else ''
    url = urlparse(values['MYSQL_URL'].removeprefix('jdbc:'))
    env = dict(os.environ, MYSQL_PWD=values['MYSQL_PASSWORD'])
    cmd = ['mysql', '--protocol=TCP', '--host='+url.hostname,
           '--port='+str(url.port or 3306), '--user='+values['MYSQL_USERNAME'],
           '--database='+url.path.lstrip('/'), '--default-character-set=utf8mb4',
           '--batch', '--raw', '--skip-column-names', '--connect-timeout=10']
    return cmd, env


def query(sql):
    cmd, env = connection()
    start = time.monotonic()
    result = subprocess.run(cmd, env=env, input='SET SESSION MAX_EXECUTION_TIME=5000; START TRANSACTION READ ONLY;\n'+sql+';\nROLLBACK;\n',
                            capture_output=True, text=True, timeout=30)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.rstrip(), round((time.monotonic()-start)*1000, 2)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('sql', type=Path)
    args = parser.parse_args()
    output, elapsed = query(args.sql.read_text())
    print(json.dumps({'elapsed_ms': elapsed, 'output': output}, ensure_ascii=False))
