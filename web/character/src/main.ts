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

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { KTX2Loader } from 'three/addons/loaders/KTX2Loader.js';
import { VRM, VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';
import { PROTOCOL, onNativeMessage, send, type ModelInfo } from './bridge';
import { Behaviour } from './behaviour';
import { isMood, isTap, type Mood } from './emotion';
import { addVroidSmile } from './vroid';

const q = new URLSearchParams(location.search);
const num = (key: string, fallback: number) => {
  const value = Number(q.get(key));
  return q.has(key) && Number.isFinite(value) && value > 0 ? value : fallback;
};
const modelPath = q.get('model');
const fpsCap = num('fps', 30);
const prCap = num('pr', 2);
const t0 = q.has('t0') ? Number(q.get('t0')) : null;
let mood: Mood = isMood(q.get('mood')) ? (q.get('mood') as Mood) : 'relieved';
let intensity = Math.min(num('intensity', 1), 1);

const fail = (e: unknown) =>
  send({ v: PROTOCOL, type: 'error', message: String((e as Error)?.stack ?? e).slice(0, 2000) });
addEventListener('error', (e) => fail(e.error ?? e.message));
addEventListener('unhandledrejection', (e) => fail(e.reason));

// Transparent canvas: the Compose screen behind it provides the background, in light and dark themes.
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
const pixelRatio = Math.min(devicePixelRatio, prCap);
renderer.setPixelRatio(pixelRatio);
renderer.setSize(innerWidth, innerHeight);
renderer.setClearColor(0x000000, 0);
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(30, innerWidth / innerHeight, 0.1, 20);
const light = new THREE.DirectionalLight(0xffffff, Math.PI);
light.position.set(1, 1.5, 1.5);
scene.add(light, new THREE.AmbientLight(0xffffff, 0.4 * Math.PI));

/** The top of the model (hair included); framing hangs from it, so any model (the sample, Rin) sits the same way. */
let topY = 1.55;

/**
 * Head and shoulders in a wide view (the strip above the alarm list), upper body in a tall one. The visible height
 * at the model decides the distance, so the head fills the strip whatever its pixel size. The top margin keeps the
 * hair inside the frame through breathing and the tap nod (2.1 cropped it, framing from the head bone).
 */
function frame() {
  camera.aspect = innerWidth / innerHeight;
  const wide = camera.aspect > 1;
  const visibleHeight = wide ? 0.44 : 0.95; // metres at the model
  const margin = wide ? 0.03 : 0.08;
  const distance = visibleHeight / 2 / Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
  const target = topY + margin - visibleHeight / 2;
  camera.position.set(0, target + 0.03, distance);
  camera.lookAt(0, target, 0);
  camera.updateProjectionMatrix();
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
/** The latest mood change still blending, timed from the app's send until its last frame. */
let pending: { mood: Mood; at: number; received: number } | null = null;
onNativeMessage((message) => {
  if (message.type === 'emotion') {
    mood = message.mood;
    intensity = message.intensity;
    pending = { mood, at: message.at, received: Date.now() };
    behaviour?.setMood(mood, intensity);
    return;
  }
  paused = message.type === 'pause';
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
  frame();
  const tCompile = performance.now();
  // Compile shaders and upload textures before the first frame, so Rin appears whole rather than in pieces.
  await renderer.compileAsync(scene, camera);
  ktx2.dispose(); // textures are on the GPU now; the transcoder worker is not needed again

  const life = new Behaviour(vrm, camera, mood, intensity, headHalf, Math.max(headHalf + 0.03, 0.1));
  behaviour = life;
  pending = null; // a mood sent while loading is already in place: nothing blends, so nothing to time
  const minFrameMs = 1000 / fpsCap - 1; // 1 ms slack, so vsync jitter cannot halve the rate
  let last = -1;
  let reported = false;
  renderer.setAnimationLoop((now) => {
    if (paused) {
      last = -1;
      return;
    }
    if (last >= 0 && now - last < minFrameMs) return;
    const dt = last < 0 ? 0 : Math.min((now - last) / 1000, 0.1);
    last = now;
    const blended = life.update(dt);
    vrm.update(dt);
    renderer.render(scene, camera);
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

main().catch(fail);
