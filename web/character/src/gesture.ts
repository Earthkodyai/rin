// Gestures (task 2.4): short VRMA clips (built from gestures.json by scripts/make-gestures.mjs) layered over the
// idle life in behaviour.ts. Arms and hands take the clip's pose (crossfaded by weight); spine, neck and head add
// the clip's rotation on top of breathing and looking, so a nod still happens while she looks at the user. Face
// tracks come back as weights for behaviour.ts to mix with the mood and the mouth.
import * as THREE from 'three';
import type { VRM, VRMHumanBoneName } from '@pixiv/three-vrm';
import { VRMAnimationLoaderPlugin, type VRMAnimation } from '@pixiv/three-vrm-animation';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import table from './gestures.json';

export type Gesture = keyof typeof table;
export const GESTURES = Object.keys(table) as Gesture[];
export const isGesture = (value: unknown): value is Gesture =>
  typeof value === 'string' && Object.hasOwn(table, value);

/** Bones whose clip rotation adds to the idle pose rather than replacing it. */
export const ADDITIVE = new Set<string>(['spine', 'chest', 'upperChest', 'neck', 'head', 'leftShoulder', 'rightShoulder']);

/** 05a: animation crossfades take 200–400 ms. */
export const FADE_IN_S = 0.25;
export const FADE_OUT_S = 0.3;

/** How much of a gesture shows at `age` seconds into a clip of `duration`: fades in, holds, fades out by the end. */
export function envelope(age: number, duration: number): number {
  if (age <= 0 || age >= duration) return 0;
  const smooth = (x: number) => x * x * (3 - 2 * x);
  const fadeIn = Math.min(FADE_IN_S, duration / 2);
  const fadeOut = Math.min(FADE_OUT_S, duration / 2);
  if (age < fadeIn) return smooth(age / fadeIn);
  if (age > duration - fadeOut) return smooth((duration - age) / fadeOut);
  return 1;
}

/** A loaded gesture, sampled by time. */
export class Clip {
  private readonly bones: [VRMHumanBoneName, THREE.Interpolant][] = [];
  private readonly faces: [string, THREE.Interpolant][] = [];

  constructor(
    readonly name: Gesture,
    animation: VRMAnimation,
    vrm0: boolean,
  ) {
    this.duration = animation.duration;
    for (const [bone, track] of animation.humanoidTracks.rotation) {
      // Same conversion as three-vrm-animation's createVRMAnimationClip for VRM 0.x models.
      const values = vrm0 ? track.values.map((v, i) => (i % 2 === 0 ? -v : v)) : track.values;
      const slerp = new THREE.QuaternionLinearInterpolant(track.times, values, 4, new Float32Array(4));
      this.bones.push([bone as VRMHumanBoneName, slerp]);
    }
    for (const map of [animation.expressionTracks.preset, animation.expressionTracks.custom]) {
      for (const [name, track] of map) {
        this.faces.push([name, new THREE.LinearInterpolant(track.times, track.values, 1, new Float32Array(1))]);
      }
    }
  }

  readonly duration: number;

  get hasFace(): boolean {
    return this.faces.length > 0;
  }

  /** Writes each bone's rotation at time t into `out` (reusing its quaternions). */
  sampleBones(t: number, out: Map<VRMHumanBoneName, THREE.Quaternion>): void {
    for (const [bone, interpolant] of this.bones) {
      const v = interpolant.evaluate(t);
      let q = out.get(bone);
      if (!q) out.set(bone, (q = new THREE.Quaternion()));
      q.set(v[0], v[1], v[2], v[3]).normalize();
    }
  }

  sampleFace(t: number, out: Record<string, number>): void {
    for (const [name, interpolant] of this.faces) out[name] = interpolant.evaluate(t)[0];
  }
}

interface Layer {
  clip: Clip;
  age: number;
  /**
   * Whether its arms fade in. A clip starts and ends on the idle arms (scripts/vrma.mjs restArm), and its path is
   * cleared of her body frame by frame; blending joint angles toward it would leave that path and cut through her. So
   * arms fade only when the clip takes over from another gesture mid-way.
   */
  fadeArms: boolean;
  /** Age when a newer gesture took over; the layer then fades out over FADE_IN_S while the new one fades in. */
  releasedAt: number | null;
}

const IDENTITY = new THREE.Quaternion();

/** Plays gestures one at a time; starting one while another plays crossfades between them. */
export class GesturePlayer {
  private layers: Layer[] = [];
  /** Desktop preview only: freezes the clock so one moment of a gesture can be inspected. */
  private held = false;
  private readonly pose = new Map<VRMHumanBoneName, THREE.Quaternion>();
  private readonly offset = new THREE.Quaternion();
  private readonly faceNow: Record<string, number> = {};
  private readonly faceOut: Record<string, number> = {};

  constructor(private readonly clips: ReadonlyMap<Gesture, Clip>) {}

  has(name: Gesture): boolean {
    return this.clips.has(name);
  }

  /** A loaded gesture's length in seconds (0 when it is not loaded). */
  duration(name: Gesture): number {
    return this.clips.get(name)?.duration ?? 0;
  }

  /** The gesture currently in charge, if any. */
  get current(): Gesture | null {
    const top = this.layers.at(-1);
    return top && top.releasedAt === null ? top.clip.name : null;
  }

  /** Starts a gesture; returns false when it is not loaded. */
  play(name: Gesture): boolean {
    const clip = this.clips.get(name);
    if (!clip) return false;
    const interrupting = this.layers.some((l) => this.weight(l) > 0);
    for (const layer of this.layers) layer.releasedAt ??= layer.age;
    this.layers.push({ clip, age: 0, releasedAt: null, fadeArms: interrupting });
    return true;
  }

  private weight(layer: Layer, arms = false): number {
    const inClip = layer.age > 0 && layer.age < layer.clip.duration;
    const w = arms && !layer.fadeArms ? (inClip ? 1 : 0) : envelope(layer.age, layer.clip.duration);
    if (layer.releasedAt === null) return w;
    return w * Math.max(0, 1 - (layer.age - layer.releasedAt) / FADE_IN_S);
  }

  /** Desktop preview only: shows `name` frozen at `age` seconds, or resumes normal playing when name is null. */
  hold(name: Gesture | null, age = 0): void {
    const clip = name ? this.clips.get(name) : undefined;
    this.held = !!clip;
    this.layers = clip ? [{ clip, age, releasedAt: null, fadeArms: false }] : [];
  }

  update(dt: number): void {
    if (this.held) return;
    for (const layer of this.layers) layer.age += dt;
    this.layers = this.layers.filter((l) => this.weight(l) > 0 || l.age < 0.05);
  }

  /** Blends the clips into the bones Behaviour has already posed this frame. */
  applyBones(vrm: VRM): void {
    for (const layer of this.layers) {
      const w = this.weight(layer);
      const wArms = this.weight(layer, true);
      if (w <= 0 && wArms <= 0) continue;
      this.pose.clear();
      layer.clip.sampleBones(layer.age, this.pose);
      for (const [bone, q] of this.pose) {
        const node = vrm.humanoid.getNormalizedBoneNode(bone);
        if (!node) continue;
        if (ADDITIVE.has(bone)) node.quaternion.multiply(this.offset.copy(IDENTITY).slerp(q, w));
        else node.quaternion.slerp(q, wArms);
      }
    }
  }

  /**
   * Face weights from the gestures (strongest layer wins per expression), and `takeover`, how much a gesture with a
   * face of its own should quiet the mood's expressions (a yawn replaces a smile).
   */
  face(): { weights: Readonly<Record<string, number>>; takeover: number } {
    for (const k of Object.keys(this.faceOut)) delete this.faceOut[k];
    let takeover = 0;
    for (const layer of this.layers) {
      if (!layer.clip.hasFace) continue;
      const w = this.weight(layer);
      if (w <= 0) continue;
      takeover = Math.max(takeover, w);
      for (const k of Object.keys(this.faceNow)) delete this.faceNow[k];
      layer.clip.sampleFace(layer.age, this.faceNow);
      for (const [name, value] of Object.entries(this.faceNow)) {
        this.faceOut[name] = Math.max(this.faceOut[name] ?? 0, value * w);
      }
    }
    return { weights: this.faceOut, takeover };
  }
}

/** Loads every gesture file next to the page (gestures/<name>.vrma); a file that fails is left out and reported. */
export async function loadGestures(
  vrm: VRM,
  onError: (name: Gesture, error: unknown) => void,
  query = '',
): Promise<Map<Gesture, Clip>> {
  const loader = new GLTFLoader();
  loader.register((parser) => new VRMAnimationLoaderPlugin(parser));
  const vrm0 = vrm.meta.metaVersion === '0';
  const clips = new Map<Gesture, Clip>();
  await Promise.all(
    GESTURES.map(async (name) => {
      try {
        const gltf = await loader.loadAsync(`gestures/${name}.vrma${query}`);
        const animation = (gltf.userData.vrmAnimations as VRMAnimation[] | undefined)?.[0];
        if (!animation) throw new Error('no VRM animation in the file');
        clips.set(name, new Clip(name, animation, vrm0));
      } catch (e) {
        onError(name, e);
      }
    }),
  );
  return clips;
}
