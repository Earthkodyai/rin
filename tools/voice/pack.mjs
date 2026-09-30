// Rin's voice pack (task 4.3): every line in lines.json plus the Repeat after Rin sentences, in ElevenLabs "Rin soft"
// on eleven_v3 (the user's pick over v4 in a blind test, 9–1, 2026-09-30). Run it only inside the paid month: output
// from a paid plan carries the commercial licence, free-plan output does not (docs/spikes/S4-voice.md).
//
//   node pack.mjs plan                        clips, characters, what is still missing
//   node pack.mjs gen [--retakes 2] [--max-credits 20000] [--ids a,b]
//                                             first takes for missing clips; a take with flags (pack-qa.mjs) is
//                                             retaken up to --retakes times and the best one is kept
//   node pack.mjs redo <id,id...> [--takes 3] new takes for clips the user marked on the listening page; they wait
//                                             as candidates until the user picks one there
//   node pack.mjs pick <id>:<take>[,...]      keep that take (a candidate the user picked, or an earlier one)
//   node pack.mjs build                       chosen takes -> trim, gain to -16 LUFS + limiter, 10 ms fades, MP3 96 kb/s mono,
//                                             mouth track; then writes qa.json for the listening page (qa.html)
//   node pack.mjs import <dir>                dry run: use <dir>/<id>.mp3 as take 1 (no API, no credits)
// Options: --pack <dir> (default tools/voice/pack, git-ignored). Key: ELEVENLABS_API_KEY or spikes/s4-voice/.env;
// voice id: RIN_VOICE_ID or spikes/s4-voice/out/eleven_voices.json. ffmpeg: FFMPEG, PATH, or the winget install.
import { spawnSync } from 'node:child_process';
import { appendFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
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
// Lossless PCM (24 kHz, the best Starter allows): in a blind test the user heard crackle on 4 of 8 lines and hiss on 1
// from the mp3_44100_128 source, and 2 and 0 from PCM (QA round 1, 2026-09-30). Round 1 takes are MP3.
const FORMAT = 'pcm_24000';
/** v3 natural. Robust (1) was no cleaner in the same test and reads flatter. */
const STABILITY = 0.5;
/** One tag per Repeat sentence, by meaning (repeat-tags.json); round 1 read all 30 [warmly] and sounded monotone. */
const REPEAT_TAGS = JSON.parse(readFileSync(join(HERE, 'repeat-tags.json'), 'utf8')).tags;
export const LOUDNESS = { I: -16, TP: -2 };
/** What a built clip must measure: loudness within 1 LU of -16, true peak (after MP3) at or under -1 dBTP. */
export const BAR = { lu: 1, tp: -1 };

/** Every clip in the pack: its id, where it goes, and exactly what TTS reads. */
export function clips({ script, repeat } = load()) {
  const lines = script.lines.map((l) => ({ id: l.id, kind: 'line', pool: l.pool, tag: l.tag, text: spoken(l), screen: l.text }));
  const rs = repeat.map((r) => ({ id: r.id, kind: 'repeat', pool: 'repeat', tag: REPEAT_TAGS[r.id] ?? '[warmly]', text: r.text, screen: r.text }));
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

export function ffmpegPath() {
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

  /** Adds a take (mp3 or wav bytes), checks it, and records it. */
  async add(c, bytes, meta = {}, ext = 'mp3') {
    const e = this.entry(c.id);
    const n = e.takes.length + 1;
    const file = `takes/${c.id}.${n}.${ext}`;
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

export class Eleven {
  constructor(maxCredits) {
    this.key = secret('ELEVENLABS_API_KEY', join(S4, '.env'), (s) =>
      s.split(/\r?\n/).find((l) => l.startsWith('ELEVENLABS_API_KEY='))?.split('=')[1].trim());
    this.voice = secret('RIN_VOICE_ID', join(S4, 'out', 'eleven_voices.json'), (s) => JSON.parse(s)['el-rin-soft']);
    this.maxCredits = maxCredits;
    this.spent = 0;
  }

  /** One request. `format` is an ElevenLabs output_format; `stability` (v3: 0 creative, 0.5 natural, 1 robust) is
   * left to the voice's saved setting unless given. */
  async say(text, log, { format = FORMAT, stability = STABILITY } = {}) {
    if (this.spent >= this.maxCredits) throw new Error(`stopped at --max-credits ${this.maxCredits} (spent ${this.spent})`);
    const r = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${this.voice}?output_format=${format}`, {
      method: 'POST',
      headers: { 'xi-api-key': this.key, 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, model_id: MODEL, ...(stability === undefined ? {} : { voice_settings: { stability } }) }),
    });
    if (!r.ok) throw new Error(`ElevenLabs ${r.status}: ${await r.text()}`);
    const body = Buffer.from(await r.arrayBuffer());
    const rate = format.startsWith('pcm_') ? Number(format.slice(4)) : 0;
    const bytes = rate ? wav(body, rate) : body;
    const meta = {
      at: new Date().toISOString(),
      cost: Number(r.headers.get('character-cost')),
      request: r.headers.get('request-id'),
      history: r.headers.get('history-item-id'),
    };
    this.spent += meta.cost || 0;
    // Evidence that every clip came from the paid month (S4 rule): time, request and history ids, credits.
    appendFileSync(log, JSON.stringify({ text, model: MODEL, format, stability, ...meta }) + '\n');
    return { bytes, meta, ext: rate ? 'wav' : 'mp3' };
  }
}

/** Raw 16-bit little-endian mono PCM as a WAV file. */
export function wav(pcm, rate) {
  const h = Buffer.alloc(44);
  h.write('RIFF', 0); h.writeUInt32LE(36 + pcm.length, 4); h.write('WAVEfmt ', 8); h.writeUInt32LE(16, 16);
  h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22); h.writeUInt32LE(rate, 24); h.writeUInt32LE(rate * 2, 28);
  h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34); h.write('data', 36); h.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([h, pcm]);
}

async function gen(pack, list, { retakes, maxCredits, redo, takes = 1 }) {
  const api = new Eleven(maxCredits);
  const log = join(pack.dir, 'gen-log.jsonl');
  for (const c of list) {
    const e = pack.entry(c.id);
    const fresh = e.takes.filter((t) => t.text === request(c));
    if (!redo && fresh.length) continue;
    // A redo makes `takes` candidates for the user to pick from by ear (the automatic checks missed most of what they
    // heard in round 1); otherwise up to 1 + retakes until one has no flags.
    const budget = redo ? takes : 1 + retakes;
    const made = [];
    for (let i = 0; i < budget; i++) {
      const { bytes, meta, ext } = await api.say(request(c), log);
      const t = await pack.add(c, bytes, meta, ext);
      made.push(t.n);
      console.log(`${c.id} take ${t.n}: ${t.qa.flags.join(',') || 'ok'} (${meta.cost} credits, total ${api.spent})`);
      pack.save();
      if (!redo && !t.qa.flags.length) break;
    }
    if (redo) {
      e.candidates = made;
      e.chosen = made[0];
    } else pack.choose(c.id);
    pack.save();
  }
  console.log(`credits this run: ${api.spent}`);
}

/** Integrated loudness (LUFS) and true peak (dBTP) by ffmpeg's EBU R128 meter, the same meter for input and output. */
function meter(bin, file) {
  const v = ffmpeg(bin, ['-i', file, '-af', 'ebur128=peak=true', '-f', 'null', '-']);
  const summary = v.slice(v.lastIndexOf('Summary:'));
  return {
    lufs: Number(summary.match(/I:\s+(-?[\d.]+) LUFS/)[1]),
    tp: Number(summary.match(/Peak:\s+(-?[\d.]+|-inf) dBFS/)[1]),
  };
}

// Plain gain to -16 LUFS, then a look-ahead limiter only for the peaks that gain pushes past the ceiling. The first
// build used loudnorm, which left 3 clips 1.3-2.2 LU quiet and fell back to its dynamic mode (a pumping compressor)
// on 13 (2026-09-30).
const LIMIT_DB = LOUDNESS.TP - 0.5;

const TRIM = 'silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,areverse,' +
  'silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.15,areverse';

/** One take (any format ffmpeg reads) -> trimmed, -16 LUFS, peak-limited, faded MP3 at `kbps`. */
export function master(bin, input, mp3, { kbps = 96, tmp = dirname(mp3) } = {}) {
  const wav = join(tmp, `_${process.pid}_${Math.random().toString(36).slice(2)}.wav`);
  ffmpeg(bin, ['-i', input, '-af', TRIM, '-ac', '1', '-ar', '44100', '-c:a', 'pcm_s16le', wav]);
  const dur = (readFileSync(wav).length - 44) / 2 / 44100;
  const before = meter(bin, wav);
  const limit = 10 ** (LIMIT_DB / 20);
  let gain = LOUDNESS.I - before.lufs;
  let m;
  // The limiter takes a little loudness off the loudest clips, so a second pass tops the gain up.
  for (let pass = 0; pass < 3; pass++) {
    ffmpeg(bin, ['-i', wav, '-af',
      `volume=${gain.toFixed(2)}dB,alimiter=limit=${limit.toFixed(4)}:level=0:attack=5:release=50:latency=1,` +
      `afade=t=in:d=0.01,afade=t=out:st=${Math.max(0, dur - 0.01).toFixed(3)}:d=0.01`,
      '-ar', '44100', '-ac', '1', '-c:a', 'libmp3lame', '-b:a', `${kbps}k`, mp3]);
    m = meter(bin, mp3);
    if (Math.abs(m.lufs - LOUDNESS.I) <= 0.3) break;
    gain += LOUDNESS.I - m.lufs;
  }
  rmSync(wav);
  const ok = Math.abs(m.lufs - LOUDNESS.I) <= BAR.lu && m.tp <= BAR.tp;
  const mode = before.tp + gain > LIMIT_DB ? 'limited' : 'gain';
  return { dur: +dur.toFixed(2), lufs: m.lufs, tp: m.tp, gain: +gain.toFixed(2), mode, ok };
}

function build(pack, list) {
  const bin = ffmpegPath();
  const out = join(pack.dir, 'out');
  const tmp = join(pack.dir, 'tmp');
  mkdirSync(join(out, 'repeat'), { recursive: true });
  mkdirSync(tmp, { recursive: true });
  const results = [];
  for (const c of list) {
    const e = pack.state[c.id];
    if (!e?.chosen) continue;
    const take = e.takes.find((t) => t.n === e.chosen);
    results.push({ c, take, ...master(bin, join(pack.dir, take.file), join(out, `${outPath(c)}.mp3`), { tmp }) });
    // Candidates waiting for the user's pick are mastered the same way, outside out/ (so never into the app).
    for (const n of e.candidates ?? []) {
      mkdirSync(join(pack.dir, 'cand'), { recursive: true });
      const t = e.takes.find((x) => x.n === n);
      master(bin, join(pack.dir, t.file), join(pack.dir, 'cand', `${c.id}.${n}.mp3`), { tmp });
    }
  }
  return results;
}

/** Vowel shapes are on for Rin's own voice: 18/20 held-out words (bar 60%), eval/results-test-rin.json. */
export const SHAPES_ON = true;

async function mouths(pack, results) {
  for (const r of results) {
    const base = join(pack.dir, 'out', outPath(r.c));
    const { samples, rate } = await readAudio(`${base}.mp3`);
    writeFileSync(`${base}.mouth.json`, JSON.stringify(mouthTrack(samples, rate, { shapes: SHAPES_ON })) + '\n');
  }
}

function page(pack, list, results) {
  const by = new Map(results.map((r) => [r.c.id, r]));
  // A build of some clips (--ids) keeps the other rows' measurements from the last build.
  const qaFile = join(pack.dir, 'qa.json');
  const old = new Map(existsSync(qaFile) ? JSON.parse(readFileSync(qaFile, 'utf8')).clips.map((r) => [r.id, r]) : []);
  const rows = list.map((c) => {
    const e = pack.state[c.id];
    const r = by.get(c.id);
    const o = old.get(c.id);
    const take = e?.takes.find((t) => t.n === e.chosen);
    const built = r || (o?.take === e?.chosen && o?.src);
    return {
      id: c.id, kind: c.kind, pool: c.pool, tag: c.tag, text: c.screen, take: e?.chosen ?? null, takes: e?.takes.length ?? 0,
      flags: take?.qa.flags ?? ['missing'], src: built ? `out/${outPath(c)}.mp3` : null,
      dur: r?.dur ?? o?.dur, lufs: r?.lufs ?? o?.lufs, tp: r?.tp ?? o?.tp, loud: r ? (r.ok ? 'ok' : 'off') : o?.loud, mode: r?.mode ?? o?.mode,
      candidates: e?.candidates?.map((n) => ({ n, src: `cand/${c.id}.${n}.mp3`, flags: e.takes.find((t) => t.n === n).qa.flags })),
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
    await gen(pack, all.filter((c) => ids.includes(c.id)), { retakes: 0, maxCredits, redo: true, takes: Number(opt('takes', 3)) });
  } else if (cmd === 'pick') {
    for (const pair of (args.shift() ?? '').split(',').filter(Boolean)) {
      const [id, n] = pair.split(':');
      const e = pack.state[id];
      if (!e?.takes.some((t) => t.n === Number(n))) throw new Error(`no take ${n} of ${id}`);
      e.chosen = Number(n);
      delete e.candidates;
    }
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
    const lim = results.filter((r) => r.mode === 'limited');
    console.log(`limiter touched peaks on ${lim.length}: ${lim.map((r) => r.c.id).join(', ')}`);
  } else {
    throw new Error('usage: node pack.mjs plan|gen|redo|pick|build|import (see the header)');
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await main();
