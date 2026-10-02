#!/usr/bin/env node
// Draft "RinAlarm" logos on the app icon (task 6.5, the user's pick: Rin's face with the name): three logo styles
// rendered by headless Chrome/Edge with the app's own font (M PLUS Rounded 1c), each at Play's 512 px and at launcher
// size, on one contact sheet. Also writes each logo alone on a transparent ground for the feature graphic.
//   node logo-options.mjs <rin-still.webp> <out-dir> [--browser <path>]
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import puppeteer from 'puppeteer-core';
import sharp from 'sharp';

const args = process.argv.slice(2);
const [still, out] = args;
const browserArg = args.indexOf('--browser');
if (!still || !out) {
  console.error('usage: node logo-options.mjs <rin-still.webp> <out-dir> [--browser <path>]');
  process.exit(2);
}
fs.mkdirSync(out, { recursive: true });
const BROWSERS = [
  process.env.RIN_BROWSER,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
].filter(Boolean);
const executablePath = browserArg >= 0 ? args[browserArg + 1] : BROWSERS.find((p) => fs.existsSync(p));

const font = pathToFileURL(path.resolve('../../app/src/main/res/font/mplus_rounded_extrabold.ttf')).href;
const rin = pathToFileURL(path.resolve(still)).href;

const PINK = '#CC3169';
const CANDY = '#FF6F9C';
const BLUSH = '#FFE1E9';
const INK = '#3A2A33';
const GOLD = '#FFD27A';

/** A small twin-bell clock, for logos that carry one. */
const clockSvg = (size, rim = PINK, face = '#FFFFFF') => `
  <svg width="${size}" height="${size}" viewBox="0 0 100 100" style="display:block">
    <path d="M14 30 a18 18 0 0 1 30 -16 z" fill="${rim}"/><path d="M86 30 a18 18 0 0 0 -30 -16 z" fill="${rim}"/>
    <line x1="30" y1="86" x2="22" y2="96" stroke="${rim}" stroke-width="8" stroke-linecap="round"/>
    <line x1="70" y1="86" x2="78" y2="96" stroke="${rim}" stroke-width="8" stroke-linecap="round"/>
    <circle cx="50" cy="56" r="38" fill="${rim}"/><circle cx="50" cy="56" r="30" fill="${face}"/>
    <line x1="50" y1="54" x2="37" y2="44" stroke="${INK}" stroke-width="6" stroke-linecap="round"/>
    <line x1="50" y1="54" x2="66" y2="40" stroke="${INK}" stroke-width="6" stroke-linecap="round"/>
  </svg>`;

/** A clock as a letter's round part: a white face in a [rim] ring, its hands at 10:10, two small bells on top. */
const clockGlyph = (size, rim, face = '#FFFFFF') => `
  <svg class="glyph" width="${size}" height="${size * 1.12}" viewBox="0 -12 100 112">
    <path d="M10 14 a16 16 0 0 1 28 -16 z" fill="${rim}"/><path d="M90 14 a16 16 0 0 0 -28 -16 z" fill="${rim}"/>
    <circle cx="50" cy="52" r="44" fill="${rim}"/><circle cx="50" cy="52" r="31" fill="${face}"/>
    <line x1="50" y1="52" x2="35" y2="40" stroke="${INK}" stroke-width="9" stroke-linecap="round"/>
    <line x1="50" y1="52" x2="68" y2="36" stroke="${INK}" stroke-width="9" stroke-linecap="round"/>
  </svg>`;

/**
 * The logo (the user's picks, 2026-10-02): "RinAlarm" with the dot of the i a little alarm clock, outlined letters
 * with no box behind them. Two outlines to choose from. [fill]: Rin's colour, Alarm's colour, the outline.
 */
const word = (rin, alarm, clockRim, clockFace) =>
  `<span><b style="color:${rin}">R<span class="ii"><span class="dot">${clockGlyph(30, clockRim, clockFace)}</span><span class="stem" style="background:${rin}"></span></span>n</b><b style="color:${alarm}">Alarm</b></span>`;
const LOGOS = {
  // a: her pink and ink letters in a white outline.
  white: `<div class="logo outlined white">${word(PINK, INK, PINK)}</div>`,
  // b: white letters in her pink outline (candy).
  pink: `<div class="logo outlined pink">${word('#FFFFFF', '#FFFFFF', '#FFFFFF', BLUSH)}</div>`,
};

const css = `
  @font-face { font-family: Rounded; src: url('${font}'); }
  * { margin: 0; box-sizing: border-box; }
  body { background: transparent; font-family: Rounded, sans-serif; }
  .icon { position: relative; width: 512px; height: 512px; overflow: hidden; background: ${BLUSH}; }
  .sun { position: absolute; left: 280px; top: -24px; width: 240px; height: 240px; border-radius: 50%; background: ${GOLD}; }
  .rin { position: absolute; left: 21px; top: -22px; width: 470px; }
  .slot { position: absolute; left: 0; right: 0; bottom: 22px; display: flex; justify-content: center; }
  .alone { display: inline-flex; padding: 24px; }
  .sticker { display: flex; align-items: center; gap: 8px; background: #fff; border-radius: 48px; padding: 18px 30px 12px 22px;
    box-shadow: 0 6px 0 #F1D2C2; font-size: 58px; color: ${INK}; letter-spacing: -1px; }
  .sticker b { font-weight: normal; }
  .glyph { display: inline-block; }
  .outlined { font-size: 62px; letter-spacing: -1px; line-height: 1; padding: 30px 10px 6px; }
  .slot:has(.outlined) { bottom: 44px; }
  .outlined b { font-weight: normal; }
  .outlined .ii { width: 20px; } .outlined .ii .stem { left: 3px; width: 13px; height: 0.52em; } .outlined .ii .dot { left: -6px; bottom: 36px; }
  .outlined.white { filter: drop-shadow(5px 0 0 #fff) drop-shadow(-5px 0 0 #fff) drop-shadow(0 5px 0 #fff) drop-shadow(0 -5px 0 #fff)
    drop-shadow(2px 2px 0 #fff) drop-shadow(-2px -2px 0 #fff) drop-shadow(2px -2px 0 #fff) drop-shadow(-2px 2px 0 #fff)
    drop-shadow(0 5px 0 rgba(158, 35, 80, 0.45)); }
  .outlined.pink { filter: drop-shadow(5px 0 0 ${PINK}) drop-shadow(-5px 0 0 ${PINK}) drop-shadow(0 5px 0 ${PINK}) drop-shadow(0 -5px 0 ${PINK})
    drop-shadow(0 6px 0 #9E2350); }
  .ii { position: relative; display: inline-block; width: 18px; height: 0.72em; margin: 0 2px; }
  .ii .stem { position: absolute; left: 4px; bottom: 0; width: 11px; height: 0.52em; border-radius: 6px; background: ${PINK}; }
  .ii .dot { position: absolute; left: -3px; bottom: 33px; line-height: 0; font-size: 0; }
  .aa { position: relative; display: inline-block; width: 40px; height: 0.6em; margin: 0 1px; vertical-align: baseline; }
  .aa .glyph { position: absolute; left: 0; bottom: -2px; }
  .aa .astem { position: absolute; right: 2px; bottom: 0; width: 9px; height: 0.56em; border-radius: 5px; background: ${INK}; }
  .AA { display: inline-block; vertical-align: -2px; margin: 0 1px 0 3px; }
  .candy { position: relative; transform: rotate(-6deg); }
  .candy .word { font-size: 84px; color: #fff; letter-spacing: -2px; -webkit-text-stroke: 14px ${PINK}; paint-order: stroke fill;
    text-shadow: 0 8px 0 #9E2350; }
  .candy .word i { font-style: normal; color: #FFF4EA; }
  .candy .spark { position: absolute; color: ${GOLD}; font-size: 40px; -webkit-text-stroke: 4px ${PINK}; paint-order: stroke fill; }
  .candy .s1 { left: -30px; top: -10px; } .candy .s2 { right: -26px; bottom: 6px; font-size: 30px; }
  .ribbon { position: relative; padding: 0 40px; }
  .ribbon .band { position: relative; background: ${PINK}; color: #FFF4EA; font-size: 60px; letter-spacing: -1px; padding: 6px 28px 10px;
    border-radius: 14px; box-shadow: 0 6px 0 #9E2350; z-index: 1; }
  .ribbon .tail { position: absolute; top: 22px; width: 70px; height: 72px; background: #9E2350; }
  .ribbon .tail.l { left: 0; clip-path: polygon(0 0, 100% 0, 100% 100%, 0 100%, 26% 50%); }
  .ribbon .tail.r { right: 0; clip-path: polygon(0 0, 100% 0, 74% 50%, 100% 100%, 0 100%); }
  .ribbon .i { position: relative; } .ribbon .i em { position: absolute; left: 50%; top: -2px; transform: translateX(-50%); }
`;

const page = (body) => `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style></head><body>${body}</body></html>`;

const browser = await puppeteer.launch({ executablePath, headless: true, args: ['--allow-file-access-from-files'] });
const tab = await browser.newPage();
// Twice the pixels, so the logo alone is sharp at the feature graphic's size; icons are scaled back to 512.
await tab.setViewport({ width: 900, height: 600, deviceScaleFactor: 2 });
const shots = {};
for (const [name, logo] of Object.entries(LOGOS)) {
  const html = path.join(out, `logo-${name}.html`);
  fs.writeFileSync(html, page(`<div class="icon"><div class="sun"></div><img class="rin" src="${rin}"><div class="slot">${logo}</div></div>
    <div class="alone" id="alone">${logo}</div>`));
  await tab.goto(pathToFileURL(html).href);
  await tab.evaluate(() => document.fonts.ready);
  shots[name] = await sharp(await (await tab.$('.icon')).screenshot({ omitBackground: true })).resize(512, 512).png().toBuffer();
  await (await tab.$('#alone')).screenshot({ path: path.join(out, `logo-${name}.png`), omitBackground: true });
  fs.writeFileSync(path.join(out, `icon-${name}.png`), shots[name]);
  fs.rmSync(html);
}
// The chosen one (the user, 2026-10-02: white outline) again with no ground, for the launcher's adaptive icon: its
// background layer is the blush, so only Rin, the sun and the logo go in the foreground.
await tab.evaluate(() => {
  document.body.innerHTML = '';
});
fs.writeFileSync(path.join(out, 'fg.html'), page(`<div class="icon" style="background:transparent"><div class="sun"></div><img class="rin" src="${rin}"><div class="slot">${LOGOS.white}</div></div>`));
await tab.goto(pathToFileURL(path.join(out, 'fg.html')).href);
await tab.evaluate(() => document.fonts.ready);
await sharp(await (await tab.$('.icon')).screenshot({ omitBackground: true })).resize(512, 512).png().toFile(path.join(out, 'foreground-512.png'));
fs.rmSync(path.join(out, 'fg.html'));
await browser.close();

// The sheet, as in icon-options.mjs: 300 px rounded (store), 96 px circles (launcher) on light and dark.
const INKC = INK;
const W = 1200;
const H = 560;
const parts = [];
const circle = (s) => Buffer.from(`<svg width="${s}" height="${s}"><circle cx="${s / 2}" cy="${s / 2}" r="${s / 2}"/></svg>`);
const rounded = (s) => Buffer.from(`<svg width="${s}" height="${s}"><rect width="${s}" height="${s}" rx="${s * 0.2}"/></svg>`);
let x = 60;
let n = 1;
for (const shot of Object.values(shots)) {
  const big = await sharp(shot).resize(300, 300).composite([{ input: rounded(300), blend: 'dest-in' }]).png().toBuffer();
  const small = await sharp(shot).resize(96, 96).composite([{ input: circle(96), blend: 'dest-in' }]).png().toBuffer();
  parts.push({ input: big, left: x, top: 60 }, { input: small, left: x + 30, top: 400 }, { input: small, left: x + 174, top: 400 });
  parts.push({
    input: Buffer.from(`<svg width="60" height="50"><text x="0" y="40" font-family="Arial" font-weight="bold" font-size="40" fill="${INKC}">${n++}</text></svg>`),
    left: x,
    top: 4,
  });
  x += 380;
}
const ground = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}"><rect width="${W}" height="${H}" fill="#FFFFFF"/>
  ${[60, 440, 820].map((sx) => `<rect x="${sx}" y="380" width="144" height="136" fill="#E8E3F0"/><rect x="${sx + 144}" y="380" width="156" height="136" fill="#1E2233"/>`).join('')}</svg>`);
await sharp(ground).composite(parts).png().toFile(path.join(out, 'logo-options.png'));
console.log('wrote', path.join(out, 'logo-options.png'));
