import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { i18n } from '@/lib/i18n';

export function baseOptions(lang: string = i18n.defaultLanguage): BaseLayoutProps {
  return {
    nav: {
      title: <span className="font-semibold">Dari</span>,
    },
    // The sidebar footer renders the theme switch and GitHub link as rows instead.
    themeSwitch: { enabled: false },
    links: [
      {
        text: lang === 'ko' ? '다운로드' : 'Download',
        url: lang === i18n.defaultLanguage ? '/download' : `/${lang}/download`,
      },
    ],
  };
}
