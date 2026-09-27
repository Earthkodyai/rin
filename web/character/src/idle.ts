// A minimal idle so Rin never looks frozen: arms down, breathing, a little sway, blinking. Task 2.2 replaces this
// with the full behaviour layer (look-at, emotions, head tap).
import type { VRM, VRMHumanBoneName } from '@pixiv/three-vrm';

export class Idle {
  private t = 0;
  private nextBlink = 2;
  private blinkT = -1;

  constructor(private readonly vrm: VRM) {}

  update(dt: number): void {
    this.t += dt;
    const t = this.t;
    const rot = (bone: VRMHumanBoneName, x: number, y: number, z: number) =>
      this.vrm.humanoid.getNormalizedBoneNode(bone)?.rotation.set(x, y, z);
    const breath = Math.sin((t * 2 * Math.PI) / 4); // one breath every 4 s
    rot('leftUpperArm', 0, 0, -1.2 + 0.03 * breath);
    rot('rightUpperArm', 0, 0, 1.2 - 0.03 * breath);
    rot('leftLowerArm', 0, 0, -0.15);
    rot('rightLowerArm', 0, 0, 0.15);
    rot('chest', 0.03 * breath, 0, 0);
    rot('spine', 0, 0.05 * Math.sin(t * 0.7), 0.02 * Math.sin(t * 0.5));
    rot('head', 0.04 * Math.sin(t * 0.9), 0.08 * Math.sin(t * 0.6), 0);

    const em = this.vrm.expressionManager;
    if (!em) return;
    if (this.blinkT < 0 && t > this.nextBlink) this.blinkT = 0;
    if (this.blinkT >= 0) {
      this.blinkT += dt;
      em.setValue('blink', Math.sin(Math.min(this.blinkT / 0.15, 1) * Math.PI));
      if (this.blinkT > 0.15) {
        this.blinkT = -1;
        this.nextBlink = t + 2 + Math.random() * 3;
        em.setValue('blink', 0);
      }
    }
  }
}
