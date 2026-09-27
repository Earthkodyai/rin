#!/usr/bin/env node
// Turns a VRoid Studio export into the model the app ships (task 2.3):
//   1. shrinks the meta thumbnail (only VRM viewers show it; the app never uploads it)
//   2. caps texture sides at --max
//   3. re-encodes material textures as KTX2 (KHR_texture_basisu), so the GPU keeps them block-compressed:
//      8 bits per pixel on the phone instead of 32, which is where S2's 620-720 MB went
//   4. checks what Rin needs (check.mjs) and fails the build on anything missing
//
// usage: node optimize-vrm.mjs <in.vrm> <out.vrm> [--mode etc1s|uastc|png] [--max 2048] [--report report.json] [--dev]
// --dev turns check failures into warnings: for the VRoid sample the debug build uses, which is not Rin.
//
// The JSON is left exactly as VRoid wrote it apart from texture extensions, so every index the VRM extensions hold
// stays valid; only image bytes change (glb.mjs).

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { encodeToKTX2 } from 'ktx2-encoder';
import { readGlb, writeGlb } from './glb.mjs';
import { checkModel } from './check.mjs';

const MODES = ['uastc', 'etc1s', 'png'];
/** Images this small cost nothing on the GPU; they stay PNG. */
const TINY = 64;
const THUMBNAIL_SIDE = 256;

/** MToon (VRM 1.0) texture slots and the colour space each one holds. */
const MTOON_SLOTS = {
  shadeMultiplyTexture: 'color',
  matcapTexture: 'color',
  rimMultiplyTexture: 'color',
  shadingShiftTexture: 'data',
  outlineWidthMultiplyTexture: 'data',
  uvAnimationMaskTexture: 'data',
};

/** image index -> 'color' | 'normal' | 'data', for every image a material samples. */
export function imageRoles(json) {
  const roles = new Map();
  const rank = { data: 0, color: 1, normal: 2 }; // an image used two ways keeps the strictest encoding
  const use = (ref, role) => {
    if (!ref) return;
    const texture = json.textures?.[ref.index];
    const image = texture?.source ?? texture?.extensions?.KHR_texture_basisu?.source;
    if (image === undefined) return;
    const had = roles.get(image);
    if (!had || rank[role] > rank[had]) roles.set(image, role);
  };
  for (const m of json.materials ?? []) {
    const pbr = m.pbrMetallicRoughness ?? {};
    use(pbr.baseColorTexture, 'color');
    use(pbr.metallicRoughnessTexture, 'data');
    use(m.emissiveTexture, 'color');
    use(m.occlusionTexture, 'data');
    use(m.normalTexture, 'normal');
    const mtoon = m.extensions?.VRMC_materials_mtoon ?? {};
    for (const [slot, role] of Object.entries(MTOON_SLOTS)) use(mtoon[slot], role);
  }
  return roles;
}

/** Bytes the GPU holds for one texture with a full mip chain (the 4/3 factor). */
export function gpuBytes(width, height, bitsPerPixel) {
  return Math.round((width * height * bitsPerPixel) / 8 * 4 / 3);
}

const decode = async (buffer) => {
  const { data, info } = await sharp(buffer).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  return { data: new Uint8Array(data), width: info.width, height: info.height };
};

async function encode(png, role, mode) {
  // Normal maps lose too much in ETC1S; UASTC keeps them whatever the mode.
  const uastc = mode === 'uastc' || role === 'normal';
  // The encoder's WASM prints a line per mip level to stdout; the summary line is the only output wanted.
  const log = console.log;
  console.log = () => {};
  try {
    return await encodeKtx2(png, role, uastc);
  } finally {
    console.log = log;
  }
}

function encodeKtx2(png, role, uastc) {
  return encodeToKTX2(png, {
    imageDecoder: decode,
    enableDebug: false,
    isUASTC: uastc,
    generateMipmap: true, // compressed textures cannot generate mips on the GPU
    isPerceptual: role === 'color',
    isSetKTX2SRGBTransferFunc: role === 'color',
    isNormalMap: role === 'normal',
    ...(uastc ? { needSupercompression: true, uastcLDRQualityLevel: 2 } : { qualityLevel: 255, compressionLevel: 2 }),
  });
}

export async function optimize(input, { mode = 'etc1s', max = 2048, strict = true } = {}) {
  if (!MODES.includes(mode)) throw new Error(`--mode must be one of ${MODES.join(', ')}`);
  const { json, views } = readGlb(input);
  // Everything but size can be judged before the minutes of encoding.
  const early = checkModel(json, { fileBytes: 0, images: [] });
  if (strict && early.errors.length) return { output: null, report: [], check: early };
  const roles = imageRoles(json);
  const thumbnail = json.extensions?.VRMC_vrm?.meta?.thumbnailImage;
  const report = [];
  const facts = [];
  let converted = false;

  for (const [index, image] of (json.images ?? []).entries()) {
    const view = image.bufferView;
    if (view === undefined) throw new Error(`image ${index} is not embedded`);
    const before = views[view];
    const role = roles.get(index);
    const meta = await sharp(before).metadata();
    let { width, height } = meta;
    const row = { index, name: image.name ?? '', role: role ?? (index === thumbnail ? 'thumbnail' : 'unused'), before: before.byteLength };

    if (index === thumbnail || !role) {
      // Only the thumbnail and images no material samples: keep them valid but small.
      const side = index === thumbnail ? THUMBNAIL_SIDE : TINY;
      if (Math.max(width, height) > side) {
        views[view] = await sharp(before).resize(side, side, { fit: 'inside' }).png({ compressionLevel: 9 }).toBuffer();
        ({ width, height } = await sharp(views[view]).metadata());
      }
      Object.assign(row, { width, height, format: 'png', after: views[view].byteLength, gpu: 0 });
      report.push(row);
      continue;
    }

    let png = before;
    if (Math.max(width, height) > max) {
      png = await sharp(before).resize(max, max, { fit: 'inside', kernel: 'lanczos3' }).png().toBuffer();
      ({ width, height } = await sharp(png).metadata());
    }
    facts.push({ index, width, height });

    if (mode === 'png' || Math.max(width, height) <= TINY) {
      views[view] = png === before ? before : png;
      Object.assign(row, { width, height, format: 'png', after: views[view].byteLength, gpu: gpuBytes(width, height, 32) });
      report.push(row);
      continue;
    }

    const ktx2 = Buffer.from(await encode(png, role, mode));
    views[view] = ktx2;
    image.mimeType = 'image/ktx2';
    converted = true;
    const format = mode === 'uastc' || role === 'normal' ? 'uastc' : 'etc1s';
    // UASTC transcodes to ASTC 4x4 (8 bpp). ETC1S goes to ETC2: 4 bpp opaque, 8 bpp with alpha.
    const opaque = format === 'etc1s' && (!meta.hasAlpha || (await sharp(png).stats()).isOpaque);
    Object.assign(row, { width, height, format, after: ktx2.byteLength, gpu: gpuBytes(width, height, opaque ? 4 : 8) });
    report.push(row);
  }

  if (converted) {
    // Every texture whose image is now KTX2 reads it through KHR_texture_basisu; there is no PNG fallback.
    for (const texture of json.textures ?? []) {
      if (texture.source === undefined || json.images[texture.source].mimeType !== 'image/ktx2') continue;
      texture.extensions = { ...texture.extensions, KHR_texture_basisu: { source: texture.source } };
      delete texture.source;
    }
    for (const list of ['extensionsUsed', 'extensionsRequired']) {
      json[list] = [...new Set([...(json[list] ?? []), 'KHR_texture_basisu'])];
    }
  }

  const output = writeGlb(json, views);
  const check = checkModel(json, { fileBytes: output.byteLength, images: facts });
  return { output, report, check };
}

async function cli(argv) {
  const args = { mode: 'etc1s', max: 2048, report: null, dev: false };
  const files = [];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--mode') args.mode = argv[++i];
    else if (a === '--max') args.max = Number(argv[++i]);
    else if (a === '--report') args.report = argv[++i];
    else if (a === '--dev') args.dev = true;
    else files.push(a);
  }
  if (files.length !== 2) {
    console.error('usage: node optimize-vrm.mjs <in.vrm> <out.vrm> [--mode etc1s|uastc|png] [--max 2048] [--report r.json] [--dev]');
    process.exit(2);
  }
  const [inPath, outPath] = files;
  const started = Date.now();
  const input = fs.readFileSync(inPath);
  const { output, report, check } = await optimize(input, { ...args, strict: !args.dev });

  if (!output) {
    for (const e of check.errors) console.error(`error: ${e}`);
    process.exit(1);
  }
  const sum = (key) => report.reduce((n, r) => n + (r[key] ?? 0), 0);
  const mb = (b) => (b / 1048576).toFixed(1);
  const inputGpu = report.filter((r) => r.gpu > 0).reduce((n, r) => n + gpuBytes(r.width, r.height, 32), 0);
  const summary = {
    input: path.basename(inPath),
    mode: args.mode,
    max: args.max,
    fileMB: { before: Number(mb(input.byteLength)), after: Number(mb(output.byteLength)) },
    imagesMB: { before: Number(mb(sum('before'))), after: Number(mb(sum('after'))) },
    // Estimate from formats and sizes (resized, uncompressed RGBA vs compressed); the phone measures the real thing.
    textureGpuMB: { rgba: Number(mb(inputGpu)), after: Number(mb(sum('gpu'))) },
    seconds: Math.round((Date.now() - started) / 100) / 10,
    ...check.stats,
  };
  console.log(JSON.stringify(summary));
  for (const w of check.warnings) console.warn(`warning: ${w}`);
  if (args.report) fs.writeFileSync(args.report, JSON.stringify({ summary, check, images: report }, null, 2) + '\n');
  for (const e of check.errors) console.error(`${args.dev ? 'warning (dev)' : 'error'}: ${e}`);
  if (check.errors.length && !args.dev) process.exit(1);
  fs.mkdirSync(path.dirname(path.resolve(outPath)), { recursive: true });
  fs.writeFileSync(outPath, output);
}

if (import.meta.url === `file:///${process.argv[1].replaceAll('\\', '/')}` || process.argv[1]?.endsWith('optimize-vrm.mjs')) {
  cli(process.argv.slice(2)).catch((e) => {
    console.error(e.stack ?? e);
    process.exit(1);
  });
}
