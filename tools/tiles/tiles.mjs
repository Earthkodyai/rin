#!/usr/bin/env node
// Pictures for the alarm editor's tiles (UX.7): generate with Gemini's Flash Image model, pick by eye, build WebPs.
//
//   node tools/tiles/tiles.mjs plan                     the prompts (no API call)
//   node tools/tiles/tiles.mjs gen [--only id,id]       missing takes into <root>/takes/<id>-<n>.png
//   node tools/tiles/tiles.mjs qa                       <root>/qa.html: every take, a pick per tile
//   node tools/tiles/tiles.mjs build pads=2 cups=1 …    app/src/main/assets/tiles/<id>.webp (cropped to the tile, 3:1)
//
// Options: --root <dir> (default D:/RinAlarm-tiles). Key: GEMINI_API_KEY or D:/RinAlarm-keys/gemini.key (one line),
// never in the repo. Uses sharp from tools/character.
import { existsSync, mkdirSync, readFileSync, writeFileSync, appendFileSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..');
const SPEC = JSON.parse(readFileSync(join(HERE, 'tiles.json'), 'utf8'));
const OUT = join(REPO, 'app', 'src', 'main', 'assets', 'tiles');

/** The tile is about 170 x 60 dp; 3:1 at 720 px wide covers a 3.5x screen at half the tile's width and then some. */
export const TILE = { width: 720, height: 240 };

export function prompt(spec, tile) {
  return `${spec.style}\n\nSubject: ${tile.subject}`;
}

function args(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) out[argv[i].slice(2)] = argv[++i];
    else out._.push(argv[i]);
  }
  return out;
}

function key() {
  if (process.env.GEMINI_API_KEY) return process.env.GEMINI_API_KEY.trim();
  const file = 'D:/RinAlarm-keys/gemini.key';
  if (!existsSync(file)) throw new Error('no GEMINI_API_KEY and no D:/RinAlarm-keys/gemini.key');
  return readFileSync(file, 'utf8').trim();
}

/** The first base64 image anywhere in a response, whichever shape the API answers with. */
export function findImage(node) {
  if (!node || typeof node !== 'object') return null;
  const mime = node.mime_type ?? node.mimeType;
  if (typeof node.data === 'string' && (!mime || mime.startsWith('image/')) && node.data.length > 1000) return node.data;
  for (const value of Object.values(node)) {
    const found = findImage(value);
    if (found) return found;
  }
  return null;
}

const takePath = (root, id, n) => join(root, 'takes', `${id}-${n}.png`);

async function gen(root, only) {
  const apiKey = key();
  mkdirSync(join(root, 'takes'), { recursive: true });
  const log = join(root, 'takes', 'log.jsonl');
  for (const tile of SPEC.tiles.filter((t) => !only || only.includes(t.id))) {
    for (let n = 1; n <= SPEC.takes; n++) {
      const file = takePath(root, tile.id, n);
      if (existsSync(file)) continue;
      const started = Date.now();
      const res = await fetch('https://generativelanguage.googleapis.com/v1beta/interactions', {
        method: 'POST',
        headers: { 'x-goog-api-key': apiKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: SPEC.model,
          input: [{ type: 'text', text: prompt(SPEC, tile) }],
          response_format: { type: 'image', mime_type: 'image/png', aspect_ratio: SPEC.aspectRatio, image_size: '1K' },
        }),
      });
      const text = await res.text();
      if (!res.ok) throw new Error(`${tile.id} take ${n}: HTTP ${res.status} ${text.slice(0, 400)}`);
      const data = findImage(JSON.parse(text));
      if (!data) throw new Error(`${tile.id} take ${n}: no image in the answer: ${text.slice(0, 400)}`);
      writeFileSync(file, Buffer.from(data, 'base64'));
      appendFileSync(log, JSON.stringify({ at: new Date().toISOString(), tile: tile.id, take: n, ms: Date.now() - started, model: SPEC.model }) + '\n');
      console.log(`${tile.id}-${n}: ${(statSync(file).size / 1024).toFixed(0)} KB in ${((Date.now() - started) / 1000).toFixed(1)} s`);
      // The free tier allows about 2 images a minute.
      await new Promise((r) => setTimeout(r, Number(process.env.TILE_GAP_MS ?? 31000)));
    }
  }
}

function qa(root) {
  const rows = SPEC.tiles
    .map((t) => {
      const takes = [];
      for (let n = 1; n <= SPEC.takes; n++) {
        if (!existsSync(takePath(root, t.id, n))) continue;
        takes.push(`<label class="take"><input type="radio" name="${t.id}" value="${n}"><span class="tile"><img src="takes/${t.id}-${n}.png"><b>${t.label.split(': ')[1]}</b></span></label>`);
      }
      return `<section><h2>${t.label} <small>${t.id}</small></h2><div class="takes">${takes.join('') || '<p>No takes yet.</p>'}</div></section>`;
    })
    .join('\n');
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Tile pictures</title>
<style>body{font:15px system-ui;max-width:820px;margin:24px auto;padding:0 16px;background:#fff4ea;color:#3a2a33}
section{background:#fff;border-radius:18px;padding:12px 16px;margin:14px 0;box-shadow:0 3px 0 #f1d2c2}h2{margin:4px 0 10px}small{color:#7a5a68;font-weight:normal}
.takes{display:flex;gap:14px;flex-wrap:wrap}.take{display:flex;align-items:center;gap:8px}
.tile{position:relative;display:block;width:340px;height:120px;border-radius:18px;overflow:hidden;border:2px solid #f1d2c2}
.tile img{width:100%;height:100%;object-fit:cover}.tile b{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);background:rgba(255,255,255,.92);padding:4px 12px;border-radius:12px;font-size:15px}
input:checked+.tile{border:3px solid #cc3169}
#picks{position:sticky;bottom:0;background:#cc3169;color:#fff;padding:12px 16px;border-radius:18px;font-weight:bold}</style>
<h1>Tile pictures: pick one each</h1><p>Shown cropped to the tile's shape, with the name on its pill as in the app.</p>
${rows}<div id="picks">Picks: none yet</div>
<script>const ids=${JSON.stringify(SPEC.tiles.map((t) => t.id))};
document.addEventListener('change',()=>{const p=ids.map(id=>{const c=document.querySelector('input[name="'+id+'"]:checked');return c?id+'='+c.value:null}).filter(Boolean);document.getElementById('picks').textContent='Picks: '+(p.join(' ')||'none yet')})</script>`;
  writeFileSync(join(root, 'qa.html'), html);
  console.log(join(root, 'qa.html'));
}

async function build(root, picks) {
  const sharp = createRequire(join(REPO, 'tools', 'character', 'package.json'))('sharp');
  mkdirSync(OUT, { recursive: true });
  for (const tile of SPEC.tiles) {
    const n = picks[tile.id];
    if (!n) continue;
    const file = join(OUT, `${tile.id}.webp`);
    await sharp(takePath(root, tile.id, n)).resize(TILE.width, TILE.height, { fit: 'cover', position: 'centre' }).webp({ quality: 80 }).toFile(file);
    console.log(`${tile.id}: take ${n} -> ${(statSync(file).size / 1024).toFixed(0)} KB`);
  }
}

async function main() {
  const a = args(process.argv.slice(2));
  const root = a.root ?? 'D:/RinAlarm-tiles';
  const only = a.only ? a.only.split(',') : null;
  switch (a._[0]) {
    case 'plan':
      for (const t of SPEC.tiles) console.log(`${t.id}: ${t.subject}\n`);
      console.log(`${SPEC.tiles.length} tiles x ${SPEC.takes} takes with ${SPEC.model}, ${SPEC.aspectRatio}`);
      break;
    case 'gen':
      await gen(root, only);
      break;
    case 'qa':
      qa(root);
      break;
    case 'build':
      await build(root, Object.fromEntries(a._.slice(1).map((p) => p.split('=')).map(([k, v]) => [k, Number(v)])));
      break;
    default:
      console.log('usage: tiles.mjs plan | gen [--only a,b] | qa | build id=take …  [--root dir]');
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main().catch((e) => { console.error(e.message); process.exit(1); });
