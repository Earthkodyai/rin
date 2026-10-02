# Play Console: steps for the closed test (tasks 6.5–6.6, D32)

The user does these in Play Console (play.google.com/console, app "RinAlarm", package `io.github.earthkodyai.rinalarm`). Claude can fill forms through Claude in Chrome with the user's approval for each submit. Texts and answers: [listing.md](listing.md), [app-content.md](app-content.md). Graphics: `docs/store/graphics/`.

## 1. Store listing (Grow users → Store presence → Main store listing)

1. App name `Rin: Anime Wake-up Alarm`, short and full description from listing.md (English).
2. App icon `graphics/icon-512.png` · Feature graphic `graphics/feature-1024x500.png` · Phone screenshots `graphics/screens/*.png` in file order (1 home day, 2 home night, 3–6 games, 7 editor; 1220×2440, 2:1).
3. **Translations → Add → Thai:** the Thai texts from listing.md (the graphics stay the same).
4. Store settings: category **Tools** (where alarm clocks sit), contact email (your choice), website https://earthkodyai.github.io/rin/.

## 2. App content (Policy and programs → App content)

Every section in app-content.md, in order. The foreground-service declaration needs the video first (record a test ring from the lock screen through a game, upload unlisted to YouTube).

## 3. Closed testing track (Test and release → Testing → Closed testing)

1. Create track (or use "Closed testing – Alpha"). **Countries: Thailand.**
2. Testers: **Google Groups** → the group's address (testers.md). Feedback URL or email: the Google Form link.
3. Copy the **opt-in link** into the tester guide (testers.md).

## 4. App signing with your own key (first release only, D32)

On "Create new release" → App integrity → **Change signing key / Use a different key** → **Export and upload a key from Java keystore**. Download **pepk.jar** and **encryption_public_key.pem** into `D:\RinAlarm-keys\`, then run in a terminal (it asks for the keystore and key passwords; type them yourself):

```bash
java -jar D:/RinAlarm-keys/pepk.jar --keystore=D:/RinAlarm-keys/rinalarm-release.jks --alias=rinalarm --output=D:/RinAlarm-keys/pepk-out.zip --include-cert --rsa-aes-encryption --encryption-key-path=D:/RinAlarm-keys/encryption_public_key.pem
```

(`java` is Android Studio's: `"C:/Program Files/Android/Android Studio/jbr/bin/java"`.) Upload `pepk-out.zip`. The same key also stays the **upload key**. Keep pepk-out.zip out of the repo; it can be deleted after.

## 5. The release

1. Upload `app/build/outputs/bundle/release/app-release.aab` (versionCode 1, 1.0.0; `./gradlew bundleRelease` refuses a bundle without Rin's model, voice or music, or with the VRoid sample).
2. Release name `1.0.0`. Release notes (en-US): `First closed test: three wake-up games, Rin's voice, alarm themes, rest and sick days, a tour and practice rounds.`
3. Save → Review release → **Send for review** (Publishing overview). Review usually takes hours to a few days.

## 6. After review

Send the opt-in link with the tester guide. The 14 days count from the 12th opted-in tester; check Dashboard → "Closed test" progress.
