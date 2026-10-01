import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkFile, report } from './check-vrm.mjs';
import { tinyVrm } from './fixtures.mjs';

test('check-vrm: a model made to the brief passes', async () => {
  const result = await checkFile(await tinyVrm(), { author: 'me' });
  assert.deepEqual(result.errors, []);
  assert.match(report('rin.vrm', result), /Result: PASS/);
});

test('check-vrm: a VRoid sample is refused by name, whatever its licence fields say', async () => {
  const sample = await tinyVrm({ meta: { name: 'AvatarSample_O', authors: ['pixiv VRoid Project'] } });
  const { errors } = await checkFile(sample);
  assert.ok(errors.some((e) => /VRoid sample model/.test(e)), errors.join('\n'));
});

test('check-vrm: someone else as author fails when the user names themselves', async () => {
  const { errors } = await checkFile(await tinyVrm({ meta: { authors: ['someone'] } }), { author: 'Earthkodyai' });
  assert.ok(errors.some((e) => /meta\.authors/.test(e)), errors.join('\n'));
});

test('check-vrm: a rig without the pads hand\'s fingers or an arm fails, naming the bones', async () => {
  const { errors } = await checkFile(await tinyVrm({ dropBones: ['rightIndexProximal', 'leftLowerArm'] }));
  assert.ok(errors.some((e) => /rightIndexProximal/.test(e) && /leftLowerArm/.test(e)), errors.join('\n'));
});

test('check-vrm: the report says what to fix for each failure', async () => {
  const result = await checkFile(await tinyVrm({ meta: { allowRedistribution: true }, drop: ['Fcl_MTH_Fun'] }));
  const text = report('rin.vrm', result);
  assert.match(text, /FAIL {2}missing morphs Fcl_MTH_Fun/);
  assert.match(text, /FAIL {2}meta\.allowRedistribution/);
  assert.match(text, /Result: FAIL \(2\)/);
});
