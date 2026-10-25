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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.example.metflix.presentation.theme.MetFlixCardShape
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StorageDriveType {
    INTERNAL,
    USB,
    MOVIES,
    DOWNLOADS,
    APP_PRIVATE,
    CUSTOM
}

data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val subtitle: String,
    val file: File,
    val freeSpaceFormatted: String,
    val totalSpaceFormatted: String,
    val usedRatio: Float,
    val isRemovable: Boolean,
    val driveType: StorageDriveType
)

data class BreadcrumbItem(
    val name: String,
    val file: File
)

object StorageVolumeManager {

    fun getStorageStats(file: File): Triple<Long, Long, Float> {
        return try {
            val stat = StatFs(file.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val usedRatio = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f
            Triple(free, total, usedRatio)
        } catch (_: Exception) {
            val free = file.freeSpace
            val total = file.totalSpace
            val usedRatio = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f
            Triple(free, total, usedRatio)
        }
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format(Locale.US, "%.1f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format(Locale.US, "%.0f MB", mb)
        }
    }

    fun getAllStorageDrives(context: Context): List<StorageVolumeInfo> {
        val list = mutableListOf<StorageVolumeInfo>()

        // 1. Primary Internal Storage (/storage/emulated/0)
        val internalDir = Environment.getExternalStorageDirectory()
        if (internalDir != null && internalDir.exists()) {
            val (free, total, ratio) = getStorageStats(internalDir)
            list.add(
                StorageVolumeInfo(
                    id = "internal_storage",
                    name = "Internal Storage",
                    subtitle = internalDir.absolutePath,
                    file = internalDir,
                    freeSpaceFormatted = formatBytes(free),
                    totalSpaceFormatted = formatBytes(total),
                    usedRatio = ratio,
                    isRemovable = false,
                    driveType = StorageDriveType.INTERNAL
                )
            )
        }

        // 2. Movies Directory
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        if (moviesDir != null) {
            if (!moviesDir.exists()) { runCatching { moviesDir.mkdirs() } }
            if (moviesDir.exists()) {
                val (free, total, ratio) = getStorageStats(moviesDir)
                list.add(
                    StorageVolumeInfo(
                        id = "movies_folder",
                        name = "Movies Folder",
                        subtitle = moviesDir.absolutePath,
                        file = moviesDir,
                        freeSpaceFormatted = formatBytes(free),
                        totalSpaceFormatted = formatBytes(total),
                        usedRatio = ratio,
                        isRemovable = false,
                        driveType = StorageDriveType.MOVIES
                    )
                )
            }
        }

        // 3. Downloads Directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null) {
            if (!downloadsDir.exists()) { runCatching { downloadsDir.mkdirs() } }
            if (downloadsDir.exists()) {
                val (free, total, ratio) = getStorageStats(downloadsDir)
                list.add(
                    StorageVolumeInfo(
                        id = "downloads_folder",
                        name = "Downloads Folder",
                        subtitle = downloadsDir.absolutePath,
                        file = downloadsDir,
                        freeSpaceFormatted = formatBytes(free),
                        totalSpaceFormatted = formatBytes(total),
                        usedRatio = ratio,
                        isRemovable = false,
                        driveType = StorageDriveType.DOWNLOADS
                    )
                )
            }
        }

        // 4. App-Safe Storage (Guaranteed 100% writable, zero permissions needed)
        val appSafeDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Downloads")
        if (!appSafeDir.exists()) { runCatching { appSafeDir.mkdirs() } }
        val (appFree, appTotal, appRatio) = getStorageStats(appSafeDir)
        list.add(
            StorageVolumeInfo(
                id = "app_safe_storage",
                name = "App-Safe Storage",
                subtitle = "App-scoped directory (No extra permissions required)",
                file = appSafeDir,
                freeSpaceFormatted = formatBytes(appFree),
                totalSpaceFormatted = formatBytes(appTotal),
                usedRatio = appRatio,
                isRemovable = false,
                driveType = StorageDriveType.APP_PRIVATE
            )
        )

        // 5. External USB Drives / SD Cards
        val externalDirs = runCatching {
            context.getExternalFilesDirs(null).filterNotNull()
        }.getOrDefault(emptyList())

        for (i in 1 until externalDirs.size) {
            val extFile = externalDirs[i]
            val (free, total, ratio) = getStorageStats(extFile)
            val usbName = extFile.parentFile?.parentFile?.parentFile?.parentFile?.name ?: "USB Drive #$i"
            list.add(
                StorageVolumeInfo(
                    id = "usb_drive_$i",
                    name = "External USB ($usbName)",
                    subtitle = extFile.absolutePath,
                    file = extFile,
                    freeSpaceFormatted = formatBytes(free),
                    totalSpaceFormatted = formatBytes(total),
                    usedRatio = ratio,
                    isRemovable = true,
                    driveType = StorageDriveType.USB
                )
            )
        }

        // Also inspect /storage directly for mounted removable media
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.canRead()) {
            val mounts = storageRoot.listFiles { f ->
                f.isDirectory && f.canRead() &&
                    !f.name.equals("emulated", ignoreCase = true) &&
                    !f.name.equals("self", ignoreCase = true) &&
                    !f.name.startsWith(".")
            } ?: emptyArray()

            for (m in mounts) {
                if (list.none { it.file.absolutePath.startsWith(m.absolutePath) }) {
                    val (free, total, ratio) = getStorageStats(m)
                    list.add(
                        StorageVolumeInfo(
                            id = "usb_mount_${m.name}",
                            name = "USB Storage (${m.name})",
                            subtitle = m.absolutePath,
                            file = m,
                            freeSpaceFormatted = formatBytes(free),
                            totalSpaceFormatted = formatBytes(total),
                            usedRatio = ratio,
                            isRemovable = true,
                            driveType = StorageDriveType.USB
                        )
                    )
                }
            }
        }

        return list
    }

    fun getBreadcrumbs(currentDir: File): List<BreadcrumbItem> {
        val list = mutableListOf<BreadcrumbItem>()
        var curr: File? = currentDir
        while (curr != null) {
            val name = when {
                curr.absolutePath == "/" -> "Root"
                curr.absolutePath == "/storage/emulated/0" -> "Internal Storage"
                curr.absolutePath == "/storage" -> "Storage"
                else -> curr.name.ifBlank { curr.absolutePath }
            }
            list.add(0, BreadcrumbItem(name, curr))
            curr = curr.parentFile
        }
        return list
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
    initialDirectory: File = Environment.getExternalStorageDirectory(),
    onDismissRequest: () -> Unit,
    onFolderSelected: (File) -> Unit
) {
    val context = LocalContext.current
    val storageDrives = remember(context) { StorageVolumeManager.getAllStorageDrives(context) }
    var hasPermission by remember { mutableStateOf(hasAllFilesAccess()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    var currentDir by remember {
        val startDir = when {
            initialDirectory.exists() && initialDirectory.canRead() -> initialDirectory
            Environment.getExternalStorageDirectory()?.exists() == true -> Environment.getExternalStorageDirectory()
            else -> File("/storage")
        }
        mutableStateOf(startDir)
    }

    val breadcrumbs = remember(currentDir) { StorageVolumeManager.getBreadcrumbs(currentDir) }

    val subFolders = remember(currentDir, hasPermission, refreshKey) {
        try {
            currentDir.listFiles { file ->
                file.isDirectory && !file.name.startsWith(".") && file.canRead()
            }?.sortedBy { it.name.lowercase() }?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val currentDirStats = remember(currentDir, refreshKey) {
        StorageVolumeManager.getStorageStats(currentDir)
    }

    // Handle TV Remote Back button to navigate up folders
    BackHandler {
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead() && parent.absolutePath != "/") {
            currentDir = parent
        } else {
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .border(1.dp, Color(0xFF33333F), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF18181E)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // 1. Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Select Download Storage Location",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Choose where offline movies, episodes and optimized videos are stored",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onDismissRequest,
                        colors = OutlinedButtonDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Dual Pane Layout (Left: Drives/Shortcuts | Right: Folder Tree)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // LEFT PANE: Storage Drives & Quick Access
                    Column(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight()
                            .background(Color(0xFF22222B), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "STORAGE DRIVES & SHORTCUTS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Permission warning button if needed
                            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                item {
                                    Surface(
                                        onClick = {
                                            requestAllFilesAccess(context)
                                            hasPermission = hasAllFilesAccess()
                                        },
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color(0xFF3E2723),
                                            focusedContainerColor = Color(0xFF5D4037)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("🔒", fontSize = 16.sp)
                                            Column {
                                                Text(
                                                    text = "Grant Storage Permission",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFFFFCC80)
                                                )
                                                Text(
                                                    text = "Click to open TV Settings",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFFBCAAA4)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            items(storageDrives, key = { it.id }) { drive ->
                                val isSelectedDrive = currentDir.absolutePath.startsWith(drive.file.absolutePath)
                                DriveShortcutCard(
                                    drive = drive,
                                    isSelected = isSelectedDrive,
                                    onClick = { currentDir = drive.file }
                                )
                            }
                        }
                    }

                    // RIGHT PANE: Breadcrumbs & Directory Browser
                    Column(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight()
                            .background(Color(0xFF22222B), RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        // Breadcrumbs bar
                        Text(
                            text = "DIRECTORY NAVIGATION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(breadcrumbs) { crumb ->
                                val isLast = crumb.file.absolutePath == currentDir.absolutePath
                                Surface(
                                    onClick = { currentDir = crumb.file },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color(0xFF2E2E38),
                                        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = if (isLast) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(
                                            text = crumb.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal)
                                        )
                                    }
                                }
                                if (!isLast) {
                                    Text("›", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }

                        // Path info & capacity badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentDir.absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFAAAAAF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2C2C38), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${StorageVolumeManager.formatBytes(currentDirStats.first)} Free",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF81C784)
                                )
                            }
                        }

                        // Directory item list
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            // 1. "Parent Directory" Item
                            val parent = currentDir.parentFile
                            if (parent != null && parent.canRead() && parent.absolutePath != "/") {
                                item {
                                    FolderListItem(
                                        icon = Icons.Default.ArrowBack,
                                        title = ".. (Up to ${parent.name.ifBlank { parent.absolutePath }})",
                                        subtitle = "Parent Folder",
                                        onClick = { currentDir = parent }
                                    )
                                }
                            }

                            // 2. "Create New Folder" Action
                            item {
                                FolderListItem(
                                    icon = Icons.Default.CreateNewFolder,
                                    title = "+ Create New Folder Here",
                                    subtitle = "Make a dedicated download folder in this directory",
                                    isAccent = true,
                                    onClick = { showCreateFolderDialog = true }
                                )
                            }

                            // 3. Subfolders
                            if (subFolders.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No subfolders in this directory. Click 'Select This Folder' to choose it.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            } else {
                                items(subFolders, key = { it.absolutePath }) { folder ->
                                    val childCount = runCatching {
                                        folder.listFiles()?.count { it.isDirectory } ?: 0
                                    }.getOrDefault(0)

                                    FolderListItem(
                                        icon = Icons.Default.Folder,
                                        title = folder.name,
                                        subtitle = "$childCount subfolder(s)",
                                        onClick = { currentDir = folder }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Bottom Action & Navigation Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // D-Pad Legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DpadLegendItem(key = "OK", action = "Enter Folder")
                        DpadLegendItem(key = "BACK", action = "Go Up / Exit")
                    }

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDismissRequest) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (currentDir.canWrite()) {
                                    onFolderSelected(currentDir)
                                } else {
                                    // Try creating a test file to verify true write permission
                                    val testFile = File(currentDir, ".metflix_perm_test")
                                    val isWritable = runCatching {
                                        testFile.createNewFile() && testFile.delete()
                                    }.getOrDefault(false)

                                    if (isWritable) {
                                        onFolderSelected(currentDir)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "This directory is read-only. Please select App-Safe Storage or grant permissions.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select Folder: ${currentDir.name.ifBlank { "Root" }}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal prompt for creating new folder
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            parentDirectory = currentDir,
            onDismiss = { showCreateFolderDialog = false },
            onFolderCreated = { newFolder ->
                showCreateFolderDialog = false
                currentDir = newFolder
                refreshKey++
                Toast.makeText(context, "Created ${newFolder.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DriveShortcutCard(
    drive: StorageVolumeInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (drive.driveType) {
        StorageDriveType.INTERNAL -> Icons.Default.Storage
        StorageDriveType.USB -> Icons.Default.Usb
        StorageDriveType.MOVIES -> Icons.Default.Movie
        StorageDriveType.DOWNLOADS -> Icons.Default.Download
        StorageDriveType.APP_PRIVATE -> Icons.Default.Folder
        StorageDriveType.CUSTOM -> Icons.Default.Folder
    }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0xFF2C2C3D) else Color(0xFF1B1B22),
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = Color.White,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2A2A35),
                RoundedCornerShape(10.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = drive.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${drive.freeSpaceFormatted} free / ${drive.totalSpaceFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Storage bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF33333F))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(drive.usedRatio)
                        .fillMaxHeight()
                        .background(if (drive.usedRatio > 0.9f) Color.Red else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FolderListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isAccent: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isAccent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0xFF1E1E26),
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isAccent) MaterialTheme.colorScheme.primary else Color.White,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DpadLegendItem(key: String, action: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF333342), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = key, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        Text(text = action, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CreateFolderDialog(
    parentDirectory: File,
    onDismiss: () -> Unit,
    onFolderCreated: (File) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(440.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E28)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Create New Folder",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Text(
                    text = "In: ${parentDirectory.name.ifBlank { parentDirectory.absolutePath }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2C2C38), RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (folderName.isEmpty()) {
                        Text(
                            text = "Folder name (e.g. Movies)",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val cleanName = folderName.trim().replace(Regex("""[\\/:*?"<>|]"""), "")
                            if (cleanName.isBlank()) {
                                Toast.makeText(context, "Please enter a valid folder name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val target = File(parentDirectory, cleanName)
                            if (target.exists()) {
                                Toast.makeText(context, "Folder already exists", Toast.LENGTH_SHORT).show()
                                onFolderCreated(target)
                            } else {
                                val success = target.mkdirs()
                                if (success) {
                                    onFolderCreated(target)
                                } else {
                                    Toast.makeText(context, "Failed to create folder. Permission denied.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
