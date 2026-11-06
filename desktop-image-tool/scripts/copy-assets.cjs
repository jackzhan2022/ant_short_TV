const fs = require('node:fs');
for (const name of [
  'index.html',
  'styles.css',
  'vector.html',
  'vector-page.mjs',
]) {
  fs.copyFileSync(`src/${name}`, `dist/${name}`);
}
fs.mkdirSync('dist/vector-vendor', { recursive: true });
for (const name of [
  'transformers.min.js',
  'ort-wasm-simd-threaded.jsep.mjs',
  'ort-wasm-simd-threaded.jsep.wasm',
]) {
  fs.copyFileSync(
    `node_modules/@huggingface/transformers/dist/${name}`,
    `dist/vector-vendor/${name}`,
  );
}
fs.copyFileSync(
  'node_modules/@huggingface/transformers/LICENSE',
  'dist/vector-vendor/TRANSFORMERS-LICENSE',
);
for (const name of ['NOTICE.txt', 'ONNX-RUNTIME-LICENSE']) {
  fs.copyFileSync(`assets/vector/${name}`, `dist/vector-vendor/${name}`);
}
for (const name of ['icon.png', 'icon.ico']) {
  fs.copyFileSync(`assets/${name}`, `dist/${name}`);
}
