"""Create and verify matching database/skill backups without exposing credentials."""
from datetime import datetime, timezone
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from mysql_check import connection

assert os.geteuid()==0, 'Run with sudo: the backup parent is root-only'
root=Path('/opt/antv/backups')/('20260912-task-center-6652857-'+datetime.now(timezone.utc).strftime('%H%M%S'))
subprocess.run(['sudo','install','-d','-m','700','-o','ubuntu','-g','ubuntu',str(root)],check=True)
cmd,env=connection()
database=next(v.split('=',1)[1] for v in cmd if v.startswith('--database='))
dump=['mysqldump']+[v for v in cmd[1:] if v.startswith(('--protocol=','--host=','--port=','--user=','--default-character-set='))]
dump+=['--single-transaction','--quick','--no-tablespaces','--set-gtid-purged=OFF',database]
process=subprocess.Popen(dump,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
with gzip.open(root/'database.sql.gz','wb') as archive:
    shutil.copyfileobj(process.stdout,archive)
stderr=process.stderr.read()
if process.wait()!=0:
    raise RuntimeError(stderr.decode())
subprocess.run(['sudo','tar','-C','/opt/antv/shared','-czf',str(root/'workflow-skills.tar.gz'),'workflow-skills'],check=True)
subprocess.run(['sudo','tar','-tzf',str(root/'workflow-skills.tar.gz')],check=True,stdout=subprocess.DEVNULL)
subprocess.run(['gzip','-t',str(root/'database.sql.gz')],check=True)
tail=b''
with gzip.open(root/'database.sql.gz','rb') as archive:
    while chunk:=archive.read(1024*1024):
        tail=(tail+chunk)[-4096:]
assert b'Dump completed on' in tail
report={'directory':str(root),'database_gzip_integrity':True,'dump_complete_marker':True,'skills_archive_readable':True,
        'artifacts':{p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in root.iterdir()}}
(Path(__file__).parent/'backup.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report))
