import { test } from 'node:test';
import assert from 'node:assert/strict';
import { best, check, voiced, wordCount } from './pack-qa.mjs';
import { clips, outPath, request } from './pack.mjs';

const RATE = 8000;
/** A tone of `s` seconds at amplitude `a`, or silence when a = 0. */
const tone = (s, a = 0.3, f = 200) => Float32Array.from({ length: Math.round(s * RATE) }, (_, i) => a * Math.sin((2 * Math.PI * f * i) / RATE));
const join = (...parts) => {
  const out = new Float32Array(parts.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const p of parts) { out.set(p, at); at += p.length; }
  return out;
};

test('tags are not words', () => {
  assert.equal(wordCount('[warmly] I am ready to start the day.'), 7);
  assert.equal(wordCount('Mmh… morning~'), 2);
});

test('voiced time skips the silence at both ends and finds the longest gap', () => {
  const v = voiced(join(tone(0.3, 0), tone(1), tone(0.6, 0), tone(0.5), tone(0.2, 0)), RATE);
  assert.ok(Math.abs(v.start - 0.3) < 0.03 && Math.abs(v.dur - 2.1) < 0.05, JSON.stringify(v));
  assert.ok(Math.abs(v.gap - 0.6) < 0.03);
});

test('a clean take has no flags', () => {
  const clean = join(tone(2), tone(0.2, 0));
  assert.deepEqual(check(clean, RATE, 'one two three four five').flags, []);
});

test('a click, clipping, a rushed read and a stall are each flagged', () => {
  const end = tone(0.2, 0); // every take below ends in quiet, so none is cut off
  const click = join(tone(2), end);
  click[4000] = 0.9;
  assert.deepEqual(check(click, RATE, 'one two three four five').flags, ['crackle']);
  assert.ok(check(join(tone(2, 1.2), end), RATE, 'one two three four five').flags.includes('clipped'));
  assert.deepEqual(check(join(tone(1), end), RATE, 'one two three four five six seven eight').flags, ['pace']);
  assert.deepEqual(check(join(tone(1), tone(1.5, 0), tone(1), end), RATE, 'one two three four five').flags, ['gap']);
  assert.deepEqual(check(tone(1, 0), RATE, 'hi').flags, ['empty']);
});

test('the best take has the fewest retake flags, then the fewest flags, then comes first', () => {
  const t = (flags) => ({ flags });
  assert.equal(best([t(['crackle']), t([]), t([])]), 1);
  assert.equal(best([t(['cut']), t(['crackle', 'gap'])]), 1);
  assert.equal(best([t(['gap']), t(['pace'])]), 0);
});

test('a take that stops mid-sound is cut; one that fades out is not', () => {
  const words = 'one two three four five';
  assert.ok(check(tone(2), RATE, words).flags.includes('cut'));
  const tail = tone(0.2);
  const fade = join(tone(2), tail.map((x, i) => x * (1 - i / tail.length) ** 4), tone(0.1, 0));
  assert.ok(!check(fade, RATE, words).flags.includes('cut'));
});

test('the pack covers every line and Repeat sentence once, in the paths the app reads', () => {
  const all = clips();
  assert.equal(new Set(all.map((c) => c.id)).size, all.length);
  const r = all.find((c) => c.id === 'R01');
  assert.equal(outPath(r), 'repeat/R01');
  assert.ok(request(r).startsWith('[warmly] '));
  for (const c of all) assert.ok(!request(c).includes('~'), c.id);
});
