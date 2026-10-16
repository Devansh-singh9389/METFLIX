package com.example.metflix.data.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LiveChannel(
    val id: String,
    val name: String,
    val channelNumber: Int,
    val streamUrl: String,
    val logoUrl: String? = null,
    val category: String = "General",
    val country: String? = null,
    val currentProgram: String = "Live Broadcast",
    val programDescription: String = "",
    val isFavorite: Boolean = false,
)
