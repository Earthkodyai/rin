import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { VRMAnimationLoaderPlugin, type VRMAnimation } from '@pixiv/three-vrm-animation';
import { buildVrma, poseAt, solveArm, validate, ARM_REST, SKELETON, type GestureDef } from '../scripts/vrma.mjs';
import { Clip, FADE_IN_S, FADE_OUT_S, GESTURES, GesturePlayer, envelope, isGesture } from './gesture';
import { MAX_OPEN, MouthPlayer, isMouthTrack, mouthAt, trackSeconds } from './mouth';
import { parseNative } from './bridge';
import table from './gestures.json';

async function parse(bytes: Uint8Array): Promise<VRMAnimation> {
  const loader = new GLTFLoader();
  loader.register((parser) => new VRMAnimationLoaderPlugin(parser));
  const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
  const gltf = await loader.parseAsync(buffer, '');
  return (gltf.userData.vrmAnimations as VRMAnimation[])[0];
}

/** Where the hand ends up for an arm channel, from the solved rotations and SKELETON's lengths. */
function handOf(side: 'left' | 'right', channel: number[]) {
  const sign = side === 'left' ? 1 : -1;
  const x = new THREE.Vector3(sign, 0, 0);
  const { upper, lower } = solveArm(side, channel);
  const a = SKELETON[`${side}LowerArm`][1][0] * sign;
  const b = SKELETON[`${side}Hand`][1][0] * sign;
  return x.clone().multiplyScalar(a).applyQuaternion(upper).add(x.clone().multiplyScalar(b).applyQuaternion(upper.clone().multiply(lower)));
}

describe('gesture keyframes (gestures.json)', () => {
  it('are the eight gestures from the plan (05a); idle breathing stays procedural in behaviour.ts', () => {
    expect(GESTURES).toEqual(['nod', 'shake', 'wave', 'clap', 'joy', 'yawn', 'stretch', 'pout']);
    expect(isGesture('wave')).toBe(true);
    expect(isGesture('dance')).toBe(false);
    expect(isGesture('toString')).toBe(false);
  });

  it('are all valid: keys from 0 to the duration, reachable hands, face weights in 0..1', () => {
    for (const [name, g] of Object.entries(table)) expect(validate(name, g as GestureDef)).toEqual([]);
  });

  it('start and end at the idle pose, so the crossfade in and out has nothing to hide', () => {
    const rest = poseAt({ keys: [{ t: 0, leftArm: 'rest', rightArm: 'rest' }] }, 0);
    const table_ = table as Record<string, GestureDef & { duration: number }>;
    for (const [name, g] of Object.entries(table_)) {
      for (const t of [0, g.duration]) {
        const pose = poseAt(g, t);
        for (const [bone, q] of Object.entries(pose)) {
          const idle = rest[bone] ?? new THREE.Quaternion();
          // Relaxed finger curl is the same at both ends; everything else returns to rest.
          if (/Thumb|Index|Middle|Ring|Little/.test(bone)) continue;
          expect(Math.abs(q.angleTo(idle)), `${name} ${bone} at t=${t}`).toBeLessThan(0.02);
        }
      }
    }
  });
});

describe('arm IK (scripts/vrma.mjs)', () => {
  it('puts the hand where the key says', () => {
    for (const [side, channel] of [
      ['right', [-0.1, 0.07, 0.07, -1, -0.8, -0.2, 0]],
      ['left', [-0.063, -0.02, 0.2, 0.3, -1, -0.3, 0.06]],
      ['left', ARM_REST.left],
    ] as const) {
      expect(handOf(side, [...channel]).distanceTo(new THREE.Vector3(...channel.slice(0, 3)))).toBeLessThan(1e-4);
    }
  });

  it('keeps the elbow a hinge: the forearm only bends in the upper arm frame, twist aside', () => {
    const { lower } = solveArm('left', [0.1, 0.1, 0.15, 0, -1, 0, 0]);
    const axis = new THREE.Vector3(lower.x, lower.y, lower.z).normalize();
    expect(Math.abs(axis.y)).toBeCloseTo(1, 5); // rotation about the rest bend axis only
  });

  it('flags a hand out of reach', () => {
    const g: GestureDef = { duration: 1, keys: [{ t: 0 }, { t: 1, leftArm: [0.5, 0, 0.3, 0, -1, 0, 0] }] };
    expect(validate('far', g).join()).toMatch(/out of reach/);
  });
});

describe('.vrma files', () => {
  it('load with three-vrm-animation: bones, face tracks and duration survive the round trip', async () => {
    const animation = await parse(buildVrma('yawn', table.yawn as GestureDef));
    expect(animation.duration).toBeCloseTo(table.yawn.duration, 5);
    expect([...animation.humanoidTracks.rotation.keys()]).toEqual(
      expect.arrayContaining(['head', 'neck', 'rightUpperArm', 'rightLowerArm', 'rightHand', 'leftShoulder']),
    );
    expect([...animation.expressionTracks.preset.keys()].sort()).toEqual(['blink', 'oh']);
    expect(animation.restHipsPosition.y).toBeGreaterThan(0.5); // a T-pose standing on the ground
  });

  it('play back the keyed pose (identity rest, so file rotations are the authored ones)', async () => {
    const animation = await parse(buildVrma('nod', table.nod as GestureDef));
    const clip = new Clip('nod', animation, false);
    const out = new Map();
    clip.sampleBones(0.22, out);
    const expected = poseAt(table.nod as GestureDef, 0.22).head;
    expect(out.get('head').angleTo(expected)).toBeLessThan(0.01);
  });

  it('keep custom expressions (smile) apart from VRM presets', async () => {
    const animation = await parse(buildVrma('wave', table.wave as GestureDef));
    expect([...animation.expressionTracks.custom.keys()]).toEqual(['smile']);
  });
});

describe('gesture player', () => {
  it('fades in and out within 05a crossfade times', () => {
    expect(FADE_IN_S).toBeGreaterThanOrEqual(0.2);
    expect(FADE_OUT_S).toBeLessThanOrEqual(0.4);
    expect(envelope(0, 2)).toBe(0);
    expect(envelope(FADE_IN_S, 2)).toBe(1);
    expect(envelope(1, 2)).toBe(1);
    expect(envelope(2 - FADE_OUT_S / 2, 2)).toBeCloseTo(0.5, 5);
    expect(envelope(2, 2)).toBe(0);
    expect(envelope(0.1, 0.4)).toBeGreaterThan(0); // shorter than both fades: still shows
  });

  it('crossfades: a new gesture takes over and the old one is gone within FADE_IN_S', async () => {
    const clips = new Map([
      ['nod', new Clip('nod', await parse(buildVrma('nod', table.nod as GestureDef)), false)],
      ['yawn', new Clip('yawn', await parse(buildVrma('yawn', table.yawn as GestureDef)), false)],
    ] as const);
    const player = new GesturePlayer(clips);
    expect(player.play('wave')).toBe(false); // not loaded
    expect(player.play('yawn')).toBe(true);
    for (let i = 0; i < 30; i++) player.update(1 / 30);
    expect(player.face().takeover).toBe(1);
    player.play('nod');
    expect(player.current).toBe('nod');
    for (let i = 0; i < Math.ceil(FADE_IN_S * 30) + 1; i++) player.update(1 / 30);
    expect(player.face().takeover).toBe(0); // yawn (the only one with a face) has faded out
  });
});

describe('mouth tracks', () => {
  const track = { fps: 10, f: '-0a9a9o5-0' };

  it('accept only the v1 format', () => {
    expect(isMouthTrack(track)).toBe(true);
    expect(isMouthTrack({ fps: 30, f: 'a' })).toBe(false); // odd length
    expect(isMouthTrack({ fps: 30, f: 'x5' })).toBe(false); // unknown shape
    expect(isMouthTrack({ fps: 0, f: '' })).toBe(false);
    expect(trackSeconds(track)).toBeCloseTo(0.5, 9);
  });

  it('interpolate between frames, so shapes crossfade', () => {
    expect(mouthAt(track, 0.1).aa).toBeCloseTo(1, 9);
    const mid = mouthAt(track, 0.25);
    expect(mid.aa).toBeCloseTo(0.5, 9);
    expect(mid.oh).toBeCloseTo((5 / 9) * 0.5, 9);
    expect(mouthAt(track, 0).aa).toBe(0);
  });

  it('play by the wall clock from when the audio started, then stop by themselves', () => {
    const player = new MouthPlayer();
    player.speak(track, 1000);
    expect(player.at(1000 + 150)?.aa).toBeCloseTo(MAX_OPEN, 9);
    expect(player.at(1000 + 600)).toBeNull();
    player.speak(track, 1000);
    player.hush();
    expect(player.at(1100)).toBeNull();
  });
});

describe('parseNative (task 2.4 messages)', () => {
  it('reads gesture, speak and hush, and drops malformed ones', () => {
    expect(parseNative('{"type":"gesture","name":"wave"}')).toEqual({ type: 'gesture', name: 'wave' });
    expect(parseNative('{"type":"gesture","name":"moonwalk"}')).toBeNull();
    expect(parseNative('{"type":"speak","mouth":{"v":1,"fps":30,"f":"a5-0"},"at":12}')).toEqual({
      type: 'speak',
      mouth: { fps: 30, f: 'a5-0' },
      at: 12,
    });
    expect(parseNative('{"type":"speak","mouth":{"fps":30,"f":"zz"},"at":12}')).toBeNull();
    expect(parseNative('{"type":"speak","mouth":{"fps":30,"f":"a5"}}')).toBeNull(); // no start time
    expect(parseNative('{"type":"hush"}')).toEqual({ type: 'hush' });
  });
});
