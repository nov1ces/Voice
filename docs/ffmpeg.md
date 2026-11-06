# FFmpeg audio decoding

Voice uses the Media3 FFmpeg renderer by default.
Choose a decoder in Settings → Listening → Audio decoding.
FFmpeg uses software decoding. System decoding prefers hardware codecs and keeps system software codecs as fallbacks.
Your choice is saved and applies during playback. Your position, speed, and play state stay the same.
System decoding cannot play formats that the device does not support.
With FFmpeg selected, AAC and ALAC audio in M4B files use software decoding when the decoder supports the stream.
The Android codec renderer remains available for formats that FFmpeg does not support.
Decoder fallback applies within the Android codec renderer. It does not recover from every FFmpeg error.
Protected audio files are not supported by FFmpeg.

The module contains audio decoders. Media3 still reads the containers and chapter data.
Supported decoders include AAC, ALAC, MP3, FLAC, Vorbis, Opus, AMR, AC-3, E-AC-3, DTS, and TrueHD.
AC-3, E-AC-3, DTS, and TrueHD need a container that Media3 supports, such as Matroska.
This change does not add WMA, APE, or AIFF containers.
The file scan also accepts `.amr` and `.adts` files.

## Build

Use Linux or macOS. Install the Android SDK, a JDK, CMake, Make, curl, tar, and xz.
Use the JDK version from `gradle/libs.versions.toml`.
Set `ANDROID_HOME` to the SDK directory. Keep the source path free of spaces.

```sh
sdkmanager "ndk;28.2.13676358" "cmake;3.22.1"
./scripts/build_ffmpeg.sh
./gradlew :app:assembleFreeDebug
```

The script downloads FFmpeg 6.1.6 and checks its SHA-256 value.
It builds each ABI from the same source archive.
The result includes `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64` libraries.
The JNI library supports 16 KB memory pages.
Set `FFMPEG_JOBS` to limit the number of build jobs. The default limit is eight.
Set `ANDROID_NDK_HOME` to use a separate NDK directory.
The script caches the source archive and native libraries under `core/ffmpeg/build/ffmpeg`.
Run it again after a script or NDK change. Gradle fails if native libraries are missing.
CI runs the script through the shared build setup action.

## Checks

```sh
./gradlew :core:scanner:testDebugUnitTest :core:playback:impl:testDebugUnitTest
./gradlew :core:playback:impl:assembleDebugAndroidTest
./gradlew :core:playback:impl:connectedDebugAndroidTest
```

The device tests need an Android device or emulator.
They play AAC and ALAC M4B samples until playback ends.
They also check that the active audio decoder is FFmpeg.
The scan tests check the duration of AAC M4B, ALAC M4B, and ADTS samples.
The samples contain a generated two-second tone. They do not contain third-party recordings.

## Source and licenses

The Media3 files come from AndroidX Media 1.10.1, commit `5fb306449733dd71595700c1227ad6087578c559`.
They keep their Apache 2.0 license headers. See `core/ffmpeg/LICENSE-AndroidX`.
FFmpeg 6.1.6 uses LGPL 2.1 or later with this build configuration.
See `core/ffmpeg/LICENSE-FFmpeg`. GPL and nonfree components are disabled.
The APK also contains these license texts in `assets/licenses`.

The exact FFmpeg source archive is <https://ffmpeg.org/releases/ffmpeg-6.1.6.tar.xz>.
Its SHA-256 value is `d4fcb164028dd3beee5d92c0ac72e46aac6973c75ea12dc14de07bf8f407370a`.
A release must include this archive and the Voice source archive.
Use this source and the build steps above to build a modified FFmpeg library and APK.
The native build links FFmpeg into `libffmpegJNI.so`. It does not require a separate FFmpeg app.

## Fork releases

The `Fork release` workflow builds only the free variant.
Set `GH_SIGNING_KEYSTORE` to a base64-encoded keystore in the repository secrets.
Also set `GH_SIGNING_STORE_PASSWORD`, `GH_SIGNING_KEY_ALIAS`, and `GH_SIGNING_KEY_PASSWORD`.
Keep the same key for future updates.
Dispatch the workflow from `main` with a version name and an increasing version code.
It attaches the APK, checksums, and both source archives to the GitHub release.
The fork key differs from the official app key. It cannot update the official app.
Keep existing audiobook data before changing the installed app.
