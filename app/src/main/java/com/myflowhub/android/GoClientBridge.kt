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

    private val varStoreListMethod = GoReflect.method(
        cls,
        "VarStoreList",
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val varStoreGetMethod = GoReflect.method(
        cls,
        "VarStoreGet",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val varStoreSetMethod = GoReflect.method(
        cls,
        "VarStoreSet",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val varStoreRevokeMethod = GoReflect.method(
        cls,
        "VarStoreRevoke",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val varStoreSubscribeMethod = GoReflect.method(
        cls,
        "VarStoreSubscribe",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val varStoreUnsubscribeMethod = GoReflect.method(
        cls,
        "VarStoreUnsubscribe",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )

    private val varStoreEventsPullMethod = GoReflect.method(
        cls,
        "VarStoreEventsPull",
        String::class.java,
        String::class.java,
    )

    private val topicBusSubscribeMethod = GoReflect.method(
        cls,
        "TopicBusSubscribeSimple",
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val topicBusSubscribeBatchMethod = GoReflect.method(
        cls,
        "TopicBusSubscribeBatchSimple",
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val topicBusUnsubscribeMethod = GoReflect.method(
        cls,
        "TopicBusUnsubscribeSimple",
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val topicBusUnsubscribeBatchMethod = GoReflect.method(
        cls,
        "TopicBusUnsubscribeBatchSimple",
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val topicBusPublishMethod = GoReflect.method(
        cls,
        "TopicBusPublish",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val topicBusEventsPullMethod = GoReflect.method(
        cls,
        "TopicBusEventsPull",
        String::class.java,
        String::class.java,
    )

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

    fun varStoreList(sourceId: String, targetId: String, owner: String): String {
        val result = varStoreListMethod.invoke(null, sourceId, targetId, owner) as? String
        if (result == null) {
            throw IllegalStateException("Go VarStoreList returned null")
        }
        return result
    }

    fun varStoreGet(sourceId: String, targetId: String, name: String, owner: String): String {
        val result = varStoreGetMethod.invoke(null, sourceId, targetId, name, owner) as? String
        if (result == null) {
            throw IllegalStateException("Go VarStoreGet returned null")
        }
        return result
    }

    fun varStoreSet(
        sourceId: String,
        targetId: String,
        name: String,
        value: String,
        visibility: String,
        type: String,
        owner: String,
    ): String {
        val result = varStoreSetMethod.invoke(null, sourceId, targetId, name, value, visibility, type, owner) as? String
        if (result == null) {
            throw IllegalStateException("Go VarStoreSet returned null")
        }
        return result
    }

    fun varStoreRevoke(sourceId: String, targetId: String, name: String, owner: String): String {
        val result = varStoreRevokeMethod.invoke(null, sourceId, targetId, name, owner) as? String
        if (result == null) {
            throw IllegalStateException("Go VarStoreRevoke returned null")
        }
        return result
    }

    fun varStoreSubscribe(sourceId: String, targetId: String, name: String, owner: String, subscriber: String): String {
        val result = varStoreSubscribeMethod.invoke(null, sourceId, targetId, name, owner, subscriber) as? String
        if (result == null) {
            throw IllegalStateException("Go VarStoreSubscribe returned null")
        }
        return result
    }

    fun varStoreUnsubscribe(sourceId: String, targetId: String, name: String, owner: String, subscriber: String): String {
        val result = varStoreUnsubscribeMethod.invoke(null, sourceId, targetId, name, owner, subscriber) as? String
        if (result == null) {
            throw IllegalStateException("Go VarStoreUnsubscribe returned null")
        }
        return result
    }

    fun varStoreEventsPull(cursor: String, limit: String): String =
        (varStoreEventsPullMethod.invoke(null, cursor, limit) as? String) ?: "{}"

    fun topicBusSubscribe(sourceId: String, targetId: String, topic: String): String {
        val result = topicBusSubscribeMethod.invoke(null, sourceId, targetId, topic) as? String
        if (result == null) {
            throw IllegalStateException("Go TopicBusSubscribe returned null")
        }
        return result
    }

    fun topicBusSubscribeBatch(sourceId: String, targetId: String, topicsJson: String): String {
        val result = topicBusSubscribeBatchMethod.invoke(null, sourceId, targetId, topicsJson) as? String
        if (result == null) {
            throw IllegalStateException("Go TopicBusSubscribeBatch returned null")
        }
        return result
    }

    fun topicBusUnsubscribe(sourceId: String, targetId: String, topic: String): String {
        val result = topicBusUnsubscribeMethod.invoke(null, sourceId, targetId, topic) as? String
        if (result == null) {
            throw IllegalStateException("Go TopicBusUnsubscribe returned null")
        }
        return result
    }

    fun topicBusUnsubscribeBatch(sourceId: String, targetId: String, topicsJson: String): String {
        val result = topicBusUnsubscribeBatchMethod.invoke(null, sourceId, targetId, topicsJson) as? String
        if (result == null) {
            throw IllegalStateException("Go TopicBusUnsubscribeBatch returned null")
        }
        return result
    }

    fun topicBusPublish(sourceId: String, targetId: String, topic: String, name: String, payloadText: String) {
        topicBusPublishMethod.invoke(null, sourceId, targetId, topic, name, payloadText)
    }

    fun topicBusEventsPull(cursor: String, limit: String): String =
        (topicBusEventsPullMethod.invoke(null, cursor, limit) as? String) ?: "{}"

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
