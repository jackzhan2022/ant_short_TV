import { execFileSync } from 'node:child_process';

const output = execFileSync('git', ['diff', '--name-only', 'HEAD'], {
  encoding: 'utf8',
});
const changed = output
  .split(/\r?\n/)
  .map((file) => file.trim())
  .filter(Boolean);

if (changed.length === 0) {
  console.log('No working-tree changes detected; running the full test suite.');
  execFileSync('npm', ['test'], { stdio: 'inherit' });
  process.exit(0);
}

const hasFrontend = changed.some((file) => file.startsWith('frontend/'));
const hasBackend = changed.some((file) => file.startsWith('backend/'));
const hasRootConfig = changed.some(
  (file) => !file.includes('/') || file.startsWith('.github/'),
);

const commands = [];
const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const mavenCommand = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
if (hasFrontend || hasRootConfig) commands.push([npmCommand, ['run', 'frontend:test']]);
if (hasBackend || hasRootConfig) {
  commands.push([mavenCommand, ['-f', 'backend/pom.xml', 'test']]);
}

if (commands.length === 0) {
  console.log('Changed files do not map to an application module; running the full test suite.');
  commands.push([npmCommand, ['test']]);
}

console.log(`Changed test scope: ${commands.map(([command]) => command).join(' + ')}`);
for (const [command, args] of commands) {
  if (process.platform === 'win32') {
    const commandLine = ['call', command, ...args].join(' ');
    execFileSync(process.env.ComSpec ?? 'cmd.exe', ['/d', '/s', '/c', commandLine], {
      stdio: 'inherit',
    });
  } else {
    execFileSync(command, args, { stdio: 'inherit' });
  }
}
