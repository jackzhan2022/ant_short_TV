"""Prepare the exact deployed release with only GeminiAdapter replaced."""
import hashlib
import json
import pathlib
import subprocess
import zipfile

root = pathlib.Path(__file__).resolve().parent
original = pathlib.Path('/opt/antv/current/backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar').resolve()
assert not (root / 'prepare.json').exists(), 'Use a fresh maintenance directory'
classes = root / 'runtime/classes'
libs = root / 'runtime/lib'
classes.mkdir(parents=True)
libs.mkdir()
with zipfile.ZipFile(original) as archive:
    for name in archive.namelist():
        if name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
            path = classes / name.removeprefix('BOOT-INF/classes/')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(name))
        elif name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
            (libs / pathlib.Path(name).name).write_bytes(archive.read(name))
classpath = str(classes) + ':' + str(libs / '*')
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes),
                str(root / 'GeminiAdapter.java'), str(root / 'VerifyGeminiNative.java')], check=True)
entry = 'BOOT-INF/classes/com/antshorttv/ai/GeminiAdapter.class'
replacement = (classes / 'com/antshorttv/ai/GeminiAdapter.class').read_bytes()
candidate = root / 'candidate.jar'
with zipfile.ZipFile(original) as source, zipfile.ZipFile(candidate, 'w') as target:
    for item in source.infolist():
        target.writestr(item, replacement if item.filename == entry else source.read(item.filename))
with zipfile.ZipFile(original) as source, zipfile.ZipFile(candidate) as target:
    assert source.namelist() == target.namelist()
    changed = [name for name in source.namelist() if source.read(name) != target.read(name)]
    assert changed == [entry], changed
    (classes / 'com/antshorttv/ai/GeminiAdapter.class').write_bytes(target.read(entry))
subprocess.run(['java', '-Xverify:all', '-Xmx128m', '-cp', classpath,
                'com.antshorttv.ai.VerifyGeminiNative'], check=True)


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            digest.update(block)
    return digest.hexdigest()


receipt = dict(original=str(original), original_sha256=sha(original), candidate=str(candidate),
               candidate_sha256=sha(candidate), changed_entries=changed, offline_probe='PASS')
(root / 'prepare.json').write_text(json.dumps(receipt, indent=2))
print(json.dumps(receipt))
