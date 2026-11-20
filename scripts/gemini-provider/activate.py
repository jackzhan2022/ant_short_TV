"""Install the verified Gemini-only candidate and add the keyless model configuration."""
import datetime
import fcntl
import hashlib
import json
import os
import pathlib
import runpy
import shutil
import subprocess
import time
import urllib.error
import urllib.request

root = pathlib.Path(__file__).resolve().parent
query = runpy.run_path(str(root / 'inspect.py'))['query']
assert os.geteuid() == 0, 'Run with sudo'
locks = []
for name in ('deploy.lock', '.deployment.lock', 'deployment.lock', 'release.lock'):
    lock = open('/opt/antv/' + name, 'a')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    locks.append(lock)
prepared = json.loads((root / 'prepare.json').read_text())
current = pathlib.Path('/opt/antv/current')
previous = current.resolve()
original = previous / 'backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar'
assert str(original) == prepared['original']


def sha(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            digest.update(block)
    return digest.hexdigest()


def rows(table, where):
    columns = query("SELECT column_name FROM information_schema.columns WHERE table_schema=database() AND table_name='" + table + "' ORDER BY ordinal_position").splitlines()
    expression = ','.join("'" + column + "',`" + column + '`' for column in columns)
    result = query('START TRANSACTION READ ONLY; SELECT JSON_OBJECT(' + expression + ') FROM `' + table + '` WHERE ' + where + '; ROLLBACK;')
    return [json.loads(line) for line in result.splitlines()] if result else []


def literal(value):
    if value is None:
        return 'NULL'
    return "CONVERT(X'" + str(value).encode('utf-8').hex() + "' USING utf8mb4)"


assert sha(original) == prepared['original_sha256'], 'Production release changed'
assert sha(prepared['candidate']) == prepared['candidate_sha256']
assert prepared['offline_probe'] == 'PASS'
assert query("SELECT COUNT(*) FROM ai_model WHERE code='Gemini-image'") == '0', 'Model already exists'
provider = rows('ai_provider', "code='Gemini'")
assert len(provider) == 1
provider_id = provider[0]['id']
config = rows('ai_provider_config', 'provider_id=' + str(provider_id))
assert len(config) == 1
assert query('SELECT code FROM ai_model WHERE id=8') == 'gpt-image-2'
gemini_models = rows('ai_model', 'provider_id=' + str(provider_id))
assert not gemini_models, 'Existing Gemini models require review'
fallback_sort = int(query("SELECT COALESCE(MIN(sort),0)-1 FROM ai_model WHERE service_type='IMAGE'"))
source = {}
for prefix in ('ai_model_price', 'ai_model_point_price'):
    versions = rows(prefix + '_version', "model_id=8 AND status='PUBLISHED' AND effective_from<=NOW() AND (effective_to IS NULL OR effective_to>NOW())")
    assert len(versions) == 1, prefix
    version = versions[0]
    components = rows(prefix + '_component', 'price_version_id=' + str(version['id']))
    assert len(components) == 3
    source[prefix] = dict(version=version, components=components)
stamp = datetime.datetime.now().strftime('%Y%m%d%H%M%S')
backup = pathlib.Path('/opt/antv/backups') / (stamp + '-gemini-provider')
backup.mkdir(mode=0o700)
before = dict(provider=provider, config=config, source=source)
snapshot = backup / 'before.private.json'
snapshot.write_text(json.dumps(before, ensure_ascii=False, indent=2))
snapshot.chmod(0o600)
release = pathlib.Path('/opt/antv/releases') / (stamp + '-gemini-native')
(release / 'backend').mkdir(parents=True)
shutil.copy2(prepared['candidate'], release / 'backend' / original.name)
os.symlink('/opt/antv/shared/env', release / 'backend/env')
os.symlink(previous / 'frontend', release / 'frontend')
assert (release / 'frontend/dist/index.html').is_file()

# These inserts are isolated to a new model; no historical versions or existing model settings change.
sql = ['START TRANSACTION', 'SET @now=NOW()',
       "UPDATE ai_provider SET default_base_url='http://89.58.47.162:8080',recommended_models='gemini-image',status='ENABLED',updated_at=@now WHERE id=" + str(provider_id),
       "UPDATE ai_provider_config SET base_url='http://89.58.47.162:8080',status='ENABLED',updated_at=@now WHERE provider_id=" + str(provider_id),
       "INSERT INTO ai_model(provider_id,code,name,model_code,service_type,description,status,is_default,sort,config_json,created_at,updated_at) VALUES(" + str(provider_id) + ", 'Gemini-image','Gemini-image','gemini-image','IMAGE','Gemini native image generation','ENABLED',0," + str(fallback_sort) + ",'{}',@now,@now)",
       'SET @model=LAST_INSERT_ID()',
       "INSERT INTO ai_model_capability(model_id,capability,status,created_at,updated_at) VALUES(@model,'IMAGE_GENERATION','ENABLED',@now,@now)",
       'INSERT INTO ai_model_execution_quota(model_id,concurrency_limit,updated_at) SELECT @model,concurrency_limit,@now FROM ai_model_execution_quota WHERE model_id=8',
       'INSERT INTO ai_model_price_version_sequence(model_id,price_type,last_version_no) SELECT @model,price_type,1 FROM ai_model_price_version_sequence WHERE model_id=8']
for prefix, data in source.items():
    sql.append('INSERT INTO ' + prefix + "_version(model_id,version_no,status,effective_from,published_at,created_at) VALUES(@model,1,'PUBLISHED',@now,@now,@now)")
    sql.append('SET @version=LAST_INSERT_ID()')
    columns = [column for column in data['components'][0] if column not in ('id', 'price_version_id', 'created_at')]
    sql.append('INSERT INTO ' + prefix + '_component(price_version_id,' + ','.join('`' + column + '`' for column in columns) + ',created_at) SELECT @version,' + ','.join('`' + column + '`' for column in columns) + ',@now FROM ' + prefix + '_component WHERE price_version_id=' + str(data['version']['id']))
sql.extend(['COMMIT', 'SELECT @model'])
model_id = int(query(';\n'.join(sql) + ';'))


def switch(path):
    temporary = pathlib.Path('/opt/antv/.current-gemini-new')
    assert not temporary.exists() and not temporary.is_symlink()
    os.symlink(path, temporary)
    os.replace(temporary, current)


try:
    # systemd performs the application's normal graceful shutdown; durable pending work remains queued.
    subprocess.run(['systemctl', 'stop', 'antv.service'], check=True)
    assert sha(original) == prepared['original_sha256']
    switch(release)
    subprocess.run(['systemctl', 'start', 'antv.service'], check=True)
    healthy = False
    for _ in range(60):
        try:
            urllib.request.urlopen('http://127.0.0.1:8080/api/currentUser', timeout=3)
        except urllib.error.HTTPError as error:
            if error.code == 401:
                healthy = True
                break
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(2)
    assert healthy, 'Backend health check did not reach protected 401'
    assert subprocess.check_output(['systemctl', 'is-active', 'antv.service'], text=True).strip() == 'active'
except BaseException:
    subprocess.run(['systemctl', 'stop', 'antv.service'], check=False)
    switch(previous)
    query("UPDATE ai_model SET status='DISABLED',updated_at=NOW() WHERE id=" + str(model_id))
    subprocess.run(['systemctl', 'start', 'antv.service'], check=True)
    raise

for prefix, data in source.items():
    versions = rows(prefix + '_version', 'model_id=' + str(model_id))
    assert len(versions) == 1
    actual = rows(prefix + '_component', 'price_version_id=' + str(versions[0]['id']))
    normalize = lambda items: sorted(json.dumps({key: value for key, value in item.items() if key not in ('id', 'price_version_id', 'created_at')}, sort_keys=True) for item in items)
    assert normalize(actual) == normalize(data['components']), 'Copied pricing mismatch'
after_config = rows('ai_provider_config', 'provider_id=' + str(provider_id))
assert after_config[0]['api_key_cipher'] == config[0]['api_key_cipher'], 'Key changed'
assert after_config[0]['base_url'] == 'http://89.58.47.162:8080'
receipt = dict(previous_release=str(previous), release=str(release), backup=str(backup),
               provider_id=provider_id, model_id=model_id, model_code='gemini-image',
               base_url=after_config[0]['base_url'], api_key_preserved=True,
               has_api_key=bool(after_config[0]['api_key_cipher']), price_source_model_id=8,
               pricing_components_verified=True, candidate_sha256=prepared['candidate_sha256'],
               service='active', protected_api_status=401)
(root / 'activation-receipt.json').write_text(json.dumps(receipt, indent=2))
print(json.dumps(receipt))
