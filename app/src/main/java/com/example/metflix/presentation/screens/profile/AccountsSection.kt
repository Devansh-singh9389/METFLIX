/*
 * Copyright 2023 Google LLC
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.metflix.data.util.StringConstants
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metflix.data.settings.PerformancePreferences

@Immutable
data class AccountsSectionData(
    val title: String,
    val value: String? = null,
    val onClick: () -> Unit = {}
)

@Composable
fun AccountsSection(
    onOpenPerformanceSettings: () -> Unit = {},
    onOpenDebridSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val childPadding = rememberChildPadding()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isLowSpec = PerformancePreferences.instance?.lowSpecMode?.collectAsStateWithLifecycle()?.value ?: true
    val accountsSectionListItems = listOf(
        AccountsSectionData(
            title = "AllDebrid Cloud Streaming",
            value = "Dual-Audio Hindi • Instant Cache",
            onClick = onOpenDebridSettings
        ),
        AccountsSectionData(
            title = "Performance Mode",
            value = if (isLowSpec) "Low-Spec (Optimized)" else "High-Performance (Active)",
            onClick = onOpenPerformanceSettings
        ),
        AccountsSectionData(
            title = StringConstants.Composable.Placeholders
                .AccountsSelectionSwitchAccountsTitle,
            value = StringConstants.Composable.Placeholders.AccountsSelectionSwitchAccountsEmail,
            onClick = { Toast.makeText(context, "Switch Account feature coming soon", Toast.LENGTH_SHORT).show() }
        ),
        AccountsSectionData(
            title = StringConstants.Composable.Placeholders.AccountsSelectionLogOut,
            value = StringConstants.Composable.Placeholders.AccountsSelectionSwitchAccountsEmail,
            onClick = { Toast.makeText(context, "Logged out of current profile", Toast.LENGTH_SHORT).show() }
        ),
        AccountsSectionData(
            title = StringConstants.Composable.Placeholders
                .AccountsSelectionChangePasswordTitle,
            value = StringConstants.Composable.Placeholders.AccountsSelectionChangePasswordValue,
            onClick = { Toast.makeText(context, "Change Password feature coming soon", Toast.LENGTH_SHORT).show() }
        ),
        AccountsSectionData(
            title = StringConstants.Composable.Placeholders.AccountsSelectionAddNewAccountTitle,
            onClick = { Toast.makeText(context, "Add New Account feature coming soon", Toast.LENGTH_SHORT).show() }
        ),
        AccountsSectionData(
            title = StringConstants.Composable.Placeholders
                .AccountsSelectionViewSubscriptionsTitle,
            onClick = { Toast.makeText(context, "Subscriptions details feature coming soon", Toast.LENGTH_SHORT).show() }
        ),
        AccountsSectionData(
            title = StringConstants.Composable.Placeholders.AccountsSelectionDeleteAccountTitle,
            onClick = { showDeleteDialog = true }
        )
    )

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = childPadding.start),
        columns = GridCells.Fixed(2),
        content = {
            items(accountsSectionListItems.size) { index ->
                AccountsSelectionItem(
                    modifier = Modifier.focusRequester(focusRequester),
                    key = index,
                    accountsSectionData = accountsSectionListItems[index]
                )
            }
        }
    )

    AccountsSectionDeleteDialog(
        showDialog = showDeleteDialog,
        onDismissRequest = { showDeleteDialog = false },
        modifier = Modifier.width(428.dp)
    )
}
