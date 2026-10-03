# RinAlarm privacy notice

Last updated: 3 October 2026. Applies to RinAlarm 1.0 for Android. ภาษาไทย: PRIVACY.th.md

RinAlarm works on your phone. Its only network use is the optional tournament leaderboard, described below, and it sends nothing unless you switch posting on. It has no camera permission, no account, no ads, no analytics and no crash reporting. Apart from what you choose to post to the leaderboard, nothing you say or do in the app leaves your phone.

## What the app keeps on your phone

- Your alarms: time, repeat days, label, game and snooze settings.
- Settings: whether setup is finished, No pouting, and a rest or sick day waiting for the next alarm (it clears after that alarm, or after 24 hours).
- A ring log: when each alarm rang and how it ended (dismissed, snoozed, game passed), with the phone's state at that moment (screen on or off, battery saver, Do Not Disturb, alarm volume, permissions). Diagnostics uses it to show whether alarms ring on time.

All of this stays in the app's private storage. Android backup is turned off for RinAlarm, so none of it is copied to a cloud backup or to a new phone.

## Microphone (Repeat after Rin)

The app asks for the microphone only when you choose the Repeat after Rin game. The mic is on only while the game listens, and the screen shows it. Speech recognition (Vosk) runs on your phone: the sound is used in memory to check the words, then discarded. It is never recorded, saved or sent anywhere.

## Rin's page

Rin is drawn by a web page built into the app. It loads only files inside the app. Android's Safe Browsing check, which would look addresses up online through Google Play services, is turned off for it, because there is nothing online to check.

## Rin's voice and lines

Rin is an AI character. Her lines were written in advance with the help of AI and reviewed by the developer, and her voice was generated once with ElevenLabs. All of it is built into the app. Nothing is generated while you use the app, and none of your data is sent to any AI service.

## Tournament leaderboard (optional)

The tournament plays on your phone, and your bests are kept there. Only if you switch on "Post my best online" on the tournament's start page does the app send these to Google Firebase (Firestore, on Google's servers outside Thailand):

- the name you typed, if any, and the university you picked, if any;
- your best levels and time in each tournament game;
- an anonymous ID that Firebase creates for this install, and a Play Integrity check that the request comes from RinAlarm.

Everyone who opens the leaderboard sees the names, universities and scores on it. Opening the leaderboard also signs in with an anonymous ID, even if you never post. Reporting a name sends the reported row and your anonymous ID to the developer, who may delete rows and block IDs that break the rules. Switching posting off deletes your rows from the leaderboard.

The list of universities (the top 50 Thai universities in Webometrics, July 2026) and their logos are built into the app, so picking one sends nothing by itself. The names and logos belong to the universities: RinAlarm is not affiliated with or endorsed by any of them. A university that wants its name or logo removed can ask at the contact below.

## Sharing

The app never shares anything by itself. One thing happens only when you tap it:

- Diagnostics, Copy report: puts a text report on your clipboard (phone model, Android and app version, check results, recent rings). You decide where to paste it.

## Deleting your data

Uninstall RinAlarm, or clear its storage (Android Settings, Apps, RinAlarm, Storage, Clear storage). Either removes everything above that is kept on your phone. Switch off "Post my best online" first: it deletes your leaderboard rows, and clearing storage loses the anonymous ID that owns them. Deleting an alarm removes that alarm; its old rows in the ring log stay until you clear storage. There is no account, so apart from the leaderboard there is nothing to delete anywhere else.

## Children

RinAlarm is meant for adults (18 and over) and is not directed at children.

## Changes and contact

Any change to this notice is published here with a new date, and every earlier version stays in the repository history. Questions or requests: open an issue at https://github.com/Earthkodyai/rin/issues.
