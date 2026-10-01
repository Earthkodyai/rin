import { describe, expect, it } from 'vitest';
import { frameStats, parseNative } from './bridge';

describe('parseNative (task 2.5 messages)', () => {
  it('reads stats and fps, capped to sane ranges', () => {
    expect(parseNative('{"type":"stats","ms":30000}')).toEqual({ type: 'stats', ms: 30000 });
    expect(parseNative('{"type":"stats","ms":1e9}')).toEqual({ type: 'stats', ms: 300000 });
    expect(parseNative('{"type":"fps","cap":120}')).toEqual({ type: 'fps', cap: 120 });
    expect(parseNative('{"type":"fps","cap":1000}')).toEqual({ type: 'fps', cap: 240 });
  });

  it('ignores missing, zero or negative values', () => {
    expect(parseNative('{"type":"stats"}')).toBeNull();
    expect(parseNative('{"type":"stats","ms":0}')).toBeNull();
    expect(parseNative('{"type":"fps","cap":-30}')).toBeNull();
    expect(parseNative('{"type":"fps","cap":"60"}')).toBeNull();
  });
});

describe('parseNative insets (UX.4)', () => {
  it('reads the covered shares of the view', () => {
    expect(parseNative('{"type":"insets","top":0.15,"bottom":0.4}')).toEqual({ type: 'insets', top: 0.15, bottom: 0.4 });
    expect(parseNative('{"type":"insets","top":0,"bottom":0}')).toEqual({ type: 'insets', top: 0, bottom: 0 });
  });

  it('refuses shares that would leave almost nothing open', () => {
    expect(parseNative('{"type":"insets","top":0.5,"bottom":0.5}')).toBeNull();
    expect(parseNative('{"type":"insets","top":-0.1,"bottom":0.2}')).toBeNull();
    expect(parseNative('{"type":"insets","top":0.1}')).toBeNull();
  });
});

describe('frameStats', () => {
  it('is null without frames', () => {
    expect(frameStats([], 30)).toBeNull();
  });

  it('reports a steady 30 fps', () => {
    const s = frameStats(Array(300).fill(1000 / 30), 30)!;
    expect(s).toMatchObject({ frames: 300, seconds: 10, avgFps: 30, p1LowFps: 30, over50: 0, fpsCap: 30 });
  });

  it('counts hitches and takes the 1% low from the slowest 1% of intervals', () => {
    // 296 frames at 33.3 ms and 4 at 66.7 ms: over 1% slow, so the 99th percentile (nearest rank) is a slow one.
    const intervals = [...Array(296).fill(1000 / 30), ...Array(4).fill(1000 / 15)];
    const s = frameStats(intervals, 30)!;
    expect(s.over50).toBe(4);
    expect(s.hitches[0]).toEqual({ at: Math.round(296 * (1000 / 30) + 1000 / 15), ms: 66.7 });
    expect(s.hitches).toHaveLength(4);
    expect(s.p1LowFps).toBe(15);
    expect(s.maxMs).toBe(66.7);
    expect(s.avgFps).toBe(29.6);
  });
});
