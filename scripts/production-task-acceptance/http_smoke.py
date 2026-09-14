"""Dedicated acceptance identity; cookies/password remain on the host, mode 0600."""
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.request

ROOT=Path(__file__).parent
STATE=ROOT/'private-smoke-state.json'
COOKIES=ROOT/'private-cookies.txt'
BASE='https://antv.aixmax.cn'
os.umask(0o077)
jar=http.cookiejar.MozillaCookieJar(str(COOKIES))
if COOKIES.exists():
    jar.load(ignore_discard=True,ignore_expires=True)
opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
state=json.loads(STATE.read_text()) if STATE.exists() else {}
records=[]

def request(path,body=None,expected=200,method=None):
    headers={'Content-Type':'application/json'}
    if state.get('tenant'):
        headers['X-Tenant-Id']=str(state['tenant'])
    for cookie in jar:
        if cookie.name=='XSRF-TOKEN':
            headers['X-XSRF-TOKEN']=cookie.value
    start=time.monotonic()
    req=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        response=opener.open(req,timeout=30)
    except urllib.error.HTTPError as error:
        response=error
    raw=response.read()
    jar.save(ignore_discard=True,ignore_expires=True)
    records.append({'path':path,'method':req.get_method(),'status':response.code,'ms':round((time.monotonic()-start)*1000,2)})
    if response.code != expected:
        raise RuntimeError(f'{req.get_method()} {path}: expected {expected}, got {response.code}; '+raw.decode()[:500])
    try:
        value=json.loads(raw)
    except ValueError:
        return None
    return value.get('data',value)

def save():
    STATE.write_text(json.dumps(state))
    STATE.chmod(0o600)

stage=sys.argv[1]
try:
    if stage=='setup':
        if not state:
            state={'mobile':'199'+''.join(str(secrets.randbelow(10)) for _ in range(8)),
                   'password':secrets.token_urlsafe(24)}
            save()
            result=request('/api/auth/register',{'mobile':state['mobile'],'password':state['password'],
                           'verificationCode':'123456','nickname':'任务中心部署验收-6652857'})
            state['user']=result['user']['id']
            save()
        if not state.get('user'):
            result=request('/api/auth/login',{'mobile':state['mobile'],'password':state['password']})
            state['user']=result['user']['id']
            save()
        request('/api/auth/bootstrap')
        if not state.get('tenant'):
            result=request('/api/tenants',{'name':'任务中心部署验收-6652857','type':'OTHER',
                           'description':'部署及回滚验收隔离团队，无真实生产数据；验收后停用。'})
            state['tenant']=result['id']
            save()
        if not state.get('project'):
            result=request('/api/projects',{'name':'任务中心回滚验收','code':'PTC6652857','ownerId':state['user']})
            state['project']=result['id']
            save()
        print(json.dumps({key:state[key] for key in ('user','tenant','project')}))
    else:
        request('/api/auth/login',{'mobile':state['mobile'],'password':state['password']})
        request('/api/auth/bootstrap')
        request('/api/projects')
        project=request('/api/projects/'+str(state['project']))
        assert project['id']==state['project']
        request('/api/video-script-decomposition/batches')
        if state.get('batch'):
            request('/api/video-script-decomposition/batches/'+str(state['batch']))
            request('/api/video-script-decomposition/batches/'+str(state['batch'])+'/screenplays')
            episode=request('/api/video-script-decomposition/episodes/'+str(state['episode']))
            assert episode['screenplayContent']=='ACCEPTANCE fixture result: preserve across rollback'
        if stage in ('new','restored'):
            path='/api/tenants/'+str(state['tenant'])+'/production-tasks'
            mine=request(path+'?scope=mine')
            team=request(path+'?scope=team')
            assert mine['canViewTeamTasks'] and team['canViewTeamTasks']
            summary=request(path+'/summary?scope=team')
            assert summary['total']==team['total']
            if state.get('batch'):
                key='VIDEO_DECOMPOSITION:'+str(state['batch'])
                detail=request(path+'/'+key)
                assert detail['taskKey']==key and detail['statusGroup']=='SUCCEEDED' and not detail['restricted']
                children=request(path+'/'+key+'/children')
                assert children['total']==1 and children['items'][0]['statusGroup']=='SUCCEEDED'
            request('/api/tenants/1/production-tasks?scope=team',expected=403)
            request('/tasks')
        elif stage=='rollback':
            request('/api/tenants/'+str(state['tenant'])+'/production-tasks?scope=mine',expected=404)
        elif stage=='close':
            request('/api/tenants/'+str(state['tenant'])+'/status',{'status':'DISABLED'},method='PUT')
            request('/api/auth/logout',{})
finally:
    (ROOT/('http-'+stage+'.json')).write_text(json.dumps(records,indent=2))
    print(json.dumps(records),flush=True)
