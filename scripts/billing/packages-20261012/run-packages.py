import os
from pathlib import Path
import shlex
import subprocess
import sys

root = Path(__file__).resolve().parent
runtime = Path('/home/ubuntu/antv-billing-20261012/runtime-c193674fdde02a24ebb677179b078efd796eabb959878b5128faa328c7523152')
assert (runtime / 'sha256.complete').read_text() == 'c193674fdde02a24ebb677179b078efd796eabb959878b5128faa328c7523152'
assert Path('/opt/antv/current').resolve().name == '20261012-billing-7f5c0b1'
values = {}
for line in Path('/opt/antv/shared/env').read_text().splitlines():
    line = line.strip()
    if not line or line.startswith('#') or '=' not in line:
        continue
    key, value = line.removeprefix('export ').split('=', 1)
    if key in ('MYSQL_URL', 'MYSQL_USERNAME', 'MYSQL_PASSWORD'):
        parts = shlex.split(value, comments=True)
        values[key] = parts[0] if parts else ''
env = dict(os.environ, **values)
env.pop('JAVA_TOOL_OPTIONS', None)
os.umask(0o077)
classpath = os.pathsep.join((str(runtime/'BOOT-INF/classes'),str(runtime/'BOOT-INF/lib/*')))
compiled = root / 'helper-classes'
compiled.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--class-path',classpath,'-d',str(compiled),
    str(root/'PublishPackages.java')],env=env,cwd=root,check=True)
raise SystemExit(subprocess.run(['java','-Xmx256m','-Duser.timezone=Asia/Shanghai','--class-path',str(compiled)+os.pathsep+classpath,
    'com.antshorttv.commercial.PublishPackages',*sys.argv[1:]],env=env,cwd=root).returncode)
