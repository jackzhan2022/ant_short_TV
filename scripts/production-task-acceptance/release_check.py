"""Explicit retained-release switches with health checks and automatic failure recovery."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request
from mysql_check import query

ROOT=Path(__file__).parent
OLD='/opt/antv/releases/202609121303-94d362f'
NEW='/opt/antv/releases/202609121450-6652857'
TMP=Path('/tmp/202609121450-6652857')
JAR='ant-short-tv-backend-0.1.0-SNAPSHOT.jar'
ARCHIVE='202609121450-6652857-frontend.tar.gz'
HASHES={JAR:'09b2503867fc736bf6de135ff7143a077c7f3a0f9893071242a060f7e42229f1',
        ARCHIVE:'356d083c46b62f24c3f1db4bb042d55fad2ff3fbe1b9b7651743633d27bde1ca'}

def run(*args):
    return subprocess.run(args,check=True,capture_output=True,text=True,timeout=60).stdout.strip()

def status(url):
    try:
        with urllib.request.urlopen(url,timeout=3) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code
    except (OSError,TimeoutError):
        return 0

stage=sys.argv[1]
assert stage in ('new','rollback','restored')
previous=str(Path('/opt/antv/current').resolve())
assert previous==(NEW if stage=='rollback' else OLD),previous
backup=json.loads((ROOT/'backup.json').read_text())
assert backup['database_gzip_integrity'] and backup['skills_archive_readable'] and backup['dump_complete_marker']
for table,condition in [('ai_execution_task',"status IN ('RUNNING','PENDING','QUEUED')"),
                        ('script_analysis_task',"status='RUNNING'"),
                        ('review_task',"status IN ('RUNNING','PENDING','QUEUED')"),
                        ('video_decomposition_episode',"status IN ('PENDING_ANALYSIS','ANALYZING','PENDING_DRAFT','DRAFT_GENERATING')")]:
    active,_=query(f'SELECT count(*) FROM {table} WHERE {condition}')
    assert active=='0',f'Active work in {table}: {active}'
if stage=='new':
    assert not Path(NEW).exists(),'Release path already exists'
    for name,digest in HASHES.items():
        assert hashlib.sha256((TMP/name).read_bytes()).hexdigest()==digest
    run('sudo','install','-d','-m','755',NEW+'/backend',NEW+'/frontend')
    run('sudo','install','-m','644',str(TMP/JAR),NEW+'/backend/'+JAR)
    run('sudo','tar','-xzf',str(TMP/ARCHIVE),'-C',NEW+'/frontend')
    run('sudo','ln','-s','/opt/antv/shared/env',NEW+'/backend/env')
    assert Path(NEW+'/frontend/dist/tasks/index.html').is_file()
for shared in ('workflow-skills','review-exports'):
    run('test','-w','/opt/antv/shared/'+shared)
target=OLD if stage=='rollback' else NEW
report={'stage':stage,'previous':previous,'target':target,'preflight_no_running_work':True,'checks':[]}
start=time.monotonic()
try:
    run('sudo','ln','-sfnT',target,'/opt/antv/current')
    run('sudo','systemctl','restart','antv.service')
    deadline=time.monotonic()+100
    while time.monotonic()<deadline:
        code=status('http://127.0.0.1:8080/api/auth/bootstrap')
        report['checks'].append({'elapsed_s':round(time.monotonic()-start,2),'bootstrap_unauth':code})
        if code==401:
            break
        time.sleep(2)
    assert code==401,'Backend readiness timeout'
    assert run('systemctl','is-active','antv.service')=='active'
    assert status('https://antv.aixmax.cn/')==200
    assert str(Path('/opt/antv/current').resolve())==target
    report['ready_seconds']=round(time.monotonic()-start,2)
    report['success']=True
    report['task_bundle_present']=bool(list(Path(target+'/frontend/dist').glob('p__tasks__index.*.async.js')))
    assert report['task_bundle_present']==(stage!='rollback')
except BaseException:
    run('sudo','ln','-sfnT',previous,'/opt/antv/current')
    run('sudo','systemctl','restart','antv.service')
    report['success']=False
    report['recovery_target']=previous
    raise
finally:
    (ROOT/('release-'+stage+'.json')).write_text(json.dumps(report,indent=2))
    print(json.dumps(report),flush=True)
