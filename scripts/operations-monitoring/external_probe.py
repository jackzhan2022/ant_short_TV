"""Run on a host independent of the application. Python 3 standard library only.

Credentials come from OPS_PROBE_WEBHOOK/OPS_PROBE_SECRET and are never logged.
State records stable transition IDs before delivery; a lost remote response can duplicate a send.
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid


def healthy(status, body):
    return 200 <= status < 300 and isinstance(body, dict) and body.get('status') == 'UP'


def check(url):
    try:
        request = urllib.request.Request(url, headers={'User-Agent': 'AntShortTV-ExternalProbe/1'})
        with urllib.request.urlopen(request, timeout=5) as response:
            raw = response.read(8193)
            return len(raw) <= 8192 and healthy(response.status, json.loads(raw))
    except (OSError, ValueError, urllib.error.URLError):
        return False


def observe(state, up, now):
    state.setdefault('pending', [])
    state.setdefault('active', False)
    state['failures'] = 0 if up else state.get('failures', 0) + 1
    state['successes'] = state.get('successes', 0) + 1 if up else 0
    transition = None
    if not state['active'] and state['failures'] >= 3:
        state['active'] = True
        state['incident'] = str(uuid.uuid4())
        transition = 'FIRING'
    elif state['active'] and state['successes'] >= 2:
        state['active'] = False
        transition = 'RESOLVED'
    elif state['active'] and not up and now - state.get('last_notice', now) >= 1800:
        transition = 'REMINDER'
    if transition:
        state['last_notice'] = now
        state['pending'].append({'id': str(uuid.uuid4()), 'incident': state['incident'],
                                 'transition': transition, 'status': 'PENDING',
                                 'attempts': 0, 'next': now})
        if len(state['pending']) > 100:
            state['pending'].pop(0)
            state['dropped'] = state.get('dropped', 0) + 1
    state['last_check'] = now
    state['up'] = up


def send(webhook, secret, text):
    payload = {'msg_type': 'text', 'content': {'text': text}}
    if secret:
        timestamp = str(int(time.time()))
        payload['timestamp'] = timestamp
        payload['sign'] = base64.b64encode(hmac.new((timestamp + '\n' + secret).encode(), b'', hashlib.sha256).digest()).decode()
    request = urllib.request.Request(webhook, data=json.dumps(payload).encode(),
                                     headers={'Content-Type': 'application/json'}, method='POST')
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            raw = response.read(8193)
            if len(raw) > 8192:
                return False
            result = json.loads(raw)
            if not isinstance(result, dict):
                return False
            code = result.get('code', result.get('StatusCode'))
            return 200 <= response.status < 300 and type(code) is int and code == 0
    except (OSError, ValueError, urllib.error.URLError):
        return False


def deliver(state, webhook, secret, name, now):
    if not webhook:
        return
    for item in state.get('pending', []):
        if item['status'] != 'PENDING' or item['next'] > now:
            continue
        item['attempts'] += 1
        text = f"[OPS-PROBE {item['incident']} / {item['id']}] {name}: {item['transition']}"
        if send(webhook, secret, text):
            item['status'] = 'DELIVERED'
        else:
            item['status'] = 'FAILED' if item['attempts'] >= 5 else 'PENDING'
            item['next'] = now + (60 if item['attempts'] == 1 else 300 if item['attempts'] == 2 else 900)
        break  # At most one delivery per check; no notification storm after reconnecting.


def save(path, state):
    temporary = path.with_suffix('.tmp')
    with open(temporary, 'w', encoding='utf-8') as target:
        json.dump(state, target)
        target.flush()
        os.fsync(target.fileno())
    os.replace(temporary, path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True, help='Public /api/public/operations-health URL')
    parser.add_argument('--state', required=True, type=Path)
    parser.add_argument('--name', default='Ant Short TV', help='Safe service label; never include credentials')
    parser.add_argument('--once', action='store_true', help='One check; notification policy still applies')
    args = parser.parse_args()
    if not args.url.startswith(('https://', 'http://')):
        parser.error('HTTP(S) URL required')
    args.state.parent.mkdir(parents=True, exist_ok=True)
    # Refuse to run a second process with the same state. File handle remains open through the loop.
    lock = open(args.state.with_suffix('.lock'), 'a+b')
    lock.seek(0)
    lock.write(b'1')
    lock.flush()
    lock.seek(0)
    if os.name == 'nt':
        import msvcrt
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
    else:
        import fcntl
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    # Corrupt state is a visible startup failure; never silently lose an active incident.
    state = json.loads(args.state.read_text(encoding='utf-8')) if args.state.exists() else {}
    webhook = os.environ.get('OPS_PROBE_WEBHOOK', '')
    secret = os.environ.get('OPS_PROBE_SECRET', '')
    if not webhook:
        print('External probe channel is not configured; checks are saved locally.', flush=True)
    while True:
        started = time.monotonic()
        observe(state, check(args.url), int(time.time()))
        save(args.state, state)
        deliver(state, webhook, secret, args.name[:100], int(time.time()))
        save(args.state, state)
        print(json.dumps({'up': state['up'], 'active': state['active'],
                          'pending': sum(x['status'] == 'PENDING' for x in state['pending']),
                          'failed': sum(x['status'] == 'FAILED' for x in state['pending']),
                          'dropped': state.get('dropped', 0)}), flush=True)
        if args.once:
            break
        time.sleep(max(0, 30 - (time.monotonic() - started)))


if __name__ == '__main__':
    main()
