#!/usr/bin/env node
// Measures Rin's hands at the cup table for every table size (G.3): 3 cups (Easy, Normal), 4 (Hard) and 5
// (Nightmare). For each size it plays one shuffle through every pair she may swap (cups.ts pairsFor) and a lift of
// every cup, and asks the page's CupScene.probe at each moment for how deep any finger sinks into a cup (`depth`,
// cm), how high the palm floats over the cup under it (`gap`, cm) and how far the wrist ends from where it was sent
// (`slip`, cm: out of reach, or pushed off by clearing her body). Then a picture of each table, mid-shuffle, for a look.
//
// usage: node probe-cups.mjs <page-dir> <model.vrm> [<out-dir>] [--browser <path>]
//   page-dir  the built web/character page (app/build/generated/assets/buildCharacterWeb/character)
//   out-dir   where cups-<n>.png go (default: no pictures)
//
// Prints one JSON line per table size. The 3-cup line is the bar: 4 and 5 cups should come out no worse.

import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const args = process.argv.slice(2);
const flag = (name) => {
  const i = args.indexOf(name);
  return i < 0 ? undefined : args.splice(i, 2)[1];
};
const browserArg = flag('--browser');
const [pageDir, model, outDir] = args;
if (!pageDir || !model) {
  console.error('usage: node probe-cups.mjs <page-dir> <model.vrm> [<out-dir>] [--browser <path>]');
  process.exit(2);
}

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.json': 'application/json' };

function findBrowser(given) {
  const candidates = [
    given,
    process.env.RIN_BROWSER,
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
  ];
  const found = candidates.find((c) => c && fs.existsSync(c));
  if (!found) throw new Error('no Chrome or Edge found: pass --browser');
  return found;
}

function serve(root, modelPath) {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://x');
    const file = url.pathname === '/model/probe.vrm' ? modelPath : path.join(root, decodeURIComponent(url.pathname));
    if (!file.startsWith(root) && file !== modelPath) return res.writeHead(403).end();
    fs.readFile(file, (err, data) => {
      if (err) return res.writeHead(404).end();
      res.writeHead(200, { 'content-type': TYPES[path.extname(file)] ?? 'application/octet-stream' }).end(data);
    });
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

const server = await serve(path.resolve(pageDir), path.resolve(model));
const chrome = await puppeteer.launch({
  executablePath: findBrowser(browserArg),
  headless: true,
  userDataDir: fs.mkdtempSync(path.join(process.env.TEMP ?? '/tmp', 'rin-probe-')),
  args: ['--enable-unsafe-swiftshader', '--no-first-run', '--hide-scrollbars'],
});
try {
  const page = await chrome.newPage();
  page.on('pageerror', (e) => console.error('page error:', String(e)));
  // A phone-shaped view, the ring screen's open part (UX.4 insets) around her.
  await page.setViewport({ width: 412, height: 915, deviceScaleFactor: 1 });
  const { port } = server.address();
  await page.goto(`http://127.0.0.1:${port}/index.html?model=model/probe.vrm&top=0.15&bottom=0.4`);
  const started = Date.now();
  while (!(await page.evaluate(() => Boolean(window.rin?.vrm?.() && window.rin?.table?.())))) {
    if (Date.now() - started > 90_000) throw new Error('the page did not load the model within 90 s');
    await new Promise((r) => setTimeout(r, 250));
  }
  for (const cups of [3, 4, 5]) {
    const result = await page.evaluate((cups) => {
      const table = window.rin.table();
      const c = (cups - 1) / 2;
      const pairs = [];
      for (let p = 0; p < cups; p++) for (let q = p + 1; q < cups; q++) if (p <= c && q >= c && Math.abs((p + q) / 2 - c) <= 0.5) pairs.push([p, q]);
      const T = { leadMs: 300, swapMs: 450, gapMs: 150, exitMs: 250 };
      const worst = { depth: 0, gap: Infinity, slip: 0 };
      // Slip only while her hands are fully at work: coming to the table and leaving it, the wrist is between her
      // idle pose and the cup on purpose.
      const take = (r, working) => {
        for (const side of Object.values(r)) {
          worst.depth = Math.max(worst.depth, side.depth);
          if (side.gap !== null) worst.gap = Math.min(worst.gap, side.gap);
          if (working) worst.slip = Math.max(worst.slip, side.slip);
        }
      };
      // Each pair twice in a row is fine here (the game never does it): it covers the glide from every pair to the next.
      const swaps = pairs.flatMap((p) => pairs.map((q) => [p, q])).flat();
      const shuffle = { kind: 'shuffle', ball: 0, at: 0, cups, swaps, ...T };
      table.setAct(shuffle);
      const end = T.leadMs + swaps.length * (T.swapMs + T.gapMs);
      for (let t = 0; t <= end; t += 20) take(table.probe(t), t >= T.leadMs && t <= end - T.gapMs);
      for (let slot = 0; slot < cups; slot++) {
        const lift = { kind: 'lift', ball: slot, at: 0, cups, lift: [slot], hands: [slot], leadMs: 300, upMs: 250, holdMs: 900, downMs: 250, exitMs: 250 };
        table.setAct(lift);
        for (let t = 0; t <= 1950; t += 20) take(table.probe(t), t >= 300 && t <= 1700);
      }
      const l = table.layout;
      const cm = (m) => Math.round(m * 1000) / 10;
      return {
        cups,
        pairs: pairs.map((p) => p.join('')).join(' '),
        depthCm: worst.depth,
        minGapCm: worst.gap === Infinity ? null : worst.gap,
        slipCm: worst.slip,
        spanCm: cm((cups - 1) * l.spacing),
        cupRCm: cm(l.cupR),
        tableYCm: cm(l.tableY),
      };
    }, cups);
    console.log(JSON.stringify(result));
    if (outDir) {
      // Mid-way through the second swap of a short shuffle, as the user sees it.
      await page.evaluate((cups) => {
        const pairs = { 3: [[0, 2], [1, 2]], 4: [[0, 3], [1, 2]], 5: [[0, 4], [1, 3]] }[cups];
        window.rin.cups({ kind: 'shuffle', ball: 0, at: Date.now(), cups, swaps: pairs, leadMs: 300, swapMs: 450, gapMs: 150, exitMs: 250 });
        window.rin.cupsAt(300 + 600 + 225);
      }, cups);
      await new Promise((r) => setTimeout(r, 1500));
      fs.mkdirSync(outDir, { recursive: true });
      await page.screenshot({ path: path.join(outDir, `cups-${cups}.png`) });
      await page.evaluate(() => window.rin.cupsAt(null));
    }
  }
} finally {
  await chrome.close();
  server.close();
}
