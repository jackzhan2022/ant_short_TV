'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const output = process.stdout.write.bind(process.stdout);
function clean(value) {
  return String(value ?? '').replace(/-----BEGIN[\s\S]*?-----END[^\r\n]*-----/g, '[已隐藏密钥]')
    .replace(/((?:access_token|authorization|signature|privateKey|token)["'\s]*[:=]["'\s]*)([^\s,}\]]+)/gi, '$1[已隐藏]')
    .replace(/[A-Za-z0-9+/=_-]{100,}/g, '[已隐藏长凭据]').slice(0, 2500);
}
function report(data) { output(JSON.stringify(data) + '\n'); }
async function main() {
  const projectPath = path.join(__dirname, 'miniprogram-dev-wx3b0031992c5a642d-0.1.0');
  const config = JSON.parse(fs.readFileSync(path.join(projectPath, 'project.config.json'), 'utf8'));
  const ext = JSON.parse(fs.readFileSync(path.join(projectPath, 'ext.json'), 'utf8'));
  assert.equal(config.appid, 'wx3b0031992c5a642d');
  assert.equal(ext.extEnable, false);
  assert.equal(ext.directCommit, false);
  assert.equal(ext.extAppid, undefined);
  const allowed = new Set(['app.js', 'app.json', 'app.wxss', 'config/api.js', 'services/api.js', 'services/playback.js', 'project.config.json', 'ext.json', ...['index', 'detail'].flatMap(page => ['js', 'json', 'wxml', 'wxss'].map(suffix => `pages/${page}/index.${suffix}`))]);
  function check(dir, prefix = '') {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const relative = prefix + entry.name;
      assert.equal(entry.isSymbolicLink(), false);
      if (entry.isDirectory()) check(path.join(dir, entry.name), relative + '/');
      else { assert.ok(allowed.delete(relative), '发现非白名单文件'); }
    }
  }
  check(projectPath);
  assert.equal(allowed.size, 0);
  assert.equal(require(path.join(projectPath, 'config/api.js')).baseURL, 'https://antv.aixmax.cn');
  // 不输出CI内部对象、鉴权信息或原始日志，仅报告受限结果字段。
  process.stdout.write = () => true;
  process.stderr.write = () => true;
  const ciRoot = 'C:/Users/jiafeng.zhan/.workbuddy/binaries/node/workspaces/ant-short-tv-miniprogram-ci/node_modules/miniprogram-ci';
  const ci = require(ciRoot);
  report({ phase: '开始上传', appid: config.appid, version: '0.1.0', ciVersion: require(path.join(ciRoot, 'package.json')).version });
  const project = new ci.Project({
    appid: config.appid, type: 'miniProgram', projectPath,
    privateKeyPath: 'D:/private.wx3b0031992c5a642d.key',
    ignores: ['tests/**/*', 'private/**/*', '**/*.key', '**/*.pem', '**/*.env', 'node_modules/**/*', 'project.private.config.json', 'package*.json']
  });
  const result = await ci.upload({ project, version: '0.1.0', desc: '基础剧库选集播放模板', robot: 1,
    setting: { es6: true, minify: true }, onProgressUpdate() {} });
  report({ phase: 'CI上传成功', appid: config.appid, version: '0.1.0', platformDraftConfirmed: false,
    subPackageInfo: Array.isArray(result?.subPackageInfo) ? result.subPackageInfo.map(item => ({ name: clean(item.name), size: item.size })) : [] });
}
main().catch(error => {
  report({ phase: '上传失败', code: clean(error?.code ?? error?.errCode ?? error?.errcode), message: clean(error?.message ?? error?.errmsg ?? '未知错误') });
  process.exitCode = 1;
});
