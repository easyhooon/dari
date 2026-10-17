import type { Tokenizer } from '@orama/orama';

// Orama has no Korean language and its default splitter drops Hangul, so split on any non-letter/digit.
export const unicodeTokenizer: Tokenizer = {
  language: 'korean',
  normalizationCache: new Map(),
  tokenize(raw) {
    return Array.from(new Set(raw.toLowerCase().split(/[^\p{L}\p{N}_]+/u).filter(Boolean)));
  },
};
