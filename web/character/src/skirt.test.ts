import * as THREE from 'three';
import { describe, expect, it } from 'vitest';
import { keepArmsOffSkirt } from './skirt';

function model() {
  const bone = (name: string, parent?: THREE.Object3D) => {
    const o = new THREE.Object3D();
    o.name = name;
    parent?.add(o);
    return o;
  };
  const hips = bone('hips');
  const spine = bone('spine', hips);
  const head = bone('head', spine);
  const arm = bone('rightUpperArm', spine);
  const hand = bone('rightHand', arm);
  const skirt = bone('skirt', hips);
  const hair = bone('hair', head);
  const handCollider = bone('handCollider', hand);
  const hipCollider = bone('hipCollider', hips);
  const group = { name: 'body', colliders: [handCollider, hipCollider] };
  const bones: Record<string, THREE.Object3D> = { hips, spine, head, rightUpperArm: arm, rightHand: hand };
  const skirtJoint = { bone: skirt, colliderGroups: [group] };
  const hairJoint = { bone: hair, colliderGroups: [group] };
  const vrm = {
    humanoid: { getRawBoneNode: (n: string) => bones[n] ?? null },
    springBoneManager: { joints: new Set([skirtJoint, hairJoint]) },
  };
  return { vrm, skirtJoint, hairJoint, handCollider, hipCollider };
}

describe('keepArmsOffSkirt', () => {
  it('drops arm colliders from skirt joints only', () => {
    const m = model();
    expect(keepArmsOffSkirt(m.vrm as never)).toBe(1);
    expect(m.skirtJoint.colliderGroups[0].colliders).toEqual([m.hipCollider]);
    // Hair shares the group but keeps the hand: the group was copied, not edited.
    expect(m.hairJoint.colliderGroups[0].colliders).toEqual([m.handCollider, m.hipCollider]);
  });

  it('does nothing without spring bones', () => {
    const m = model();
    expect(keepArmsOffSkirt({ ...m.vrm, springBoneManager: null } as never)).toBe(0);
  });
});
