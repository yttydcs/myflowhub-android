package com.myflowhub.android.ui
// Context: This file supports the Android app or gomobile host flow around UiNotifier.

import androidx.compose.material3.SnackbarDuration

data class SnackEvent(
    val message: String,
    val duration: SnackbarDuration,
)

class UiNotifier internal constructor(
    private val emit: (SnackEvent) -> Unit,
) {
    fun info(message: String) = emit(SnackEvent(message, SnackbarDuration.Short))
    fun success(message: String) = emit(SnackEvent(message, SnackbarDuration.Short))
    fun error(message: String) = emit(SnackEvent(message, SnackbarDuration.Short))
    fun progress(message: String) = emit(SnackEvent(message, SnackbarDuration.Indefinite))
}
