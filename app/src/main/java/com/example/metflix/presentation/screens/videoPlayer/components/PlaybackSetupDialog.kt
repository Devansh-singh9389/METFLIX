package com.example.metflix.presentation.screens.videoPlayer.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.metflix.data.entities.MovieDetails

val AvailableQualityOptions = listOf("720p", "1080p", "4K UHD", "Auto")

@Composable
fun PlaybackSetupDialog(
    movieDetails: MovieDetails,
    initialSource: String = "debrid",
    initialQuality: String = "720p",
    initialPreferHindi: Boolean = true,
    providerDisplayName: String = "TorBox",
    onConfirm: (source: String, quality: String, preferHindi: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    var selectedSource by remember { mutableStateOf(initialSource) }
    var selectedQuality by remember { mutableStateOf(initialQuality) }
    var preferHindi by remember { mutableStateOf(initialPreferHindi) }

    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Automatically focus the primary Play button for 1-click launch on Android TV remote
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(680.dp)
                .background(Color(0xFF181A20), RoundedCornerShape(20.dp))
                .border(BorderStroke(1.5.dp, Color(0xFF2E323D)), RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Movie Title & Modal Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFFE50914).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Playback Setup",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE50914),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = movieDetails.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF2A2E39))
                )

                // 1. Source Selection: Debrid Cloud vs Free Scrapers
                Text(
                    text = "1. STREAMING SOURCE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFB0B3B8)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Option A: Cloud Debrid
                    val isDebridSelected = selectedSource == "debrid"
                    Surface(
                        onClick = { selectedSource = "debrid" },
                        modifier = Modifier.weight(1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isDebridSelected) Color(0xFF1B3B2B) else Color(0xFF20232B),
                            focusedContainerColor = Color(0xFF2E7D32)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(
                                    if (isDebridSelected) 2.dp else 1.dp,
                                    if (isDebridSelected) Color(0xFF4CAF50) else Color(0xFF333842)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp)
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = if (isDebridSelected) Color(0xFF81C784) else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$providerDisplayName Cloud",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isDebridSelected) Color.White else Color(0xFFD0D0D0)
                                    )
                                }
                                if (isDebridSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Instant cached CDN • Zero buffering",
                                fontSize = 11.sp,
                                color = if (isDebridSelected) Color(0xFFA5D6A7) else Color(0xFF8E929B)
                            )
                        }
                    }

                    // Option B: Free Web Servers
                    val isFreeSelected = selectedSource == "free"
                    Surface(
                        onClick = { selectedSource = "free" },
                        modifier = Modifier.weight(1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isFreeSelected) Color(0xFF37281D) else Color(0xFF20232B),
                            focusedContainerColor = Color(0xFFE65100)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(
                                    if (isFreeSelected) 2.dp else 1.dp,
                                    if (isFreeSelected) Color(0xFFFF9800) else Color(0xFF333842)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp)
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = if (isFreeSelected) Color(0xFFFFB74D) else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Free Web Servers",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isFreeSelected) Color.White else Color(0xFFD0D0D0)
                                    )
                                }
                                if (isFreeSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB74D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Free scraper mirrors (Non-Debrid)",
                                fontSize = 11.sp,
                                color = if (isFreeSelected) Color(0xFFFFCC80) else Color(0xFF8E929B)
                            )
                        }
                    }
                }

                // 2. Audio Language Preference (Hindi Dubbed / Dual-Audio vs Original)
                Text(
                    text = "2. AUDIO LANGUAGE PREFERENCE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFB0B3B8)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Hindi Dubbed / Dual-Audio
                    Surface(
                        onClick = { preferHindi = true },
                        modifier = Modifier.weight(1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (preferHindi) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color(0xFF20232B),
                            focusedContainerColor = Color(0xFF2563EB)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(
                                    if (preferHindi) 2.dp else 1.dp,
                                    if (preferHindi) Color(0xFF60A5FA) else Color(0xFF333842)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(10.dp)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = if (preferHindi) Color(0xFF93C5FD) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🇮🇳 Hindi Dubbed / Dual-Audio (Preferred)",
                                fontSize = 12.sp,
                                fontWeight = if (preferHindi) FontWeight.Bold else FontWeight.Normal,
                                color = if (preferHindi) Color.White else Color(0xFFD0D0D0)
                            )
                        }
                    }

                    // Original / English
                    Surface(
                        onClick = { preferHindi = false },
                        modifier = Modifier.weight(0.7f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (!preferHindi) Color(0xFF374151).copy(alpha = 0.5f) else Color(0xFF20232B),
                            focusedContainerColor = Color(0xFF4B5563)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(
                                    if (!preferHindi) 2.dp else 1.dp,
                                    if (!preferHindi) Color(0xFF9CA3AF) else Color(0xFF333842)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(10.dp)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🌐 Original / English",
                                fontSize = 12.sp,
                                fontWeight = if (!preferHindi) FontWeight.Bold else FontWeight.Normal,
                                color = if (!preferHindi) Color.White else Color(0xFFD0D0D0)
                            )
                        }
                    }
                }

                // 3. Preferred Quality Selection
                Text(
                    text = "3. PREFERRED QUALITY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFB0B3B8)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AvailableQualityOptions.forEach { qOption ->
                        val isSelected = selectedQuality.equals(qOption, ignoreCase = true)
                        Surface(
                            onClick = { selectedQuality = qOption },
                            modifier = Modifier.weight(1f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Color(0xFF4A148C).copy(alpha = 0.6f) else Color(0xFF20232B),
                                focusedContainerColor = Color(0xFF7B1FA2)
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) Color(0xFFCE93D8) else Color(0xFF333842)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = qOption,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFFD0D0D0)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Action Buttons: [Start Streaming] & [Cancel]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = {
                            onConfirm(selectedSource, selectedQuality, preferHindi)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(playButtonFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFE50914),
                            focusedContainerColor = Color(0xFFFF2E39)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start Streaming",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.45f),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF2A2E39),
                            focusedContainerColor = Color(0xFF424754)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 13.sp,
                                color = Color(0xFFD0D0D0)
                            )
                        }
                    }
                }
            }
        }
    }
}
