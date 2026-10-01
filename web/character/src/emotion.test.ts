import { describe, expect, it } from 'vitest';
import { BLEND_S, Blend, CHANNELS, MOODS, TAP_TALK_HUSH, TapReaction, isMood, isTap, moodChannels } from './emotion';
import { parseNative } from './bridge';

const FRAME = 1 / 30;

describe('moods', () => {
  it('are the seven from the plan (05a), in the table the app also reads', () => {
    expect(MOODS).toEqual(['sleepy', 'cheerful', 'proud', 'worried', 'pouty', 'sulky', 'relieved']);
    expect(isMood('cheerful')).toBe(true);
    expect(isMood('angry')).toBe(false); // an expression, not a mood
    expect(isMood('toString')).toBe(false);
  });

  it('keep every channel in range, and intensity scales the whole look', () => {
    for (const mood of MOODS) {
      const full = moodChannels(mood);
      for (const c of CHANNELS) {
        expect(Number.isFinite(full[c])).toBe(true);
        if (c === 'pitch' || c === 'yaw' || c === 'roll') expect(Math.abs(full[c])).toBeLessThanOrEqual(0.2);
        else if (c !== 'gaze') expect(full[c]).toBeGreaterThanOrEqual(0);
      }
      const half = moodChannels(mood, 0.5);
      for (const c of CHANNELS) expect(half[c]).toBeCloseTo(full[c] / 2);
      expect(moodChannels(mood, 7)).toEqual(full); // clamped
    }
  });
});

describe('Blend', () => {
  it('reaches the target after exactly BLEND_S, and says so once', () => {
    const blend = new Blend(moodChannels('sleepy'));
    blend.set(moodChannels('cheerful'));
    let frames = 0;
    let done = 0;
    while (blend.blending) {
      frames++;
      if (blend.step(FRAME)) done++;
    }
    expect(done).toBe(1);
    expect(frames).toBe(Math.ceil(BLEND_S / FRAME));
    expect(blend.values).toEqual(moodChannels('cheerful'));
    expect(blend.step(FRAME)).toBe(false);
  });

  it('fits inside the 300 ms budget with a frame and the bridge to spare', () => {
    expect(BLEND_S * 1000 + FRAME * 1000).toBeLessThanOrEqual(250);
  });

  it('retargets from where it is, without a jump', () => {
    const blend = new Blend(moodChannels('sleepy'));
    blend.set(moodChannels('cheerful'));
    blend.step(BLEND_S / 2);
    const midway = blend.values.smile;
    expect(midway).toBeGreaterThan(0);
    expect(midway).toBeLessThan(moodChannels('cheerful').smile);
    blend.set(moodChannels('worried'));
    expect(blend.values.smile).toBe(midway);
    blend.step(0.001);
    expect(Math.abs(blend.values.smile - midway)).toBeLessThan(0.01);
  });

  it('with zero duration, jumps at once', () => {
    const blend = new Blend(moodChannels('sleepy'));
    blend.set(moodChannels('pouty'), 0);
    expect(blend.blending).toBe(false);
    expect(blend.values).toEqual(moodChannels('pouty'));
  });
});

describe('TapReaction', () => {
  it('rises, holds, fades back to the mood, and nods only at the start', () => {
    const r = new TapReaction();
    const sulky = moodChannels('sulky');
    expect(r.apply(sulky)).toEqual(sulky); // nothing before a tap
    expect(r.trigger()).toBe(true);
    r.step(TapReaction.RISE_S + 0.2);
    expect(r.weight).toBe(1);
    const joy = r.apply(sulky);
    expect(joy.happy).toBe(1);
    expect(joy.angry).toBe(0);
    expect(joy.sad).toBe(0);
    expect(joy.yaw).toBe(sulky.yaw); // pose stays the mood's
    r.step(TapReaction.NOD_S);
    expect(r.nod).toBe(0);
    r.step(TapReaction.HOLD_S + TapReaction.FADE_S);
    expect(r.weight).toBe(0);
    expect(r.apply(sulky)).toEqual(sulky);
  });

  it('raises the eyes-open smile instead of the open-mouthed joy, and steps back while she talks', () => {
    const r = new TapReaction();
    const cheerful = moodChannels('cheerful');
    r.trigger();
    r.step(TapReaction.RISE_S + 0.2);
    const quiet = r.apply(cheerful, 'smile', 0);
    expect(quiet.smile).toBe(1);
    expect(quiet.happy).toBe(cheerful.happy); // Fcl_ALL_Joy stays where the mood had it
    const talking = r.apply(cheerful, 'smile', 1);
    expect(talking.smile).toBeLessThan(quiet.smile);
    expect(talking.smile).toBeCloseTo(cheerful.smile + (1 - cheerful.smile) * (1 - TAP_TALK_HUSH), 9);
  });

  it('ignores taps during the cooldown', () => {
    const r = new TapReaction();
    expect(r.trigger()).toBe(true);
    r.step(TapReaction.COOLDOWN_S - 0.01);
    expect(r.trigger()).toBe(false);
    r.step(0.02);
    expect(r.trigger()).toBe(true);
  });
});

describe('isTap', () => {
  it('accepts a short, still touch and rejects drags and long presses', () => {
    expect(isTap(120, 3, -4)).toBe(true);
    expect(isTap(351, 0, 0)).toBe(false);
    expect(isTap(100, 9, 9)).toBe(false); // ~12.7 px
  });
});

describe('parseNative', () => {
  it('reads emotion commands, clamping intensity', () => {
    expect(parseNative('{"type":"emotion","mood":"proud","intensity":2,"at":5}')).toEqual({
      type: 'emotion',
      mood: 'proud',
      intensity: 1,
      at: 5,
    });
    expect(parseNative('{"type":"emotion","mood":"proud"}')).toMatchObject({ intensity: 1 });
  });

  it('ignores unknown moods, unknown types and non-objects', () => {
    expect(parseNative('{"type":"emotion","mood":"furious"}')).toBeNull();
    expect(parseNative('{"type":"dance"}')).toBeNull();
    expect(parseNative('null')).toBeNull();
    expect(parseNative('3')).toBeNull();
    expect(parseNative('{"type":"pause","extra":1}')).toEqual({ type: 'pause' });
  });
});
