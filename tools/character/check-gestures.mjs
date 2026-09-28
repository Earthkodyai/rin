#!/usr/bin/env node
// Checks that Rin's arms stay out of her body through every gesture (task 2.5), on the model itself: the built page
// poses each gesture frame by frame (web/character/src/inspect.ts) and reports the deepest an arm or sleeve sinks
// into her torso, when and where. Frills (the outermost 10% of an arm's surface, such as a glove cuff) may brush her
// as cloth would: they are reported beside the result and do not fail it. Each gesture is saved as a sheet seen from the front, her left, her right and
// above, to look at: at its worst moment, or when nothing sinks in, at 45% through (where most gestures hold).
//
// usage: node check-gestures.mjs <page-dir> <model.vrm> [--out dir] [--max-mm 10] [--write-body <body.json>]
//        [--browser <path>] [--software]
// Exits 1 when a gesture sinks deeper than --max-mm. --write-body saves the model's measured skeleton and torso
// outline (web/character/src/body.json), which the gesture builder bends the arms around: for a new model, write it,
// rebuild the gestures (npm run gestures), then check again.

import fs from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';
import { openStillPage, pngOf } from './render-stills.mjs';

const SIDE = 800;
const PANEL = 400;
const VIEWS = ['front', 'left', 'right', 'above'];

export async function checkGestures({ pageDir, model, out, maxMm = 10, writeBody, browser, software = false }) {
  const { page, close } = await openStillPage({ pageDir, model, width: SIDE, height: SIDE, browser, software });
  try {
    if (writeBody) {
      const body = await page.evaluate(() => window.rinBody);
      fs.writeFileSync(writeBody, JSON.stringify({ model: path.basename(model), ...body }) + '\n');
    }
    const names = [null, ...(await page.evaluate(() => window.rinGestures))];
    fs.mkdirSync(out, { recursive: true });
    const rows = [];
    for (const name of names) {
      const result = await page.evaluate((n) => window.rinInspect(n), name);
      const mm = result.worst?.mm ?? 0;
      rows.push({ ...result, mm, ok: mm <= maxMm });
      const at = result.worst?.t ?? result.seconds * 0.45;
      const panels = [];
      for (const [i, view] of VIEWS.entries()) {
        const png = pngOf(await page.evaluate((n, t, v) => window.rinView(n, t, v), name, at, view));
        panels.push({ input: await sharp(png).resize(PANEL, PANEL).toBuffer(), left: i * PANEL, top: 0 });
      }
      await sharp({ create: { width: PANEL * VIEWS.length, height: PANEL, channels: 4, background: '#d8d8e0' } })
        .composite(panels)
        .png()
        .toFile(path.join(out, `${result.gesture}.png`));
    }
    return rows;
  } finally {
    await close();
  }
}

async function cli(argv) {
  const args = { out: 'build/gesture-check', maxMm: 10, writeBody: undefined, browser: undefined, software: false };
  const files = [];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--out') args.out = argv[++i];
    else if (a === '--max-mm') args.maxMm = Number(argv[++i]);
    else if (a === '--write-body') args.writeBody = argv[++i];
    else if (a === '--browser') args.browser = argv[++i];
    else if (a === '--software') args.software = true;
    else files.push(a);
  }
  if (files.length !== 2 || !(args.maxMm >= 0)) {
    console.error('usage: node check-gestures.mjs <page-dir> <model.vrm> [--out dir] [--max-mm 10] [--write-body f] [--browser <path>] [--software]');
    process.exit(2);
  }
  const [pageDir, model] = files;
  const rows = await checkGestures({ pageDir, model, ...args });
  const counts = rows[0];
  console.log(`vertices checked: torso ${counts.torso}, arms ${counts.arms}; bar ${args.maxMm} mm`);
  for (const r of rows) {
    const w = r.worst;
    const where = w ? ` at ${w.t.toFixed(2)} s, ${w.bone} at ${JSON.stringify(w.at)}, torso there x ${JSON.stringify(w.torso.x)} z ${JSON.stringify(w.torso.z)}` : '';
    const frill = r.frill ? ` (frill ${r.frill.mm} mm at ${r.frill.t.toFixed(2)} s, ${r.frill.bone})` : '';
    console.log(`${r.ok ? 'ok  ' : 'FAIL'} ${r.gesture.padEnd(8)} ${String(r.mm).padStart(3)} mm${where}${frill}`);
  }
  console.log(`sheets (front, left, right, above): ${path.resolve(args.out)}`);
  if (rows.some((r) => !r.ok)) process.exit(1);
}

if (process.argv[1]?.endsWith('check-gestures.mjs')) {
  cli(process.argv.slice(2)).catch((e) => {
    console.error(e.stack ?? e);
    process.exit(1);
  });
}
