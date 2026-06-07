// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

package ews

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EwsTest {
    @Test
    fun exposesLibraryName() {
        assertEquals("ews", Ews.name)
    }

    @Test
    fun roundTripsManySourceByteStrings() {
        for (sourceSize in 8..32) {
            repeat(256) { sample ->
                val source = testBytes(sourceSize, sample)
                val words = padSourceWithCrc(source)

                assertEquals(
                    expectedWordSize(sourceSize),
                    words.size,
                    "Unexpected word size for $sourceSize bytes, sample $sample"
                )
                assertTrue(
                    words.all { it in 0..0x7ff },
                    "All encoded words should fit into 11 bits"
                )
                assertContentEquals(
                    source,
                    unpadSourceWithCrc(words),
                    "Round-trip failed for $sourceSize bytes, sample $sample"
                )
            }
        }
    }

    @Test
    fun handlesAmbiguousWordSizesDeterministically() {
        val ambiguousPairs = listOf(
            8 to 9,
            12 to 13,
            15 to 16,
            19 to 20,
            23 to 24,
            26 to 27,
            30 to 31,
        )

        for ((shortSize, longSize) in ambiguousPairs) {
            repeat(128) { sample ->
                val shortSource = testBytes(shortSize, sample)
                val longSource = testBytes(longSize, sample + 1000)
                val shortWords = padSourceWithCrc(shortSource)
                val longWords = padSourceWithCrc(longSource)

                assertEquals(
                    shortWords.size,
                    longWords.size,
                    "$shortSize and $longSize byte sources should share the same result word count"
                )
                assertContentEquals(shortSource, unpadSourceWithCrc(shortWords))
                assertContentEquals(longSource, unpadSourceWithCrc(longWords))
            }
        }
    }

    @Test
    fun rejectsInvalidWords() {
        assertFailsWith<IllegalArgumentException> {
            unpadSourceWithCrc(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            unpadSourceWithCrc(listOf(-1))
        }
        assertFailsWith<IllegalArgumentException> {
            unpadSourceWithCrc(listOf(0x800))
        }
    }

    @Test
    fun exposesAvailableEwsVocabularyLanguages() {
        assertEquals(
            listOf(
                "chinese_simplified",
                "chinese_traditional",
                "czech",
                "english",
                "french",
                "italian",
                "japanese",
                "korean",
                "portuguese",
                "russian",
                "spanish",
            ),
            EwsVocabularies.availableLanguages
        )
    }

    @Test
    fun loadsEwsVocabularyWithPrefixLookupAndCodes() {
        val english = EwsVocabularies.load("english")

        assertEquals("english", english.language)
        assertEquals(4, english.minimumDistinctPrefixLength)
        assertEquals(listOf("abandon"), english.wordsMatchingPrefix("aban"))
        assertEquals(listOf("zoo"), english.wordsMatchingPrefix("zoo"))
        assertEquals(0, english.codeOf("abandon"))
        assertEquals(2047, english.codeOf("zoo"))
        assertEquals("abandon", english.wordOf(0))
        assertEquals("zoo", english.wordOf(2047))
    }

    @Test
    fun loadsAllEwsVocabularies() {
        for (language in EwsVocabularies.availableLanguages) {
            val vocabulary = EwsVocabularies.load(language)

            assertEquals(language, vocabulary.language)
            assertTrue(vocabulary.minimumDistinctPrefixLength > 0)
            assertEquals(0, vocabulary.codeOf(vocabulary.wordOf(0)))
            assertEquals(2047, vocabulary.codeOf(vocabulary.wordOf(2047)))
        }
    }

    @Test
    fun rejectsUnknownEwsVocabularyLanguageAndInvalidCodes() {
        assertFailsWith<IllegalArgumentException> {
            EwsVocabularies.load("unknown")
        }
        assertFailsWith<IllegalArgumentException> {
            EwsVocabularies.load("english").wordOf(2048)
        }
        assertFailsWith<IllegalArgumentException> {
            EwsVocabularies.load("english").codeOf("not-an-ews-word")
        }
    }

    @Test
    fun vocabularyRoundTripsByteArrays() {
        val english = EwsVocabularies.load("english")

        for (sourceSize in 0..64) {
            repeat(64) { sample ->
                val source = testBytes(sourceSize, sample + 10_000)
                val words = english.encode(source)

                assertEquals(
                    expectedWordSize(sourceSize),
                    words.size,
                    "Unexpected word size for $sourceSize bytes, sample $sample"
                )
                assertTrue(
                    words.all { english.codeOf(it) in 0..2047 },
                    "Encoded words should all belong to the selected vocabulary"
                )
                assertContentEquals(
                    source,
                    english.decode(words),
                    "Vocabulary byte round-trip failed for $sourceSize bytes, sample $sample"
                )
            }
        }
    }

    @Test
    fun vocabularyRoundTripsUtf8Text() {
        val english = EwsVocabularies.load("english")
        val samples = listOf(
            "",
            "hello EWS",
            "Entropy Word Sequences",
            "unicode: Привет, こんにちは, 안녕하세요",
            "symbols: \u0000 \t \n \uD83D\uDD10",
        )

        for (sample in samples) {
            val words = english.encodeText(sample)
            assertEquals(sample, english.decodeText(words))
        }
    }

    @Test
    fun vocabularyRoundTripsPhrasesWithFlexibleWhitespace() {
        val english = EwsVocabularies.load("english")
        val source = testBytes(31, 1234)
        val phrase = english.encodeToPhrase(source)
        val spacedPhrase = phrase.split(" ").joinToString("\n\t  ", prefix = "  ", postfix = "  ")

        assertContentEquals(source, english.decodePhrase(phrase))
        assertContentEquals(source, english.decodePhrase(spacedPhrase))
    }

    @Test
    fun vocabularyRoundTripsTextPhrases() {
        val english = EwsVocabularies.load("english")
        val text = "EWS text phrase: Привет / こんにちは / 안녕하세요"
        val phrase = english.encodeTextToPhrase(text)

        assertEquals(text, english.decodePhraseToText(phrase))
    }

    @Test
    fun vocabulariesShareCodesAcrossLanguages() {
        val english = EwsVocabularies.load("english")
        val french = EwsVocabularies.load("french")
        val source = testBytes(37, 9876)

        val englishWords = english.encode(source)
        val codes = englishWords.map(english::codeOf)
        val frenchWords = codes.map(french::wordOf)

        assertContentEquals(source, french.decode(frenchWords))
        assertEquals(codes, frenchWords.map(french::codeOf))
    }

    @Test
    fun allVocabularyEncodersUseTheSameNumericCodes() {
        val source = testBytes(24, 4567)
        val english = EwsVocabularies.load("english")
        val expectedCodes = english.encode(source).map(english::codeOf)

        for (language in EwsVocabularies.availableLanguages) {
            val vocabulary = EwsVocabularies.load(language)
            val words = vocabulary.encode(source)

            assertEquals(
                expectedCodes,
                words.map(vocabulary::codeOf),
                "Unexpected code sequence for $language"
            )
            assertContentEquals(source, vocabulary.decode(words))
        }
    }

    @Test
    fun vocabularyDecodeRejectsInvalidInputs() {
        val english = EwsVocabularies.load("english")

        assertFailsWith<IllegalArgumentException> {
            english.decode(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            english.decode(listOf("not-an-ews-word"))
        }
        assertFailsWith<IllegalArgumentException> {
            english.decode(listOf("aban"))
        }
        assertFailsWith<IllegalArgumentException> {
            english.decodePhrase(" \n\t ")
        }
    }

    @Test
    fun vocabularyDecodeRejectsCrcCorruption() {
        val english = EwsVocabularies.load("english")
        val source = testBytes(24, 2222)
        val words = english.encode(source)
        val corruptedWords = firstCrcRejectedSingleWordMutation(english, words)

        assertFailsWith<IllegalArgumentException> {
            english.decode(corruptedWords)
        }
    }

    @Test
    fun ewsTryDecodeFindsExactWordLanguageCandidate() {
        val english = EwsVocabularies.load("english")
        val source = testBytes(19, 301)
        val phrase = english.encodeToPhrase(source)

        val result = Ews.tryDecode(phrase).single { it.language == "english" }

        assertContentEquals(source, result.decoded)
    }

    @Test
    fun ewsTryDecodeAcceptsMinimumDistinctPrefixes() {
        val english = EwsVocabularies.load("english")
        val source = testBytes(28, 302)
        val prefixPhrase = english.encode(source)
            .joinToString(" ") { word -> word.take(english.minimumDistinctPrefixLength) }

        val result = Ews.tryDecode(prefixPhrase).single { it.language == "english" }

        assertContentEquals(source, result.decoded)
    }

    @Test
    fun ewsTryDecodeAcceptsLongerThanMinimumPrefixesWithoutFullWords() {
        val english = EwsVocabularies.load("english")
        val source = testBytes(18, 303)
        val prefixPhrase = english.encode(source).joinToString(" ") { word ->
            word.take((english.minimumDistinctPrefixLength + 1).coerceAtMost(word.length))
        }

        val result = Ews.tryDecode(prefixPhrase).single { it.language == "english" }

        assertContentEquals(source, result.decoded)
    }

    @Test
    fun ewsTryDecodeFindsRussianPrefixLanguageCandidate() {
        val russian = EwsVocabularies.load("russian")
        val source = testBytes(25, 304)
        val prefixPhrase = russian.encode(source)
            .joinToString(" ") { word -> word.take(russian.minimumDistinctPrefixLength) }

        val result = Ews.tryDecode(prefixPhrase).single { it.language == "russian" }

        assertContentEquals(source, result.decoded)
    }

    @Test
    fun ewsTryDecodeReturnsEmptyForTooShortPrefixes() {
        val english = EwsVocabularies.load("english")
        val source = testBytes(17, 305)
        val tooShortPhrase = english.encode(source)
            .joinToString(" ") { word -> word.take(english.minimumDistinctPrefixLength - 1) }

        assertEquals(emptyList(), Ews.tryDecode(tooShortPhrase))
    }

    @Test
    fun ewsTryDecodeReturnsEmptyForUnknownPrefixes() {
        assertEquals(emptyList(), Ews.tryDecode("not-a-word-prefix"))
    }

    @Test
    fun ewsTryDecodeReturnsEmptyForCrcCorruption() {
        val english = EwsVocabularies.load("english")
        val source = testBytes(24, 306)
        val words = english.encode(source)
        val corruptedPhrase = firstCrcRejectedSingleWordMutation(english, words).joinToString(" ")

        assertEquals(emptyList(), Ews.tryDecode(corruptedPhrase))
    }

    @Test
    fun ewsTryDecodePreservesPossibleMultipleLanguageResults() {
        val source = testBytes(29, 307)
        val results = EwsVocabularies.availableLanguages.map { language ->
            val vocabulary = EwsVocabularies.load(language)
            val phrase = vocabulary.encode(source)
                .joinToString(" ") { word -> word.take(vocabulary.minimumDistinctPrefixLength) }
            Ews.tryDecode(phrase).single { it.language == language }
        }

        assertEquals(EwsVocabularies.availableLanguages, results.map { it.language })
        results.forEach { result ->
            assertContentEquals(source, result.decoded)
        }
    }

    private fun expectedWordSize(sourceSize: Int): Int {
        val sourceBits = sourceSize * 8
        return (sourceBits + 4 + 10) / 11
    }

    private fun testBytes(size: Int, sample: Int): ByteArray {
        var state = sample * 0x45d9f3b + size * 0x119de1f3
        return ByteArray(size) { index ->
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            (state + index * 31).toByte()
        }
    }

    private fun firstCrcRejectedSingleWordMutation(
        vocabulary: EwsVocabulary,
        words: List<String>,
    ): List<String> {
        for (index in words.indices) {
            val originalCode = vocabulary.codeOf(words[index])
            for (delta in 1..16) {
                val mutated = words.toMutableList()
                mutated[index] = vocabulary.wordOf((originalCode + delta) and 0x7ff)
                val rejected = runCatching { vocabulary.decode(mutated) }.isFailure
                if (rejected) return mutated
            }
        }
        error("Could not find a CRC-rejected single-word mutation")
    }
}
