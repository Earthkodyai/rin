// The leaderboard's security rules (G.6) against the Firestore emulator: `npm test` in firebase/ (needs Java 21+).
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getCountFromServer,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
} from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-rinalarm',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(() => env.cleanup());

beforeEach(() => env.clearFirestore());

const row = (over = {}) => ({ name: 'Earth', uni: 'ku', levels: 5, timeMs: 61_000, at: serverTimestamp(), ...over });
const db = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();
const score = (uid, game = 'pads') => doc(db(uid), `boards/${game}/scores/${uid}`);

/** Puts a row in place as the server would have, `at` in the past so the 5 s limit is not in the way. */
async function seed(uid, data, game = 'pads') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `boards/${game}/scores/${uid}`), {
      ...row(),
      at: Timestamp.fromMillis(Date.now() - 60_000),
      ...data,
    });
  });
}

describe('posting a score', () => {
  test('a player posts their own row, in either game', async () => {
    await assertSucceeds(setDoc(score('a'), row()));
    await assertSucceeds(setDoc(score('a', 'cups'), row()));
  });

  test('no one posts without signing in, or as someone else, or to another game', async () => {
    await assertFails(setDoc(doc(db(null), 'boards/pads/scores/a'), row()));
    await assertFails(setDoc(doc(db('a'), 'boards/pads/scores/b'), row()));
    await assertFails(setDoc(doc(db('a'), 'boards/chess/scores/a'), row()));
  });

  test('every field must be there, and nothing else', async () => {
    const { uni, ...noUni } = row();
    await assertFails(setDoc(score('a'), noUni));
    await assertFails(setDoc(score('a'), row({ admin: true })));
  });

  test("the time must be the server's", async () => {
    await assertFails(setDoc(score('a'), row({ at: Timestamp.fromMillis(Date.now()) })));
  });

  test('levels 1..500, whole numbers', async () => {
    await assertFails(setDoc(score('a'), row({ levels: 0, timeMs: 0 })));
    await assertFails(setDoc(score('a'), row({ levels: 501, timeMs: 600_000 })));
    await assertFails(setDoc(score('a'), row({ levels: 5.5 })));
    await assertFails(setDoc(score('a'), row({ levels: '5' })));
    await assertSucceeds(setDoc(score('a'), row({ levels: 500, timeMs: 500_000 })));
  });

  test('at least 1 s a level and at most a day', async () => {
    await assertFails(setDoc(score('a'), row({ levels: 5, timeMs: 4_999 })));
    await assertSucceeds(setDoc(score('a'), row({ levels: 5, timeMs: 5_000 })));
    await assertFails(setDoc(score('b'), row({ levels: 5, timeMs: 86_400_001 })));
  });

  test('names: empty, Thai, emoji and 20 characters pass', async () => {
    await assertSucceeds(setDoc(score('a'), row({ name: '' })));
    await assertSucceeds(setDoc(score('b'), row({ name: 'เอิร์ธ เกษตร' })));
    await assertSucceeds(setDoc(score('c'), row({ name: 'Rin fan 🌸' })));
    await assertSucceeds(setDoc(score('d'), row({ name: 'x'.repeat(20) })));
  });

  test('names: too long, untrimmed, control or hidden characters fail', async () => {
    await assertFails(setDoc(score('a'), row({ name: 'x'.repeat(21) })));
    await assertFails(setDoc(score('a'), row({ name: ' Earth' })));
    await assertFails(setDoc(score('a'), row({ name: 'Earth ' })));
    await assertFails(setDoc(score('a'), row({ name: 'Ea\nrth' })));
    await assertFails(setDoc(score('a'), row({ name: 'Ea​rth' })));
    await assertFails(setDoc(score('a'), row({ name: '‮htrae' })));
    await assertFails(setDoc(score('a'), row({ name: 42 })));
  });

  test('university: one in the list, or null', async () => {
    await assertSucceeds(setDoc(score('a'), row({ uni: null })));
    await assertSucceeds(setDoc(score('b'), row({ uni: 'utcc' })));
    await assertFails(setDoc(score('c'), row({ uni: 'chula' })));
    await assertFails(setDoc(score('c'), row({ uni: 7 })));
    await assertFails(setDoc(score('c'), row({ uni: 'KU' })));
    await assertFails(setDoc(score('c'), row({ uni: 'k' })));
    await assertFails(setDoc(score('c'), row({ uni: '<script>' })));
  });
});

describe('updating a score', () => {
  test('a better score replaces the old one', async () => {
    await seed('a', { levels: 5, timeMs: 61_000 });
    await assertSucceeds(setDoc(score('a'), row({ levels: 6, timeMs: 90_000 })));
  });

  test('as many levels in less time, or the same score with a new name, also pass', async () => {
    await seed('a', { levels: 5, timeMs: 61_000 });
    await assertSucceeds(setDoc(score('a'), row({ levels: 5, timeMs: 60_000 })));
    await seed('b', { levels: 5, timeMs: 61_000 });
    await assertSucceeds(setDoc(score('b'), row({ name: 'New name', levels: 5, timeMs: 61_000 })));
  });

  test('a worse score never replaces a better one', async () => {
    await seed('a', { levels: 5, timeMs: 61_000 });
    await assertFails(setDoc(score('a'), row({ levels: 4, timeMs: 30_000 })));
    await assertFails(setDoc(score('a'), row({ levels: 5, timeMs: 62_000 })));
  });

  test('at most one change every 5 s', async () => {
    await assertSucceeds(setDoc(score('a'), row()));
    await assertFails(setDoc(score('a'), row({ levels: 6, timeMs: 90_000 })));
  });

  test("no one changes another player's row", async () => {
    await seed('a', {});
    await assertFails(setDoc(doc(db('b'), 'boards/pads/scores/a'), row({ levels: 9, timeMs: 90_000 })));
  });
});

describe('banned players', () => {
  beforeEach(() => env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'banned/a'), { why: 'name' })));

  test('cannot post or report, but can still delete their rows', async () => {
    await assertFails(setDoc(score('a'), row()));
    await seed('a', {});
    await assertFails(setDoc(score('a'), row({ levels: 9, timeMs: 90_000 })));
    await seed('b', {});
    await assertFails(setDoc(doc(db('a'), 'reports/a_pads_b'), { game: 'pads', target: 'b', at: serverTimestamp() }));
    await assertSucceeds(deleteDoc(score('a')));
  });

  test('the ban list is not readable or writable from the app', async () => {
    await assertFails(getDoc(doc(db('a'), 'banned/a')));
    await assertFails(setDoc(doc(db('b'), 'banned/c'), { why: 'x' }));
  });
});

describe('reading the board', () => {
  test('signed-in players read the top rows, their own row, and a count', async () => {
    await seed('a', { levels: 7, timeMs: 80_000 });
    await seed('b', { levels: 5, timeMs: 61_000 });
    const top = await assertSucceeds(
      getDocs(query(collection(db('c'), 'boards/pads/scores'), orderBy('levels', 'desc'), orderBy('timeMs'), limit(50))),
    );
    if (top.size !== 2) throw new Error(`expected 2 rows, got ${top.size}`);
    await assertSucceeds(getDoc(doc(db('c'), 'boards/pads/scores/a')));
    await assertSucceeds(getCountFromServer(collection(db('c'), 'boards/pads/scores')));
  });

  test('not without signing in, and no other collections', async () => {
    await assertFails(getDocs(collection(db(null), 'boards/pads/scores')));
    await assertFails(getDocs(collection(db('c'), 'boards/chess/scores')));
    await assertFails(getDocs(collection(db('c'), 'reports')));
  });
});

describe('deleting', () => {
  test("a player deletes their own row, not someone else's", async () => {
    await seed('a', {});
    await assertFails(deleteDoc(doc(db('b'), 'boards/pads/scores/a')));
    await assertSucceeds(deleteDoc(score('a')));
  });
});

describe('reporting a row', () => {
  const report = (by, target, game = 'pads', id = `${by}_${game}_${target}`) =>
    setDoc(doc(db(by), `reports/${id}`), { game, target, at: serverTimestamp() });

  beforeEach(() => seed('b', {}));

  test('once per reporter and row', async () => {
    await assertSucceeds(report('a', 'b'));
    await assertFails(report('a', 'b'));
  });

  test('not your own row, not a row that is not there, not under another id', async () => {
    await seed('a', {});
    await assertFails(report('a', 'a'));
    await assertFails(report('a', 'zzz'));
    await assertFails(report('a', 'b', 'cups'));
    await assertFails(report('a', 'b', 'pads', 'c_pads_b'));
  });

  test('no extra fields, and never read back', async () => {
    await assertFails(
      setDoc(doc(db('a'), 'reports/a_pads_b'), { game: 'pads', target: 'b', at: serverTimestamp(), note: 'x' }),
    );
    await assertSucceeds(report('a', 'b'));
    await assertFails(getDoc(doc(db('a'), 'reports/a_pads_b')));
  });
});
