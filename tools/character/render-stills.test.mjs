import { test } from 'node:test';
import assert from 'node:assert/strict';
import { findBrowser, symmetricCrop } from './render-stills.mjs';

test('symmetricCrop keeps her centred, out to the far side plus a margin', () => {
  // Drawn columns 400..700 in a 1000-wide frame: the far side is 200 from the middle.
  assert.deepEqual(symmetricCrop(1000, 400, 700, 8), { left: 292, width: 416 });
  assert.deepEqual(symmetricCrop(1000, 450, 800, 8), { left: 192, width: 616 });
});

test('symmetricCrop never grows past the frame', () => {
  assert.deepEqual(symmetricCrop(1000, 0, 1000, 8), { left: 0, width: 1000 });
});

test('findBrowser takes an explicit path, else the first install that exists', () => {
  assert.equal(findBrowser('/x/chrome', 'linux', (p) => p === '/x/chrome'), '/x/chrome');
  assert.throws(() => findBrowser('/x/missing', 'linux', () => false), /not found/);
  assert.equal(findBrowser(undefined, 'linux', (p) => p === '/usr/bin/chromium'), '/usr/bin/chromium');
  assert.throws(() => findBrowser(undefined, 'linux', () => false), /RIN_BROWSER/);
});
