package com.myflowhub.android

class GoClientBridge {
    private val cls: Class<*> = GomobileLoader.loadHubClass()

    private val ensureInitMethod = cls.getMethod("EnsureInit", String::class.java)
    private val connectMethod = cls.getMethod("Connect", String::class.java)
    private val closeMethod = cls.getMethod("Close")
    private val isConnectedMethod = cls.getMethod("IsConnected")
    private val lastAddrMethod = cls.getMethod("LastAddr")
    private val authStateMethod = cls.getMethod("AuthState")
    private val clearAuthMethod = cls.getMethod("ClearAuth")
    private val ensureKeysMethod = cls.getMethod("EnsureKeys")
    private val registerMethod = cls.getMethod("Register", String::class.java)
    private val loginMethod = cls.getMethod("Login", String::class.java, String::class.java)
    private val getSelfNodeIdMethod = cls.getMethod("GetSelfNodeID")
    private val getLastErrorMethod = cls.getMethod("GetLastError")

    private val listNodesMethod = cls.getMethod("ListNodes", String::class.java, String::class.java)
    private val listSubtreeMethod = cls.getMethod("ListSubtree", String::class.java, String::class.java)
    private val nodeInfoMethod = cls.getMethod("NodeInfo", String::class.java, String::class.java)
    private val configListMethod = cls.getMethod("ConfigList", String::class.java, String::class.java)
    private val configGetMethod = cls.getMethod("ConfigGet", String::class.java, String::class.java, String::class.java)
    private val configSetMethod = cls.getMethod("ConfigSet", String::class.java, String::class.java, String::class.java, String::class.java)

    private val logsPullMethod = cls.getMethod("LogsPull", String::class.java, String::class.java)

    private val sendAndAwaitMethod = cls.getMethod(
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

    fun register(deviceId: String): String = (registerMethod.invoke(null, deviceId) as? String) ?: "{}"

    fun login(deviceId: String, nodeId: String): String = (loginMethod.invoke(null, deviceId, nodeId) as? String) ?: "{}"

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
