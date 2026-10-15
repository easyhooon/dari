import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { Github } from 'lucide-react';
import { i18n } from '@/lib/i18n';

export function baseOptions(lang: string = i18n.defaultLanguage): BaseLayoutProps {
  return {
    nav: {
      title: <span className="font-semibold">Dari</span>,
    },
    links: [
      {
        text: lang === 'ko' ? '다운로드' : 'Download',
        url: lang === i18n.defaultLanguage ? '/download' : `/${lang}/download`,
      },
      {
        type: 'icon',
        text: 'GitHub',
        label: 'GitHub',
        icon: <Github />,
        url: 'https://github.com/easyhooon/dari',
        external: true,
      },
    ],
  };
}
