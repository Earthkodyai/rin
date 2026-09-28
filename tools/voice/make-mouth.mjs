// Writes a mouth track next to each voice clip: node make-mouth.mjs <clip.wav|clip.mp3>... [--out <dir>]
// Output: <name>.mouth.json ({ v, fps, f }, see mouth.mjs), which the app loads with the clip (character/Voice.kt).
import { mkdirSync, writeFileSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { readAudio } from './audio.mjs';
import { mouthTrack } from './mouth.mjs';

const args = process.argv.slice(2);
const outAt = args.indexOf('--out');
const out = outAt >= 0 ? args.splice(outAt, 2)[1] : null;
if (!args.length) throw new Error('usage: node make-mouth.mjs <clip>... [--out <dir>]');
for (const clip of args) {
  const { samples, rate } = await readAudio(clip);
  const track = mouthTrack(samples, rate);
  const dir = out ?? dirname(clip);
  mkdirSync(dir, { recursive: true });
  const file = join(dir, basename(clip).replace(/\.[^.]+$/, '.mouth.json'));
  writeFileSync(file, JSON.stringify(track) + '\n');
  console.log(`${file}: ${(track.f.length / 2 / track.fps).toFixed(2)} s`);
}
