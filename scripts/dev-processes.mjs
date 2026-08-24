import { spawn, execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { dirname, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const stateFile = resolve(root, '.temp', 'dev-processes.json');
const mode = process.argv[2] ?? 'start';

function readState() {
  if (!existsSync(stateFile)) return null;
  try {
    return JSON.parse(readFileSync(stateFile, 'utf8'));
  } catch {
    return null;
  }
}

function isRunning(pid) {
  if (!Number.isInteger(pid) || pid <= 0) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function stopTree(pid) {
  if (!isRunning(pid)) return;
  if (process.platform === 'win32') {
    try {
      execFileSync('taskkill.exe', ['/pid', String(pid), '/t', '/f'], { stdio: 'ignore' });
    } catch (error) {
      if (isRunning(pid)) throw error;
    }
  } else {
    process.kill(-pid, 'SIGTERM');
  }
}

function stop() {
  const state = readState();
  for (const child of state?.children ?? []) {
    try {
      stopTree(child.pid);
    } catch {
      console.warn(`Unable to stop ${child.name} process ${child.pid}.`);
    }
  }
  rmSync(stateFile, { force: true });
  console.log('Development processes stopped.');
}

async function portAvailable(port) {
  return new Promise((resolveAvailability) => {
    const server = createServer();
    server.once('error', () => resolveAvailability(false));
    server.once('listening', () => server.close(() => resolveAvailability(true)));
    server.listen(port, '127.0.0.1');
  });
}

if (mode === 'stop') {
  stop();
  process.exit(0);
}

const state = readState();
const active = (state?.children ?? []).filter((child) => isRunning(child.pid));
if (mode === 'status') {
  if (active.length === 0) console.log('Development processes are not running.');
  for (const child of active) console.log(`${child.name}: running (PID ${child.pid})`);
  process.exit(0);
}

if (active.length > 0) {
  console.error('Development processes are already running. Use npm run dev:status or npm run dev:stop.');
  process.exit(1);
}
rmSync(stateFile, { force: true });

const occupied = [];
for (const port of [8000, 8080]) {
  if (!(await portAvailable(port))) occupied.push(port);
}
if (occupied.length > 0) {
  console.error(`Required port(s) already in use: ${occupied.join(', ')}. Stop the existing process before starting.`);
  process.exit(1);
}

const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const mvn = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
const java = process.platform === 'win32' ? 'java.exe' : 'java';

if (process.platform === 'win32') {
  execFileSync(process.env.ComSpec ?? 'cmd.exe', [
    '/d',
    '/s',
    '/c',
    'call mvn.cmd -f backend/pom.xml package -DskipTests',
  ], { cwd: root, stdio: 'inherit' });
} else {
  execFileSync(mvn, ['-f', 'backend/pom.xml', 'package', '-DskipTests'], {
    cwd: root,
    stdio: 'inherit',
  });
}

const backendJar = readdirSync(resolve(root, 'backend', 'target'))
  .find((name) => name.endsWith('.jar'));
if (!backendJar) {
  console.error('Backend JAR was not produced.');
  process.exit(1);
}

const definitions = [
  {
    name: 'backend',
    command: java,
    args: ['-jar', `backend/target/${backendJar}`],
  },
  { name: 'frontend', command: npm, args: ['--prefix', 'frontend', 'run', 'dev'] },
];
const children = definitions.map((definition) => ({
  ...definition,
  process: spawn(
    process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : definition.command,
    process.platform === 'win32'
      ? ['/d', '/s', '/c', ['call', definition.command, ...definition.args].join(' ')]
      : definition.args,
    {
    cwd: root,
    stdio: 'inherit',
    detached: process.platform !== 'win32',
    },
  ),
}));

mkdirSync(dirname(stateFile), { recursive: true });
writeFileSync(stateFile, JSON.stringify({
  ownerPid: process.pid,
  startedAt: new Date().toISOString(),
  children: children.map((child) => ({ name: child.name, pid: child.process.pid })),
}, null, 2));

let shuttingDown = false;
function shutdown(exitCode = 0) {
  if (shuttingDown) return;
  shuttingDown = true;
  stop();
  process.exit(exitCode);
}

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));
process.on('exit', () => rmSync(stateFile, { force: true }));
for (const child of children) {
  child.process.on('error', (error) => {
    console.error(`${child.name} failed to start: ${error.message}`);
    shutdown(1);
  });
  child.process.on('exit', (code) => {
    if (!shuttingDown) {
      console.error(`${child.name} exited with code ${code ?? 1}; stopping the other development process.`);
      shutdown(code ?? 1);
    }
  });
}

console.log('Frontend and backend started. Press Ctrl+C to stop both.');
