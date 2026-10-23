package com.example.metflix.presentation.screens.profile

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import com.example.metflix.data.util.QrCodeGenerator
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.IconButton
import com.example.metflix.data.network.torbox.TorBoxDeviceAuthUiState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.example.metflix.data.settings.DebridProviderType
import com.example.metflix.presentation.theme.MetFlixCardShape

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
@Composable
fun DebridSection(
    onCachedPlaybackRequested: (String) -> Unit = {},
    viewModel: DebridViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val userInfo by viewModel.userInfo.collectAsStateWithLifecycle()
    val userStatus by viewModel.userStatus.collectAsStateWithLifecycle()
    val isValidating by viewModel.isValidating.collectAsStateWithLifecycle()
    val deviceAuthState by viewModel.deviceAuthState.collectAsStateWithLifecycle()
    val torBoxStats by viewModel.torBoxStats.collectAsStateWithLifecycle()
    val autoFallbackToFree by viewModel.autoFallbackToFree.collectAsStateWithLifecycle()
    val keystoreError by viewModel.keystoreError.collectAsStateWithLifecycle()
    val cloudDownloads by viewModel.cloudDownloads.collectAsStateWithLifecycle()
    val isLoadingDownloads by viewModel.isLoadingDownloads.collectAsStateWithLifecycle()
    val cloudDownloadsError by viewModel.cloudDownloadsError.collectAsStateWithLifecycle()
    var fileSelectionItem by remember { mutableStateOf<com.example.metflix.data.network.torbox.TorBoxCachedItem?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.cachedPlaybackRequests.collect { onCachedPlaybackRequested(it) }
    }

    var isEditingKey by remember { mutableStateOf(false) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var tempKey by remember { mutableStateOf("") }

    LaunchedEffect(apiKey, selectedProvider) {
        tempKey = apiKey
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 72.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 64.dp)
    ) {
        Text(
            text = "Cloud Debrid Streaming",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose your high-speed cloud provider (AllDebrid or TorBox) for unthrottled Indian Dual-Audio (Hindi + English) 4K/1080p streaming.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1. DEBRID PROVIDER SELECTION BOARD (Android TV D-Pad Optimized)
        Text(
            text = "Select Debrid Provider",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DebridProviderType.entries.forEach { provider ->
                val isSelected = selectedProvider == provider
                val providerColor = if (provider == DebridProviderType.ALLDEBRID) Color(0xFF29B6F6) else Color(0xFFFF9800)

                Surface(
                    onClick = {
                        viewModel.selectProvider(provider)
                        isEditingKey = false
                        Toast.makeText(context, "Selected ${provider.displayName}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp) else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(16.dp)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) providerColor else Color.White.copy(alpha = 0.15f)
                            )
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(width = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                        )
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) providerColor else Color.White
                            )
                            if (isSelected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active",
                                        tint = providerColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = providerColor
                                    )
                                }
                            }
                        }

                        Text(
                            text = when (provider) {
                                DebridProviderType.ALLDEBRID -> "High-speed multi-gigabit [AD+] torrent unthrottling with verified Indian dual audio."
                                DebridProviderType.TORBOX -> "Modern [TB+] cloud debrid with direct torrent & usenet caching for pristine streams."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Account / Status Card for Selected Provider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isEnabled && apiKey.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedProvider.displayName} Service: ${if (isEnabled && apiKey.isNotBlank()) "Active" else "Inactive / Key Required"}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isEnabled && apiKey.isNotBlank()) Color(0xFF66BB6A) else Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val statusText = when {
                            isValidating -> "Validating account status with ${selectedProvider.displayName}..."
                            userStatus != null && userStatus!!.isValid -> {
                                if (userStatus!!.isPremium) {
                                    "Premium Active (${userStatus!!.daysRemaining} days remaining) • Account: ${userStatus!!.username}"
                                } else {
                                    "Free Account • Account: ${userStatus!!.username} (Paid plan required for debrid streaming)"
                                }
                            }
                            userInfo.isNotBlank() -> userInfo
                            apiKey.isNotBlank() -> "API key loaded. Press 'Test API' to verify connection."
                            else -> "No API key configured for ${selectedProvider.displayName}. Put key below or in .env."
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (userStatus?.isValid == true && userStatus?.isPremium == true) Color(0xFF81C784) else Color.LightGray
                        )
                    }
                }

                if (keystoreError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE53935).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "⚠ $keystoreError",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFEF5350)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Feature Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF66BB6A).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Provider: ${selectedProvider.displayName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF66BB6A)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF42A5F5).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Audio: Hindi + English Dual-Audio",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF42A5F5)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFAB47BC).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Format: Direct MKV / Remux",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFAB47BC)
                        )
                    }

                    if (selectedProvider == DebridProviderType.TORBOX && torBoxStats != null) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF9800).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Downloaded: ${torBoxStats?.totalDownloadedFormatted} • Egress: ${torBoxStats?.totalEgressFormatted}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Master Toggle Switch
        ListItem(
            selected = false,
            onClick = {
                val newState = !isEnabled
                viewModel.toggleEnabled(newState)
                Toast.makeText(
                    context,
                    if (newState) "${selectedProvider.displayName} Cloud Streaming Enabled" else "Debrid Disabled (Using Free Servers)",
                    Toast.LENGTH_SHORT
                ).show()
            },
            trailingContent = {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        viewModel.toggleEnabled(it)
                        Toast.makeText(
                            context,
                            if (it) "${selectedProvider.displayName} Cloud Streaming Enabled" else "Debrid Disabled (Using Free Servers)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            headlineContent = {
                Text(
                    text = "Enable Cloud Debrid Streaming",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = "Prioritizes instant cached cloud streams on Server 0. When disabled, MetFlix falls back to free web scrapers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ListItemDefaults.shape(shape = MetFlixCardShape)
        )

        Spacer(modifier = Modifier.height(12.dp))

        cloudDownloadsError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ListItem(
            selected = false,
            onClick = {
                val newState = !autoFallbackToFree
                viewModel.toggleAutoFallback(newState)
                Toast.makeText(
                    context,
                    if (newState) "Auto-fallback to free sources enabled" else "Auto-fallback disabled (prompt if uncached)",
                    Toast.LENGTH_SHORT
                ).show()
            },
            trailingContent = {
                Switch(
                    checked = autoFallbackToFree,
                    onCheckedChange = {
                        viewModel.toggleAutoFallback(it)
                        Toast.makeText(
                            context,
                            if (it) "Auto-fallback to free sources enabled" else "Auto-fallback disabled (prompt if uncached)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            headlineContent = {
                Text(
                    text = "Auto-Fallback to Free Sources",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = "When enabled, if a title is not cached on Debrid within 3.5s, MetFlix automatically falls back to free streams without interrupting playback.",
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

        // 4. TorBox TV Quick Connect (Device Code Authorization)
        if (selectedProvider == DebridProviderType.TORBOX) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFFF9800).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "TorBox Quick Connect (No Typing Required)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFFB74D)
                                )
                                Text(
                                    text = "Authorize this TV using your phone or computer in seconds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    when (val authState = deviceAuthState) {
                        is TorBoxDeviceAuthUiState.Idle -> {
                            Button(
                                onClick = {
                                    viewModel.startTorBoxDeviceAuth()
                                    Toast.makeText(context, "Requesting code from TorBox...", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFFFF9800),
                                    focusedContainerColor = Color(0xFFFFA726)
                                )
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate 6-Digit TV Login Code", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        is TorBoxDeviceAuthUiState.Loading -> {
                            Text(
                                text = "Contacting TorBox servers to generate code...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }
                        is TorBoxDeviceAuthUiState.WaitingForApproval -> {
                            val qrUrl = authState.verificationUrlComplete.ifBlank { authState.verificationUrl }
                            val qrBitmap = remember(qrUrl) {
                                QrCodeGenerator.generateQrBitmap(qrUrl, 320)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Offline QR code for fast mobile camera scanning
                                if (qrBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Scan to Link Account",
                                            modifier = Modifier.size(130.dp)
                                        )
                                    }
                                }

                                // 2. Text instructions & 6-digit code
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Scan QR with your phone camera, or enter code below:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                    Text(
                                        text = authState.userCode,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 4.sp
                                        ),
                                        color = Color(0xFFFFB74D)
                                    )
                                    Text(
                                        text = "Go to: ${authState.verificationUrl.ifBlank { "torbox.app/oauth/device" }}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Waiting for approval (${authState.secondsRemaining}s remaining)...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF81C784)
                                    )
                                }

                                Button(
                                    onClick = { viewModel.cancelTorBoxDeviceAuth() },
                                    colors = ButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                                        focusedContainerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel", fontSize = 11.sp)
                                }
                            }
                        }
                        is TorBoxDeviceAuthUiState.Approved -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "✓ TorBox account authorized successfully! API key saved.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF81C784)
                                )
                            }
                        }
                        is TorBoxDeviceAuthUiState.Error -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✗ ${authState.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFEF5350),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.startTorBoxDeviceAuth() },
                                    colors = ButtonDefaults.colors(
                                        focusedContainerColor = Color(0xFFFF9800)
                                    )
                                ) {
                                    Text("Try Again", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 5. API Key Field with Right-Side Test Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${selectedProvider.displayName} API Key",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Save and test your key. Auto-stored so you don't need to re-enter it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isEditingKey) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true
                        )

                        IconButton(
                            onClick = { isKeyVisible = !isKeyVisible },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Hide Key" else "Show Key",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Testing Button on the right side of input field
                        Button(
                            onClick = {
                                viewModel.saveApiKey(tempKey)
                                Toast.makeText(context, "Testing ${selectedProvider.displayName} Key...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF2E7D32),
                                focusedContainerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isValidating) "Testing..." else "Test API", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.saveApiKey(tempKey)
                                isEditingKey = false
                                isKeyVisible = false
                                Toast.makeText(context, "Key Saved for ${selectedProvider.displayName}", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Save", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                tempKey = apiKey
                                isEditingKey = false
                                isKeyVisible = false
                            },
                            colors = ButtonDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                    }
                } else {
                    // Display Current Key with Test Button on the RIGHT SIDE
                    val displayKey = if (apiKey.isNotBlank()) {
                        if (apiKey.length > 8) {
                            "${apiKey.take(4)}••••••••${apiKey.takeLast(4)}"
                        } else {
                            "••••••••"
                        }
                    } else {
                        "Not Set"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedProvider.displayName} Key: $displayKey",
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                color = if (apiKey.isNotBlank()) Color(0xFF81C784) else Color.Gray,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        // TESTING BUTTON ON THE RIGHT SIDE OF THE FIELD
                        Button(
                            onClick = {
                                viewModel.validateStatus()
                                Toast.makeText(context, "Testing ${selectedProvider.displayName} API...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (userStatus?.isValid == true && userStatus?.isPremium == true) Color(0xFF1B5E20) else Color(0xFF1565C0),
                                focusedContainerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isValidating) "Testing..." else if (userStatus?.isValid == true && userStatus?.isPremium == true) "✓ Working (Test Again)" else "Test API",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Test Result Feedback Banner
                    if (userStatus != null) {
                        val bannerColor = when {
                            userStatus!!.isValid && userStatus!!.isPremium -> Color(0xFF2E7D32)
                            userStatus!!.isValid -> Color(0xFFF57F17)
                            else -> Color(0xFFC62828)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bannerColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = when {
                                    userStatus!!.isValid && userStatus!!.isPremium ->
                                        "✓ ${selectedProvider.displayName} API Working! Account: ${userStatus!!.username} • ${userStatus!!.daysRemaining} days left • Ready for Dual-Audio streams"
                                    userStatus!!.isValid ->
                                        "⚠ ${selectedProvider.displayName} API connected, but user is on Free plan. Paid subscription required for direct debrid streams."
                                    else ->
                                        "✗ ${selectedProvider.displayName} API Error: ${userStatus!!.message}"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = bannerColor
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                tempKey = apiKey
                                isEditingKey = true
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                                focusedContainerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (apiKey.isNotBlank()) "Change Key" else "Enter Key", fontSize = 12.sp)
                        }

                        if (apiKey.isNotBlank()) {
                            Button(
                                onClick = {
                                    viewModel.disconnect()
                                    Toast.makeText(context, "Disconnected from ${selectedProvider.displayName}", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                                    focusedContainerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 5. MY CLOUD DOWNLOADS LIBRARY (TorBox / Debrid Cloud)
        if (selectedProvider == DebridProviderType.TORBOX && apiKey.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "My Cloud Library (TorBox Downloads)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF9800)
                    )
                    Text(
                        text = "Instant 4K/HDR streaming directly from your personal TorBox cloud storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        viewModel.loadCloudDownloads()
                        Toast.makeText(context, "Refreshing Cloud Downloads...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                        focusedContainerColor = Color(0xFFFF9800)
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isLoadingDownloads) "Refreshing..." else "Refresh", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingDownloads && cloudDownloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading your TorBox cloud library...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (cloudDownloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No downloads in your TorBox cloud yet. Add magnet links on TorBox or stream movies to see them here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    cloudDownloads.forEach { item ->
                        val isReady = item.isCached ||
                            item.downloadState.equals("completed", ignoreCase = true) ||
                            item.downloadState.equals("cached", ignoreCase = true) ||
                            item.progress >= 1.0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isReady) Color(0xFF81C784) else Color(0xFFFF9800),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = item.name.ifBlank { "Torrent #${item.id}" },
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 2
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = item.formattedSize,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray
                                                )
                                                Text(
                                                    text = "•",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                                Text(
                                                    text = if (isReady) "✓ Download Ready (Cached)" else "Downloading (${(item.progress * 100).toInt()}%)",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (isReady) Color(0xFF81C784) else Color(0xFFFFB74D)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    var isDeletingThis by remember { mutableStateOf(false) }

                                    Button(
                                        onClick = {
                                            val videoFiles = item.files.filter { file ->
                                                val extension = file.name.substringAfterLast('.', "").lowercase()
                                                extension in setOf("mkv", "mp4", "m4v", "ts", "mov", "avi") &&
                                                    !file.name.contains("sample", ignoreCase = true)
                                            }
                                            when {
                                                videoFiles.size == 1 -> viewModel.playCloudTorrent(item, videoFiles.first().id)
                                                videoFiles.size > 1 -> fileSelectionItem = item.copy(files = videoFiles)
                                                else -> viewModel.playCloudTorrent(item)
                                            }
                                        },
                                        enabled = isReady && !isDeletingThis,
                                        colors = ButtonDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                                            focusedContainerColor = Color(0xFF388E3C)
                                        )
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play from TorBox", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Play", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = {
                                                isDeletingThis = true
                                                viewModel.deleteCloudTorrent(item.id) { success, msg ->
                                                    isDeletingThis = false
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = !isDeletingThis,
                                            colors = ButtonDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                                                focusedContainerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete from TorBox", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isDeletingThis) "Deleting..." else "Delete",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fileSelectionItem?.let { selectedItem ->
            Dialog(onDismissRequest = { fileSelectionItem = null }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Choose a video file", style = MaterialTheme.typography.titleMedium)
                        selectedItem.files.forEach { file ->
                            Button(onClick = {
                                fileSelectionItem = null
                                viewModel.playCloudTorrent(selectedItem, file.id)
                            }) {
                                Text(file.name, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}
