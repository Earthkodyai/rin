// Lip-sync shape accuracy on one-syllable words with a known vowel (eval/vowels.json, frozen before any analysis).
//   node eval.mjs dev    accuracy on the dev words/voice with the current centroids, plus per-shape formant medians
//   node eval.mjs fit    writes centroids.json from the dev medians (then frozen)
//   node eval.mjs test   held-out words and voices, run once after fitting; writes eval/results-test.json
//   --spec rin           task 4.3: eval/vowels-rin.json on Rin's own voice (clips from make-vowels-rin.mjs, .mp3),
//                        results in eval/results-test-rin.json
// A word counts as right when the most common shape over its nucleus (voiced frames with LPC formants, at least half
// open) is its label. Clips come from eval/make_vowels.py (Kokoro, local; git-ignored).
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { analyse, classify, SHAPES } from './mouth.mjs';
import { readAudio } from './audio.mjs';

const here = (p) => new URL(p, import.meta.url);
const specAt = process.argv.indexOf('--spec');
const suffix = specAt >= 0 ? `-${process.argv.splice(specAt, 2)[1]}` : '';
const spec = JSON.parse(readFileSync(here(`eval/vowels${suffix}.json`), 'utf8'));
const mode = process.argv[2];
if (!['dev', 'fit', 'test'].includes(mode)) throw new Error('usage: node eval.mjs dev|fit|test');
const split = mode === 'test' ? 'test' : 'dev';

const median = (xs) => {
  const s = [...xs].sort((a, b) => a - b);
  return s.length ? s[Math.floor(s.length / 2)] : NaN;
};

/** vowels.json names shapes by VRM viseme; tracks use one letter. */
const SHAPE_OF = { aa: 'a', ih: 'i', ou: 'u', ee: 'e', oh: 'o' };
const words = [];
for (const voice of spec[split].voices) {
  for (const [viseme, list] of Object.entries(spec[split].words)) {
    const label = SHAPE_OF[viseme];
    for (const word of list) {
      const path = ['wav', 'mp3'].map((ext) => here(`eval/clips/${voice}/${viseme}-${word}.${ext}`)).find(existsSync) ??
        here(`eval/clips/${voice}/${viseme}-${word}.wav`);
      if (!existsSync(path)) throw new Error(`missing ${path.pathname}: run eval/make_vowels.py`);
      const { samples, rate } = await readAudio(path.pathname.replace(/^\/([A-Z]:)/, '$1'));
      const nucleus = analyse(samples, rate).filter((f) => f.formants && f.open >= 0.5);
      const votes = Object.fromEntries(SHAPES.map((s) => [s, 0]));
      for (const f of nucleus) votes[classify(f.formants)]++;
      const predicted = nucleus.length ? Object.entries(votes).sort((a, b) => b[1] - a[1])[0][0] : '-';
      words.push({ voice, word, label, predicted, frames: nucleus.length, formants: nucleus.map((f) => f.formants) });
    }
  }
}

const right = words.filter((w) => w.label === w.predicted).length;
const confusion = Object.fromEntries(SHAPES.map((l) => [l, Object.fromEntries([...SHAPES, '-'].map((p) => [p, 0]))]));
for (const w of words) confusion[w.label][w.predicted]++;
console.log(`${split}: ${right}/${words.length} words (${((100 * right) / words.length).toFixed(0)}%)`);
console.log('label -> predicted counts');
for (const [label, row] of Object.entries(confusion)) console.log(`  ${label}: ${JSON.stringify(row)}`);
const misses = words.filter((w) => w.label !== w.predicted).map((w) => `${w.voice}/${w.word} ${w.label}->${w.predicted}`);
if (misses.length) console.log('misses:', misses.join(', '));

const medians = Object.fromEntries(
  SHAPES.map((s) => {
    const all = words.filter((w) => w.label === s).flatMap((w) => w.formants);
    return [s, [Math.round(median(all.map((f) => f[0]))), Math.round(median(all.map((f) => f[1])))]];
  }),
);
console.log('F1/F2 medians by label:', JSON.stringify(medians));

if (mode === 'fit') {
  const current = JSON.parse(readFileSync(here('centroids.json'), 'utf8'));
  const fitted = {
    _note: `Fitted ${new Date().toISOString().slice(0, 10)} by eval.mjs fit: medians of F1/F2 over nucleus frames of the dev words (${spec.dev.voices.join(', ')}). Frozen before the held-out run.`,
    f1Weight: current.f1Weight,
    shapes: medians,
  };
  writeFileSync(here('centroids.json'), JSON.stringify(fitted, null, 2) + '\n');
  console.log('centroids.json written');
}
if (mode === 'test') {
  const result = { date: new Date().toISOString(), right, total: words.length, confusion, misses, passBar: spec.passBar };
  writeFileSync(here(`eval/results-test${suffix}.json`), JSON.stringify(result, null, 2) + '\n');
}
