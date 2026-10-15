import Link from 'next/link';
import type { ReactNode } from 'react';
import { LanguageSwitcher } from '@/components/language-switcher';
import { DownloadButtons } from './download-buttons';

export type DownloadLang = 'en' | 'ko';

const linkClass = 'underline underline-offset-4 hover:text-gray-900 dark:hover:text-gray-100';

interface Copy {
  title: string;
  intro: string;
  terminalHeading: string;
  checksumNote: string;
  requirements: ReactNode;
  guide: ReactNode;
}

const copy: Record<DownloadLang, Copy> = {
  en: {
    title: 'Dari for desktop',
    intro:
      'Watch WebView bridge messages from a connected debug build, live on your computer. It needs no proxy, certificate or port setup, only adb.',
    terminalHeading: 'Or install on macOS from Terminal',
    checksumNote: 'Verifies the SHA-256 checksum before installing.',
    requirements: (
      <>
        Requires <code>dari</code> 2.0.0 or later in your app&apos;s debug build, and <code>adb</code> on your
        computer.
      </>
    ),
    guide: (
      <>
        Installing, connecting a device and privacy: see the{' '}
        <Link href="/docs/desktop" className={linkClass}>
          Desktop App guide
        </Link>
        .
      </>
    ),
  },
  ko: {
    title: 'Dari 데스크톱',
    intro:
      '연결된 디버그 빌드의 WebView 브릿지 메시지를 컴퓨터에서 실시간으로 확인하세요. 프록시, 인증서, 포트 설정 없이 adb만 있으면 됩니다.',
    terminalHeading: '또는 macOS 터미널에서 설치',
    checksumNote: '설치 전에 SHA-256 체크섬을 검증합니다.',
    requirements: (
      <>
        앱의 디버그 빌드에 <code>dari</code> 2.0.0 이상이, 컴퓨터에 <code>adb</code>가 필요합니다.
      </>
    ),
    guide: (
      <>
        설치, 기기 연결, 개인정보 처리는{' '}
        <Link href="/ko/docs/desktop" className={linkClass}>
          데스크톱 앱 가이드
        </Link>
        를 참고하세요.
      </>
    ),
  },
};

export function DownloadPageContent({ lang }: { lang: DownloadLang }) {
  const t = copy[lang];

  return (
    <main className="relative flex min-h-screen flex-col items-center justify-center gap-10 bg-white px-4 py-16 text-center dark:bg-neutral-950">
      <div className="absolute right-4 top-4">
        <LanguageSwitcher />
      </div>

      <div className="flex flex-col items-center gap-4">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/dari/logo.png" alt="Dari logo" width={120} height={120} className="rounded-2xl" />
        <h1 className="text-5xl font-bold tracking-tight">{t.title}</h1>
        <p className="max-w-xl text-lg text-gray-500 dark:text-gray-400">{t.intro}</p>
      </div>

      <DownloadButtons lang={lang} />

      <div className="flex w-full max-w-xl flex-col gap-2 text-left">
        <p className="text-sm font-medium text-gray-700 dark:text-gray-300">{t.terminalHeading}</p>
        <pre className="overflow-x-auto rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-xs dark:border-gray-800 dark:bg-neutral-900">
          <code>{'curl -fsSL https://raw.githubusercontent.com/easyhooon/dari/main/scripts/install-desktop.sh | bash'}</code>
        </pre>
        <p className="text-xs text-gray-500 dark:text-gray-400">{t.checksumNote}</p>
      </div>

      <div className="flex max-w-xl flex-col gap-2 text-sm text-gray-500 dark:text-gray-400">
        <p>{t.requirements}</p>
        <p>{t.guide}</p>
      </div>
    </main>
  );
}
