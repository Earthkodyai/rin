import { test } from 'node:test';
import assert from 'node:assert/strict';
import { LPC_RATE, classify, formants, mouthTrack, resample } from './mouth.mjs';
import { readWav } from './audio.mjs';
import centroids from './centroids.json' with { type: 'json' };

/** A vowel-like sound: a pulse train at f0 through two-pole resonators at the given formants. */
function vowel(rate, f0, resonances, seconds) {
  let x = new Float32Array(Math.round(rate * seconds));
  for (let i = 0; i < x.length; i += Math.round(rate / f0)) x[i] = 1;
  for (const [f, bw] of resonances) {
    const r = Math.exp((-Math.PI * bw) / rate);
    const a1 = -2 * r * Math.cos((2 * Math.PI * f) / rate);
    const y = new Float32Array(x.length);
    for (let i = 0; i < x.length; i++) y[i] = x[i] - a1 * (y[i - 1] ?? 0) - r * r * (y[i - 2] ?? 0);
    x = y;
  }
  const peak = x.reduce((m, v) => Math.max(m, Math.abs(v)), 0);
  return x.map((v) => (0.5 * v) / peak);
}

test('resampling keeps a tone at its frequency', () => {
  const tone = Float32Array.from({ length: 24000 }, (_, i) => Math.sin((2 * Math.PI * 440 * i) / 24000));
  const out = resample(tone, 24000, LPC_RATE);
  let crossings = 0;
  for (let i = 1; i < out.length; i++) if (out[i - 1] < 0 && out[i] >= 0) crossings++;
  assert.ok(Math.abs(crossings - 440) <= 2, `${crossings} cycles in 1 s`);
});

// At f0 150 Hz. With f0 near F1 (a 220 Hz voice saying /i/, F1 ~300) LPC reads F1 ~25% high: harmonics are too sparse
// to outline the resonance. That is one reason vowel shapes failed across voices (eval.mjs).
test('LPC finds F1 within 15% and F2 within 10% on synthetic vowels', () => {
  for (const [f1, f2] of [[800, 1200], [300, 2700], [450, 900], [700, 2000]]) {
    const x = vowel(LPC_RATE, 150, [[f1, 80], [f2, 100], [2900, 150]], 0.04);
    const found = formants(x);
    assert.ok(found, `no formants for ${f1}/${f2}`);
    assert.ok(Math.abs(found[0] / f1 - 1) < 0.15, `F1 ${found[0]} vs ${f1}`);
    assert.ok(Math.abs(found[1] / f2 - 1) < 0.1, `F2 ${found[1]} vs ${f2}`);
  }
});

test('each frozen centroid classifies as its own shape', () => {
  for (const [shape, point] of Object.entries(centroids.shapes)) assert.equal(classify(point), shape);
});

test('a track has one frame per 1/fps, closed in silence and open only where the voice is', () => {
  const rate = 24000;
  const samples = new Float32Array(rate); // 1 s: silence, 0.4 s of vowel from 0.3 s, silence
  samples.set(vowel(rate, 220, [[800, 80], [1200, 100]], 0.4), Math.round(0.3 * rate));
  const track = mouthTrack(samples, rate);
  assert.deepEqual(Object.keys(track), ['v', 'fps', 'f']);
  assert.equal(track.v, 1);
  assert.equal(track.f.length / 2, 30);
  assert.match(track.f, /^(?:[aiueo-][0-9])*$/);
  const frames = track.f.match(/../g);
  // Smoothing starts opening one frame (33 ms) early, as real mouths do before a vowel.
  assert.ok(frames.slice(0, 7).every((f) => f === '-0'), 'closed before the voice');
  assert.ok(frames.slice(12, 19).every((f) => f[0] === 'a' && Number(f[1]) >= 7), 'open during it');
  assert.ok(frames.slice(24).every((f) => f === '-0'), 'closed after it');
});

test('without calibration every open frame uses shape a (held-out 37%, plan 05a)', () => {
  const rate = 24000;
  const samples = vowel(rate, 220, [[300, 60], [2700, 120]], 0.5); // an /i/ that `shapes: true` would call i
  assert.ok(!/[iueo]/.test(mouthTrack(samples, rate).f));
  assert.match(mouthTrack(samples, rate, { shapes: true }).f, /i[1-9]/);
});

test('a silent clip is a closed mouth', () => {
  assert.ok(mouthTrack(new Float32Array(8000), 8000).f.match(/../g).every((f) => f === '-0'));
});

test('WAV: 16-bit PCM stereo reads as mono floats', () => {
  const frames = [[16384, -16384], [32767, 32767]];
  const data = Buffer.alloc(frames.length * 4);
  frames.forEach(([l, r], i) => {
    data.writeInt16LE(l, i * 4);
    data.writeInt16LE(r, i * 4 + 2);
  });
  const fmt = Buffer.alloc(16);
  fmt.writeUInt16LE(1, 0);
  fmt.writeUInt16LE(2, 2);
  fmt.writeUInt32LE(8000, 4);
  fmt.writeUInt32LE(8000 * 4, 8);
  fmt.writeUInt16LE(4, 12);
  fmt.writeUInt16LE(16, 14);
  const chunk = (id, body) => Buffer.concat([Buffer.from(id), Buffer.from(Uint32Array.of(body.length).buffer), body]);
  const wav = Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WAVE'), chunk('fmt ', fmt), chunk('data', data)]);
  const { samples, rate } = readWav(wav);
  assert.equal(rate, 8000);
  assert.equal(samples.length, 2);
  assert.ok(Math.abs(samples[0]) < 1e-6);
  assert.ok(Math.abs(samples[1] - 32767 / 32768) < 1e-6);
});
