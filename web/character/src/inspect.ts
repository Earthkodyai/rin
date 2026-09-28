// Gesture inspection (task 2.5), still mode only (tools/character/check-gestures.mjs). Gestures are keyframed against
// a bare skeleton (scripts/vrma.mjs), which knows nothing of her body's volume, so an elbow or a sleeve can pass into
// her chest or sides. This measures that on the real model: every frame, her torso from the waist up (clothes
// included) is cut into 2 cm horizontal slices, each slice's outline is its convex hull, and any arm vertex (sleeves
// included) inside the outline of its slice has sunk in by its distance to the edge. Below the waist are skirts and
// thighs, where hands may rest against her as cloth gives way (your pick, 2026-09-28: arms close to her body).
import * as THREE from 'three';
import type { VRM, VRMHumanBoneName } from '@pixiv/three-vrm';

const TORSO = new Set<string>(['hips', 'spine', 'chest', 'upperChest']);
const ARM = /^(left|right)(UpperArm|LowerArm|Hand|Thumb|Index|Middle|Ring|Little)/;
const SLICE_M = 0.02;
/**
 * The first 8 cm along the upper arm (shoulder and armpit) are left out, as in scripts/vrma.mjs clearArm: there the
 * sleeve presses into her side through skinning, which no arm path can avoid.
 */
const SHOULDER_SKIP_M = 0.08;

interface Vertex {
  mesh: THREE.SkinnedMesh;
  index: number;
  /** The humanoid bone that moves it most (non-humanoid bones such as sleeve springs count as their humanoid parent). */
  bone: string;
  /**
   * Arms only: part of the outermost 10% of its arm segment's surface (by distance from the bone, in the rest pose),
   * such as a glove's or sleeve's frill. The gesture builder lets those brush her as cloth would, so they are
   * reported apart and do not fail a gesture.
   */
  frill?: boolean;
}

export interface Depth {
  mm: number;
  bone: string;
  at: [number, number, number];
  torso: { x: [number, number]; z: [number, number] };
}

/**
 * Her body as the gesture builder needs it (scripts/vrma.mjs, src/body.json), measured in the rest (T) pose with the
 * model standing at the origin, in metres: each humanoid bone's parent and offset; `waistY`, the narrowest slice
 * between hips and chest; her torso's outline (clothes included) from there up, in horizontal slices: `y` is the
 * slice's bottom, `hull` a counter-clockwise [x, z, x, z, ...] outline;
 * and how thick her arms are, sleeves included (`arm`, 90th percentiles, so the outermost frill may brush her as
 * cloth would): the forearm's radius all round, the upper arm's on its armpit side (the side that faces her), and
 * how far the hand's surface (a glove included) lies from its nearest finger joint.
 */
export interface BodyShape {
  skeleton: Record<string, [string | null, [number, number, number]]>;
  sliceM: number;
  waistY: number;
  slices: { y: number; hull: number[] }[];
  arm: { upperR: number; lowerR: number; handR: number };
}

export class BodyCheck {
  /** Measured in the constructor, before anything moves. */
  readonly shape: BodyShape;
  private readonly torso: Vertex[] = [];
  private readonly arms: Vertex[] = [];
  private readonly v = new THREE.Vector3();
  private readonly waistY: number;

  /** Call on the freshly loaded model, in its rest (T) pose. */
  constructor(vrm: VRM) {
    const humanoid = new Map<THREE.Object3D, string>();
    for (const [name, bone] of Object.entries(vrm.humanoid.rawHumanBones)) if (bone) humanoid.set(bone.node, name);
    const nameOf = (node: THREE.Object3D | null): string => {
      for (let n = node; n; n = n.parent) {
        const name = humanoid.get(n);
        if (name) return name;
      }
      return '';
    };
    const joint = (bone: string) =>
      vrm.humanoid.getRawBoneNode(bone as VRMHumanBoneName)?.getWorldPosition(new THREE.Vector3());
    vrm.scene.updateMatrixWorld(true);
    const joints = { left: joint('leftUpperArm'), right: joint('rightUpperArm') };
    const restTorso: number[] = [];
    // In the T-pose each arm lies along x, so a vertex's distance from its bone's axis is its (y, z) offset.
    const sizes = { upperR: [] as number[], lowerR: [] as number[], handR: [] as number[] };
    const handJoints = Object.entries(vrm.humanoid.rawHumanBones)
      .filter(([name]) => /^(left|right)(Hand|Thumb|Index|Middle|Ring|Little)/.test(name))
      .map(([, bone]) => bone!.node.getWorldPosition(new THREE.Vector3()));
    // Every arm vertex's distance from its bone in the rest pose, to find the frills (the outermost 10%) per segment.
    const reach: { vertex: Vertex; part: 'upper' | 'lower' | 'hand'; r: number }[] = [];
    const starts: Record<string, THREE.Vector3 | undefined> = {};
    for (const s of ['left', 'right']) {
      starts[`${s}upper`] = joint(`${s}UpperArm`);
      starts[`${s}lower`] = joint(`${s}LowerArm`);
    }
    vrm.scene.traverse((o) => {
      const mesh = o as THREE.SkinnedMesh;
      if (!mesh.isSkinnedMesh) return;
      const skinIndex = mesh.geometry.attributes.skinIndex;
      const skinWeight = mesh.geometry.attributes.skinWeight;
      const bones = mesh.skeleton.bones.map(nameOf);
      for (let i = 0; i < skinIndex.count; i++) {
        let best = 0;
        for (let k = 1; k < 4; k++) if (skinWeight.getComponent(i, k) > skinWeight.getComponent(i, best)) best = k;
        const bone = bones[skinIndex.getComponent(i, best)];
        if (TORSO.has(bone)) {
          mesh.getVertexPosition(i, this.v).applyMatrix4(mesh.matrixWorld);
          restTorso.push(this.v.x, this.v.y, this.v.z);
          if (i % 2 === 0) this.torso.push({ mesh, index: i, bone }); // half is plenty for an outline
        } else if (ARM.test(bone)) {
          const s = bone.startsWith('left') ? 'left' : 'right';
          mesh.getVertexPosition(i, this.v).applyMatrix4(mesh.matrixWorld);
          // In the T-pose the arm lies along x, so the distance along it is the x offset from the shoulder joint.
          const vertex: Vertex = { mesh, index: i, bone };
          if (Math.abs(this.v.x - (joints[s]?.x ?? 0)) > SHOULDER_SKIP_M) this.arms.push(vertex);
          if (!/(UpperArm|LowerArm)$/.test(bone)) {
            const r = Math.min(...handJoints.map((j) => j.distanceTo(this.v)));
            sizes.handR.push(r);
            reach.push({ vertex, part: 'hand', r });
            continue;
          }
          const part = bone.includes('UpperArm') ? 'upper' : bone.includes('LowerArm') ? 'lower' : null;
          const start = part && starts[`${s}${part}`];
          if (!part || !start) continue;
          const d = this.v.clone().sub(start);
          if (part === 'upper' && Math.abs(d.x) < SHOULDER_SKIP_M) continue; // the shoulder, not the sleeve
          reach.push({ vertex, part, r: Math.hypot(d.y, d.z) });
          // The upper arm keeps its armpit side (the T-pose underside) toward her body, and a puffed sleeve bulges
          // outward and up, so only its underside counts; a forearm rolls, so it counts all round.
          if (part === 'upper') sizes.upperR.push(Math.max(0, -d.y));
          else sizes.lowerR.push(Math.hypot(d.y, d.z));
        }
      }
    });
    const p90 = (list: number[]) => mm([...list].sort((a, b) => a - b)[Math.floor(list.length * 0.9)] ?? 0);
    for (const part of ['upper', 'lower', 'hand'] as const) {
      const mine = reach.filter((x) => x.part === part);
      const edge = p90(mine.map((x) => x.r));
      for (const x of mine) x.vertex.frill = x.r > edge;
    }
    const slices = slicesOf(restTorso);
    this.waistY = waistOf(slices, joint('hips')?.y ?? 0, joint('chest')?.y ?? Infinity);
    this.shape = {
      skeleton: skeletonOf(vrm, nameOf),
      sliceM: SLICE_M,
      waistY: this.waistY,
      slices: slices.filter((slice) => slice.y >= this.waistY),
      arm: { upperR: p90(sizes.upperR), lowerR: p90(sizes.lowerR), handR: p90(sizes.handR) },
    };
  }

  get counts() {
    return { torso: this.torso.length, arms: this.arms.length };
  }

  /**
   * The deepest arm vertex inside her torso in the current pose (world matrices up to date), or null if none: how
   * deep, which bone moves it, where it is (m) and the torso's outline at that height (x and z extents, m). Frills
   * (see Vertex) are measured apart: `frill` is the deepest of them.
   */
  deepest(): { solid: Depth | null; frill: Depth | null } {
    const slices = new Map<number, number[]>();
    for (const t of this.torso) {
      const p = this.world(t);
      const key = Math.floor(p.y / SLICE_M);
      let list = slices.get(key);
      if (!list) slices.set(key, (list = []));
      list.push(p.x, p.z);
    }
    const hulls = new Map<number, number[]>();
    for (const [key, points] of slices) if (points.length >= 6) hulls.set(key, convexHull(points));
    const worst: { solid: Depth | null; frill: Depth | null } = { solid: null, frill: null };
    for (const a of this.arms) {
      const p = this.world(a);
      if (p.y < this.waistY) continue;
      const hull = hulls.get(Math.floor(p.y / SLICE_M));
      if (!hull) continue;
      const depth = insideDepth(hull, p.x, p.z);
      const kind = a.frill ? 'frill' : 'solid';
      if (depth > 0 && (!worst[kind] || depth * 1000 > worst[kind].mm)) {
        const xs = hull.filter((_, i) => i % 2 === 0);
        const zs = hull.filter((_, i) => i % 2 === 1);
        const r = (x: number) => Math.round(x * 1000) / 1000;
        worst[kind] = {
          mm: depth * 1000,
          bone: a.bone,
          at: [r(p.x), r(p.y), r(p.z)],
          torso: { x: [r(Math.min(...xs)), r(Math.max(...xs))], z: [r(Math.min(...zs)), r(Math.max(...zs))] },
        };
      }
    }
    return worst;
  }

  private world(vertex: Vertex): THREE.Vector3 {
    return vertex.mesh.getVertexPosition(vertex.index, this.v).applyMatrix4(vertex.mesh.matrixWorld);
  }
}

const mm = (x: number) => Math.round(x * 1000) / 1000;

function skeletonOf(vrm: VRM, nameOf: (node: THREE.Object3D | null) => string): BodyShape['skeleton'] {
  const out: BodyShape['skeleton'] = {};
  const at = (node: THREE.Object3D) => node.getWorldPosition(new THREE.Vector3());
  for (const [name, bone] of Object.entries(vrm.humanoid.rawHumanBones)) {
    if (!bone) continue;
    const parentName = nameOf(bone.node.parent);
    const parent = parentName ? vrm.humanoid.getRawBoneNode(parentName as VRMHumanBoneName) : null;
    const offset = at(bone.node).sub(parent ? at(parent) : new THREE.Vector3());
    out[name] = [parentName || null, [mm(offset.x), mm(offset.y), mm(offset.z)]];
  }
  return out;
}

/** The bottom of the narrowest slice between the hips and the chest joints (her waist), or the hips if none. */
export function waistOf(slices: BodyShape['slices'], hipsY: number, chestY: number): number {
  let best = { y: hipsY, width: Infinity };
  for (const { y, hull } of slices) {
    if (y < hipsY || y > chestY) continue;
    const xs = hull.filter((_, i) => i % 2 === 0);
    const width = Math.max(...xs) - Math.min(...xs);
    if (width < best.width) best = { y, width };
  }
  return best.y;
}

function slicesOf(flat: readonly number[]): BodyShape['slices'] {
  const byKey = new Map<number, number[]>();
  for (let i = 0; i < flat.length; i += 3) {
    const key = Math.floor(flat[i + 1] / SLICE_M);
    let list = byKey.get(key);
    if (!list) byKey.set(key, (list = []));
    list.push(flat[i], flat[i + 2]);
  }
  return [...byKey.entries()]
    .filter(([, points]) => points.length >= 6)
    .sort(([a], [b]) => a - b)
    .map(([key, points]) => ({ y: mm(key * SLICE_M), hull: convexHull(points).map(mm) }));
}

/** Convex hull of flat [x0, z0, x1, z1, ...] points, counter-clockwise, as the same flat layout (Andrew's chain). */
export function convexHull(flat: readonly number[]): number[] {
  const pts: [number, number][] = [];
  for (let i = 0; i < flat.length; i += 2) pts.push([flat[i], flat[i + 1]]);
  pts.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  if (pts.length < 3) return pts.flat();
  const cross = (o: number[], a: number[], b: number[]) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lower: [number, number][] = [];
  for (const p of pts) {
    while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], p) <= 0) lower.pop();
    lower.push(p);
  }
  const upper: [number, number][] = [];
  for (let i = pts.length - 1; i >= 0; i--) {
    const p = pts[i];
    while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], p) <= 0) upper.pop();
    upper.push(p);
  }
  return [...lower.slice(0, -1), ...upper.slice(0, -1)].flat();
}

/** How far (x, z) lies inside a counter-clockwise convex polygon: the distance to its nearest edge, or 0 outside. */
export function insideDepth(hull: readonly number[], x: number, z: number): number {
  const n = hull.length / 2;
  if (n < 3) return 0;
  let depth = Infinity;
  for (let i = 0; i < n; i++) {
    const [ax, az] = [hull[i * 2], hull[i * 2 + 1]];
    const [bx, bz] = [hull[((i + 1) % n) * 2], hull[((i + 1) % n) * 2 + 1]];
    const len = Math.hypot(bx - ax, bz - az);
    if (len === 0) continue;
    const side = ((bx - ax) * (z - az) - (bz - az) * (x - ax)) / len; // > 0: left of the edge, inside for CCW
    if (side <= 0) return 0;
    depth = Math.min(depth, side);
  }
  return depth === Infinity ? 0 : depth;
}
