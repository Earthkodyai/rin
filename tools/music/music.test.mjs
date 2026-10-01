import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CREDITS_PER_MINUTE, estimate, loopFilter } from './music.mjs';

test('estimate counts every take of every theme, or only the ones asked for', () => {
  const spec = { lengthMs: 60000, takes: 3, themes: [{ id: 'a' }, { id: 'b' }, { id: 'c' }, { id: 'd' }] };
  assert.deepEqual(estimate(spec), { themes: 4, takes: 12, minutes: 12, credits: 12 * CREDITS_PER_MINUTE });
  assert.equal(estimate(spec, ['b']).credits, 3 * CREDITS_PER_MINUTE);
});

test('the loop starts with the seam and leaves out what the seam already used', () => {
  const f = loopFilter(60, 2);
  assert.match(f, /atrim=58\.000:60\.000/); // the tail that fades out
  assert.match(f, /atrim=0:2,/); // the head that fades in
  assert.match(f, /atrim=2:58\.000/); // the body between them
  assert.match(f, /\[seam\]\[body\]concat/);
});
