"""Run the packaged Java account service against a disposable schema on the independent mysql8 instance.

Usage on the test host: python3 run-mysql-account-probe.py /absolute/candidate.jar
Requires sudo docker access; credentials stay in process memory and are never printed.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import uuid
import zipfile

jar = Path(sys.argv[1]).resolve(strict=True)
schema = 'antv_account_regression_' + uuid.uuid4().hex
assert re.fullmatch(r'antv_account_regression_[0-9a-f]{32}', schema)
container = json.loads(subprocess.check_output(['sudo', '-n', 'docker', 'inspect', 'mysql8'], text=True))[0]
values = dict(value.split('=', 1) for value in container['Config']['Env'] if '=' in value)
password = values['MYSQL_ROOT_PASSWORD']
address = next(network['IPAddress'] for network in container['NetworkSettings']['Networks'].values() if network['IPAddress'])
assert address.startswith(('172.', '192.168.', '10.'))
mysql = ['sudo', '-n', 'docker', 'exec', '-i', 'mysql8', 'sh', '-c',
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names']

def sql(statement):
    return subprocess.run(mysql, input=statement + '\n', text=True, capture_output=True, check=True, timeout=20).stdout.strip()

port = int(sql('SELECT @@port;'))
sql(f'CREATE DATABASE {schema};')
try:
    with tempfile.TemporaryDirectory(prefix='antv-account-probe-') as temp:
        root = Path(temp)
        (root / 'classes').mkdir()
        (root / 'lib').mkdir()
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                    (root / 'lib' / Path(name).name).write_bytes(archive.read(name))
                elif name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
                    target = root / 'classes' / name.removeprefix('BOOT-INF/classes/')
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(archive.read(name))
        source = Path(__file__).with_name('PointAccountConcurrencyProbe.java')
        classpath = str(root / 'classes') + ':' + str(root / 'lib' / '*')
        subprocess.run(['javac', '-cp', classpath, '-d', str(root / 'classes'), str(source)], check=True, timeout=45)
        env = dict(os.environ, POINT_ACCOUNT_MYSQL_URL=f'jdbc:mysql://{address}:{port}/{schema}?useSSL=false&allowPublicKeyRetrieval=true',
                   POINT_ACCOUNT_MYSQL_PASSWORD=password)
        result = subprocess.run(['java', '-Xms16m', '-Xmx96m', '-cp', classpath, 'PointAccountConcurrencyProbe'],
                                env=env, capture_output=True, text=True, timeout=120)
        print(result.stdout, end='')
        if result.stderr:
            print(result.stderr, file=sys.stderr, end='')
        print(json.dumps({'jar':str(jar),'schema':schema,'exit':result.returncode}))
        exit_code = result.returncode
finally:
    sql(f'DROP DATABASE {schema};')
    print('Disposable regression schema removed', flush=True)
raise SystemExit(exit_code)
