// Everything that keeps Rin alive between gestures (task 2.2): breathing, blinking, looking at the user with small
// eye movements and the odd glance away, following a finger on the strip, blending moods, and the head-tap
// reaction. The numbers behind moods and the tap live in emotion.ts; this file turns them into bones and expressions.
// Task 2.4 layers gestures (gesture.ts) over the idle pose and the mouth (mouth.ts) over the face. Since task 2.5 the
// idle arms come from the gesture builder's rest pose, cleared of her body (scripts/vrma.mjs restArm), so her hands
// rest beside her clothes rather than in them, and gestures start and end exactly where she stands.
import * as THREE from 'three';
import { restArm, restFingers } from '../scripts/vrma.mjs';
import type { VRM, VRMHumanBoneName } from '@pixiv/three-vrm';
import { Blend, EXPRESSIONS, TapReaction, moodChannels, type Mood } from './emotion';
import type { GesturePlayer } from './gesture';
import { MouthPlayer, VISEMES, gestureFace, talkEnvelope } from './mouth';

const rand = (min: number, max: number) => min + Math.random() * (max - min);
const REST_ARMS = { left: restArm('left'), right: restArm('right') };
const REST_FINGERS = Object.entries({ ...restFingers('left'), ...restFingers('right') }) as [VRMHumanBoneName, THREE.Quaternion][];
const Z = new THREE.Vector3(0, 0, 1);
/** Frame-rate independent exponential approach: the fraction of the gap to close this frame. */
const approach = (rate: number, dt: number) => 1 - Math.exp(-rate * dt);

export class Behaviour {
  readonly tap = new TapReaction();
  readonly mouth = new MouthPlayer();
  /** Set once the gesture files have loaded (after the first frame, so they never delay her appearing). */
  gestures: GesturePlayer | null = null;
  private readonly blend: Blend;
  private t = 0;

  private nextBlink = 2;
  private blinkT = -1;
  /** 0 quiet .. 1 speaking, eased (mouth.ts talkEnvelope). */
  private talk = 0;

  /** What her eyes look at (three-vrm aims the eyes at it); it chases `desired` quickly, like a saccade. */
  private readonly target = new THREE.Object3D();
  private readonly desired = new THREE.Vector3();
  private finger: THREE.Vector3 | null = null;
  private readonly saccade = new THREE.Vector2();
  private nextSaccade = 1;
  private readonly glance = new THREE.Vector2();
  private glanceUntil = 0;
  private nextGlance = rand(6, 12);

  /** How far the head (and neck) turn toward the target, smoothed so the head lags the eyes. */
  private lookYaw = 0;
  private lookPitch = 0;
  private readonly headPos = new THREE.Vector3();
  private readonly headCenter = new THREE.Vector3();
  /** Without VRoid's eyes-open smile (vroid.ts), a little of `happy` stands in for it. */
  private readonly hasSmile: boolean;
  private readonly breathArm = new THREE.Quaternion();
  /** Set only inside rest(): eyes and head jump to where they are heading instead of easing there. */
  private snap = false;

  /**
   * How much of the way to the camera (the user) her head turns, 0..1 (task 3.7). At 1 she meets the user's eyes: the
   * eyes alone cannot, as VRoid's turn only a ninth of what is asked (10° for 90°), and following a third of the way
   * left her gazing 6° over the user in the full-body view, 12° with the proud mood's raised chin (the tester saw it
   * as she clapped). The cup table lowers it while her hands move the cups (main.ts, CupScene.attend): she watches
   * them then, and the user again once her hands are free.
   */
  aimCamera = 1;

  /**
   * @param headCenterUp  metres from the head bone up to the middle of the head (hair included)
   * @param headRadius    radius of the sphere that counts as "the head" for taps
   */
  constructor(
    private readonly vrm: VRM,
    private readonly camera: THREE.Camera,
    mood: Mood,
    intensity: number,
    private readonly headCenterUp: number,
    private readonly headRadius: number,
  ) {
    this.blend = new Blend(moodChannels(mood, intensity));
    this.hasSmile = !!vrm.expressionManager?.getExpression('smile');
    this.target.position.copy(camera.position);
    if (vrm.lookAt) vrm.lookAt.target = this.target;
  }

  setMood(mood: Mood, intensity: number): void {
    this.blend.set(moodChannels(mood, intensity));
  }

  /** A finger on the strip, as a point in the world, or null once it lifts (her gaze drifts back to the user). */
  follow(point: THREE.Vector3 | null): void {
    this.finger = point ? (this.finger ?? new THREE.Vector3()).copy(point) : null;
  }

  /** Whether a ray from the camera (a tap) passes through her head, hair included. */
  hitsHead(ray: THREE.Ray): boolean {
    const head = this.vrm.humanoid.getNormalizedBoneNode('head');
    if (!head) return false;
    head.getWorldPosition(this.headCenter).y += this.headCenterUp;
    return ray.intersectsSphere(new THREE.Sphere(this.headCenter, this.headRadius));
  }

  /**
   * Her resting pose for a still image (task 2.5): the mood fully shown, eyes open (a sleepy lid stays), no breath or
   * sway, and eyes and head already where the mood looks. Call it on a fresh Behaviour, before any update.
   */
  rest(): void {
    this.snap = true;
    this.update(0);
    this.snap = false;
  }

  /** Advances everything by dt seconds; returns true on the frame a mood blend finishes. */
  update(dt: number): boolean {
    this.t += dt;
    const done = this.blend.step(dt);
    this.tap.step(dt);
    const c = this.tap.apply(this.blend.values);
    this.gestures?.update(dt);
    this.look(dt, c.gaze, c.pitch);
    this.body(c.pitch + this.tap.nod, c.yaw, c.roll);
    this.gestures?.applyBones(this.vrm);
    this.face(dt, c);
    return done;
  }

  private look(dt: number, gaze: number, moodPitch: number): void {
    const t = this.t;
    if (t > this.nextSaccade) {
      this.saccade.set(rand(-0.02, 0.02), rand(-0.015, 0.015));
      this.nextSaccade = t + rand(0.8, 2.5);
    }
    if (t > this.nextGlance) {
      this.glance.set(rand(0.2, 0.35) * (Math.random() < 0.5 ? -1 : 1), rand(-0.05, 0.1));
      this.glanceUntil = t + rand(0.4, 0.8);
      this.nextGlance = t + rand(6, 12);
    }
    if (this.finger) {
      this.desired.copy(this.finger);
    } else {
      const away = t < this.glanceUntil ? this.glance : null;
      this.desired.copy(this.camera.position);
      this.desired.x += this.saccade.x + (away?.x ?? 0) + gaze;
      this.desired.y += this.saccade.y + (away?.y ?? 0);
    }
    this.target.position.lerp(this.desired, this.snap ? 1 : approach(25, dt));

    // The head turns toward the user by aimCamera, less the mood's own pitch (body() adds it back), so a raised chin
    // still meets their eyes; toward anything else (a saccade, a glance, a finger, the cups) it follows a third of the
    // way. Within limits, and more slowly than the eyes.
    const head = this.vrm.humanoid.getNormalizedBoneNode('head');
    if (!head) return;
    head.getWorldPosition(this.headPos);
    const angles = (p: THREE.Vector3) => {
      const d = p.clone().sub(this.headPos);
      return [Math.atan2(d.x, d.z), -Math.atan2(d.y, Math.hypot(d.x, d.z))];
    };
    const [camYaw, camPitch] = angles(this.camera.position);
    const [toYaw, toPitch] = angles(this.target.position);
    const a = this.aimCamera;
    const baseYaw = a * camYaw;
    const basePitch = a * camPitch;
    const yaw = THREE.MathUtils.clamp(baseYaw + 0.35 * (toYaw - baseYaw), -0.35, 0.35);
    const pitchLimit = 0.25 + 0.1 * a;
    const pitch = THREE.MathUtils.clamp(basePitch + 0.35 * (toPitch - basePitch) - a * moodPitch, -pitchLimit, pitchLimit);
    const k = this.snap ? 1 : approach(5, dt);
    this.lookYaw += (yaw - this.lookYaw) * k;
    this.lookPitch += (pitch - this.lookPitch) * k;
  }

  private body(pitch: number, yaw: number, roll: number): void {
    const t = this.t;
    const rot = (bone: VRMHumanBoneName, x: number, y: number, z: number) =>
      this.vrm.humanoid.getNormalizedBoneNode(bone)?.rotation.set(x, y, z);
    const breath = Math.sin((t * 2 * Math.PI) / 4); // one breath every 4 s
    for (const [s, sign] of [['left', 1], ['right', -1]] as const) {
      const bone = (name: VRMHumanBoneName) => this.vrm.humanoid.getNormalizedBoneNode(name)?.quaternion;
      bone(`${s}UpperArm`)?.copy(REST_ARMS[s].upper).multiply(this.breathArm.setFromAxisAngle(Z, sign * 0.03 * breath));
      bone(`${s}LowerArm`)?.copy(REST_ARMS[s].lower);
    }
    for (const [name, q] of REST_FINGERS) this.vrm.humanoid.getNormalizedBoneNode(name)?.quaternion.copy(q);
    rot('chest', 0.03 * breath, 0, 0);
    // Gestures add rotation to these (gesture.ts ADDITIVE), so they must start every frame from rest.
    rot('upperChest', 0, 0, 0);
    rot('leftShoulder', 0, 0, 0);
    rot('rightShoulder', 0, 0, 0);
    const sway = 0.04 * Math.sin(t * 0.7);
    rot('spine', 0, sway, 0.02 * Math.sin(t * 0.5));
    rot('neck', 0.4 * this.lookPitch, 0.4 * this.lookYaw, 0);
    // While she looks at the user, the head turns against the body's sway, as a person's does to hold eye contact.
    rot(
      'head',
      0.6 * this.lookPitch + pitch + 0.03 * Math.sin(t * 0.9),
      0.6 * this.lookYaw + yaw + 0.03 * Math.sin(t * 0.6) - this.aimCamera * sway,
      roll,
    );
  }

  private face(dt: number, c: Readonly<Record<string, number>>): void {
    const em = this.vrm.expressionManager;
    if (!em) return;
    const gesture = this.gestures?.face() ?? { weights: {}, takeover: 0 };
    const g = (name: string) => gesture.weights[name] ?? 0;
    // Speaking opens the mouth over the mood's own mouth shape, so a smile softens while she talks.
    const mouth = this.mouth.at(Date.now());
    this.talk = talkEnvelope(this.talk, mouth !== null, dt);
    let open = 0;
    for (const v of VISEMES) {
      const value = Math.max(g(v), mouth?.[v] ?? 0);
      em.setValue(v, value);
      open += value;
    }
    const mood = (1 - 0.8 * gesture.takeover) * (1 - 0.5 * Math.min(open, 1));
    // A gesture's face steps back while she talks too, or its mouth shape hides her lips (the win screen's joy).
    const ge = (e: string) => gestureFace(g(e), this.talk);
    for (const e of EXPRESSIONS) em.setValue(e, Math.max(c[e] * mood, ge(e)));
    if (!this.hasSmile) em.setValue('happy', Math.min(Math.max(c.happy * mood, ge('happy')) + 0.35 * c.smile * mood, 1));
    const t = this.t;
    let pulse = 0;
    if (this.blinkT < 0 && t > this.nextBlink) this.blinkT = 0;
    if (this.blinkT >= 0) {
      this.blinkT += dt;
      pulse = Math.sin(Math.min(this.blinkT / 0.15, 1) * Math.PI);
      if (this.blinkT > 0.15) {
        this.blinkT = -1;
        this.nextBlink = t + rand(2, 5);
        pulse = 0;
      }
    }
    em.setValue('blink', Math.max(c.lid, pulse, g('blink'))); // a sleepy resting lid, and blinks on top of it
  }
}
