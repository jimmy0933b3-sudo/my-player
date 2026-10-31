package com.jimmy0933.myplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DecoderSettingsDialog(
    settings: DecoderPreferences,
    onChange: (DecoderPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("解碼器設定") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("影片解碼（本機）", style = MaterialTheme.typography.titleSmall)
                Text(
                    "優先用手機硬體解碼，減少處理負擔；無法解碼時才嘗試備援。原始影片畫質不會被重新壓縮。",
                    style = MaterialTheme.typography.bodySmall,
                )
                DecoderModeRow("系統自動", VideoDecoderPreference.SYSTEM, settings, onChange)
                DecoderModeRow("硬體優先（預設，清晰流暢）", VideoDecoderPreference.HARDWARE_FIRST, settings, onChange)
                DecoderModeRow("軟體優先（相容性）", VideoDecoderPreference.SOFTWARE_FIRST, settings, onChange)

                HorizontalDivider()
                DecoderSwitchRow(
                    "解碼器初始化失敗時切換備用解碼器",
                    "使用 Media3 的低優先級解碼器備援。若手機沒有其他支援的解碼器，仍可能播放失敗。",
                    settings.decoderFallback,
                ) { onChange(settings.copy(decoderFallback = it)) }
                DecoderSwitchRow(
                    "播放過程解碼失敗時自動重試",
                    "偵測解碼錯誤後，以軟體優先模式重建播放器並從原進度續播，最多嘗試一次。",
                    settings.recoverOnPlaybackError,
                ) { onChange(settings.copy(recoverOnPlaybackError = it)) }
                HorizontalDivider()
                Text("音訊解碼", style = MaterialTheme.typography.titleSmall)
                DecoderSwitchRow(
                    "軟體音訊解碼優先",
                    "優先使用手機已安裝的軟體音訊解碼器；沒有可用軟解時仍使用其他支援的解碼器。",
                    settings.softwareAudioFirst,
                ) { onChange(settings.copy(softwareAudioFirst = it)) }
                HorizontalDivider()
                Text("其他功能支援情況", style = MaterialTheme.typography.titleSmall)
                Text(
                    "目前已支援本機影片硬／軟解優先順序與自動備援。網路串流解碼設定適用於日後新增串流播放時。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "MX Player 專用 HW+、自訂 FFmpeg 解碼核心、CPU 核心數、手動色彩格式及反交錯尚未支援，" +
                        "因為它們需要額外原生播放器或圖像處理實作，不會提供無效的假開關。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "部分 Android 系統解碼器會忽略軟硬體分類，或不提供特定格式。切換設定後，下次開啟影片生效。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun DecoderModeRow(
    label: String,
    mode: VideoDecoderPreference,
    settings: DecoderPreferences,
    onChange: (DecoderPreferences) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = settings.video == mode,
            onClick = { onChange(settings.copy(video = mode)) },
        )
        TextButton(onClick = { onChange(settings.copy(video = mode)) }) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DecoderSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
