package com.myflowhub.android.ui
// 本文件实现 Android 客户端中与 `UiNotifier` 界面相关的宿主逻辑。

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
