#!/usr/bin/env node
// The store's feature graphic, 1024×500 (task 6.5): Rin waist up on the right, the RinAlarm logo and one line on the
// left, in the app's day look and its night look, for the user to pick. Rendered by headless Chrome/Edge with the
// app's font.
//   node feature-graphic.mjs <waist-still.webp> <logo.png> <out-dir>
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import puppeteer from 'puppeteer-core';

const [still, logo, out] = process.argv.slice(2);
if (!still || !logo || !out) {
  console.error('usage: node feature-graphic.mjs <waist-still.webp> <logo.png> <out-dir>');
  process.exit(2);
}
fs.mkdirSync(out, { recursive: true });
const executablePath = [
  process.env.RIN_BROWSER,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
].find((p) => p && fs.existsSync(p));
const url = (p) => pathToFileURL(path.resolve(p)).href;
const font = url('../../app/src/main/res/font/mplus_rounded_extrabold.ttf');

const LOOKS = {
  day: { ground: 'linear-gradient(120deg, #FFF4EA 0%, #FFE1E9 70%)', sun: '#FFD27A', ink: '#3A2A33', muted: '#7A5A68', stars: false },
  night: { ground: 'linear-gradient(120deg, #1C2350 0%, #121836 70%)', sun: '#FFE9A8', ink: '#F3EEFF', muted: '#B9B3D9', stars: true },
};

const html = (look) => `<!doctype html><html><head><meta charset="utf-8"><style>
  @font-face { font-family: Rounded; src: url('${font}'); }
  * { margin: 0; box-sizing: border-box; }
  body { font-family: Rounded, sans-serif; }
  .g { position: relative; width: 1024px; height: 500px; overflow: hidden; background: ${look.ground}; }
  .sun { position: absolute; right: 40px; top: -70px; width: 300px; height: 300px; border-radius: 50%; background: ${look.sun}; opacity: 0.95; }
  .rin { position: absolute; right: 70px; top: 18px; height: 560px; }
  .logo { position: absolute; left: 56px; top: 118px; width: 470px; }
  .line { position: absolute; left: 70px; top: 300px; font-size: 34px; line-height: 1.25; color: ${look.ink}; }
  .line small { display: block; margin-top: 14px; font-size: 22px; color: ${look.muted}; }
  .star { position: absolute; width: 4px; height: 4px; border-radius: 50%; background: #fff; opacity: 0.75; }
</style></head><body><div class="g">
  ${look.stars ? [[80, 60], [300, 40], [520, 90], [460, 420], [150, 450], [600, 30], [40, 300]].map(([x, y]) => `<div class="star" style="left:${x}px;top:${y}px"></div>`).join('') : ''}
  <div class="sun"></div><img class="rin" src="${url(still)}"><img class="logo" src="${url(logo)}">
  <div class="line">Beat her at a quick game,<br>and the alarm stops.<small>Offline · Free · No ads</small></div>
</div></body></html>`;

const browser = await puppeteer.launch({ executablePath, headless: true, args: ['--allow-file-access-from-files'] });
const tab = await browser.newPage();
await tab.setViewport({ width: 1024, height: 500 });
for (const [name, look] of Object.entries(LOOKS)) {
  const file = path.join(out, `fg-${name}.html`);
  fs.writeFileSync(file, html(look));
  await tab.goto(pathToFileURL(file).href);
  await tab.evaluate(() => document.fonts.ready);
  await (await tab.$('.g')).screenshot({ path: path.join(out, `feature-${name}.png`) });
  fs.rmSync(file);
}
await browser.close();
console.log('wrote', out);
