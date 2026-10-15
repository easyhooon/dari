'use client';

import { useEffect, useState } from 'react';
import type { DownloadLang } from './download-page';

const RELEASES_URL = 'https://github.com/easyhooon/dari/releases/latest';
const LATEST_API = 'https://api.github.com/repos/easyhooon/dari/releases/latest';

type Target = 'mac-arm64' | 'mac-x64' | 'windows';

interface Installer {
  target: Target;
  label: string;
  detail: string;
  pattern: RegExp;
  preview?: boolean;
}

// Asset names come from scripts/package-desktop-app.sh.
const INSTALLERS: Installer[] = [
  { target: 'mac-arm64', label: 'macOS', detail: 'Apple Silicon', pattern: /-arm64\.dmg$/ },
  { target: 'mac-x64', label: 'macOS', detail: 'Intel', pattern: /-x64\.dmg$/ },
  { target: 'windows', label: 'Windows', detail: 'x64', pattern: /-x64\.msi$/, preview: true },
];

interface Asset {
  name: string;
  size: number;
  browser_download_url: string;
}

interface Release {
  tag_name: string;
  assets: Asset[];
}

interface UserAgentData {
  platform?: string;
  getHighEntropyValues?: (hints: string[]) => Promise<{ architecture?: string }>;
}

async function detectTarget(): Promise<Target | null> {
  const uaData = (navigator as Navigator & { userAgentData?: UserAgentData }).userAgentData;
  const ua = navigator.userAgent;
  if (/Windows/i.test(ua)) return 'windows';
  if (!/Mac OS X|Macintosh/i.test(ua)) return null;
  // Browsers report "Intel" for every Mac; Chromium can tell the real architecture.
  try {
    const { architecture } = (await uaData?.getHighEntropyValues?.(['architecture'])) ?? {};
    if (architecture === 'x86') return 'mac-x64';
  } catch {
    // Fall through to the common case.
  }
  return 'mac-arm64';
}

const strings = {
  en: {
    downloadFor: (name: string) => `Download for ${name}`,
    preview: 'Preview',
    opensReleases: 'Opens the releases page',
    finding: 'Finding the latest release…',
    releasesPage: 'Releases page',
    comingSoon: 'Coming soon',
    allReleases: 'All releases',
  },
  ko: {
    downloadFor: (name: string) => `${name}용 다운로드`,
    preview: '미리보기',
    opensReleases: '릴리스 페이지로 이동',
    finding: '최신 릴리스를 찾는 중…',
    releasesPage: '릴리스 페이지',
    comingSoon: '준비 중',
    allReleases: '모든 릴리스',
  },
};

function formatSize(bytes: number) {
  return `${(bytes / 1_000_000).toFixed(0)} MB`;
}

export function DownloadButtons({ lang }: { lang: DownloadLang }) {
  const t = strings[lang];
  const [release, setRelease] = useState<Release | null>(null);
  const [failed, setFailed] = useState(false);
  const [target, setTarget] = useState<Target | null>(null);

  useEffect(() => {
    detectTarget().then(setTarget);
    fetch(LATEST_API)
      .then((response) => (response.ok ? response.json() : Promise.reject(response.status)))
      .then((data: Release) => setRelease(data))
      .catch(() => setFailed(true));
  }, []);

  const assetFor = (installer: Installer) => release?.assets.find((asset) => installer.pattern.test(asset.name));
  const checksumFor = (asset: Asset) => release?.assets.find((other) => other.name === `${asset.name}.sha256`);

  const primary = INSTALLERS.find((installer) => installer.target === target) ?? INSTALLERS[0];
  const others = INSTALLERS.filter((installer) => installer !== primary);
  const primaryAsset = assetFor(primary);

  return (
    <div className="flex w-full max-w-xl flex-col items-center gap-6">
      <a
        href={primaryAsset?.browser_download_url ?? RELEASES_URL}
        className="flex w-full flex-col items-center gap-1 rounded-2xl bg-black px-10 py-5 text-white transition-opacity hover:opacity-80 dark:bg-white dark:text-black"
      >
        <span className="text-lg font-semibold">
          {t.downloadFor(`${primary.label} (${primary.detail})`)}
          {primary.preview ? ` · ${t.preview}` : ''}
        </span>
        <span className="text-sm opacity-70">
          {primaryAsset && release
            ? `${release.tag_name} · ${primaryAsset.name} · ${formatSize(primaryAsset.size)}`
            : release || failed
              ? t.opensReleases
              : t.finding}
        </span>
      </a>

      <div className="flex w-full flex-col gap-2">
        {others.map((installer) => {
          const asset = assetFor(installer);
          return (
            <a
              key={installer.target}
              href={asset?.browser_download_url ?? RELEASES_URL}
              className="flex items-center justify-between rounded-xl border border-gray-200 px-5 py-3 text-sm transition-colors hover:bg-gray-50 dark:border-gray-800 dark:hover:bg-neutral-900"
            >
              <span className="font-medium">
                {installer.label} ({installer.detail})
                {installer.preview ? (
                  <span className="ml-2 rounded-md bg-gray-100 px-1.5 py-0.5 text-xs text-gray-500 dark:bg-neutral-800 dark:text-gray-400">
                    {t.preview}
                  </span>
                ) : null}
              </span>
              <span className="text-gray-500 dark:text-gray-400">{asset ? formatSize(asset.size) : t.releasesPage}</span>
            </a>
          );
        })}
        <div className="flex items-center justify-between rounded-xl border border-dashed border-gray-200 px-5 py-3 text-sm text-gray-400 dark:border-gray-800">
          <span className="font-medium">Linux</span>
          <span>{t.comingSoon}</span>
        </div>
      </div>

      <p className="text-xs text-gray-500 dark:text-gray-400">
        {primaryAsset && checksumFor(primaryAsset) ? (
          <>
            <a href={checksumFor(primaryAsset)?.browser_download_url} className="underline underline-offset-4">
              SHA-256
            </a>
            {' · '}
          </>
        ) : null}
        <a href={RELEASES_URL} className="underline underline-offset-4">
          {t.allReleases}
        </a>
      </p>
    </div>
  );
}
