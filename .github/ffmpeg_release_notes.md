## What's New

### Karaoke lyrics 🎵

- Ordinary LRC files now sweep across each sentence, using its start and end times.
- The current sentence stays near the center, including wrapped text and the first and last captions.
- Very tall paragraphs start at the top, so their beginning stays readable.
- Set **Word-by-word highlighting** in each book's **Edit** page. Turn it off to highlight whole lines instead.
- Browse freely, then select **Follow playback** to return to the current sentence.

SPL word timestamps keep their exact timing. Sentence-only files use an estimated sweep and do not gain new word timestamps.
Put a matching `.spl` or `.lrc` file beside the audio file. SPL takes priority, with LRC as a fallback.
The parser follows the [SPL timing standard](https://moriafly.com/standards/spl.html), including its compatibility field rules.
Files can use UTF-8, UTF-16 with a byte order mark, or GB18030.
Embedded lyrics and plain text files are not supported.
The limits are 32 MiB per file, 250,000 cues, and 4,096 characters per cue.

### Audio playback

Choose FFmpeg software decoding or system decoding in Settings. This build retains AAC and ALAC support in M4B files.
It includes native libraries for ARM, ARM64, x86, and x86_64.

## Installation

This free build uses the existing fork signing key. It can update earlier fork releases without removing the library.
It cannot update the official app.

The release includes the signed APK, checksums, Voice source, and FFmpeg source.
Read `docs/ffmpeg.md` for build steps and codec limits.
