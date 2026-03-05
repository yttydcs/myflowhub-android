package com.myflowhub.android

import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException

internal object GoReflect {
    fun method(cls: Class<*>, name: String, vararg params: Class<*>): Method {
        val candidates = nameCandidates(name)
        var last: NoSuchMethodException? = null
        for (candidate in candidates) {
            try {
                return cls.getMethod(candidate, *params)
            } catch (t: NoSuchMethodException) {
                last = t
            }
        }

        val sig = params.joinToString(prefix = "(", postfix = ")") { it.simpleName }
        val msg = "未找到方法：${cls.name}.${name}${sig}；已尝试：${candidates.joinToString()}"
        throw NoSuchMethodException(msg).apply { initCause(last) }
    }

    fun invokeStatic(method: Method, vararg args: Any?): Any? {
        try {
            return method.invoke(null, *args)
        } catch (t: InvocationTargetException) {
            val cause = rootCause(t.targetException ?: t.cause ?: t)
            if (cause !== t) {
                throw cause
            }
            throw IllegalStateException("调用 Go 方法失败：${method.name}", t)
        } catch (t: ReflectiveOperationException) {
            throw IllegalStateException("调用 Go 方法失败：${method.name}", t)
        } catch (t: IllegalArgumentException) {
            throw IllegalStateException("调用 Go 方法参数非法：${method.name}", t)
        }
    }

    fun renderError(err: Throwable, fallback: String = ""): String {
        val root = rootCause(err)
        val rootMsg = root.message?.trim().orEmpty()
        val errMsg = err.message?.trim().orEmpty()
        val base = when {
            rootMsg.isNotBlank() -> rootMsg
            errMsg.isNotBlank() -> errMsg
            else -> root.toString()
        }
        val fb = fallback.trim()
        if (fb.isBlank() || base.contains(fb)) {
            return base
        }
        return "$base (lastError: $fb)"
    }

    private fun nameCandidates(name: String): List<String> {
        if (name.isBlank()) {
            return listOf(name)
        }
        val lowerFirst = name.replaceFirstChar { it.lowercaseChar() }
        val upperFirst = name.replaceFirstChar { it.uppercaseChar() }
        return listOf(name, lowerFirst, upperFirst).distinct()
    }

    private fun rootCause(err: Throwable): Throwable {
        var current = err
        val seen = HashSet<Throwable>()
        while (current.cause != null && current.cause !== current && seen.add(current.cause!!)) {
            current = current.cause!!
        }
        return current
    }
}

