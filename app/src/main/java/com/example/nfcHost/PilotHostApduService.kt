package com.example.nfcHost

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.util.Arrays

class PilotHostApduService : HostApduService() {

    companion object {
        // Dynamic payload for NDEF emulation (set via platform channel)
        private var currentPayload = "https://coles.opapi.au/o/cpnasbwi75emnx"
        private var isUrl = true

        fun setPayload(payload: String, url: Boolean) {
            currentPayload = payload
            isUrl = url
            Log.d("PilotHostApduService", "Payload set: $payload, isUrl: $url")
        }
    }
    // AID for NFC Forum Type 4 Tag
    private val TYPE4_AID = "D2760000850101"

    // File IDs for CC and NDEF
    private val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
    private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

    // Capability Container (CC) file (15 bytes, standard for NDEF)
    private val CC_FILE = byteArrayOf(
        0x00, 0x0F, 0x20, 0x00, 0x3B, 0x00, 0x34,
        0x04, 0x06, 0xE1.toByte(), 0x04, 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte()
    )

    private var currentFile = 0

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d("Mane", "Received APDU: ${apdu.toHex()}")

        // APDU command parsing (simplified for demo)
        return when {
            isSelectAid(apdu) -> {
                currentFile = 0
                success()
            }

            isSelectFile(apdu, CC_FILE_ID) -> {
                currentFile = 1
                fci()
            }

            isSelectFile(apdu, NDEF_FILE_ID) -> {
                currentFile = 2
                fci()
            }

            isReadBinary(apdu) -> {
                val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
                val le =
                    if (apdu.size > 4) if (apdu[4] == 0.toByte()) 256 else apdu[4].toInt() and 0xFF else 256
                val file = if (currentFile == 1) CC_FILE else getNdefFile()
                if (offset >= file.size) return success()
                val end = offset + le.coerceAtMost(file.size - offset)
                val out = file.copyOfRange(offset, end)
                concat(out, success())
            }

            else -> byteArrayOf(0x6A.toByte(), 0x82.toByte()) // File not found
        }
    }

    override fun onDeactivated(reason: Int) {}

    private fun isSelectAid(apdu: ByteArray): Boolean {
        return apdu.size >= 12 && apdu[1] == 0xA4.toByte() && apdu[2] == 0x04.toByte()
    }

    private fun isSelectFile(apdu: ByteArray, fileId: ByteArray): Boolean {
        return apdu.size >= 7 && apdu[1] == 0xA4.toByte() && apdu[2] == 0x00.toByte()
                && apdu[4] == 0x02.toByte() && apdu[5] == fileId[0] && apdu[6] == fileId[1]
    }

    private fun isReadBinary(apdu: ByteArray): Boolean {
        return apdu.size >= 5 && apdu[1] == 0xB0.toByte()
    }

    private fun success(): ByteArray {
        return byteArrayOf(0x90.toByte(), 0x00)
    }

    private fun fci(): ByteArray {
        return byteArrayOf(0x62.toByte(), 0x00, 0x90.toByte(), 0x00)
    }

    private fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val out = Arrays.copyOf(a, a.size + b.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }

    fun setPayload(payload: String, url: Boolean) {
        currentPayload = payload
        isUrl = url
        Log.d("PilotHostApduService", "Payload set: $payload, isUrl: $url")
    }

    private fun getNdefFile(): ByteArray {
        Log.d("PilotHostApduService", "getNdefFile: payload=$currentPayload, isUrl=$isUrl")
        return if (isUrl) makeNdefUri(currentPayload) else makeNdefText(currentPayload)
    }

    private fun makeNdefUri(url: String): ByteArray {
        // Build a simple NDEF URI record with 2-byte NLEN prefix
        val uriBytes = url.toByteArray()
        val ndefRecord = ByteArray(uriBytes.size + 5)
        ndefRecord[0] = 0xD1.toByte() // MB/ME/Short/Type=Well-known
        ndefRecord[1] = 0x01 // Type Length
        ndefRecord[2] = (uriBytes.size + 1).toByte() // Payload Length
        ndefRecord[3] = 0x55 // Type = 'U' (URI)
        ndefRecord[4] = 0x00 // URI Prefix: none
        System.arraycopy(uriBytes, 0, ndefRecord, 5, uriBytes.size)

        val nlen = ndefRecord.size
        val ndefFile = ByteArray(nlen + 2)
        ndefFile[0] = ((nlen shr 8) and 0xFF).toByte()
        ndefFile[1] = (nlen and 0xFF).toByte()
        System.arraycopy(ndefRecord, 0, ndefFile, 2, nlen)
        return ndefFile
    }

    private fun makeNdefText(text: String): ByteArray {
        val langBytes = "en".toByteArray()
        val textBytes = text.toByteArray()
        val payload = ByteArray(1 + langBytes.size + textBytes.size)
        payload[0] = langBytes.size.toByte()
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

        val ndefRecord = ByteArray(4 + payload.size)
        ndefRecord[0] = 0xD1.toByte() // MB/ME/Short/Type=Well-known
        ndefRecord[1] = 0x01 // Type Length
        ndefRecord[2] = payload.size.toByte()
        ndefRecord[3] = 0x54 // Type = 'T' (Text)
        System.arraycopy(payload, 0, ndefRecord, 4, payload.size)

        val nlen = ndefRecord.size
        val ndefFile = ByteArray(nlen + 2)
        ndefFile[0] = ((nlen shr 8) and 0xFF).toByte()
        ndefFile[1] = (nlen and 0xFF).toByte()
        System.arraycopy(ndefRecord, 0, ndefFile, 2, nlen)
        return ndefFile
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }