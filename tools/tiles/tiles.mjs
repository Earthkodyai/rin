#!/usr/bin/env node
// Pictures for the alarm editor's tiles (UX.7): generate with Gemini's Flash Image model, pick by eye, build WebPs.
//
//   node tools/tiles/tiles.mjs plan                     the prompts (no API call)
//   node tools/tiles/tiles.mjs gen [--only id,id]       missing takes into <root>/takes/<id>-<n>.jpg
//   node tools/tiles/tiles.mjs sheet                    <root>/prompts.html: the prompts to make takes by hand (web app)
//   node tools/tiles/tiles.mjs qa                       <root>/qa.html: every take, a pick per tile
//   node tools/tiles/tiles.mjs build pads=2 cups=1 …    app/src/main/assets/tiles/<id>.webp (cropped to the tile, 3:1)
//
// --night: the same for the night look (nightStyle; takes <id>-night-<n>, prompts-night.html, qa-night.html,
// <id>_night.webp). Options: --root <dir> (default D:/RinAlarm-tiles). Key: GEMINI_API_KEY or D:/RinAlarm-keys/gemini.key (one line),
// never in the repo. Uses sharp from tools/character.
import { existsSync, mkdirSync, readFileSync, writeFileSync, appendFileSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..');
const SPEC = JSON.parse(readFileSync(join(HERE, 'tiles.json'), 'utf8'));
const OUT = join(REPO, 'app', 'src', 'main', 'assets', 'tiles');

/** The tile is about 170 x 60 dp; 3:1 at 720 px wide covers a 3.5x screen at half the tile's width and then some. */
export const TILE = { width: 720, height: 240 };

/** Night versions: the app's night look (indigo ground), built to <id>_night.webp. Set by --night. */
let NIGHT = false;

export function prompt(spec, tile, night = false) {
  // Night prompts have their own props: the day subjects' tables and forests pulled Gemini into whole scenes.
  return night ? `${spec.nightStyle}\n\nProps: ${tile.nightSubject ?? tile.subject}` : `${spec.style}\n\nSubject: ${tile.subject}`;
}

/** A tile's name in takes/ and in the APK, by look. */
const take = (id) => (NIGHT ? `${id}-night` : id);

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

/** A take as the API saves it (.jpg), or as a web app downloads it (.png, .jpeg, .webp: the user's own takes). */
const EXTS = ['jpg', 'png', 'jpeg', 'webp'];
const takePath = (root, id, n) =>
  EXTS.map((e) => join(root, 'takes', `${take(id)}-${n}.${e}`)).find(existsSync) ?? join(root, 'takes', `${take(id)}-${n}.jpg`);

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
          input: [{ type: 'text', text: prompt(SPEC, tile, NIGHT) }],
          response_format: { type: 'image', mime_type: 'image/jpeg', aspect_ratio: SPEC.aspectRatio, image_size: '1K' },
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
        const src = 'takes/' + basename(takePath(root, t.id, n));
        takes.push(`<label class="take"><input type="radio" name="${t.id}" value="${n}"><span class="tile"><img src="${src}"><b>${t.label.split(': ')[1]}</b></span></label>`);
      }
      return `<section><h2>${t.label} <small>${t.id}</small></h2><div class="takes">${takes.join('') || '<p>No takes yet.</p>'}</div></section>`;
    })
    .join('\n');
  const pill = NIGHT ? 'background:rgba(28,35,80,.92);color:#f3eeff' : 'background:rgba(255,255,255,.92)';
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Tile pictures${NIGHT ? ' (night)' : ''}</title>
<style>body{font:15px system-ui;max-width:820px;margin:24px auto;padding:0 16px;background:${NIGHT ? '#121836;color:#f3eeff' : '#fff4ea;color:#3a2a33'}}
section{background:${NIGHT ? '#1c2350' : '#fff'};border-radius:18px;padding:12px 16px;margin:14px 0;box-shadow:0 3px 0 ${NIGHT ? '#0b1028' : '#f1d2c2'}}h2{margin:4px 0 10px}small{opacity:.7;font-weight:normal}
.takes{display:flex;gap:14px;flex-wrap:wrap}.take{display:flex;align-items:center;gap:8px}
.tile{position:relative;display:block;width:340px;height:120px;border-radius:18px;overflow:hidden;border:2px solid #f1d2c2}
.tile img{width:100%;height:100%;object-fit:cover}.tile b{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);${pill};padding:4px 12px;border-radius:12px;font-size:15px}
input:checked+.tile{border:3px solid #cc3169}
#picks{position:sticky;bottom:0;background:#cc3169;color:#fff;padding:12px 16px;border-radius:18px;font-weight:bold}</style>
<h1>Tile pictures: pick one each</h1><p>Shown cropped to the tile's shape, with the name on its pill as in the app.</p>
${rows}<div id="picks">Picks: none yet</div>
<script>const ids=${JSON.stringify(SPEC.tiles.map((t) => t.id))};
document.addEventListener('change',()=>{const p=ids.map(id=>{const c=document.querySelector('input[name="'+id+'"]:checked');return c?id+'='+c.value:null}).filter(Boolean);document.getElementById('picks').textContent='Picks: '+(p.join(' ')||'none yet')})</script>`;
  const page = join(root, NIGHT ? 'qa-night.html' : 'qa.html');
  writeFileSync(page, html);
  console.log(page);
}

/** The prompt sheet for making takes by hand in the Gemini web app: one prompt per tile, a copy button, the file name. */
function sheet(root) {
  mkdirSync(join(root, 'takes'), { recursive: true });
  const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
  const rows = SPEC.tiles
    .map((t, i) => {
      const text = `${prompt(SPEC, t, NIGHT)}

Format: a wide landscape image, aspect ratio ${SPEC.aspectRatio}.`;
      return `<section><h2>${i + 1}. ${t.label}</h2><pre id="p${i}">${esc(text)}</pre>
<button onclick="navigator.clipboard.writeText(document.getElementById('p${i}').textContent);this.textContent='Copied'">Copy prompt</button>
<p>Save as <code>${take(t.id)}-1</code> (and <code>${take(t.id)}-2</code> for a second take) (any of .png .jpg .webp) in <code>${esc(join(root, 'takes'))}</code></p></section>`;
    })
    .join('\n');
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Tile prompts${NIGHT ? ' (night)' : ''}</title>
<style>body{font:15px system-ui;max-width:820px;margin:24px auto;padding:0 16px;background:#fff4ea;color:#3a2a33}
section{background:#fff;border-radius:18px;padding:12px 16px;margin:14px 0;box-shadow:0 3px 0 #f1d2c2}h2{margin:4px 0 8px}
pre{white-space:pre-wrap;background:#fff4ea;border-radius:12px;padding:10px;font:13px system-ui;color:#3a2a33}
button{background:#cc3169;color:#fff;border:0;border-radius:20px;padding:8px 16px;font-weight:bold;cursor:pointer}
code{background:#ffe1e9;padding:1px 6px;border-radius:6px}p{color:#7a5a68;font-size:13px}</style>
<h1>Tile pictures${NIGHT ? ', night look' : ''}: ${SPEC.tiles.length} prompts</h1>
<p>In gemini.google.com (or AI Studio), paste a prompt, generate, download. Then tell Claude.</p>${rows}`;
  const page = join(root, NIGHT ? 'prompts-night.html' : 'prompts.html');
  writeFileSync(page, html);
  console.log(page);
}

async function build(root, picks) {
  const sharp = createRequire(join(REPO, 'tools', 'character', 'package.json'))('sharp');
  mkdirSync(OUT, { recursive: true });
  for (const tile of SPEC.tiles) {
    const n = picks[tile.id];
    if (!n) continue;
    const file = join(OUT, `${tile.id}${NIGHT ? '_night' : ''}.webp`);
    await sharp(takePath(root, tile.id, n)).resize(TILE.width, TILE.height, { fit: 'cover', position: 'centre' }).webp({ quality: 80 }).toFile(file);
    console.log(`${basename(file)}: take ${n} -> ${(statSync(file).size / 1024).toFixed(0)} KB`);
  }
}

async function main() {
  const argv = process.argv.slice(2);
  NIGHT = argv.includes('--night');
  const a = args(argv.filter((x) => x !== '--night'));
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
    case 'sheet':
      sheet(root);
      break;
    case 'build':
      await build(root, Object.fromEntries(a._.slice(1).map((p) => p.split('=')).map(([k, v]) => [k, Number(v)])));
      break;
    default:
      console.log('usage: tiles.mjs plan | gen [--only a,b] | sheet | qa | build id=take …  [--night] [--root dir]');
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main().catch((e) => { console.error(e.message); process.exit(1); });
