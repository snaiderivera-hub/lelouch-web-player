package com.lelouch.feature.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lelouch.core.designsystem.LelouchBackground
import com.lelouch.core.designsystem.LelouchCyanAccent
import com.lelouch.core.designsystem.LelouchTextSecondary

@Composable
fun TvBootstrapRecoveryScreen(
    title: String = "REPRODUCTOR LELOUCH",
    subtitle: String,
    message: String? = null,
    isLoading: Boolean = false,
    retryable: Boolean = true,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val retryFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            if (retryable) {
                retryFocusRequester.requestFocus()
            } else {
                settingsFocusRequester.requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LelouchBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitle,
                color = LelouchCyanAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (!message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = LelouchTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 500.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    color = LelouchCyanAccent,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (retryable) {
                        ActionButton(
                            icon = Icons.Default.Refresh,
                            label = "Reintentar",
                            modifier = Modifier.focusRequester(retryFocusRequester),
                            onClick = onRetry
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    ActionButton(
                        icon = Icons.Default.Settings,
                        label = "Configurar fuente",
                        modifier = Modifier.focusRequester(settingsFocusRequester),
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }
}
