import { test } from 'node:test';
import assert from 'node:assert/strict';
import { billed, check, load, sentences, spoken, words } from './script-check.mjs';

const env = { moods: ['cheerful', 'worried'], gestures: ['nod', 'wave'] };
const line = (over = {}) => ({ id: 'a.b.01', pool: 'a.b', emotion: 'cheerful', gesture: null, tag: null, text: 'Good morning~', ...over });
const script = (lines, pools = { 'a.b': { use: 'event' } }) => ({ budget: { scriptMax: 1000 }, tags: ['[softly]'], pools, lines });

test('TTS never reads the screen-only tilde, and say overrides text', () => {
  assert.equal(spoken(line()), 'Good morning');
  assert.equal(spoken(line({ text: 'Call 1323.', say: 'Call one, three, two, three.' })), 'Call one, three, two, three.');
});

test('billed characters include the tag and its space', () => {
  assert.equal(billed(line({ tag: '[softly]' })), '[softly] Good morning'.length);
});

test('ellipsis and tilde end a sentence', () => {
  assert.deepEqual(sentences('Mmh… morning already? Let’s go~ Up.'), ['Mmh…', 'morning already?', 'Let’s go.', 'Up.']);
  assert.equal(words('Okay, a short nap.'), 4);
});

test('the real script passes every rule', () => {
  const { script: s, repeat, moods, gestures } = load();
  assert.deepEqual(check(s, { moods, gestures, repeat }), []);
});

test('flags pet names, Thai text, inline tags and long lines', () => {
  const bad = [
    line({ id: 'a.b.01', text: 'Morning, darling.' }),
    line({ id: 'a.b.02', text: 'สู้ๆ' }),
    line({ id: 'a.b.03', text: '[laughs] Hi.' }),
    line({ id: 'a.b.04', text: 'One two three four five six seven eight nine ten eleven twelve thirteen.' }),
    line({ id: 'a.b.05', text: 'One. Two. Three. Four.' }),
  ];
  const p = check(script(bad), env).join('\n');
  assert.match(p, /a\.b\.01: forbidden word/);
  assert.match(p, /a\.b\.02: English only/);
  assert.match(p, /a\.b\.03: audio tags go in "tag"/);
  assert.match(p, /a\.b\.04: .* 13 words/);
  assert.match(p, /a\.b\.05: 4 sentences/);
});

test('flags unknown mood, gesture, tag and duplicate ids', () => {
  const p = check(script([line({ emotion: 'angry', gesture: 'dance', tag: '[shouts]' }), line()]), env).join('\n');
  assert.match(p, /emotion angry/);
  assert.match(p, /gesture dance/);
  assert.match(p, /tag \[shouts\]/);
  assert.match(p, /duplicate id/);
});

test('a daily pool needs a week of lines, counting the pool it mixes with', () => {
  const pools = { g: { use: 'daily' }, 'g.x': { use: 'daily', mix: 'g' } };
  const g = [1, 2, 3, 4].map((i) => line({ id: `g.0${i}`, pool: 'g' }));
  const gx = [1, 2, 3].map((i) => line({ id: `g.x.0${i}`, pool: 'g.x' }));
  const p = check(script([...g, ...gx], pools), env);
  assert.deepEqual(p, ['g: daily pool has 4 lines (min 7)']);
});

test('every question has one affirm and one deny answer and both chips', () => {
  const pools = { 'chat.question': { use: 'event' }, 'chat.answer': { use: 'bound' } };
  const q = line({ id: 'chat.x.01', pool: 'chat.question', replies: { affirm: 'Yes' } });
  const yes = line({ id: 'chat.x.01.yes', pool: 'chat.answer', reply_to: 'chat.x.01', intent: 'affirm' });
  const p = check(script([q, yes], pools), env).join('\n');
  assert.match(p, /no deny chip/);
  assert.match(p, /exactly 1 deny answer, has 0/);
});

test('the budget counts the Repeat-after-Rin sentences too', () => {
  const s = script([line()]);
  s.budget.scriptMax = 20;
  assert.match(check(s, { ...env, repeat: [{ id: 'R01', text: 'Twelve chars' }] }).join(), /budget: 24 characters/);
});
