#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="$repo_dir/core/ffmpeg/build/ffmpeg"
version=6.1.6
archive_sha256=d4fcb164028dd3beee5d92c0ac72e46aac6973c75ea12dc14de07bf8f407370a
ndk_version=28.2.13676358
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ndk_dir="${ANDROID_NDK_HOME:-${sdk_dir}/ndk/${ndk_version}}"
host="${FFMPEG_HOST_PLATFORM:-linux-x86_64}"
if [[ "$(uname -s)" == Darwin ]]; then host=darwin-x86_64; fi
toolchain="$ndk_dir/toolchains/llvm/prebuilt/$host/bin"
if [[ ! -x "$toolchain/clang" ]]; then
  echo "Install NDK $ndk_version. Set ANDROID_HOME or ANDROID_NDK_HOME." >&2
  exit 1
fi
jobs="${FFMPEG_JOBS:-$(getconf _NPROCESSORS_ONLN)}"
if (( jobs > 8 )); then jobs=8; fi
hash_stream() {
  if command -v sha256sum >/dev/null; then sha256sum; else shasum -a 256; fi | cut -d ' ' -f 1
}
mkdir -p "$output_dir"
archive="$output_dir/ffmpeg-$version.tar.xz"
if [[ ! -f "$archive" ]]; then
  curl --fail --location --retry 3 "https://ffmpeg.org/releases/ffmpeg-$version.tar.xz" -o "$archive.tmp"
  mv "$archive.tmp" "$archive"
fi
if [[ "$(hash_stream < "$archive")" != "$archive_sha256" ]]; then
  echo "FFmpeg source archive checksum failed." >&2
  exit 1
fi
source_dir="$output_dir/ffmpeg-$version"
if [[ ! -d "$source_dir" ]]; then tar -xf "$archive" -C "$output_dir"; fi

decoders=(aac aac_latm alac mp3 mp3float mp2 mp2float ac3 eac3 dca truehd mlp vorbis opus amrnb amrwb flac pcm_mulaw pcm_alaw)
options=(
  --target-os=android --enable-cross-compile --enable-static --disable-shared
  --enable-pic --disable-autodetect --disable-doc --disable-programs
  --disable-everything --disable-avdevice --disable-avformat --disable-swscale
  --disable-postproc --disable-avfilter --disable-network --disable-symver
  --disable-v4l2-m2m --disable-vulkan --enable-swresample
  --disable-gpl --disable-nonfree
)
for decoder in "${decoders[@]}"; do options+=("--enable-decoder=$decoder"); done

for abi in armeabi-v7a arm64-v8a x86 x86_64; do
  case "$abi" in
    armeabi-v7a) arch=arm; cpu=armv7-a; triple=armv7a-linux-androideabi; extra=(--extra-cflags=-mfloat-abi=softfp) ;;
    arm64-v8a) arch=aarch64; cpu=armv8-a; triple=aarch64-linux-android; extra=() ;;
    x86) arch=x86; cpu=i686; triple=i686-linux-android; extra=(--disable-asm) ;;
    x86_64) arch=x86_64; cpu=x86-64; triple=x86_64-linux-android; extra=(--disable-asm) ;;
  esac
  prefix="$output_dir/$abi"
  stamp="$prefix/build.sha256"
  build_id="$(cat "$0" "$ndk_dir/source.properties" | hash_stream)"
  if [[ -f "$stamp" && "$(cat "$stamp")" == "$build_id" &&
    -s "$prefix/lib/libavcodec.a" && -s "$prefix/lib/libswresample.a" &&
    -s "$prefix/lib/libavutil.a" && -s "$prefix/include/libavcodec/avcodec.h" &&
    -s "$prefix/include/libswresample/swresample.h" && -s "$prefix/include/libavutil/avutil.h" ]]; then
    echo "FFmpeg $version for $abi is ready."
    continue
  fi
  work_dir="$output_dir/work-$abi"
  mkdir -p "$work_dir"
  (
    cd "$work_dir"
    "$source_dir/configure" "${options[@]}" "${extra[@]}" \
      --prefix="$prefix" --arch="$arch" --cpu="$cpu" \
      --cc="$toolchain/${triple}28-clang" --cxx="$toolchain/${triple}28-clang++" \
      --ar="$toolchain/llvm-ar" --nm="$toolchain/llvm-nm" \
      --ranlib="$toolchain/llvm-ranlib" --strip="$toolchain/llvm-strip" \
      --extra-cflags="-fPIC -ffile-prefix-map=$output_dir=." \
      --extra-ldflags=-Wl,-z,max-page-size=16384
    make -j"$jobs"
    make install-libs install-headers
  )
  echo "$build_id" > "$stamp"
done
