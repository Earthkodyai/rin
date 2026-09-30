// Task 4.3, air test 2. exp4 showed the air sits in the hiss band during speech: the de-esser removed it on 6 of 6
// lines (gate and FFT denoise on 1 of 6), but at full strength it wobbled ("robot") on 2 and muffled 1. The usual
// cures for de-esser wobble: a gentler de-esser, or a static high-shelf cut, which cannot pump. Blind (exp.html?exp=exp5):
//   deess     exp4's de-esser (i 0.7, m 0.7), the reference that removed the air
//   soft      gentler de-esser (i 0.4, m 0.5)
//   shelf6    static EQ: -6 dB above 6.5 kHz
//   shelf4    static EQ: -4 dB above 8 kHz
//   mix       gentle de-esser plus a light shelf (-3 dB above 7 kHz)
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { clips, ffmpegPath, master } from './pack.mjs';

export const VARIANTS = {
  deess: 'deesser=i=0.7:m=0.7:f=0.45',
  soft: 'deesser=i=0.4:m=0.5:f=0.45',
  shelf6: 'highshelf=f=6500:g=-6:t=s:w=0.7',
  shelf4: 'highshelf=f=8000:g=-4:t=s:w=0.7',
  mix: 'deesser=i=0.4:m=0.5:f=0.45,highshelf=f=7000:g=-3:t=s:w=0.7',
};
// The two that wobbled, two where the de-esser was clean, and the two sent back for hiss.
const LINES = ['after.remark.04', 'R04', 'cups.wrong.06', 'won.hard.04', 'snooze.first.04', 'snooze.again.03'];
const dir = new URL('./pack/', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1');
const state = JSON.parse(readFileSync(join(dir, 'takes.json'), 'utf8'));
const all = clips();
const bin = ffmpegPath();
const lines = [];
for (const id of LINES) {
  const e = state[id];
  const take = e.takes.find((t) => t.n === e.chosen);
  const d = join(dir, 'exp5', id);
  mkdirSync(d, { recursive: true });
  for (const [name, polish] of Object.entries(VARIANTS)) master(bin, join(dir, take.file), join(d, `${name}.mp3`), { polish, tmp: d });
  const c = all.find((x) => x.id === id);
  lines.push({ id, text: c.screen, tag: c.tag });
}
writeFileSync(join(dir, 'exp5', 'exp.json'), JSON.stringify({
  title: 'เสียงลม รอบ 2: ลดลมโดยไม่ให้สั่น (ซ่อนชื่อ)',
  hint: '6 ประโยค ประโยคละ 5 แบบ สลับลำดับแล้ว ทุกแบบพยายามลดเสียงลมท้ายคำ แต่ละแบบลดด้วยวิธีต่างกัน กดป้ายที่ได้ยิน (กดได้หลายอัน) แล้วกด ★ ที่แบบที่ชอบที่สุด',
  variants: Object.keys(VARIANTS),
  tags: [['ok', 'ปกติ'], ['air', 'ยังมีเสียงลม'], ['wobble', 'สั่น/แปลก'], ['muffle', 'อู้อี้/ทื่อ']],
  lines,
}, null, 1));
console.log(`${lines.length} lines x ${Object.keys(VARIANTS).length} variants`);
