import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const mvn = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';

function run(command, args) {
  if (process.platform === 'win32') {
    const commandLine = ['call', command, ...args].join(' ');
    execFileSync(process.env.ComSpec ?? 'cmd.exe', ['/d', '/s', '/c', commandLine], {
      cwd: root,
      stdio: 'inherit',
    });
    return;
  }
  execFileSync(command, args, { cwd: root, stdio: 'inherit' });
}

function copyDirectory(source, destination) {
  mkdirSync(destination, { recursive: true });
  for (const entry of readdirSync(source, { withFileTypes: true })) {
    const sourcePath = join(source, entry.name);
    const destinationPath = join(destination, entry.name);
    if (entry.isDirectory()) copyDirectory(sourcePath, destinationPath);
    else if (entry.isFile()) copyFileSync(sourcePath, destinationPath);
  }
}

run(npm, ['run', 'frontend:build']);
run(mvn, ['-f', 'backend/pom.xml', 'clean', 'package', '-DskipTests']);

const jarDirectory = resolve(root, 'backend', 'target');
const jars = readdirSync(jarDirectory)
  .filter((name) => name.endsWith('.jar') && !name.startsWith('original-'))
  .map((name) => resolve(jarDirectory, name))
  .sort((left, right) => statSync(right).mtimeMs - statSync(left).mtimeMs);
if (jars.length === 0) throw new Error('Backend JAR was not produced.');

const commit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
const output = resolve(root, '.release', `${commit.slice(0, 8)}-${timestamp}`);
mkdirSync(resolve(output, 'backend'), { recursive: true });
copyDirectory(resolve(root, 'frontend', 'dist'), resolve(output, 'frontend'));
copyFileSync(jars[0], resolve(output, 'backend', 'ant-short-tv-backend.jar'));

writeFileSync(resolve(output, 'manifest.json'), JSON.stringify({
  commit,
  builtAt: new Date().toISOString(),
  backendSource: basename(jars[0]),
  frontend: 'frontend/',
  backend: 'backend/ant-short-tv-backend.jar',
}, null, 2));

console.log(`Release bundle created at ${output}`);
