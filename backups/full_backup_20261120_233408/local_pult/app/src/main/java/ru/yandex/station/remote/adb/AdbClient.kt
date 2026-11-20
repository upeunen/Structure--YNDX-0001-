package ru.yandex.station.remote.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val durationMs: Long,
    val tcpConnectMs: Long = 0L,
    val handshakeMs: Long = 0L,
    val errorMessage: String? = null
)

class AdbClient(
    val host: String,
    val port: Int = 5555,
    private val timeoutMs: Int = 12000
) {
    suspend fun openSession() = withContext(Dispatchers.IO) {
        // Заглушка: команды открывают собственные чистые сессии с быстрым сбросом TIME_WAIT
    }

    fun closeSession() {
        // Заглушка
    }

    suspend fun execute(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.reuseAddress = true
                socket.tcpNoDelay = true
                socket.setSoLinger(true, 0)
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                // 1. Рукопожатие CNXN
                val cnxnData = "host::\u0000".toByteArray(StandardCharsets.UTF_8)
                val cnxnMsg = AdbProtocol.Message(
                    command = AdbProtocol.A_CNXN,
                    arg0 = AdbProtocol.A_VERSION,
                    arg1 = AdbProtocol.MAX_PAYLOAD,
                    data = cnxnData
                )
                cnxnMsg.write(out)

                val cnxnResponse = AdbProtocol.Message.read(input)
                if (cnxnResponse.command != AdbProtocol.A_CNXN) {
                    return@withContext Result.failure(
                        Exception("Устройство отклонило подключение ADB")
                    )
                }

                // 2. Открытие shell-сессии
                val localId = 1
                val cmdData = "shell:$command\u0000".toByteArray(StandardCharsets.UTF_8)
                val openMsg = AdbProtocol.Message(
                    command = AdbProtocol.A_OPEN,
                    arg0 = localId,
                    arg1 = 0,
                    data = cmdData
                )
                openMsg.write(out)

                // 3. Подтверждение OKAY
                val openResponse = AdbProtocol.Message.read(input)
                if (openResponse.command != AdbProtocol.A_OKAY) {
                    return@withContext Result.failure(
                        Exception("Команда не принята устройством")
                    )
                }

                val remoteId = openResponse.arg0
                val outputBuilder = StringBuilder()

                // 4. Чтение пакетов
                try {
                    while (true) {
                        val packet = AdbProtocol.Message.read(input)
                        when (packet.command) {
                            AdbProtocol.A_WRTE -> {
                                if (packet.data.isNotEmpty()) {
                                    outputBuilder.append(String(packet.data, StandardCharsets.UTF_8))
                                }
                                val ackMsg = AdbProtocol.Message(
                                    command = AdbProtocol.A_OKAY,
                                    arg0 = localId,
                                    arg1 = remoteId
                                )
                                ackMsg.write(out)
                            }
                            AdbProtocol.A_CLSE -> {
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Команды выполняются мгновенно, таймаут чтения вывода ожидаем
                }

                Result.success(outputBuilder.toString().trim())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeFast(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        val res = execute(command)
        if (res.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Ошибка выполнения ADB"))
        }
    }

    suspend fun testConnectionDetailed(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTotal = System.currentTimeMillis()
        var tcpConnectMs = 0L
        try {
            kotlinx.coroutines.withTimeout(6500L) {
                Socket().use { socket ->
                    socket.reuseAddress = true
                    socket.tcpNoDelay = true
                    socket.setSoLinger(true, 0)
                    socket.soTimeout = 4500

                    val startTcp = System.currentTimeMillis()
                    socket.connect(InetSocketAddress(host, port), 4500)
                    tcpConnectMs = System.currentTimeMillis() - startTcp

                    val out = socket.getOutputStream()
                    val input = socket.getInputStream()

                    val startHandshake = System.currentTimeMillis()
                    val cnxnData = "host::\u0000".toByteArray(StandardCharsets.UTF_8)
                    val cnxnMsg = AdbProtocol.Message(
                        command = AdbProtocol.A_CNXN,
                        arg0 = AdbProtocol.A_VERSION,
                        arg1 = AdbProtocol.MAX_PAYLOAD,
                        data = cnxnData
                    )
                    cnxnMsg.write(out)
                    val response = AdbProtocol.Message.read(input)
                    val handshakeMs = System.currentTimeMillis() - startHandshake

                    if (response.command == AdbProtocol.A_CNXN) {
                        ConnectionTestResult(
                            isSuccess = true,
                            durationMs = System.currentTimeMillis() - startTotal,
                            tcpConnectMs = tcpConnectMs,
                            handshakeMs = handshakeMs
                        )
                    } else {
                        ConnectionTestResult(
                            isSuccess = false,
                            durationMs = System.currentTimeMillis() - startTotal,
                            tcpConnectMs = tcpConnectMs,
                            handshakeMs = handshakeMs,
                            errorMessage = "Отклонено устройством (код ${response.command})"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            val err = when (e) {
                is java.net.SocketTimeoutException -> "Таймаут сокета Станции (>4.5 сек)"
                is java.net.ConnectException -> "Порт $port отклонил подключение (${e.message ?: "нет ответа"})"
                is java.net.NoRouteToHostException -> "Нет маршрута к $host (телефон не в домашней Wi-Fi сети)"
                is kotlinx.coroutines.TimeoutCancellationException -> "Превышен общий лимит ожидания (6.5 сек)"
                else -> "${e.javaClass.simpleName}: ${e.message ?: "ошибка сети"}"
            }
            ConnectionTestResult(
                isSuccess = false,
                durationMs = System.currentTimeMillis() - startTotal,
                tcpConnectMs = tcpConnectMs,
                errorMessage = err
            )
        }
    }

    suspend fun testConnection(): Boolean = testConnectionDetailed().isSuccess
}
