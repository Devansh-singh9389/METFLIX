/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.metflix.presentation.screens.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.example.metflix.presentation.theme.MetFlixCardShape

@Composable
fun PerformanceSection(
    viewModel: PerformanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isDeviceLowRam = viewModel.isDeviceLowRam
    val lowSpecMode by viewModel.lowSpecMode.collectAsStateWithLifecycle()
    val tmdbEnabled by viewModel.tmdbEnabled.collectAsStateWithLifecycle()
    val highQualityImages by viewModel.highQualityImages.collectAsStateWithLifecycle()
    val reduceAnimations by viewModel.reduceAnimations.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .padding(horizontal = 72.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 64.dp)
    ) {
        Text(
            text = "Performance & Hardware",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Hardware Profile Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isDeviceLowRam) "Hardware: Low-RAM Device (<=1GB RAM)" else "Hardware: High-Performance Device (>1GB RAM)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lowSpecMode) "Current Status: Low-Spec Mode (Instant local fallback, 16-bit RGB_565, Zero-Lag)" else "Current Status: High-Performance Mode (Full quality ARGB_8888, Live TMDB sync)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lowSpecMode) Color(0xFF66BB6A) else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Master Low-Spec / Performance Mode Switch
        ListItem(
            selected = false,
            onClick = { viewModel.toggleLowSpecMode(!lowSpecMode) },
            trailingContent = {
                Switch(
                    checked = lowSpecMode,
                    onCheckedChange = { viewModel.toggleLowSpecMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            headlineContent = {
                Text(
                    text = "Performance Mode (Low-RAM Optimization)",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = "Optimizes memory, avoids slow network freezes, and downsamples graphics for smooth operation on 512MB-1GB TV sticks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ListItemDefaults.shape(shape = MetFlixCardShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Granular Option: Live TMDB Online Metadata
        ListItem(
            selected = false,
            onClick = { viewModel.toggleTmdb(!tmdbEnabled) },
            trailingContent = {
                Switch(
                    checked = tmdbEnabled,
                    onCheckedChange = { viewModel.toggleTmdb(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            headlineContent = {
                Text(
                    text = "Live TMDB Network Metadata",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = "Fetches real-time movies, cast, and trending data from TMDB online. Disable if your network hangs or blocks TMDB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ListItemDefaults.shape(shape = MetFlixCardShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Granular Option: High Definition 32-bit Graphics
        ListItem(
            selected = false,
            onClick = { viewModel.toggleHighQualityImages(!highQualityImages) },
            trailingContent = {
                Switch(
                    checked = highQualityImages,
                    onCheckedChange = { viewModel.toggleHighQualityImages(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            headlineContent = {
                Text(
                    text = "High-Definition Graphics (ARGB_8888)",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = "Decodes posters and backdrops in full 32-bit color. For low-spec TVs, keep disabled to use memory-efficient 16-bit RGB_565.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ListItemDefaults.shape(shape = MetFlixCardShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Granular Option: Smooth Navigation Mode
        ListItem(
            selected = false,
            onClick = { viewModel.toggleReduceAnimations(!reduceAnimations) },
            trailingContent = {
                Switch(
                    checked = reduceAnimations,
                    onCheckedChange = { viewModel.toggleReduceAnimations(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            headlineContent = {
                Text(
                    text = "Smooth Navigation Mode",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = "Reduces heavy layout and scroll animations to keep D-pad navigation instant and responsive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ListItemDefaults.shape(shape = MetFlixCardShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Memory Purge / Cache Clear Action
        ListItem(
            selected = false,
            onClick = {
                viewModel.clearCache()
                Toast.makeText(context, "Memory and Image Cache Purged", Toast.LENGTH_SHORT).show()
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = {
                Text(
                    text = "Purge Memory & Cache",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = "Frees image bitmap memory and resets network circuits immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ListItemDefaults.shape(shape = MetFlixCardShape)
        )
    }
}
