// The cup shuffle's timeline (task 3.4, D17): where each cup, the ball and each of Rin's hands is at a moment of one
// "act" the app sent. The app (mission/CupsGame.kt) owns the game: it picks the swaps, knows where the ball is and
// judges the pick; this page only acts it out (cupscene.ts), and the native 2D board (CupsBoard.kt) draws the same
// acts when this page is missing. Pure functions of time, so a dropped frame never puts a cup in the wrong place.
//
// Slots are 0, 1, 2 from the user's left to right. At the start of every act cup i stands in slot i, and the ball is
// under the cup in slot `ball`. Her right hand works the user's left (slot 0 side), her left hand the right.

export type Swap = readonly [number, number];

/** Times are epoch ms (the app's wall clock, which the page shares). */
export type CupsAct =
  /**
   * Cups lift (the ball shows under its cup if lifted), stay up for `holdMs` (null: until the next act), come down.
   * Her hands lift the cups in `hands` (she shows where the ball is); the others rise by themselves (the user's pick),
   * which leaves her arms free for a clap.
   */
  | { kind: 'lift'; ball: number; at: number; lift: number[]; hands: number[]; leadMs: number; upMs: number; holdMs: number | null; downMs: number; exitMs: number }
  /** Her hands slide pairs of cups past each other: the cup from the lower slot passes in front. */
  | { kind: 'shuffle'; ball: number; at: number; swaps: Swap[]; leadMs: number; swapMs: number; gapMs: number; exitMs: number }
  /** Cups stand still, hands off (waiting for the pick). */
  | { kind: 'rest'; ball: number; at: number };

/** A cup: x in slot units (0..2), z from -1 (back, toward her) to 1 (front), lift from 0 (down) to 1 (up). */
export interface CupPose {
  x: number;
  z: number;
  lift: number;
}

/** A hand on a cup top (same units as the cup), and how much of her arm the hand pose takes (0 rest .. 1). */
export interface HandPose {
  x: number;
  z: number;
  lift: number;
  w: number;
}

export interface CupsFrame {
  cups: CupPose[];
  /** Which cup (index into `cups`) the ball is under. */
  ballCup: number;
  right: HandPose | null;
  left: HandPose | null;
  /** Her eyes follow her hands while they work. */
  busy: boolean;
}

/** How high a gliding hand rises, in lifts (a lifted cup's height). */
export const GLIDE_HOP = 0.15;

export const smooth = (u: number) => {
  const x = Math.min(Math.max(u, 0), 1);
  return x * x * (3 - 2 * x);
};
const lerp = (a: number, b: number, u: number) => a + (b - a) * u;

const isSlot = (x: unknown): x is number => x === 0 || x === 1 || x === 2;
const isMs = (x: unknown): x is number => typeof x === 'number' && Number.isFinite(x) && x >= 0 && x <= 60_000;

/** Checks an act from the app; anything malformed is null (ignored). */
export function parseAct(m: unknown): CupsAct | null {
  if (typeof m !== 'object' || m === null) return null;
  const a = m as Record<string, unknown>;
  if (!isSlot(a.ball) || typeof a.at !== 'number' || !Number.isFinite(a.at)) return null;
  const base = { ball: a.ball, at: a.at };
  if (a.kind === 'rest') return { kind: 'rest', ...base };
  if (a.kind === 'lift') {
    const lift = a.lift;
    if (!Array.isArray(lift) || lift.length < 1 || lift.length > 3 || !lift.every(isSlot)) return null;
    const hands = a.hands ?? lift;
    if (!Array.isArray(hands) || !hands.every((h) => lift.includes(h))) return null;
    if (![a.leadMs, a.upMs, a.downMs, a.exitMs].every(isMs)) return null;
    if (a.holdMs !== null && !isMs(a.holdMs)) return null;
    return {
      kind: 'lift', ...base, lift: [...new Set(lift as number[])].sort(), hands: [...new Set(hands as number[])].sort(),
      leadMs: a.leadMs as number, upMs: a.upMs as number, holdMs: a.holdMs as number | null,
      downMs: a.downMs as number, exitMs: a.exitMs as number,
    };
  }
  if (a.kind === 'shuffle') {
    const swaps = a.swaps;
    if (!Array.isArray(swaps) || swaps.length > 30) return null;
    const ok = swaps.every((s) => Array.isArray(s) && s.length === 2 && isSlot(s[0]) && isSlot(s[1]) && s[0] !== s[1]);
    if (!ok || ![a.leadMs, a.swapMs, a.gapMs, a.exitMs].every(isMs) || (a.swapMs as number) <= 0) return null;
    return {
      kind: 'shuffle', ...base, swaps: swaps.map((s) => [s[0], s[1]] as const),
      leadMs: a.leadMs as number, swapMs: a.swapMs as number, gapMs: a.gapMs as number, exitMs: a.exitMs as number,
    };
  }
  return null;
}

/** The slot each cup ends in, and where the ball is, after an act's swaps (the same rule as CupsGame.kt). */
export function afterSwaps(swaps: readonly Swap[], ball: number): number {
  let b = ball;
  for (const [p, q] of swaps) b = b === p ? q : b === q ? p : b;
  return b;
}

const still = (): CupPose[] => [0, 1, 2].map((x) => ({ x, z: 0, lift: 0 }));

/** The whole act at `now` (epoch ms). */
export function frameAt(act: CupsAct, now: number): CupsFrame {
  const t = now - act.at;
  if (act.kind === 'rest') return { cups: still(), ballCup: act.ball, right: null, left: null, busy: false };
  if (act.kind === 'lift') return liftAt(act, t);
  return shuffleAt(act, t);
}

function liftAt(act: Extract<CupsAct, { kind: 'lift' }>, t: number): CupsFrame {
  const cups = still();
  const up = act.leadMs;
  const hold = up + act.upMs;
  const down = act.holdMs === null ? Infinity : hold + act.holdMs;
  const end = down + act.downMs;
  const lift = t < up ? 0 : t < hold ? smooth((t - up) / act.upMs) : t < down ? 1 : smooth(1 - (t - down) / act.downMs);
  for (const s of act.lift) cups[s].lift = lift;
  // Hands: one cup takes the nearer hand (the middle one her right); two take one each.
  const [first, second] = act.hands;
  const rightSlot = act.hands.length === 1 ? (first === 2 ? null : first) : (first ?? null);
  const leftSlot = act.hands.length === 1 ? (first === 2 ? 2 : null) : (second ?? null);
  const w = t < up ? smooth(t / Math.max(act.leadMs, 1)) : t < end ? 1 : 1 - smooth((t - end) / Math.max(act.exitMs, 1));
  const hand = (slot: number | null): HandPose | null => (slot === null || w <= 0 ? null : { x: slot, z: 0, lift, w });
  return { cups, ballCup: act.ball, right: hand(rightSlot), left: hand(leftSlot), busy: t < end };
}

/**
 * A cup in a swap of slots lo < hi, `f` (0..1) of the way through: the one from lo passes in front, the other behind.
 * Each cup eases in to the middle and out again, so both are still for a moment side by side, when her hands trade
 * them. With one ease across the whole swap they moved fastest right there, and the hands, reversing at full speed,
 * looked like they trembled (the tester, 2026-09-29; per-frame jumps of 4 cm). The top speed is the same.
 */
export function swapPose(lo: number, hi: number, fromLo: boolean, f: number): CupPose {
  const [from, to] = fromLo ? [lo, hi] : [hi, lo];
  const mid = (lo + hi) / 2;
  const x = f < 0.5 ? lerp(from, mid, smooth(2 * f)) : lerp(mid, to, smooth(2 * f - 1));
  const arc = Math.sin(Math.PI * smooth(f));
  return { x, z: fromLo ? arc : -arc, lift: 0 };
}

/** The part of a swap (0..1 of its time) in which the two hands trade cups in the middle. */
export const REGRIP = [0.35, 0.65] as const;

/**
 * Where a hand is `f` (0..1) through a swap of slots lo < hi. Her arms are short and cannot cross her middle
 * (crossing, the upper arm went through her chest: 11–15 cm deep with the sample, task 3.4 sweep), so the hands meet
 * in the middle: her right hand pushes the cup from lo (passing in front) and her left the cup from hi (behind) until
 * they meet, they trade cups (the left hand passing over the right), and each pulls the other cup back to its own
 * side. The tester's pick (2026-09-29), after "the hands don't stay with the cups" with hands that let go halfway.
 */
export function handInSwap(lo: number, hi: number, side: 'right' | 'left', f: number): Omit<HandPose, 'w'> {
  const right = side === 'right';
  const first = swapPose(lo, hi, right, f);
  const then = swapPose(lo, hi, !right, f);
  if (f <= REGRIP[0]) return first;
  if (f >= REGRIP[1]) return then;
  const u = smooth((f - REGRIP[0]) / (REGRIP[1] - REGRIP[0]));
  return { x: lerp(first.x, then.x, u), z: lerp(first.z, then.z, u), lift: right ? 0 : 0.5 * Math.sin(Math.PI * u) };
}

/**
 * Swap i starts at leadMs + i·(swapMs + gapMs). The hands work each swap as handInSwap says, ending on their own
 * sides (her right on lo, her left on hi), then glide to the next pair with a small hop over the rims; after the last
 * swap they go back to rest.
 */
function shuffleAt(act: Extract<CupsAct, { kind: 'shuffle' }>, t: number): CupsFrame {
  const slotOf = [0, 1, 2]; // cup -> slot
  const cupIn = (slot: number) => slotOf.indexOf(slot);
  const cups = still();
  const step = act.swapMs + act.gapMs;
  const n = act.swaps.length;
  const end = act.leadMs + Math.max(n * step - act.gapMs, 0);
  if (n === 0) return { cups, ballCup: act.ball, right: null, left: null, busy: false };
  const i = Math.min(Math.max(Math.floor((t - act.leadMs) / step), 0), n - 1);
  const into = t - act.leadMs - i * step; // ms into swap i (negative: the lead-in)
  // Replay the swaps before i, and swap i too once it is over.
  for (let j = 0; j < i || (j === i && into >= act.swapMs); j++) {
    const [p, q] = act.swaps[j];
    const a = cupIn(p);
    const b = cupIn(q);
    slotOf[a] = q;
    slotOf[b] = p;
  }
  for (let c = 0; c < 3; c++) cups[c].x = slotOf[c];
  const [p0, q0] = act.swaps[i];
  const lo = Math.min(p0, q0);
  const hi = Math.max(p0, q0);
  const f = into / act.swapMs;
  if (f >= 0 && f < 1) {
    cups[cupIn(lo)] = swapPose(lo, hi, true, f);
    cups[cupIn(hi)] = swapPose(lo, hi, false, f);
  }
  const hand = (side: 'right' | 'left'): HandPose | null => {
    const own = side === 'right' ? lo : hi; // where her hand starts and ends this swap
    if (into < 0) return { x: own, z: 0, lift: 0, w: smooth(t / Math.max(act.leadMs, 1)) };
    if (f < 1) return { ...handInSwap(lo, hi, side, f), w: 1 };
    const since = into - act.swapMs;
    if (i + 1 < n) {
      const [p1, q1] = act.swaps[i + 1];
      const next = side === 'right' ? Math.min(p1, q1) : Math.max(p1, q1);
      const u = smooth(since / Math.max(act.gapMs, 1));
      return { x: lerp(own, next, u), z: 0, lift: next === own ? 0 : GLIDE_HOP * Math.sin(Math.PI * u), w: 1 };
    }
    const w = 1 - smooth(since / Math.max(act.exitMs, 1));
    return w > 0 ? { x: own, z: 0, lift: 0, w } : null;
  };
  // The ball stays under the cup that started in slot `ball`, which is cup `ball`.
  return { cups, ballCup: act.ball, right: hand('right'), left: hand('left'), busy: t < end };
}

/** When an act's cups come to rest, in ms after `at` (Infinity when a lift holds); her hands leave `exitMs` later. */
export function actLength(act: CupsAct): number {
  if (act.kind === 'rest') return 0;
  if (act.kind === 'lift') return act.holdMs === null ? Infinity : act.leadMs + act.upMs + act.holdMs + act.downMs;
  const n = act.swaps.length;
  return act.leadMs + Math.max(n * (act.swapMs + act.gapMs) - act.gapMs, 0);
}
