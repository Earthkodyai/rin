import { describe, expect, it } from 'vitest';
import { REGRIP, actLength, afterSwaps, frameAt, handInSwap, parseAct, type CupsAct } from './cups';

const T = { leadMs: 300, swapMs: 400, gapMs: 100, exitMs: 200 };
const shuffle = (swaps: [number, number][], ball = 0): Extract<CupsAct, { kind: 'shuffle' }> => ({
  kind: 'shuffle', ball, at: 1000, swaps, ...T,
});

describe('shuffle', () => {
  it('ends with each cup in the slot the swaps put it', () => {
    const act = shuffle([[0, 1], [1, 2]], 0);
    const end = frameAt(act, 1000 + actLength(act));
    // cup 0: 0 -> 1 -> 2; cup 1: 1 -> 0; cup 2: 2 -> 1
    expect(end.cups.map((c) => c.x)).toEqual([2, 0, 1]);
    expect(end.cups[end.ballCup].x).toBe(afterSwaps(act.swaps, 0));
    expect(end.cups.every((c) => c.z === 0 && c.lift === 0)).toBe(true);
  });

  it('passes the cup from the lower slot in front, the other behind, never at the same spot', () => {
    const act = shuffle([[2, 0]]);
    for (let ms = 0; ms <= T.swapMs; ms += 20) {
      const f = frameAt(act, 1000 + T.leadMs + ms);
      const a = f.cups[0];
      const b = f.cups[2];
      expect(a.z).toBeGreaterThanOrEqual(0);
      expect(b.z).toBeLessThanOrEqual(0);
      if (ms > 0 && ms < T.swapMs) expect(a.z - b.z).toBeGreaterThan(0);
    }
  });

  it('never lets her hands cross: the right stays on the near half of the pair, the left on the far half', () => {
    for (const pair of [[0, 1], [1, 2], [0, 2]] as [number, number][]) {
      const act = shuffle([pair, pair, pair]);
      const mid = (pair[0] + pair[1]) / 2;
      for (let ms = T.leadMs; ms <= actLength(act); ms += 10) {
        const f = frameAt(act, 1000 + ms);
        if (f.right && f.left) expect(f.right.x).toBeLessThanOrEqual(f.left.x + 1e-9);
        if (f.right) expect(f.right.x).toBeLessThanOrEqual(Math.max(mid, 1) + 1e-9);
        if (f.left) expect(f.left.x).toBeGreaterThanOrEqual(Math.min(mid, 1) - 1e-9);
      }
    }
  });

  it('keeps a hand on a cup through every swap except the short trade in the middle', () => {
    const act = shuffle([[0, 2], [1, 2], [0, 1]]);
    for (let ms = 0; ms <= actLength(act); ms += 5) {
      const into = (ms - T.leadMs) % (T.swapMs + T.gapMs);
      const f = into / T.swapMs;
      if (ms < T.leadMs || f >= 1 || (f > REGRIP[0] && f < REGRIP[1])) continue;
      const fr = frameAt(act, 1000 + ms);
      for (const h of [fr.right!, fr.left!]) {
        expect(fr.cups.some((c) => Math.abs(c.x - h.x) < 1e-9 && Math.abs(c.z - h.z) < 1e-9)).toBe(true);
      }
    }
  });

  it('trades cups in the middle: each hand ends on the cup that came to its side', () => {
    expect(handInSwap(0, 2, 'right', 0)).toMatchObject({ x: 0, z: 0 });
    expect(handInSwap(0, 2, 'right', 0.5).x).toBeCloseTo(1);
    expect(handInSwap(0, 2, 'right', 1)).toMatchObject({ x: 0 });
    expect(handInSwap(0, 2, 'left', 1)).toMatchObject({ x: 2 });
    expect(handInSwap(0, 2, 'left', 0.5).lift).toBeGreaterThan(0); // passing over the right hand
  });

  it('brings the hands in over the lead and takes them away after the last swap', () => {
    const act = shuffle([[0, 1]]);
    expect(frameAt(act, 1000).right?.w ?? 0).toBe(0);
    expect(frameAt(act, 1000 + T.leadMs).right?.w).toBe(1);
    expect(frameAt(act, 1000 + actLength(act) + T.exitMs).right).toBeNull();
    expect(frameAt(act, 1000 + actLength(act) + T.exitMs).busy).toBe(false);
  });
});

describe('lift', () => {
  const lift = (holdMs: number | null, hands = [1]): CupsAct => ({
    kind: 'lift', ball: 1, at: 0, lift: [1], hands, leadMs: 300, upMs: 200, holdMs, downMs: 200, exitMs: 200,
  });

  it('raises the cup after the lead, holds it, and sets it down', () => {
    expect(frameAt(lift(500), 300).cups[1].lift).toBe(0);
    expect(frameAt(lift(500), 600).cups[1].lift).toBe(1);
    expect(frameAt(lift(500), 1200).cups[1].lift).toBe(0);
    expect(frameAt(lift(null), 60_000).cups[1].lift).toBe(1);
    expect(actLength(lift(null))).toBe(Infinity);
  });

  it('uses her hands only on the cups she lifts herself', () => {
    expect(frameAt(lift(500), 600).right).not.toBeNull();
    const own = frameAt(lift(500, []), 600);
    expect(own.right).toBeNull();
    expect(own.left).toBeNull();
  });
});

describe('parseAct', () => {
  it('reads the app acts exactly as CharacterCommand.Cups writes them (CharacterMessageTest)', () => {
    const shuffle = JSON.parse('{"ball":2,"at":1700000005000,"kind":"shuffle","swaps":[[0,1],[2,0]],"leadMs":300,"swapMs":450,"gapMs":150,"exitMs":250}');
    expect(parseAct(shuffle)).toEqual({ kind: 'shuffle', ball: 2, at: 1700000005000, swaps: [[0, 1], [2, 0]], leadMs: 300, swapMs: 450, gapMs: 150, exitMs: 250 });
    const lift = JSON.parse('{"ball":1,"at":1700000000010,"kind":"lift","lift":[0,1],"hands":[1],"leadMs":300,"upMs":250,"holdMs":null,"downMs":250,"exitMs":250}');
    expect(parseAct(lift)).toEqual({ kind: 'lift', ball: 1, at: 1700000000010, lift: [0, 1], hands: [1], leadMs: 300, upMs: 250, holdMs: null, downMs: 250, exitMs: 250 });
  });

  it('accepts what the app sends', () => {
    const act = parseAct({ kind: 'shuffle', ball: 2, at: 5, swaps: [[0, 1]], leadMs: 1, swapMs: 2, gapMs: 3, exitMs: 4 });
    expect(act).toMatchObject({ kind: 'shuffle', ball: 2, swaps: [[0, 1]] });
    expect(parseAct({ kind: 'lift', ball: 0, at: 5, lift: [2, 0], leadMs: 1, upMs: 1, holdMs: null, downMs: 1, exitMs: 1 }))
      .toMatchObject({ lift: [0, 2], hands: [0, 2], holdMs: null });
    expect(parseAct({ kind: 'rest', ball: 1, at: 5 })).toEqual({ kind: 'rest', ball: 1, at: 5 });
  });

  it('ignores anything malformed', () => {
    expect(parseAct(null)).toBeNull();
    expect(parseAct({ kind: 'rest', ball: 3, at: 5 })).toBeNull();
    expect(parseAct({ kind: 'shuffle', ball: 0, at: 5, swaps: [[1, 1]], leadMs: 1, swapMs: 2, gapMs: 3, exitMs: 4 })).toBeNull();
    expect(parseAct({ kind: 'shuffle', ball: 0, at: 5, swaps: [[0, 1]], leadMs: 1, swapMs: 0, gapMs: 3, exitMs: 4 })).toBeNull();
    expect(parseAct({ kind: 'lift', ball: 0, at: 5, lift: [0], hands: [1], leadMs: 1, upMs: 1, holdMs: 1, downMs: 1, exitMs: 1 })).toBeNull();
    expect(parseAct({ kind: 'spin', ball: 0, at: 5 })).toBeNull();
  });
});
