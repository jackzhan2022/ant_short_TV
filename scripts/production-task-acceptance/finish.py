"""Validate evidence, disable only our acceptance identity, remove its local secrets."""
import json
from pathlib import Path
import subprocess
from mysql_check import connection, query

root=Path(__file__).parent.resolve()
baseline=json.loads((root/'snapshot-baseline.json').read_text())
for stage in ('new','rollback','restored'):
    assert json.loads((root/('snapshot-'+stage+'.json')).read_text())==baseline,stage
    assert json.loads((root/('release-'+stage+'.json')).read_text())['success'],stage
state=json.loads((root/'private-smoke-state.json').read_text())
tenant,user=int(state['tenant']),int(state['user'])
guard,_=query(f"SELECT count(*) FROM app_user u JOIN tenant_member m ON m.user_id=u.id JOIN tenant t ON t.id=m.tenant_id WHERE u.id={user} AND t.id={tenant} AND u.nickname='任务中心部署验收-6652857' AND t.name='任务中心部署验收-6652857' AND m.member_type='OWNER'")
assert guard=='1'
cmd,env=connection()
result=subprocess.run(cmd,env=env,input=f"""
START TRANSACTION;
UPDATE app_user SET status='DISABLED',token_version=COALESCE(token_version,0)+1,updated_at=NOW() WHERE id={user} AND nickname='任务中心部署验收-6652857';
UPDATE auth_session SET status='REVOKED',revoked_at=NOW(),revoked_reason='ACCEPTANCE_COMPLETE',updated_at=NOW() WHERE user_id={user} AND revoked_at IS NULL;
COMMIT;
""",capture_output=True,text=True,timeout=30)
if result.returncode:
    raise RuntimeError(result.stderr)
checks={
    'scratch_tables':"SELECT count(*) FROM information_schema.tables WHERE table_schema=database() AND table_name LIKE 'ptc_accept6652857_%'",
    'active_test_sessions':f"SELECT count(*) FROM auth_session WHERE user_id={user} AND revoked_at IS NULL AND status='ACTIVE'",
    'disabled_test_user':f"SELECT count(*) FROM app_user WHERE id={user} AND status='DISABLED'",
    'disabled_test_tenant':f"SELECT count(*) FROM tenant WHERE id={tenant} AND status='DISABLED'",
    'active_executions':"SELECT count(*) FROM ai_execution_task WHERE status IN ('PENDING','QUEUED','RUNNING')"
}
report={'snapshot_groups_unchanged':len(baseline),'stages_compared':['baseline','new','rollback','restored'],
        'release':str(Path('/opt/antv/current').resolve()),'fixture':{k:state[k] for k in ('user','tenant','project','batch','episode')}}
for key,sql in checks.items():
    value,_=query(sql)
    report[key]=int(value)
assert all(report[k]==0 for k in ('scratch_tables','active_test_sessions','active_executions'))
assert report['disabled_test_user']==report['disabled_test_tenant']==1
for name in ('private-smoke-state.json','private-cookies.txt'):
    path=(root/name).resolve()
    assert path.parent==root
    path.unlink()
report['private_files_removed']=True
report['service_active']=subprocess.run(['systemctl','is-active','antv.service'],capture_output=True,text=True,check=True).stdout.strip()
version,_=query('SELECT VERSION(); SELECT max(version+0) FROM flyway_schema_history WHERE success=1;')
report['mysql_version_and_migration']=version.splitlines()
(root/'acceptance-result.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report))
