const { test } = require('node:test');
const assert = require('node:assert/strict');
let ImageQueue;
try {
  ({ ImageQueue } = require('../dist/queue'));
} catch {}

function fixture(limit = 5, waiting = []) {
  assert.equal(
    typeof ImageQueue,
    'function',
    'image queue must be implemented',
  );
  const starts = [],
    finish = new Map();
  const queue = new ImageQueue(
    (id) =>
      new Promise((resolve) => {
        starts.push(id);
        finish.set(id, resolve);
      }),
    () => {},
    { limit, waiting },
  );
  return { queue, starts, finish };
}
const flush = () => new Promise((resolve) => setImmediate(resolve));

test('all entry points share five slots and only a settled worker releases a slot', async () => {
  const { queue, starts, finish } = fixture();
  queue.add(Array.from({ length: 12 }, (_, i) => String(i)));
  assert.equal(starts.length, 5);
  assert.equal(queue.state().waiting.length, 7);
  queue.add(['other-project']);
  assert.equal(starts.length, 5);
  finish.get('0')();
  await flush();
  assert.equal(starts.length, 6);
  assert.equal(queue.state().running.length, 5);
  assert.equal(queue.state().waiting.length, 7);
});

test('pause keeps active tasks but prevents replacement until resumed', async () => {
  const { queue, finish } = fixture(2);
  queue.add(['a', 'b', 'c']);
  queue.pause();
  finish.get('a')();
  finish.get('b')();
  await flush();
  assert.deepEqual(queue.state().running, []);
  assert.deepEqual(queue.state().waiting, ['c']);
  queue.resume();
  assert.deepEqual(queue.state().running, ['c']);
});

test('lower limit does not abort active work and waits before dispatching more', async () => {
  const { queue, starts, finish } = fixture();
  queue.add(['a', 'b', 'c', 'd', 'e', 'f']);
  queue.setLimit(2);
  finish.get('a')();
  finish.get('b')();
  finish.get('c')();
  await flush();
  assert.equal(starts.length, 5);
  finish.get('d')();
  await flush();
  assert.equal(starts.length, 6);
  assert.equal(queue.state().running.length, 2);
});

test('restore leaves unsubmitted work paused and queued cancellations never run', () => {
  const { queue, starts } = fixture(5, ['unsubmitted']);
  assert.equal(starts.length, 0);
  assert.equal(queue.state().paused, true);
  assert.equal(queue.cancel('unsubmitted'), true);
  queue.resume();
  assert.equal(starts.length, 0);
  assert.deepEqual(queue.state().waiting, []);
});

test('invalid limits cannot exceed hard ceiling, and rejected work frees its slot', async () => {
  const { queue } = fixture();
  for (const limit of [0, 6, 1.5, NaN])
    assert.throws(() => queue.setLimit(limit));
  const starts = [];
  const rejecting = new ImageQueue(
    async (id) => {
      starts.push(id);
      throw new Error('provider failed');
    },
    () => {},
    { limit: 1 },
  );
  rejecting.add(['a', 'b']);
  await flush();
  await flush();
  assert.deepEqual(starts, ['a', 'b']);
  assert.deepEqual(rejecting.state().running, []);
});

test('explicit pause on an empty queue is retained when new work arrives', () => {
  const { queue, starts } = fixture();
  queue.pause();
  queue.add(['new']);
  assert.equal(starts.length, 0);
  queue.resume();
  assert.equal(starts.length, 1);
});
