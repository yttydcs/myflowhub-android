package com.myflowhub.android

internal object GomobileLoader {
    fun loadHubClass(): Class<*> {
        val candidates = listOf(
            // Current default (scripts/build_aar.*): -javapkg com.myflowhub.gomobile + Go pkg "hubmobile"
            "com.myflowhub.gomobile.hubmobile.Hubmobile",
            "com.myflowhub.gomobile.Hubmobile",
            // Backward-compatible fallbacks for older AARs.
            "com.myflowhub.native.hubmobile.Hubmobile",
            "com.myflowhub.native.Hubmobile",
        )

        var lastError: Throwable? = null
        for (fqcn in candidates) {
            try {
                return Class.forName(fqcn)
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw IllegalStateException(
            "未找到 gomobile 生成类；请确认 app/libs/myflowhub.aar 已打包进 APK。已尝试：${candidates.joinToString()}",
            lastError,
        )
    }
}

