import { test } from 'node:test';
import assert from 'node:assert/strict';
import { findBrowser, symmetricCrop, handCrop } from './render-stills.mjs';

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

test('handCrop puts the fingertip at the bottom centre and keeps the forearm to the top edge', () => {
  // Drawn columns 100..400 in a 600 x 800 render, fingertip at (300, 700).
  const crop = handCrop(600, 800, [300, 700], 100, 400, 8);
  assert.deepEqual(crop, { left: 92, top: 0, width: 416, height: 708 });
  assert.equal(crop.left + crop.width / 2, 300);
  // Never past the render's edges: a tip near the side narrows the crop, a tip near the bottom stops at it.
  assert.deepEqual(handCrop(600, 800, [40, 796], 0, 300, 8), { left: 0, top: 0, width: 80, height: 800 });
});
