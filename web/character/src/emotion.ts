// Rin's face and head pose as numbers, free of three.js so it can be unit tested (emotion.test.ts). The moods and
// their weights live in moods.json, which the app's MoodContractTest also reads, so Kotlin and the page cannot drift.
import table from './moods.json';

export type Mood = keyof typeof table;
export const MOODS = Object.keys(table) as Mood[];
export const isMood = (value: unknown): value is Mood => typeof value === 'string' && Object.hasOwn(table, value);

/**
 * The VRM 1.0 presets a mood may use (05a), plus `smile`, an eyes-open smile built from VRoid morphs (vroid.ts). A
 * model without one of them simply ignores it.
 */
export const EXPRESSIONS = ['happy', 'angry', 'sad', 'relaxed', 'surprised', 'smile'] as const;

/**
 * Everything a mood sets: expression weights, `lid` (resting eyelid, 0 open .. 1 closed), head `pitch`/`yaw`/`roll`
 * offsets in radians (pitch > 0 looks down), and `gaze`, a sideways shift of where she looks, in metres.
 */
export const CHANNELS = [...EXPRESSIONS, 'lid', 'pitch', 'yaw', 'roll', 'gaze'] as const;
export type Channel = (typeof CHANNELS)[number];
export type Channels = Record<Channel, number>;

const clamp01 = (x: number) => Math.min(Math.max(x, 0), 1);

/** A mood at an intensity (0..1) as channel values; intensity scales the whole look, face and pose alike. */
export function moodChannels(mood: Mood, intensity = 1): Channels {
  const { expressions, ...pose } = table[mood];
  const values: Partial<Channels> = { ...expressions, ...pose };
  const k = clamp01(intensity);
  const out = {} as Channels;
  for (const c of CHANNELS) out[c] = (values[c] ?? 0) * k;
  return out;
}

/** 05a: expressions blend over 150–300 ms. 200 ms leaves room for one 30 fps frame and the bridge inside 300 ms. */
export const BLEND_S = 0.2;

const smoothstep = (x: number) => x * x * (3 - 2 * x);

/**
 * Moves every channel from where it is now to a target over a fixed time. The fixed duration, rather than an
 * exponential chase, is what makes "the new mood is fully shown" a moment that can be measured.
 */
export class Blend {
  private from: Channels;
  private to: Channels;
  private current: Channels;
  private t = 0;
  private duration = 0;

  constructor(start: Channels) {
    this.from = { ...start };
    this.to = { ...start };
    this.current = { ...start };
  }

  /** Starts a new blend from the current values, so a change in the middle of a blend never jumps. */
  set(target: Channels, duration = BLEND_S): void {
    this.from = { ...this.current };
    this.to = { ...target };
    this.t = 0;
    this.duration = duration;
    if (duration <= 0) this.current = { ...target };
  }

  get blending(): boolean {
    return this.t < this.duration;
  }

  /** Advances time; returns true on the one step where the blend reaches its target. */
  step(dt: number): boolean {
    if (!this.blending) return false;
    this.t += dt;
    if (this.t > this.duration - 1e-6) {
      // Done (float drift must not cost a whole extra frame), and exactly on target.
      this.t = this.duration;
      this.current = { ...this.to };
      return true;
    }
    const k = smoothstep(this.t / this.duration);
    for (const c of CHANNELS) this.current[c] = this.from[c] + (this.to[c] - this.from[c]) * k;
    return false;
  }

  get values(): Readonly<Channels> {
    return this.current;
  }
}

/** The head-tap reaction: a burst of joy with a small nod, then back to the mood. */
export class TapReaction {
  static readonly RISE_S = 0.1;
  static readonly HOLD_S = 1.0;
  static readonly FADE_S = 0.4;
  static readonly COOLDOWN_S = 1.0;
  static readonly NOD_S = 0.6;
  static readonly NOD_RAD = 0.12;

  private age = Infinity;

  /** Starts the reaction unless the last one started under COOLDOWN_S ago; returns whether it started. */
  trigger(): boolean {
    if (this.age < TapReaction.COOLDOWN_S) return false;
    this.age = 0;
    return true;
  }

  step(dt: number): void {
    this.age += dt;
  }

  /** 0..1, how much the reaction replaces the mood's face. */
  get weight(): number {
    const { RISE_S, HOLD_S, FADE_S } = TapReaction;
    const a = this.age;
    if (a < RISE_S) return a / RISE_S;
    if (a < RISE_S + HOLD_S) return 1;
    if (a < RISE_S + HOLD_S + FADE_S) return 1 - (a - RISE_S - HOLD_S) / FADE_S;
    return 0;
  }

  /** Extra head pitch: two small nods. */
  get nod(): number {
    const { NOD_S, NOD_RAD } = TapReaction;
    if (this.age >= NOD_S) return 0;
    return NOD_RAD * Math.sin((this.age / NOD_S) * 2 * Math.PI) ** 2;
  }

  /** The mood's channels with the reaction mixed in: joy up, every other expression down, pose untouched. */
  apply(mood: Readonly<Channels>): Channels {
    const w = this.weight;
    const out = { ...mood };
    if (w <= 0) return out;
    for (const e of EXPRESSIONS) out[e] = e === 'happy' ? mood.happy + (1 - mood.happy) * w : mood[e] * (1 - w);
    out.lid = mood.lid * (1 - w);
    return out;
  }
}

/** A tap, not a drag or a long press: released within 350 ms and within 12 CSS px of where it went down. */
export function isTap(ms: number, dx: number, dy: number): boolean {
  return ms <= 350 && dx * dx + dy * dy <= 12 * 12;
}
