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

package com.example.metflix.presentation.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import java.io.File
import java.util.Locale

data class StorageVolumeInfo(
    val name: String,
    val file: File,
    val freeSpaceFormatted: String,
    val isRemovable: Boolean
)

fun getSelectableStorageLocations(context: Context): List<StorageVolumeInfo> {
    val dirs = try {
        context.getExternalFilesDirs(Environment.DIRECTORY_DOWNLOADS).filterNotNull()
    } catch (_: Exception) {
        emptyList()
    }

    return dirs.mapIndexed { index, file ->
        if (!file.exists()) {
            try { file.mkdirs() } catch (_: Exception) {}
        }

        val freeSpaceBytes = try {
            val stat = StatFs(file.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            file.freeSpace
        }

        val freeGb = String.format(Locale.US, "%.2f GB free", freeSpaceBytes / (1024.0 * 1024.0 * 1024.0))
        val isUsb = try {
            Environment.isExternalStorageRemovable(file) || index > 0
        } catch (_: Exception) {
            index > 0
        }

        val displayName = if (isUsb) {
            val volName = file.parentFile?.parentFile?.parentFile?.parentFile?.name ?: "USB Drive"
            "External USB Storage ($volName)"
        } else {
            "Internal App Storage"
        }

        StorageVolumeInfo(
            name = displayName,
            file = file,
            freeSpaceFormatted = freeGb,
            isRemovable = isUsb
        )
    }
}

fun hasAllFilesAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            Environment.isExternalStorageManager()
        } catch (_: Exception) {
            true
        }
    } else {
        true
    }
}

fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open storage settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFolderPickerDialog(
    initialDirectory: File = File("/storage"),
    onDismissRequest: () -> Unit,
    onFolderSelected: (File) -> Unit
) {
    val context = LocalContext.current
    val detectedVolumes = remember(context) { getSelectableStorageLocations(context) }
    var hasPermission by remember { mutableStateOf(hasAllFilesAccess()) }

    var currentDir by remember {
        val rootPath = File("/storage")
        val startDir = when {
            initialDirectory.exists() && initialDirectory.canRead() -> initialDirectory
            rootPath.exists() && rootPath.canRead() -> rootPath
            else -> Environment.getExternalStorageDirectory()
        }
        mutableStateOf(startDir)
    }

    val subFolders = remember(currentDir, hasPermission) {
        try {
            currentDir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") && file.canRead() }
                ?.sortedBy { it.name.lowercase() }
                ?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .width(660.dp)
                .height(520.dp),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Select Download Storage Location",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Current Path: ${currentDir.absolutePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Permission Prompt if All Files Access is missing
                    if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        item {
                            DenseListItem(
                                headline = "🔒 Grant 'All Files Access' Permission (Click to open Settings)",
                                onClick = {
                                    requestAllFilesAccess(context)
                                    hasPermission = hasAllFilesAccess()
                                }
                            )
                        }
                    }

                    // 2. Pre-configured App Storage Volumes (Always Work, Zero Permission Needed)
                    if (detectedVolumes.isNotEmpty()) {
                        item {
                            Text(
                                text = "Auto-Detected Storage Volumes (App-Scoped):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(detectedVolumes) { vol ->
                            DenseListItem(
                                headline = "💾 ${vol.name} (${vol.freeSpaceFormatted})",
                                onClick = { onFolderSelected(vol.file) }
                            )
                        }
                    }

                    // 3. System Storage File Browser
                    item {
                        Text(
                            text = "Browse Custom Directories:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    // Navigate to Parent Directory
                    val parent = currentDir.parentFile
                    if (parent != null && parent.canRead() && parent.absolutePath != "/") {
                        item {
                            DenseListItem(
                                headline = "📁 .. (Back to Parent: ${parent.name.ifBlank { parent.absolutePath }})",
                                onClick = { currentDir = parent }
                            )
                        }
                    }

                    if (subFolders.isEmpty()) {
                        item {
                            Text(
                                text = if (!hasPermission) {
                                    "No subfolders accessible. Click 'Grant All Files Access' above or select an Auto-Detected Storage Volume."
                                } else {
                                    "No accessible subfolders found in this directory."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        items(subFolders, key = { it.absolutePath }) { folder ->
                            DenseListItem(
                                headline = "📁 ${folder.name}",
                                onClick = { currentDir = folder }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onFolderSelected(currentDir) }
                    ) {
                        Text("Select Current Folder", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DenseListItem(
    headline: String,
    onClick: () -> Unit
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text(text = headline, style = MaterialTheme.typography.bodyMedium)
        },
        colors = ListItemDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = ListItemDefaults.shape(shape = RoundedCornerShape(8.dp))
    )
}
