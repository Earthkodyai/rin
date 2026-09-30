// Rin's voice pack (task 4.3): every line in lines.json plus the Repeat after Rin sentences, in ElevenLabs "Rin soft"
// on eleven_v3 (the user's pick over v4 in a blind test, 9–1, 2026-09-30). Run it only inside the paid month: output
// from a paid plan carries the commercial licence, free-plan output does not (docs/spikes/S4-voice.md).
//
//   node pack.mjs plan                        clips, characters, what is still missing
//   node pack.mjs gen [--retakes 2] [--max-credits 20000] [--ids a,b]
//                                             first takes for missing clips; a take with flags (pack-qa.mjs) is
//                                             retaken up to --retakes times and the best one is kept
//   node pack.mjs redo <id,id...>             one more take for clips the user marked on the listening page
//   node pack.mjs pick <id> <take>            keep an earlier take instead
//   node pack.mjs build                       chosen takes -> trim, -16 LUFS / -2 dBTP, 10 ms fades, MP3 96 kb/s mono,
//                                             mouth track; then writes qa.json for the listening page (qa.html)
//   node pack.mjs import <dir>                dry run: use <dir>/<id>.mp3 as take 1 (no API, no credits)
// Options: --pack <dir> (default tools/voice/pack, git-ignored). Key: ELEVENLABS_API_KEY or spikes/s4-voice/.env;
// voice id: RIN_VOICE_ID or spikes/s4-voice/out/eleven_voices.json. ffmpeg: FFMPEG, PATH, or the winget install.
import { spawnSync } from 'node:child_process';
import { appendFileSync, copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readAudio } from './audio.mjs';
import { mouthTrack } from './mouth.mjs';
import { best, check } from './pack-qa.mjs';
import { load, spoken } from './script-check.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..', '..');
const S4 = join(ROOT, 'spikes', 's4-voice');
export const MODEL = 'eleven_v3';
const FORMAT = 'mp3_44100_128';
/** Same read as the 3.5 debug clips: the user repeats it word for word. */
const REPEAT_TAG = '[warmly]';
export const LOUDNESS = { I: -16, TP: -2, LRA: 11 };
/** What a built clip must measure: loudness within 1 LU of -16, true peak (after MP3) at or under -1 dBTP. */
export const BAR = { lu: 1, tp: -1 };

/** Every clip in the pack: its id, where it goes, and exactly what TTS reads. */
export function clips({ script, repeat } = load()) {
  const lines = script.lines.map((l) => ({ id: l.id, kind: 'line', pool: l.pool, tag: l.tag, text: spoken(l), screen: l.text }));
  const rs = repeat.map((r) => ({ id: r.id, kind: 'repeat', pool: 'repeat', tag: REPEAT_TAG, text: r.text, screen: r.text }));
  return [...lines, ...rs];
}

export const request = (c) => (c.tag ? `${c.tag} ${c.text}` : c.text);
/** Where a built clip lives, relative to assets/voice/rin (LineVoice reads <id>, the Repeat game repeat/<id>). */
export const outPath = (c) => (c.kind === 'repeat' ? `repeat/${c.id}` : c.id);

const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const at = args.indexOf(`--${name}`);
  return at >= 0 ? args.splice(at, 2)[1] : fallback;
};

function secret(name, file, pick) {
  if (process.env[name]) return process.env[name];
  if (!existsSync(file)) throw new Error(`set ${name} or create ${file}`);
  return pick(readFileSync(file, 'utf8'));
}

function ffmpegPath() {
  if (process.env.FFMPEG) return process.env.FFMPEG;
  if (spawnSync('ffmpeg', ['-version']).status === 0) return 'ffmpeg';
  const base = join(process.env.LOCALAPPDATA ?? '', 'Microsoft', 'WinGet', 'Packages');
  const pkg = existsSync(base) && readdirSync(base).find((d) => d.startsWith('Gyan.FFmpeg'));
  const build = pkg && readdirSync(join(base, pkg)).find((d) => d.startsWith('ffmpeg-'));
  if (build) return join(base, pkg, build, 'bin', 'ffmpeg.exe');
  throw new Error('ffmpeg not found: install it (winget install Gyan.FFmpeg.Essentials) or set FFMPEG');
}

function ffmpeg(bin, argv) {
  const r = spawnSync(bin, ['-hide_banner', '-nostats', '-y', ...argv], { encoding: 'utf8', maxBuffer: 64 << 20 });
  if (r.status !== 0) throw new Error(`ffmpeg ${argv.join(' ')}\n${r.stderr}`);
  return r.stderr;
}

/** The last {...} block ffmpeg's loudnorm prints. */
const loudnormJson = (log) => JSON.parse(log.slice(log.lastIndexOf('{'), log.lastIndexOf('}') + 1));

class Pack {
  constructor(dir) {
    this.dir = dir;
    this.stateFile = join(dir, 'takes.json');
    this.state = existsSync(this.stateFile) ? JSON.parse(readFileSync(this.stateFile, 'utf8')) : {};
    mkdirSync(join(dir, 'takes'), { recursive: true });
  }

  save() {
    writeFileSync(this.stateFile, JSON.stringify(this.state, null, 1) + '\n');
  }

  entry(id) {
    return (this.state[id] ??= { takes: [], chosen: null });
  }

  /** Adds a take from mp3 bytes, checks it, and records it. */
  async add(c, bytes, meta = {}) {
    const e = this.entry(c.id);
    const n = e.takes.length + 1;
    const file = `takes/${c.id}.${n}.mp3`;
    writeFileSync(join(this.dir, file), bytes);
    const { samples, rate } = await readAudio(join(this.dir, file));
    const qa = check(samples, rate, c.text);
    e.takes.push({ n, file, text: request(c), qa, ...meta });
    return e.takes.at(-1);
  }

  /** Chooses the best take by the automatic checks. */
  choose(id) {
    const e = this.state[id];
    e.chosen = e.takes[best(e.takes.map((t) => t.qa))].n;
  }
}

class Eleven {
  constructor(maxCredits) {
    this.key = secret('ELEVENLABS_API_KEY', join(S4, '.env'), (s) =>
      s.split(/\r?\n/).find((l) => l.startsWith('ELEVENLABS_API_KEY='))?.split('=')[1].trim());
    this.voice = secret('RIN_VOICE_ID', join(S4, 'out', 'eleven_voices.json'), (s) => JSON.parse(s)['el-rin-soft']);
    this.maxCredits = maxCredits;
    this.spent = 0;
  }

  async say(text, log) {
    if (this.spent >= this.maxCredits) throw new Error(`stopped at --max-credits ${this.maxCredits} (spent ${this.spent})`);
    const r = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${this.voice}?output_format=${FORMAT}`, {
      method: 'POST',
      headers: { 'xi-api-key': this.key, 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, model_id: MODEL }),
    });
    if (!r.ok) throw new Error(`ElevenLabs ${r.status}: ${await r.text()}`);
    const bytes = Buffer.from(await r.arrayBuffer());
    const meta = {
      at: new Date().toISOString(),
      cost: Number(r.headers.get('character-cost')),
      request: r.headers.get('request-id'),
      history: r.headers.get('history-item-id'),
    };
    this.spent += meta.cost || 0;
    // Evidence that every clip came from the paid month (S4 rule): time, request and history ids, credits.
    appendFileSync(log, JSON.stringify({ text, model: MODEL, ...meta }) + '\n');
    return { bytes, meta };
  }
}

async function gen(pack, list, { retakes, maxCredits, redo }) {
  const api = new Eleven(maxCredits);
  const log = join(pack.dir, 'gen-log.jsonl');
  for (const c of list) {
    const e = pack.entry(c.id);
    const fresh = e.takes.filter((t) => t.text === request(c));
    if (!redo && fresh.length) continue;
    // A redo is one more take the user asked for; otherwise up to 1 + retakes until one has no flags.
    const budget = redo ? 1 : 1 + retakes;
    for (let i = 0; i < budget; i++) {
      const { bytes, meta } = await api.say(request(c), log);
      const t = await pack.add(c, bytes, meta);
      console.log(`${c.id} take ${t.n}: ${t.qa.flags.join(',') || 'ok'} (${meta.cost} credits, total ${api.spent})`);
      pack.save();
      if (!t.qa.flags.length) break;
    }
    if (redo) e.chosen = e.takes.at(-1).n;
    else pack.choose(c.id);
    pack.save();
  }
  console.log(`credits this run: ${api.spent}`);
}

function build(pack, list) {
  const bin = ffmpegPath();
  const out = join(pack.dir, 'out');
  const tmp = join(pack.dir, 'tmp');
  mkdirSync(join(out, 'repeat'), { recursive: true });
  mkdirSync(tmp, { recursive: true });
  const trim = 'silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,areverse,' +
    'silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.15,areverse';
  const target = `I=${LOUDNESS.I}:TP=${LOUDNESS.TP}:LRA=${LOUDNESS.LRA}`;
  const results = [];
  for (const c of list) {
    const e = pack.state[c.id];
    if (!e?.chosen) continue;
    const take = e.takes.find((t) => t.n === e.chosen);
    const wav = join(tmp, `${c.id}.wav`);
    ffmpeg(bin, ['-i', join(pack.dir, take.file), '-af', trim, '-ac', '1', '-ar', '44100', '-c:a', 'pcm_s16le', wav]);
    const dur = (readFileSync(wav).length - 44) / 2 / 44100;
    const m = loudnormJson(ffmpeg(bin, ['-i', wav, '-af', `loudnorm=${target}:print_format=json`, '-f', 'null', '-']));
    const measured = `measured_I=${m.input_i}:measured_TP=${m.input_tp}:measured_LRA=${m.input_lra}:` +
      `measured_thresh=${m.input_thresh}:offset=${m.target_offset}`;
    const mp3 = join(out, `${outPath(c)}.mp3`);
    const n = loudnormJson(ffmpeg(bin, ['-i', wav, '-af',
      `loudnorm=${target}:${measured}:linear=true:print_format=json,` +
      `afade=t=in:d=0.01,afade=t=out:st=${Math.max(0, dur - 0.01).toFixed(3)}:d=0.01`,
      '-ar', '44100', '-ac', '1', '-c:a', 'libmp3lame', '-b:a', '96k', mp3]));
    const v = ffmpeg(bin, ['-i', mp3, '-af', 'ebur128=peak=true', '-f', 'null', '-']);
    const summary = v.slice(v.lastIndexOf('Summary:'));
    const lufs = Number(summary.match(/I:\s+(-?[\d.]+) LUFS/)[1]);
    const tp = Number(summary.match(/Peak:\s+(-?[\d.]+|-inf) dBFS/)[1]);
    const ok = Math.abs(lufs - LOUDNESS.I) <= BAR.lu && tp <= BAR.tp;
    results.push({ c, take, dur: +dur.toFixed(2), lufs, tp, mode: n.normalization_type, ok });
  }
  return results;
}

async function mouths(pack, results) {
  for (const r of results) {
    const base = join(pack.dir, 'out', outPath(r.c));
    const { samples, rate } = await readAudio(`${base}.mp3`);
    writeFileSync(`${base}.mouth.json`, JSON.stringify(mouthTrack(samples, rate)) + '\n');
  }
}

function page(pack, list, results) {
  const by = new Map(results.map((r) => [r.c.id, r]));
  const rows = list.map((c) => {
    const e = pack.state[c.id];
    const r = by.get(c.id);
    const take = e?.takes.find((t) => t.n === e.chosen);
    return {
      id: c.id, kind: c.kind, pool: c.pool, tag: c.tag, text: c.screen, take: e?.chosen ?? null, takes: e?.takes.length ?? 0,
      flags: take?.qa.flags ?? ['missing'], src: r ? `out/${outPath(c)}.mp3` : null,
      dur: r?.dur, lufs: r?.lufs, tp: r?.tp, loud: r ? (r.ok ? 'ok' : 'off') : null, mode: r?.mode,
    };
  });
  writeFileSync(join(pack.dir, 'qa.json'), JSON.stringify({ built: new Date().toISOString(), model: MODEL, clips: rows }, null, 1) + '\n');
  return rows;
}

async function main() {
  const cmd = args.shift();
  const pack = new Pack(resolve(opt('pack', join(HERE, 'pack'))));
  const retakes = Number(opt('retakes', 2));
  const maxCredits = Number(opt('max-credits', 20000));
  const only = opt('ids', null)?.split(',');
  const all = clips();
  const list = only ? all.filter((c) => only.includes(c.id)) : all;
  if (cmd === 'plan') {
    const chars = list.reduce((s, c) => s + request(c).length, 0);
    const missing = list.filter((c) => !pack.state[c.id]?.takes.some((t) => t.text === request(c)));
    console.log(`${list.length} clips, ${chars} characters requested; ${missing.length} still need a take` +
      (missing.length ? ` (${missing.reduce((s, c) => s + request(c).length, 0)} characters)` : ''));
  } else if (cmd === 'gen') {
    await gen(pack, list, { retakes, maxCredits, redo: false });
  } else if (cmd === 'redo') {
    const ids = (args.shift() ?? '').split(',').filter(Boolean);
    const unknown = ids.filter((id) => !all.some((c) => c.id === id));
    if (!ids.length || unknown.length) throw new Error(`usage: node pack.mjs redo <id,id...> (unknown: ${unknown})`);
    await gen(pack, all.filter((c) => ids.includes(c.id)), { retakes: 0, maxCredits, redo: true });
  } else if (cmd === 'pick') {
    const [id, n] = args;
    const e = pack.state[id];
    if (!e?.takes.some((t) => t.n === Number(n))) throw new Error(`no take ${n} of ${id}`);
    e.chosen = Number(n);
    pack.save();
  } else if (cmd === 'import') {
    const from = resolve(args.shift());
    for (const c of list) {
      const f = [join(from, `${c.id}.mp3`), join(from, 'repeat', `${c.id}.mp3`)].find(existsSync);
      if (!f || pack.state[c.id]?.takes.length) continue;
      await pack.add(c, readFileSync(f), { at: new Date().toISOString(), imported: f });
      pack.choose(c.id);
    }
    pack.save();
    console.log(`imported ${Object.keys(pack.state).length} clips`);
  } else if (cmd === 'build') {
    const results = build(pack, list);
    await mouths(pack, results);
    const rows = page(pack, all, results);
    const off = results.filter((r) => !r.ok);
    const flagged = rows.filter((r) => r.flags.length && r.take);
    console.log(`built ${results.length}/${all.length}; loudness off the bar: ${off.map((r) => `${r.c.id} (${r.lufs} LUFS, ${r.tp} dBTP)`).join(', ') || 'none'}`);
    console.log(`still flagged after retakes: ${flagged.map((r) => `${r.id} ${r.flags}`).join('; ') || 'none'}`);
    const dyn = results.filter((r) => r.mode !== 'linear');
    if (dyn.length) console.log(`loudnorm fell back to dynamic on: ${dyn.map((r) => r.c.id).join(', ')}`);
  } else {
    throw new Error('usage: node pack.mjs plan|gen|redo|pick|build|import (see the header)');
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await main();
