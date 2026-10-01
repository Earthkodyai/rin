#!/usr/bin/env node
// Alarm themes (UX.7, D30): generate takes with Eleven Music, pick them by ear, build seamless loops.
//
//   node tools/music/music.mjs plan                      prompts, takes and the credit estimate (no API call)
//   node tools/music/music.mjs gen [--only id,id]        missing takes into <root>/takes/<id>/<id>-<n>.mp3
//   node tools/music/music.mjs qa                        <root>/qa.html: every take, a pick per theme
//   node tools/music/music.mjs build morning=2 cafe=1 …  <root>/out/<id>.mp3 + themes.json (point rin.music there)
//
// Options: --root <dir> (default D:/RinAlarm-music: the music never enters the public repo). Key: ELEVENLABS_API_KEY
// or spikes/s4-voice/.env, as for the voice pack. Needs ffmpeg on PATH for build.
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync, appendFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..');
const SPEC = JSON.parse(readFileSync(join(HERE, 'themes.json'), 'utf8'));

/** Eleven Music on a self-serve plan: about 900 credits per generated minute (checked 2026-10-02). */
export const CREDITS_PER_MINUTE = 900;

/** Loudness every theme is brought to, so Rin's rotation never jumps in volume; the ramp and gain do the rest. */
export const LOUDNESS = { I: -14, TP: -1.5, LRA: 11 };

/** The loop's seam: the last seconds fade out over the first seconds fading in. */
export const CROSSFADE_S = 2;

function args(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) out[argv[i].slice(2)] = argv[++i];
    else out._.push(argv[i]);
  }
  return out;
}

export function estimate(spec, only = null) {
  const themes = spec.themes.filter((t) => !only || only.includes(t.id));
  const minutes = (themes.length * spec.takes * spec.lengthMs) / 60000;
  return { themes: themes.length, takes: themes.length * spec.takes, minutes, credits: Math.round(minutes * CREDITS_PER_MINUTE) };
}

function key() {
  if (process.env.ELEVENLABS_API_KEY) return process.env.ELEVENLABS_API_KEY.trim();
  const env = join(REPO, 'spikes', 's4-voice', '.env');
  const line = existsSync(env) && readFileSync(env, 'utf8').split(/\r?\n/).find((l) => l.startsWith('ELEVENLABS_API_KEY='));
  if (!line) throw new Error('no ELEVENLABS_API_KEY (env or spikes/s4-voice/.env)');
  return line.split('=')[1].trim();
}

const takePath = (root, id, n) => join(root, 'takes', id, `${id}-${n}.mp3`);

async function gen(root, only) {
  const apiKey = key();
  const log = join(root, 'takes', 'log.jsonl');
  mkdirSync(dirname(log), { recursive: true });
  for (const theme of SPEC.themes.filter((t) => !only || only.includes(t.id))) {
    for (let n = 1; n <= SPEC.takes; n++) {
      const file = takePath(root, theme.id, n);
      if (existsSync(file)) continue;
      mkdirSync(dirname(file), { recursive: true });
      const started = Date.now();
      const res = await fetch('https://api.elevenlabs.io/v1/music?output_format=mp3_44100_192', {
        method: 'POST',
        headers: { 'xi-api-key': apiKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: theme.prompt,
          music_length_ms: SPEC.lengthMs,
          model_id: SPEC.model,
          force_instrumental: true,
        }),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(`${theme.id} take ${n}: HTTP ${res.status} ${text.slice(0, 300)}`);
      }
      const audio = Buffer.from(await res.arrayBuffer());
      writeFileSync(file, audio);
      const headers = Object.fromEntries([...res.headers].filter(([k]) => k.startsWith('x-') || k.includes('cost') || k.includes('request')));
      appendFileSync(log, JSON.stringify({ at: new Date().toISOString(), theme: theme.id, take: n, bytes: audio.length, ms: Date.now() - started, model: SPEC.model, headers }) + '\n');
      console.log(`${theme.id}-${n}: ${(audio.length / 1024).toFixed(0)} KB in ${((Date.now() - started) / 1000).toFixed(1)} s`);
    }
  }
}

function qa(root) {
  const rows = SPEC.themes
    .map((t) => {
      const takes = [];
      for (let n = 1; n <= SPEC.takes; n++) {
        const file = takePath(root, t.id, n);
        if (!existsSync(file)) continue;
        const src = relative(root, file).split('\\').join('/');
        takes.push(`<label class="take"><input type="radio" name="${t.id}" value="${n}"> Take ${n}<audio controls preload="none" src="${src}"></audio></label>`);
      }
      return `<section><h2>${t.name} <small>${t.id}</small></h2><p>${t.prompt}</p>${takes.join('') || '<p>No takes yet.</p>'}</section>`;
    })
    .join('\n');
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Alarm themes</title>
<style>body{font:15px system-ui;max-width:760px;margin:24px auto;padding:0 16px;background:#fff4ea;color:#3a2a33}
section{background:#fff;border-radius:18px;padding:12px 16px;margin:14px 0;box-shadow:0 3px 0 #f1d2c2}
h2{margin:4px 0}small{color:#7a5a68;font-weight:normal}p{color:#7a5a68;font-size:13px}
.take{display:flex;align-items:center;gap:10px;margin:6px 0}audio{flex:1}
#picks{position:sticky;bottom:0;background:#cc3169;color:#fff;padding:12px 16px;border-radius:18px;font-weight:bold}</style>
<h1>Alarm themes: pick one take each</h1>
<p>Listen at alarm volume. Tell Claude the line at the bottom.</p>
${rows}
<div id="picks">Picks: none yet</div>
<script>
const ids = ${JSON.stringify(SPEC.themes.map((t) => t.id))};
function show() {
  const picks = ids.map((id) => { const c = document.querySelector('input[name="' + id + '"]:checked'); return c ? id + '=' + c.value : null; }).filter(Boolean);
  document.getElementById('picks').textContent = 'Picks: ' + (picks.join(' ') || 'none yet');
}
document.addEventListener('change', show);
// Only one take plays at a time.
document.addEventListener('play', (e) => document.querySelectorAll('audio').forEach((a) => a !== e.target && a.pause()), true);
</script>`;
  writeFileSync(join(root, 'qa.html'), html);
  console.log(join(root, 'qa.html'));
}

function ffmpeg(argv) {
  const r = spawnSync('ffmpeg', ['-hide_banner', '-nostats', ...argv], { encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`ffmpeg failed: ${r.stderr.slice(-800)}`);
  return r.stderr;
}

function seconds(file) {
  const r = spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file], { encoding: 'utf8' });
  return Number(r.stdout.trim());
}

/** The filter that turns a track of [d] seconds into a loop whose end runs on into its start without a seam. */
export function loopFilter(d, x = CROSSFADE_S) {
  const end = (d - x).toFixed(3);
  return [
    `[0:a]atrim=${end}:${d.toFixed(3)},asetpts=PTS-STARTPTS,afade=t=out:d=${x}:curve=qsin[tail]`,
    `[0:a]atrim=0:${x},asetpts=PTS-STARTPTS,afade=t=in:d=${x}:curve=qsin[head]`,
    `[tail][head]amix=inputs=2:normalize=0[seam]`,
    `[0:a]atrim=${x}:${end},asetpts=PTS-STARTPTS[body]`,
    `[seam][body]concat=n=2:v=0:a=1[out]`,
  ].join(';');
}

function build(root, picks) {
  const out = join(root, 'out');
  const work = join(root, 'work');
  mkdirSync(out, { recursive: true });
  mkdirSync(work, { recursive: true });
  const built = [];
  for (const theme of SPEC.themes) {
    const n = picks[theme.id];
    if (!n) continue;
    const take = takePath(root, theme.id, n);
    if (!existsSync(take)) throw new Error(`no take ${take}`);
    // 1. Silence trimmed from both ends, so the seam joins music to music.
    const trimmed = join(work, `${theme.id}-trim.wav`);
    const silence = 'silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.05';
    ffmpeg(['-y', '-i', take, '-af', `${silence},areverse,${silence},areverse`, '-ar', '44100', '-ac', '2', trimmed]);
    // 2. The loop.
    const looped = join(work, `${theme.id}-loop.wav`);
    ffmpeg(['-y', '-i', trimmed, '-filter_complex', loopFilter(seconds(trimmed)), '-map', '[out]', looped]);
    // 3. Loudness in two passes (measure, then a linear gain), so every theme sits at the same level.
    const { I, TP, LRA } = LOUDNESS;
    const report = ffmpeg(['-i', looped, '-af', `loudnorm=I=${I}:TP=${TP}:LRA=${LRA}:print_format=json`, '-f', 'null', '-']);
    const m = JSON.parse(report.slice(report.lastIndexOf('{'), report.lastIndexOf('}') + 1));
    const norm = `loudnorm=I=${I}:TP=${TP}:LRA=${LRA}:measured_I=${m.input_i}:measured_TP=${m.input_tp}:measured_LRA=${m.input_lra}:measured_thresh=${m.input_thresh}:offset=${m.target_offset}:linear=true`;
    const file = join(out, `${theme.id}.mp3`);
    ffmpeg(['-y', '-i', looped, '-af', norm, '-ar', '44100', '-ac', '2', '-c:a', 'libmp3lame', '-b:a', '192k', file]);
    built.push({ id: theme.id, name: theme.name });
    console.log(`${theme.id}: take ${n}, ${seconds(file).toFixed(1)} s, ${(statSync(file).size / 1024).toFixed(0)} KB (was ${m.input_i} LUFS)`);
  }
  writeFileSync(join(out, 'themes.json'), JSON.stringify(built, null, 2) + '\n');
  console.log(`${built.length} themes -> ${out}`);
}

async function main() {
  const a = args(process.argv.slice(2));
  const root = a.root ?? 'D:/RinAlarm-music';
  const only = a.only ? a.only.split(',') : null;
  switch (a._[0]) {
    case 'plan': {
      for (const t of SPEC.themes) console.log(`${t.id} (${t.name}): ${t.prompt}\n`);
      const e = estimate(SPEC, only);
      console.log(`${e.takes} takes of ${SPEC.lengthMs / 1000} s with ${SPEC.model}: ${e.minutes} min, about ${e.credits} credits`);
      break;
    }
    case 'gen':
      await gen(root, only);
      break;
    case 'qa':
      qa(root);
      break;
    case 'build':
      build(root, Object.fromEntries(a._.slice(1).map((p) => p.split('=')).map(([k, v]) => [k, Number(v)])));
      break;
    default:
      console.log('usage: music.mjs plan | gen [--only a,b] | qa | build id=take …  [--root dir]');
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main().catch((e) => { console.error(e.message); process.exit(1); });
