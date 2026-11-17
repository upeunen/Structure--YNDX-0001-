package ru.yandex.station.remote.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class AdbClient(
    val host: String,
    val port: Int = 5555,
    private val timeoutMs: Int = 3000
) {
    suspend fun execute(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
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
                    // Команды типа 'am start' и 'input keyevent' выполняются мгновенно, таймаут чтения вывода ожидаем
                }

                Result.success(outputBuilder.toString().trim())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.soTimeout = 2000
                socket.connect(InetSocketAddress(host, port), 2000)
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                val cnxnData = "host::\u0000".toByteArray(StandardCharsets.UTF_8)
                val cnxnMsg = AdbProtocol.Message(
                    command = AdbProtocol.A_CNXN,
                    arg0 = AdbProtocol.A_VERSION,
                    arg1 = AdbProtocol.MAX_PAYLOAD,
                    data = cnxnData
                )
                cnxnMsg.write(out)
                val response = AdbProtocol.Message.read(input)
                response.command == AdbProtocol.A_CNXN
            }
        } catch (_: Exception) {
            false
        }
    }
}
