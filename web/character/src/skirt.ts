// Keeps her arms from lifting her skirt (task 3.3, tester's report: the skirt flipped up as she waved). VRoid puts
// spring-bone colliders on the arms and hands, and the skirt's joints collide with them: with her hands resting
// beside the skirt, an arm rising for a wave drags the skirt up with it. Here the skirt's joints (spring bones below
// the hips, not under the spine) stop colliding with anything on the arms; hair keeps every collider.
import type * as THREE from 'three';
import type { VRMHumanBoneName } from '@pixiv/three-vrm';

const ARM_BONES: VRMHumanBoneName[] = [
  'leftUpperArm', 'leftLowerArm', 'leftHand',
  'rightUpperArm', 'rightLowerArm', 'rightHand',
];

/** The small part of the VRM and spring-bone API this needs, so tests can pass plain objects. */
export interface SpringModel {
  humanoid: { getRawBoneNode(name: VRMHumanBoneName): THREE.Object3D | null };
  springBoneManager?: {
    joints: Set<{ bone: THREE.Object3D; colliderGroups: { colliders: THREE.Object3D[]; name?: string }[] }>;
  } | null;
}

const under = (o: THREE.Object3D | null, ancestors: Set<THREE.Object3D>): boolean => {
  for (let p = o; p; p = p.parent) if (ancestors.has(p)) return true;
  return false;
};

/** Returns how many skirt joints lost arm colliders. */
export function keepArmsOffSkirt(vrm: SpringModel): number {
  const manager = vrm.springBoneManager;
  const hips = vrm.humanoid.getRawBoneNode('hips');
  const spine = vrm.humanoid.getRawBoneNode('spine');
  if (!manager || !hips) return 0;
  const arms = new Set(ARM_BONES.map((n) => vrm.humanoid.getRawBoneNode(n)).filter((b): b is THREE.Object3D => !!b));
  const hipsOnly = new Set([hips]);
  const upperBody = new Set(spine ? [spine] : []);
  const copies = new Map<object, { colliders: THREE.Object3D[]; name?: string }>();
  let changed = 0;
  for (const joint of manager.joints) {
    if (!under(joint.bone, hipsOnly) || under(joint.bone, upperBody)) continue;
    let touched = false;
    joint.colliderGroups = joint.colliderGroups.map((group) => {
      let copy = copies.get(group);
      if (!copy) {
        copy = { ...group, colliders: group.colliders.filter((c) => !under(c, arms)) };
        copies.set(group, copy);
      }
      if (copy.colliders.length !== group.colliders.length) touched = true;
      return copy.colliders.length === group.colliders.length ? group : copy;
    });
    if (touched) changed++;
  }
  return changed;
}
