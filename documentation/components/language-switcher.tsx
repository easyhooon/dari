'use client';

import { useParams, usePathname } from 'next/navigation';
import Link from 'next/link';
import { i18n } from '@/lib/i18n';
import { Globe } from 'lucide-react';

const toggleLabels: Record<string, string> = {
  en: 'EN',
  ko: '한국어',
};

export function LanguageSwitcher({ showIcon = true }: { showIcon?: boolean }) {
  const params = useParams();
  const pathname = usePathname();
  const currentLang = (params.lang as string) || i18n.defaultLanguage;

  const getLocalePath = (locale: string) => {
    let path = pathname;

    for (const lang of i18n.languages) {
      if (path.startsWith(`/${lang}/`) || path === `/${lang}`) {
        path = path.slice(lang.length + 1) || '/';
        break;
      }
    }

    if (locale === i18n.defaultLanguage) {
      return `${path}`;
    }
    return `/${locale}${path}`;
  };

  if (i18n.languages.length <= 1) return null;

  return (
    <div className="flex items-center gap-1.5 text-fd-muted-foreground">
      {showIcon && <Globe className="size-4" />}
      <div role="group" aria-label="Language" className="flex rounded-md border p-0.5">
        {i18n.languages.map((lang) => (
          <Link
            key={lang}
            href={getLocalePath(lang)}
            aria-current={lang === currentLang ? 'true' : undefined}
            className={`rounded px-2 py-1 text-xs transition-colors ${
              lang === currentLang
                ? 'bg-fd-accent font-medium text-fd-foreground'
                : 'hover:text-fd-foreground'
            }`}
          >
            {toggleLabels[lang] || lang}
          </Link>
        ))}
      </div>
    </div>
  );
}
