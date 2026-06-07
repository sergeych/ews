# Entropy Word Sequences 1

This is a standard for encoding entropy as word sequences. It is intentionally
not compatible with BIP39.

- not depending on the words spelling to some extent
- possibility to use different languages: byte sequences have representations in different languages, and can be restored with any language.
- not to depend on PBKDF, character encoding, etc.

EWS uses familiar 2048-word vocabulary files where possible. The current
vocabulary texts are copied from the Bitcoin BIPs repository with thanks:

https://github.com/bitcoin/bips/tree/master/bip-0039

Only the vocabulary texts are reused; EWS code words, padding, checks, and
recovery rules are deliberately different from BIP39.

TODO
