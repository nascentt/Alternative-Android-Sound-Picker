# Alternative Sound Picker

An Android sound picker for apps using the standard `android.intent.action.RINGTONE_PICKER` intent. Application ID: `com.nascent.soundpicker`.

## Features

- Lists available system tones and device audio, with selection, preview, and volume control.
- Can browse for audio files not shown in the list.
- Returns the selected sound to the requesting app.

## Build

Requires JDK 17+ and Android SDK 37.

```sh
./gradlew assembleDebug
./gradlew assembleRelease
```

APKs are written under `app/build/outputs/apk/`. Release signing credentials are local and are not included; without local signing configuration, the release APK is unsigned.
