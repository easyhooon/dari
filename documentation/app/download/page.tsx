import type { Metadata } from 'next';
import Link from 'next/link';
import { DownloadButtons } from './download-buttons';

export const metadata: Metadata = {
  title: 'Download Dari for desktop',
  description: 'Inspect WebView bridge messages from your computer. Dari for macOS, with Windows in preview.',
};

export default function DownloadPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-10 bg-white px-4 py-16 text-center dark:bg-neutral-950">
      <div className="flex flex-col items-center gap-4">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/dari/logo.png" alt="Dari logo" width={120} height={120} className="rounded-2xl" />
        <h1 className="text-5xl font-bold tracking-tight">Dari for desktop</h1>
        <p className="max-w-xl text-lg text-gray-500 dark:text-gray-400">
          Watch WebView bridge messages from a connected debug build, live on your computer. It needs no
          proxy, certificate or port setup, only adb.
        </p>
      </div>

      <DownloadButtons />

      <div className="flex max-w-xl flex-col gap-2 text-sm text-gray-500 dark:text-gray-400">
        <p>
          Requires <code>dari</code> 2.0.0 or later in your app&apos;s debug build, and <code>adb</code> on your
          computer.
        </p>
        <p>
          macOS blocks the first launch because Dari isn&apos;t notarized: click <strong>Open Anyway</strong> in System
          Settings → Privacy &amp; Security.
        </p>
        <p>
          First launch, connecting a device and privacy: see the{' '}
          <Link href="/docs/desktop" className="underline underline-offset-4 hover:text-gray-900 dark:hover:text-gray-100">
            Desktop App guide
          </Link>
          .
        </p>
      </div>
    </main>
  );
}
