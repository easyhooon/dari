'use client';

import { usePathname } from 'next/navigation';
import { create } from '@orama/orama';
import { useDocsSearch } from 'fumadocs-core/search/client';
import { oramaStaticClient } from 'fumadocs-core/search/client/orama-static';
import {
  SearchDialog,
  SearchDialogClose,
  SearchDialogContent,
  SearchDialogHeader,
  SearchDialogIcon,
  SearchDialogInput,
  SearchDialogList,
  SearchDialogOverlay,
} from 'fumadocs-ui/components/dialog/search';
import type { SharedProps } from 'fumadocs-ui/contexts/search';
import { unicodeTokenizer } from '@/lib/search-tokenizer';

// The default static client fetches /api/search without the basePath and can't build a Korean Orama DB.
const client = (locale: string) =>
  oramaStaticClient({
    from: '/dari/api/search',
    locale,
    initOrama: (l) =>
      create({
        schema: { _: 'string' },
        ...(l === 'ko' ? { components: { tokenizer: unicodeTokenizer } } : { language: 'english' }),
      }),
  });

export function StaticSearchDialog(props: SharedProps) {
  const pathname = usePathname();
  const locale = pathname.startsWith('/ko') ? 'ko' : 'en';
  const { search, setSearch, query } = useDocsSearch({ client: client(locale) });

  return (
    <SearchDialog search={search} onSearchChange={setSearch} isLoading={query.isLoading} {...props}>
      <SearchDialogOverlay />
      <SearchDialogContent>
        <SearchDialogHeader>
          <SearchDialogIcon />
          <SearchDialogInput />
          <SearchDialogClose />
        </SearchDialogHeader>
        <SearchDialogList items={query.data !== 'empty' ? query.data : null} />
      </SearchDialogContent>
    </SearchDialog>
  );
}
