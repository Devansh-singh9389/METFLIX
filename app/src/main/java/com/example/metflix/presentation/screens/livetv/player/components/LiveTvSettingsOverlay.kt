package com.example.metflix.presentation.screens.livetv.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

enum class LiveTvAspectRatio(val title: String, val contentScale: ContentScale) {
    Fit("Fit (Original)", ContentScale.Fit),
    Crop("Zoom / Fill", ContentScale.Crop),
    FillBounds("Stretch", ContentScale.FillBounds)
}

val QualityOptions = listOf(
    "Auto" to 0,
    "1080p" to 1080,
    "720p" to 720,
    "480p" to 480,
    "360p" to 360
)

val SleepTimerOptions = listOf(
    "Off" to 0,
    "15m" to 15,
    "30m" to 30,
    "45m" to 45,
    "60m" to 60,
    "90m" to 90,
    "120m" to 120
)

@Composable
fun LiveTvSettingsOverlay(
    isPresented: Boolean,
    currentAspectRatio: LiveTvAspectRatio,
    currentQualityMaxHeight: Int,
    currentSleepTimerMinutes: Int,
    isStatsVisible: Boolean,
    availableAudioTracks: List<String>,
    selectedAudioTrackIndex: Int,
    availableVideoHeights: List<Int> = emptyList(),
    sourceResolutionLabel: String = "",
    onSelectAspectRatio: (LiveTvAspectRatio) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectSleepTimer: (Int) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onToggleStats: () -> Unit,
    onReloadStream: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return

    BackHandler(onBack = onDismiss)

    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { initialFocusRequester.requestFocus() }
    }

    val isSingleQuality = availableVideoHeights.size <= 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .padding(vertical = 20.dp),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            onClick = {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "Live TV Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 1. Aspect Ratio / Screen Sizing Section
                SettingsSectionHeader(
                    icon = Icons.Default.AspectRatio,
                    title = "Screen Aspect Ratio"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiveTvAspectRatio.entries.forEachIndexed { index, ratio ->
                        val isSelected = currentAspectRatio == ratio
                        val buttonModifier = if (index == 0) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else Modifier

                        OptionPillButton(
                            label = ratio.title,
                            isSelected = isSelected,
                            modifier = buttonModifier.weight(1f),
                            onClick = { onSelectAspectRatio(ratio) }
                        )
                    }
                }

                // 2. Video Quality Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Video Quality",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (sourceResolutionLabel.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Source: $sourceResolutionLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (isSingleQuality) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionPillButton(
                            label = if (sourceResolutionLabel.isNotBlank()) "Original ($sourceResolutionLabel)" else "Auto (Source Feed)",
                            isSelected = true,
                            modifier = Modifier.weight(1f),
                            onClick = {}
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "This channel broadcasts at a single quality feed. Streaming directly with zero transcoding overhead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QualityOptions.forEach { (label, height) ->
                            val isSelected = currentQualityMaxHeight == height
                            OptionPillButton(
                                label = label,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectQuality(height) }
                            )
                        }
                    }

                    val qualityDescription = when (currentQualityMaxHeight) {
                        0 -> "Auto: Dynamically adapts video quality to your internet speed."
                        1080 -> "1080p FHD: Highest visual quality."
                        720 -> "720p HD: High definition with moderate bandwidth usage."
                        480 -> "480p SD: Data Saver — saves up to 80% internet data."
                        360 -> "360p Low: Minimum data usage for low-bandwidth networks."
                        else -> "Adaptive streaming"
                    }

                    Text(
                        text = qualityDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }

                // 3. Audio Track Selection (if multi-audio available)
                if (availableAudioTracks.isNotEmpty()) {
                    SettingsSectionHeader(
                        icon = Icons.Default.Audiotrack,
                        title = "Audio Track / Language"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableAudioTracks.forEachIndexed { index, trackLabel ->
                            val isSelected = selectedAudioTrackIndex == index
                            OptionPillButton(
                                label = trackLabel,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectAudioTrack(index) }
                            )
                        }
                    }
                }

                // 4. Device Hardware & DRM Capabilities
                SettingsSectionHeader(
                    icon = Icons.Default.Info,
                    title = "Device Hardware & DRM Capabilities"
                )

                val hwProfile = remember {
                    com.example.metflix.data.drm.DeviceCapabilityManager().profile
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val drmColor = when (hwProfile.widevineLevel) {
                        com.example.metflix.data.drm.WidevineLevel.L1 -> Color(0xFF66BB6A)
                        com.example.metflix.data.drm.WidevineLevel.L3 -> Color(0xFFFFA726)
                        com.example.metflix.data.drm.WidevineLevel.UNSUPPORTED -> Color(0xFFEF5350)
                    }

                    Box(
                        modifier = Modifier
                            .background(drmColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Widevine: ${hwProfile.widevineLevel.name}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = drmColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (hwProfile.supportsHevc) "HEVC: Yes" else "HEVC: Software",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hwProfile.supportsHevc) Color.White else Color.Gray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (hwProfile.supportsEac3 || hwProfile.supportsAc3) "Dolby Audio: Supported" else "Dolby: Software",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hwProfile.supportsEac3 || hwProfile.supportsAc3) Color(0xFF64B5F6) else Color.Gray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Max Safe: ${hwProfile.maxDecodableHeight}p",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                    }
                }

                // 5. Sleep Timer
                SettingsSectionHeader(
                    icon = Icons.Default.Timer,
                    title = "Sleep Timer (Auto Off)"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SleepTimerOptions.forEach { (label, minutes) ->
                        val isSelected = currentSleepTimerMinutes == minutes
                        OptionPillButton(
                            label = label,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectSleepTimer(minutes) }
                        )
                    }
                }

                // 5. Stream Actions & Diagnostics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            onReloadStream()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Reconnect Stream")
                        }
                    }

                    Button(
                        onClick = onToggleStats,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = if (isStatsVisible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            contentColor = if (isStatsVisible) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(text = if (isStatsVisible) "Hide Stream Stats" else "Show Stream Stats")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun OptionPillButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(38.dp),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            focusedContainerColor = MaterialTheme.colorScheme.secondary,
            focusedContentColor = MaterialTheme.colorScheme.onSecondary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSecondary),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
