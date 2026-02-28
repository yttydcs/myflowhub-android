package com.myflowhub.android

class GoClientBridge {
    private val cls: Class<*> = GomobileLoader.loadHubClass()

    private val ensureInitMethod = GoReflect.method(cls, "EnsureInit", String::class.java)
    private val connectMethod = GoReflect.method(cls, "Connect", String::class.java)
    private val closeMethod = GoReflect.method(cls, "Close")
    private val isConnectedMethod = GoReflect.method(cls, "IsConnected")
    private val lastAddrMethod = GoReflect.method(cls, "LastAddr")
    private val authStateMethod = GoReflect.method(cls, "AuthState")
    private val clearAuthMethod = GoReflect.method(cls, "ClearAuth")
    private val ensureKeysMethod = GoReflect.method(cls, "EnsureKeys")
    private val registerMethod = GoReflect.method(cls, "Register", String::class.java)
    private val loginMethod = GoReflect.method(cls, "Login", String::class.java, String::class.java)
    private val getSelfNodeIdMethod = GoReflect.method(cls, "GetSelfNodeID")
    private val getLastErrorMethod = GoReflect.method(cls, "GetLastError")

    private val listNodesMethod = GoReflect.method(cls, "ListNodes", String::class.java, String::class.java)
    private val listSubtreeMethod = GoReflect.method(cls, "ListSubtree", String::class.java, String::class.java)
    private val nodeInfoMethod = GoReflect.method(cls, "NodeInfo", String::class.java, String::class.java)
    private val configListMethod = GoReflect.method(cls, "ConfigList", String::class.java, String::class.java)
    private val configGetMethod = GoReflect.method(cls, "ConfigGet", String::class.java, String::class.java, String::class.java)
    private val configSetMethod = GoReflect.method(cls, "ConfigSet", String::class.java, String::class.java, String::class.java, String::class.java)

    private val logsPullMethod = GoReflect.method(cls, "LogsPull", String::class.java, String::class.java)

    private val sendAndAwaitMethod = GoReflect.method(
        cls,
        "SendAndAwait",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )

    fun ensureInit(workDir: String) {
        ensureInitMethod.invoke(null, workDir)
    }

    fun connect(addr: String) {
        connectMethod.invoke(null, addr)
    }

    fun close() {
        closeMethod.invoke(null)
    }

    fun isConnected(): Boolean = (isConnectedMethod.invoke(null) as? Boolean) ?: false

    fun lastAddr(): String = (lastAddrMethod.invoke(null) as? String) ?: ""

    fun authState(): String = (authStateMethod.invoke(null) as? String) ?: "{}"

    fun clearAuth() {
        clearAuthMethod.invoke(null)
    }

    fun ensureKeys(): String = (ensureKeysMethod.invoke(null) as? String) ?: ""

    fun getSelfNodeId(): String = (getSelfNodeIdMethod.invoke(null) as? String) ?: "0"

    fun lastError(): String = (getLastErrorMethod.invoke(null) as? String) ?: ""

    fun register(deviceId: String): String {
        val result = registerMethod.invoke(null, deviceId) as? String
        if (result == null) {
            throw IllegalStateException("Go Register returned null")
        }
        return result
    }

    fun login(deviceId: String, nodeId: String): String {
        val result = loginMethod.invoke(null, deviceId, nodeId) as? String
        if (result == null) {
            throw IllegalStateException("Go Login returned null")
        }
        return result
    }

    fun listNodes(sourceId: String, targetId: String): String =
        (listNodesMethod.invoke(null, sourceId, targetId) as? String) ?: "{}"

    fun listSubtree(sourceId: String, targetId: String): String =
        (listSubtreeMethod.invoke(null, sourceId, targetId) as? String) ?: "{}"

    fun nodeInfo(sourceId: String, targetId: String): String =
        (nodeInfoMethod.invoke(null, sourceId, targetId) as? String) ?: "{}"

    fun configList(sourceId: String, targetId: String): String =
        (configListMethod.invoke(null, sourceId, targetId) as? String) ?: "{}"

    fun configGet(sourceId: String, targetId: String, key: String): String =
        (configGetMethod.invoke(null, sourceId, targetId, key) as? String) ?: "{}"

    fun configSet(sourceId: String, targetId: String, key: String, value: String): String =
        (configSetMethod.invoke(null, sourceId, targetId, key, value) as? String) ?: "{}"

    fun logsPull(cursor: String, limit: String): String =
        (logsPullMethod.invoke(null, cursor, limit) as? String) ?: "{}"

    fun sendAndAwait(
        subProto: String,
        sourceId: String,
        targetId: String,
        action: String,
        dataJson: String,
        expectAction: String,
        timeoutMs: String,
    ): String {
        return (sendAndAwaitMethod.invoke(null, subProto, sourceId, targetId, action, dataJson, expectAction, timeoutMs) as? String) ?: "{}"
    }
}
