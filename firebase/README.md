# Tournament leaderboard (Firebase, G.6)

The online leaderboard is the only part of RinAlarm that uses the network (ADR 0008). It runs on Firebase's free
Spark plan: Firestore, anonymous sign-in, and App Check with Play Integrity. Going over Spark's free quotas stops
reads and writes for the day; it never bills.

- `firestore.rules`: who may write what. Every field is checked; a row is written only by its owner and only when it
  is no worse than before; reports are write-only; `banned/{uid}` (console only) blocks a player.
- `firestore.indexes.json`: the board's sort (levels down, time up).
- `test/rules.test.mjs`: the rules on the emulator. `npm ci && npm test` here (Java 21+; on this PC
  `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`). CI runs them too.

## Data

`boards/{pads|cups}/scores/{uid}`: `name` (0-20 characters), `uni` (an id or null), `levels` (1-500), `timeMs`
(at least 1 s a level), `at` (server time). `reports/{reporter}_{game}_{target}`: `game`, `target`, `at`.

## Setting up the project (once, by the developer)

1. [Firebase console](https://console.firebase.google.com): Add project, any name, Google Analytics **off**. It
   starts on Spark; leave it there.
2. Build, Firestore Database, Create database: **Standard** edition, location **asia-southeast1 (Singapore)** (it
   cannot be changed later), start in **production mode**.
3. Build, Authentication, Get started, Sign-in method: enable **Anonymous** only.
4. Project settings, Your apps, Android: package `io.github.earthkodyai.rinalarm`, nickname RinAlarm. Add the release
   key's **SHA-256** (`./gradlew signingReport`, variant release; Play App Signing uses this same key, PEPK). Skip the
   google-services.json download and the SDK steps; the app does not use them.
5. Copy three values from that Android app's settings into `local.properties` (never into the repo):
   ```
   rin.firebase.projectId=<Project ID>
   rin.firebase.appId=<App ID, 1:...:android:...>
   rin.firebase.apiKey=<Web API key>
   ```
   The API key names the project; it is not a secret, but keep it out of the public repo anyway. In Google Cloud
   console, APIs and services, Credentials, restrict it to Android apps (package + SHA-256 above).
6. Deploy the rules and index from here: `npx firebase login`, then
   `npx firebase deploy --only firestore --project <Project ID>`.
7. App Check: register the Android app with **Play Integrity**. In Play Console, Test and release, App integrity,
   link the same Google Cloud project. For debug builds: run the app once, copy the token logged by
   `DebugAppCheckProvider` (logcat), and add it under App Check, Manage debug tokens.
8. After a few days of App Check metrics showing verified requests, turn on **Enforce** for Firestore and
   Authentication.

## Moderation

Reports land in `reports`. To remove a row, delete `boards/{game}/scores/{uid}`. To stop a player posting, create
`banned/{uid}` (any field). Both in the Firestore console.

## Trying it against the emulators

`npm run emulators` here, then `rin.firebase.emulator=127.0.0.1` in `local.properties` (debug builds only) and, with
the phone on USB, `adb reverse tcp:8080 tcp:8080` and `adb reverse tcp:9099 tcp:9099`. The three `rin.firebase.*`
values must still be set, with `rin.firebase.projectId=demo-rinalarm` to match the emulators (the app id and key can
be any non-empty text).
