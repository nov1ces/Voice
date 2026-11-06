package voice.features.playbackScreen.lyrics

import androidx.compose.runtime.Immutable

@Immutable
data class LyricSegment(
  val timeMs: Long,
  val endTimeMs: Long?,
  val startIndex: Int,
  val endIndex: Int,
)

@Immutable
data class LyricLine(
  val timeMs: Long,
  val text: String,
  val endTimeMs: Long? = null,
  val segments: List<LyricSegment> = emptyList(),
) {
  fun activeSegments(positionMs: Long): List<LyricSegment> =
    if (positionMs < timeMs || (endTimeMs != null && positionMs >= endTimeMs)) emptyList()
    else
      segments.filter {
        positionMs >= it.timeMs && (it.endTimeMs == null || positionMs < it.endTimeMs)
      }
}

@Immutable
data class LyricsDocument(val lines: List<LyricLine>) {
  fun currentLineIndex(positionMs: Long): Int {
    val index = followLineIndex(positionMs)
    if (index < 0) return -1
    val end = lines[index].endTimeMs
    return if (end != null && positionMs >= end) -1 else index
  }

  fun followLineIndex(positionMs: Long): Int {
    var low = 0
    var high = lines.size
    while (low < high) {
      val middle = (low + high) ushr 1
      if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle
    }
    return low - 1
  }
}

internal fun parseLyrics(text: String, checkCancelled: () -> Unit = {}): LyricsDocument? {
  val groups = mutableListOf<RawLyric>()
  var previous: RawLyric? = null
  val endings = mutableMapOf<Long, Long>()
  var offset = 0L
  var cues = 0
  var markers = 0
  for (source in text.lineSequence()) {
    checkCancelled()
    val line = source.trim().removePrefix("\uFEFF")
    if (line.isEmpty() || isCompatibilityField(line)) continue
    val offsetMatch = offsetPattern.matchEntire(line)
    if (offsetMatch != null) {
      offsetMatch.groupValues[1].toLongOrNull()?.let { offset = it }
      continue
    }
    if (metadataPattern.matches(line)) continue
    var contentStart = 0
    val times = mutableListOf<Long>()
    while (true) {
      while (contentStart < line.length && line[contentStart].isWhitespace()) contentStart++
      val match =
        timestampPattern.find(line, contentStart)?.takeIf {
          it.range.first == contentStart && it.value.startsWith('[')
        } ?: break
      checkCancelled()
      if (++markers > MAX_SEGMENTS) return null
      contentStart = match.range.last + 1
      parseTimestamp(match)?.let { times += it }
      if (cues + times.size > MAX_CUES) return null
    }
    if (contentStart == line.length && times.isNotEmpty()) {
      previous?.times?.forEach { time ->
        if (times.last() >= time) endings[time] = times.last()
      }
      previous = null
      continue
    }
    if (times.isEmpty() && contentStart > 0) {
      previous = null
      continue
    }
    if (times.isEmpty() && previous == null) continue
    val content = line.substring(contentStart).trimEnd()
    val words = StringBuilder()
    val boundaries = mutableListOf<Boundary>()
    var start = 0
    var explicitEnd: Long? = null
    for (match in timestampPattern.findAll(content)) {
      checkCancelled()
      if (++markers > MAX_SEGMENTS) return null
      words.append(content, start, match.range.first)
      if (words.length > MAX_CUE_CHARACTERS) return null
      val time = parseTimestamp(match)
      if (time != null) {
        if (match.range.last == content.lastIndex && match.value.startsWith('[')) {
          explicitEnd = time
        } else {
          boundaries += Boundary(time, words.length)
        }
      }
      start = match.range.last + 1
    }
    words.append(content, start, content.length)
    if (words.length > MAX_CUE_CHARACTERS) return null
    val parsed = RawText(words.toString(), boundaries, explicitEnd)
    if (times.isEmpty()) {
      val group = previous ?: continue
      if (group.texts.sumOf { it.text.length + 1 } + parsed.text.length > MAX_CUE_CHARACTERS) {
        return null
      }
      group.texts += parsed
    } else {
      cues += times.size
      val group = RawLyric(times, mutableListOf(parsed))
      groups += group
      previous = group
    }
  }
  val rawCues = ArrayList<Pair<Long, RawLyric>>(cues)
  for (group in groups) {
    checkCancelled()
    group.text = group.texts.joinToString("\n") { it.text }
    group.times.forEach { rawCues += it to group }
  }
  rawCues.sortBy { it.first }
  val lines = ArrayList<LyricLine>(cues)
  var segmentCount = 0
  var cueIndex = 0
  while (cueIndex < rawCues.size) {
    checkCancelled()
    val time = rawCues[cueIndex].first
    var nextIndex = cueIndex + 1
    while (nextIndex < rawCues.size && rawCues[nextIndex].first == time) nextIndex++
    val nextStart = rawCues.getOrNull(nextIndex)?.first
    var end = endings[time]
    for (index in cueIndex until nextIndex) {
      checkCancelled()
      if (end != null) break
      end =
        rawCues[index].second.texts.firstNotNullOfOrNull { raw ->
          raw.endTimeMs?.takeIf { it >= time }
        }
    }
    if (end == null) end = nextStart
    while (cueIndex < nextIndex) {
      val group = rawCues[cueIndex++].second
      checkCancelled()
      val segments = mutableListOf<LyricSegment>()
      var textStart = 0
      for (raw in group.texts) {
        checkCancelled()
        if (raw.boundaries.isNotEmpty()) {
          var segmentStart = 0
          var segmentTime = time
          for (boundary in raw.boundaries) {
            checkCancelled()
            if (boundary.timeMs <= segmentTime || (end != null && boundary.timeMs >= end)) continue
            if (boundary.textIndex > segmentStart) {
              segments +=
                LyricSegment(
                  segmentTime,
                  boundary.timeMs,
                  textStart + segmentStart,
                  textStart + boundary.textIndex,
                )
            }
            segmentStart = boundary.textIndex
            segmentTime = boundary.timeMs
          }
          if (segmentStart < raw.text.length) {
            segments +=
              LyricSegment(
                segmentTime,
                end,
                textStart + segmentStart,
                textStart + raw.text.length,
              )
          }
        }
        textStart += raw.text.length + 1
      }
      segmentCount += segments.size
      if (segmentCount > MAX_SEGMENTS) return null
      if (group.text.isNotBlank()) {
        lines +=
          LyricLine(
            timeMs = adjustedTime(time, offset),
            text = group.text,
            endTimeMs = end?.let { adjustedTime(it, offset) },
            segments =
              segments.map { segment ->
                segment.copy(
                  timeMs = adjustedTime(segment.timeMs, offset),
                  endTimeMs = segment.endTimeMs?.let { adjustedTime(it, offset) },
                )
              },
          )
      }
    }
  }
  if (lines.isEmpty()) return null
  lines.sortBy { it.timeMs }
  val merged = ArrayList<LyricLine>(lines.size)
  var index = 0
  while (index < lines.size) {
    checkCancelled()
    val first = lines[index++]
    val words = StringBuilder(first.text)
    val seen = mutableSetOf(first.text)
    val segments = first.segments.toMutableList()
    var end = first.endTimeMs
    while (index < lines.size && lines[index].timeMs == first.timeMs) {
      checkCancelled()
      val next = lines[index++]
      end = if (end == null || next.endTimeMs == null) null else end.coerceAtLeast(next.endTimeMs)
      if (seen.add(next.text)) {
        if (words.length + next.text.length + 1 > MAX_CUE_CHARACTERS) return null
        val textStart = words.length + 1
        words.append('\n').append(next.text)
        segments +=
          next.segments.map {
            it.copy(startIndex = it.startIndex + textStart, endIndex = it.endIndex + textStart)
          }
      }
    }
    val nextStart = lines.getOrNull(index)?.timeMs
    if (end == null) end = nextStart
    merged +=
      first.copy(
        timeMs = first.timeMs,
        text = words.toString(),
        endTimeMs = end,
        segments =
          segments.map { segment ->
            val segmentEnd = segment.endTimeMs?.let { end?.coerceAtMost(it) ?: it } ?: end
            segment.copy(endTimeMs = segmentEnd)
          },
      )
  }
  return LyricsDocument(merged)
}

private fun adjustedTime(time: Long, offset: Long): Long =
  if (offset > 0 && time > Long.MAX_VALUE - offset) Long.MAX_VALUE
  else (time + offset).coerceAtLeast(0)

private fun parseTimestamp(match: MatchResult): Long? {
  val group = if (match.groupValues[1].isEmpty()) 4 else 1
  val minutes = match.groupValues[group].toLongOrNull() ?: return null
  val seconds = match.groupValues[group + 1].toLongOrNull() ?: return null
  if (seconds >= 60 || minutes > Long.MAX_VALUE / 60_000 - 1) return null
  val fraction = match.groupValues[group + 2].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
  return minutes * 60_000 + seconds * 1000 + fraction
}

private fun isCompatibilityField(line: String): Boolean {
  if (!line.startsWith("[awlrc:", ignoreCase = true) || !line.endsWith(']')) return false
  var recognized = false
  for (field in line.substring(7, line.lastIndex).split(',')) {
    val colon = field.indexOf(':')
    if (colon <= 0 || !base64Pattern.matches(field.substring(colon + 1))) return false
    if (field.substring(0, colon).lowercase() in compatibilityNames) recognized = true
  }
  return recognized
}

private data class RawLyric(
  val times: List<Long>,
  val texts: MutableList<RawText>,
  var text: String = "",
)

private data class RawText(val text: String, val boundaries: List<Boundary>, val endTimeMs: Long?)

private data class Boundary(val timeMs: Long, val textIndex: Int)

private const val MAX_CUE_CHARACTERS = 4096
private const val MAX_CUES = 250_000
private const val MAX_SEGMENTS = 1_000_000
private val timestampPattern =
  Regex("(?:\\[(\\d+):(\\d{1,2})(?:[.,](\\d{1,6}))?]|<(\\d+):(\\d{1,2})(?:[.,](\\d{1,6}))?>)")
private val offsetPattern = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
private val metadataPattern =
  Regex("\\[(?:ar|al|au|by|id|length|re|ti|ve):.*]", RegexOption.IGNORE_CASE)
private val base64Pattern = Regex("[A-Za-z0-9+/]+={0,2}")
private val compatibilityNames = setOf("lrc", "awlrc", "tlrc", "rlrc")
