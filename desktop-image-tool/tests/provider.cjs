const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {
  generateImage,
  validateDraft,
  decodeImage,
} = require('../dist/provider');
const { BASE_URL } = require('../dist/shared');
const png = fs.readFileSync('assets/icon.png');
const reference = {
  name: 'icon.png',
  dataUrl: `data:image/png;base64,${png.toString('base64')}`,
};
const draft = {
  prompt: '画一只熊',
  model: 'gpt-image-2',
  size: '1024x1024',
  quality: 'auto',
  format: 'png',
  references: [],
};

test('fixed host, bearer key and Responses image generation match the existing adapter', async () => {
  let called = 0;
  const result = await generateImage(
    draft,
    'test-key',
    'gpt-6-astra',
    new AbortController().signal,
    async (url, init) => {
      called++;
      assert.equal(url, `${BASE_URL}/responses`);
      assert.equal(init.headers.Authorization, 'Bearer test-key');
      assert.match(init.headers['Idempotency-Key'], /^[a-f0-9-]{36}$/);
      assert.equal(init.redirect, 'error');
      assert.deepEqual(JSON.parse(init.body), {
        model: 'gpt-6-astra',
        input: [
          { role: 'user', content: [{ type: 'input_text', text: '画一只熊' }] },
        ],
        tools: [
          {
            type: 'image_generation',
            model: 'gpt-image-2',
            size: '1024x1024',
            quality: 'auto',
            output_format: 'png',
            action: 'generate',
          },
        ],
      });
      return Response.json({
        id: 'resp-1',
        output: [
          {
            type: 'image_generation_call',
            output_format: 'png',
            result: png.toString('base64'),
          },
        ],
      });
    },
  );
  assert.equal(called, 1);
  assert.equal(result.responseId, 'resp-1');
  assert.deepEqual(result.images[0].bytes, png);
});

test('references switch to edit while retaining prompt and image order', async () => {
  await generateImage(
    { ...draft, references: [reference] },
    'test',
    'custom-model',
    new AbortController().signal,
    async (_url, init) => {
      const body = JSON.parse(init.body);
      assert.equal(body.tools[0].action, 'edit');
      assert.equal(body.model, 'custom-model');
      assert.deepEqual(body.input[0].content[1], {
        type: 'input_image',
        image_url: reference.dataUrl,
      });
      return Response.json({
        output: [
          { type: 'image_generation_call', result: png.toString('base64') },
        ],
      });
    },
  );
});

test('server errors never echo credentials and are never automatically retried', async () => {
  let called = 0;
  await assert.rejects(
    generateImage(
      draft,
      'secret-key',
      'model',
      new AbortController().signal,
      async () => {
        called++;
        return new Response('secret-key and private diagnostics', {
          status: 401,
        });
      },
    ),
    (error) =>
      error.message.includes('Key 无效') &&
      !error.message.includes('secret-key'),
  );
  assert.equal(called, 1);
});

test('missing image output, bad JSON, invalid image bytes are failures', async () => {
  for (const body of [
    'not json',
    '{}',
    '{"output":[]}',
    '{"output":[{"type":"image_generation_call","result":"YWJj"}]}',
  ]) {
    await assert.rejects(
      generateImage(
        draft,
        'test',
        'model',
        new AbortController().signal,
        async () => new Response(body),
      ),
    );
  }
});

test('cancel signal reaches transport, without a second request', async () => {
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(
    generateImage(
      draft,
      'test',
      'model',
      controller.signal,
      async (_url, init) => {
        assert.equal(init.signal, controller.signal);
        init.signal.throwIfAborted();
      },
    ),
    { name: 'AbortError' },
  );
});

test('main-process validation rejects forged parameters, oversized and invalid reference data', () => {
  assert.equal(
    validateDraft({ ...draft, references: [reference] }).references.length,
    1,
  );
  for (const patch of [
    { prompt: '' },
    { prompt: 'a'.repeat(2001) },
    { model: '../path' },
    { size: '999x1' },
    { format: 'svg' },
    { references: Array(9).fill(reference) },
    { references: [{ name: 'bad', dataUrl: 'data:image/png;base64,YWJj' }] },
  ]) {
    assert.throws(() => validateDraft({ ...draft, ...patch }));
  }
  assert.throws(() => decodeImage(reference.dataUrl, 1));
});
