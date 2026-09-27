// What a model must have before it ships as Rin. Each rule names its source, so a failure says what to fix in VRoid.

/** Expressions the page drives (emotion.ts, behaviour.ts) and lip sync (task 2.4) needs. */
export const REQUIRED_EXPRESSIONS = ['happy', 'angry', 'sad', 'relaxed', 'surprised', 'blink', 'aa', 'ih', 'ou', 'ee', 'oh'];
/** VRoid per-part morphs vroid.ts builds the eyes-open `smile` from. Without them she smiles with closed eyes. */
export const SMILE_MORPHS = ['Fcl_MTH_Fun', 'Fcl_BRW_Fun'];
/** VRM 1.0 meta the brief (docs/character/vroid-brief.md, step 5) asks for: the app is public, the model is not. */
export const REQUIRED_META = {
  avatarPermission: 'onlyAuthor',
  allowExcessivelyViolentUsage: false,
  allowExcessivelySexualUsage: false,
  commercialUsage: 'personalNonProfit',
  allowRedistribution: false,
  modification: 'prohibited',
};
export const LIMITS = { fileBytes: 15 * 1024 * 1024, textureSide: 2048 };

/** Morph target names across all meshes (VRoid writes them to mesh.extras.targetNames). */
export function morphNames(json) {
  const names = new Set();
  for (const mesh of json.meshes ?? []) {
    for (const name of mesh.extras?.targetNames ?? []) names.add(name);
    for (const p of mesh.primitives ?? []) for (const name of p.extras?.targetNames ?? []) names.add(name);
  }
  return names;
}

/**
 * @param json    the glTF JSON
 * @param facts   { fileBytes, images: [{ index, width, height }] } measured from the optimized file
 * @returns       { errors: string[], warnings: string[], stats }
 */
export function checkModel(json, facts) {
  const errors = [];
  const warnings = [];
  const vrm = json.extensions?.VRMC_vrm;
  if (!vrm) {
    errors.push(json.extensions?.VRM ? 'VRM 0.x file: export as VRM 1.0 (brief step 1)' : 'not a VRM file (no VRMC_vrm)');
    return { errors, warnings, stats: {} };
  }

  const expressions = new Set([
    ...Object.keys(vrm.expressions?.preset ?? {}),
    ...Object.keys(vrm.expressions?.custom ?? {}),
  ]);
  const missingExpressions = REQUIRED_EXPRESSIONS.filter((e) => !expressions.has(e));
  if (missingExpressions.length) errors.push(`missing expressions: ${missingExpressions.join(', ')} (keep VRoid's defaults)`);

  const morphs = morphNames(json);
  const missingMorphs = SMILE_MORPHS.filter((m) => !morphs.has(m));
  if (missingMorphs.length) errors.push(`missing morphs ${missingMorphs.join(', ')}: the eyes-open smile needs them (vroid.ts)`);

  const meta = vrm.meta ?? {};
  for (const [key, want] of Object.entries(REQUIRED_META)) {
    if (meta[key] !== want) errors.push(`meta.${key} is ${JSON.stringify(meta[key])}, brief asks for ${JSON.stringify(want)}`);
  }
  if (!meta.authors?.length) errors.push('meta.authors is empty');

  const bones = vrm.humanoid?.humanBones ?? {};
  for (const bone of ['head', 'neck', 'leftEye', 'rightEye']) {
    if (bones[bone]?.node === undefined) errors.push(`humanoid has no ${bone} bone (look-at and framing use it)`);
  }

  if (facts.fileBytes > LIMITS.fileBytes) {
    errors.push(`file is ${mb(facts.fileBytes)} MB, limit ${mb(LIMITS.fileBytes)} MB (export with a 1024 atlas for clothes)`);
  }
  const big = facts.images.filter((i) => Math.max(i.width, i.height) > LIMITS.textureSide);
  if (big.length) errors.push(`${big.length} textures larger than ${LIMITS.textureSide} px`);

  const springJoints = (json.extensions?.VRMC_springBone?.springs ?? []).reduce((n, s) => n + (s.joints?.length ?? 0), 0);
  // The VRoid sample has 179 by this count (S2 counted 132 by another rule) and runs at the 30 fps cap on the 14T.
  if (springJoints > 179) warnings.push(`${springJoints} spring-bone joints; the sample's 179 is the most measured on the 14T`);
  const materials = json.materials?.length ?? 0;
  if (materials > 20) warnings.push(`${materials} materials; turn on "Reduce materials" at export`);

  return {
    errors,
    warnings,
    stats: {
      fileMB: Number(mb(facts.fileBytes)),
      materials,
      meshes: json.meshes?.length ?? 0,
      textures: json.textures?.length ?? 0,
      springJoints,
      maxTextureSide: Math.max(0, ...facts.images.map((i) => Math.max(i.width, i.height))),
    },
  };
}

const mb = (bytes) => (bytes / 1048576).toFixed(1);
