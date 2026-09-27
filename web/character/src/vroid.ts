// Extra expressions built from VRoid Studio's per-part morphs. VRoid's `happy` and `relaxed` presets close the eyes
// (Fcl_ALL_Joy / Fcl_ALL_Fun), so a cheerful Rin could never look at the user. `smile` moves only the mouth and
// brows. Register before VRMUtils.combineMorphs, which merges every registered expression's morphs. On a model
// without these morphs nothing is added, and Behaviour falls back to a lighter `happy`.
import * as THREE from 'three';
import { VRMExpression, VRMExpressionMorphTargetBind, type VRM } from '@pixiv/three-vrm';

const SMILE: Record<string, number> = { Fcl_MTH_Fun: 1, Fcl_BRW_Fun: 1 };

/** Adds `smile` when the model has VRoid's mouth and brow morphs; returns whether it did. */
export function addVroidSmile(vrm: VRM): boolean {
  const em = vrm.expressionManager;
  if (!em || em.getExpression('smile')) return !!em;
  const expression = new VRMExpression('smile');
  const found = new Set<string>();
  vrm.scene.traverse((o) => {
    const mesh = o as THREE.Mesh;
    const dictionary = mesh.isMesh ? mesh.morphTargetDictionary : undefined;
    if (!dictionary) return;
    for (const [name, weight] of Object.entries(SMILE)) {
      const index = dictionary[name];
      if (index === undefined) continue;
      expression.addBind(new VRMExpressionMorphTargetBind({ primitives: [mesh], index, weight }));
      found.add(name);
    }
  });
  if (found.size !== Object.keys(SMILE).length) return false;
  em.registerExpression(expression);
  vrm.scene.add(expression);
  return true;
}
