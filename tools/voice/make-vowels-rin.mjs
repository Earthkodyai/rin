// Task 4.3: the vowel-shape words (eval/vowels-rin.json, frozen before any clip) in Rin soft, paid month.
//   node make-vowels-rin.mjs     writes eval/clips/rin-soft/<shape>-<word>.mp3 (git-ignored), skips clips that exist
// Each word is read alone with a full stop and no tag, like the Kokoro words in 2.4. Then: node eval.mjs dev --spec rin
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { Eleven } from './pack.mjs';

const here = (p) => new URL(p, import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1');
const spec = JSON.parse(readFileSync(here('eval/vowels-rin.json'), 'utf8'));
const api = new Eleven(2000);
const log = join(here('pack'), 'gen-log.jsonl');
mkdirSync(here('pack'), { recursive: true });
for (const split of ['dev', 'test']) {
  for (const voice of spec[split].voices) {
    const dir = here(`eval/clips/${voice}`);
    mkdirSync(dir, { recursive: true });
    for (const [shape, words] of Object.entries(spec[split].words)) {
      for (const word of words) {
        const file = join(dir, `${shape}-${word}.mp3`);
        if (existsSync(file)) continue;
        const { bytes } = await api.say(`${word}.`, log);
        writeFileSync(file, bytes);
      }
    }
  }
}
console.log(`credits: ${api.spent}`);
