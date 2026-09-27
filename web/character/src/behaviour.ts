// Everything that keeps Rin alive between gestures (task 2.2): breathing, blinking, looking at the user with small
// eye movements and the odd glance away, following a finger on the strip, blending moods, and the head-tap
// reaction. The numbers behind moods and the tap live in emotion.ts; this file turns them into bones and expressions.
import * as THREE from 'three';
import type { VRM, VRMHumanBoneName } from '@pixiv/three-vrm';
import { Blend, EXPRESSIONS, TapReaction, moodChannels, type Mood } from './emotion';

const rand = (min: number, max: number) => min + Math.random() * (max - min);
/** Frame-rate independent exponential approach: the fraction of the gap to close this frame. */
const approach = (rate: number, dt: number) => 1 - Math.exp(-rate * dt);

export class Behaviour {
  readonly tap = new TapReaction();
  private readonly blend: Blend;
  private t = 0;

  private nextBlink = 2;
  private blinkT = -1;

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

  /** Advances everything by dt seconds; returns true on the frame a mood blend finishes. */
  update(dt: number): boolean {
    this.t += dt;
    const done = this.blend.step(dt);
    this.tap.step(dt);
    const c = this.tap.apply(this.blend.values);
    this.look(dt, c.gaze);
    this.body(c.pitch + this.tap.nod, c.yaw, c.roll);
    this.face(dt, c);
    return done;
  }

  private look(dt: number, gaze: number): void {
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
    this.target.position.lerp(this.desired, approach(25, dt));

    // The head follows about a third of the way, within limits, and more slowly than the eyes.
    const head = this.vrm.humanoid.getNormalizedBoneNode('head');
    if (!head) return;
    const d = this.target.position.clone().sub(head.getWorldPosition(this.headPos));
    const yaw = THREE.MathUtils.clamp(0.35 * Math.atan2(d.x, d.z), -0.35, 0.35);
    const pitch = THREE.MathUtils.clamp(-0.35 * Math.atan2(d.y, Math.hypot(d.x, d.z)), -0.25, 0.25);
    const k = approach(5, dt);
    this.lookYaw += (yaw - this.lookYaw) * k;
    this.lookPitch += (pitch - this.lookPitch) * k;
  }

  private body(pitch: number, yaw: number, roll: number): void {
    const t = this.t;
    const rot = (bone: VRMHumanBoneName, x: number, y: number, z: number) =>
      this.vrm.humanoid.getNormalizedBoneNode(bone)?.rotation.set(x, y, z);
    const breath = Math.sin((t * 2 * Math.PI) / 4); // one breath every 4 s
    rot('leftUpperArm', 0, 0, -1.2 + 0.03 * breath);
    rot('rightUpperArm', 0, 0, 1.2 - 0.03 * breath);
    rot('leftLowerArm', 0, 0, -0.15);
    rot('rightLowerArm', 0, 0, 0.15);
    rot('chest', 0.03 * breath, 0, 0);
    rot('spine', 0, 0.04 * Math.sin(t * 0.7), 0.02 * Math.sin(t * 0.5));
    rot('neck', 0.4 * this.lookPitch, 0.4 * this.lookYaw, 0);
    rot(
      'head',
      0.6 * this.lookPitch + pitch + 0.03 * Math.sin(t * 0.9),
      0.6 * this.lookYaw + yaw + 0.03 * Math.sin(t * 0.6),
      roll,
    );
  }

  private face(dt: number, c: Readonly<Record<string, number>>): void {
    const em = this.vrm.expressionManager;
    if (!em) return;
    for (const e of EXPRESSIONS) em.setValue(e, c[e]);
    if (!this.hasSmile) em.setValue('happy', Math.min(c.happy + 0.35 * c.smile, 1));
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
    em.setValue('blink', Math.max(c.lid, pulse)); // a sleepy resting lid, and blinks on top of it
  }
}
