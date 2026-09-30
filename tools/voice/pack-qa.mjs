// Automatic checks on one voice clip (task 4.3), run on the raw take before anyone listens. They catch what the user
// heard in the v3/v4 blind test (2026-09-30, crackle on S4 L06) and what a TTS model does wrong without a sound
// check: clicks, clipping, a line cut short or dragged out, and long gaps. Listening QA stays the final bar.

/** Adjacent samples further apart than this are a click: speech has almost no energy that high up. On the S4 v3
 * clips it fired on L02, L06 (the line the user flagged) and L10, and on none of the v4 ones. */
export const JUMP = 0.5;
/** Samples at or above this are clipped. */
export const CLIP = 0.99;
/** Words per second outside this range: a cut-off take or one with a stall (tags like [yawns] add time). */
export const PACE = [1.0, 4.5];
/** The last 40 ms louder than this, in dB under the take's peak, is a line cut off mid-sound. 69 of the first 191
 * chosen takes ended at -35 to -17 dB, where a clean end is ~-45 (median); the user heard them as cut off
 * (2026-09-30). */
export const CUT_DB = -35;
/** Flags a retake is made for automatically. The others (crackle, pace, gap) did not match the user's ear in round 1
 * (crackle caught 1 of 23), so they only inform the listening page. */
export const RETAKE = ['cut', 'clipped', 'empty'];
/** A gap of quiet longer than this mid-line, in seconds. */
export const GAP_S = 1.0;
/** Quiet, in dBFS, for trimming and for gaps. */
export const QUIET_DB = -45;
const FRAME_S = 0.02;

const db = (x) => 20 * Math.log10(Math.max(x, 1e-9));

/** Frame loudness (RMS, dBFS) in 20 ms steps. */
export function frames(samples, rate) {
  const n = Math.max(1, Math.round(rate * FRAME_S));
  const out = [];
  for (let i = 0; i + n <= samples.length; i += n) {
    let s = 0;
    for (let k = i; k < i + n; k++) s += samples[k] * samples[k];
    out.push(db(Math.sqrt(s / n)));
  }
  return out;
}

/** Seconds of sound from the first to the last frame above QUIET_DB. */
export function voiced(samples, rate) {
  const f = frames(samples, rate);
  const first = f.findIndex((x) => x > QUIET_DB);
  if (first < 0) return { start: 0, end: 0, dur: 0, gap: 0 };
  let last = f.length - 1;
  while (f[last] <= QUIET_DB) last--;
  let gap = 0;
  let run = 0;
  for (let i = first; i <= last; i++) {
    run = f[i] <= QUIET_DB ? run + 1 : 0;
    gap = Math.max(gap, run);
  }
  return { start: first * FRAME_S, end: (last + 1) * FRAME_S, dur: (last + 1 - first) * FRAME_S, gap: gap * FRAME_S };
}

/** Words the listener hears, for pace. */
export const wordCount = (text) => text.replace(/\[[^\]]*\]/g, ' ').split(/\s+/).filter((w) => /[a-z0-9]/i.test(w)).length;

/** RMS of the last 40 ms in dB under the peak. */
export function endDb(samples, rate) {
  let peak = 0;
  for (const x of samples) peak = Math.max(peak, Math.abs(x));
  const n = Math.min(samples.length, Math.max(1, Math.round(0.04 * rate)));
  let e = 0;
  for (let i = samples.length - n; i < samples.length; i++) e += samples[i] * samples[i];
  return db(Math.sqrt(e / n)) - db(peak);
}

/**
 * Flags for one take: `crackle` (clicks), `clipped`, `pace` (too fast or slow for its words), `gap` (a long stall),
 * `empty`, `cut` (stops mid-sound). An empty list means the take goes to listening as is.
 */
export function check(samples, rate, text) {
  let jumps = 0;
  let clipped = 0;
  let peak = 0;
  for (let i = 0; i < samples.length; i++) {
    const a = Math.abs(samples[i]);
    if (a > peak) peak = a;
    if (a >= CLIP) clipped++;
    if (i && Math.abs(samples[i] - samples[i - 1]) > JUMP) jumps++;
  }
  const v = voiced(samples, rate);
  const words = wordCount(text);
  const wps = v.dur ? words / v.dur : 0;
  const flags = [];
  if (!v.dur) flags.push('empty');
  if (jumps) flags.push('crackle');
  if (clipped) flags.push('clipped');
  if (v.dur && (wps < PACE[0] || wps > PACE[1])) flags.push('pace');
  if (v.gap > GAP_S) flags.push('gap');
  const end = endDb(samples, rate);
  if (v.dur && end > CUT_DB) flags.push('cut');
  return { flags, end: +end.toFixed(1), jumps, clipped, peakDb: +db(peak).toFixed(2), dur: +v.dur.toFixed(2), gap: +v.gap.toFixed(2), wps: +wps.toFixed(2) };
}

/** Of several takes' checks, the index of the best: fewest retake flags, then fewest flags, then the earliest. */
export function best(checks) {
  let at = 0;
  for (let i = 1; i < checks.length; i++) {
    const a = checks[i];
    const b = checks[at];
    const ra = a.flags.filter((f) => RETAKE.includes(f)).length;
    const rb = b.flags.filter((f) => RETAKE.includes(f)).length;
    if (ra < rb || (ra === rb && a.flags.length < b.flags.length)) at = i;
  }
  return at;
}
