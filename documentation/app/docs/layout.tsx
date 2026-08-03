import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { baseOptions } from '@/app/layout.config';
import { source } from '@/lib/source';
import { i18n } from '@/lib/i18n';
import { LanguageSwitcher } from '@/components/language-switcher';

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <DocsLayout
      tree={source.getPageTree(i18n.defaultLanguage)}
      {...baseOptions}
      sidebar={{ footer: <LanguageSwitcher /> }}
    >
      {children}
    </DocsLayout>
  );
}
