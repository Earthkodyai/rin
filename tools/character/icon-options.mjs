#!/usr/bin/env node
// Draft app icons for the store listing (task 6.5): three options at Play's 512 px and at launcher size, on one
// contact sheet for the user to pick from. Lives here for sharp:
//   node icon-options.mjs <rin-still.webp> <out-dir>
// The still is Rin's head-and-shoulders render (build/generated/renderRinStills/character/stills/cheerful.webp).
import fs from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';

const [still, out] = process.argv.slice(2);
if (!still || !out) {
  console.error('usage: node icon-options.mjs <rin-still.webp> <out-dir>');
  process.exit(2);
}
fs.mkdirSync(out, { recursive: true });

const PINK = '#CC3169';
const CANDY = '#FF6F9C';
const CREAM = '#FFF4EA';
const BLUSH = '#FFE1E9';
const MINT = '#3DBE9A';
const INK = '#3A2A33';
const GOLD = '#FFD27A';

/** A twin-bell alarm clock at (cx, cy), radius r. */
function clock(cx, cy, r, { face = CREAM, rim = PINK, hands = INK, bells = PINK } = {}) {
  const bell = (dx) =>
    `<path d="M ${cx + dx - r * 0.38} ${cy - r * 0.78} a ${r * 0.4} ${r * 0.4} 0 0 1 ${r * 0.76} 0 z" fill="${bells}" transform="rotate(${dx < 0 ? -32 : 32} ${cx + dx} ${cy - r * 0.8})"/>`;
  return `
    ${bell(-r * 0.62)}${bell(r * 0.62)}
    <rect x="${cx - r * 0.06}" y="${cy - r * 1.22}" width="${r * 0.12}" height="${r * 0.3}" rx="${r * 0.05}" fill="${rim}"/>
    <line x1="${cx - r * 0.55}" y1="${cy + r * 0.8}" x2="${cx - r * 0.75}" y2="${cy + r * 1.08}" stroke="${rim}" stroke-width="${r * 0.14}" stroke-linecap="round"/>
    <line x1="${cx + r * 0.55}" y1="${cy + r * 0.8}" x2="${cx + r * 0.75}" y2="${cy + r * 1.08}" stroke="${rim}" stroke-width="${r * 0.14}" stroke-linecap="round"/>
    <circle cx="${cx}" cy="${cy}" r="${r}" fill="${rim}"/>
    <circle cx="${cx}" cy="${cy}" r="${r * 0.8}" fill="${face}"/>
    <line x1="${cx}" y1="${cy - r * 0.08}" x2="${cx - r * 0.36}" y2="${cy - r * 0.34}" stroke="${hands}" stroke-width="${r * 0.11}" stroke-linecap="round"/>
    <line x1="${cx}" y1="${cy - r * 0.08}" x2="${cx + r * 0.46}" y2="${cy - r * 0.4}" stroke="${hands}" stroke-width="${r * 0.11}" stroke-linecap="round"/>
    <circle cx="${cx}" cy="${cy - r * 0.08}" r="${r * 0.08}" fill="${hands}"/>`;
}

const svg = (body) => Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">${body}</svg>`);

// A: Rin herself, head and shoulders, on her panel's blush with the sun behind, and a small clock badge.
async function optionA() {
  const meta = await sharp(still).metadata();
  const side = Math.round(meta.width * 0.92);
  const left = Math.round((meta.width - side) / 2);
  const face = await sharp(still).extract({ left, top: 30, width: side, height: side }).resize(470, 470).toBuffer();
  const ground = svg(`
    <rect width="512" height="512" fill="${BLUSH}"/>
    <circle cx="400" cy="96" r="120" fill="${GOLD}"/>`);
  const badge = svg(`<circle cx="430" cy="430" r="66" fill="#FFFFFF"/>${clock(430, 436, 44)}`);
  return sharp(ground)
    .composite([
      { input: face, left: 21, top: 42 },
      { input: badge, left: 0, top: 0 },
    ])
    .png()
    .toBuffer();
}

// B: a pink alarm clock wearing Rin's ribbon, mint eyes on its face: simple and readable at launcher size.
function optionB() {
  return sharp(
    svg(`
    <rect width="512" height="512" fill="${CREAM}"/>
    ${clock(256, 282, 150, { face: '#FFFFFF' })}
    <path d="M256 118 l-74 -40 q-20 40 0 80 z M256 118 l74 -40 q20 40 0 80 z" fill="${CANDY}"/>
    <circle cx="256" cy="118" r="20" fill="${PINK}"/>
    <circle cx="206" cy="322" r="15" fill="${MINT}"/>
    <circle cx="306" cy="322" r="15" fill="${MINT}"/>
    <path d="M236 360 q20 16 40 0" stroke="${INK}" stroke-width="9" fill="none" stroke-linecap="round"/>`)
  )
    .png()
    .toBuffer();
}

// C: a chibi clock with Rin's long pink hair and bangs, on the night look's navy with stars.
function optionC() {
  return sharp(
    svg(`
    <rect width="512" height="512" fill="#121836"/>
    <circle cx="90" cy="90" r="5" fill="#FFFFFF" opacity="0.8"/><circle cx="430" cy="70" r="4" fill="#FFFFFF" opacity="0.7"/>
    <circle cx="460" cy="300" r="3" fill="#FFFFFF" opacity="0.6"/><circle cx="60" cy="380" r="4" fill="#FFFFFF" opacity="0.6"/>
    <path d="M110 250 q-10 190 40 220 q30 -60 20 -200 z M402 250 q10 190 -40 220 q-30 -60 -20 -200 z" fill="${CANDY}"/>
    ${clock(256, 270, 150, { face: '#FFFFFF', rim: CANDY, bells: CANDY })}
    <path d="M112 250 q20 -150 144 -150 q124 0 144 150 q-30 -40 -64 -36 q-14 -44 -40 -54 q-6 40 -40 50 q-34 -10 -40 -50 q-26 10 -40 54 q-34 -4 -64 36 z" fill="${CANDY}"/>
    <circle cx="204" cy="318" r="15" fill="${MINT}"/>
    <circle cx="308" cy="318" r="15" fill="${MINT}"/>
    <path d="M238 352 q18 14 36 0" stroke="${INK}" stroke-width="9" fill="none" stroke-linecap="round"/>`)
  )
    .png()
    .toBuffer();
}

const options = { A: await optionA(), B: await optionB(), C: await optionC() };
for (const [k, v] of Object.entries(options)) fs.writeFileSync(path.join(out, `icon-${k}.png`), v);

// The sheet: each option at 300 px (store size, rounded as Play shows it) and at 96 px in a launcher circle, on a
// light and a dark wallpaper, with its letter.
const W = 1200;
const H = 560;
const parts = [];
const circle = (s) => Buffer.from(`<svg width="${s}" height="${s}"><circle cx="${s / 2}" cy="${s / 2}" r="${s / 2}"/></svg>`);
const rounded = (s) => Buffer.from(`<svg width="${s}" height="${s}"><rect width="${s}" height="${s}" rx="${s * 0.2}"/></svg>`);
let x = 60;
for (const [k, v] of Object.entries(options)) {
  const big = await sharp(v).resize(300, 300).composite([{ input: rounded(300), blend: 'dest-in' }]).png().toBuffer();
  const small = await sharp(v).resize(96, 96).composite([{ input: circle(96), blend: 'dest-in' }]).png().toBuffer();
  parts.push({ input: big, left: x, top: 60 });
  parts.push({ input: small, left: x + 30, top: 400 });
  parts.push({ input: small, left: x + 174, top: 400 });
  parts.push({
    input: Buffer.from(`<svg width="60" height="50"><text x="0" y="40" font-family="Arial" font-weight="bold" font-size="40" fill="${INK}">${k}</text></svg>`),
    left: x,
    top: 4,
  });
  x += 380;
}
const sheetGround = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
  <rect width="${W}" height="${H}" fill="#FFFFFF"/>
  ${[60, 440, 820].map((sx) => `<rect x="${sx}" y="380" width="144" height="136" fill="#E8E3F0"/><rect x="${sx + 144}" y="380" width="156" height="136" fill="#1E2233"/>`).join('')}
</svg>`);
await sharp(sheetGround).composite(parts).png().toFile(path.join(out, 'icon-options.png'));
console.log('wrote', path.join(out, 'icon-options.png'));
