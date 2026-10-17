'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'miniprogram');
const staging = path.join(__dirname, 'miniprogram-dev-wx3b0031992c5a642d-0.1.0');
const files = ['app.js', 'app.json', 'app.wxss', 'config/api.js', 'services/api.js', 'services/playback.js', ...['index', 'detail'].flatMap(page => ['js', 'json', 'wxml', 'wxss'].map(ext => `pages/${page}/index.${ext}`))];
if (fs.existsSync(staging)) throw new Error('staging已存在，停止以避免覆盖');
const before = Object.fromEntries([...files, 'project.config.json'].map(file => [file, crypto.createHash('sha256').update(fs.readFileSync(path.join(source, file))).digest('hex')]));
fs.mkdirSync(staging);
for (const file of files) {
  const src = path.join(source, file);
  if (!fs.lstatSync(src).isFile()) throw new Error('白名单文件不是普通文件');
  const dst = path.join(staging, file);
  fs.mkdirSync(path.dirname(dst), { recursive: true });
  fs.copyFileSync(src, dst, fs.constants.COPYFILE_EXCL);
}
const project = JSON.parse(fs.readFileSync(path.join(source, 'project.config.json'), 'utf8'));
project.appid = 'wx3b0031992c5a642d';
project.projectname = 'ant-short-tv-dev-template';
project.description = '基础剧库选集播放模板';
project.packOptions.ignore = [
  { type: 'folder', value: 'tests' }, { type: 'folder', value: 'private' },
  { type: 'folder', value: 'node_modules' }, { type: 'file', value: 'package.json' },
  { type: 'file', value: 'package-lock.json' }, { type: 'file', value: 'project.private.config.json' },
  { type: 'suffix', value: '.key' }, { type: 'suffix', value: '.pem' }, { type: 'suffix', value: '.env' }
];
fs.writeFileSync(path.join(staging, 'project.config.json'), JSON.stringify(project, null, 2) + '\n', { flag: 'wx' });
fs.writeFileSync(path.join(staging, 'ext.json'), JSON.stringify({ extEnable: false, directCommit: false }, null, 2) + '\n', { flag: 'wx' });
for (const [file, hash] of Object.entries(before)) {
  if (crypto.createHash('sha256').update(fs.readFileSync(path.join(source, file))).digest('hex') !== hash) throw new Error('原工程发生变化');
}
console.log(JSON.stringify({ staging, files: [...files, 'project.config.json', 'ext.json'], originalUnchanged: true, baseURL: require(path.join(staging, 'config/api.js')).baseURL }, null, 2));
