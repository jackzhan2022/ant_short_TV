"""Activate the reviewed exact-release candidate and verify the narrow supplier migration."""
import datetime,fcntl,hashlib,json,os,pathlib,shlex,shutil,subprocess,time,urllib.request
from urllib.parse import urlparse
root=pathlib.Path(__file__).resolve().parent
prepared=json.loads((root/'prepare.json').read_text())
assert os.geteuid()==0, 'Run with sudo'
locks=[]
for name in ('deploy.lock','.deployment.lock','deployment.lock','release.lock'):
 f=open('/opt/antv/'+name,'a');fcntl.flock(f,fcntl.LOCK_EX|fcntl.LOCK_NB);locks.append(f)
def sha(p):
 h=hashlib.sha256()
 with pathlib.Path(p).open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block)
 return h.hexdigest()
current=pathlib.Path('/opt/antv/current')
assert current.resolve()/('backend/'+pathlib.Path(prepared['original']).name)==pathlib.Path(prepared['original'])
assert sha(prepared['original'])==prepared['original_sha256']
assert sha(prepared['candidate'])==prepared['candidate_sha256']
values={}
for line in pathlib.Path('/opt/antv/shared/env').read_text().splitlines():
 line=line.strip()
 if not line or line.startswith('#') or '=' not in line:continue
 k,v=line.removeprefix('export ').split('=',1);parts=shlex.split(v,comments=True);values[k]=parts[0] if parts else ''
u=urlparse(values['MYSQL_URL'].removeprefix('jdbc:'))
env=dict(os.environ,MYSQL_PWD=values['MYSQL_PASSWORD'])
mysql=['mysql','--protocol=TCP','--host='+u.hostname,'--port='+str(u.port or 3306),'--user='+values['MYSQL_USERNAME'],'--database='+u.path.lstrip('/'),'--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','--connect-timeout=10']
def query(sql):
 r=subprocess.run(mysql,env=env,input='SET SESSION MAX_EXECUTION_TIME=10000; START TRANSACTION READ ONLY;\n'+sql+';\nROLLBACK;\n',text=True,capture_output=True,timeout=25)
 if r.returncode:raise RuntimeError(r.stderr[:1500])
 return r.stdout.strip()
def rows(table,where='1=1'):
 cols=query("select column_name from information_schema.columns where table_schema=database() and table_name='"+table+"' order by ordinal_position").splitlines()
 assert cols,table
 expr=','.join("'"+col+"',`"+col+'`' for col in cols)
 data=query('select JSON_OBJECT('+expr+') from `'+table+'` where '+where+' order by `'+cols[0]+'`')
 return [json.loads(line) for line in data.splitlines()] if data else []
def snapshot():
 tables=['ai_model_price_version','ai_model_price_component','ai_model_point_price_version','ai_model_point_price_component',
         'ai_model_price_version_sequence','ai_model_parameter_profile','ai_model_capability','ai_model_execution_quota']
 result={table:rows(table) for table in tables}
 result['models']=rows('ai_model',"code like 'SEEDANCE_%'")
 result['original_provider']=rows('ai_provider',"code='VOLCENGINE_ARK'")
 original_id=result['original_provider'][0]['id']
 result['original_config']=rows('ai_provider_config','provider_id='+str(original_id))
 return result
assert query("select count(*) from ai_provider where code='VOLCENGINE_ARK_XINJI'")=='0','Supplier already exists'
assert query("select count(*) from flyway_schema_history where version='155'")=='0','Migration version is already used'
assert query("select count(*) from ai_execution_task where status in ('PENDING','RUNNING')")=='0','Active AI tasks; retry once they finish'
before=snapshot()
stamp=datetime.datetime.now().strftime('%Y%m%d%H%M%S')
backup=pathlib.Path('/opt/antv/backups')/(stamp+'-xinji-provider');backup.mkdir(mode=0o700)
private=backup/'before.private.json';private.write_text(json.dumps(before,ensure_ascii=False));private.chmod(0o600)
release=pathlib.Path('/opt/antv/releases')/(stamp+'-xinji-provider')
(release/'backend').mkdir(parents=True)
shutil.copy2(prepared['candidate'],release/'backend'/pathlib.Path(prepared['original']).name)
os.symlink('/opt/antv/shared/env',release/'backend/env')
os.symlink(current.resolve()/'frontend',release/'frontend')
receipt={'before_release':str(current.resolve()),'release':str(release),'backup':str(backup),'prepared':prepared,'started_at':datetime.datetime.now().isoformat()}
(backup/'activation.json').write_text(json.dumps(receipt,indent=2))
print('Preflight passed; targeted configuration backup saved. Activating candidate.',flush=True)
subprocess.run(['systemctl','stop','antv.service'],check=True,timeout=60)
link=pathlib.Path('/opt/antv')/('current.xinji-'+stamp);os.symlink(release,link);os.replace(link,current)
subprocess.run(['systemctl','start','antv.service'],check=True,timeout=30)
print('Candidate started; waiting for health and Flyway migration.',flush=True)
health=None
for _ in range(90):
 try:
  with urllib.request.urlopen('http://127.0.0.1:8080/actuator/health',timeout=3) as response:health=json.load(response)
  if health.get('status')=='UP':break
 except Exception:pass
 time.sleep(2)
assert health and health.get('status')=='UP','Candidate did not become healthy; inspect activation.json and service logs'
after=snapshot()
xinji=rows('ai_provider',"code='VOLCENGINE_ARK_XINJI'");assert len(xinji)==1
assert xinji[0]['name']=='\u706b\u5c71\uff08\u4fe1\u8ba1\uff09'
xinji_id=xinji[0]['id']
for model in before['models']:
 if model['code'] in ('SEEDANCE_2_0_STANDARD','SEEDANCE_2_5'):model['provider_id']=xinji_id
assert after==before,'Unexpected configuration, price, or model change'
new_config=rows('ai_provider_config','provider_id='+str(xinji_id))[0]
original_config=after['original_config'][0]
for key in original_config:
 if key not in ('id','provider_id','created_at','updated_at'):assert new_config[key]==original_config[key],key
for key in ('supported_types','default_base_url','recommended_models','description','status'):
 assert xinji[0][key]==after['original_provider'][0][key],key
assert query("select count(*) from flyway_schema_history where version='155' and success=1")=='1'
receipt.update({'completed_at':datetime.datetime.now().isoformat(),'health':health,'supplier_id':xinji_id,'supplier_name':xinji[0]['name'],
 'models':[{'id':m['id'],'code':m['code'],'provider_id':m['provider_id']} for m in after['models']],
 'model_settings_preserved':True,'cost_prices_preserved':True,'point_prices_preserved':True,'capabilities_parameters_quotas_preserved':True,'connection_config_identical':True,
 'status':'VERIFIED'})
(backup/'activation.json').write_text(json.dumps(receipt,indent=2,ensure_ascii=False))
(root/'activation-receipt.json').write_text(json.dumps(receipt,indent=2,ensure_ascii=False));os.chown(root/'activation-receipt.json',1000,1000)
print(json.dumps(receipt,ensure_ascii=True),flush=True)
