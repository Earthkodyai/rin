// Builds VRM Animation (.vrma) files from the keyframes in src/gestures.json (task 2.4). Rin's gestures are our own
// work, written as keyframes in this repo, so the public repo carries no third-party animation (plan phase 2). A
// .vrma is a glTF binary whose nodes form a T-posed humanoid; the VRMC_vrm_animation extension names the bones, and
// each expression is a node whose translation.x is its weight. Any VRMA player can play these, and a better file
// (motion capture, Blender) can replace one later without code changes.
//
// Keys are authored in body space: +X is Rin's left, +Y up, +Z forward (toward the camera). Arms are posed by where
// the hand goes, solved with two-bone IK against SKELETON. Every frame, hands, forearms and elbows are then pushed out
// of her torso from the waist up (clearArm), so a path between keys bends around her body instead of through it; below
// the waist her hands may rest against skirts and thighs, so idle arms hang close to her (task 2.5). Both use
// src/body.json, measured from the model by tools/character/check-gestures.mjs --write-body: for a new model, measure
// it, rebuild and check again.
import * as THREE from 'three';
import body from '../src/body.json' with { type: 'json' };

export const FPS = 30;

/** The bones a .vrma carries -> [parent, offset from the parent in metres], T-pose (the VRoid sample's, as a fallback). */
const TABLE = {
  hips: [null, [0, 0.723, 0]],
  spine: ['hips', [0, 0.045, 0]],
  chest: ['spine', [0, 0.094, 0]],
  upperChest: ['chest', [0, 0.092, 0]],
  neck: ['upperChest', [0, 0.117, 0]],
  head: ['neck', [0, 0.062, 0]],
  ...side('left', 1),
  ...side('right', -1),
};

/** TABLE with the model's measured offsets (src/body.json) wherever it has the bone under the same parent. */
export const SKELETON = Object.fromEntries(
  Object.entries(TABLE).map(([bone, [parent, offset]]) => {
    const measured = body.skeleton[bone];
    return [bone, [parent, measured && measured[0] === parent ? measured[1] : offset]];
  }),
);

function side(s, x) {
  const bones = {
    [`${s}Shoulder`]: ['upperChest', [0.019 * x, 0.094, 0]],
    [`${s}UpperArm`]: [`${s}Shoulder`, [0.068 * x, 0, 0]],
    [`${s}LowerArm`]: [`${s}UpperArm`, [0.185 * x, 0, 0]],
    [`${s}Hand`]: [`${s}LowerArm`, [0.178 * x, 0, 0]],
    [`${s}ThumbMetacarpal`]: [`${s}Hand`, [0.002 * x, -0.007, 0.013]],
    [`${s}ThumbProximal`]: [`${s}ThumbMetacarpal`, [0.036 * x, 0, 0]],
    [`${s}ThumbDistal`]: [`${s}ThumbProximal`, [0.022 * x, 0, 0]],
  };
  const fingers = { Index: [0.05, 0.005, 0.015], Middle: [0.052, 0.005, 0], Ring: [0.05, 0.004, -0.012], Little: [0.045, 0.002, -0.024] };
  for (const [finger, [fx, fy, fz]] of Object.entries(fingers)) {
    bones[`${s}${finger}Proximal`] = [`${s}Hand`, [fx * x, fy, fz]];
    bones[`${s}${finger}Intermediate`] = [`${s}${finger}Proximal`, [0.025 * x, 0, 0]];
    bones[`${s}${finger}Distal`] = [`${s}${finger}Intermediate`, [0.017 * x, 0, 0]];
  }
  return bones;
}

/** Bones posed by Euler angles [x, y, z] (radians, XYZ): x > 0 bends forward/looks down, y > 0 turns to her left. */
export const EULER_BONES = ['spine', 'chest', 'upperChest', 'neck', 'head', 'leftShoulder', 'rightShoulder', 'leftHand', 'rightHand'];
const SIDES = ['left', 'right'];
const FINGERS = ['Thumb', 'Index', 'Middle', 'Ring', 'Little'];

/**
 * Arm channel: [hand x, y, z (relative to the upper-arm joint), pole x, y, z (where the elbow points), twist
 * (forearm roll, radians)]. The resting arm matches Behaviour's idle pose (behaviour.ts): down, elbow back.
 */
export const ARM_REST = { left: [0.106, -0.346, 0.01, 0, -0.3, -1, 0], right: [-0.106, -0.346, 0.01, 0, -0.3, -1, 0] };
/** Finger curl 0 (flat) .. 1 (fist); a relaxed hand is a little curled. */
export const CURL_REST = 0.15;

function channelDefault(name) {
  if (name === 'leftArm' || name === 'rightArm') return ARM_REST[name.slice(0, -3)];
  if (name === 'leftCurl' || name === 'rightCurl') return [CURL_REST];
  return [0, 0, 0];
}

/** Every channel a gesture's keys use, filled forward so each key has all of them; "rest" is the idle value. */
function fillKeys(keys) {
  const names = new Set(keys.flatMap((k) => Object.keys(k).filter((n) => n !== 't')));
  const last = Object.fromEntries([...names].map((n) => [n, channelDefault(n)]));
  return {
    names: [...names],
    keys: keys.map((k) => {
      for (const n of names) if (k[n] !== undefined) last[n] = k[n] === 'rest' ? channelDefault(n) : [k[n]].flat();
      return { t: k.t, values: Object.fromEntries([...names].map((n) => [n, last[n]])) };
    }),
  };
}

/**
 * Hermite interpolation through the keys with Catmull-Rom tangents (smooth through the middle keys), flat at the
 * first and last key so the gesture eases out of and back into the idle pose.
 */
export function interpolate(times, values, t) {
  const n = times.length;
  if (t <= times[0]) return values[0].slice();
  if (t >= times[n - 1]) return values[n - 1].slice();
  let i = 0;
  while (t > times[i + 1]) i++;
  const [t0, t1] = [times[i], times[i + 1]];
  const h = t1 - t0;
  const u = (t - t0) / h;
  const tangent = (j) =>
    j === 0 || j === n - 1
      ? values[j].map(() => 0)
      : values[j].map((_, c) => (values[j + 1][c] - values[j - 1][c]) / (times[j + 1] - times[j - 1]));
  const [m0, m1] = [tangent(i), tangent(i + 1)];
  const h00 = 2 * u ** 3 - 3 * u ** 2 + 1;
  const h10 = u ** 3 - 2 * u ** 2 + u;
  const h01 = -2 * u ** 3 + 3 * u ** 2;
  const h11 = u ** 3 - u ** 2;
  return values[i].map((p0, c) => h00 * p0 + h10 * h * m0[c] + h01 * values[i + 1][c] + h11 * h * m1[c]);
}

const v3 = (a) => new THREE.Vector3(a[0], a[1], a[2]);

/**
 * Two-bone IK for one arm. The upper arm's frame is built so its rest bend direction (+Z, forward: the elbow flexes
 * that way with palms down in T-pose) lies in the plane of shoulder, elbow and hand; the elbow is then a pure hinge.
 * @returns {{ upper: THREE.Quaternion, lower: THREE.Quaternion, reach: number }} local rotations; `reach` is how far
 *   the hand ended from the target (0 unless the target was out of reach)
 */
export function solveArm(s, channel) {
  const sign = s === 'left' ? 1 : -1;
  const a = SKELETON[`${s}LowerArm`][1][0] * sign;
  const b = SKELETON[`${s}Hand`][1][0] * sign;
  const target = v3(channel);
  const pole = v3(channel.slice(3, 6));
  const twist = channel[6];

  const dist = THREE.MathUtils.clamp(target.length(), Math.abs(a - b) + 1e-4, a + b - 1e-4);
  const u = target.clone().normalize();
  let v = pole.clone().sub(u.clone().multiplyScalar(pole.dot(u)));
  if (v.lengthSq() < 1e-8) v = new THREE.Vector3(0, -1, 0).sub(u.clone().multiplyScalar(-u.y));
  v.normalize();
  const alpha = Math.acos(THREE.MathUtils.clamp((a * a + dist * dist - b * b) / (2 * a * dist), -1, 1));
  const elbow = u.clone().multiplyScalar(Math.cos(alpha) * a).add(v.clone().multiplyScalar(Math.sin(alpha) * a));
  const hand = u.clone().multiplyScalar(dist);
  const upperDir = elbow.clone().normalize();
  const lowerDir = hand.clone().sub(elbow).normalize();

  // Rest frame: x along the arm, z the bend direction, y = z × x. Target frame: the same built from the solved arm.
  const xr = new THREE.Vector3(sign, 0, 0);
  const zr = new THREE.Vector3(0, 0, 1);
  const yr = zr.clone().cross(xr);
  const xt = upperDir;
  let zt = lowerDir.clone().sub(xt.clone().multiplyScalar(lowerDir.dot(xt)));
  if (zt.lengthSq() < 1e-8) zt = v.clone().multiplyScalar(-1); // straight arm: the elbow points at the pole
  zt.normalize();
  const yt = zt.clone().cross(xt);
  const rest = new THREE.Matrix4().makeBasis(xr, yr, zr);
  const posed = new THREE.Matrix4().makeBasis(xt, yt, zt);
  const upper = new THREE.Quaternion().setFromRotationMatrix(posed.multiply(rest.transpose()));

  const bend = Math.acos(THREE.MathUtils.clamp(upperDir.dot(lowerDir), -1, 1));
  const lower = new THREE.Quaternion()
    .setFromUnitVectors(xr, xr.clone().multiplyScalar(Math.cos(bend)).add(zr.clone().multiplyScalar(Math.sin(bend))))
    .multiply(new THREE.Quaternion().setFromAxisAngle(xr, twist));
  return { upper, lower, reach: Math.abs(target.length() - dist), elbow, hand };
}

// Her arms' thickness, sleeves included, as measured (src/body.json `arm`, metres): what must stay outside her torso.
// Defaults (rough adult sizes) stand in until a model has been measured.
const { upperR: UPPER_R, lowerR: LOWER_R, handR: HAND_R } = body.arm ?? { upperR: 0.03, lowerR: 0.035, handR: 0.02 };
/** The top of the upper arm meets the torso at rest; only what lies further out along the arm is kept clear. */
const SHOULDER_SKIP_M = 0.08;
const SLICES = new Map(body.slices.map((s) => [Math.round(s.y / body.sliceM), s.hull]));

/** Where a bone's joint is in the T-pose, from SKELETON. */
export function restPosition(bone) {
  const p = new THREE.Vector3();
  for (let b = bone; b; b = SKELETON[b][0]) p.add(v3(SKELETON[b][1]));
  return p;
}

/** The horizontal move [x, z] that takes a point out of one torso slice's outline grown by r; [0, 0] if clear. */
function pushFromSlice(hull, p, r) {
  const n = hull ? hull.length / 2 : 0;
  if (n < 3) return [0, 0];
  let inside = Infinity;
  let normal = [0, 0];
  for (let i = 0; i < n; i++) {
    const [ax, az, bx, bz] = [hull[i * 2], hull[i * 2 + 1], hull[((i + 1) % n) * 2], hull[((i + 1) % n) * 2 + 1]];
    const len = Math.hypot(bx - ax, bz - az);
    if (len === 0) continue;
    const side = ((bx - ax) * (p.z - az) - (bz - az) * (p.x - ax)) / len; // > 0: inside (counter-clockwise hull)
    if (side < inside) [inside, normal] = [side, [(bz - az) / len, -(bx - ax) / len]]; // outward
  }
  return inside > -r ? [normal[0] * (inside + r), normal[1] * (inside + r)] : [0, 0];
}

/**
 * The horizontal move [x, z] that takes a point (T-pose world space) out of her torso, grown by r. The two slices
 * around its height are blended, so the push changes smoothly as an arm moves up or down.
 */
export function pushOut(p, r) {
  // From the waist up the lowest slice counts in full; a ball whose top is below the waist fades out over one slice,
  // so arms that reach down past her waist move smoothly.
  const h = body.sliceM;
  const fade = THREE.MathUtils.clamp(1 - (body.waistY - (p.y + r)) / h, 0, 1);
  if (fade === 0) return [0, 0];
  const f = Math.max(p.y, body.waistY + h / 2) / h - 0.5; // slice centres sit half a slice above their bottoms
  const i = Math.floor(f);
  const w = f - i;
  const a = pushFromSlice(SLICES.get(i), p, r);
  const b = pushFromSlice(SLICES.get(i + 1), p, r);
  return [(a[0] * (1 - w) + b[0] * w) * fade, (a[1] * (1 - w) + b[1] * w) * fade];
}

const longer = (a, b) => (Math.hypot(b[0], b[1]) > Math.hypot(a[0], a[1]) ? b : a);

/**
 * Her hand as points (T-pose world space): the wrist at `wrist`, then every finger joint and fingertip, by forward
 * kinematics with the hand turned by `q` (its rotation in the world) and the fingers curled by `curl`. A flat palm
 * is thin, so points follow its real shape where one ball around it would push her hands far off her body.
 */
function handPoints(s, wrist, q, curl) {
  const turns = {};
  curlBones(s, curl, turns);
  const points = [wrist.clone()];
  // Joints, and points between them: across the palm (wrist to knuckles) a ball per joint would leave gaps.
  const segment = (from, to, steps) => {
    for (let i = 1; i <= steps; i++) points.push(from.clone().lerp(to, i / steps));
  };
  for (const f of FINGERS) {
    const p = wrist.clone();
    const r = q.clone();
    for (const seg of f === 'Thumb' ? ['Metacarpal', 'Proximal', 'Distal'] : ['Proximal', 'Intermediate', 'Distal']) {
      const bone = `${s}${f}${seg}`;
      const from = p.clone();
      p.add(v3(SKELETON[bone][1]).applyQuaternion(r));
      segment(from, p, seg === 'Proximal' && f !== 'Thumb' ? 3 : 2);
      if (turns[bone]) r.multiply(turns[bone]);
    }
    segment(p.clone(), p.clone().add(v3(SKELETON[`${s}${f}Distal`][1]).applyQuaternion(r)), 2); // to the tip
  }
  return points;
}

/**
 * An arm channel moved so her hand, fingers, forearm and elbow stay outside her torso: points near the hand move
 * the hand target, points near the elbow move the elbow (the pole), a few rounds until nothing is inside. `grip` is
 * the wrist's own turn (Euler, as in a key) and how curled the fingers are at that moment, since both move the
 * fingertips, and `shoulder` the shoulder's turn (a gesture adds it, which carries the whole arm). The twist is kept.
 * Deterministic and continuous in its inputs, so an interpolated path stays smooth.
 */
export function clearArm(s, channel, grip = { turn: [0, 0, 0], curl: 0, shoulder: [0, 0, 0] }) {
  const pivot = restPosition(`${s}Shoulder`);
  const shoulder = new THREE.Quaternion().setFromEuler(new THREE.Euler(...(grip.shoulder ?? [0, 0, 0]), 'XYZ'));
  const unturn = shoulder.clone().invert();
  const joint = restPosition(`${s}UpperArm`).sub(pivot).applyQuaternion(shoulder).add(pivot);
  const world = (v) => v.clone().applyQuaternion(shoulder).add(joint); // arm space (from the joint) -> world
  const target = v3(channel);
  const pole = v3(channel.slice(3, 6));
  const wristTurn = new THREE.Quaternion().setFromEuler(new THREE.Euler(...grip.turn, 'XYZ'));
  for (let round = 0; round < 16; round++) {
    const solved = solveArm(s, [...target.toArray(), ...pole.toArray(), channel[6]]);
    const { elbow, hand } = solved;
    const e = world(elbow);
    const h = world(hand);
    // The hand by its joints; the forearm and upper arm as balls along them, as thick as measured (sleeves included).
    let moveHand = [0, 0];
    const q = shoulder.clone().multiply(solved.upper).multiply(solved.lower).multiply(wristTurn);
    for (const point of handPoints(s, h, q, grip.curl)) moveHand = longer(moveHand, pushOut(point, HAND_R));
    moveHand = longer(moveHand, pushOut(h, Math.max(HAND_R, LOWER_R))); // the wrist wears the cuff
    let moveElbow = pushOut(e, LOWER_R);
    for (const t of [0.25, 0.5, 0.75]) {
      const push = pushOut(e.clone().lerp(h, t), LOWER_R);
      if (t > 0.5) moveHand = longer(moveHand, push);
      else moveElbow = longer(moveElbow, push);
    }
    // The elbow alone cannot always swing the upper arm clear (hands held across her chest pin it in front of her), so
    // an upper arm deep in her body also takes the hand, up to half as far. A light touch (under 2 cm, as a resting
    // arm against her side) takes nothing, and the share ramps up smoothly from there.
    const upperLength = elbow.length();
    for (const t of [SHOULDER_SKIP_M / upperLength, 0.65, 0.85]) {
      const push = pushOut(joint.clone().lerp(e, t), UPPER_R);
      moveElbow = longer(moveElbow, push);
      const share = 0.5 * THREE.MathUtils.clamp((Math.hypot(...push) - 0.02) / 0.02, 0, 1);
      moveHand = longer(moveHand, [push[0] * share, push[1] * share]);
    }
    if (Math.hypot(...moveHand) < 1e-4 && Math.hypot(...moveElbow) < 1e-4) break;
    // Moves are horizontal in the world; the key lives in the turned shoulder's space.
    target.add(new THREE.Vector3(moveHand[0], 0, moveHand[1]).applyQuaternion(unturn));
    pole.copy(elbow).add(new THREE.Vector3(moveElbow[0], 0, moveElbow[1]).applyQuaternion(unturn)); // where it was pushed
  }
  return [...target.toArray(), ...pole.toArray(), channel[6]];
}

/** The idle arm (behaviour.ts) as bone rotations: ARM_REST, cleared of her body, so gestures start and end on it. */
export function restArm(s) {
  return solveArm(s, clearArm(s, ARM_REST[s], { turn: [0, 0, 0], curl: CURL_REST, shoulder: [0, 0, 0] }));
}

/** The idle fingers (behaviour.ts): a little curled, as gestures start and end. Bone -> local rotation. */
export function restFingers(s) {
  const out = {};
  curlBones(s, CURL_REST, out);
  return out;
}

/** Fingers (and thumb) curled by `curl`, 0 flat .. 1 fist, as bone -> local rotation (the cup shuffle's grip). */
export function curlFingers(s, curl) {
  const out = {};
  curlBones(s, curl, out);
  return out;
}

/** Curl angles per segment (radians) for a fist; the thumb folds less and across the palm. */
const CURL = { Proximal: 1.3, Intermediate: 1.5, Distal: 1.0 };

function curlBones(s, curl, out) {
  const sign = s === 'left' ? -1 : 1; // toward the palm (-Y) from ±X
  for (const f of FINGERS) {
    if (f === 'Thumb') {
      out[`${s}ThumbProximal`] = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, -sign * 0.5 * curl, 0));
      out[`${s}ThumbDistal`] = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, -sign * 0.6 * curl, 0));
      continue;
    }
    for (const [seg, angle] of Object.entries(CURL)) {
      out[`${s}${f}${seg}`] = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, 0, sign * angle * curl));
    }
  }
}

/** The pose at time t: bone -> local quaternion, for the bones this gesture moves. */
export function poseAt(gesture, t) {
  const { names, keys } = fillKeys(gesture.keys);
  const times = keys.map((k) => k.t);
  const out = {};
  const values = Object.fromEntries(names.map((n) => [n, interpolate(times, keys.map((k) => k.values[n]), t)]));
  for (const name of names) {
    const value = values[name];
    if (EULER_BONES.includes(name)) {
      out[name] = new THREE.Quaternion().setFromEuler(new THREE.Euler(value[0], value[1], value[2], 'XYZ'));
    } else if (name === 'leftArm' || name === 'rightArm') {
      const s = name.slice(0, -3);
      // Without its own channels, a hand keeps the idle pose: straight wrist, fingers a little curled.
      const hand = {
        turn: values[`${s}Hand`] ?? [0, 0, 0],
        curl: values[`${s}Curl`]?.[0] ?? CURL_REST,
        shoulder: values[`${s}Shoulder`] ?? [0, 0, 0],
      };
      const { upper, lower } = solveArm(s, clearArm(s, value, hand));
      out[`${s}UpperArm`] = upper;
      out[`${s}LowerArm`] = lower;
    } else if (name === 'leftCurl' || name === 'rightCurl') {
      curlBones(name.slice(0, -4), THREE.MathUtils.clamp(value[0], 0, 1), out);
    } else {
      throw new Error(`unknown channel "${name}"`);
    }
  }
  return out;
}

/** Face keys are [t, weight] pairs, eased between keys (each key is a held shape, not a point to pass through). */
export function faceAt(keys, t) {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 0; i < keys.length - 1; i++) {
    const [t0, w0] = keys[i];
    const [t1, w1] = keys[i + 1];
    if (t > t1) continue;
    const u = (t - t0) / (t1 - t0);
    return w0 + (w1 - w0) * u * u * (3 - 2 * u);
  }
  return keys[keys.length - 1][1];
}

export const PRESET_EXPRESSIONS = new Set([
  'happy', 'angry', 'sad', 'relaxed', 'surprised', 'aa', 'ih', 'ou', 'ee', 'oh',
  'blink', 'blinkLeft', 'blinkRight', 'lookUp', 'lookDown', 'lookLeft', 'lookRight', 'neutral',
]);

/** Checks a gesture definition; returns a list of problems (empty when fine). */
export function validate(name, g) {
  const problems = [];
  if (!(g.duration > 0)) problems.push(`${name}: duration must be > 0`);
  const times = g.keys.map((k) => k.t);
  if (times[0] !== 0) problems.push(`${name}: the first key must be at t=0`);
  if (Math.abs(times.at(-1) - g.duration) > 1e-9) problems.push(`${name}: the last key must be at the duration`);
  if (times.some((t, i) => i > 0 && t <= times[i - 1])) problems.push(`${name}: key times must increase`);
  for (const [expr, keys] of Object.entries(g.face ?? {})) {
    if (keys[0][0] !== 0 || keys.at(-1)[0] !== g.duration) problems.push(`${name}: face ${expr} must span 0..duration`);
    if (keys.some(([, w]) => w < 0 || w > 1)) problems.push(`${name}: face ${expr} weights must be 0..1`);
  }
  for (const k of g.keys) {
    for (const [n, value] of Object.entries(k)) {
      if (n === 't' || value === 'rest') continue;
      const arm = n === 'leftArm' || n === 'rightArm';
      if (arm && value.length !== 7) problems.push(`${name}: ${n} needs 7 numbers`);
      if (!arm && !EULER_BONES.includes(n) && !n.endsWith('Curl')) problems.push(`${name}: unknown channel ${n}`);
      if (arm) {
        const { reach } = solveArm(n.slice(0, -3), value);
        if (reach > 0.02) problems.push(`${name}: ${n} at t=${k.t} is ${reach.toFixed(2)} m out of reach`);
      }
    }
  }
  return problems;
}

/** The .vrma bytes for one gesture. */
export function buildVrma(name, gesture) {
  const problems = validate(name, gesture);
  if (problems.length) throw new Error(problems.join('\n'));
  const frames = Math.round(gesture.duration * FPS) + 1;
  const times = Float32Array.from({ length: frames }, (_, i) => Math.min(i / FPS, gesture.duration));
  const poses = [...times].map((t) => poseAt(gesture, t));
  const bones = Object.keys(poses[0]);

  const nodes = [];
  const index = {};
  for (const [bone, [parent, offset]] of Object.entries(SKELETON)) {
    index[bone] = nodes.length;
    nodes.push({ name: bone, translation: offset });
    if (parent) (nodes[index[parent]].children ??= []).push(index[bone]);
  }
  const expressionNodes = {};
  for (const expr of Object.keys(gesture.face ?? {})) {
    expressionNodes[expr] = nodes.length;
    nodes.push({ name: `expression.${expr}` });
  }

  const chunks = [];
  const accessors = [];
  const add = (array, type, extra = {}) => {
    chunks.push(Buffer.from(array.buffer, array.byteOffset, array.byteLength));
    accessors.push({ bufferView: chunks.length - 1, componentType: 5126, count: array.length / { SCALAR: 1, VEC3: 3, VEC4: 4 }[type], type, ...extra });
    return accessors.length - 1;
  };
  const input = add(times, 'SCALAR', { min: [0], max: [gesture.duration] });
  const samplers = [];
  const channels = [];
  for (const bone of bones) {
    const q = new Float32Array(frames * 4);
    let prev = null;
    poses.forEach((pose, i) => {
      const cur = pose[bone].clone();
      if (prev && prev.dot(cur) < 0) cur.set(-cur.x, -cur.y, -cur.z, -cur.w); // same hemisphere: no spins
      q.set([cur.x, cur.y, cur.z, cur.w], i * 4);
      prev = cur;
    });
    samplers.push({ input, output: add(q, 'VEC4'), interpolation: 'LINEAR' });
    channels.push({ sampler: samplers.length - 1, target: { node: index[bone], path: 'rotation' } });
  }
  for (const [expr, keys] of Object.entries(gesture.face ?? {})) {
    const w = new Float32Array(frames * 3);
    times.forEach((t, i) => (w[i * 3] = faceAt(keys, t)));
    samplers.push({ input, output: add(w, 'VEC3'), interpolation: 'LINEAR' });
    channels.push({ sampler: samplers.length - 1, target: { node: expressionNodes[expr], path: 'translation' } });
  }

  const humanBones = Object.fromEntries(Object.keys(SKELETON).map((b) => [b, { node: index[b] }]));
  const preset = {};
  const custom = {};
  for (const [expr, node] of Object.entries(expressionNodes)) (PRESET_EXPRESSIONS.has(expr) ? preset : custom)[expr] = { node };
  const json = {
    asset: { version: '2.0', generator: 'RinAlarm web/character/scripts/vrma.mjs' },
    extensionsUsed: ['VRMC_vrm_animation'],
    extensions: {
      VRMC_vrm_animation: {
        specVersion: '1.0',
        humanoid: { humanBones },
        ...(Object.keys(expressionNodes).length ? { expressions: { preset, custom } } : {}),
      },
    },
    scene: 0,
    scenes: [{ nodes: [index.hips, ...Object.values(expressionNodes)] }],
    nodes,
    animations: [{ name, samplers, channels }],
    accessors,
    bufferViews: [],
    buffers: [{ byteLength: 0 }],
  };
  return writeGlb(json, chunks);
}

const pad4 = (n) => (n + 3) & ~3;

function writeGlb(json, chunks) {
  let offset = 0;
  json.bufferViews = chunks.map((c) => {
    const view = { buffer: 0, byteOffset: offset, byteLength: c.byteLength };
    offset = pad4(offset + c.byteLength);
    return view;
  });
  const bin = Buffer.alloc(offset);
  json.bufferViews.forEach((v, i) => bin.set(chunks[i], v.byteOffset));
  json.buffers[0].byteLength = bin.length;
  let text = Buffer.from(JSON.stringify(json), 'utf8');
  text = Buffer.concat([text, Buffer.alloc(pad4(text.length) - text.length, 0x20)]);
  const header = Buffer.alloc(12);
  header.writeUInt32LE(0x46546c67, 0);
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(12 + 8 + text.length + 8 + bin.length, 8);
  const chunkHeader = (length, type) => {
    const h = Buffer.alloc(8);
    h.writeUInt32LE(length, 0);
    h.writeUInt32LE(type, 4);
    return h;
  };
  return Buffer.concat([header, chunkHeader(text.length, 0x4e4f534a), text, chunkHeader(bin.length, 0x004e4942), bin]);
}
