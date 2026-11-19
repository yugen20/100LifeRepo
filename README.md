# 100Life

Android app for tracking blood glucose and daily steps on the phone. Readings live in a SQLCipher database. Name, IC number, doctor details and reminder settings are encrypted with the Android Keystore. Android cloud backup and device-to-device transfer are off.

Package name: `my.toolmentum.hundredlife`. This is the Play application id and should not change after the first upload.

## Requirements

- JDK 17 or newer (the Gradle toolchain file requests 21)
- Android SDK platform 36.1 and matching build-tools
- This repo's Gradle wrapper (`./gradlew`)

## Debug build

```bash
./gradlew :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Unit tests

```bash
./gradlew :app:testDebugUnitTest
```

On-device tests (package name check) need a device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Release signing

Release builds run R8 (minify) and resource shrinking. Signing is optional. If `keystore.properties` is missing, `bundleRelease` still produces an unsigned AAB. Do not commit a keystore or `keystore.properties`.

1. Create an upload keystore. Keep the file outside the repo, or anywhere that matches the ignore rules (`*.jks`, `*.keystore`):

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

2. Create `keystore.properties` in the project root:

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=your-store-password
keyAlias=upload
keyPassword=your-key-password
```

`storeFile` may be an absolute path, or a path relative to the project root (the directory that contains `keystore.properties`).

3. Build the Play App Bundle:

```bash
./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`.

`versionName` is `1.0.0`. `versionCode` is `1`.

## Notes for this release

- There are no production installs. A leftover unencrypted `health.db` from a dev build is deleted and created again as an empty encrypted database. An encrypted database is not deleted when its key cannot be read.
- If the Android Keystore cannot encrypt settings, the app stops and does not write those fields to ordinary SharedPreferences.
- The CSV attachment still contains the name, IC and readings the user chooses to email. The file name and the email subject do not include the IC or any readings.
