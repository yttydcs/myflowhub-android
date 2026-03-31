package com.myflowhub.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * Install Android RFCOMM (Bluetooth Classic) provider for Go transport layer.
 *
 * Design:
 * - Avoid hard-coding gomobile-generated package/class names (they depend on build flags / -javapkg).
 * - Discover the provider interface type via reflection on Hubmobile.* methods.
 */
internal object BluetoothRfcommProvider {
    fun installIfAvailable(hubCls: Class<*>) {
        val setMethod = findSetProviderMethod(hubCls) ?: return
        val providerIface = setMethod.parameterTypes.firstOrNull() ?: return
        val provider = createProviderProxy(providerIface)
        runCatching { GoReflect.invokeStatic(setMethod, provider) }
    }

    private fun findSetProviderMethod(hubCls: Class<*>): Method? {
        val names = setOf("SetRFCOMMProvider", "setRFCOMMProvider", "SetRfcommProvider", "setRfcommProvider")
        return hubCls.methods.firstOrNull { m ->
            m.parameterTypes.size == 1 && names.contains(m.name)
        }
    }

    private fun createProviderProxy(providerIface: Class<*>): Any {
        return Proxy.newProxyInstance(providerIface.classLoader, arrayOf(providerIface)) { _, method, args ->
            when (method.name.lowercase()) {
                "listen" -> {
                    val uuid = (args?.getOrNull(0) as? String).orEmpty()
                    val secure = (args?.getOrNull(1) as? Boolean) ?: true
                    createListenerProxy(method.returnType, uuid, secure)
                }
                "dial" -> {
                    val bdaddr = (args?.getOrNull(0) as? String).orEmpty()
                    val uuid = (args?.getOrNull(1) as? String).orEmpty()
                    val secure = (args?.getOrNull(3) as? Boolean) ?: true
                    createDialPipeProxy(method.returnType, bdaddr, uuid, secure)
                }
                "tostring" -> "BluetoothRfcommProvider(proxy)"
                "hashcode" -> System.identityHashCode(this)
                "equals" -> false
                else -> throw UnsupportedOperationException("unsupported provider method: ${method.name}")
            }
        }
    }

    private fun createListenerProxy(listenerIface: Class<*>, uuid: String, secure: Boolean): Any {
        return withBluetoothPermissionHint("开启 RFCOMM 监听") {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("BluetoothAdapter 不可用")
            if (!adapter.isEnabled) {
                throw IllegalStateException("蓝牙未开启")
            }
            val u = UUID.fromString(uuid)
            val serverSocket = if (secure) {
                adapter.listenUsingRfcommWithServiceRecord("MyFlowHub", u)
            } else {
                adapter.listenUsingInsecureRfcommWithServiceRecord("MyFlowHub", u)
            }

            Proxy.newProxyInstance(listenerIface.classLoader, arrayOf(listenerIface)) { _, method, _ ->
                when (method.name.lowercase()) {
                    "accept" -> {
                        val socket = serverSocket.accept()
                        createAcceptedPipeProxy(method.returnType, socket)
                    }
                    "close" -> {
                        serverSocket.close()
                        null
                    }
                    "addr" -> "rfcomm(uuid=$uuid,secure=$secure)"
                    "tostring" -> "AndroidRFCOMMListener(proxy)"
                    "hashcode" -> System.identityHashCode(serverSocket)
                    "equals" -> false
                    else -> throw UnsupportedOperationException("unsupported listener method: ${method.name}")
                }
            }
        }
    }

    private fun createDialPipeProxy(pipeIface: Class<*>, bdaddr: String, uuid: String, secure: Boolean): Any {
        return withBluetoothPermissionHint("建立 RFCOMM 连接") {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("BluetoothAdapter 不可用")
            if (!adapter.isEnabled) {
                throw IllegalStateException("蓝牙未开启")
            }
            adapter.cancelDiscovery()
            val device = adapter.getRemoteDevice(bdaddr)
            val u = UUID.fromString(uuid)
            val socket = if (secure) {
                device.createRfcommSocketToServiceRecord(u)
            } else {
                device.createInsecureRfcommSocketToServiceRecord(u)
            }
            socket.connect()
            createPipeProxy(pipeIface, socket)
        }
    }

    private fun createAcceptedPipeProxy(pipeIface: Class<*>, socket: BluetoothSocket): Any {
        return createPipeProxy(pipeIface, socket)
    }

    private fun createPipeProxy(pipeIface: Class<*>, socket: BluetoothSocket): Any {
        val input: InputStream = socket.inputStream
        val output: OutputStream = socket.outputStream
        val remoteAddr: String = runCatching { socket.remoteDevice?.address ?: "" }.getOrDefault("")

        return Proxy.newProxyInstance(pipeIface.classLoader, arrayOf(pipeIface)) { _, method, args ->
            when (method.name.lowercase()) {
                "read" -> {
                    val b = args?.getOrNull(0) as? ByteArray ?: ByteArray(0)
                    input.read(b)
                }
                "write" -> {
                    val b = args?.getOrNull(0) as? ByteArray ?: ByteArray(0)
                    output.write(b)
                    b.size
                }
                "close" -> {
                    socket.close()
                    null
                }
                "remotebdaddr" -> remoteAddr
                "tostring" -> "AndroidRFCOMMPipe(proxy,$remoteAddr)"
                "hashcode" -> System.identityHashCode(socket)
                "equals" -> false
                else -> throw UnsupportedOperationException("unsupported pipe method: ${method.name}")
            }
        }
    }

    private inline fun <T> withBluetoothPermissionHint(action: String, block: () -> T): T {
        return try {
            block()
        } catch (e: SecurityException) {
            throw IllegalStateException("${BluetoothRfcommSupport.permissionDeniedMessage()} 无法$action。", e)
        }
    }
}

