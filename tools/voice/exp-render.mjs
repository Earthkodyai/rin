// Task 4.3 QA round 1 follow-up: why did the user hear crackle, hiss and muffling on 66 of 191 clips? A blind test on
// 8 of the lines they marked, 5 versions each (page exp.html, results in docs/spikes/4.3-voice-pack.md):
//   raw     the chosen take as ElevenLabs sent it (mp3_44100_128), untouched
//   built   the same take after pack.mjs build (what the user heard): raw vs built isolates our processing
//   mp3rob  a new take, mp3_44100_128, stability 1 (robust), mastered at 128 kb/s
//   pcmrob  a new take, pcm_24000 (lossless, 12 kHz band), stability 1: mp3rob vs pcmrob isolates the source format
//   pcmnat  a new take, pcm_24000, stability 0.5 (natural): pcmrob vs pcmnat isolates stability
//   node exp-render.mjs      writes pack/exp/<id>/<variant>.mp3 and pack/exp/exp.json (~500 credits)
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { clips, Eleven, ffmpegPath, master, outPath, request } from './pack.mjs';

export const LINES = ['pads.wrong.01', 'won.hard.04', 'speech.missed.03', 'game.intro.cups.01', // crackle on a "!" word
  'R04', 'back.first.01', // hiss
  'app.morning.01', 'p5.sick.01']; // muffled
const dir = new URL('./pack/', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1');
const state = JSON.parse(readFileSync(join(dir, 'takes.json'), 'utf8'));
const bin = ffmpegPath();
const api = new Eleven(1500);
const log = join(dir, 'gen-log.jsonl');
const all = clips();
const out = [];
for (const id of LINES) {
  const c = all.find((x) => x.id === id);
  const d = join(dir, 'exp', id);
  mkdirSync(d, { recursive: true });
  const take = state[id].takes.find((t) => t.n === state[id].chosen);
  copyFileSync(join(dir, take.file), join(d, 'raw.mp3'));
  copyFileSync(join(dir, 'out', `${outPath(c)}.mp3`), join(d, 'built.mp3'));
  for (const [name, format, stability] of [['mp3rob', 'mp3_44100_128', 1], ['pcmrob', 'pcm_24000', 1], ['pcmnat', 'pcm_24000', 0.5]]) {
    if (existsSync(join(d, `${name}.mp3`))) continue;
    const { bytes, ext } = await api.say(request(c), log, { format, stability });
    const src = join(d, `${name}.src`);
    writeFileSync(`${src}.${ext}`, bytes);
    master(bin, `${src}.${ext}`, join(d, `${name}.mp3`), { kbps: 128 });
  }
  out.push({ id, text: c.screen, tag: c.tag });
}
writeFileSync(join(dir, 'exp', 'exp.json'), JSON.stringify({ variants: ['raw', 'built', 'mp3rob', 'pcmrob', 'pcmnat'], lines: out }, null, 1));
console.log(`credits: ${api.spent}`);
