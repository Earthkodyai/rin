// Mouth tracks from audio (task 2.4, plan 05a/05d): how open Rin's mouth is comes from loudness. It needs nothing
// but the audio, so the same code serves the offline voice pack at build time and, later, live replies from any TTS
// vendor.
//
// Mouth *shapes* (VRM visemes aa ih ou ee oh from LPC formants, `shapes: true`) are off by default: on held-out TTS
// voices they were right for 22/60 words (37%, bar 60%; eval.mjs, 2026-09-28), because one set of vowel centroids
// does not carry across voices. Task 4.3 fitted centroids.json on Rin's own voice (ElevenLabs Rin soft, dev 20 words)
// and a fresh held-out set got 18/20 (90%, eval/vowels-rin.json, 2026-09-30), so Rin's voice pack turns them on
// (pack.mjs SHAPES_ON). Other voices keep loudness only.
//
// Output format v1 (web/character/src/mouth.ts): { v: 1, fps, f } where f holds two characters per frame: the shape
// (a i u e o, or - for closed) and the opening 0..9.
import centroids from './centroids.json' with { type: 'json' };

export const FPS = 30;
export const SHAPES = ['a', 'i', 'u', 'e', 'o'];
/** LPC runs on audio resampled to this rate: formants F1/F2 of a female voice sit below ~3.5 kHz. */
export const LPC_RATE = 11025;
const LPC_ORDER = 12;
const WINDOW_S = 0.04;
/** Loudness range mapped onto the opening: RANGE_DB below the clip's loud level is closed. */
const RANGE_DB = 20;
/** Anything quieter than this is silence, however quiet the whole clip is. */
const FLOOR_DB = -60;
/** Periodicity (normalized autocorrelation) above which a frame counts as voiced. */
const VOICED = 0.4;
/** Resonances wider than this are not formants. TTS voices show F2 bandwidths of 400–650 Hz (dev set, 2026-09-28). */
const MAX_BANDWIDTH = 800;

/** Windowed-sinc resampler; good enough for analysis (not for playback). */
export function resample(x, from, to) {
  if (from === to) return Float32Array.from(x);
  const ratio = from / to;
  const cutoff = Math.min(1, 1 / ratio) * 0.9;
  const half = 16 * Math.ceil(ratio);
  const out = new Float32Array(Math.floor(x.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const center = i * ratio;
    const first = Math.ceil(center - half);
    let sum = 0;
    let norm = 0;
    for (let j = first; j <= center + half; j++) {
      if (j < 0 || j >= x.length) continue;
      const d = j - center;
      const w = 0.5 + 0.5 * Math.cos((Math.PI * d) / (half + 1)); // Hann
      const s = d === 0 ? cutoff : Math.sin(Math.PI * cutoff * d) / (Math.PI * d);
      sum += x[j] * s * w;
      norm += s * w;
    }
    out[i] = norm ? sum / norm : 0;
  }
  return out;
}

function frameAt(x, center, length) {
  const out = new Float32Array(length);
  const start = Math.round(center - length / 2);
  for (let i = 0; i < length; i++) {
    const j = start + i;
    out[i] = j >= 0 && j < x.length ? x[j] : 0;
  }
  return out;
}

const db = (rms) => 20 * Math.log10(rms + 1e-9);

/** Normalized autocorrelation peak for pitch 100..500 Hz: near 1 for a vowel, low for noise (s, f, sh). */
export function periodicity(frame, rate) {
  const minLag = Math.floor(rate / 500);
  const maxLag = Math.ceil(rate / 100);
  let energy = 0;
  for (const v of frame) energy += v * v;
  if (energy < 1e-10) return 0;
  let best = 0;
  for (let lag = minLag; lag <= maxLag; lag++) {
    let r = 0;
    let e1 = 0;
    let e2 = 0;
    for (let i = 0; i + lag < frame.length; i++) {
      r += frame[i] * frame[i + lag];
      e1 += frame[i] * frame[i];
      e2 += frame[i + lag] * frame[i + lag];
    }
    const n = r / Math.sqrt(e1 * e2 + 1e-12);
    if (n > best) best = n;
  }
  return best;
}

/** LPC coefficients a[0..p] (a[0] = 1) by the autocorrelation method and Levinson-Durbin. */
export function lpc(frame, order) {
  const r = new Float64Array(order + 1);
  for (let k = 0; k <= order; k++) for (let i = k; i < frame.length; i++) r[k] += frame[i] * frame[i - k];
  if (r[0] <= 0) return null;
  r[0] *= 1 + 1e-9; // white-noise correction keeps the recursion stable
  const a = new Float64Array(order + 1);
  a[0] = 1;
  let err = r[0];
  for (let i = 1; i <= order; i++) {
    let acc = r[i];
    for (let j = 1; j < i; j++) acc += a[j] * r[i - j];
    const k = -acc / err;
    const prev = a.slice();
    for (let j = 1; j < i; j++) a[j] = prev[j] + k * prev[i - j];
    a[i] = k;
    err *= 1 - k * k;
    if (err <= 0) return null;
  }
  return a;
}

/** Roots of z^p + a1 z^(p-1) + ... + ap (Durand-Kerner), as [re, im] pairs. */
export function roots(a) {
  const p = a.length - 1;
  let z = Array.from({ length: p }, (_, k) => {
    const angle = (2 * Math.PI * k) / p + 0.4;
    return [0.9 * Math.cos(angle), 0.9 * Math.sin(angle)];
  });
  const mul = ([a1, b1], [a2, b2]) => [a1 * a2 - b1 * b2, a1 * b2 + b1 * a2];
  const div = ([a1, b1], [a2, b2]) => {
    const d = a2 * a2 + b2 * b2 || 1e-300;
    return [(a1 * a2 + b1 * b2) / d, (b1 * a2 - a1 * b2) / d];
  };
  const evaluate = (x) => {
    let v = [1, 0];
    for (let i = 1; i <= p; i++) v = [...mul(v, x)].map((c, j) => c + (j === 0 ? a[i] : 0));
    return v;
  };
  for (let iter = 0; iter < 200; iter++) {
    let moved = 0;
    z = z.map((zi, i) => {
      let denom = [1, 0];
      z.forEach((zj, j) => {
        if (j !== i) denom = mul(denom, [zi[0] - zj[0], zi[1] - zj[1]]);
      });
      const step = div(evaluate(zi), denom);
      moved = Math.max(moved, Math.hypot(step[0], step[1]));
      return [zi[0] - step[0], zi[1] - step[1]];
    });
    if (moved < 1e-10) break;
  }
  return z;
}

/** The first two formants (Hz) of a frame at LPC_RATE, or null when fewer than two resonances are found. */
export function formants(frame) {
  const x = new Float32Array(frame.length);
  for (let i = frame.length - 1; i > 0; i--) x[i] = frame[i] - 0.97 * frame[i - 1]; // pre-emphasis
  for (let i = 0; i < x.length; i++) x[i] *= 0.54 - 0.46 * Math.cos((2 * Math.PI * i) / (x.length - 1)); // Hamming
  const a = lpc(x, LPC_ORDER);
  if (!a) return null;
  const found = [];
  for (const [re, im] of roots(a)) {
    if (im <= 0) continue;
    const freq = (Math.atan2(im, re) * LPC_RATE) / (2 * Math.PI);
    const bandwidth = (-Math.log(Math.hypot(re, im)) * LPC_RATE) / Math.PI;
    if (freq > 200 && freq < 4000 && bandwidth < MAX_BANDWIDTH) found.push(freq);
  }
  found.sort((p, q) => p - q);
  return found.length >= 2 ? [found[0], found[1]] : null;
}

/** The nearest vowel centroid to (F1, F2) on a log scale; centroids.json is fitted on the dev set (eval.mjs). */
export function classify([f1, f2], table = centroids.shapes) {
  let best = null;
  let bestD = Infinity;
  for (const [shape, [c1, c2]] of Object.entries(table)) {
    const d = (Math.log(f1 / c1) * centroids.f1Weight) ** 2 + Math.log(f2 / c2) ** 2;
    if (d < bestD) {
      bestD = d;
      best = shape;
    }
  }
  return best;
}

/**
 * Per-frame analysis: loudness in dB, periodicity, formants. Exposed for eval.mjs; `mouthTrack` turns it into a track.
 * @param {Float32Array} samples mono PCM, -1..1
 */
export function analyse(samples, rate, fps = FPS) {
  const x = resample(samples, rate, LPC_RATE);
  const length = Math.round(WINDOW_S * LPC_RATE);
  const count = Math.ceil((samples.length / rate) * fps);
  const frames = [];
  for (let k = 0; k < count; k++) {
    const frame = frameAt(x, ((k + 0.5) / fps) * LPC_RATE, length);
    let sum = 0;
    for (const v of frame) sum += v * v;
    const level = db(Math.sqrt(sum / length));
    const voicing = periodicity(frame, LPC_RATE);
    frames.push({ level, voicing, formants: voicing >= VOICED ? formants(frame) : null });
  }
  // The clip's loud level: a high percentile, so one click or peak does not set it.
  const sorted = frames.map((f) => f.level).sort((p, q) => p - q);
  const loud = sorted[Math.floor(sorted.length * 0.95)] ?? -100;
  for (const f of frames) {
    f.open = f.level < FLOOR_DB ? 0 : Math.min(Math.max((f.level - (loud - RANGE_DB)) / RANGE_DB, 0), 1);
  }
  return frames;
}

/** Most common value among neighbours, so a single odd frame does not flash a different mouth shape. */
function modeFilter(values, radius) {
  return values.map((_, i) => {
    const counts = new Map();
    for (let j = Math.max(0, i - radius); j <= Math.min(values.length - 1, i + radius); j++) {
      counts.set(values[j], (counts.get(values[j]) ?? 0) + 1 + (j === i ? 0.5 : 0));
    }
    return [...counts].sort((p, q) => q[1] - p[1])[0][0];
  });
}

/**
 * The mouth track for one clip. With `shapes` off (the default until Phase 4) every open frame uses `a`.
 * @param {{ fps?: number, shapes?: boolean }} options
 */
export function mouthTrack(samples, rate, { fps = FPS, shapes: withShapes = false } = {}) {
  const frames = analyse(samples, rate, fps);
  // Opening: a light 3-tap smoothing, then a curve that favours clear open/closed over a constant half-open mouth.
  const open = frames.map((f, i) => {
    const prev = frames[i - 1]?.open ?? f.open;
    const next = frames[i + 1]?.open ?? f.open;
    return (0.25 * prev + 0.5 * f.open + 0.25 * next) ** 0.8;
  });
  let last = 'a';
  const raw = frames.map((f) => {
    if (!withShapes) return 'a';
    if (f.formants) last = classify(f.formants);
    else if (f.voicing < VOICED && f.open > 0) return 'i'; // noise (s, sh, f, t): teeth together, lips a little apart
    return last; // a voiced frame LPC could not read keeps the previous shape
  });
  const shapes = modeFilter(raw, 1);
  let f = '';
  frames.forEach((frame, i) => {
    let level = Math.round(open[i] * 9);
    if (frame.voicing < VOICED) level = Math.min(level, 3);
    f += level === 0 ? '-0' : `${shapes[i]}${level}`;
  });
  return { v: 1, fps, f };
}
