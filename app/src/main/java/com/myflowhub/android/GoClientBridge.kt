package com.myflowhub.android
// 本文件实现 Android 宿主中与 `GoClientBridge` 相关的逻辑。

import java.lang.reflect.Method

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
    private val fileListMethod = GoReflect.method(
        cls,
        "FileList",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val fileReadTextMethod = GoReflect.method(
        cls,
        "FileReadText",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val fileCreateDirMethod = GoReflect.method(
        cls,
        "FileCreateDir",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val filePullMethod = GoReflect.method(
        cls,
        "FilePull",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )
    private val fileOfferMethod = GoReflect.method(
        cls,
        "FileOffer",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
    )

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

    private fun invoke(method: Method, vararg args: Any?): Any? =
        GoReflect.invokeStatic(method, *args)

    private fun invokeString(method: Method, defaultValue: String, vararg args: Any?): String =
        (invoke(method, *args) as? String) ?: defaultValue

    private fun requireString(method: Method, methodLabel: String, vararg args: Any?): String {
        val result = invoke(method, *args) as? String
        if (result == null) {
            throw IllegalStateException("Go $methodLabel returned null")
        }
        return result
    }

    fun ensureInit(workDir: String) {
        invoke(ensureInitMethod, workDir)
    }

    fun connect(addr: String) {
        invoke(connectMethod, addr)
    }

    fun close() {
        invoke(closeMethod)
    }

    fun isConnected(): Boolean = (invoke(isConnectedMethod) as? Boolean) ?: false

    fun lastAddr(): String = invokeString(lastAddrMethod, "")

    fun authState(): String = invokeString(authStateMethod, "{}")

    fun clearAuth() {
        invoke(clearAuthMethod)
    }

    fun ensureKeys(): String = invokeString(ensureKeysMethod, "")

    fun getSelfNodeId(): String = invokeString(getSelfNodeIdMethod, "0")

    fun lastError(): String = runCatching { invokeString(getLastErrorMethod, "") }.getOrDefault("")

    fun register(deviceId: String): String {
        return requireString(registerMethod, "Register", deviceId)
    }

    fun login(deviceId: String, nodeId: String): String {
        return requireString(loginMethod, "Login", deviceId, nodeId)
    }

    fun listNodes(sourceId: String, targetId: String): String =
        invokeString(listNodesMethod, "{}", sourceId, targetId)

    fun listSubtree(sourceId: String, targetId: String): String =
        invokeString(listSubtreeMethod, "{}", sourceId, targetId)

    fun nodeInfo(sourceId: String, targetId: String): String =
        invokeString(nodeInfoMethod, "{}", sourceId, targetId)

    fun configList(sourceId: String, targetId: String): String =
        invokeString(configListMethod, "{}", sourceId, targetId)

    fun configGet(sourceId: String, targetId: String, key: String): String =
        invokeString(configGetMethod, "{}", sourceId, targetId, key)

    fun configSet(sourceId: String, targetId: String, key: String, value: String): String =
        invokeString(configSetMethod, "{}", sourceId, targetId, key, value)

    fun logsPull(cursor: String, limit: String): String =
        invokeString(logsPullMethod, "{}", cursor, limit)

    fun fileList(sourceId: String, hubId: String, targetId: String, dir: String): String {
        return requireString(fileListMethod, "FileList", sourceId, hubId, targetId, dir)
    }

    fun fileReadText(sourceId: String, hubId: String, targetId: String, dir: String, name: String, maxBytes: String = "65536"): String {
        return requireString(fileReadTextMethod, "FileReadText", sourceId, hubId, targetId, dir, name, maxBytes)
    }

    fun fileCreateDir(sourceId: String, hubId: String, targetId: String, dir: String, name: String): String {
        return requireString(fileCreateDirMethod, "FileCreateDir", sourceId, hubId, targetId, dir, name)
    }

    fun filePull(sourceId: String, hubId: String, targetId: String, dir: String, name: String, wantHash: String = "true", localBaseDir: String): String {
        return requireString(filePullMethod, "FilePull", sourceId, hubId, targetId, dir, name, wantHash, localBaseDir)
    }

    fun fileOffer(sourceId: String, hubId: String, targetId: String, dir: String, name: String, wantHash: String = "true", localBaseDir: String): String {
        return requireString(fileOfferMethod, "FileOffer", sourceId, hubId, targetId, dir, name, wantHash, localBaseDir)
    }

    fun varStoreList(sourceId: String, targetId: String, owner: String): String {
        return requireString(varStoreListMethod, "VarStoreList", sourceId, targetId, owner)
    }

    fun varStoreGet(sourceId: String, targetId: String, name: String, owner: String): String {
        return requireString(varStoreGetMethod, "VarStoreGet", sourceId, targetId, name, owner)
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
        return requireString(varStoreSetMethod, "VarStoreSet", sourceId, targetId, name, value, visibility, type, owner)
    }

    fun varStoreRevoke(sourceId: String, targetId: String, name: String, owner: String): String {
        return requireString(varStoreRevokeMethod, "VarStoreRevoke", sourceId, targetId, name, owner)
    }

    fun varStoreSubscribe(sourceId: String, targetId: String, name: String, owner: String, subscriber: String): String {
        return requireString(varStoreSubscribeMethod, "VarStoreSubscribe", sourceId, targetId, name, owner, subscriber)
    }

    fun varStoreUnsubscribe(sourceId: String, targetId: String, name: String, owner: String, subscriber: String): String {
        return requireString(varStoreUnsubscribeMethod, "VarStoreUnsubscribe", sourceId, targetId, name, owner, subscriber)
    }

    fun varStoreEventsPull(cursor: String, limit: String): String =
        invokeString(varStoreEventsPullMethod, "{}", cursor, limit)

    fun topicBusSubscribe(sourceId: String, targetId: String, topic: String): String {
        return requireString(topicBusSubscribeMethod, "TopicBusSubscribe", sourceId, targetId, topic)
    }

    fun topicBusSubscribeBatch(sourceId: String, targetId: String, topicsJson: String): String {
        return requireString(topicBusSubscribeBatchMethod, "TopicBusSubscribeBatch", sourceId, targetId, topicsJson)
    }

    fun topicBusUnsubscribe(sourceId: String, targetId: String, topic: String): String {
        return requireString(topicBusUnsubscribeMethod, "TopicBusUnsubscribe", sourceId, targetId, topic)
    }

    fun topicBusUnsubscribeBatch(sourceId: String, targetId: String, topicsJson: String): String {
        return requireString(topicBusUnsubscribeBatchMethod, "TopicBusUnsubscribeBatch", sourceId, targetId, topicsJson)
    }

    fun topicBusPublish(sourceId: String, targetId: String, topic: String, name: String, payloadText: String) {
        invoke(topicBusPublishMethod, sourceId, targetId, topic, name, payloadText)
    }

    fun topicBusEventsPull(cursor: String, limit: String): String =
        invokeString(topicBusEventsPullMethod, "{}", cursor, limit)

    fun sendAndAwait(
        subProto: String,
        sourceId: String,
        targetId: String,
        action: String,
        dataJson: String,
        expectAction: String,
        timeoutMs: String,
    ): String {
        return invokeString(sendAndAwaitMethod, "{}", subProto, sourceId, targetId, action, dataJson, expectAction, timeoutMs)
    }
}
