// Task 4.3: 69 of 191 chosen takes end mid-sound (the last 40 ms still 17-35 dB under peak; a clean end is ~45 dB under).
// The user heard it as lines cut off before the end. Does a text ending stop eleven_v3 from cutting? 6 cut lines x 3
// endings, measured automatically (no ear needed for this part):
//   plain   the text as is            ellipsis   text + " …"            pause   text + " [short pause]"
//   node exp3-tail.mjs          writes pack/exp3/<id>.<variant>.wav and prints the end loudness per take
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { readAudio } from './audio.mjs';
import { clips, Eleven, request } from './pack.mjs';

export const ENDINGS = { plain: '', ellipsis: ' …', pause: ' [short pause]' };
const LINES = ['ring.sleepy.02', 'R01', 'snooze.again.02', 'ring.cheerful.04', 'pads.wrong.06', 'after.meal.lunch.02'];
const dir = new URL('./pack/', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1');
const api = new Eleven(600);
const all = clips();

/** RMS of the last 40 ms, in dB under the take's peak. */
export function endDb(s, rate) {
  let p = 0;
  for (const x of s) p = Math.max(p, Math.abs(x));
  const n = Math.round(0.04 * rate);
  let e = 0;
  for (let i = s.length - n; i < s.length; i++) e += s[i] * s[i];
  return 20 * Math.log10(Math.max(Math.sqrt(e / n), 1e-9) / Math.max(p, 1e-9));
}

mkdirSync(join(dir, 'exp3'), { recursive: true });
const rows = [];
for (const id of LINES) {
  const c = all.find((x) => x.id === id);
  const row = { id };
  for (const [name, end] of Object.entries(ENDINGS)) {
    const { bytes } = await api.say(request(c) + end, join(dir, 'gen-log.jsonl'));
    const f = join(dir, 'exp3', `${id}.${name}.wav`);
    writeFileSync(f, bytes);
    const { samples, rate } = await readAudio(f);
    row[name] = +endDb(samples, rate).toFixed(1);
  }
  rows.push(row);
  console.log(JSON.stringify(row));
}
writeFileSync(join(dir, 'exp3', 'ends.json'), JSON.stringify(rows, null, 1));
console.log(`credits: ${api.spent}`);
