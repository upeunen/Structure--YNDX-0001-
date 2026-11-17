package ru.yandex.station.remote.adb

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AdbProtocol {
    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257

    const val A_VERSION = 0x01000000
    const val MAX_PAYLOAD = 4096

    fun calculateChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }

    data class Message(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val data: ByteArray = ByteArray(0)
    ) {
        val magic: Int get() = command xor -1
        val dataLength: Int get() = data.size
        val checksum: Int get() = calculateChecksum(data)

        fun write(out: OutputStream) {
            val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(command)
            header.putInt(arg0)
            header.putInt(arg1)
            header.putInt(dataLength)
            header.putInt(checksum)
            header.putInt(magic)
            out.write(header.array())
            if (data.isNotEmpty()) {
                out.write(data)
            }
            out.flush()
        }

        companion object {
            fun read(input: InputStream): Message {
                val headerBuf = ByteArray(24)
                var bytesRead = 0
                while (bytesRead < 24) {
                    val r = input.read(headerBuf, bytesRead, 24 - bytesRead)
                    if (r < 0) throw java.io.EOFException("Подключение закрыто удаленным устройством")
                    bytesRead += r
                }
                val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
                val cmd = bb.getInt()
                val arg0 = bb.getInt()
                val arg1 = bb.getInt()
                val length = bb.getInt()
                val chk = bb.getInt()
                val magic = bb.getInt()

                if (cmd != (magic xor -1)) {
                    throw IllegalStateException("Поврежден заголовок пакета ADB (контрольная сумма magic не совпадает)")
                }

                val payload = if (length > 0) {
                    val buf = ByteArray(length)
                    var payloadRead = 0
                    while (payloadRead < length) {
                        val r = input.read(buf, payloadRead, length - payloadRead)
                        if (r < 0) throw java.io.EOFException("Обрыв связи при чтении данных ADB")
                        payloadRead += r
                    }
                    buf
                } else {
                    ByteArray(0)
                }

                return Message(cmd, arg0, arg1, payload)
            }
        }
    }
}
