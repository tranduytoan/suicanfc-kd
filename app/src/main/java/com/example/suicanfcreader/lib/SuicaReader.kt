package com.example.suicanfcreader.lib

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale

data class GatePassageRecord(
    val lineCode: Int,
    val stationCode: Int,
    val isEntry: Boolean,
    val date: String,
    val time: String
)

data class GatePassageStationCodes(
    val inLineCode: Int,
    val inStationCode: Int,
    val outLineCode: Int,
    val outStationCode: Int
)

class SuicaReader {
    var termId = 0
    var procId = 0
    var year = 0
    var month = 0
    var day = 0
    var kind = ""
    var remain = 0
    var seqNo = 0
    var regionCode = 0
    var inStation = 0
    var inLine = 0
    var outStation = 0
    var outLine = 0
    var busLine = 0
    var busStop = 0
    var device = ""
    var action = ""

    private fun init(res: ByteArray, off: Int) {
        termId = unsigned(res[off])
        procId = unsigned(res[off + 1])

        val dateBits = intAt(res, off, 4, 5)
        year = (dateBits shr 9) and 0x07f
        month = (dateBits shr 5) and 0x00f
        day = dateBits and 0x01f

        kind = when {
            isCharge(procId) -> "Nạp tiền"
            isShopping(procId) -> "Mua sắm"
            isBus(procId) -> "Xe buýt"
            unsigned(res[off + 6]) < 0x80 -> "JR"
            else -> "Tàu điện/Tư nhân"
        }

        inLine = intAt(res, off, 6)
        inStation = intAt(res, off, 7)
        outLine = intAt(res, off, 8)
        outStation = intAt(res, off, 9)
        busLine = intAt(res, off, 6, 7)
        busStop = intAt(res, off, 8, 9)
        remain = intAt(res, off, 11, 10)
        seqNo = intAt(res, off, 12, 13, 14)
        regionCode = unsigned(res[off + 15])
        device = deviceList[termId] ?: "Thiết bị không xác định"
        action = actionList[procId] ?: kind
    }

    private fun intAt(res: ByteArray, off: Int, vararg indexes: Int): Int {
        var value = 0
        for (index in indexes) {
            value = (value shl 8) + unsigned(res[off + index])
        }
        return value
    }

    fun isStationRecord(): Boolean =
        !isCharge(procId) && !isShopping(procId) && !isBus(procId) &&
            (inLine != 0 || inStation != 0 || outLine != 0 || outStation != 0)

    fun isChargeRecord(): Boolean = isCharge(procId)

    fun isShoppingRecord(): Boolean = isShopping(procId)

    fun isBusRecord(): Boolean = isBus(procId)

    fun hasPlausibleHistoryValues(): Boolean {
        if (year !in 0..99 || month !in 1..12 || day !in 1..daysInMonth(year, month)) return false
        if (remain !in 0..MAX_TRANSIT_BALANCE) return false
        return inLine in BYTE_VALUE_RANGE && inStation in BYTE_VALUE_RANGE &&
            outLine in BYTE_VALUE_RANGE && outStation in BYTE_VALUE_RANGE &&
            regionCode in BYTE_VALUE_RANGE && busLine in BUS_CODE_RANGE && busStop in BUS_CODE_RANGE
    }

    private fun isCharge(value: Int): Boolean = value == 2 || value == 31 || value == 72 || value == 73

    private fun isShopping(value: Int): Boolean = value == 70 || value == 74 || value == 75 || value == 198 || value == 203

    private fun isBus(value: Int): Boolean = value == 13 || value == 15 || value == 31 || value == 35

    companion object {
        private val deviceList = mapOf(
            3 to "Máy điều chỉnh cước", 4 to "Thiết bị cầm tay", 5 to "Thiết bị trên xe", 7 to "Máy bán vé", 8 to "Máy bán vé",
            9 to "Máy nạp tiền", 18 to "Máy bán vé", 20 to "Máy bán vé, v.v.", 21 to "Máy bán vé, v.v.", 22 to "Cổng soát vé",
            23 to "Cổng soát vé đơn giản", 24 to "Quầy giao dịch", 25 to "Quầy giao dịch", 26 to "Thiết bị soát vé", 27 to "Điện thoại di động",
            28 to "Máy chuyển tuyến", 29 to "Cổng chuyển tuyến", 31 to "Máy nạp tiền đơn giản", 70 to "VIEW ALTTE",
            72 to "VIEW ALTTE", 199 to "POS", 200 to "Máy bán hàng tự động"
        )

        private val actionList = mapOf(
            1 to "Thanh toán cước", 2 to "Nạp tiền", 3 to "Mua vé", 4 to "Điều chỉnh cước", 5 to "Vào cổng điều chỉnh",
            6 to "Xử lý tại quầy", 7 to "Phát hành mới", 8 to "Khấu trừ", 13 to "Xe buýt", 15 to "Xe buýt", 17 to "Cấp lại thẻ",
            19 to "Thanh toán", 20 to "Tự động nạp khi vào", 21 to "Tự động nạp khi ra", 31 to "Nạp trên xe buýt",
            35 to "Mua vé xe buýt", 70 to "Mua sắm", 72 to "Nạp điểm thưởng", 73 to "Nạp tại quầy thu ngân", 74 to "Hủy mua hàng",
            75 to "Mua sắm trong ga", 132 to "Thanh toán hãng khác", 133 to "Vào cổng thanh toán hãng khác", 198 to "Mua hàng kèm tiền mặt",
            203 to "Mua hàng trong ga kèm tiền mặt"
        )

        @JvmStatic
        fun parse(res: ByteArray?, off: Int): SuicaReader {
            require(res != null && off >= 0 && res.size - off >= 16) { "Invalid FeliCa history block" }
            return SuicaReader().also { it.init(res, off) }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readWithoutEncryption(idm: ByteArray, size: Int): ByteArray =
            readWithoutEncryption(idm, 0, size)

        @JvmStatic
        @Throws(IOException::class)
        fun readWithoutEncryption(idm: ByteArray, startBlock: Int, size: Int): ByteArray {
            return readWithoutEncryption(idm, startBlock, size, SERVICE_TRANSACTION_HISTORY)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readWithoutEncryption(idm: ByteArray, startBlock: Int, size: Int, serviceCode: Int): ByteArray {
            if (idm.size != 8) throw IOException("Invalid FeliCa IDm")
            if (startBlock !in 0..255 || size !in 1..10 || startBlock + size > 256 || serviceCode !in 0..0xffff) {
                throw IOException("Invalid FeliCa block range")
            }
            return ByteArrayOutputStream(100).use { output ->
                output.write(0)
                output.write(0x06)
                output.write(idm)
                output.write(1)
                // FeliCa service codes are transmitted least-significant byte first.
                output.write(serviceCode and 0xff)
                output.write((serviceCode shr 8) and 0xff)
                output.write(size)
                repeat(size) { index ->
                    output.write(0x80)
                    output.write(startBlock + index)
                }
                output.toByteArray().also { it[0] = it.size.toByte() }
            }
        }

        @JvmStatic
        fun parseGatePassageRecord(data: ByteArray, offset: Int = 0): GatePassageRecord? {
            if (offset < 0 || data.size - offset < HISTORY_BLOCK_SIZE) return null

            val gateType = unsigned(data[offset])
            val deviceCode = unsigned(data[offset + 4])
            val isEntry = when {
                gateType == 0xa0 || gateType == 0xc0 -> true
                gateType == 0x20 || gateType == 0x40 -> false
                deviceCode == 0x10 -> true // JR East tap-on marker
                deviceCode == 0x20 -> false // JR East tap-off marker
                else -> return null
            }

            val dateBits = (unsigned(data[offset + 6]) shl 8) or unsigned(data[offset + 7])
            val year = ((dateBits shr 9) and 0x7f) + 2000
            val month = (dateBits shr 5) and 0x0f
            val day = dateBits and 0x1f
            if (month !in 1..12 || day !in 1..daysInMonth(year, month)) return null

            val hour = fromBcd(unsigned(data[offset + 8])) ?: return null
            val minute = fromBcd(unsigned(data[offset + 9])) ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null

            val lineCode = unsigned(data[offset + 2])
            val stationCode = unsigned(data[offset + 3])
            if (lineCode == 0 && stationCode == 0) return null

            return GatePassageRecord(
                lineCode = lineCode,
                stationCode = stationCode,
                isEntry = isEntry,
                date = "%04d/%02d/%02d".format(Locale.US, year, month, day),
                time = "%02d:%02d".format(Locale.US, hour, minute)
            )
        }

        @JvmStatic
        fun parseGatePassageStationCodes(internalCode: String?): GatePassageStationCodes? {
            val groups = internalCode
                ?.let { INTERNAL_STATION_CODES.matchEntire(it)?.groupValues }
                ?: return null
            return GatePassageStationCodes(
                inLineCode = groups[1].toIntOrNull() ?: return null,
                inStationCode = groups[2].toIntOrNull() ?: return null,
                outLineCode = groups[3].toIntOrNull() ?: return null,
                outStationCode = groups[4].toIntOrNull() ?: return null
            )
        }

        private fun fromBcd(value: Int): Int? {
            val tens = (value shr 4) and 0x0f
            val ones = value and 0x0f
            if (tens > 9 || ones > 9) return null
            return tens * 10 + ones
        }

        private fun unsigned(value: Byte): Int = value.toInt() and 0x0ff

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (year % 4 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private val BYTE_VALUE_RANGE = 0..0xff
        private val BUS_CODE_RANGE = 0..0xffff
        private const val MAX_TRANSIT_BALANCE = 20_000
        private const val HISTORY_BLOCK_SIZE = 16
        private val INTERNAL_STATION_CODES = Regex("Area=\\d+ In=(\\d+)/(\\d+) Out=(\\d+)/(\\d+)")
        const val SERVICE_TRANSACTION_HISTORY = 0x090f
        const val SERVICE_GATE_PASSAGE_HISTORY = 0x108f
    }
}
