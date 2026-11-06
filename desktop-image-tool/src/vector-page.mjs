import { env, pipeline } from './vendor/transformers.min.js';

env.allowRemoteModels = false;
env.allowLocalModels = true;
env.useBrowserCache = false;
env.useFSCache = false;
env.useFS = false;
env.localModelPath = 'antv-vector://local/models/';
env.backends.onnx.wasm.wasmPaths = 'antv-vector://local/vendor/';
env.backends.onnx.wasm.numThreads = 1;
env.backends.onnx.wasm.proxy = false;
let extractor;
window.vectorRuntime.onRequest(async ({ id, texts }) => {
  try {
    extractor ??= await pipeline('feature-extraction', 'bge-small-zh-v1.5', {
      local_files_only: true,
      dtype: 'q8',
      device: 'wasm',
    });
    const output = await extractor(texts, {
      pooling: 'cls',
      normalize: true,
      truncation: true,
      max_length: 128,
    });
    window.vectorRuntime.result({ id, vectors: output.tolist() });
  } catch {
    extractor = undefined;
    window.vectorRuntime.result({
      id,
      error: '本地向量模型加载或计算失败，请重新开始；模型文件会保留。',
    });
  }
});
window.vectorRuntime.ready();
