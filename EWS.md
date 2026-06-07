# Entropy Word Sequences 1

EWS is a draft format for encoding arbitrary bytes as word sequences and
decoding them back with a compact CRC check. It is designed for data that must
be copied, printed, read aloud, or entered by hand.

EWS is intentionally not compatible with BIP39. It reuses familiar 2048-word
vocabulary files where possible, but it does not use BIP39 mnemonic packing,
checksum rules, PBKDF2, seed derivation, or wallet semantics.

This document describes the wire format and vocabulary rules so independent
implementations can produce compatible encoders and decoders.

## Model

An EWS implementation has two layers:

1. A numeric codec that converts bytes to unsigned 11-bit codes and back.
2. A vocabulary mapping that converts each 11-bit code to a word.

The numeric codec is language-independent. The same byte sequence produces the
same list of numeric codes in every language. A phrase can therefore be rendered
through one vocabulary and decoded through another by preserving the numeric
code indexes.

## Constants

```text
BITS_IN_BYTE = 8
WORD_BITS = 11
WORD_COUNT = 2048
MIN_CRC_BITS = 4
MAX_ADDED_BITS = 14
GUARD_BITS = 8
```

All bit ordering is most-significant-bit first.

Each numeric EWS word is an integer in `0..2047`, inclusive.

## Vocabulary Files

A vocabulary is an ordered list of exactly 2048 distinct non-empty words.

The EWS code for a word is its zero-based index in the vocabulary:

```text
word 0    -> code 0
word 1    -> code 1
...
word 2047 -> code 2047
```

Words should be treated as opaque Unicode strings. Implementations should not
case-fold, normalize, transliterate, or otherwise rewrite vocabulary entries
unless they deliberately define an application-level input policy outside the
EWS format.

Bundled language identifiers:

```text
chinese_simplified
chinese_traditional
czech
english
french
italian
japanese
korean
portuguese
russian
spanish
```

The current vocabulary texts are copied with thanks from the Bitcoin BIPs
repository:

```text
https://github.com/bitcoin/bips/tree/master/bip-0039
```

Only the vocabulary texts are reused.

## Phrases

A phrase is a whitespace-separated list of vocabulary words.

Recommended phrase parsing:

1. Trim leading and trailing whitespace.
2. Split on one or more Unicode whitespace characters.
3. Reject an empty token list.
4. Map each token to its vocabulary code.
5. Decode the resulting code list with the numeric decoder.

Exact vocabulary decoding requires complete words. Prefix handling is optional
and should be implemented as a separate input-recovery layer.

## Prefix Input

For user entry, an implementation may accept unique word prefixes. For each
vocabulary, compute:

```text
minimum_distinct_prefix_length =
    the smallest n where the first n characters of every word are unique
```

A prefix token is accepted for a vocabulary only if:

```text
length(prefix) >= minimum_distinct_prefix_length
and exactly one vocabulary word starts with prefix
```

Then the token resolves to that word's numeric code.

The reference Kotlin implementation uses this rule when auto-detecting a
phrase's language.

## Encoding Bytes To Numeric Codes

Given a byte array `source`:

```text
source_bits = 8 * length(source)
word_count = ceil((source_bits + MIN_CRC_BITS) / WORD_BITS)
total_bits = word_count * WORD_BITS
added_bits = total_bits - source_bits
```

`added_bits` is always in `4..14`.

There are two encoded forms:

```text
ordinary:
    source || crc

guarded:
    source || guard_byte || crc
```

Use the ordinary form when `added_bits < 12`:

```text
crc_bits = added_bits
crc = CRC(source, crc_bits)
bit_stream = bits(source) || bits(crc, crc_bits)
```

Use the guarded form when `added_bits >= 12`:

```text
crc_bits = added_bits - GUARD_BITS
source_crc = CRC(source, crc_bits)
guard_byte = first byte in 0..255 where
    CRC(source || guard_byte, crc_bits) != source_crc
bit_stream = bits(source) || bits(guard_byte, 8) || bits(source_crc, crc_bits)
```

Finally split `bit_stream` into consecutive 11-bit groups and convert each group
to an integer, MSB-first. The result is the numeric EWS code list.

## Decoding Numeric Codes To Bytes

Given a numeric code list:

1. Reject an empty list.
2. Reject any code outside `0..2047`.
3. Convert each code to exactly 11 bits, MSB-first, and concatenate them.
4. Try every `added_bits` value in `4..14` where:

```text
source_bits = bit_stream.length - added_bits
source_bits >= 0
source_bits % 8 == 0
```

Validate each structural candidate.

For `added_bits < 12`, validate the ordinary form:

```text
crc_bits = added_bits
source = first source_bits as bytes
encoded_crc = final crc_bits as integer

valid if CRC(source, crc_bits) == encoded_crc
```

For `added_bits >= 12`, validate the guarded form:

```text
crc_bits = added_bits - GUARD_BITS
source = first source_bits as bytes
guard_byte = next 8 bits as byte
encoded_crc = final crc_bits as integer

valid if:
    CRC(source, crc_bits) == encoded_crc
and CRC(source || guard_byte, crc_bits) != encoded_crc
```

The decoder must accept exactly one valid candidate. Reject the code list if no
candidate validates or if more than one candidate validates.

## Why The Guard Byte Exists

Some word counts can represent two possible byte lengths. For example:

```text
2 words = 22 bits

1 source byte  + 14 added bits = 22 bits
2 source bytes +  6 added bits = 22 bits
```

Without a guard, both interpretations could theoretically pass their CRC checks.
For `added_bits` values `12`, `13`, and `14`, EWS spends 8 bits on a guard byte
and keeps `4`, `5`, or `6` CRC bits. The selected guard byte forces the longer
ordinary interpretation to fail deterministically for encodings produced by the
standard algorithm.

## CRC Definition

The CRC width is `crc_bits`.

Standard EWS encoding uses CRC widths `4..11` in ordinary form and `4..6` in
guarded form. The polynomial table also includes widths `12..14` to match the
reference low-level CRC helper.

Initial state:

```text
crc = (1 << crc_bits) - 1
```

For each input byte, process bits from bit 7 down to bit 0:

```text
top_bit = 1 << (crc_bits - 1)
mask = (1 << crc_bits) - 1
poly = CRC_POLYNOMIAL[crc_bits]

for each input bit:
    carry = (crc & top_bit) != 0
    crc = (crc << 1) & mask
    if carry XOR input_bit:
        crc = crc XOR poly

return crc & mask
```

Polynomial table:

| CRC bits | Polynomial |
|---:|---:|
| 4 | `0x3` |
| 5 | `0x05` |
| 6 | `0x03` |
| 7 | `0x09` |
| 8 | `0x07` |
| 9 | `0x11` |
| 10 | `0x09` |
| 11 | `0x05` |
| 12 | `0x80f` |
| 13 | `0x1cf5` |
| 14 | `0x0805` |

## Reference Pseudocode

```text
function encode(source):
    source_bits = 8 * length(source)
    word_count = ceil((source_bits + 4) / 11)
    added_bits = word_count * 11 - source_bits

    bits = bytes_to_bits_msb_first(source)

    if added_bits >= 12:
        crc_bits = added_bits - 8
        crc = CRC(source, crc_bits)

        for guard in 0..255:
            if CRC(source || byte(guard), crc_bits) != crc:
                break

        append bits(guard, 8) to bits
        append bits(crc, crc_bits) to bits
    else:
        crc_bits = added_bits
        crc = CRC(source, crc_bits)
        append bits(crc, crc_bits) to bits

    return split_into_11_bit_codes(bits)
```

```text
function decode(codes):
    if codes is empty:
        reject
    if any code is outside 0..2047:
        reject

    bits = codes_to_bits_msb_first(codes)
    valid_sources = []

    for added_bits in 4..14:
        source_bits = length(bits) - added_bits
        if source_bits < 0 or source_bits % 8 != 0:
            continue

        if added_bits >= 12:
            crc_bits = added_bits - 8
            source = bits[0 : source_bits] as bytes
            guard = bits[source_bits : source_bits + 8] as byte
            encoded_crc =
                bits[source_bits + 8 : source_bits + 8 + crc_bits] as int

            if CRC(source, crc_bits) == encoded_crc and
               CRC(source || guard, crc_bits) != encoded_crc:
                append source to valid_sources
        else:
            crc_bits = added_bits
            source = bits[0 : source_bits] as bytes
            encoded_crc = bits[source_bits : source_bits + crc_bits] as int

            if CRC(source, crc_bits) == encoded_crc:
                append source to valid_sources

    if length(valid_sources) != 1:
        reject

    return valid_sources[0]
```

## Text Helpers

EWS itself encodes bytes, not text. A text helper should encode strings as
UTF-8 bytes before EWS encoding and decode the recovered bytes as UTF-8 after
EWS decoding.

Invalid UTF-8 after byte decoding is a text-layer error, not a numeric EWS
format error.

## Language Auto-Detection

To auto-detect a phrase against bundled vocabularies:

1. Split the phrase into tokens.
2. For each vocabulary, resolve every token as either an exact word or a unique
   prefix satisfying that vocabulary's `minimum_distinct_prefix_length`.
3. If all tokens resolve, decode the numeric code list.
4. Keep the vocabulary as a candidate only if the CRC/guard decoder accepts it.

Return all valid candidates. Most valid phrases produce zero or one candidate,
but multiple candidates are possible because short CRCs and independent
vocabularies can collide.

## Test Vectors

These vectors use the English vocabulary.

| Source bytes | Numeric codes | English phrase |
|---|---|---|
| empty byte array | `[2047]` | `zoo` |
| `00` | `[0, 8]` | `abandon absurd` |
| `01234567` | `[9, 209, 718, 3]` | `abuse boss flush about` |
| UTF-8 `hello EWS` (`68656c6c6f20455753`) | `[835, 347, 216, 1778, 34, 1373, 618]` | `half clock brand tattoo affair produce essence` |

## Compatibility Checklist

A compatible implementation should:

- validate vocabularies contain exactly 2048 distinct words;
- map words to zero-based indexes and indexes back to words;
- preserve MSB-first bit ordering for bytes, CRC bits, guard bytes, and codes;
- reject empty code lists and codes outside `0..2047`;
- reject numeric decodings unless exactly one source-length candidate validates;
- treat EWS as byte encoding and keep text handling as a UTF-8 helper layer;
- avoid BIP39 checksum, entropy sizing, PBKDF2, seed, and wallet rules.

For a more focused description of the CRC padding and 11-bit packing, see
[EWS-PADDING.md](EWS-PADDING.md).
