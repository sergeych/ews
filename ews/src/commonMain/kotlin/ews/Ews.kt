package ews

object Ews {
    const val name: String = "ews"
}

private const val BitsInByte = 8
private const val EwsWordSizeBits = 11
private const val MinCrcBits = 4
private const val MaxCrcBits = 14
private const val GuardBits = 8

/**
 * Encode source bytes into 11-bit EWS words with enough check bits to fill the
 * last word. In ambiguous-size cases, 8 of those check bits are used as a guard
 * byte that makes the shorter and longer source interpretations disjoint.
 */
fun padSourceWithCrc(source: ByteArray): List<Int> {
    val sourceBits = BitsInByte * source.size
    val resultWords = (sourceBits + MinCrcBits + EwsWordSizeBits - 1) / EwsWordSizeBits
    val addedBits = resultWords * EwsWordSizeBits - sourceBits

    val bits = ArrayList<Int>(resultWords * EwsWordSizeBits)
    appendByteArrayBits(bits, source)

    if (addedBits > MaxCrcBits) {
        error("Internal error: CRC padding is too large: $addedBits")
    }

    if (usesGuard(addedBits)) {
        val crcBits = addedBits - GuardBits
        val crc = calculateCrc(source, crcBits)
        val guard = findGuardByte(source, crcBits, crc)
        appendByteBits(bits, guard)
        appendIntBits(bits, crc, crcBits)
    } else {
        val crc = calculateCrc(source, addedBits)
        appendIntBits(bits, crc, addedBits)
    }

    return bitsToWords(bits)
}

/**
 * Decode words produced by [padSourceWithCrc], verify their CRC/guard, and
 * return the original source bytes.
 *
 * @throws IllegalArgumentException if a word is outside the 11-bit range or the
 *      encoded data has no valid deterministic source interpretation
 */
fun unpadSourceWithCrc(words: List<Int>): ByteArray {
    require(words.isNotEmpty()) { "EWS word sequence is empty" }
    words.forEach { word ->
        require(word in 0 until (1 shl EwsWordSizeBits)) {
            "EWS word is outside the 11-bit range: $word"
        }
    }

    val bits = wordsToBits(words)
    val candidates = (MinCrcBits..MaxCrcBits).filter { addedBits ->
        (bits.size - addedBits) >= 0 && (bits.size - addedBits) % BitsInByte == 0
    }

    val decoded = candidates.mapNotNull { addedBits ->
        decodeCandidate(bits, addedBits)
    }

    require(decoded.size == 1) {
        "EWS word sequence has ${decoded.size} valid source interpretations"
    }

    return decoded.single()
}

private fun decodeCandidate(bits: List<Int>, addedBits: Int): ByteArray? {
    val sourceBits = bits.size - addedBits

    return if (usesGuard(addedBits)) {
        val crcBits = addedBits - GuardBits
        val source = bitsToBytes(bits, 0, sourceBits)
        val guard = bitsToInt(bits, sourceBits, GuardBits)
        val encodedCrc = bitsToInt(bits, sourceBits + GuardBits, crcBits)
        val sourceWithGuard = source + guard.toByte()

        if (
            calculateCrc(source, crcBits) == encodedCrc &&
            calculateCrc(sourceWithGuard, crcBits) != encodedCrc
        ) {
            source
        } else {
            null
        }
    } else {
        val source = bitsToBytes(bits, 0, sourceBits)
        val encodedCrc = bitsToInt(bits, sourceBits, addedBits)

        if (calculateCrc(source, addedBits) == encodedCrc) source else null
    }
}

private fun usesGuard(addedBits: Int): Boolean = addedBits >= 12

private fun findGuardByte(source: ByteArray, crcBits: Int, sourceCrc: Int): Int {
    for (guard in 0..0xff) {
        if (calculateCrc(source + guard.toByte(), crcBits) != sourceCrc) {
            return guard
        }
    }
    error("Could not find a CRC guard byte for $crcBits-bit CRC")
}

private fun calculateCrc(source: ByteArray, crcBits: Int): Int {
    require(crcBits in MinCrcBits..MaxCrcBits) {
        "CRC size should be in $MinCrcBits..$MaxCrcBits bits: $crcBits"
    }

    val topBit = 1 shl (crcBits - 1)
    val mask = (1 shl crcBits) - 1
    val polynomial = crcPolynomial(crcBits)
    var crc = mask

    for (byte in source) {
        var value = byte.toInt() and 0xff
        repeat(BitsInByte) {
            val inputBit = (value and 0x80) != 0
            value = (value shl 1) and 0xff

            val carry = (crc and topBit) != 0
            crc = (crc shl 1) and mask
            if (carry xor inputBit) {
                crc = crc xor polynomial
            }
        }
    }

    return crc and mask
}

private fun crcPolynomial(crcBits: Int): Int = when (crcBits) {
    4 -> 0x3
    5 -> 0x05
    6 -> 0x03
    7 -> 0x09
    8 -> 0x07
    9 -> 0x11
    10 -> 0x09
    11 -> 0x05
    12 -> 0x80f
    13 -> 0x1cf5
    14 -> 0x0805
    else -> error("Unsupported CRC size: $crcBits")
}

private fun appendByteArrayBits(bits: MutableList<Int>, bytes: ByteArray) {
    for (byte in bytes) {
        appendByteBits(bits, byte.toInt() and 0xff)
    }
}

private fun appendByteBits(bits: MutableList<Int>, byte: Int) {
    appendIntBits(bits, byte, BitsInByte)
}

private fun appendIntBits(bits: MutableList<Int>, value: Int, size: Int) {
    for (shift in size - 1 downTo 0) {
        bits += (value shr shift) and 1
    }
}

private fun bitsToWords(bits: List<Int>): List<Int> {
    require(bits.size % EwsWordSizeBits == 0) {
        "Bit sequence size should be a multiple of $EwsWordSizeBits: ${bits.size}"
    }

    return bits.chunked(EwsWordSizeBits).map { wordBits ->
        wordBits.fold(0) { word, bit -> (word shl 1) or bit }
    }
}

private fun wordsToBits(words: List<Int>): List<Int> = buildList(words.size * EwsWordSizeBits) {
    for (word in words) {
        appendIntBits(this, word, EwsWordSizeBits)
    }
}

private fun bitsToBytes(bits: List<Int>, offset: Int, size: Int): ByteArray {
    require(size % BitsInByte == 0) {
        "Bit sequence size should be a multiple of $BitsInByte: $size"
    }

    return ByteArray(size / BitsInByte) { index ->
        bitsToInt(bits, offset + index * BitsInByte, BitsInByte).toByte()
    }
}

private fun bitsToInt(bits: List<Int>, offset: Int, size: Int): Int {
    var value = 0
    for (index in offset until offset + size) {
        value = (value shl 1) or bits[index]
    }
    return value
}
