// Task 4.3: the user hears air (breath noise) at word ends on almost every clip (2026-09-30), and asked for the usual
// fix. Standard voice clean-up, all built into ffmpeg (no download), blind by ear (exp.html?exp=exp4), no credits:
//   plain    the build as it is
//   deess    de-esser: compresses the hiss band only when it spikes
//   gate     soft downward expander: sound under ~-29 dBFS (air after and between words) drops up to 14 dB (at -36 it barely acted: the air is louder)
//   denoise  FFT noise reduction (afftdn) that tracks the noise floor: steady hiss under the voice
//   all      denoise, then de-esser, then expander
// Sources are the chosen takes of 6 lines the user noted air on.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { clips, ffmpegPath, master } from './pack.mjs';

export const VARIANTS = {
  plain: '',
  deess: 'deesser=i=0.7:m=0.7:f=0.45',
  gate: 'agate=threshold=0.035:ratio=3:range=0.2:attack=2:release=80:knee=3',
  denoise: 'afftdn=nr=12:nf=-50:tn=1',
  all: 'afftdn=nr=12:nf=-50:tn=1,deesser=i=0.7:m=0.7:f=0.45,agate=threshold=0.035:ratio=3:range=0.2:attack=2:release=80:knee=3',
};
const LINES = ['cups.wrong.06', 'pads.wrong.01', 'won.hard.04', 'after.remark.04', 'speech.missed.03', 'R04'];
const dir = new URL('./pack/', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1');
const state = JSON.parse(readFileSync(join(dir, 'takes.json'), 'utf8'));
const all = clips();
const bin = ffmpegPath();
const lines = [];
for (const id of LINES) {
  const e = state[id];
  const take = e.takes.find((t) => t.n === e.chosen);
  const d = join(dir, 'exp4', id);
  mkdirSync(d, { recursive: true });
  for (const [name, polish] of Object.entries(VARIANTS)) master(bin, join(dir, take.file), join(d, `${name}.mp3`), { polish, tmp: d });
  const c = all.find((x) => x.id === id);
  lines.push({ id, text: c.screen, tag: c.tag });
}
writeFileSync(join(dir, 'exp4', 'exp.json'), JSON.stringify({
  title: 'เสียงลม: เทียบวิธีแก้มาตรฐาน (ซ่อนชื่อ)',
  hint: '6 ประโยคที่มีเสียงลม แต่ละประโยค 5 แบบ สลับลำดับแล้ว ฟังช่วงท้ายคำให้ดี กดป้ายที่ได้ยิน (กดได้หลายอัน) หรือ "ปกติ" แล้วกด ★ ที่แบบที่ชอบที่สุด ระวังว่าแบบที่ลมหายอาจทำให้เสียงทื่อ อู้อี้ หรือมีเสียงแปลกๆ (เหมือนใต้น้ำ/หุ่นยนต์) ถ้าเป็นแบบนั้นให้กดป้ายนั้น',
  variants: Object.keys(VARIANTS),
  tags: [['ok', 'ปกติ'], ['air', 'มีเสียงลม'], ['muffle', 'อู้อี้/ทื่อ'], ['robot', 'เสียงแปลก/หุ่นยนต์']],
  lines,
}, null, 1));
console.log(`${lines.length} lines x ${Object.keys(VARIANTS).length} variants`);
