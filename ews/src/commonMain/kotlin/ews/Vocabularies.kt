// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

package ews

/**
 * A loaded 2048-word EWS vocabulary.
 *
 * Encoding code should use this interface after choosing a language from
 * [EwsVocabularies.availableLanguages] and loading it with [EwsVocabularies.load].
 * The interface is intentionally small so tests or applications can provide
 * their own loaded vocabulary objects without depending on bundled resources.
 */
interface EwsVocabulary {
    /**
     * Stable language id used by [EwsVocabularies], for example `"english"`.
     */
    val language: String

    /**
     * The shortest prefix length that uniquely identifies every word in this
     * vocabulary.
     */
    val minimumDistinctPrefixLength: Int

    /**
     * Return vocabulary words that start with [prefix], preserving vocabulary
     * order. This is intended for user input completion and prefix recovery.
     */
    fun wordsMatchingPrefix(prefix: String): List<String>

    /**
     * Return the 11-bit EWS code for [word].
     *
     * Codes are zero-based indexes in this vocabulary and are therefore always
     * in `0..2047`.
     */
    fun codeOf(word: String): Int

    /**
     * Return the word for an 11-bit EWS [code].
     *
     * Codes are zero-based indexes in this vocabulary and must be in `0..2047`.
     */
    fun wordOf(code: Int): String

    /**
     * Encode raw bytes as EWS words in this vocabulary.
     *
     * The returned words are vocabulary words, not BIP39 mnemonic words. The
     * underlying numeric codes are produced by [padSourceWithCrc].
     */
    fun encode(source: ByteArray): List<String> {
        return padSourceWithCrc(source).map(::wordOf)
    }

    /**
     * Decode EWS words from this vocabulary and return the original bytes.
     *
     * All words must be complete vocabulary words. Prefix matching is available
     * separately through [wordsMatchingPrefix] so callers can resolve user input
     * before decoding.
     */
    fun decode(words: List<String>): ByteArray {
        return unpadSourceWithCrc(words.map(::codeOf))
    }

    /**
     * Encode UTF-8 text as EWS words in this vocabulary.
     */
    fun encodeText(text: String): List<String> {
        return encode(text.encodeToByteArray())
    }

    /**
     * Decode EWS words from this vocabulary and interpret the result as UTF-8
     * text.
     */
    fun decodeText(words: List<String>): String {
        return decode(words).decodeToString()
    }

    /**
     * Encode raw bytes as a separator-delimited EWS phrase.
     */
    fun encodeToPhrase(source: ByteArray, separator: String = " "): String {
        return encode(source).joinToString(separator)
    }

    /**
     * Decode a separator-delimited EWS phrase into the original bytes.
     *
     * Words are split on any Unicode whitespace. Empty or whitespace-only
     * phrases are rejected by the underlying CRC decoder.
     */
    fun decodePhrase(phrase: String): ByteArray {
        return decode(splitPhrase(phrase))
    }

    /**
     * Encode UTF-8 text as a separator-delimited EWS phrase.
     */
    fun encodeTextToPhrase(text: String, separator: String = " "): String {
        return encodeToPhrase(text.encodeToByteArray(), separator)
    }

    /**
     * Decode a separator-delimited EWS phrase and interpret the result as UTF-8
     * text.
     */
    fun decodePhraseToText(phrase: String): String {
        return decodePhrase(phrase).decodeToString()
    }
}

private fun splitPhrase(phrase: String): List<String> {
    return phrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}

/**
 * Built-in EWS vocabulary registry.
 *
 * Use [availableLanguages] to present language choices and [load] to lazily load
 * the selected vocabulary. Loading validates that the resource has exactly 2048
 * distinct words, then caches the resulting [EwsVocabulary].
 *
 * EWS uses familiar vocabulary text files originally published with Bitcoin
 * BIP-0039 where possible. These are only wordlists; EWS encoding is
 * intentionally not compatible with BIP39.
 *
 * Source: https://github.com/bitcoin/bips/tree/master/bip-0039
 */
object EwsVocabularies {
    private const val WordCount = 2048
    private const val ResourceDirectory = "ews/vocabularies"

    val availableLanguages: List<String> = listOf(
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
    )

    private val vocabularies = availableLanguages.associateWith { language ->
        lazy { loadVocabulary(language) }
    }

    fun load(language: String): EwsVocabulary {
        return vocabularies[language]?.value
            ?: throw IllegalArgumentException("Unknown EWS vocabulary language: $language")
    }

    private fun loadVocabulary(language: String): EwsVocabulary {
        val text = readEwsResourceText("$ResourceDirectory/$language.txt")
        val words = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        require(words.size == WordCount) {
            "EWS vocabulary '$language' should contain exactly $WordCount words, got ${words.size}"
        }

        val uniqueWords = words.toSet()
        require(uniqueWords.size == WordCount) {
            "EWS vocabulary '$language' contains duplicate words"
        }

        return ResourceVocabulary(
            language = language,
            words = words,
            wordCodes = words.withIndex().associate { (index, word) -> word to index },
            minimumDistinctPrefixLength = calculateMinimumDistinctPrefixLength(language, words),
        )
    }

    private fun calculateMinimumDistinctPrefixLength(language: String, words: List<String>): Int {
        val maxLength = words.maxOf { it.length }
        for (prefixLength in 1..maxLength) {
            val prefixes = HashSet<String>(words.size)
            if (words.all { prefixes.add(it.take(prefixLength)) }) {
                return prefixLength
            }
        }
        error("EWS vocabulary '$language' has no distinct prefix length")
    }
}

private class ResourceVocabulary(
    override val language: String,
    private val words: List<String>,
    private val wordCodes: Map<String, Int>,
    override val minimumDistinctPrefixLength: Int,
) : EwsVocabulary {
    override fun wordsMatchingPrefix(prefix: String): List<String> {
        return words.filter { it.startsWith(prefix) }
    }

    override fun codeOf(word: String): Int {
        return wordCodes[word]
            ?: throw IllegalArgumentException("Word is not in '$language' vocabulary: $word")
    }

    override fun wordOf(code: Int): String {
        require(code in words.indices) {
            "Vocabulary code should be in 0..${words.lastIndex}: $code"
        }
        return words[code]
    }
}

internal expect fun readEwsResourceText(path: String): String
