import { test } from 'node:test';
import assert from 'node:assert/strict';
import { findImage, prompt } from './tiles.mjs';

const big = 'A'.repeat(2000);

test('findImage finds the picture in either answer shape, and ignores short or non-image data', () => {
  assert.equal(findImage({ output_image: { data: big, mime_type: 'image/png' } }), big);
  assert.equal(findImage({ candidates: [{ content: { parts: [{ text: 'hi' }, { inlineData: { mimeType: 'image/png', data: big } }] } }] }), big);
  assert.equal(findImage({ outputs: [{ data: big, mime_type: 'application/json' }] }), null);
  assert.equal(findImage({ data: 'tiny' }), null);
});

test('every prompt carries the shared style, so the tiles match', () => {
  const spec = { style: 'Pastel style.', tiles: [] };
  assert.equal(prompt(spec, { subject: 'Cups.' }), 'Pastel style.\n\nSubject: Cups.');
});
