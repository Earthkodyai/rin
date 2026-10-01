// Rin's character page: loads the VRM the app chose, renders it with capped pixel ratio and frame rate, and reports
// to Kotlin through the bridge. Nothing here is on the ring path; if this page fails, the app shows a still image.
//
// Query parameters (set by CharacterView.kt):
//   model=model/dev.vrm   path relative to the page, required
//   fps=30                frame-rate cap (S2: the display runs at 120 Hz, far more than an idle character needs)
//   pr=2                  pixel-ratio cap (S2: native DPR 3.25 costs ~45 MB more GPU memory for no visible gain)
//   t0=<epoch ms>         native timestamp taken before the WebView was created
//   mood=cheerful         starting mood (emotion.ts), shown from the first frame without a blend
//   intensity=1           starting mood intensity, 0..1
//   frame=full            head to toe (the ring screen, task 3.1); frame=waist: waist up (UX.2, the home panel);
//                         default: the strip's head and shoulders
//   gesture=wave          (desktop preview) play this gesture once loaded, and again every 4 s
//   cups=demo             (desktop preview) the cup shuffle (task 3.4) on a loop, as the app would drive it
//   still                 still-image mode for tools/character/render-stills.mjs: no loop, no bridge; window.rinStill

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { KTX2Loader } from 'three/addons/loaders/KTX2Loader.js';
import { VRM, VRMLoaderPlugin, VRMUtils, type VRMHumanBoneName } from '@pixiv/three-vrm';
import { PROTOCOL, frameStats, onNativeMessage, send, type ModelInfo } from './bridge';
import { Behaviour } from './behaviour';
import { MOODS, isMood, isTap, type Mood } from './emotion';
import { GESTURES, GesturePlayer, isGesture, loadGestures, type Gesture } from './gesture';
import type { MouthTrack } from './mouth';
import { addVroidSmile } from './vroid';
import { keepArmsOffSkirt } from './skirt';
import { BodyCheck, type Depth } from './inspect';
import { CupScene } from './cupscene';
import { actLength, afterSwaps, smooth, type CupsAct, type Swap } from './cups';

const q = new URLSearchParams(location.search);
const num = (key: string, fallback: number) => {
  const value = Number(q.get(key));
  return q.has(key) && Number.isFinite(value) && value > 0 ? value : fallback;
};
const modelPath = q.get('model');
/** The lights' default share of the original rig (see lightScale; the user picked 0.7 on the phone, 2026-10-01). */
const LIGHT_SCALE = 0.7;
let fpsCap = num('fps', 30);
const prCap = num('pr', 2);
const t0 = q.has('t0') ? Number(q.get('t0')) : null;
let mood: Mood = isMood(q.get('mood')) ? (q.get('mood') as Mood) : 'relieved';
let intensity = Math.min(num('intensity', 1), 1);
const fullBody = q.get('frame') === 'full';
const waistUp = q.get('frame') === 'waist';
/**
 * How bright her lights are, as a share of the original rig (directional pi + ambient 0.4 pi). That rig washed the
 * user's Rin's cream skin out to white (2026-10-01; VRoid Studio shows it shaded). `light=` overrides it to compare.
 */
const lightScale = num('light', LIGHT_SCALE);

const fail = (e: unknown) =>
  send({ v: PROTOCOL, type: 'error', message: String((e as Error)?.stack ?? e).slice(0, 2000) });
addEventListener('error', (e) => fail(e.error ?? e.message));
addEventListener('unhandledrejection', (e) => fail(e.reason));

// Transparent canvas: the Compose screen behind it provides the background, in light and dark themes.
const stillMode = q.has('still');
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
const pixelRatio = Math.min(devicePixelRatio, prCap);
renderer.setPixelRatio(pixelRatio);
renderer.setSize(innerWidth, innerHeight);
renderer.setClearColor(0x000000, 0);
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(30, innerWidth / innerHeight, 0.1, 20);
const light = new THREE.DirectionalLight(0xffffff, Math.PI * lightScale);
light.position.set(1, 1.5, 1.5);
const ambient = new THREE.AmbientLight(0xffffff, 0.4 * Math.PI * lightScale);
scene.add(light, ambient);

/** The top of the model (hair included); framing hangs from it, so any model (the sample, Rin) sits the same way. */
let topY = 1.55;

/**
 * Head and shoulders in a wide view (the strip above the alarm list), upper body in a tall one, waist up with
 * `frame=waist` (the home panel), head to toe with `frame=full` (the ring screen, where clap and pout show). The visible height at the model decides the distance, so
 * she fills the view whatever its pixel size. The top margin keeps the hair inside the frame through breathing and
 * the tap nod (2.1 cropped it, framing from the head bone); full body leaves room under her feet for a stretch.
 */
function frame() {
  camera.aspect = innerWidth / innerHeight;
  const wide = camera.aspect > 1;
  const margin = fullBody ? 0.06 : waistUp ? WAIST_MARGIN : wide ? 0.03 : 0.08;
  // metres at the model
  const visibleHeight = fullBody ? topY + 2 * margin : waistUp ? WAIST_HEIGHT : wide ? 0.44 : 0.95;
  const distance = visibleHeight / 2 / Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
  const target = topY + margin - visibleHeight / 2;
  baseView.position.set(0, target + (fullBody ? 0 : 0.03), distance);
  baseView.target.set(0, target, 0);
  if (cups) tableView = fitView(cups.framePoints(topY + 0.03), TABLE_PITCH);
  aim();
  cupsReported = -1; // the tap zones move with the view
}

/**
 * Waist up (UX.2): the panel where she stands beside the home screen's controls, half her body so the busy screen
 * keeps room (the user, 2026-10-01). Metres at the model from her hair down past her waist, whatever the view's shape.
 */
const WAIST_HEIGHT = 0.72;
const WAIST_MARGIN = 0.05;

/** The cup table (task 3.4), built with her; an act that arrives before she loads waits in `queuedAct`. */
let cups: CupScene | null = null;
let queuedAct: CupsAct | null | undefined;
/** What the app was last told about the table: -1 nothing yet (or the view moved), 0 away, 1 in view. */
let cupsReported = -1;
/** Desktop preview: shows one moment of the act (ms after it starts) instead of playing it. */
let cupsFrozenAt: number | null = null;
/** Her usual framing, and the cup table's (task 3.4); the camera blends between them as the table comes and goes. */
const baseView = { position: new THREE.Vector3(), target: new THREE.Vector3() };
let tableView: typeof baseView | null = null;
/** How far the table view looks down, so the cups never hide one another and a lifted cup shows the ball. */
const TABLE_PITCH = THREE.MathUtils.degToRad(22);

function aim() {
  const e = tableView && cups ? smooth(cups.shown) : 0;
  const view = tableView ?? baseView;
  camera.position.lerpVectors(baseView.position, view.position, e);
  camera.lookAt(new THREE.Vector3().lerpVectors(baseView.target, view.target, e));
  camera.updateProjectionMatrix();
}

/**
 * A view looking down by `pitch` that fits every point with a margin, centred on them: the camera backs off until the
 * farthest point is inside, then shifts so the points sit in the middle, three times over.
 */
function fitView(points: THREE.Vector3[], pitch: number) {
  const dir = new THREE.Vector3(0, -Math.sin(pitch), -Math.cos(pitch));
  const eye = new THREE.PerspectiveCamera(camera.fov, camera.aspect, 0.05, 20);
  const center = new THREE.Box3().setFromPoints(points).getCenter(new THREE.Vector3());
  const place = (d: number) => {
    eye.position.copy(center).addScaledVector(dir, -d);
    eye.lookAt(center);
    eye.updateMatrixWorld(true);
  };
  const extent = () => {
    let x = 0;
    let lo = Infinity;
    let hi = -Infinity;
    for (const p of points) {
      const n = p.clone().project(eye);
      x = Math.max(x, Math.abs(n.x));
      lo = Math.min(lo, n.y);
      hi = Math.max(hi, n.y);
    }
    return { x, lo, hi };
  };
  let d = 1;
  for (let round = 0; round < 3; round++) {
    let near = 0.2;
    let far = 10;
    for (let i = 0; i < 30; i++) {
      d = (near + far) / 2;
      place(d);
      const e = extent();
      if (Math.max(e.x, -e.lo, e.hi) > 0.9) near = d;
      else far = d;
    }
    d = far;
    place(d);
    const e = extent();
    const up = new THREE.Vector3(0, 1, 0).applyQuaternion(eye.quaternion);
    center.addScaledVector(up, ((e.lo + e.hi) / 2) * d * Math.tan(THREE.MathUtils.degToRad(eye.fov / 2)));
  }
  place(d);
  return { position: eye.position.clone(), target: center.clone() };
}
frame();
addEventListener('resize', () => {
  renderer.setSize(innerWidth, innerHeight);
  frame();
});

function modelInfo(vrm: VRM, bytes: number): ModelInfo {
  let triangles = 0;
  let meshes = 0;
  vrm.scene.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    meshes++;
    const g = mesh.geometry;
    triangles += (g.index ? g.index.count : g.attributes.position.count) / 3;
  });
  const expressions = vrm.expressionManager?.expressions.map((e) => e.expressionName) ?? [];
  // Textures as uploaded to the GPU: MToon keeps its maps in uniforms, where walking material fields misses them.
  return { bytes, meshes, triangles: Math.round(triangles), textures: renderer.info.memory.textures, expressions };
}

let paused = false;
let behaviour: Behaviour | null = null;
let loadedVrm: VRM | null = null;
/** The latest mood change still blending, timed from the app's send until its last frame. */
let pending: { mood: Mood; at: number; received: number } | null = null;
/** A gesture asked for before the files loaded, played once they have (if still recent). */
let queued: { name: Gesture; at: number } | null = null;
/** A frame-pacing measurement in progress (debug builds ask for it): intervals between rendered frames. */
let measuring: { left: number; last: number; intervals: number[] } | null = null;

function gesture(name: Gesture) {
  const player = behaviour?.gestures;
  if (!player) {
    queued = { name, at: performance.now() };
    return;
  }
  send({ v: PROTOCOL, type: 'gesture', name, ok: player.play(name) });
}
function speak(mouth: MouthTrack, at: number) {
  behaviour?.mouth.speak(mouth, at);
}
function setAct(act: CupsAct | null) {
  if (!cups) {
    queuedAct = act;
    return;
  }
  const was = cups.active;
  cups.setAct(act);
  if (was !== cups.active) cupsReported = -1;
}

/**
 * Tells the app once the table is fully in view (with where each cup stands across the view, 0..1, for its tap
 * zones) or fully away. The app draws its own 2D board until then, so the game never waits on this page.
 */
function reportCups() {
  const table = cups;
  if (!table) return;
  const state = table.active && table.shown >= 1 ? 1 : !table.active && table.shown <= 0 ? 0 : null;
  if (state === null || state === cupsReported) return;
  cupsReported = state;
  const x = [0, 1, 2].map((s) => Math.round(((table.slotPoint(s).project(camera).x + 1) / 2) * 1000) / 1000);
  send({ v: PROTOCOL, type: 'cups', shown: state === 1, x });
}

onNativeMessage((message) => {
  switch (message.type) {
    case 'emotion':
      mood = message.mood;
      intensity = message.intensity;
      pending = { mood, at: message.at, received: Date.now() };
      behaviour?.setMood(mood, intensity);
      return;
    case 'gesture':
      return gesture(message.name);
    case 'speak':
      return speak(message.mouth, message.at);
    case 'hush':
      return behaviour?.mouth.hush();
    case 'stats':
      measuring = { left: message.ms, last: -1, intervals: [] };
      return;
    case 'fps':
      fpsCap = message.cap;
      return;
    case 'cups':
      return setAct(message.act);
    default:
      paused = message.type === 'pause';
  }
});

// Touch: a finger on the strip draws her gaze; a tap on her head makes her happy. The rest of her ignores taps.
const raycaster = new THREE.Raycaster();
const ndc = new THREE.Vector2();
const plane = new THREE.Plane(new THREE.Vector3(0, 0, 1), 0);
const fingerPoint = new THREE.Vector3();
let down: { x: number; y: number; at: number } | null = null;

function rayAt(e: PointerEvent): THREE.Ray {
  ndc.set((e.clientX / innerWidth) * 2 - 1, -(e.clientY / innerHeight) * 2 + 1);
  raycaster.setFromCamera(ndc, camera);
  return raycaster.ray;
}
function followPointer(e: PointerEvent) {
  // A plane between her and the camera: a finger at the strip's edge turns her eyes, not her whole body.
  plane.constant = -camera.position.z * 0.6;
  behaviour?.follow(rayAt(e).intersectPlane(plane, fingerPoint));
}
const canvas = renderer.domElement;
canvas.addEventListener('pointerdown', (e) => {
  down = { x: e.clientX, y: e.clientY, at: e.timeStamp };
  try {
    canvas.setPointerCapture(e.pointerId); // keep following a finger that slides off the strip
  } catch {
    // Not an active pointer (a synthetic event): following still works while it stays on the canvas.
  }
  followPointer(e);
});
canvas.addEventListener('pointermove', (e) => {
  if (down) followPointer(e);
});
const release = (e: PointerEvent) => {
  const start = down;
  down = null;
  behaviour?.follow(null);
  if (!start || e.type !== 'pointerup' || !behaviour) return;
  if (!isTap(e.timeStamp - start.at, e.clientX - start.x, e.clientY - start.y)) return;
  if (behaviour.hitsHead(rayAt(e)) && behaviour.tap.trigger()) send({ v: PROTOCOL, type: 'tap', part: 'head' });
};
canvas.addEventListener('pointerup', release);
canvas.addEventListener('pointercancel', release);

async function main() {
  if (!modelPath) throw new Error('no model parameter');
  const tFetch = performance.now();
  const response = await fetch(modelPath);
  if (!response.ok) throw new Error(`fetch ${modelPath}: ${response.status}`);
  const buffer = await response.arrayBuffer();
  const tParse = performance.now();

  const loader = new GLTFLoader();
  // Rin's model carries KTX2 textures (tools/character/optimize-vrm.mjs), which the GPU keeps block-compressed.
  // Vite bundles three's Basis transcoder from KTX2Loader's own URL; it runs in a worker only when a model needs it.
  const ktx2 = new KTX2Loader().detectSupport(renderer);
  loader.setKTX2Loader(ktx2);
  loader.register((parser) => new VRMLoaderPlugin(parser));
  const gltf = await loader.parseAsync(buffer, '');
  const vrm = gltf.userData.vrm as VRM;
  VRMUtils.removeUnnecessaryVertices(gltf.scene);
  VRMUtils.combineSkeletons(gltf.scene);
  addVroidSmile(vrm); // before combineMorphs, which keeps only the morphs that expressions use
  VRMUtils.combineMorphs(vrm);
  VRMUtils.rotateVRM0(vrm);
  keepArmsOffSkirt(vrm);
  vrm.scene.traverse((o) => {
    o.frustumCulled = false;
  });
  scene.add(vrm.scene);
  vrm.scene.updateMatrixWorld(true);
  // Skinned bounds follow the bones, so this is the posed model's top: the hair, not the head bone.
  const bounds = new THREE.Box3().setFromObject(vrm.scene);
  topY = bounds.max.y;
  const head = vrm.humanoid.getNormalizedBoneNode('head');
  const headY = head ? head.getWorldPosition(new THREE.Vector3()).y : topY - 0.2;
  const headHalf = (topY - headY) / 2;
  // The cup table (task 3.4), hidden until the app sends an act; warmTable() readies it on the GPU after her first frame.
  const table = stillMode ? null : new CupScene(vrm);
  if (table) {
    cups = table;
    scene.add(table.group);
  }
  frame();
  const tCompile = performance.now();
  // Compile shaders and upload textures before the first frame, so Rin appears whole rather than in pieces.
  await renderer.compileAsync(scene, camera);
  ktx2.dispose(); // textures are on the GPU now; the transcoder worker is not needed again
  if (!table) return exposeStills(vrm, headHalf);

  const life = new Behaviour(vrm, camera, mood, intensity, headHalf, Math.max(headHalf + 0.03, 0.1));
  behaviour = life;
  loadedVrm = vrm;
  if (queuedAct !== undefined) setAct(queuedAct);
  if (!window.RinBridge && q.get('cups') === 'demo') cupsDemo();
  pending = null; // a mood sent while loading is already in place: nothing blends, so nothing to time
  let last = -1;
  let reported = false;
  renderer.setAnimationLoop((now) => {
    if (paused) {
      last = -1;
      if (measuring) measuring.last = -1; // time off screen is not a slow frame
      return;
    }
    const minFrameMs = 1000 / fpsCap - 1; // 1 ms slack, so vsync jitter cannot halve the rate
    if (last >= 0 && now - last < minFrameMs) return;
    const first = last < 0;
    const dt = first ? 0 : Math.min((now - last) / 1000, 0.1);
    last = now;
    // At her table she watches the cups while her hands move them, and the user the rest of the time (CupScene.attend).
    life.aimCamera = 1 - smooth(table.shown) * (1 - smooth(table.attend));
    const blended = life.update(dt);
    if (table.active || table.shown > 0) {
      life.follow(table.update(cupsFrozenAt === null ? Date.now() : table.actStart + cupsFrozenAt, dt));
      aim();
      reportCups();
    }
    if (first) {
      // Hair and skirt start hanging from this pose. Their stored rest came from before rotateVRM0 turned the model,
      // so without this the skirt flew up to her chest for the first second (the tester saw it as she waved hello).
      vrm.scene.updateMatrixWorld(true);
      vrm.springBoneManager?.reset();
    }
    vrm.update(dt);
    renderer.render(scene, camera);
    if (measuring) measure(now);
    if (blended && pending) {
      const done = Date.now();
      send({
        v: PROTOCOL,
        type: 'emotion',
        mood: pending.mood,
        ms: { toPage: pending.received - pending.at, total: done - pending.at },
      });
      pending = null;
    }
    if (reported) return;
    reported = true;
    warmTable(table).catch((e) => console.warn('cup table warm-up failed', e));
    // Gestures load after her first frame, so they never delay her appearing.
    loadGestures(vrm, (name, e) => console.warn(`gesture ${name} failed to load`, e)).then((clips) => {
      life.gestures = new GesturePlayer(clips);
      if (queued && performance.now() - queued.at < 2000) gesture(queued.name);
      queued = null;
      const demo = q.get('gesture');
      if (!window.RinBridge && isGesture(demo)) {
        gesture(demo);
        setInterval(() => gesture(demo), 4000);
      }
    });
    const firstFrame = performance.now();
    const r = Math.round;
    send({
      v: PROTOCOL,
      type: 'ready',
      ms: {
        pageToFirstFrame: r(firstFrame),
        fetch: r(tParse - tFetch),
        parse: r(tCompile - tParse),
        compile: r(firstFrame - tCompile),
        nativeToFirstFrame: t0 ? r(performance.timeOrigin + firstFrame - t0) : null,
      },
      model: modelInfo(vrm, buffer.byteLength),
      pixelRatio,
      fpsCap,
    });
  });
}

/**
 * Readies the cup table on the GPU without showing it or delaying her first frame: a copy sharing its shapes and
 * materials, lit like the page, is compiled and drawn once into a small offscreen target. Shown cold, the table froze
 * the ring screen as "Let's play" brought it in (14T: 108 ms; then 2 × 58 ms with only its shaders compiled), and
 * warming it before her first frame made her appear about a second later.
 */
async function warmTable(table: CupScene) {
  const stage = new THREE.Scene();
  const copy = table.group.clone();
  copy.visible = true;
  stage.add(copy, light.clone(), ambient.clone());
  await renderer.compileAsync(stage, camera);
  const target = new THREE.WebGLRenderTarget(16, 16);
  renderer.setRenderTarget(target);
  renderer.render(stage, camera);
  renderer.setRenderTarget(null);
  target.dispose();
}

function measure(now: number) {
  const m = measuring!;
  if (m.last >= 0) {
    m.intervals.push(now - m.last);
    m.left -= now - m.last;
  }
  m.last = now;
  if (m.left > 0) return;
  measuring = null;
  const stats = frameStats(m.intervals, fpsCap);
  if (stats) send({ v: PROTOCOL, type: 'stats', ...stats });
}

/** Her right arm reaching for the colour pads (task 3.3): forward, a little down and in. */
const HAND_REACH = [0.18, -0.4, 1] as const;
/** The wrist bent down toward the pads, radians. */
const HAND_BEND = 0.35;
/** Metres of the table in view along the frame's height, around her hand. */
const HAND_VIEW = 0.3;
/** How far below her fingertip the hand render cuts everything away (her skirt is under her arm). */
const HAND_CLIP = 0.06;

/** Where the inspection camera stands, relative to the middle of her upper body (metres; +X is her left). */
const VIEWS = {
  front: [0, 0, 1],
  left: [1, 0, 0],
  right: [-1, 0, 0],
  above: [0, 0.8, 0.6],
} as const;
type View = keyof typeof VIEWS;

/**
 * Still mode (task 2.5): no loop and no bridge.
 * - `rinStill(mood)` (tools/character/render-stills.mjs) poses her at rest in that mood, renders one frame with the
 *   page's framing (strip, or full body with `frame=full`) and returns it as a PNG data URL. The app shows these while the 3D page loads, and when it fails.
 * - `rinHand(width, height)` (task 3.3) renders her right hand for the colour pads, seen from above as if she sat across
 *   the table: arm reaching forward, index finger out, the other fingers curled, her body toward the top of the frame.
 *   Returns the PNG and the fingertip's pixel position, which the tool puts at the bottom centre of the crop.
 * - `rinInspect(gesture)` and `rinView(gesture, age, view)` (tools/character/check-gestures.mjs) measure how far her
 *   arms sink into her body through a gesture (inspect.ts), and render a moment of it from any side.
 */
async function exposeStills(vrm: VRM, headHalf: number) {
  const body = new BodyCheck(vrm); // before any posing: it reads the rest (T) pose
  const player = new GesturePlayer(await loadGestures(vrm, (name, e) => fail(`gesture ${name}: ${e}`)));
  const pose = (m: Mood, name: Gesture | null, age: number) => {
    const life = new Behaviour(vrm, camera, m, 1, headHalf, headHalf);
    if (name) {
      player.hold(name, age);
      life.gestures = player;
    }
    life.rest();
    vrm.humanoid.update();
    vrm.scene.updateMatrixWorld(true);
  };
  const render = (eye: THREE.Camera) => {
    vrm.springBoneManager?.reset(); // hair hangs at rest from this pose, not from the last one
    vrm.update(0);
    renderer.render(scene, eye);
    return renderer.domElement.toDataURL('image/png'); // same task as the render, so the buffer is still there
  };
  const still = (m: Mood) => {
    pose(m, null, 0);
    return render(camera);
  };
  /** The deepest the arms sink in, sampled every `step` seconds through the gesture (null: the idle pose alone). */
  const inspect = (name: Gesture | null, step = 1 / 30) => {
    const duration = name ? player.duration(name) : 0;
    let worst: (Depth & { t: number }) | null = null;
    let frill: (Depth & { t: number }) | null = null;
    const at = (d: Depth, age: number) => ({ ...d, mm: Math.round(d.mm), t: Math.round(age * 100) / 100 });
    for (let age = 0; age <= duration + 1e-6; age += step) {
      pose('cheerful', name, age);
      const d = body.deepest();
      if (d.solid && (!worst || d.solid.mm > worst.mm)) worst = at(d.solid, age);
      if (d.frill && (!frill || d.frill.mm > frill.mm)) frill = at(d.frill, age);
    }
    return { gesture: name ?? 'idle', seconds: duration, worst, frill, ...body.counts };
  };
  const eye = new THREE.PerspectiveCamera(30, innerWidth / innerHeight, 0.1, 20);
  const view = (name: Gesture | null, age: number, side: View) => {
    pose('cheerful', name, age);
    const center = new THREE.Vector3(0, topY - 0.5, 0);
    const distance = 0.6 / Math.tan(THREE.MathUtils.degToRad(eye.fov / 2)); // 1.2 m of her in view
    const [x, y, z] = VIEWS[side];
    eye.position.set(x, y, z).normalize().multiplyScalar(distance).add(center);
    eye.lookAt(center);
    eye.updateProjectionMatrix();
    return render(eye);
  };
  const hand = (width: number, height: number) => {
    pose('cheerful', null, 0);
    const node = (name: VRMHumanBoneName) => vrm.humanoid.getNormalizedBoneNode(name);
    const euler = (name: VRMHumanBoneName, x: number, y: number, z: number) => node(name)?.rotation.set(x, y, z);
    // Normalized bones share the world's axes at rest (T-pose, facing +Z): the right arm points -X, palm down, and a
    // positive Z turn curls a right finger toward the palm (scripts/vrma.mjs curlBones).
    const reach = new THREE.Vector3(HAND_REACH[0], HAND_REACH[1], HAND_REACH[2]).normalize();
    node('rightUpperArm')?.quaternion.setFromUnitVectors(new THREE.Vector3(-1, 0, 0), reach);
    euler('rightLowerArm', 0, 0, 0);
    euler('rightHand', 0, 0, HAND_BEND);
    for (const f of ['Middle', 'Ring', 'Little'] as const) {
      euler(`right${f}Proximal`, 0, 0, 1.2);
      euler(`right${f}Intermediate`, 0, 0, 1.4);
      euler(`right${f}Distal`, 0, 0, 0.9);
    }
    euler('rightIndexProximal', 0, 0, 0.08);
    euler('rightIndexIntermediate', 0, 0, 0.08);
    euler('rightIndexDistal', 0, 0, 0.05);
    // The thumb open, angled down toward the pad (3.7, the tester): tucked under the palm it looked hooked, and lying
    // flat it looked longer than the index, which points down and so shows shorter from above.
    euler('rightThumbProximal', 0, -0.35, 0.1);
    euler('rightThumbDistal', 0, -0.2, 0.1);
    vrm.humanoid.update();
    vrm.scene.updateMatrixWorld(true);
    const at = (name: VRMHumanBoneName) => vrm.humanoid.getRawBoneNode(name)!.getWorldPosition(new THREE.Vector3());
    // The fingertip: past the last joint by about the last segment's length.
    const distal = at('rightIndexDistal');
    const tip = distal.clone().add(distal.clone().sub(at('rightIndexIntermediate')).multiplyScalar(0.9));
    const center = tip.clone().add(at('rightHand')).multiplyScalar(0.5);
    const eye = new THREE.PerspectiveCamera(30, width / height, 0.05, 5);
    eye.up.set(0, 0, -1); // her body at the top of the frame, her fingertip toward the user
    eye.position.copy(center).add(new THREE.Vector3(0, HAND_VIEW / 2 / Math.tan(THREE.MathUtils.degToRad(15)), 0));
    eye.lookAt(center);
    eye.updateProjectionMatrix();
    // Only her arm: a plane just under the hand cuts away the skirt and legs below it. From straight above, the page's
    // light washes the back of the hand out, so this frame is lit more softly.
    const saved = { size: renderer.getSize(new THREE.Vector2()), light: light.intensity, ambient: ambient.intensity };
    renderer.clippingPlanes = [new THREE.Plane(new THREE.Vector3(0, 1, 0), -(tip.y - HAND_CLIP))];
    light.intensity = saved.light * 0.6;
    ambient.intensity = saved.ambient * 0.5;
    renderer.setSize(width, height, false);
    const png = render(eye);
    renderer.setSize(saved.size.x, saved.size.y, false);
    renderer.clippingPlanes = [];
    light.intensity = saved.light;
    ambient.intensity = saved.ambient;
    const p = tip.clone().project(eye);
    return { png, tip: [((p.x + 1) / 2) * width, ((1 - p.y) / 2) * height] };
  };
  /** A bone's world position after the last pose (inspection: compare with what scripts/vrma.mjs predicts). */
  const bone = (name: VRMHumanBoneName) =>
    vrm.humanoid.getRawBoneNode(name)?.getWorldPosition(new THREE.Vector3()).toArray();
  const posed = (name: Gesture | null, age: number, names: VRMHumanBoneName[]) => {
    pose('cheerful', name, age);
    return Object.fromEntries(names.map((n) => [n, bone(n)]));
  };
  Object.assign(window, {
    rinStill: still,
    rinHand: hand,
    rinMoods: MOODS,
    rinInspect: inspect,
    rinView: view,
    rinGestures: GESTURES,
    rinBody: body.shape,
    rinPosed: posed,
  });
  document.title = 'still-ready';
}

main().catch(fail);

// Desktop preview only (npm run dev, no app): drive her from the browser console. After `npm run gestures`,
// rin.reload() picks up the new files without reloading the page; rin.hold('wave', 1.2) freezes a moment.
if (!window.RinBridge) {
  const reload = async () => {
    if (!behaviour || !loadedVrm) return;
    behaviour.gestures = new GesturePlayer(await loadGestures(loadedVrm, (n, e) => console.warn(n, e), `?${Date.now()}`));
  };
  const hold = (name: Gesture | null, age = 0) => behaviour?.gestures?.hold(name, age);
  const bone = (name: VRMHumanBoneName) =>
    loadedVrm?.humanoid.getNormalizedBoneNode(name)?.getWorldPosition(new THREE.Vector3()).toArray();
  /** Plays `<base>.mp3` with its `<base>.mouth.json`, timed like the app does it: from when the audio starts. */
  const say = async (base: string) => {
    const track = (await (await fetch(`${base}.mouth.json`)).json()) as MouthTrack;
    const audio = new Audio(`${base}.mp3`);
    audio.addEventListener('playing', () => speak(track, Date.now() - audio.currentTime * 1000), { once: true });
    await audio.play();
  };
  Object.assign(window, { rin: { gestures: GESTURES, gesture, speak, say, reload, hold, bone, vrm: () => loadedVrm, cups: setAct,
    cupsAt: (ms: number | null) => (cupsFrozenAt = ms), camera, table: () => cups } });
}

/**
 * Desktop preview (`cups=demo`): the game as the app plays it, without the app. Show the ball, shuffle 4, 5, then 6
 * swaps, lift the ball's cup, and again. The timings are CupsRules' defaults.
 */
function cupsDemo() {
  let ball = 1;
  let n = 4;
  const T = { leadMs: 300, upMs: 250, downMs: 250, exitMs: 250, swapMs: 450, gapMs: 150 };
  const pairs: Swap[] = [[0, 1], [1, 2], [0, 2]];
  const lift = (): CupsAct => ({ kind: 'lift', ball, at: Date.now(), lift: [ball], hands: [ball], holdMs: 900, ...T });
  const shuffle = (): Extract<CupsAct, { kind: 'shuffle' }> => {
    const swaps: Swap[] = [];
    while (swaps.length < n) {
      const s = pairs[Math.floor(Math.random() * 3)];
      if (swaps.at(-1) !== s) swaps.push(s);
    }
    return { kind: 'shuffle', ball, at: Date.now(), swaps, ...T };
  };
  const run = (act: CupsAct, next: () => void) => {
    setAct(act);
    setTimeout(next, actLength(act) + 700);
  };
  const loop = () =>
    run(lift(), () => {
      const s = shuffle();
      run(s, () => {
        ball = afterSwaps(s.swaps, ball);
        n = n >= 6 ? 4 : n + 1;
        loop();
      });
    });
  loop();
}
