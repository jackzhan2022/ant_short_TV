"""Read-only Gemini configuration and billing inspection; never print secrets."""
import json
import os
import pathlib
import shlex
import subprocess
from urllib.parse import urlparse


def connection():
    values = {}
    for line in pathlib.Path('/opt/antv/shared/env').read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line:
            continue
        key, value = line.strip().removeprefix('export ').split('=', 1)
        parts = shlex.split(value, comments=True)
        values[key] = parts[0] if parts else ''
    url = urlparse(values['MYSQL_URL'].removeprefix('jdbc:'))
    command = ['mysql', '--host=' + url.hostname, '--port=' + str(url.port or 3306),
               '--user=' + values['MYSQL_USERNAME'], '--database=' + url.path.lstrip('/'),
               '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4']
    return command, dict(os.environ, MYSQL_PWD=values['MYSQL_PASSWORD'])


def query(sql):
    command, env = connection()
    result = subprocess.run(command, env=env, input=sql, text=True, capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


if __name__ == '__main__':
    for table in ['ai_model', 'ai_model_capability', 'ai_model_execution_quota',
                  'ai_model_price_version', 'ai_model_price_component',
                  'ai_model_point_price_version', 'ai_model_point_price_component',
                  'ai_model_price_version_sequence', 'ai_provider_config']:
        print(table, query('SHOW CREATE TABLE ' + table))
    for table in ['ai_model_price_version', 'ai_model_price_component',
                  'ai_model_point_price_version', 'ai_model_point_price_component']:
        print(table, query('SELECT * FROM ' + table + ' WHERE ' + (
            'model_id IN (8,9,10)' if table.endswith('version') else
            'price_version_id IN (SELECT id FROM ' + table.replace('_component', '_version') +
            ' WHERE model_id IN (8,9,10))')))
