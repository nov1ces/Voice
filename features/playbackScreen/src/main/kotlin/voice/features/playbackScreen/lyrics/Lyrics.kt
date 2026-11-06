package voice.features.playbackScreen.lyrics

import androidx.compose.runtime.Immutable

@Immutable data class LyricLine(val timeMs: Long, val text: String)

@Immutable
data class LyricsDocument(val lines: List<LyricLine>) {
  fun currentLineIndex(positionMs: Long): Int {
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
  val lines = mutableListOf<LyricLine>()
  var offset = 0L
  for (source in text.lineSequence()) {
    checkCancelled()
    val line = source.trim().removePrefix("\uFEFF")
    offsetPattern.matchEntire(line)?.groupValues?.get(1)?.toLongOrNull()?.let { offset = it }
    var end = 0
    val times = mutableListOf<Long>()
    while (true) {
      while (end < line.length && line[end].isWhitespace()) end++
      val match = timestampPattern.find(line, end)?.takeIf { it.range.first == end } ?: break
      checkCancelled()
      end = match.range.last + 1
      val minutes = match.groupValues[1].toLongOrNull() ?: continue
      val seconds = match.groupValues[2].toLongOrNull() ?: continue
      if (seconds >= 60 || minutes > Long.MAX_VALUE / 60_000 - 1) continue
      val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
      times += minutes * 60_000 + seconds * 1000 + fraction
      if (lines.size + times.size > MAX_CUES) return null
    }
    if (times.isEmpty()) continue
    var wordsEnd = line.length
    while (wordsEnd > end && line[wordsEnd - 1].isWhitespace()) wordsEnd--
    if (wordsEnd - end > MAX_CUE_CHARACTERS) return null
    val words = line.substring(end, wordsEnd)
    times.forEach { lines += LyricLine(it, words) }
  }
  if (lines.none { it.text.isNotBlank() }) return null
  for (index in lines.indices) {
    checkCancelled()
    val line = lines[index]
    val adjusted =
      if (offset > 0 && line.timeMs > Long.MAX_VALUE - offset) Long.MAX_VALUE
      else line.timeMs + offset
    lines[index] = line.copy(timeMs = adjusted.coerceAtLeast(0))
  }
  lines.sortBy { it.timeMs }
  val merged = ArrayList<LyricLine>(lines.size)
  var index = 0
  while (index < lines.size) {
    checkCancelled()
    val first = lines[index++]
    val words = linkedSetOf(first.text)
    var characters = first.text.length
    while (index < lines.size && lines[index].timeMs == first.timeMs) {
      checkCancelled()
      val next = lines[index++].text
      if (words.add(next)) {
        characters += next.length + 1
        if (characters > MAX_CUE_CHARACTERS) return null
      }
    }
    merged += if (words.size == 1) first else first.copy(text = words.joinToString("\n"))
  }
  return LyricsDocument(merged)
}

private const val MAX_CUE_CHARACTERS = 4096
private const val MAX_CUES = 250_000
private val timestampPattern = Regex("\\[(\\d+):(\\d{2})(?:[.,](\\d{1,3}))?]")
private val offsetPattern = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
