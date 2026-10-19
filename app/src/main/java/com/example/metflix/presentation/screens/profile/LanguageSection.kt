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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.example.metflix.R
import com.example.metflix.data.util.StringConstants
import com.example.metflix.presentation.theme.MetFlixCardShape

import com.example.metflix.data.settings.LanguagePreferences

@Composable
fun LanguageSection(
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(horizontal = 72.dp)) {
        item {
            Text(
                text = "Application Language",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        items(LanguagePreferences.AVAILABLE_LANGUAGES.size) { index ->
            val lang = LanguagePreferences.AVAILABLE_LANGUAGES[index]
            val isSelected = lang.code == currentLanguageCode
            ListItem(
                modifier = Modifier.padding(top = 16.dp),
                selected = false,
                onClick = { onLanguageSelected(lang.code) },
                trailingContent = if (isSelected) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected language: ${lang.displayName}"
                        )
                    }
                } else null,
                headlineContent = {
                    Text(
                        text = lang.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                ),
                shape = ListItemDefaults.shape(shape = MetFlixCardShape)
            )
        }
    }
}
