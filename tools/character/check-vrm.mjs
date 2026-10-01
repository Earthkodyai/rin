// Checks a VRoid export before it goes anywhere near the app (the vroid-check skill, .claude/skills/vroid-check):
// the same rules the Gradle pipeline applies (check.mjs), on the raw file, with a report a person can act on.
//
// usage: node check-vrm.mjs <model.vrm> [--author <name>] [--json]
// exit:  0 when it passes (warnings allowed), 1 when any rule fails, 2 when the file cannot be read

import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import sharp from 'sharp';
import { readGlb } from './glb.mjs';
import { checkModel } from './check.mjs';

/** Width and height of every embedded image, read from its own header. */
async function imageFacts(json, views) {
  const facts = [];
  for (const [index, image] of (json.images ?? []).entries()) {
    if (image.bufferView === undefined) continue;
    const meta = await sharp(views[image.bufferView]).metadata().catch(() => null);
    if (meta) facts.push({ index, width: meta.width, height: meta.height });
  }
  return facts;
}

/** @returns {{ errors: string[], warnings: string[], stats: object, meta: object }} */
export async function checkFile(bytes, { author } = {}) {
  const { json, views } = readGlb(bytes);
  const result = checkModel(json, { fileBytes: bytes.byteLength, images: await imageFacts(json, views), author });
  // The size limit is for the file the app ships, after the pipeline's KTX2 step (optimize-vrm.mjs), which usually
  // shrinks a VRoid export by a quarter or more: a 16.5 MB export became 12.1 MB (2026-10-01). On the raw export an
  // oversize file is only a warning, with the command that gives the real answer.
  const sizeError = result.errors.findIndex((e) => e.startsWith('file is '));
  if (sizeError >= 0) {
    result.errors.splice(sizeError, 1);
    result.warnings.unshift(
      `raw export is ${(bytes.byteLength / 1048576).toFixed(1)} MB; the shipped (KTX2) file must be <= 15 MB: ` +
        'node optimize-vrm.mjs <model.vrm> build/rin.vrm decides',
    );
  }
  const meta = json.extensions?.VRMC_vrm?.meta ?? {};
  return { ...result, meta: { name: meta.name, authors: meta.authors, version: meta.version } };
}

export function report(file, result) {
  const lines = [`Model: ${file}`, `Name: ${result.meta.name ?? '?'} · authors: ${(result.meta.authors ?? []).join(', ') || '?'}`];
  if (Object.keys(result.stats).length) {
    const s = result.stats;
    lines.push(`Size ${s.fileMB} MB · largest texture ${s.maxTextureSide} px · ${s.materials} materials · ${s.springJoints} spring joints`);
  }
  for (const e of result.errors) lines.push(`FAIL  ${e}`);
  for (const w of result.warnings) lines.push(`WARN  ${w}`);
  lines.push(result.errors.length ? `Result: FAIL (${result.errors.length})` : 'Result: PASS');
  return lines.join('\n');
}

async function main(argv) {
  const file = argv.find((a) => !a.startsWith('--') && argv[argv.indexOf(a) - 1] !== '--author');
  const author = argv.includes('--author') ? argv[argv.indexOf('--author') + 1] : undefined;
  if (!file) {
    console.error('usage: node check-vrm.mjs <model.vrm> [--author <name>] [--json]');
    return 2;
  }
  let result;
  try {
    result = await checkFile(await readFile(file), { author });
  } catch (e) {
    console.error(`cannot read ${file}: ${e.message}`);
    return 2;
  }
  console.log(argv.includes('--json') ? JSON.stringify(result, null, 2) : report(file, result));
  return result.errors.length ? 1 : 0;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) process.exitCode = await main(process.argv.slice(2));
