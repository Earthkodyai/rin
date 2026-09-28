// Writes one .vrma per gesture in src/gestures.json to public/gestures/ (git-ignored; Vite copies public/ into the
// bundle). Runs before `npm run dev` and `npm run build`, so the files always match the keyframes.
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { buildVrma } from './vrma.mjs';

const root = new URL('../', import.meta.url);
const gestures = JSON.parse(readFileSync(new URL('src/gestures.json', root), 'utf8'));
const out = new URL('public/gestures/', root);
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
let bytes = 0;
for (const [name, gesture] of Object.entries(gestures)) {
  const file = buildVrma(name, gesture);
  writeFileSync(new URL(`${name}.vrma`, out), file);
  bytes += file.length;
}
console.log(`gestures: ${Object.keys(gestures).length} files, ${(bytes / 1024).toFixed(0)} KB -> public/gestures/`);
