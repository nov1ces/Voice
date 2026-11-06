## What's New

### Synced lyrics 🎵

- Put an LRC file beside the audio file, with the same name, to read along while listening.
- Open a large-text view with the current line highlighted. Tap a line to jump to that point.
- Browse the lyrics at your own pace, then select **Follow playback** to return to the current line.
- Long episodes show only the lines near the screen. Compact windows keep the lyrics and playback controls visible.
- The lyrics button appears only after valid lyrics load. Switching audio files clears the previous lyrics.

LRC files can use UTF-8, UTF-16 with a byte order mark, or GB18030.
Multiple timestamps, timing offsets, and translated lines at the same time are supported.
Embedded lyrics and plain text files are not supported.
The limits are 32 MiB per file, 250,000 timestamps, and 4,096 characters per cue.

### Audio playback

Choose FFmpeg software decoding or system decoding in Settings. This build retains AAC and ALAC support in M4B files.
It includes native libraries for ARM, ARM64, x86, and x86_64.

## Installation

This free build uses the existing fork signing key. It can update earlier fork releases without removing the library.
It cannot update the official app.

The release includes the signed APK, checksums, Voice source, and FFmpeg source.
Read `docs/ffmpeg.md` for build steps and codec limits.
