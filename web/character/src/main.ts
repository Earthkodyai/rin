// Rin's character page: loads the VRM the app chose, renders it with capped pixel ratio and frame rate, and reports
// to Kotlin through the bridge. Nothing here is on the ring path; if this page fails, the app shows a still image.
//
// Query parameters (set by CharacterView.kt):
//   model=model/dev.vrm   path relative to the page, required
//   fps=30                frame-rate cap (S2: the display runs at 120 Hz, far more than an idle character needs)
//   pr=2                  pixel-ratio cap (S2: native DPR 3.25 costs ~45 MB more GPU memory for no visible gain)
//   t0=<epoch ms>         native timestamp taken before the WebView was created

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { VRM, VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';
import { PROTOCOL, onNativeMessage, send, type ModelInfo } from './bridge';
import { Idle } from './idle';

const q = new URLSearchParams(location.search);
const num = (key: string, fallback: number) => {
  const value = Number(q.get(key));
  return q.has(key) && Number.isFinite(value) && value > 0 ? value : fallback;
};
const modelPath = q.get('model');
const fpsCap = num('fps', 30);
const prCap = num('pr', 2);
const t0 = q.has('t0') ? Number(q.get('t0')) : null;

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

/** Height of the model's head bone; framing follows it, so any model (the sample, Rin) sits the same way. */
let headY = 1.3;

/**
 * Head and shoulders in a wide view (the strip above the alarm list), upper body in a tall one. The visible height
 * at the model decides the distance, so the head fills the strip whatever its pixel size.
 */
function frame() {
  camera.aspect = innerWidth / innerHeight;
  const wide = camera.aspect > 1;
  const visibleHeight = wide ? 0.42 : 0.95; // metres at the model
  const distance = visibleHeight / 2 / Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
  const target = wide ? headY - 0.06 : headY - 0.3;
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
onNativeMessage((message) => {
  paused = message.type === 'pause';
});

async function main() {
  if (!modelPath) throw new Error('no model parameter');
  const tFetch = performance.now();
  const response = await fetch(modelPath);
  if (!response.ok) throw new Error(`fetch ${modelPath}: ${response.status}`);
  const buffer = await response.arrayBuffer();
  const tParse = performance.now();

  const loader = new GLTFLoader();
  loader.register((parser) => new VRMLoaderPlugin(parser));
  const gltf = await loader.parseAsync(buffer, '');
  const vrm = gltf.userData.vrm as VRM;
  VRMUtils.removeUnnecessaryVertices(gltf.scene);
  VRMUtils.combineSkeletons(gltf.scene);
  VRMUtils.combineMorphs(vrm);
  VRMUtils.rotateVRM0(vrm);
  vrm.scene.traverse((o) => {
    o.frustumCulled = false;
  });
  scene.add(vrm.scene);
  vrm.scene.updateMatrixWorld(true);
  const head = vrm.humanoid.getNormalizedBoneNode('head');
  if (head) {
    headY = head.getWorldPosition(new THREE.Vector3()).y;
    frame();
  }
  const tCompile = performance.now();
  // Compile shaders and upload textures before the first frame, so Rin appears whole rather than in pieces.
  await renderer.compileAsync(scene, camera);

  const idle = new Idle(vrm);
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
    idle.update(dt);
    vrm.update(dt);
    renderer.render(scene, camera);
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
