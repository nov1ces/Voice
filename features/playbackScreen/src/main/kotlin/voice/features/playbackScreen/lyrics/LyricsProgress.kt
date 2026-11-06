package voice.features.playbackScreen.lyrics

internal fun lyricSegmentProgress(segment: LyricSegment, positionMs: Long): Float {
  if (positionMs < segment.timeMs) return 0F
  val end = segment.endTimeMs ?: return 1F
  if (end <= segment.timeMs || positionMs >= end) return 1F
  return (positiveDifference(positionMs, segment.timeMs) / positiveDifference(end, segment.timeMs))
    .toFloat()
}

internal fun interpolatedLyricsPosition(
  samplePositionMs: Long,
  sampleTimeNanos: Long,
  nowNanos: Long,
  playing: Boolean,
  speed: Float,
): Long {
  if (!playing) return samplePositionMs.coerceAtLeast(0)
  val elapsedMs =
    ((nowNanos.toDouble() - sampleTimeNanos.toDouble()) / 1_000_000).coerceIn(0.0, 1000.0)
  val rate = if (speed.isFinite() && speed > 0F) speed else 1F
  val position = samplePositionMs.coerceAtLeast(0)
  val advance = (elapsedMs * rate).toLong()
  return if (position > Long.MAX_VALUE - advance) Long.MAX_VALUE else position + advance
}

private fun positiveDifference(end: Long, start: Long): Double {
  val difference = end - start
  return if (difference >= 0) difference.toDouble() else end.toDouble() - start.toDouble()
}
