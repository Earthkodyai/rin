// The page's half of the bridge to Kotlin (character/CharacterMessage.kt), protocol version 1. The native side
// registers `RinBridge` with WebViewCompat.addWebMessageListener, scoped to the app's asset origin, so only this page
// can post. Messages are JSON strings; both sides ignore types they do not know, so either side can add types first.
// Task 2.2 added `emotion` both ways and `tap` to the app; task 2.4 added `gesture` both ways, `speak` and `hush`;
// task 2.5 added `stats` both ways and `fps` (debug builds use them to measure). Still version 1, since every
// addition is ignorable.
import { isMood, type Mood } from './emotion';
import { isGesture, type Gesture } from './gesture';
import { isMouthTrack, type MouthTrack } from './mouth';

export const PROTOCOL = 1;

export interface LoadTimings {
  /** Navigation start to first rendered frame. */
  pageToFirstFrame: number;
  fetch: number;
  parse: number;
  compile: number;
  /** From the native timestamp taken before the WebView was created to the first frame, when known. */
  nativeToFirstFrame: number | null;
}

export interface ModelInfo {
  bytes: number;
  meshes: number;
  triangles: number;
  textures: number;
  expressions: string[];
}

export type ToNative =
  | { v: typeof PROTOCOL; type: 'ready'; ms: LoadTimings; model: ModelInfo; pixelRatio: number; fpsCap: number }
  | { v: typeof PROTOCOL; type: 'error'; message: string }
  /** A mood is fully shown. `toPage`: the app's send to the page receiving it; `total`: to the blend's last frame. */
  | { v: typeof PROTOCOL; type: 'emotion'; mood: Mood; ms: { toPage: number; total: number } }
  /** Only the head reacts to taps (the rest of her ignores them), so `part` is always 'head' for now. */
  | { v: typeof PROTOCOL; type: 'tap'; part: 'head' }
  /** Whether a gesture started; false when its file failed to load (it has been reported as an error log). */
  | { v: typeof PROTOCOL; type: 'gesture'; name: Gesture; ok: boolean }
  | ({ v: typeof PROTOCOL; type: 'stats' } & FrameStats);

/** Frame pacing over a window of rendered frames (paused time is left out). */
export interface FrameStats {
  frames: number;
  seconds: number;
  avgFps: number;
  /** 1000 / the 99th-percentile frame interval: the rate of the slowest 1% of frames. */
  p1LowFps: number;
  /** Intervals over 50 ms: at a 30 fps cap (33 ms) that is a visibly dropped frame. */
  over50: number;
  maxMs: number;
  fpsCap: number;
  /** The first 20 intervals over 50 ms: `at` is ms into the window (where the frame ended), to match with events. */
  hitches: { at: number; ms: number }[];
}

/** Summarises intervals (ms) between rendered frames; null when there are none. */
export function frameStats(intervals: readonly number[], fpsCap: number): FrameStats | null {
  if (intervals.length === 0) return null;
  const total = intervals.reduce((a, b) => a + b, 0);
  const sorted = [...intervals].sort((a, b) => a - b);
  const p99 = sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.99) - 1)];
  const r1 = (x: number) => Math.round(x * 10) / 10;
  const hitches: { at: number; ms: number }[] = [];
  let at = 0;
  for (const ms of intervals) {
    at += ms;
    if (ms > 50 && hitches.length < 20) hitches.push({ at: Math.round(at), ms: r1(ms) });
  }
  return {
    frames: intervals.length,
    seconds: r1(total / 1000),
    avgFps: r1((1000 * intervals.length) / total),
    p1LowFps: r1(1000 / p99),
    over50: intervals.filter((ms) => ms > 50).length,
    maxMs: r1(sorted[sorted.length - 1]),
    fpsCap,
    hitches,
  };
}

/** `at` is the app's wall clock (epoch ms) when it sent the message; the page's Date.now() shares that clock. */
export type FromNative =
  | { type: 'pause' }
  | { type: 'resume' }
  | { type: 'emotion'; mood: Mood; intensity: number; at: number }
  | { type: 'gesture'; name: Gesture }
  /** Move the mouth along `mouth`; `at` is when the app's audio played its first sample (epoch ms). */
  | { type: 'speak'; mouth: MouthTrack; at: number }
  | { type: 'hush' }
  /** Measure frame pacing over the next `ms` of rendering, then send `stats`. */
  | { type: 'stats'; ms: number }
  /** Change the frame-rate cap (debug: 120 shows the headroom above the 30 fps cap). */
  | { type: 'fps'; cap: number };

interface NativeBridge {
  postMessage(message: string): void;
  onmessage: ((event: { data: string }) => void) | null;
}

declare global {
  interface Window {
    RinBridge?: NativeBridge;
  }
}

export function send(message: ToNative): void {
  const json = JSON.stringify(message);
  if (window.RinBridge) window.RinBridge.postMessage(json);
  else console.log('[RinBridge] ' + json); // in a desktop browser (npm run dev)
}

export function onNativeMessage(handler: (message: FromNative) => void): void {
  if (!window.RinBridge) return;
  window.RinBridge.onmessage = (event) => {
    try {
      const message = parseNative(event.data);
      if (message) handler(message);
    } catch {
      // Not JSON: ignored, like any unknown message.
    }
  };
}

/** Checks a message from the app; anything unknown or malformed is null, and ignored. */
export function parseNative(data: string): FromNative | null {
  const m = JSON.parse(data) as Record<string, unknown> | null;
  if (typeof m !== 'object' || m === null) return null;
  if (m.type === 'pause' || m.type === 'resume' || m.type === 'hush') return { type: m.type };
  if (m.type === 'gesture' && isGesture(m.name)) return { type: 'gesture', name: m.name };
  if (m.type === 'speak' && isMouthTrack(m.mouth) && typeof m.at === 'number' && Number.isFinite(m.at)) {
    return { type: 'speak', mouth: { fps: m.mouth.fps, f: m.mouth.f }, at: m.at };
  }
  const positive = (x: unknown): x is number => typeof x === 'number' && Number.isFinite(x) && x > 0;
  if (m.type === 'stats' && positive(m.ms)) return { type: 'stats', ms: Math.min(m.ms, 300_000) };
  if (m.type === 'fps' && positive(m.cap)) return { type: 'fps', cap: Math.min(m.cap, 240) };
  if (m.type === 'emotion' && isMood(m.mood)) {
    const intensity = typeof m.intensity === 'number' && Number.isFinite(m.intensity) ? m.intensity : 1;
    const at = typeof m.at === 'number' && Number.isFinite(m.at) ? m.at : Date.now();
    return { type: 'emotion', mood: m.mood, intensity: Math.min(Math.max(intensity, 0), 1), at };
  }
  return null;
}
