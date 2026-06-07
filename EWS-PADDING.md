# EWS CRC Padding and 11-Bit Word Packing

This document specifies how to encode a byte string as a sequence of 11-bit EWS
words with deterministic padding removal and a small CRC check.

The algorithm has three goals:

- the encoded bit stream is exactly divisible into 11-bit words;
- every encoded value has at least 4 effective CRC bits;
- the word count is minimal for the 4-bit minimum CRC requirement.

All bit ordering in this document is most-significant-bit first.

## Constants

```text
BITS_IN_BYTE = 8
WORD_BITS = 11
MIN_CRC_BITS = 4
MAX_ADDED_BITS = 14
GUARD_BITS = 8
```

`MAX_ADDED_BITS` follows from the minimal word-count rule. If at least 4 bits
must be added and the result is rounded up to an 11-bit boundary, the number of
added bits is always in `4..14`.

## Encoded Form

The output is a list of unsigned 11-bit integers. Each integer must be in
`0..2047`.

The encoded bit stream is one of two forms:

```text
ordinary form:
    source || crc

guarded form:
    source || guard_byte || crc
```

The guarded form is used only when the total number of added bits is `12`, `13`,
or `14`. In that case the final 8 added bits before the CRC are a guard byte,
and the remaining `4`, `5`, or `6` bits are the CRC.

## Encoding

Given `source`, a byte array:

```text
source_bits = 8 * source.length
word_count = ceil((source_bits + MIN_CRC_BITS) / WORD_BITS)
total_bits = word_count * WORD_BITS
added_bits = total_bits - source_bits
```

`added_bits` must be in `4..14`.

If `added_bits < 12`, encode the ordinary form:

```text
crc_bits = added_bits
crc = CRC(source, crc_bits)
bit_stream = bits(source) || bits(crc, crc_bits)
```

If `added_bits >= 12`, encode the guarded form:

```text
crc_bits = added_bits - GUARD_BITS
source_crc = CRC(source, crc_bits)
guard = first byte in 0..255 where CRC(source || guard, crc_bits) != source_crc
bit_stream = bits(source) || bits(guard, 8) || bits(source_crc, crc_bits)
```

Finally, split `bit_stream` into consecutive 11-bit groups. Convert each group
to an integer, MSB-first. The resulting integer list is the encoded EWS word
sequence.

## Decoding

Given a list of EWS words:

1. Reject an empty list.
2. Reject any word outside `0..2047`.
3. Convert each word to 11 bits, MSB-first, and concatenate them.
4. Try every `added_bits` value in `4..14` where:

```text
(bit_stream.length - added_bits) >= 0
(bit_stream.length - added_bits) % 8 == 0
```

Each such value is a structural candidate. Validate each candidate as described
below.

Exactly one candidate must validate. If zero candidates validate, reject the
word sequence. If more than one candidate validates, reject the word sequence.

### Ordinary Candidate

For candidate `added_bits < 12`:

```text
crc_bits = added_bits
source_bits = bit_stream.length - crc_bits
source = first source_bits, converted to bytes
encoded_crc = final crc_bits
```

The candidate validates if:

```text
CRC(source, crc_bits) == encoded_crc
```

### Guarded Candidate

For candidate `added_bits >= 12`:

```text
crc_bits = added_bits - 8
source_bits = bit_stream.length - added_bits
source = first source_bits, converted to bytes
guard = next 8 bits, converted to one byte
encoded_crc = final crc_bits
```

The candidate validates if both conditions are true:

```text
CRC(source, crc_bits) == encoded_crc
CRC(source || guard, crc_bits) != encoded_crc
```

The second condition is the deterministic disambiguation rule. It ensures that
the guarded short-source interpretation and the ordinary longer-source
interpretation cannot both be valid for an encoding produced by this algorithm.

## Why the Guard Exists

Some word counts can be produced by two different byte-aligned source lengths.
For example:

```text
2 words = 22 bits

1 source byte  + 14 added bits = 22 bits
2 source bytes +  6 added bits = 22 bits
```

Without the guard, a decoder would need to choose between two CRC-bearing
interpretations. A wrong interpretation could pass by CRC collision.

The guarded form spends 8 bits from the larger added-bit cases:

```text
12 added bits -> guard8 + CRC4
13 added bits -> guard8 + CRC5
14 added bits -> guard8 + CRC6
```

The encoder chooses a guard byte that makes the longer-source interpretation
fail deterministically.

## Effective CRC Size Pattern

For source sizes modulo 11:

| `source.length % 11` | Added bits | Encoded form | Effective CRC bits |
|---:|---:|---|---:|
| 0 | 11 | ordinary | 11 |
| 1 | 14 | guarded | 6 |
| 2 | 6 | ordinary | 6 |
| 3 | 9 | ordinary | 9 |
| 4 | 12 | guarded | 4 |
| 5 | 4 | ordinary | 4 |
| 6 | 7 | ordinary | 7 |
| 7 | 10 | ordinary | 10 |
| 8 | 13 | guarded | 5 |
| 9 | 5 | ordinary | 5 |
| 10 | 8 | ordinary | 8 |

## CRC Definition

The CRC state width is `crc_bits`, where `crc_bits` is in `4..14`.

Initial state:

```text
crc = (1 << crc_bits) - 1
```

For each source byte, process bits from bit 7 down to bit 0:

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
        guard = 0
        while CRC(source || byte(guard), crc_bits) == crc:
            guard = guard + 1
        append bits(guard, 8) to bits
        append bits(crc, crc_bits) to bits
    else:
        crc_bits = added_bits
        crc = CRC(source, crc_bits)
        append bits(crc, crc_bits) to bits

    return split_into_11_bit_words(bits)
```

```text
function decode(words):
    if words is empty:
        reject
    if any word is not in 0..2047:
        reject

    bits = words_to_bits_msb_first(words)
    valid_sources = []

    for added_bits in 4..14:
        source_bits = length(bits) - added_bits
        if source_bits < 0 or source_bits % 8 != 0:
            continue

        if added_bits >= 12:
            crc_bits = added_bits - 8
            source = bits[0 : source_bits] as bytes
            guard = bits[source_bits : source_bits + 8] as byte
            encoded_crc = bits[source_bits + 8 : source_bits + 8 + crc_bits] as int

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
