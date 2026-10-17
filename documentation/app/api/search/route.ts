import { createFromSource } from 'fumadocs-core/search/server';
import { source } from '@/lib/source';
import { unicodeTokenizer } from '@/lib/search-tokenizer';

// Exported as a static JSON index at build time (output: 'export' has no server).
export const revalidate = false;

export const { staticGET: GET } = createFromSource(source, {
  localeMap: {
    en: 'english',
    ko: { tokenizer: unicodeTokenizer },
  },
});
