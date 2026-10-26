"""Prepare a candidate from the exact deployed JAR; never changes the running application."""
import hashlib,json,pathlib,shutil,subprocess,sys,zipfile
root=pathlib.Path(__file__).resolve().parent
original=pathlib.Path('/opt/antv/current/backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar').resolve()
state=root/'prepare.json'
assert not state.exists(), 'Use a new preparation directory'
runtime=root/'runtime'; classes=runtime/'classes'; libs=runtime/'lib'
classes.mkdir(parents=True); libs.mkdir()
with zipfile.ZipFile(original) as z:
 for n in z.namelist():
  if n.startswith('BOOT-INF/classes/') and n.endswith('.class'):
   p=classes/n.removeprefix('BOOT-INF/classes/');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
  elif n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):
   (libs/pathlib.Path(n).name).write_bytes(z.read(n))
cp=str(classes)+':'+str(libs/'*')
subprocess.run(['javac','-cp',cp,'-d',str(classes),str(root/'VolcengineProviderCodes.java'),str(root/'PatchArkSupplier.java'),str(root/'VerifyArkSupplier.java')],check=True)
subprocess.run(['java','-cp',cp,'PatchArkSupplier',str(classes)],check=True)
subprocess.run(['java','-Xverify:all','-Xmx128m','-cp',cp,'VerifyArkSupplier'],check=True)
changed=['ai/AiModelRouter','video/AiVideoTaskService','freecreation/FreeCreationService','freecreation/FreeCreationExecutionHandler']
if (classes/'com/antshorttv/video/VideoReferenceDiagnostics.class').exists():changed.append('video/VideoReferenceDiagnostics')
replacements={'BOOT-INF/classes/com/antshorttv/'+n+'.class':(classes/('com/antshorttv/'+n+'.class')).read_bytes() for n in changed}
replacements['BOOT-INF/classes/com/antshorttv/ai/VolcengineProviderCodes.class']=(classes/'com/antshorttv/ai/VolcengineProviderCodes.class').read_bytes()
replacements['BOOT-INF/classes/db/migration/V155__volcengine_xinji_supplier.sql']=(root/'V155__volcengine_xinji_supplier.sql').read_bytes()
candidate=root/'candidate.jar'
with zipfile.ZipFile(original) as source,zipfile.ZipFile(candidate,'w') as target:
 for item in source.infolist():target.writestr(item,replacements.get(item.filename,source.read(item.filename)))
 for name,data in replacements.items():
  if name not in source.namelist():target.writestr(name,data)
with zipfile.ZipFile(original) as a,zipfile.ZipFile(candidate) as b:
 unexpected=[n for n in a.namelist() if n not in replacements and a.read(n)!=b.read(n)]
 assert not unexpected,unexpected
 assert set(b.namelist())-set(a.namelist())==set(replacements)-set(a.namelist())
 # Extract again from the candidate to ensure the actual packaged classes pass verification.
 for name in replacements:
  if name.endswith('.class'):(classes/name.removeprefix('BOOT-INF/classes/')).write_bytes(b.read(name))
subprocess.run(['java','-Xverify:all','-Xmx128m','-cp',cp,'VerifyArkSupplier'],check=True)
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block)
 return h.hexdigest()
receipt={'original':str(original),'original_sha256':sha(original),'candidate':str(candidate),'candidate_sha256':sha(candidate),'changed_entries':sorted(replacements),'unchanged_entries_verified':True,'offline_probe':'PASS'}
state.write_text(json.dumps(receipt,indent=2));print(json.dumps(receipt))
