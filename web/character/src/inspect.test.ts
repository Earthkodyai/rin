import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { clearArm, pushOut, restPosition, solveArm, ARM_REST } from '../scripts/vrma.mjs';
import { convexHull, insideDepth } from './inspect';

describe('convexHull and insideDepth (inspect.ts)', () => {
  // A 0.2 x 0.1 box with a point in the middle, which the hull drops.
  const hull = convexHull([-0.1, -0.05, 0.1, -0.05, 0.1, 0.05, -0.1, 0.05, 0, 0]);

  it('keeps the outline, counter-clockwise', () => {
    expect(hull).toHaveLength(8);
    let area = 0;
    for (let i = 0; i < 4; i++) area += hull[i * 2] * hull[((i + 1) % 4) * 2 + 1] - hull[((i + 1) % 4) * 2] * hull[i * 2 + 1];
    expect(area / 2).toBeCloseTo(0.02, 6); // positive: counter-clockwise
  });

  it('measures how deep a point is, to the nearest edge', () => {
    expect(insideDepth(hull, 0, 0)).toBeCloseTo(0.05, 6);
    expect(insideDepth(hull, 0.09, 0)).toBeCloseTo(0.01, 6);
    expect(insideDepth(hull, 0.2, 0)).toBe(0);
  });
});

describe('arm clearance (scripts/vrma.mjs, src/body.json)', () => {
  const elbowAndHand = (side: 'left' | 'right', channel: number[]) => {
    const joint = restPosition(`${side}UpperArm`);
    const { elbow, hand } = solveArm(side, channel);
    return [elbow.add(joint), hand.add(joint)];
  };

  it('moves a hand asked into her chest out of it', () => {
    // Crossed arms, with the hands where her chest is (the pout key before task 2.5 moved it forward).
    const inside: number[] = [-0.1, -0.13, 0.1, 1, -0.3, -0.2, -0.79];
    const [, before] = elbowAndHand('left', inside);
    expect(Math.hypot(...pushOut(before, 0.02))).toBeGreaterThan(0.03);
    const [elbow, hand] = elbowAndHand('left', clearArm('left', inside, { turn: [0, 0, 0], curl: 0.6 }));
    expect(Math.hypot(...pushOut(hand, 0.018))).toBeLessThan(0.005);
    expect(Math.hypot(...pushOut(elbow, 0.02))).toBeLessThan(0.005);
  });

  it('leaves an arm that is already clear alone', () => {
    const wave = [-0.1, 0.07, 0.07, -1, -0.8, -0.2, 0];
    expect(clearArm('right', wave)).toEqual(wave);
  });

  it('keeps the twist, and a small change in the key makes a small change in the result (smooth paths)', () => {
    const a = clearArm('left', ARM_REST.left);
    const b = clearArm('left', ARM_REST.left.map((v, i) => (i === 1 ? v + 0.001 : v)));
    expect(a[6]).toBe(ARM_REST.left[6]);
    expect(new THREE.Vector3(...a.slice(0, 3)).distanceTo(new THREE.Vector3(...b.slice(0, 3)))).toBeLessThan(0.01);
  });
});
