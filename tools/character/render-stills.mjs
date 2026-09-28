#!/usr/bin/env node
// Renders the still images the app shows while Rin's 3D page loads, or instead of it when it fails (task 2.5): one
// transparent WebP per mood, from the model the build ships, through the built page itself (its `still` mode), so a
// still matches the live strip's framing, lighting and faces exactly.
//
// usage: node render-stills.mjs <page-dir> <model.vrm> <out-dir> [--height 720] [--browser <path>] [--software]
//   page-dir  the built web/character page (the folder holding index.html)
//   --height  pixels; the phone's strip is 220 dp, so 720 covers 3.25x screens like the 14T's
//   --browser a Chrome or Edge executable (default: $RIN_BROWSER, then the usual install paths)
//   --software render with SwiftShader (CPU) even when there is a GPU
//
// The browser is the one already installed (puppeteer-core downloads nothing), running headless. It uses the GPU when
// there is one and falls back to SwiftShader. On this PC (Iris Xe) 7 stills take ~5 s on the GPU and ~11 s with
// SwiftShader, and the two differ by under 1/255 per channel on average (task 2.5).

import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import sharp from 'sharp';

/** The live strip is wider than 1:1 (main.ts frame(): the wide framing), so render wide, then crop the empty sides. */
const ASPECT = 1.6;
const MARGIN = 8;

const TYPES = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.wasm': 'application/wasm',
  '.vrm': 'application/octet-stream',
  '.vrma': 'application/octet-stream',
};

const BROWSERS = {
  win32: [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  ],
  darwin: [
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
  ],
  linux: ['/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/microsoft-edge'],
};

export function findBrowser(explicit = process.env.RIN_BROWSER, platform = process.platform, exists = fs.existsSync) {
  if (explicit) {
    if (!exists(explicit)) throw new Error(`browser not found: ${explicit}`);
    return explicit;
  }
  const found = (BROWSERS[platform] ?? []).find((p) => exists(p));
  if (!found) throw new Error('no Chrome or Edge found; set RIN_BROWSER or pass --browser <path>');
  return found;
}

/**
 * The crop that keeps her centred: as wide as the far side of the drawn pixels from the middle, both ways, so the app
 * can centre the image and scale it to the strip's height, and she lands where the live page draws her.
 * [left, right) are the drawn columns.
 */
export function symmetricCrop(width, left, right, margin = MARGIN) {
  const mid = width / 2;
  const half = Math.min(Math.ceil(Math.max(mid - left, right - mid)) + margin, Math.floor(mid));
  return { left: Math.round(mid - half), width: 2 * half };
}

function serve(pageDir, modelPath) {
  const root = path.resolve(pageDir);
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://x');
    const file = url.pathname === '/model/still.vrm' ? modelPath : path.join(root, decodeURIComponent(url.pathname));
    if (!file.startsWith(root) && file !== modelPath) return res.writeHead(403).end();
    fs.readFile(file, (err, data) => {
      if (err) return res.writeHead(404).end();
      res.writeHead(200, { 'content-type': TYPES[path.extname(file)] ?? 'application/octet-stream' }).end(data);
    });
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

/**
 * Opens the built page in headless Chrome or Edge with `model`, in still mode, at width x height, and waits until it
 * has loaded (window.rinStill and friends, main.ts exposeStills). Close it with `close()`.
 */
export async function openStillPage({ pageDir, model, width, height, browser, software = false }) {
  const server = await serve(pageDir, path.resolve(model));
  const chrome = await puppeteer.launch({
    executablePath: findBrowser(browser),
    headless: true,
    args: [...(software ? ['--use-angle=swiftshader'] : []), '--enable-unsafe-swiftshader', '--no-first-run', '--hide-scrollbars'],
  });
  const close = async () => {
    await chrome.close();
    server.close();
  };
  try {
    const page = await chrome.newPage();
    let pageError = null;
    page.on('console', (m) => {
      if (m.text().includes('"type":"error"')) pageError = m.text();
    });
    page.on('pageerror', (e) => (pageError = String(e)));
    await page.setViewport({ width, height, deviceScaleFactor: 1 });
    const { port } = server.address();
    await page.goto(`http://127.0.0.1:${port}/index.html?model=model/still.vrm&still&pr=1`);
    const started = Date.now();
    while ((await page.title()) !== 'still-ready') {
      if (pageError) throw new Error(`page error: ${pageError}`);
      if (Date.now() - started > 90_000) throw new Error('the page did not load the model within 90 s');
      await new Promise((r) => setTimeout(r, 200));
    }
    return { page, close, loadedS: (Date.now() - started) / 1000 };
  } catch (e) {
    await close();
    throw e;
  }
}

/** A PNG data URL from the page, as bytes. */
export const pngOf = (url) => Buffer.from(url.slice(url.indexOf(',') + 1), 'base64');

export async function renderStills({ pageDir, model, outDir, height = 720, browser, software = false }) {
  const width = Math.round(height * ASPECT);
  const { page, close, loadedS } = await openStillPage({ pageDir, model, width, height, browser, software });
  try {
    const moods = await page.evaluate(() => window.rinMoods);
    fs.mkdirSync(outDir, { recursive: true });
    const written = [];
    for (const mood of moods) {
      const png = pngOf(await page.evaluate((m) => window.rinStill(m), mood));
      const { info } = await sharp(png).trim({ threshold: 0 }).toBuffer({ resolveWithObject: true });
      const drawnLeft = -(info.trimOffsetLeft ?? 0);
      const crop = symmetricCrop(width, drawnLeft, drawnLeft + info.width);
      const file = path.join(outDir, `${mood}.webp`);
      // Lossy colour with lossless-quality alpha: about a tenth of the PNG, and the edges stay clean.
      await sharp(png)
        .extract({ left: crop.left, top: 0, width: crop.width, height })
        .webp({ quality: 90, alphaQuality: 100, effort: 4 }) // effort 6 is 20x slower for 1% smaller files
        .toFile(file);
      written.push({ mood, file: path.basename(file), width: crop.width, height, bytes: fs.statSync(file).size });
    }
    return { written, loadedS };
  } finally {
    await close();
  }
}

async function cli(argv) {
  const args = { height: 720, browser: undefined, software: false };
  const files = [];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--height') args.height = Number(argv[++i]);
    else if (a === '--browser') args.browser = argv[++i];
    else if (a === '--software') args.software = true;
    else files.push(a);
  }
  if (files.length !== 3 || !(args.height > 0)) {
    console.error('usage: node render-stills.mjs <page-dir> <model.vrm> <out-dir> [--height 720] [--browser <path>] [--software]');
    process.exit(2);
  }
  const [pageDir, model, outDir] = files;
  const started = Date.now();
  const { written, loadedS } = await renderStills({ pageDir, model, outDir, ...args });
  const kb = Math.round(written.reduce((n, w) => n + w.bytes, 0) / 1024);
  const total = (Date.now() - started) / 1000;
  console.log(`${written.length} stills, ${kb} KB, ${total.toFixed(1)} s (model loaded in ${loadedS.toFixed(1)} s) -> ${outDir}`);
}

if (process.argv[1]?.endsWith('render-stills.mjs')) {
  cli(process.argv.slice(2)).catch((e) => {
    console.error(e.stack ?? e);
    process.exit(1);
  });
}
