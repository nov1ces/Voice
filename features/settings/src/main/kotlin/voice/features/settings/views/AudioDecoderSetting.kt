package voice.features.settings.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import voice.core.playback.AudioDecoderMode
import voice.core.strings.R as StringsR

@Composable
internal fun AudioDecoderSetting(
  mode: AudioDecoderMode,
  onSelect: (AudioDecoderMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.selectableGroup()) {
    IslandLabel(stringResource(StringsR.string.settings_playback_audio_decoder_title))
    Spacer(Modifier.height(8.dp))
    AudioDecoderMode.entries.forEach { option ->
      val selected = option == mode
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .selectable(
              selected = selected,
              role = Role.RadioButton,
              onClick = { onSelect(option) },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = IslandContentPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        RadioButton(selected = selected, onClick = null)
        Text(
          text =
            stringResource(
              when (option) {
                AudioDecoderMode.Ffmpeg -> StringsR.string.settings_playback_audio_decoder_ffmpeg
                AudioDecoderMode.System -> StringsR.string.settings_playback_audio_decoder_system
              },
            ),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
  }
}
