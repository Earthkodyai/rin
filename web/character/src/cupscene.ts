// The cup shuffle on the page (task 3.4): a table in front of Rin with three cups and a ball, acted out from the app's
// acts (cups.ts), her hands resting on the cups she moves (not a real grip, D17). The arms are posed live by the same
// two-bone IK the gestures are built with (scripts/vrma.mjs solveArm), cleared of her body (clearArm). Everything is
// sized from her skeleton, so the cups stay within reach for the sample and for Rin's own model alike.
//
// Why it looks the way it does (a sweep of layouts against clearArm, task 3.4): with the sample's short arms, any hand
// that crosses her middle drives the upper arm into her chest. So her hands keep to their own sides (cups.ts release),
// she leans over the table a little, which brings the cups in front of her chest rather than her waist, and the cup
// passing in front swings wide while the one behind stays close to its line (clear of her body).
import * as THREE from 'three';
import type { VRM, VRMHumanBoneName } from '@pixiv/three-vrm';
import { clearArm, curlFingers, restArm, restFingers, restPosition, SKELETON, solveArm } from '../scripts/vrma.mjs';
import { frameAt, handInSwap, smooth, type CupsAct, type CupsFrame, type HandPose } from './cups';

type Side = 'left' | 'right';

/** The table and cups, in metres, sized from her arm (the VRoid sample's is 0.363 m). */
export interface Layout {
  /** Distance between slot centres. */
  spacing: number;
  /** Cup centres' distance in front of her (+Z). */
  cupZ: number;
  /** How far a cup passing in front swings forward, and one passing behind swings back. */
  arcFront: number;
  arcBack: number;
  tableY: number;
  cupH: number;
  cupR: number;
  /** How high a lifted cup rises. */
  liftH: number;
  /** How far she leans forward at the spine while the table is up (radians). */
  lean: number;
}

/** Share of full stretch her arm may use at the farthest point a hand goes. */
const STRETCH = 0.9;
const LEAN = 0.2;

/** Rotates `p` about her spine joint by her lean (forward, about +X). */
function leaned(p: THREE.Vector3, pivot: THREE.Vector3, lean: number): THREE.Vector3 {
  return p.clone().sub(pivot).applyAxisAngle(new THREE.Vector3(1, 0, 0), lean).add(pivot);
}

/**
 * Sizes the scene from her skeleton (T-pose body space: +X her left, facing +Z): cups a third of an arm apart, 0.8 of
 * an arm in front of her shoulder, and the table as low as it can be while every point her right hand visits (the
 * left mirrors it) stays within STRETCH of her reach, measured with her leaning.
 */
export function layoutFor(joint: THREE.Vector3, pivot: THREE.Vector3, arm: number, grip: { back: number; up: number }): Layout {
  const spacing = 0.34 * arm;
  const cupZ = joint.z + 0.8 * arm;
  const cupH = 0.26 * arm;
  const arcFront = 0.22 * arm;
  const arcBack = 0.08 * arm;
  const wristBack = grip.back * arm;
  const wristUp = grip.up * arm;
  // Every point her right hand visits in a swap, and her left hand's mirrored to her right side.
  const points: [number, number][] = [];
  const z = (pz: number) => (pz >= 0 ? pz * arcFront : pz * arcBack);
  for (const [lo, hi] of [[0, 1], [0, 2], [1, 2]]) {
    for (let f = 0; f <= 1.0001; f += 0.05) {
      const r = handInSwap(lo, hi, 'right', f);
      const l = handInSwap(lo, hi, 'left', f);
      points.push([(r.x - 1) * spacing, z(r.z)], [-(l.x - 1) * spacing, z(l.z)]);
    }
  }
  const unlean = (p: THREE.Vector3) => leaned(p, pivot, -LEAN);
  const reach = (y: number) =>
    points.every(([px, pz]) => unlean(new THREE.Vector3(px, y, cupZ - wristBack + pz)).distanceTo(joint) <= STRETCH * arm);
  let wristY = leaned(joint, pivot, LEAN).y;
  while (wristY > joint.y - arm && reach(wristY - 0.005)) wristY -= 0.005;
  const tableY = wristY - wristUp - cupH;
  return { spacing, cupZ, arcFront, arcBack, tableY, cupH, cupR: 0.13 * arm, liftH: 0.3 * arm, lean: LEAN };
}

const WOOD = 0xb07a4f;
const CUP = 0xd9534f;
const BALL = 0xffd54a;

export class CupScene {
  readonly group = new THREE.Group();
  readonly layout: Layout;
  private readonly cups: THREE.Mesh[] = [];
  private readonly ball: THREE.Mesh;
  private act: CupsAct | null = null;
  /** Hands as they were when the current act arrived, so its lead-in starts from there. */
  private readonly from: Record<Side, { pos: THREE.Vector3; w: number } | null> = { left: null, right: null };
  private readonly last: Record<Side, { pos: THREE.Vector3; w: number } | null> = { left: null, right: null };
  private readonly pivot: THREE.Vector3;
  /** How far the table view is blended in (0 her usual framing .. 1 the table). */
  shown = 0;
  private target = 0;
  /**
   * How her hand holds a cup: the wrist `back` behind the top's centre and `up` above it (in arms), the hand tipped
   * down by `pitch` (radians), fingers curled by `curl` (0 flat .. 1 fist). On a cup passing behind, the wrist sits
   * `backShift` further back at the height of the pass, so her fingers stay off the cup passing in front. The hand
   * rolls by `roll` (thumb side up) and the thumb lies along it (`thumb*`, Euler angles for her right hand): the
   * thumb's root sank 2.3 cm into the cup before (the tester saw it); a sweep put it at 0.4 cm, the whole hand at
   * 0.7 cm at worst, the palm 0.3 cm over the top.
   */
  readonly grip = {
    back: 0.12,
    up: 0.035,
    pitch: 0,
    curl: 0.1,
    backShift: 0.08,
    roll: 0.5,
    thumbBase: [0, -0.3, -0.3] as number[],
    thumbMid: [0, 0, 0] as number[],
    thumbTip: [0, 0, 0] as number[],
  };
  private readonly arm: number;

  constructor(private readonly vrm: VRM) {
    const arm = (this.arm = Math.abs(SKELETON.rightLowerArm[1][0]) + Math.abs(SKELETON.rightHand[1][0]));
    this.pivot = restPosition('spine');
    const l = (this.layout = layoutFor(restPosition('rightUpperArm'), this.pivot, arm, this.grip));

    // The back edge stops just behind the back of a swap, clear of her skirt; the front runs out of the view.
    const width = 2 * l.spacing + 6 * l.cupR;
    const back = l.cupZ - l.arcBack - l.cupR - 0.02;
    const depth = 0.6;
    const top = new THREE.Mesh(
      new THREE.BoxGeometry(width, 0.03, depth),
      new THREE.MeshStandardMaterial({ color: WOOD, roughness: 0.8 }),
    );
    top.position.set(0, l.tableY - 0.015, back + depth / 2);
    this.group.add(top);

    // A cup upside down: wide rim on the table, closed top, open underneath (a lathe profile, drawn from both sides).
    const profile = [
      new THREE.Vector2(l.cupR, 0),
      new THREE.Vector2(l.cupR * 0.97, l.cupH * 0.08),
      new THREE.Vector2(l.cupR * 0.78, l.cupH * 0.96),
      new THREE.Vector2(l.cupR * 0.7, l.cupH),
      new THREE.Vector2(0, l.cupH),
    ];
    const cupGeometry = new THREE.LatheGeometry(profile, 32);
    const cupMaterial = new THREE.MeshStandardMaterial({ color: CUP, roughness: 0.45, side: THREE.DoubleSide });
    for (let i = 0; i < 3; i++) {
      const cup = new THREE.Mesh(cupGeometry, cupMaterial);
      this.cups.push(cup);
      this.group.add(cup);
    }
    this.ball = new THREE.Mesh(
      new THREE.SphereGeometry(l.cupR * 0.55, 24, 16),
      new THREE.MeshStandardMaterial({ color: BALL, roughness: 0.35 }),
    );
    this.group.add(this.ball);
    this.group.visible = false;
    this.place(frameAt({ kind: 'rest', ball: 1, at: 0 }, 0));
  }

  /** The app's latest act, or null to put the table away. */
  setAct(act: CupsAct | null): void {
    for (const s of ['left', 'right'] as const) {
      const was = this.last[s];
      this.from[s] = was ? { pos: was.pos.clone(), w: was.w } : null;
    }
    this.act = act;
    this.target = act ? 1 : 0;
  }

  /** When the current act started (epoch ms). */
  get actStart(): number {
    return this.act?.at ?? 0;
  }

  get active(): boolean {
    return this.act !== null;
  }

  /** Where a slot's cup stands, for the app's tap zones. */
  slotPoint(slot: number, y = 0.5): THREE.Vector3 {
    const l = this.layout;
    return new THREE.Vector3((slot - 1) * l.spacing, l.tableY + y * l.cupH, l.cupZ);
  }

  /** The points the table view keeps in frame: the top of her head as she leans, and the cups' reach. */
  framePoints(topY: number): THREE.Vector3[] {
    const l = this.layout;
    const w = l.spacing + 2 * l.cupR;
    const front = l.cupZ + l.arcFront + l.cupR;
    return [
      leaned(new THREE.Vector3(0, topY, 0), this.pivot, l.lean),
      new THREE.Vector3(-w, l.tableY, front),
      new THREE.Vector3(w, l.tableY, front),
      new THREE.Vector3(-w, l.tableY + l.cupH + l.liftH, l.cupZ),
      new THREE.Vector3(w, l.tableY + l.cupH + l.liftH, l.cupZ),
    ];
  }

  /**
   * Moves the table view in or out, and poses cups, ball, her lean and her arms for `now` (epoch ms). Call after
   * Behaviour and the gestures have posed her, before vrm.update. Returns where her eyes should go (her hands while
   * they work), or null.
   */
  update(now: number, dt: number): THREE.Vector3 | null {
    // The clock for easing her arms' body clearing: the act's time, so a frozen moment (the desktop probe) holds too.
    this.clearDt = this.clearAt === null ? 0 : Math.min(Math.max((now - this.clearAt) / 1000, 0), 0.1);
    this.clearAt = now;
    this.shown += Math.sign(this.target - this.shown) * Math.min(Math.abs(this.target - this.shown), dt / VIEW_S);
    this.group.visible = this.shown > 0;
    const spine = this.vrm.humanoid.getNormalizedBoneNode('spine');
    if (spine && this.shown > 0) spine.quaternion.premultiply(this.rig(new THREE.Quaternion().setFromAxisAngle(X, this.layout.lean * smooth(this.shown))));
    if (!this.act) {
      this.last.left = this.last.right = null;
      return null;
    }
    const frame = frameAt(this.act, now);
    this.place(frame);
    let look: THREE.Vector3 | null = null;
    const lead = this.act.kind === 'rest' ? 0 : this.act.leadMs;
    const u = lead > 0 ? smooth((now - this.act.at) / lead) : 1;
    for (const s of ['right', 'left'] as const) {
      const hand = frame[s];
      let at = hand ? this.handPoint(hand) : null;
      let w = hand?.w ?? 0;
      // During the act's lead-in, come from where the hand was when it arrived.
      const from = this.from[s];
      if (u < 1 && from && from.w > 0) {
        // From one cup to another between acts: over the cups, not through them.
        at = at ? from.pos.clone().lerp(at, u).setY(from.pos.y + (at.y - from.pos.y) * u + HOP * this.arm * Math.sin(Math.PI * u)) : from.pos;
        w = from.w + (w - from.w) * u;
      }
      this.last[s] = at && w > 0 ? { pos: at.clone(), w } : null;
      if (at && w > 0) {
        this.reach(s, at, Math.min(w * smooth(this.shown), 1));
        if (frame.busy) look = look ? look.lerp(at, 0.5) : at.clone();
      } else {
        this.clearing[s] = null;
      }
    }
    return look;
  }

  private place(frame: CupsFrame): void {
    const l = this.layout;
    frame.cups.forEach((c, i) => {
      const z = c.z >= 0 ? c.z * l.arcFront : c.z * l.arcBack;
      this.cups[i].position.set((c.x - 1) * l.spacing, l.tableY + c.lift * l.liftH, l.cupZ + z);
    });
    const under = this.cups[frame.ballCup].position;
    this.ball.position.set(under.x, l.tableY + l.cupR * 0.55, under.z);
  }

  /** Her wrist for a hand on a cup top: behind the top's centre, so the palm and fingers cover it. */
  private handPoint(h: HandPose): THREE.Vector3 {
    const l = this.layout;
    const z = h.z >= 0 ? h.z * l.arcFront : h.z * l.arcBack;
    return new THREE.Vector3(
      (h.x - 1) * l.spacing,
      l.tableY + l.cupH + h.lift * l.liftH + this.grip.up * this.arm,
      l.cupZ + z - (this.grip.back + Math.max(-h.z, 0) * this.grip.backShift) * this.arm,
    );
  }

  /** A rotation in body space (+X her left, facing +Z) as the rig's local rotation (VRM 0.x rigs face the other way). */
  private rig(q: THREE.Quaternion): THREE.Quaternion {
    const s = this.vrm.scene.getWorldQuaternion(new THREE.Quaternion());
    return s.clone().invert().multiply(q).multiply(s);
  }

  /**
   * Poses one arm so its wrist is at `target` (world) once `w` reaches 1. On the way (a hand coming to the table or
   * leaving it) the wrist travels from where her idle arm holds it up over the cups to the target: blending the two
   * arm poses joint by joint swept her hands through the cups (5 cm deep, probe of 2026-09-29, the tester saw it).
   */
  private clearAt: number | null = null;
  private clearDt = 0;
  /** Each arm's body-clearing correction, eased (see reach). */
  private readonly clearing: Record<Side, { push: THREE.Vector3; elbow: THREE.Vector3 } | null> = { left: null, right: null };

  private reach(s: Side, target: THREE.Vector3, w: number): void {
    const node = (name: VRMHumanBoneName) => this.vrm.humanoid.getNormalizedBoneNode(name);
    const upper = node(`${s}UpperArm`);
    const lower = node(`${s}LowerArm`);
    const hand = node(`${s}Hand`);
    if (!upper?.parent || !lower || !hand) return;
    let wrist = target;
    if (w < 1) {
      hand.updateWorldMatrix(true, false);
      const idle = hand.getWorldPosition(new THREE.Vector3());
      wrist = idle.lerp(target, w);
      wrist.y += HOP * 2 * this.arm * Math.sin(Math.PI * w);
      w = smooth(Math.min(1, w * 4)); // the arm leaves its idle pose early; after that the wrist leads
    }
    upper.parent.updateWorldMatrix(true, false);
    // The parent's turn in body space (her lean, breathing): solveArm works in the parent's frame.
    const sceneQ = this.vrm.scene.getWorldQuaternion(new THREE.Quaternion());
    const parent = upper.parent.getWorldQuaternion(new THREE.Quaternion()).multiply(sceneQ.clone().invert());
    const toParent = parent.clone().invert();
    const joint = upper.getWorldPosition(new THREE.Vector3());
    const local = wrist.clone().sub(joint).applyQuaternion(toParent);
    const sign = s === 'left' ? 1 : -1;
    // Elbows out to the side and down, as when leaning on a table.
    const cleared = clearArm(s, [local.x, local.y, local.z, sign, -0.8, -0.3, 0], { turn: [0, 0, 0], curl: 0.15 });
    // The correction eases in and out over CLEAR_S rather than jumping: switching on within a frame where a hand
    // reaches past her middle, it made the hands tremble (per-frame jumps of 4 cm, task 3.4).
    const push = new THREE.Vector3(cleared[0] - local.x, cleared[1] - local.y, cleared[2] - local.z);
    const elbow = new THREE.Vector3(cleared[3], cleared[4], cleared[5]);
    const eased = this.clearing[s];
    if (!eased) {
      this.clearing[s] = { push, elbow };
    } else {
      const k = 1 - Math.exp(-this.clearDt / CLEAR_S);
      eased.push.lerp(push, k);
      eased.elbow.lerp(elbow, k);
    }
    const c = this.clearing[s]!;
    const channel = [local.x + c.push.x, local.y + c.push.y, local.z + c.push.z, c.elbow.x, c.elbow.y, c.elbow.z, 0];
    const solved = solveArm(s, channel);
    // The hand lies along the table, pointing ahead, its fingers tipped a little down over the cup's rim.
    const dir = new THREE.Vector3(0, -Math.sin(this.grip.pitch), Math.cos(this.grip.pitch)).applyQuaternion(toParent);
    // Rolled about its length so the thumb side lifts off the cup (the thumb's root sank 1.3 cm into it).
    const handBody = new THREE.Quaternion()
      .setFromAxisAngle(dir, -sign * this.grip.roll)
      .multiply(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(sign, 0, 0), dir));
    const handLocal = solved.upper.clone().multiply(solved.lower).invert().multiply(handBody);
    upper.quaternion.slerp(this.rig(solved.upper), w);
    lower.quaternion.slerp(this.rig(solved.lower), w);
    hand.quaternion.slerp(this.rig(handLocal), w);
    for (const [bone, q] of Object.entries(curlFingers(s, this.grip.curl))) {
      node(bone as VRMHumanBoneName)?.quaternion.slerp(this.rig(q), w);
    }
    // The thumb lies along her hand instead of hanging down the cup's side. Euler angles are for her right hand; the
    // left mirrors them (y and z flip).
    const m = s === 'left' ? -1 : 1;
    const thumb = (bone: VRMHumanBoneName, [x, y, z]: readonly number[]) =>
      node(bone)?.quaternion.slerp(this.rig(new THREE.Quaternion().setFromEuler(new THREE.Euler(x, m * y, m * z))), w);
    thumb(`${s}ThumbMetacarpal`, this.grip.thumbBase);
    thumb(`${s}ThumbProximal`, this.grip.thumbMid);
    thumb(`${s}ThumbDistal`, this.grip.thumbTip);
  }

  /**
   * Debug (desktop preview): poses everything for `now` and measures each working hand against the cups, in cm:
   * `depth`, how far any finger or palm point sinks into a cup (fingers taken as 8 mm thick), and `gap`, how high
   * the palm floats over the top of the cup under it.
   */
  probe(now: number): Record<string, { depth: number; thumb: number; gap: number | null; slip: number }> {
    this.shown = 1;
    const spine = this.vrm.humanoid.getNormalizedBoneNode('spine');
    const saved = spine?.quaternion.clone();
    spine?.quaternion.identity(); // Behaviour resets it every frame; here only the lean goes on
    // Her idle arms, as Behaviour leaves them each frame, so a hand coming to the table starts from where it would.
    for (const s of ['left', 'right'] as const) {
      const rest = restArm(s);
      this.vrm.humanoid.getNormalizedBoneNode(`${s}UpperArm`)?.quaternion.copy(rest.upper);
      this.vrm.humanoid.getNormalizedBoneNode(`${s}LowerArm`)?.quaternion.copy(rest.lower);
      this.vrm.humanoid.getNormalizedBoneNode(`${s}Hand`)?.quaternion.identity();
      for (const [bone, q] of Object.entries(restFingers(s))) this.vrm.humanoid.getNormalizedBoneNode(bone as VRMHumanBoneName)?.quaternion.copy(q);
    }
    this.update(now, 0);
    this.vrm.scene.updateMatrixWorld(true);
    const l = this.layout;
    const out: Record<string, { depth: number; thumb: number; gap: number | null; slip: number }> = {};
    const at = (name: string) => this.vrm.humanoid.getNormalizedBoneNode(name as VRMHumanBoneName)?.getWorldPosition(new THREE.Vector3());
    for (const s of ['right', 'left'] as const) {
      if (!this.last[s]) continue;
      const wrist = at(`${s}Hand`)!;
      const points: THREE.Vector3[] = [wrist];
      const palm: THREE.Vector3[] = [wrist];
      for (const f of ['Index', 'Middle', 'Ring', 'Little']) {
        const p = at(`${s}${f}Proximal`)!;
        const i = at(`${s}${f}Intermediate`)!;
        const d = at(`${s}${f}Distal`)!;
        const tip = d.clone().add(d.clone().sub(i).multiplyScalar(0.9));
        points.push(p, i, d, tip, wrist.clone().lerp(p, 0.5));
        palm.push(p, wrist.clone().lerp(p, 0.5));
      }
      // The thumb too (the tester saw it sink into the cup once the fingers were clear).
      const tm = at(`${s}ThumbMetacarpal`);
      const tp = at(`${s}ThumbProximal`);
      const td = at(`${s}ThumbDistal`);
      const thumb = tm && tp && td ? [tm, tp, td, td.clone().add(td.clone().sub(tp).multiplyScalar(0.9)), tm.clone().lerp(tp, 0.5)] : [];
      points.push(...thumb);
      let depth = 0;
      let thumbDepth = 0;
      let gap: number | null = null;
      for (const cup of this.cups) {
        const c = cup.position;
        const into = (p: THREE.Vector3) => {
          const h = p.y - c.y;
          const rho = Math.hypot(p.x - c.x, p.z - c.z);
          const r = l.cupR * (1 - 0.3 * Math.min(Math.max(h / l.cupH, 0), 1));
          return h > -0.008 && h < l.cupH + 0.008 ? Math.min(r + 0.008 - rho, l.cupH + 0.008 - h) : 0;
        };
        for (const p of points) depth = Math.max(depth, into(p));
        for (const p of thumb) thumbDepth = Math.max(thumbDepth, into(p));
        for (const p of palm) {
          if (Math.hypot(p.x - c.x, p.z - c.z) > l.cupR * 0.7) continue;
          const g = p.y - 0.008 - (c.y + l.cupH);
          gap = gap === null ? g : Math.min(gap, g);
        }
      }
      // How far the wrist ended from where it was sent (clearing her body, or out of reach), horizontally.
      const sent = this.last[s]!.pos;
      const slip = Math.hypot(wrist.x - sent.x, wrist.z - sent.z);
      out[s] = { depth: Math.round(depth * 1000) / 10, thumb: Math.round(thumbDepth * 1000) / 10, gap: gap === null ? null : Math.round(gap * 1000) / 10, slip: Math.round(slip * 1000) / 10 };
    }
    if (saved) spine?.quaternion.copy(saved);
    return out;
  }
}

const X = new THREE.Vector3(1, 0, 0);
/** How high (in arms) a hand hops over the cups when it moves between them. */
const HOP = 0.12;
/** How quickly an arm's body-clearing correction follows (seconds, time constant). */
const CLEAR_S = 0.05;

/** Seconds for the camera to move between her usual framing and the table. */
export const VIEW_S = 0.6;
