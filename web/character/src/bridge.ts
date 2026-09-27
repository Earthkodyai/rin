// The page's half of the bridge to Kotlin (character/CharacterMessage.kt), protocol version 1. The native side
// registers `RinBridge` with WebViewCompat.addWebMessageListener, scoped to the app's asset origin, so only this page
// can post. Messages are JSON strings; both sides ignore types they do not know, so either side can add types first.

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
  | { v: typeof PROTOCOL; type: 'error'; message: string };

export type FromNative = { type: 'pause' } | { type: 'resume' };

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
      const parsed = JSON.parse(event.data) as { type?: unknown };
      if (parsed.type === 'pause' || parsed.type === 'resume') handler(parsed as FromNative);
    } catch {
      // Not JSON: ignored, like any unknown message.
    }
  };
}
