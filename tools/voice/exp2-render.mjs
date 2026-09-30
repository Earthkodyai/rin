// Task 4.3: the "crackle" the user heard is air at the end of some words (their note, 2026-09-30: *watch*, *job*,
// *time*, *cup*). Can post-processing remove it without muffling her? Blind test (exp.html?exp=exp2), no credits:
// the same takes as the first blind test (exp/<id>/raw.mp3 = the round 1 take they heard, pcmnat = a PCM take).
//   plain    the build as it is
//   deess    ffmpeg deesser: compresses only the hiss band, only when it spikes
//   gate     a soft downward expander: quiet air between and after words drops by up to ~10 dB, speech untouched
//   both     deesser then the expander
//   node exp2-render.mjs     writes pack/exp2/<id>/<variant>.mp3 and pack/exp2/exp.json
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { ffmpegPath, master } from './pack.mjs';

export const VARIANTS = {
  plain: '',
  deess: 'deesser=i=0.7:m=0.7:f=0.45',
  gate: 'agate=threshold=0.04:ratio=2.5:range=0.3:attack=3:release=90:knee=4',
  both: 'deesser=i=0.7:m=0.7:f=0.45,agate=threshold=0.04:ratio=2.5:range=0.3:attack=3:release=90:knee=4',
};
const SOURCES = [
  ['pads.wrong.01', 'raw', 'Hmm, not that one. Watch my hand again.'],
  ['won.hard.04', 'raw', 'We got there. Good job.'],
  ['speech.missed.03', 'raw', null],
  ['game.intro.cups.01', 'raw', null],
  ['pads.wrong.01', 'pcmnat', 'Hmm, not that one. Watch my hand again.'],
  ['won.hard.04', 'pcmnat', 'We got there. Good job.'],
];
const dir = new URL('./pack/', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1');
const exp1 = JSON.parse((await import('node:fs')).readFileSync(join(dir, 'exp', 'exp.json'), 'utf8'));
const bin = ffmpegPath();
const lines = [];
for (const [id, src] of SOURCES) {
  const key = `${id}~${src}`;
  const d = join(dir, 'exp2', key);
  mkdirSync(d, { recursive: true });
  const input = join(dir, 'exp', id, src === 'raw' ? 'raw.mp3' : `${src}.src.wav`);
  for (const [name, polish] of Object.entries(VARIANTS)) master(bin, input, join(d, `${name}.mp3`), { polish });
  lines.push({ id: key, text: exp1.lines.find((l) => l.id === id).text, tag: src === 'raw' ? 'round 1 take' : 'PCM take' });
}
writeFileSync(join(dir, 'exp2', 'exp.json'), JSON.stringify({ variants: Object.keys(VARIANTS), tags: [['ok', 'ปกติ'], ['air', 'มีเสียงลม'], ['muffle', 'อู้อี้/ทื่อ']], lines }, null, 1));
console.log(`${lines.length} lines x ${Object.keys(VARIANTS).length} variants`);
