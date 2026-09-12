"""Terminal legacy fixture in the API-created acceptance tenant; no scheduled work."""
import json
from pathlib import Path
import subprocess
from mysql_check import connection, query

path=Path(__file__).parent/'private-smoke-state.json'
state=json.loads(path.read_text())
assert not state.get('batch'), 'Fixture already seeded'
tenant,user,project=(int(state[k]) for k in ('tenant','user','project'))
output,_=query(f"SELECT count(*) FROM tenant WHERE id={tenant} AND name='任务中心部署验收-6652857'")
assert output=='1'
sql=f"""
START TRANSACTION;
INSERT INTO video_decomposition_batch(tenant_id,project_id,name,status,total_episodes,completed_episodes,failed_episodes,created_by,created_at,updated_at)
VALUES({tenant},{project},'ACCEPTANCE legacy terminal fixture','SUCCEEDED',1,1,0,{user},NOW(),NOW());
SET @batch=LAST_INSERT_ID();
INSERT INTO video_decomposition_episode(batch_id,tenant_id,project_id,episode_no,source_file_name,storage_path,file_size,status,analysis_version,draft_status,draft_version,created_by,created_at,updated_at)
VALUES(@batch,{tenant},{project},1,'acceptance-no-media.mp4','acceptance://no-media',1,'SUCCEEDED',1,'COMPLETED',1,{user},NOW(),NOW());
SET @episode=LAST_INSERT_ID();
INSERT INTO video_decomposition_analysis(episode_id,schema_version,status,normalized_json,created_at)
VALUES(@episode,'v1','SUCCEEDED','{{}}',NOW());
SET @analysis=LAST_INSERT_ID();
INSERT INTO video_decomposition_script_result(tenant_id,batch_id,episode_id,analysis_id,content,format_version,created_at)
VALUES({tenant},@batch,@episode,@analysis,'ACCEPTANCE fixture result: preserve across rollback','v1',NOW());
SELECT @batch,@episode;
COMMIT;
"""
cmd,env=connection()
result=subprocess.run(cmd,env=env,input=sql,capture_output=True,text=True,timeout=30)
if result.returncode:
    raise RuntimeError(result.stderr)
state['batch'],state['episode']=map(int,result.stdout.strip().split('\t'))
path.write_text(json.dumps(state))
path.chmod(0o600)
print(json.dumps({k:state[k] for k in ('tenant','project','batch','episode')}))
