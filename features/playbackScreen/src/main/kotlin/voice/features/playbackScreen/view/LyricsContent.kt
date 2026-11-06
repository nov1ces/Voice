@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.playbackScreen.view

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.time.Duration
import voice.core.data.BookId
import voice.core.strings.R
import voice.core.ui.MaxContentWidth
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.features.playbackScreen.BookPlayViewState
import voice.features.playbackScreen.lyrics.LyricsDocument

@Composable
internal fun LyricsContent(
  lyrics: LyricsDocument,
  viewState: BookPlayViewState,
  bookId: BookId,
  clock: () -> Float,
  contentPadding: PaddingValues,
  onLineClick: (Long) -> Unit,
  onSeek: (Duration) -> Unit,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  var follow by remember(lyrics) { mutableStateOf(true) }
  val dragged by listState.interactionSource.collectIsDraggedAsState()
  LaunchedEffect(dragged) { if (dragged) follow = false }
  val manualScroll =
    remember(lyrics) {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (source == NestedScrollSource.UserInput) follow = false
          return Offset.Zero
        }
      }
    }
  val position =
    rememberLyricsPosition(
      lyrics,
      viewState.lyricsPositionMs,
      viewState.playing,
      viewState.playbackSpeed,
      continuousLyrics = viewState.continuousLyrics,
    )
  val activeIndex by
    remember(lyrics, position) {
      derivedStateOf { lyrics.currentLineIndex(position.value) }
    }
  val followIndex by
    remember(lyrics, position) {
      derivedStateOf { lyrics.followLineIndex(position.value) }
    }
  val onFollow = remember(lyrics) { { follow = true } }
  val viewportHeight by remember { derivedStateOf { listState.layoutInfo.viewportSize.height } }
  LaunchedEffect(lyrics, followIndex, follow, viewportHeight) {
    if (follow && lyrics.lines.isNotEmpty() && viewportHeight > 0) {
      val targetIndex = followIndex.coerceAtLeast(0)
      val offset = -(viewportHeight * 0.28F).toInt()
      if (abs(targetIndex - listState.firstVisibleItemIndex) > 30) {
        listState.scrollToItem(targetIndex, offset)
      } else {
        listState.animateScrollToItem(targetIndex, offset)
      }
    }
  }
  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .padding(contentPadding)
        .wrapContentWidth()
        .widthIn(max = MaxContentWidth)
        .padding(horizontal = 24.dp),
  ) {
    val compact =
      maxHeight < 320.dp + 40.dp * (LocalDensity.current.fontScale - 1F).coerceAtLeast(0F)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
      if (!compact)
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = viewState.title,
            style = MaterialTheme.typography.titleMedium.bookText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1F),
          )
          if (!follow) {
            TextButton(onClick = { follow = true }) {
              Text(stringResource(R.string.playback_lyrics_follow))
            }
          }
        }
      BoxWithConstraints(Modifier.weight(1F).fillMaxWidth()) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize().nestedScroll(manualScroll),
          contentPadding = PaddingValues(top = maxHeight * 0.25F, bottom = maxHeight * 0.6F),
          verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          itemsIndexed(lyrics.lines, key = { index, _ -> index }) { index, line ->
            LyricText(
              line = line,
              active = index == activeIndex,
              position = position,
              onFollow = onFollow,
              onLineClick = onLineClick,
              continuousLyrics = viewState.continuousLyrics,
            )
          }
        }
        if (compact && !follow) {
          FilledTonalIconButton(
            onClick = { follow = true },
            modifier = Modifier.align(Alignment.TopEnd),
          ) {
            Icon(
              VoiceIcons.Sync,
              contentDescription = stringResource(R.string.playback_lyrics_follow),
            )
          }
        }
      }
      MediaControlsDirection {
        Column {
          if (!compact)
            SeekSection(
              playedTime = viewState.playedTime,
              duration = viewState.duration,
              playing = viewState.playing,
              clock = clock,
              onSeek = onSeek,
              jumpBack = null,
              unavailable = viewState.unavailable,
              bookPercent = null,
              onJumpBack = {},
              onJumpBackExpire = {},
              modifier = Modifier.fillMaxWidth(),
            )
          TransportControls(
            bookId = bookId,
            playing = viewState.playing,
            skipSeconds = viewState.skipSeconds,
            onRewindClick = onRewindClick,
            onPlayClick = onPlayClick,
            onFastForwardClick = onFastForwardClick,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(72.dp),
          )
        }
      }
    }
  }
}
