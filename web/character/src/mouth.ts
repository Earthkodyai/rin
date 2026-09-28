// Lip sync on the page (task 2.4). The app plays the voice natively (so it survives this page dying) and sends the
// line's mouth track with the wall-clock time at which the audio's first sample plays. The track is made from the
// audio at build time (tools/voice/mouth.mjs) or, when a clip has none, from its loudness alone (shape `a`).
//
// Track format v1: `fps` frames per second; `f` holds two characters per frame: the shape (a i u e o = VRM visemes
// aa ih ou ee oh, `-` = closed) and how open the mouth is, 0..9.

export const VISEMES = ['aa', 'ih', 'ou', 'ee', 'oh'] as const;
export type Viseme = (typeof VISEMES)[number];
export type MouthWeights = Record<Viseme, number>;

export interface MouthTrack {
  fps: number;
  f: string;
}

const SHAPES: Record<string, number> = { a: 0, i: 1, u: 2, e: 3, o: 4 };

/** A well-formed track: fps 1..120, an even-length string of shape and digit pairs. */
export function isMouthTrack(value: unknown): value is MouthTrack {
  const m = value as MouthTrack | null;
  return (
    typeof m === 'object' &&
    m !== null &&
    typeof m.fps === 'number' &&
    m.fps >= 1 &&
    m.fps <= 120 &&
    typeof m.f === 'string' &&
    /^(?:[aiueo-][0-9])*$/.test(m.f)
  );
}

/** A track's fully open frame (9) opens the mouth this far: VRoid's `aa` at 1 is a shout, not speech. */
export const MAX_OPEN = 0.75;

export const closedMouth = (): MouthWeights => ({ aa: 0, ih: 0, ou: 0, ee: 0, oh: 0 });

function frameWeights(track: MouthTrack, i: number, out: number[]): void {
  out.fill(0);
  if (i < 0 || i * 2 >= track.f.length) return;
  const shape = SHAPES[track.f[i * 2]];
  if (shape !== undefined) out[shape] = Number(track.f[i * 2 + 1]) / 9;
}

const a = [0, 0, 0, 0, 0];
const b = [0, 0, 0, 0, 0];

/** The viseme weights at `t` seconds into the line, interpolated between frames so shapes crossfade. */
export function mouthAt(track: MouthTrack, t: number, out: MouthWeights = closedMouth()): MouthWeights {
  const x = t * track.fps;
  const i = Math.floor(x);
  frameWeights(track, i, a);
  frameWeights(track, i + 1, b);
  const k = x - i;
  VISEMES.forEach((v, j) => (out[v] = a[j] + (b[j] - a[j]) * k));
  return out;
}

/** Track length in seconds. */
export const trackSeconds = (track: MouthTrack) => track.f.length / 2 / track.fps;

/** The line being spoken, if any; `at` is the epoch ms at which the audio started (Date.now() shares the clock). */
export class MouthPlayer {
  private line: { track: MouthTrack; at: number } | null = null;
  private readonly weights = closedMouth();

  speak(track: MouthTrack, at: number): void {
    this.line = { track, at };
  }

  hush(): void {
    this.line = null;
  }

  /** Viseme weights now, or null when she is not speaking. */
  at(nowMs: number): MouthWeights | null {
    if (!this.line) return null;
    const t = (nowMs - this.line.at) / 1000;
    if (t > trackSeconds(this.line.track)) {
      this.line = null;
      return null;
    }
    mouthAt(this.line.track, t, this.weights);
    for (const v of VISEMES) this.weights[v] *= MAX_OPEN;
    return this.weights;
  }
}
