package com.example.metflix.presentation.screens.livetv.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val DialOverlayShape = RoundedCornerShape(14.dp)
private val DigitBoxShape = RoundedCornerShape(8.dp)

/**
 * Standard Set-Top-Box (STB) style direct channel dialer overlay.
 * Displays accumulated remote digits (e.g., "1", "11", "112") or an "Invalid Channel" notice.
 */
@Composable
fun LiveTvNumberInputOverlay(
    digits: String,
    isInvalid: Boolean,
    modifier: Modifier = Modifier
) {
    val isVisible = digits.isNotEmpty() || isInvalid

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        val cursorAlpha = remember { Animatable(1f) }
        LaunchedEffect(digits) {
            if (digits.isNotEmpty() && !isInvalid) {
                cursorAlpha.animateTo(
                    targetValue = 0.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            } else {
                cursorAlpha.snapTo(1f)
            }
        }

        val backgroundColor = if (isInvalid) {
            Color(0xE62A0D0D) // Reddish warning background
        } else {
            Color(0xEA10141D) // Deep STB slate background
        }

        val borderColor = if (isInvalid) {
            Color(0xFFFF4D4D)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        }

        Box(
            modifier = Modifier
                .widthIn(min = 180.dp)
                .clip(DialOverlayShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = 0.95f)
                        )
                    )
                )
                .border(2.dp, borderColor, DialOverlayShape)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isInvalid) {
                // Invalid Channel Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Invalid Channel",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "Invalid Channel",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color(0xFFFF5252)
                        )
                        Text(
                            text = "No channel found",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                    }
                }
            } else {
                // Active Dialing Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "TUNING CHANNEL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Display individual digit boxes for authentic STB feel
                        digits.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(width = 32.dp, height = 44.dp)
                                    .clip(DigitBoxShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), DigitBoxShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = digit.toString(),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Blinking cursor placeholder for next digit
                        if (digits.length < 4) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(width = 32.dp, height = 44.dp)
                                    .alpha(cursorAlpha.value)
                                    .clip(DigitBoxShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), DigitBoxShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "-",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
