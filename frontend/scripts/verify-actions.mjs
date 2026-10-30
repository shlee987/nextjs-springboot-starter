import assert from 'node:assert/strict';
import { createActionScope } from '../src/lib/action-scope.ts';

const scope = createActionScope();
const first = Promise.withResolvers();
let calls = 0;
const one = scope.run('save:100', async () => {
  calls++;
  await first.promise;
  return 1;
});
assert.equal(scope.isRunning('save:100'), true);
assert.deepEqual(
  await scope.run('save:100', () => {
    calls++;
  }),
  { status: 'ignored' },
);
assert.equal(calls, 1);
assert.deepEqual(await scope.run('save:200', () => 2), { status: 'completed', value: 2 });
first.resolve();
assert.deepEqual(await one, { status: 'completed', value: 1 });
assert.equal(scope.isRunning('save:100'), false);
const failure = new Error('expected');
await assert.rejects(
  scope.run('sync', () => {
    throw failure;
  }),
  (error) => error === failure,
);
assert.equal(scope.isRunning('sync'), false);
await assert.rejects(
  scope.run('async', async () => {
    throw failure;
  }),
  (error) => error === failure,
);
assert.equal(scope.isRunning('async'), false);
const cancel = Promise.withResolvers();
const cancelled = scope.run('cancel', () => cancel.promise);
scope.cancel('cancel');
assert.equal(scope.isRunning('cancel'), true);
assert.deepEqual(await scope.run('cancel', () => {}), { status: 'ignored' });
cancel.resolve();
assert.deepEqual(await cancelled, { status: 'cancelled' });
assert.equal(scope.isRunning('cancel'), false);
const old = Promise.withResolvers();
const oldTask = scope.run('save:100', () => old.promise);
scope.dispose();
const nextScope = createActionScope();
const next = Promise.withResolvers();
const nextTask = nextScope.run('save:100', () => next.promise);
old.resolve();
assert.deepEqual(await oldTask, { status: 'cancelled' });
assert.equal(nextScope.isRunning('save:100'), true);
next.resolve();
await nextTask;
assert.equal(nextScope.isRunning('save:100'), false);
const strict = createActionScope();
const release = strict.retain();
release();
const releaseAgain = strict.retain();
await Promise.resolve();
assert.deepEqual(await strict.run('strict', () => 1), { status: 'completed', value: 1 });
releaseAgain();
await Promise.resolve();
assert.deepEqual(await strict.run('closed', () => 1), { status: 'cancelled' });
let generation = 1;
const guarded = createActionScope(() => generation === 1);
const late = Promise.withResolvers();
const guardedTask = guarded.run('guarded', () => late.promise);
generation = 2;
assert.deepEqual(await guarded.run('new-click-before-render', () => 1), { status: 'cancelled' });
late.resolve('old value');
assert.deepEqual(await guardedTask, { status: 'cancelled' });
const detached = createActionScope();
const detach = detached.retain();
const beforeCleanup = Promise.withResolvers();
const detachedTask = detached.run('detach', () => beforeCleanup.promise);
beforeCleanup.resolve(1);
detach();
assert.deepEqual(await detachedTask, { status: 'cancelled' });
console.log(
  'PASS action scope: immediate duplicate, shared key, parallel keys, sync/async failure, actual cancellation, old finally, scope disposal, StrictMode replay',
);
