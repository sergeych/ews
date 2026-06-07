# EWS: Entropy Word Sequences

EWS is a small Kotlin Multiplatform library and draft protocol for encoding
arbitrary bytes as human-friendly word sequences, then decoding them back with a
compact CRC check.

It is meant for cases where random binary material has to be copied, printed,
read aloud, or entered by hand. EWS does not generate entropy, derive keys,
stretch passwords, or define wallet semantics. Bring your own RNG and crypto
library; EWS only handles byte-to-word and word-to-byte conversion.

## Why EWS

- **Arbitrary byte arrays**: encode 0 bytes, 32-byte seeds, private keys, or
  larger binary blobs.
- **No PBKDF step**: decoding is dictionary lookup plus CRC validation.
- **Language-independent codes**: words map to 11-bit positions, so the same
  byte sequence can be represented in any available vocabulary.
- **Prefix-friendly input**: every vocabulary exposes the minimum prefix length
  needed for unique word completion.
- **Language auto-detection**: a phrase can be tried against all bundled
  vocabularies and decoded if a CRC-valid interpretation exists.
- **Deterministic padding checks**: padding bits are used for CRC/guard data;
  see [EWS-PADDING.md](EWS-PADDING.md).

## Not BIP39

EWS intentionally is **not compatible with BIP39**. It reuses familiar 2048-word
vocabulary texts where possible, but not the BIP39 mnemonic format, checksum,
PBKDF2 seed derivation, or wallet conventions.

Vocabulary texts are copied with thanks from the Bitcoin BIPs repository:

<https://github.com/bitcoin/bips/tree/master/bip-0039>

The Russian vocabulary is included as an EWS vocabulary as well.

## Status

This repository currently contains:

- a Kotlin Multiplatform implementation;
- bundled vocabularies in `ews/src/commonMain/resources/ews/vocabularies`;
- tests for JVM, JS browser, JS node, and linuxX64;
- the padding/CRC specification in [EWS-PADDING.md](EWS-PADDING.md).

The project is still pre-release. Treat the API and encoded format as draft
until the spec is finalized.

## Build And Test

```bash
./gradlew :ews:allTests
```

Current targets are configured in [ews/build.gradle.kts](ews/build.gradle.kts):

- JVM, toolchain 17
- JS browser
- JS node
- linuxX64

## Basic Usage

Load a vocabulary and encode raw bytes:

```kotlin
import ews.EwsVocabularies

val vocabulary = EwsVocabularies.load("english")
val source: ByteArray = byteArrayOf(0x01, 0x23, 0x45, 0x67)

val words: List<String> = vocabulary.encode(source)
val restored: ByteArray = vocabulary.decode(words)
```

Encode as a phrase:

```kotlin
val phrase: String = vocabulary.encodeToPhrase(source)
val restored: ByteArray = vocabulary.decodePhrase(phrase)
```

Encode UTF-8 text:

```kotlin
val phrase = vocabulary.encodeTextToPhrase("hello EWS")
val text = vocabulary.decodePhraseToText(phrase)
```

## Languages

List bundled vocabularies:

```kotlin
val languages: List<String> = EwsVocabularies.availableLanguages
```

Load any supported language:

```kotlin
val russian = EwsVocabularies.load("russian")
val russianPhrase = russian.encodeTextToPhrase("пример")
```

The same numeric EWS codes can be rendered in another vocabulary:

```kotlin
val english = EwsVocabularies.load("english")
val french = EwsVocabularies.load("french")

val englishWords = english.encode(source)
val codes = englishWords.map(english::codeOf)
val frenchWords = codes.map(french::wordOf)

val restored = french.decode(frenchWords)
```

## Prefix Input

Each vocabulary provides the shortest prefix length that uniquely identifies all
words in that vocabulary:

```kotlin
val minPrefix = vocabulary.minimumDistinctPrefixLength
```

You can use it for completion:

```kotlin
val matches: List<String> = vocabulary.wordsMatchingPrefix("aban")
```

Direct vocabulary decoding expects complete words. For user-entered phrases that
may contain prefixes, use language auto-detection.

## Auto-Detect And Decode

`Ews.tryDecode` tries every bundled language. Each phrase token may be a full
word or a unique prefix of at least the language's minimum prefix length.
Candidates that fail the CRC check are discarded.

```kotlin
import ews.Ews

val candidates = Ews.tryDecode("aban abil ...")

for (candidate in candidates) {
    println(candidate.language)
    println(candidate.decoded.decodeToString())
}
```

The result is a list because more than one language can theoretically pass for a
short CRC-bearing phrase, though normally the list is empty or has one item.

## Low-Level Numeric API

If you want to work below vocabularies, use the 11-bit word-code functions:

```kotlin
import ews.padSourceWithCrc
import ews.unpadSourceWithCrc

val codes: List<Int> = padSourceWithCrc(source)
val restored: ByteArray = unpadSourceWithCrc(codes)
```

Every code is in `0..2047` and can be mapped through any 2048-word EWS
vocabulary.

## Design Notes

EWS stores the source bits followed by CRC bits, and in ambiguous-size cases a
guard byte is inserted before the CRC. The encoded stream is split into 11-bit
words, which map directly to vocabulary positions.

More detail:

- [EWS-PADDING.md](EWS-PADDING.md): CRC padding and 11-bit packing
- [EWS.md](EWS.md): draft protocol notes

## License

EWS is released under the MIT License. See [LICENSE](LICENSE).
