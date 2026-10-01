// Test models for the model tools: a tiny VRM 1.0 that passes every check, with knobs to break one rule at a time.
import sharp from 'sharp';
import { writeGlb } from './glb.mjs';
import { REQUIRED_BONES, REQUIRED_EXPRESSIONS, REQUIRED_META } from './check.mjs';

export const png = (side, color = '#8060c0') =>
  sharp({ create: { width: side, height: side, channels: 4, background: color } }).png().toBuffer();

/** A tiny VRM 1.0 that passes every check: one textured MToon material, a normal map, a big thumbnail. */
export async function tinyVrm({ meta = {}, drop = [], dropBones = [] } = {}) {
  const images = [await png(128), await png(128, '#8080ff'), await png(512, '#ffffff')];
  const views = [new Uint8Array([1, 2, 3, 4, 5, 6]), ...images.map((b) => new Uint8Array(b))];
  const preset = Object.fromEntries(REQUIRED_EXPRESSIONS.filter((e) => !drop.includes(e)).map((e) => [e, {}]));
  const json = {
    asset: { version: '2.0' },
    buffers: [{ byteLength: 0 }],
    bufferViews: views.map((v) => ({ buffer: 0, byteLength: v.byteLength })),
    images: images.map((_, i) => ({ bufferView: i + 1, mimeType: 'image/png', name: `img${i}` })),
    textures: [{ source: 0 }, { source: 1 }, { source: 2 }],
    materials: [
      {
        pbrMetallicRoughness: { baseColorTexture: { index: 0 } },
        normalTexture: { index: 1 },
        extensions: { VRMC_materials_mtoon: { shadeMultiplyTexture: { index: 0 } } },
      },
    ],
    meshes: [{ primitives: [], extras: { targetNames: ['Fcl_MTH_Fun', 'Fcl_BRW_Fun'].filter((m) => !drop.includes(m)) } }],
    extensionsUsed: ['VRMC_vrm'],
    extensions: {
      VRMC_vrm: {
        meta: { name: 'test', authors: ['me'], thumbnailImage: 2, ...REQUIRED_META, ...meta },
        humanoid: { humanBones: Object.fromEntries(REQUIRED_BONES.filter((bone) => !dropBones.includes(bone)).map((bone) => [bone, { node: 0 }])) },
        expressions: { preset },
      },
    },
  };
  return writeGlb(json, views);
}
