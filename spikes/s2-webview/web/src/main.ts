// S2 spike: load a VRoid VRM 1.0 model with three-vrm, run an idle loop that is
// representative of the real app (spring bones, blink, expressions, lookAt,
// breathing), and measure load time, fps and memory. Results go to the native
// side through window.S2Bridge.postMessage(json), or to the console in a browser.
//
// Query params (all optional):
//   model=sample.vrm  pr=2 (pixel-ratio cap, 0 = device)  aa=1  alpha=0
//   opt=1 (VRMUtils optimisations)  spring=1  warm=3  dur=30  (seconds)
//   t0=<epoch ms> (native timestamp taken before the WebView was created)
//   run=<label>

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { VRM, VRMLoaderPlugin, VRMUtils, VRMHumanBoneName } from '@pixiv/three-vrm';

declare global {
  interface Window { S2Bridge?: { postMessage(msg: string): void } }
  interface Performance { memory?: { usedJSHeapSize: number; totalJSHeapSize: number } }
}

const jsStart = performance.now();
const q = new URLSearchParams(location.search);
const num = (k: string, d: number) => (q.has(k) ? Number(q.get(k)) : d);
const cfg = {
  run: q.get('run') ?? 'default',
  model: q.get('model') ?? 'sample.vrm',
  prCap: num('pr', 2),
  aa: num('aa', 1) === 1,
  alpha: num('alpha', 0) === 1,
  opt: num('opt', 1) === 1,
  spring: num('spring', 1) === 1,
  warm: num('warm', 3),
  dur: num('dur', 30),
  t0: q.has('t0') ? Number(q.get('t0')) : null,
};

const hud = document.getElementById('hud')!;
const log = (m: string) => { console.log('[S2] ' + m); window.S2Bridge?.postMessage(JSON.stringify({ log: m })); };
const report = (o: object) => {
  const json = JSON.stringify(o);
  console.log('[S2-RESULT] ' + json);
  window.S2Bridge?.postMessage(json);
};
window.addEventListener('error', (e) => report({ run: cfg.run, error: String(e.message) }));

// ---------- renderer / scene ----------
const renderer = new THREE.WebGLRenderer({ antialias: cfg.aa, alpha: cfg.alpha, powerPreference: 'high-performance' });
const pr = cfg.prCap > 0 ? Math.min(devicePixelRatio, cfg.prCap) : devicePixelRatio;
renderer.setPixelRatio(pr);
renderer.setSize(innerWidth, innerHeight);
if (!cfg.alpha) renderer.setClearColor(0xf3e9df);
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(30, innerWidth / innerHeight, 0.1, 20);
// Portrait, upper-body framing, like Rin on the ring / morning screen.
camera.position.set(0, 1.3, 1.9);
camera.lookAt(0, 1.2, 0);
const light = new THREE.DirectionalLight(0xffffff, Math.PI);
light.position.set(1, 1.5, 1.5);
scene.add(light, new THREE.AmbientLight(0xffffff, 0.4 * Math.PI));

addEventListener('resize', () => {
  renderer.setSize(innerWidth, innerHeight);
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
});

function gpuName(): string {
  const gl = renderer.getContext();
  const ext = gl.getExtension('WEBGL_debug_renderer_info');
  return ext ? String(gl.getParameter(ext.UNMASKED_RENDERER_WEBGL)) : String(gl.getParameter(gl.RENDERER));
}

function modelStats(vrm: VRM) {
  let tris = 0, meshes = 0, morphs = 0, maxTex = 0;
  const textures = new Set<THREE.Texture>();
  vrm.scene.traverse((o) => {
    const m = o as THREE.Mesh;
    if (!m.isMesh) return;
    meshes++;
    const g = m.geometry;
    tris += (g.index ? g.index.count : g.attributes.position.count) / 3;
    morphs += Object.values(g.morphAttributes)[0]?.length ?? 0;
    for (const mat of ([] as THREE.Material[]).concat(m.material)) {
      for (const v of Object.values(mat as unknown as Record<string, unknown>)) {
        if (v instanceof THREE.Texture) textures.add(v);
      }
    }
  });
  for (const t of textures) {
    const img = t.image as { width?: number; height?: number } | undefined;
    maxTex = Math.max(maxTex, img?.width ?? 0, img?.height ?? 0);
  }
  return {
    meshes, triangles: Math.round(tris), morphTargets: morphs, textures: textures.size, maxTexture: maxTex,
    springJoints: vrm.springBoneManager?.joints.size ?? 0,
    expressions: vrm.expressionManager?.expressions.map((e) => e.expressionName).length ?? 0,
  };
}

// ---------- idle animation (procedural stand-in for VRMA clips) ----------
const lookTarget = new THREE.Object3D();
lookTarget.position.copy(camera.position);
scene.add(lookTarget);
let nextBlink = 2, blinkT = -1;

function animate(vrm: VRM, t: number, dt: number) {
  const h = vrm.humanoid;
  const rot = (b: VRMHumanBoneName, x: number, y: number, z: number) => h.getNormalizedBoneNode(b)?.rotation.set(x, y, z);
  const breath = Math.sin(t * 2 * Math.PI / 4); // one breath per 4 s
  rot('leftUpperArm', 0, 0, -1.2 + 0.03 * breath);
  rot('rightUpperArm', 0, 0, 1.2 - 0.03 * breath);
  rot('leftLowerArm', 0, 0, -0.15);
  rot('rightLowerArm', 0, 0, 0.15);
  rot('chest', 0.03 * breath, 0, 0);
  rot('spine', 0, 0.05 * Math.sin(t * 0.7), 0.02 * Math.sin(t * 0.5));
  rot('head', 0.04 * Math.sin(t * 0.9), 0.08 * Math.sin(t * 0.6), 0);

  const em = vrm.expressionManager;
  if (em) {
    if (blinkT < 0 && t > nextBlink) blinkT = 0;
    if (blinkT >= 0) {
      blinkT += dt;
      em.setValue('blink', Math.sin(Math.min(blinkT / 0.15, 1) * Math.PI));
      if (blinkT > 0.15) { blinkT = -1; nextBlink = t + 2 + Math.random() * 3; em.setValue('blink', 0); }
    }
    // Slow cross-fade between moods, plus a talking mouth, to keep morphs busy.
    const mood = (Math.sin(t * 0.4) + 1) / 2;
    em.setValue('happy', 0.6 * mood);
    em.setValue('relaxed', 0.4 * (1 - mood));
    em.setValue('aa', Math.max(0, Math.sin(t * 9)) * 0.5 * (Math.sin(t * 0.25) > 0 ? 1 : 0));
  }
  vrm.lookAt && (vrm.lookAt.target = lookTarget);
  lookTarget.position.x = camera.position.x + 0.3 * Math.sin(t * 0.3);
}

// ---------- load ----------
async function main() {
  const tFetch0 = performance.now();
  const res = await fetch(cfg.model);
  if (!res.ok) throw new Error(`fetch ${cfg.model}: ${res.status}`);
  const buf = await res.arrayBuffer();
  const tFetch1 = performance.now();

  const loader = new GLTFLoader();
  loader.register((p) => new VRMLoaderPlugin(p));
  const gltf = await loader.parseAsync(buf, '');
  const vrm = gltf.userData.vrm as VRM;
  const tParse = performance.now();

  if (cfg.opt) {
    VRMUtils.removeUnnecessaryVertices(gltf.scene);
    VRMUtils.combineSkeletons(gltf.scene);
    VRMUtils.combineMorphs(vrm);
  }
  VRMUtils.rotateVRM0(vrm);
  vrm.scene.traverse((o) => { o.frustumCulled = false; });
  scene.add(vrm.scene);
  const tOpt = performance.now();

  // Compile shaders + upload textures now so the first frame is honest.
  await renderer.compileAsync(scene, camera);
  const clock = new THREE.Clock();
  let t = 0, firstFrame = -1, measureStart = -1, lastNow = -1;
  const frames: number[] = [];

  renderer.setAnimationLoop((now) => {
    const dt = Math.min(clock.getDelta(), 0.1);
    t += dt;
    animate(vrm, t, dt);
    if (cfg.spring) vrm.update(dt);
    else { vrm.humanoid.update(); vrm.lookAt?.update(dt); vrm.expressionManager?.update(); }
    renderer.render(scene, camera);

    if (firstFrame < 0) {
      firstFrame = performance.now();
      const epoch = performance.timeOrigin + firstFrame;
      report({
        run: cfg.run, phase: 'loaded', cfg,
        ua: navigator.userAgent, gpu: gpuName(), dpr: devicePixelRatio, pixelRatio: pr,
        canvas: [renderer.domElement.width, renderer.domElement.height],
        modelBytes: buf.byteLength,
        ms: {
          jsStart: r(jsStart), fetch: r(tFetch1 - tFetch0), parse: r(tParse - tFetch1),
          optimise: r(tOpt - tParse), compileAndFirstFrame: r(firstFrame - tOpt),
          pageToFirstFrame: r(firstFrame),
          nativeToFirstFrame: cfg.t0 ? r(epoch - cfg.t0) : null,
        },
        model: modelStats(vrm),
        heapMB: heap(),
      });
      log(`first frame ${r(firstFrame)} ms after navigation start`);
    }

    const since = (now - firstFrame) / 1000;
    if (since >= cfg.warm) {
      if (measureStart < 0) { measureStart = now; lastNow = now; }
      else { frames.push(now - lastNow); lastNow = now; }
      if ((now - measureStart) / 1000 >= cfg.dur) {
        renderer.setAnimationLoop(null);
        finish(vrm, frames, now - measureStart);
      }
    }
    if (frames.length % 30 === 0) {
      const recent = frames.slice(-30);
      const fps = recent.length ? 1000 / (recent.reduce((a, b) => a + b, 0) / recent.length) : 0;
      hud.textContent = `${cfg.run}  fps ${fps.toFixed(1)}  pr ${pr}  ${renderer.domElement.width}x${renderer.domElement.height}\n` +
        (measureStart < 0 ? 'warming up…' : `measuring ${((now - measureStart) / 1000).toFixed(0)}/${cfg.dur}s`);
    }
  });
}

function finish(vrm: VRM, frames: number[], elapsed: number) {
  const sorted = [...frames].sort((a, b) => a - b);
  const pct = (p: number) => sorted[Math.min(sorted.length - 1, Math.floor(p * sorted.length))];
  const worst1 = sorted.slice(Math.floor(sorted.length * 0.99));
  const info = renderer.info;
  report({
    run: cfg.run, phase: 'fps',
    seconds: r(elapsed / 1000), frames: frames.length,
    avgFps: r(frames.length / (elapsed / 1000)),
    low1Fps: r(1000 / (worst1.reduce((a, b) => a + b, 0) / worst1.length)),
    frameMs: { p50: r(pct(0.5)), p95: r(pct(0.95)), p99: r(pct(0.99)), max: r(sorted[sorted.length - 1]) },
    over33ms: frames.filter((f) => f > 34).length, over50ms: frames.filter((f) => f > 50).length,
    drawCalls: info.render.calls, trianglesDrawn: info.render.triangles,
    gpuGeometries: info.memory.geometries, gpuTextures: info.memory.textures,
    heapMB: heap(),
    springJoints: vrm.springBoneManager?.joints.size ?? 0,
  });
  hud.textContent += '\ndone';
}

const r = (x: number) => Math.round(x * 10) / 10;
const heap = () => (performance.memory ? r(performance.memory.usedJSHeapSize / 1048576) : null);

main().catch((e) => { report({ run: cfg.run, error: String(e?.stack ?? e) }); hud.textContent = 'ERROR ' + e; });
