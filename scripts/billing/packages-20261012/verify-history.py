import json
from pathlib import Path
import subprocess
import urllib.request

root = Path(__file__).resolve().parent
result = subprocess.run(['python3','/tmp/antv-billing-20261012/mysql_check.py',
    '/tmp/antv-billing-20261012/package-preflight.sql'],check=True,capture_output=True,text=True)
(root/'audit-after.json').write_text(result.stdout)
before=json.loads((root/'audit-before.json').read_text())['output']
after=json.loads(result.stdout)['output']
prefixes=('orders\t','ledger\t','account\t','subscriptions\t')
prior=[line for line in before.splitlines() if line.startswith(prefixes)]
current=[line for line in after.splitlines() if line.startswith(prefixes)]
assert prior==current,'History aggregates changed; inspect concurrent activity'
assert subprocess.check_output(['systemctl','is-active','antv.service'],text=True).strip()=='active'
with urllib.request.urlopen('http://127.0.0.1:8080/actuator/health',timeout=10) as response:
    assert json.load(response)['status']=='UP'
print('HISTORY_UNCHANGED_AND_SERVICE_UP')
print('\n'.join(line for line in after.splitlines() if line.startswith('versions\t')))
