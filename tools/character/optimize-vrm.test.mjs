import { test } from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import { readGlb, writeGlb } from './glb.mjs';
import { checkModel, REQUIRED_EXPRESSIONS, REQUIRED_META } from './check.mjs';
import { png, tinyVrm } from './fixtures.mjs';
import { imageRoles, optimize } from './optimize-vrm.mjs';


test('GLB round trip keeps the JSON and every view, 4-byte aligned', async () => {
  const bytes = await tinyVrm();
  const { json, views } = readGlb(bytes);
  assert.deepEqual([...views[0]], [1, 2, 3, 4, 5, 6]);
  for (const v of json.bufferViews) assert.equal(v.byteOffset % 4, 0);
  const again = readGlb(writeGlb(json, views));
  assert.deepEqual(again.json, json);
  assert.equal(bytes.byteLength % 4, 0);
});

test('image roles: base colour and MToon shade are colour, the normal map is normal, the thumbnail has none', async () => {
  const { json } = readGlb(await tinyVrm());
  const roles = imageRoles(json);
  assert.equal(roles.get(0), 'color');
  assert.equal(roles.get(1), 'normal');
  assert.equal(roles.has(2), false);
});

test('an image used as colour and as data keeps the colour encoding', () => {
  const json = {
    textures: [{ source: 0 }],
    materials: [{ extensions: { VRMC_materials_mtoon: { shadingShiftTexture: { index: 0 }, matcapTexture: { index: 0 } } } }],
  };
  assert.equal(imageRoles(json).get(0), 'color');
});

test('checks: a complete model passes', async () => {
  const { json } = readGlb(await tinyVrm());
  assert.deepEqual(checkModel(json, { fileBytes: 1000, images: [] }).errors, []);
});

test('checks: missing expressions, smile morphs, wrong licence and size all fail with a reason', async () => {
  const { json } = readGlb(await tinyVrm({ drop: ['aa', 'Fcl_BRW_Fun'], meta: { allowRedistribution: true } }));
  const { errors } = checkModel(json, { fileBytes: 16 * 1048576, images: [{ index: 0, width: 4096, height: 4096 }] });
  assert.equal(errors.length, 5);
  assert.match(errors.join('\n'), /missing expressions: aa/);
  assert.match(errors.join('\n'), /Fcl_BRW_Fun/);
  assert.match(errors.join('\n'), /allowRedistribution/);
  assert.match(errors.join('\n'), /16\.0 MB/);
  assert.match(errors.join('\n'), /larger than 2048/);
});

test('checks: a VRM 0.x export is told to re-export as 1.0', () => {
  const { errors } = checkModel({ extensions: { VRM: {} } }, { fileBytes: 0, images: [] });
  assert.match(errors[0], /VRM 1\.0/);
});

test('optimize: textures become KTX2 through KHR_texture_basisu, the thumbnail shrinks, VRM data is untouched', async () => {
  const input = await tinyVrm();
  const { output, check, report } = await optimize(input, { mode: 'etc1s' });
  assert.deepEqual(check.errors, []);
  const { json, views } = readGlb(output);
  assert.deepEqual(json.extensionsRequired, ['KHR_texture_basisu']);
  for (const i of [0, 1]) {
    assert.equal(json.images[i].mimeType, 'image/ktx2');
    assert.deepEqual(json.textures[i], { extensions: { KHR_texture_basisu: { source: i } } });
    assert.deepEqual([...views[json.images[i].bufferView].subarray(1, 7)], [...Buffer.from('KTX 20')]); // KTX2 magic
  }
  assert.equal(report[1].format, 'uastc'); // normal maps stay UASTC in etc1s mode
  assert.equal(json.images[2].mimeType, 'image/png');
  assert.deepEqual(json.textures[2], { source: 2 });
  assert.equal((await sharp(views[json.images[2].bufferView]).metadata()).width, 256);
  assert.deepEqual(json.extensions.VRMC_vrm, readGlb(input).json.extensions.VRMC_vrm);
  assert.deepEqual([...views[0]], [1, 2, 3, 4, 5, 6]);
});

test('optimize: a model that fails the checks stops before encoding', async () => {
  const { output, check } = await optimize(await tinyVrm({ meta: { modification: 'allowModification' } }));
  assert.equal(output, null);
  assert.match(check.errors[0], /meta\.modification/);
});
