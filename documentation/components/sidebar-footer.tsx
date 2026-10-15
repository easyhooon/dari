'use client';

import { useParams } from 'next/navigation';
import type { ReactNode } from 'react';
import { ThemeSwitch } from 'fumadocs-ui/layouts/shared/slots/theme-switch';
import { Globe, SunMoon } from 'lucide-react';
import { i18n } from '@/lib/i18n';
import { GithubIcon } from '@/components/github-icon';
import { LanguageSwitcher } from '@/components/language-switcher';

const labels: Record<string, { theme: string; language: string; useful: string; star: string }> = {
  en: { theme: 'Theme', language: 'Language', useful: 'Found Dari useful?', star: '⭐ Star on GitHub' },
  ko: { theme: '테마', language: '언어', useful: 'Dari가 도움이 됐나요?', star: '⭐ GitHub에서 Star 주기' },
};

const rowClass = 'flex items-center gap-2 rounded-lg px-2 py-1.5 text-fd-muted-foreground [&>svg]:size-4';

function Row({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return (
    <div className={rowClass}>
      {icon}
      <span>{label}</span>
      <div className="ms-auto">{children}</div>
    </div>
  );
}

export function SidebarFooter() {
  const params = useParams();
  const t = labels[(params.lang as string) || i18n.defaultLanguage] ?? labels.en;

  return (
    <div className="flex flex-col gap-0.5 md:mt-2 md:border-t md:pt-2">
      <a
        href="https://github.com/easyhooon/dari"
        target="_blank"
        rel="noopener noreferrer"
        className={`${rowClass} transition-colors hover:bg-fd-accent/50 hover:text-fd-accent-foreground/80`}
      >
        <GithubIcon />
        GitHub
      </a>
      <Row icon={<SunMoon />} label={t.theme}>
        <ThemeSwitch className="rounded-md p-0.5 *:rounded" />
      </Row>
      <Row icon={<Globe />} label={t.language}>
        <LanguageSwitcher showIcon={false} />
      </Row>
      <p className="flex flex-wrap items-center gap-x-1.5 px-2 pt-2 text-xs text-fd-muted-foreground">
        <span>{t.useful}</span>
        <a
          href="https://github.com/easyhooon/dari"
          target="_blank"
          rel="noopener noreferrer"
          className="font-medium underline-offset-4 hover:text-fd-foreground hover:underline"
        >
          {t.star}
        </a>
      </p>
    </div>
  );
}
