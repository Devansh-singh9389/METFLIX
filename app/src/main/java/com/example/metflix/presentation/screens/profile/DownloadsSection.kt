/*
 * Copyright 2026 Google LLC
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

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.example.metflix.data.settings.DownloadPreferences
import com.example.metflix.presentation.common.TvFolderPickerDialog
import com.example.metflix.presentation.theme.MetFlixCardShape
import java.io.File

@Composable
fun DownloadsSection(
    downloadPreferences: DownloadPreferences
) {
    val context = LocalContext.current
    val downloadLocation by downloadPreferences.downloadLocation.collectAsStateWithLifecycle()
    val customLocations by downloadPreferences.customLocations.collectAsStateWithLifecycle()
    val preferredQuality by downloadPreferences.preferredQuality.collectAsStateWithLifecycle()
    val preferredLanguages by downloadPreferences.preferredLanguages.collectAsStateWithLifecycle()
    val alwaysAsk by downloadPreferences.alwaysAsk.collectAsStateWithLifecycle()

    var showLocationPicker by remember { mutableStateOf(false) }
    var showTvFolderPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .padding(horizontal = 72.dp)
            .padding(bottom = 64.dp)
    ) {
        item {
            Text(
                text = "Download Settings & Storage",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 1. Storage Location Picker
        item {
            val displayName = when (downloadLocation) {
                Environment.DIRECTORY_DOWNLOADS -> "Downloads Folder (Default)"
                Environment.DIRECTORY_MOVIES -> "Movies Folder"
                else -> {
                    customLocations.find { it.second == downloadLocation }?.first ?: "Custom Storage Folder"
                }
            }

            ListItem(
                modifier = Modifier.padding(top = 8.dp),
                selected = false,
                onClick = { showLocationPicker = true },
                trailingContent = {
                    Text(
                        text = "$displayName ▾",
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                headlineContent = {
                    Text(
                        text = "Storage Download Location",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = "Select default destination directory or browse device to add custom folder.",
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

        // 2. Preferred Video Quality Selector
        item {
            ListItem(
                modifier = Modifier.padding(top = 16.dp),
                selected = false,
                onClick = {
                    val qualities = DownloadPreferences.AVAILABLE_QUALITIES
                    val idx = qualities.indexOf(preferredQuality)
                    val nextIdx = if (idx < 0 || idx + 1 >= qualities.size) 0 else idx + 1
                    downloadPreferences.setPreferredQuality(qualities[nextIdx])
                },
                trailingContent = {
                    Text(
                        text = "$preferredQuality ▾",
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                headlineContent = {
                    Text(
                        text = "Preferred Video Quality",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = "Click to cycle default preferred download quality.",
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

        // 3. Preferred Audio Languages Multi-Select
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Preferred Audio Languages (Multi-Select)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Click to toggle audio tracks you prefer to include in downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(DownloadPreferences.AVAILABLE_LANGUAGES.size) { index ->
            val lang = DownloadPreferences.AVAILABLE_LANGUAGES[index]
            val isChecked = preferredLanguages.contains(lang)
            ListItem(
                modifier = Modifier.padding(top = 8.dp),
                selected = isChecked,
                onClick = { downloadPreferences.toggleLanguage(lang) },
                trailingContent = {
                    Text(
                        text = if (isChecked) "✓ Selected" else "+ Select",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                headlineContent = {
                    Text(
                        text = lang,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                ),
                shape = ListItemDefaults.shape(shape = MetFlixCardShape)
            )
        }

        // 4. Always Ask Toggle Switch
        item {
            ListItem(
                modifier = Modifier.padding(top = 16.dp),
                selected = false,
                onClick = { downloadPreferences.setAlwaysAsk(!alwaysAsk) },
                trailingContent = {
                    Switch(
                        checked = alwaysAsk,
                        onCheckedChange = { downloadPreferences.setAlwaysAsk(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                headlineContent = {
                    Text(
                        text = "Always Confirm Options Before Download",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = "When enabled, shows the YouTube-style download popup to choose resolution and audio tracks before starting.",
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

    if (showLocationPicker) {
        Dialog(onDismissRequest = { showLocationPicker = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select Storage Download Location",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Button(
                        onClick = {
                            downloadPreferences.setDownloadLocation(Environment.DIRECTORY_DOWNLOADS)
                            showLocationPicker = false
                            Toast.makeText(context, "Set to Downloads Folder", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = if (downloadLocation == Environment.DIRECTORY_DOWNLOADS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(if (downloadLocation == Environment.DIRECTORY_DOWNLOADS) "✓ Downloads Folder (Default)" else "Downloads Folder (Default)")
                    }

                    Button(
                        onClick = {
                            downloadPreferences.setDownloadLocation(Environment.DIRECTORY_MOVIES)
                            showLocationPicker = false
                            Toast.makeText(context, "Set to Movies Folder", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = if (downloadLocation == Environment.DIRECTORY_MOVIES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(if (downloadLocation == Environment.DIRECTORY_MOVIES) "✓ Movies Folder" else "Movies Folder")
                    }

                    customLocations.forEach { loc ->
                        val isSelected = downloadLocation == loc.second
                        Button(
                            onClick = {
                                downloadPreferences.setDownloadLocation(loc.second)
                                showLocationPicker = false
                                Toast.makeText(context, "Set to ${loc.first}", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(if (isSelected) "✓ ${loc.first}" else loc.first)
                        }
                    }

                    Button(
                        onClick = {
                            showLocationPicker = false
                            showTvFolderPicker = true
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text("+ Add Custom Folder (In-App Folder Browser)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showLocationPicker = false },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    if (showTvFolderPicker) {
        val initialFile = remember(downloadLocation) {
            if (downloadLocation.startsWith("/")) File(downloadLocation)
            else Environment.getExternalStorageDirectory()
        }
        TvFolderPickerDialog(
            initialDirectory = initialFile,
            onDismissRequest = { showTvFolderPicker = false },
            onFolderSelected = { chosenFolder ->
                showTvFolderPicker = false
                downloadPreferences.addCustomLocation(chosenFolder.name, chosenFolder.absolutePath)
                Toast.makeText(context, "Added storage location: ${chosenFolder.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
