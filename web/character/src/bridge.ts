// The page's half of the bridge to Kotlin (character/CharacterMessage.kt), protocol version 1. The native side
// registers `RinBridge` with WebViewCompat.addWebMessageListener, scoped to the app's asset origin, so only this page
// can post. Messages are JSON strings; both sides ignore types they do not know, so either side can add types first.
// Task 2.2 added `emotion` both ways and `tap` to the app; still version 1, since every addition is ignorable.
import { isMood, type Mood } from './emotion';

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
  | { v: typeof PROTOCOL; type: 'tap'; part: 'head' };

/** `at` is the app's wall clock (epoch ms) when it sent the message; the page's Date.now() shares that clock. */
export type FromNative =
  | { type: 'pause' }
  | { type: 'resume' }
  | { type: 'emotion'; mood: Mood; intensity: number; at: number };

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
  if (m.type === 'pause' || m.type === 'resume') return { type: m.type };
  if (m.type === 'emotion' && isMood(m.mood)) {
    const intensity = typeof m.intensity === 'number' && Number.isFinite(m.intensity) ? m.intensity : 1;
    const at = typeof m.at === 'number' && Number.isFinite(m.at) ? m.at : Date.now();
    return { type: 'emotion', mood: m.mood, intensity: Math.min(Math.max(intensity, 0), 1), at };
  }
  return null;
}
