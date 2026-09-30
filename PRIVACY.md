# RinAlarm privacy notice

Last updated: 1 October 2026. Applies to RinAlarm 0.1 for Android. ภาษาไทย: PRIVACY.th.md

RinAlarm works entirely on your phone. It has no internet permission, no account, no ads, no analytics and no crash reporting. Nothing you say or do in the app leaves your phone.

## What the app keeps on your phone

- Your alarms: time, repeat days, label, game and snooze settings.
- Settings: whether setup is finished, No pouting, and a rest or sick day waiting for the next alarm (it clears after that alarm, or after 24 hours).
- A ring log: when each alarm rang and how it ended (dismissed, snoozed, game passed), with the phone's state at that moment (screen on or off, battery saver, Do Not Disturb, alarm volume, permissions). Diagnostics uses it to show whether alarms ring on time.
- If you set up a QR sticker: its random code and image.

All of this stays in the app's private storage. Android backup is turned off for RinAlarm, so none of it is copied to a cloud backup or to a new phone.

## Microphone (Repeat after Rin)

The app asks for the microphone only when you choose the Repeat after Rin game. The mic is on only while the game listens, and the screen shows it. Speech recognition (Vosk) runs on your phone: the sound is used in memory to check the words, then discarded. It is never recorded, saved or sent anywhere.

## Camera (QR sticker)

The camera is used only by the QR sticker game, which is turned off in this version. When it is on, the camera opens only after you tap Scan, and each frame is checked on your phone (Google ML Kit, built into the app) and discarded. ML Kit normally sends anonymous usage statistics to Google; RinAlarm removes the internet permission, so it cannot.

## Rin's voice and lines

Rin is an AI character. Her lines were written in advance with the help of AI and reviewed by the developer, and her voice was generated once with ElevenLabs. All of it is built into the app. Nothing is generated while you use the app, and none of your data is sent to any AI service.

## Sharing

The app never shares anything by itself. Two things happen only when you tap them:

- Diagnostics, Copy report: puts a text report on your clipboard (phone model, Android and app version, check results, recent rings). You decide where to paste it.
- QR setup, Share: sends the sticker image to the app you pick, so you can print it.

## Deleting your data

Uninstall RinAlarm, or clear its storage (Android Settings, Apps, RinAlarm, Storage, Clear storage). Either removes everything above. Deleting an alarm removes that alarm; its old rows in the ring log stay until you clear storage. There is no account, so there is nothing to delete anywhere else.

## Children

RinAlarm is meant for adults (18 and over) and is not directed at children.

## Changes and contact

Any change to this notice is published here with a new date, and every earlier version stays in the repository history. Questions or requests: open an issue at https://github.com/Earthkodyai/rin/issues.
